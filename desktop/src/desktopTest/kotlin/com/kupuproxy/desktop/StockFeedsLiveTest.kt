package com.kupuproxy.desktop

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Живая проверка встроенных источников по сети. По умолчанию пропускается;
 * запуск: `KUPU_LIVE=1 ./gradlew :desktop:desktopTest --tests '*StockFeedsLiveTest*'`.
 */
class StockFeedsLiveTest {

    private val enabled = System.getenv("KUPU_LIVE") == "1"

    @Test
    fun loadsEveryFeedFromNetworkAndFindsWorkingProxies() {
        if (!enabled) return
        val loaded = StockFeeds.all.map { StockFeeds.load(it) }
        loaded.forEach { println("[live] ${it.feed.id}: ${it.entries.size} via ${it.origin} ${it.error ?: ""}") }
        assertTrue(loaded.all { it.entries.isNotEmpty() })

        val sample = loaded.flatMap { it.entries.take(60) }
        val rows = runBlocking {
            ProxyEngine().scanEntries(sample, ScanConfig(jitterSamples = 1, maxToCheck = sample.size))
        }
        println("[live] working: ${rows.size} of ${sample.size}; top: ${rows.take(3).map { "${it.protocol} ${it.label} ${it.latencyMs}ms" }}")
    }
}
