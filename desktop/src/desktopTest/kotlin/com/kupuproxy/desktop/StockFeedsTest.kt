package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockFeedsTest {

    @Test
    fun everyStockFeedIsBundledAndParsable() {
        StockFeeds.all.forEach { feed ->
            val text = assertNotNull(StockFeeds.bundledText(feed), "нет снимка ${feed.file} в ресурсах")
            val entries = StockFeeds.parse(feed, text)
            assertTrue(entries.isNotEmpty(), "${feed.file}: ни одной записи")
            assertTrue(entries.all { it.protocol == feed.protocol }, "${feed.file}: чужой протокол")
            assertTrue(entries.size <= feed.limit)
        }
        assertNotNull(StockFeeds.bundledSnapshotDate())
    }

    @Test
    fun gradleBundleListMatchesStockFeeds() {
        val script = File("build.gradle.kts").readText()
        val declared = Regex("""val stockFeedFiles = listOf\(([^)]*)\)""").find(script)!!.groupValues[1]
            .split(',').map { it.trim().trim('"') }.filter(String::isNotEmpty).toSet()
        assertEquals(StockFeeds.all.map { it.file }.toSet(), declared)
    }

    @Test
    fun bareHostPortLinesBecomeSocksLinks() {
        val text = "1.2.3.4:1080\n# comment\nsocks5://5.6.7.8:1081\nbad line"
        val normalized = StockFeeds.normalizeBareHostPort(text, ProxyProtocol.SOCKS5)
        assertEquals("socks5://1.2.3.4:1080\n# comment\nsocks5://5.6.7.8:1081\nbad line", normalized)
        val feed = StockFeeds.byId("hookzof_socks5")!!
        val entries = StockFeeds.parse(feed, text)
        assertEquals(setOf("1.2.3.4", "5.6.7.8"), entries.map { it.host }.toSet())
    }

    @Test
    fun largeFeedsAreSampledToLimit() {
        val feed = StockFeeds.byId("hookzof_socks5")!!
        val text = (1..5_000).joinToString("\n") { "8.8.${it / 250}.${it % 250 + 1}:1080" }
        assertEquals(feed.limit, StockFeeds.parse(feed, text).size)
    }

    @Test
    fun prefersGithubThenCdnThenBundled() {
        val feed = StockFeeds.byId("mtproto_merged")!!
        val good = "tg://proxy?server=8.8.8.8&port=443&secret=ee" + "a".repeat(32) + "676f6f676c652e636f6d"

        val fromGithub = StockFeeds.load(feed) { url -> if ("raw.githubusercontent" in url) good else error("x") }
        assertEquals(FeedOrigin.GITHUB, fromGithub.origin)
        assertEquals(1, fromGithub.entries.size)

        val fromCdn = StockFeeds.load(feed) { url -> if ("jsdelivr" in url) good else error("blocked") }
        assertEquals(FeedOrigin.CDN, fromCdn.origin)

        val fromBundle = StockFeeds.load(feed) { error("offline") }
        assertEquals(FeedOrigin.BUNDLED, fromBundle.origin)
        assertTrue(fromBundle.entries.size > 10)
        assertNotNull(fromBundle.error)

        // Пустой ответ зеркала не должен перекрывать рабочий снимок.
        val emptyMirror = StockFeeds.load(feed) { "" }
        assertEquals(FeedOrigin.BUNDLED, emptyMirror.origin)

        val offline = StockFeeds.load(feed, offline = true) { error("must not be called") }
        assertEquals(FeedOrigin.BUNDLED, offline.origin)
        assertNull(offline.error)
    }

    @Test
    fun downloadRejectsNonHttpSchemes() {
        val error = runCatching { StockFeeds.download("file:///etc/passwd") }.exceptionOrNull()
        assertNotNull(error)
    }

    @Test
    fun readBoundedStopsOnOversizedBody() {
        val big = java.io.ByteArrayInputStream(ByteArray(2048))
        assertTrue(runCatching { StockFeeds.readBounded(big, limit = 1024) }.isFailure)
        val ok = java.io.ByteArrayInputStream(ByteArray(512))
        assertEquals(512, StockFeeds.readBounded(ok, limit = 1024).size())
    }
}
