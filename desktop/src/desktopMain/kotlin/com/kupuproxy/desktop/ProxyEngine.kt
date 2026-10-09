package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.check.LatencyStats
import com.kupuproxy.shared.domain.check.LinkQuality
import com.kupuproxy.shared.domain.check.ProxyProbe
import com.kupuproxy.shared.domain.model.ProxyProtocol
import com.kupuproxy.shared.domain.model.RawProxyEntry
import com.kupuproxy.shared.domain.parser.ProxyParser
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Строка списка прокси с посчитанной статистикой. */
data class ProxyRow(
    val url: String,
    val host: String,
    val port: Int,
    val protocol: ProxyProtocol,
    val latencyMs: Int,
    val jitterMs: Int,
    val samples: Int,
    /** Название источника (фид, файл или URL), откуда пришёл прокси. */
    val source: String = "",
    /** Время проверки, мс epoch. 0 — неизвестно. */
    val checkedAt: Long = 0,
) {
    val quality: LinkQuality get() = LinkQuality.of(latencyMs)
    val label: String get() = "$host:$port"
    val stats: LatencyStats get() = LatencyStats(lastLatencyMs = latencyMs, avgLatencyMs = latencyMs, jitterMs = jitterMs, samples = samples)
}

/** Снимок состояния сканирования для UI. */
data class ScanState(
    val running: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val phase: String = "",
    val candidates: Int = 0,
)

data class ScanConfig(
    val connectTimeoutMs: Int = 1200,
    val responseTimeoutMs: Int = 1800,
    val maxPingMs: Int = 6000,
    val maxToCheck: Int = 2000,
    /** Потоков на рукопожатие протокола (фаза 2). */
    val parallelism: Int = 64,
    /** Потоков на TCP-префлайт (фаза 1): connect дешёвый, поэтому их больше. */
    val preflightParallelism: Int = 192,
    /** Таймаут TCP-префлайта, мс. */
    val preflightTimeoutMs: Int = 700,
    /** 1 — быстрый скан; 3 — с расчётом jitter. */
    val jitterSamples: Int = 3,
    val stopWhenFound: Int = 0,
    /** Максимальная задержка в фильтре списка, мс. */
    val maxLatencyFilterMs: Int = 5000,
    val protocolFilter: Set<ProxyProtocol> = ProxyProtocol.entries.toSet(),
    val searchQuery: String = "",
)

/**
 * Движок проверки прокси для десктопа.
 *
 * Логика та же, что и в мобильном клиенте (общий модуль `:shared`): быстрый TCP-префлайт по
 * уникальным host:port, затем полное рукопожатие протокола с несколькими замерами для jitter.
 */
class ProxyEngine {

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun reset() {
        cancelled.set(false)
    }

    val isCancelled: Boolean get() = cancelled.get()

    /**
     * Проверяет список ссылок. [onProgress] и [onFound] вызываются из фонового потока,
 * поэтому вызывающий код сам решает, как маршалить их в UI-поток.
     */
    suspend fun scan(
        urls: List<String>,
        config: ScanConfig,
        onProgress: (ScanState) -> Unit = {},
        onFound: (ProxyRow) -> Unit = {},
    ): List<ProxyRow> = scanParsed(urls.mapNotNull { ProxyProbe.parse(it) }, urls.size, config, onProgress, onFound)

    /**
     * Разбирает документ со списком прокси (txt / json / yaml / html / base64) и проверяет его.
     * Именно этот вход используется приложением: пользователь отдаёт файл или URL целиком.
     */
    suspend fun scanDocument(
        body: String,
        config: ScanConfig,
        onProgress: (ScanState) -> Unit = {},
        onFound: (ProxyRow) -> Unit = {},
    ): List<ProxyRow> {
        val parsed = ProxyParser.parse(body)
        return scanParsed(parsed, parsed.size, config, onProgress, onFound)
    }

    /** Проверяет уже разобранные записи — например, собранные из встроенных фидов. */
    suspend fun scanEntries(
        entries: List<RawProxyEntry>,
        config: ScanConfig,
        onProgress: (ScanState) -> Unit = {},
        onFound: (ProxyRow) -> Unit = {},
    ): List<ProxyRow> = scanParsed(entries, entries.size, config, onProgress, onFound)

