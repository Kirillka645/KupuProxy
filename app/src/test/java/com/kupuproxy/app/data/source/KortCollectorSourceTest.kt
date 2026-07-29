package com.kupuproxy.app.data.source

import com.kupuproxy.app.domain.model.SecretType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KortCollectorSourceTest {

    private val validSecret = "ee0123456789abcdef0123456789abcdef7777772e676f6f676c652e636f6d"

    @Test
    fun parsesMtprotoMetadataAndFiltersSocks() {
        val body = """
            [
              {"type":"mtproto","host":"64.186.246.160","port":443,"secret":"$validSecret","ping":0.013,"region":"us","method":"TCP_OK","probe_resistant":true},
              {"type":"socks5","host":"8.8.8.8","port":1080,"secret":"0123456789abcdef0123456789abcdef","region":"us"}
            ]
        """.trimIndent()

        val entries = KortCollectorSource.parseVerifiedJson(body)

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("us", entry.region)
        assertEquals(13, entry.upstreamPingMs)
        assertEquals("TCP_OK", entry.verificationMethod)
        assertTrue(entry.probeResistant == true)
        assertEquals(SecretType.FAKE_TLS, entry.secretType)
        assertEquals("www.google.com", entry.sniDomain)
    }

    @Test
    fun regionFilterAndDeduplicationAreDeterministic() {
        val body = """
            [
              {"type":"mtproto","host":"8.8.8.8","port":443,"secret":"0123456789abcdef0123456789abcdef","region":"ru"},
              {"type":"mtproto","host":"8.8.8.8","port":443,"secret":"0123456789abcdef0123456789abcdef","region":"ru"},
              {"type":"mtproto","host":"1.1.1.1","port":443,"secret":"0123456789abcdef0123456789abcdef","region":"eu"}
            ]
        """.trimIndent()

        val ru = KortCollectorSource.parseVerifiedJson(body, "ru")

        assertEquals(1, ru.size)
        assertEquals("8.8.8.8", ru.single().host)
    }

    @Test
    fun rejectsPrivateAndMalformedRecords() {
        val body = """
            [
              {"type":"mtproto","host":"127.0.0.1","port":443,"secret":"0123456789abcdef0123456789abcdef","region":"ru"},
              {"type":"mtproto","host":"8.8.8.8","port":0,"secret":"short","region":"ru"}
            ]
        """.trimIndent()

        assertTrue(KortCollectorSource.parseVerifiedJson(body).isEmpty())
    }

    @Test
    fun parsesAndNormalizesStats() {
        val stats = KortCollectorSource.parseStats(
            """{"timestamp":"2026-07-29T17:44:51.808988Z","total":62328,"by_region":{"RU":119,"eu":515,"socks5":61671},"top":100}"""
        )

        assertEquals(62328, stats?.total)
        assertEquals(119, stats?.byRegion?.get("ru"))
        assertFalse(stats?.byRegion?.containsKey("socks5") == true)
        assertNull(KortCollectorSource.parseStats("""{"timestamp":"not-a-date"}"""))
    }
}
