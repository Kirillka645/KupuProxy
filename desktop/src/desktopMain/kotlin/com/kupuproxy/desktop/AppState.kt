package com.kupuproxy.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kupuproxy.shared.domain.model.ProxyProtocol
import com.kupuproxy.shared.domain.model.RawProxyEntry
import com.kupuproxy.shared.domain.parser.ProxyParser
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Раздел главного окна. */
enum class DesktopTab(val title: String) {
    DASHBOARD("Обзор"),
    PROXIES("Прокси"),
    SOURCES("Источники"),
    SETTINGS("Настройки"),
}

/** Итог загрузки одного встроенного фида — показывается на экране «Источники». */
data class FeedStatus(
    val feedId: String,
    val origin: FeedOrigin?,
    val count: Int,
    val error: String? = null,
)

class AppState(
    initialTab: DesktopTab = DesktopTab.DASHBOARD,
    /** Подменяется в тестах: Swing-диспетчер без окна недоступен. */
    private val uiDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
    private val persist: Boolean = true,
) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val engine = ProxyEngine()
    val localProxy = LocalProxyServer()

    var settings by mutableStateOf(if (persist) SettingsStore.load() else DesktopSettings())
        private set
    var tab by mutableStateOf(initialTab)
    var scan by mutableStateOf(ScanState())
    var rows by mutableStateOf(emptyList<ProxyRow>())
        private set
    var traffic by mutableStateOf(TrafficStats())
        private set
    var searchQuery by mutableStateOf("")
    var selectedUrl by mutableStateOf<String?>(null)
        private set
    var sourceLabel by mutableStateOf("—")
    var statusMessage by mutableStateOf("Готов к поиску прокси")
    var errorMessage by mutableStateOf<String?>(null)
    /** Отражает [LocalProxyServer.isRunning] в Compose-состоянии: трей и кнопки обновляются сами. */
    var localProxyRunning by mutableStateOf(false)
        private set
    /** Идёт загрузка источника или проверка. Кнопки запуска на это время блокируются. */
    var busy by mutableStateOf(false)
        private set
    var feedStatuses by mutableStateOf(emptyList<FeedStatus>())
        private set

    private var scanJob: Job? = null
    private var saveJob: Job? = null
    private val trafficHistory = ArrayDeque<Long>()
    private var lastTrafficTotal = 0L

    val filteredRows: List<ProxyRow>
        get() = ProxyFilter.apply(
            rows,
            settings.toScanConfig().copy(searchQuery = searchQuery),
        )

    val selectedRow: ProxyRow?
        get() = selectedUrl?.let { url -> rows.firstOrNull { it.url == url } }

    val favorites: Set<String> get() = settings.favorites

    /**
     * Меняет настройки. Запись на диск откладывается на 300 мс: слайдеры вызывают
     * обновление на каждый пиксель перетаскивания, и раньше каждый раз переписывался файл.
     */
    fun updateSettings(block: (DesktopSettings) -> DesktopSettings) {
        settings = block(settings)
        if (!persist) return
        saveJob?.cancel()
        val snapshot = settings
        saveJob = scope.launch {
            delay(300)
            withContext(Dispatchers.IO) { SettingsStore.save(snapshot) }
        }
    }

    fun toggleFavorite(url: String) = updateSettings {
        it.copy(favorites = if (url in it.favorites) it.favorites - url else it.favorites + url)
    }

    fun clearFavorites() = updateSettings { it.copy(favorites = emptySet()) }

    fun toggleProtocol(protocol: ProxyProtocol) = updateSettings {
        val next = if (protocol in it.protocolFilter) it.protocolFilter - protocol else it.protocolFilter + protocol
        // Хотя бы один протокол должен остаться включённым, иначе список будет пустым всегда.
        it.copy(protocolFilter = next.ifEmpty { ProxyProtocol.entries.toSet() })
    }

    fun toggleStockFeed(id: String) = updateSettings {
        val next = if (id in it.stockFeeds) it.stockFeeds - id else it.stockFeeds + id
        it.copy(stockFeeds = next)
    }

    fun select(url: String?) {
        selectedUrl = url
    }

    /** Разбирает локальный файл и запускает проверку. */
    fun scanFile(file: File) = launchScan("Чтение ${file.name}…") {
        val input = withContext(Dispatchers.IO) { ProxySource.fromFile(file) }
        sourceLabel = input.label
        ProxyParser.parse(input.body, sourceId = "file", sourceName = input.label)
    }

    /** Загружает список по URL и запускает проверку. */
    fun scanUrl(url: String) = launchScan("Загрузка $url…") {
        val input = withContext(Dispatchers.IO) { ProxySource.fromUrl(url) }
        sourceLabel = input.label
        ProxyParser.parse(input.body, sourceId = "url", sourceName = input.label)
    }

    /**
     * Скан по встроенным источникам из `proxy-feeds/`: GitHub → jsDelivr → вшитый снимок.
     * Именно его запускает кнопка «Запустить скан» — работает сразу после установки.
     */
    fun scanStock(offline: Boolean = false) {
        val feeds = StockFeeds.all.filter { it.id in settings.stockFeeds }
        if (feeds.isEmpty()) {
            errorMessage = "Включите хотя бы один встроенный источник"
            tab = DesktopTab.SOURCES
            return
        }
        launchScan("Загрузка встроенных источников…") {
            val loaded = withContext(Dispatchers.IO) {
                feeds.map { feed -> async { StockFeeds.load(feed, offline) } }.awaitAll()
            }
            feedStatuses = loaded.map { FeedStatus(it.feed.id, it.origin, it.entries.size, it.error) }
            sourceLabel = loaded.filter { it.entries.isNotEmpty() }
                .joinToString(", ") { "${it.feed.title} (${it.origin?.label})" }
                .ifEmpty { "встроенные источники" }
            val failed = loaded.filter { it.entries.isEmpty() }
            if (failed.isNotEmpty()) {
                errorMessage = "Не загрузились: " + failed.joinToString(", ") { it.feed.title }
            }
            mergeEntries(loaded.map { it.entries })
        }
    }

    /**
     * Общий запуск: отменяет предыдущий скан, грузит записи и проверяет их.
     * Раньше второй скан запускался параллельно с первым, оба писали в один список, и
     * LazyColumn падал на повторяющихся ключах.
     */
    private fun launchScan(loadingMessage: String, load: suspend () -> List<RawProxyEntry>) {
        val previous = scanJob
        previous?.cancel()
        engine.cancel()
        errorMessage = null
        busy = true
        statusMessage = loadingMessage
        scanJob = scope.launch(uiDispatcher) {
            previous?.join()
            engine.reset()
            rows = emptyList()
            selectedUrl = null
            scan = ScanState(running = true, phase = loadingMessage)
            try {
                val entries = load()
                if (entries.isEmpty()) {
                    scan = ScanState()
                    statusMessage = "В источнике не найдено ни одного прокси"
                    if (errorMessage == null) errorMessage = "Список пуст или формат не распознан"
                    return@launch
                }
                statusMessage = "Сканирование…"
                val config = settings.toScanConfig()
                val seen = HashSet<String>()
                val result = withContext(Dispatchers.IO) {
                    engine.scanEntries(
                        entries = entries,
                        config = config,
                        onProgress = { state ->
                            ui {
                                if (scanJob?.isActive == true) {
                                    scan = state
                                    if (state.running) statusMessage = state.phase
                                }
                            }
                        },
                        onFound = { row ->
                            ui {
                                if (scanJob?.isActive == true && seen.add(row.url)) {
                                    rows = (rows + row).sortedWith(ROW_ORDER)
                                }
                            }
                        },
                    )
                }
                rows = result.distinctBy { it.url }.sortedWith(ROW_ORDER)
                scan = scan.copy(running = false, found = rows.size)
                statusMessage = if (engine.isCancelled) {
                    "Сканирование остановлено: ${rows.size} рабочих прокси"
                } else {
                    "Готово: ${rows.size} рабочих прокси"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scan = scan.copy(running = false)
                statusMessage = "Готов к поиску прокси"
                errorMessage = e.message ?: e.javaClass.simpleName
                logError(errorMessage.orEmpty())
            } finally {
                if (scanJob === coroutineContext[Job]) busy = false
            }
        }
    }

    /** Склеивает несколько фидов, чередуя их: так лимит «максимум за скан» не съедает один фид. */
    private fun mergeEntries(lists: List<List<RawProxyEntry>>): List<RawProxyEntry> {
        val out = ArrayList<RawProxyEntry>(lists.sumOf { it.size })
        val seen = HashSet<String>()
        val iterators = lists.map { it.iterator() }
        var progressed = true
        while (progressed) {
            progressed = false
            for (iterator in iterators) {
                if (iterator.hasNext()) {
                    progressed = true
                    val entry = iterator.next()
                    if (seen.add(entry.dedupeKey)) out += entry
                }
            }
        }
        return out
    }

    fun cancelScan() {
        engine.cancel()
        scan = scan.copy(running = false)
        statusMessage = "Сканирование останавливается…"
    }

    /** Включает локальный прокси через выбранный рабочий прокси. */
    fun toggleLocalProxy() {
        if (localProxy.isRunning) {
            localProxy.stop()
            localProxyRunning = false
            updateSettings { it.copy(localProxyEnabled = false) }
            statusMessage = "Локальный прокси остановлен"
            refreshTraffic()
            return
        }
        val row = selectedRow
        if (row == null) {
            errorMessage = "Сначала выберите прокси в списке"
            return
        }
        if (row.protocol == ProxyProtocol.MTPROTO) {
            errorMessage = "MTProto нельзя использовать как туннель — откройте прокси в Telegram"
            return
        }
        val parsed = ProxyParser.fromUrl(row.url)
        val started = runCatching {
            localProxy.start(
                LocalProxyServer.Target(
                    host = parsed?.host ?: row.host,
                    port = parsed?.port ?: row.port,
                    protocol = row.protocol,
                    username = parsed?.username,
                    password = parsed?.password,
                ),
            )
        }
        if (started.isFailure) {
            errorMessage = "Не удалось запустить локальный прокси: ${started.exceptionOrNull()?.message}"
            localProxyRunning = false
            return
        }
        localProxyRunning = true
        lastTrafficTotal = localProxy.snapshot().totalBytes
        updateSettings { it.copy(localProxyEnabled = true) }
        statusMessage = "Локальный прокси: 127.0.0.1:${localProxy.localPort}"
    }

    /** Вызывается раз в секунду: история — это байты за секунду, а не сырые чанки сокета. */
    fun refreshTraffic() {
        val snapshot = localProxy.snapshot()
        val total = snapshot.totalBytes
        val delta = (total - lastTrafficTotal).coerceAtLeast(0)
        lastTrafficTotal = total
        if (localProxy.isRunning) {
            trafficHistory.addLast(delta)
            while (trafficHistory.size > 120) trafficHistory.removeFirst()
        }
        traffic = snapshot.copy(history = trafficHistory.toList())
        localProxyRunning = localProxy.isRunning
    }

    fun resetTraffic() {
        localProxy.resetStats()
        trafficHistory.clear()
        lastTrafficTotal = 0
        refreshTraffic()
    }

    fun dispose() {
        engine.cancel()
        scanJob?.cancel()
        localProxy.stop()
        localProxyRunning = false
        if (persist) {
            saveJob?.cancel()
            runCatching { SettingsStore.save(settings) }
        }
        scope.cancel()
    }

    /** Ждёт завершения текущего скана (для тестов). */
    suspend fun awaitScan() {
        scanJob?.join()
    }

    /**
     * Переносит изменение состояния в UI-поток. Обновления приходят из фонового пула проверки,
     * а Compose-состояние нужно менять из UI-потока.
     */
    private fun ui(block: () -> Unit) {
        scope.launch(uiDispatcher) { block() }
    }

    private fun logError(message: String) {
        System.err.println("[KupuProxy] $message")
    }

    private companion object {
        val ROW_ORDER: Comparator<ProxyRow> = compareBy({ it.latencyMs }, { it.jitterMs })
    }
}
