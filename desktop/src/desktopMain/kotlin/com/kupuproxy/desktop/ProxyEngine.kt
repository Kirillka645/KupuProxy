package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.check.LatencyStats
import com.kupuproxy.shared.domain.check.LinkQuality
import com.kupuproxy.shared.domain.check.ProxyProbe
import com.kupuproxy.shared.domain.model.ProxyProtocol
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
    val parallelism: Int = 48,
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
        return scanParsed(parsed, ProxyParser.MAX_RESULTS, config, onProgress, onFound)
    }

    private suspend fun scanParsed(
        candidates: List<com.kupuproxy.shared.domain.model.RawProxyEntry>,
        inputSize: Int,
        config: ScanConfig,
        onProgress: (ScanState) -> Unit,
        onFound: (ProxyRow) -> Unit,
    ): List<ProxyRow> = withContext(Dispatchers.IO) {
        cancelled.set(false)
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

        fun emitPhase(phase: String) = onProgress(
            ScanState(
                running = true,
                processed = processed.get(),
                total = selected.size,
                found = found.get(),
                phase = phase,
                candidates = selected.size,
            ),
        )

        // Фаза 1 — TCP-префлайт по уникальным host:port.
        emitPhase("Проверка доступности…")
        val groups = selected.groupBy { "${it.protocol.name}:${it.host}:${it.port}" }
        val groupList = groups.entries.toList()
        val groupCursor = AtomicInteger(0)
        val reachable = java.util.Collections.synchronizedList(mutableListOf<List<com.kupuproxy.shared.domain.model.RawProxyEntry>>())

        coroutineScope {
            List(config.parallelism.coerceAtMost(groupList.size)) {
                async {
                    while (currentCoroutineContext().isActive && !cancelled.get()) {
                        val index = groupCursor.getAndIncrement()
                        if (index >= groupList.size) break
                        val group = groupList[index].value
                        val head = group.first()
                        if (ProxyProbe.isTcpReachable(head.host, head.port, (config.connectTimeoutMs / 2).coerceIn(500, 800))) {
                            reachable.add(group)
                        }
                        val done = processed.addAndGet(group.size)
                        if (done % 25 == 0) emitPhase("Проверка доступности…")
                    }
                }
            }.awaitAll()
        }

        if (cancelled.get() || reachable.isEmpty()) {
            onProgress(ScanState(running = false, processed = processed.get(), total = selected.size, found = found.get(), candidates = selected.size))
            return@withContext rows.toList()
        }

        // Фаза 2 — полное рукопожатие протокола.
        emitPhase("Проверка протокола…")
        val live = reachable.flatMap { it }
        val cursor = AtomicInteger(0)

        coroutineScope {
            List(config.parallelism.coerceAtMost(live.size)) {
                async {
                    while (currentCoroutineContext().isActive && !cancelled.get()) {
                        val index = cursor.getAndIncrement()
                        if (index >= live.size) break
                        val entry = live[index]

                        val result = try {
                            ProxyProbe.probe(
                                entry = entry,
                                connectTimeoutMs = config.connectTimeoutMs,
                                responseTimeoutMs = config.responseTimeoutMs,
                                samples = config.jitterSamples,
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
                            )
                            rows.add(row)
                            val count = found.incrementAndGet()
                            onFound(row)
                            if (config.stopWhenFound > 0 && count >= config.stopWhenFound) cancelled.set(true)
                        }
                        val done = processed.incrementAndGet()
                        if (done % 5 == 0 || done == selected.size) emitPhase("Проверка протокола…")
                    }
                }
            }.awaitAll()
        }

        onProgress(
            ScanState(
                running = false,
                processed = processed.get(),
                total = selected.size,
                found = found.get(),
                candidates = selected.size,
            ),
        )
        rows.toList().sortedWith(compareBy({ it.latencyMs }, { it.jitterMs }))
    }
}

/** Фильтрация и сортировка списка под настройки панели. */
object ProxyFilter {
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