    private suspend fun scanParsed(
        candidates: List<RawProxyEntry>,
        inputSize: Int,
        config: ScanConfig,
        onProgress: (ScanState) -> Unit,
        onFound: (ProxyRow) -> Unit,
    ): List<ProxyRow> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) {
            onProgress(ScanState(running = false, total = inputSize))
            return@withContext emptyList()
        }

        val selected = candidates
            .filter { it.protocol in config.protocolFilter }
            .distinctBy { it.dedupeKey }
            .take(config.maxToCheck)

        if (selected.isEmpty()) {
            onProgress(ScanState(running = false, total = inputSize))
            return@withContext emptyList()
        }

        val processed = AtomicInteger(0)
        val found = AtomicInteger(0)
        val rows = java.util.Collections.synchronizedList(mutableListOf<ProxyRow>())

        // Прогресс считается отдельно для каждой фазы: раньше счётчик фазы 1 (по записям
        // в группах) продолжался в фазе 2, и полоса уходила за 100 % («750 / 500»).
        var phaseTotal = 0
        fun emitPhase(phase: String) = onProgress(
            ScanState(
                running = true,
                processed = processed.get().coerceAtMost(phaseTotal),
                total = phaseTotal,
                found = found.get(),
                phase = phase,
                candidates = selected.size,
            ),
        )

        // Фаза 1 — TCP-префлайт по уникальным host:port.
        val groups = selected.groupBy { "${it.protocol.name}:${it.host.lowercase()}:${it.port}" }
        val groupList = groups.entries.toList()
        phaseTotal = selected.size
        emitPhase(PHASE_PREFLIGHT)
        val groupCursor = AtomicInteger(0)
        val reachable = java.util.Collections.synchronizedList(mutableListOf<List<RawProxyEntry>>())
        val preflightTimeout = config.preflightTimeoutMs.coerceIn(300, 2000)

        coroutineScope {
            List(config.preflightParallelism.coerceIn(1, 512).coerceAtMost(groupList.size)) {
                async {
                    while (currentCoroutineContext().isActive && !cancelled.get()) {
                        val index = groupCursor.getAndIncrement()
                        if (index >= groupList.size) break
                        val group = groupList[index].value
                        val head = group.first()
                        if (ProxyProbe.isTcpReachable(head.host, head.port, preflightTimeout)) {
                            reachable.add(group)
                        }
                        val before = processed.getAndAdd(group.size)
                        // Шаг группы может «перепрыгнуть» кратное 25 — сравниваем интервалы.
                        if ((before + group.size) / 25 != before / 25) emitPhase(PHASE_PREFLIGHT)
                    }
                }
            }.awaitAll()
        }

        if (cancelled.get() || reachable.isEmpty()) {
            onProgress(ScanState(running = false, processed = processed.get().coerceAtMost(phaseTotal), total = phaseTotal, found = found.get(), candidates = selected.size))
            return@withContext rows.toList()
        }

        // Фаза 2 — рукопожатие протокола, один замер. TCP уже проверен в фазе 1, поэтому
        // префлайт не повторяется. Рабочие прокси появляются в списке сразу, jitter
        // досчитывается в фазе 3 — раньше каждая запись ждала все замеры подряд.
        val live = reachable.flatMap { it }
        val cursor = AtomicInteger(0)
        processed.set(0)
        phaseTotal = live.size
        emitPhase(PHASE_HANDSHAKE)
        val stopReached = AtomicBoolean(false)

        coroutineScope {
            List(config.parallelism.coerceIn(1, 256).coerceAtMost(live.size)) {
                async {
                    while (currentCoroutineContext().isActive && !cancelled.get() && !stopReached.get()) {
                        val index = cursor.getAndIncrement()
                        if (index >= live.size) break
                        val entry = live[index]

                        val result = try {
                            ProxyProbe.probe(
                                entry = entry,
                                connectTimeoutMs = config.connectTimeoutMs,
                                responseTimeoutMs = config.responseTimeoutMs,
                                samples = 1,
                                skipPreflight = true,
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }

                        if (result != null && result.ok && result.latencyMs in 1 until config.maxPingMs) {
                            val row = ProxyRow(
                                url = entry.url,
                                host = entry.host,
                                port = entry.port,
                                protocol = result.protocol,
                                latencyMs = result.latencyMs,
                                jitterMs = result.jitterMs,
                                samples = result.samples.size.coerceAtLeast(1),
                                source = entry.sourceName,
                                checkedAt = System.currentTimeMillis(),
                            )
                            rows.add(row)
                            val count = found.incrementAndGet()
                            onFound(row)
                            if (config.stopWhenFound > 0 && count >= config.stopWhenFound) stopReached.set(true)
                        }
                        val done = processed.incrementAndGet()
                        if (done % 5 == 0 || done == live.size) emitPhase(PHASE_HANDSHAKE)
                    }
                }
            }.awaitAll()
        }

        // Фаза 3 — дополнительные замеры для jitter только по рабочим прокси.
        val extra = config.jitterSamples.coerceIn(1, 5) - 1
        if (extra > 0 && rows.isNotEmpty() && !cancelled.get()) {
            val found1 = rows.toList()
            val refineCursor = AtomicInteger(0)
            processed.set(0)
            phaseTotal = found1.size
            emitPhase(PHASE_JITTER)
            val byUrl = live.associateBy { it.url }
            coroutineScope {
                List(config.parallelism.coerceIn(1, 256).coerceAtMost(found1.size)) {
                    async {
                        while (currentCoroutineContext().isActive && !cancelled.get()) {
                            val index = refineCursor.getAndIncrement()
                            if (index >= found1.size) break
                            val base = found1[index]
                            val entry = byUrl[base.url]
                            val more = entry?.let {
                                runCatching {
                                    ProxyProbe.probe(it, config.connectTimeoutMs, config.responseTimeoutMs, extra, skipPreflight = true)
                                }.getOrNull()
                            }
                            if (more != null && more.ok) {
                                val stats = LatencyStats.from(listOf(base.latencyMs) + more.samples)
                                val refined = base.copy(
                                    latencyMs = stats.avgLatencyMs,
                                    jitterMs = stats.jitterMs,
                                    samples = stats.samples,
                                    checkedAt = System.currentTimeMillis(),
                                )
                                synchronized(rows) {
                                    val at = rows.indexOfFirst { it.url == base.url }
                                    if (at >= 0) rows[at] = refined
                                }
                                onFound(refined)
                            }
                            val done = processed.incrementAndGet()
                            if (done % 5 == 0 || done == found1.size) emitPhase(PHASE_JITTER)
                        }
                    }
                }.awaitAll()
            }
        }

        onProgress(
            ScanState(
                running = false,
                processed = processed.get().coerceAtMost(phaseTotal),
                total = phaseTotal,
                found = found.get(),
                candidates = selected.size,
            ),
        )
        rows.toList().sortedWith(compareBy({ it.latencyMs }, { it.jitterMs }))
    }

    companion object {
        const val PHASE_PREFLIGHT = "Проверка доступности…"
        const val PHASE_HANDSHAKE = "Проверка протокола…"
        const val PHASE_JITTER = "Замер стабильности…"
    }
}

