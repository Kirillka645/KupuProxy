package com.kupuproxy.shared.domain.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LatencyStatsTest {

    @Test
    fun computesAverageAndJitter() {
        val stats = LatencyStats.from(listOf(100, 120, 80))
        assertEquals(100, stats.avgLatencyMs)
        assertEquals(120, stats.lastLatencyMs)
        assertEquals(40, stats.jitterMs)
        assertEquals(3, stats.samples)
    }

    @Test
    fun ignoresFailedSamples() {
        val stats = LatencyStats.from(listOf(100, 0, 200, -1))
        assertEquals(2, stats.samples)
        assertEquals(150, stats.avgLatencyMs)
    }

    @Test
    fun singleSampleHasZeroJitter() {
        val stats = LatencyStats.from(listOf(250))
        assertEquals(250, stats.avgLatencyMs)
        assertEquals(0, stats.jitterMs)
        assertEquals(1, stats.samples)
    }

    @Test
    fun emptyInputYieldsEmptyStats() {
        assertEquals(LatencyStats.Empty, LatencyStats.from(emptyList()))
        assertEquals(LatencyStats.Empty, LatencyStats.from(listOf(-1, 0)))
        assertEquals(-1, LatencyStats.Empty.lastLatencyMs)
    }

    @Test
    fun stableConnectionHasLowJitter() {
        val stats = LatencyStats.from(listOf(300, 302, 301, 299))
        assertTrue(stats.jitterMs <= 5, "expected low jitter, got ${stats.jitterMs}")
    }

    @Test
    fun computesStandardDeviation() {
        assertEquals(0.0, listOf(100).stdDev())
        assertEquals(0.0, listOf(100, 100, 100).stdDev())
        assertTrue(listOf(100, 200, 300).stdDev() > 50)
    }

    @Test
    fun mapsLatencyToQuality() {
        assertEquals(LinkQuality.EXCELLENT, LinkQuality.of(80))
        assertEquals(LinkQuality.GOOD, LinkQuality.of(300))
        assertEquals(LinkQuality.FAIR, LinkQuality.of(700))
        assertEquals(LinkQuality.POOR, LinkQuality.of(2000))
        assertEquals(LinkQuality.UNKNOWN, LinkQuality.of(-1))
        assertEquals(LinkQuality.UNKNOWN, LinkQuality.of(0))
    }

    @Test
    fun probeResultReportsDisplayLatency() {
        val ok = ProbeResult(true, com.kupuproxy.shared.domain.model.ProxyProtocol.MTPROTO, 120)
        assertEquals(120, ok.displayLatency)

        val failed = ProbeResult(false, com.kupuproxy.shared.domain.model.ProxyProtocol.MTPROTO, 1500)
        assertEquals(-1, failed.displayLatency)
    }
}