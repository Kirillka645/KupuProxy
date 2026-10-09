package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopFeaturesTest {

    private fun row(url: String, ping: Int, jitter: Int = 0, protocol: ProxyProtocol = ProxyProtocol.SOCKS5, at: Long = 0) =
        ProxyRow(url, "h", 1, protocol, ping, jitter, 1, source = "src", checkedAt = at)

    @Test
    fun updateVersionComparison() {
        assertTrue(UpdateChecker.isNewer("1.4.0.5", "1.4.0.4"))
        assertTrue(UpdateChecker.isNewer("v1.5", "1.4.0.4"))
        assertFalse(UpdateChecker.isNewer("1.4.0.4", "1.4.0.4"))
        assertFalse(UpdateChecker.isNewer("1.4.0.3", "1.4.0.4"))
        assertFalse(UpdateChecker.isNewer("1.4.0.4-hotfix", "1.4.0.4"))
    }

    @Test
    fun resultsSurviveRestart() {
        val file = File.createTempFile("results", ".tsv").apply { deleteOnExit() }
        val rows = listOf(
            row("tg://proxy?server=a&port=1&secret=00", 120, 4, ProxyProtocol.MTPROTO, 1000),
            row("socks5://b:2", 300, 0, ProxyProtocol.SOCKS5, 2000).copy(source = "SOCKS5\tKort"),
        )
        ResultsStore.save(rows, file)
        val loaded = ResultsStore.load(file)
        assertEquals(2, loaded.size)
        assertEquals(rows[0], loaded[0])
        assertEquals("SOCKS5 Kort", loaded[1].source)
    }

    @Test
    fun sortsFavoritesFirstThenByMode() {
        val rows = listOf(row("a", 300, 1), row("b", 100, 50), row("c", 200, 5))
        assertEquals(listOf("b", "c", "a"), ProxyFilter.sort(rows, SortMode.PING).map { it.url })
        assertEquals(listOf("a", "c", "b"), ProxyFilter.sort(rows, SortMode.JITTER).map { it.url })
        assertEquals(listOf("a", "b", "c"), ProxyFilter.sort(rows, SortMode.PING, setOf("a")).map { it.url })
    }

    @Test
    fun parsesWindowsAccentPalette() {
        val reg = "    AccentPalette    REG_BINARY    " +
            "99EBFF00" + "4CC2FF00" + "0091F800" + "0078D400" + "0067C000" + "003E9200" + "001A6800" + "F7630C00"
        val pair = assertNotNull(SystemAccent.parsePalette(reg))
        assertEquals(androidx.compose.ui.graphics.Color(0x00, 0x67, 0xC0), pair.light)
        assertEquals(androidx.compose.ui.graphics.Color(0x4C, 0xC2, 0xFF), pair.dark)
    }

    @Test
    fun settingsMapToFasterScanConfig() {
        val config = DesktopSettings(scanThreads = 96, connectTimeoutMs = 1000).toScanConfig()
        assertEquals(96, config.parallelism)
        assertEquals(288, config.preflightParallelism)
        assertEquals(1600, config.responseTimeoutMs)
    }
}