/** Фильтрация и сортировка списка под настройки панели. */
object ProxyFilter {
    /** Сортировка списка; избранные (если переданы) идут первыми. */
    fun sort(rows: List<ProxyRow>, mode: SortMode, favoritesFirst: Set<String> = emptySet()): List<ProxyRow> {
        val byMode: Comparator<ProxyRow> = when (mode) {
            SortMode.PING -> compareBy({ it.latencyMs }, { it.jitterMs })
            SortMode.JITTER -> compareBy({ it.jitterMs }, { it.latencyMs })
            SortMode.PROTOCOL -> compareBy({ it.protocol.ordinal }, { it.latencyMs })
            SortMode.NEWEST -> compareByDescending<ProxyRow> { it.checkedAt }.thenBy { it.latencyMs }
        }
        val comparator = if (favoritesFirst.isEmpty()) byMode else compareBy<ProxyRow> { it.url !in favoritesFirst }.then(byMode)
        return rows.sortedWith(comparator)
    }

    fun apply(rows: List<ProxyRow>, config: ScanConfig): List<ProxyRow> {
        val query = config.searchQuery.trim().lowercase()
        return rows.filter { row ->
            if (row.protocol !in config.protocolFilter) return@filter false
            if (row.latencyMs !in 1..config.maxLatencyFilterMs) return@filter false
            if (query.isEmpty()) return@filter true
            row.host.lowercase().contains(query) ||
                row.url.lowercase().contains(query) ||
                row.protocol.name.lowercase().contains(query)
        }
    }
}

/** Накопительная статистика трафика для дашборда. */
data class TrafficStats(
    val upBytes: Long = 0,
    val downBytes: Long = 0,
    val connections: Int = 0,
    val history: List<Long> = emptyList(),
) {
    val totalBytes: Long get() = upBytes + downBytes

    /**
     * Форматирует объём трафика. Locale задаём явно: в локалях с запятой в качестве
     * десятичного разделителя вывод иначе получается вида «2,0 МБ».
     */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format(java.util.Locale.ROOT, "%.2f ГБ", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format(java.util.Locale.ROOT, "%.1f МБ", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format(java.util.Locale.ROOT, "%.1f КБ", bytes / 1024.0)
        else -> "$bytes Б"
    }
}