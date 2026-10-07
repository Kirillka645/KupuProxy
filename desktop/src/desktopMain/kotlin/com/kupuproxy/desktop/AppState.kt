package com.kupuproxy.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Раздел главного окна. */
enum class DesktopTab(val title: String) {
    DASHBOARD("Обзор"),
    PROXIES("Прокси"),
    SOURCES("Источники"),
    SETTINGS("Настройки"),
}

class AppState(initialTab: DesktopTab = DesktopTab.DASHBOARD) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val engine = ProxyEngine()
    val localProxy = LocalProxyServer()

    var settings by mutableStateOf(SettingsStore.load())
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
    var trayIcon by mutableStateOf<String?>(null)
    var running by mutableStateOf(true)

    val filteredRows: List<ProxyRow>
        get() = ProxyFilter.apply(
            rows,
            settings.toScanConfig().copy(
                searchQuery = searchQuery,
                maxLatencyFilterMs = settings.maxLatencyFilterMs,
                protocolFilter = settings.protocolFilter,
            ),
        )

    val selectedRow: ProxyRow?
        get() = selectedUrl?.let { url -> rows.firstOrNull { it.url == url } }

    val favorites: Set<String> get() = settings.favorites

    fun updateSettings(block: (DesktopSettings) -> DesktopSettings) {
        settings = block(settings)
        SettingsStore.save(settings)
    }

    fun toggleFavorite(url: String) = updateSettings {
        it.copy(favorites = if (url in it.favorites) it.favorites - url else it.favorites + url)
    }

    fun toggleProtocol(protocol: ProxyProtocol) = updateSettings {
        val next = if (protocol in it.protocolFilter) it.protocolFilter - protocol else it.protocolFilter + protocol
        // Хотя бы один протокол должен остаться включённым, иначе список будет пустым всегда.
        it.copy(protocolFilter = next.ifEmpty { ProxyProtocol.entries.toSet() })
    }

    fun select(url: String?) {
        selectedUrl = url
    }

    /** Разбирает локальный файл и запускает проверку. */
    fun scanFile(file: File) {
        scope.launch {
            val text = runCatching { ProxySource.fromFile(file) }.getOrElse {
                logError(it.message ?: "Не удалось прочитать файл")
                return@launch
            }
            startScan(text)
        }
    }

    /** Загружает список по URL и запускает проверку. */
    fun scanUrl(url: String) {
        scope.launch {
            statusMessage = "Загрузка $url…"
            val text = runCatching { ProxySource.fromUrl(url) }.getOrElse {
                logError(it.message ?: "Не удалось загрузить источник")
                statusMessage = "Готов к поиску прокси"
                return@launch
            }
            startScan(text)
        }
    }

    private fun startScan(input: ProxyInput) {
        errorMessage = null
        rows = emptyList()
        selectedUrl = null
        engine.reset()
        val config = settings.toScanConfig().copy(jitterSamples = settings.jitterSamples)
        statusMessage = "Сканирование…"

        scope.launch {
            val result = engine.scanDocument(
                body = input.body,
                config = config,
                onProgress = { state ->
                    SwingMain {
                        scan = state
                        statusMessage = if (state.running) state.phase else "Найдено ${state.found}"
                    }
                },
                onFound = { row ->
                    SwingMain { rows = (rows + row).sortedWith(compareBy({ it.latencyMs }, { it.jitterMs })) }
                },
            )
            SwingMain {
                rows = result
                scan = scan.copy(running = false, found = result.size)
                statusMessage = "Готово: ${result.size} рабочих прокси"
                refreshTraffic()
            }
        }
    }

    fun cancelScan() {
        engine.cancel()
        scan = scan.copy(running = false)
        statusMessage = "Сканирование остановлено"
    }

    /** Включает локальный прокси через выбранный рабочий прокси. */
    fun toggleLocalProxy() {
        val row = selectedRow
        if (localProxy.isRunning) {
            localProxy.stop()
            updateSettings { it.copy(localProxyEnabled = false) }
            statusMessage = "Локальный прокси остановлен"
            refreshTraffic()
            return
        }
        if (row == null) {
            errorMessage = "Сначала выберите прокси в списке"
            return
        }
        if (row.protocol == com.kupuproxy.shared.domain.model.ProxyProtocol.MTPROTO) {
            errorMessage = "MTProto нельзя использовать как туннель — откройте прокси в Telegram"
            return
        }
        val parsed = com.kupuproxy.shared.domain.parser.ProxyParser.fromUrl(row.url)
        localProxy.start(
            LocalProxyServer.Target(
                host = parsed?.host ?: row.host,
                port = parsed?.port ?: row.port,
                protocol = row.protocol,
                username = parsed?.username,
                password = parsed?.password,
            ),
        )
        updateSettings { it.copy(localProxyEnabled = true) }
        statusMessage = "Локальный прокси: 127.0.0.1:${localProxy.localPort}"
    }

    fun refreshTraffic() {
        traffic = localProxy.snapshot()
    }

    fun resetTraffic() {
        localProxy.resetStats()
        refreshTraffic()
    }

    fun dispose() {
        engine.cancel()
        localProxy.stop()
    }

    /**
     * Переносит изменение состояния в EDT. Обновления приходят из фонового пула проверки,
     * а Compose-состояние можно трогать только из UI-потока.
     */
    private fun SwingMain(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    private fun logError(message: String) {
        System.err.println("[KupuProxy] $message")
    }
}