package com.kupuproxy.shared.domain.parser

import com.kupuproxy.shared.domain.model.ProxyProtocol
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Deterministic, offline robustness/property checks; safe to run on JVM and Android. */
class ProxyParserRobustnessTest {
    @Test
    fun seededMalformedDocumentsNeverThrowOrProduceInvalidEntries() {
        val random = Random(645)
        val alphabet = "abcXYZ0123456789:/?&=%[]{}<>\\\"'@#\n\r\u0000\uFFFD"
        val prefixes = listOf("", "tg://proxy?server=", "socks5://", "https://", "{\"proxies\":[", "<code>")
        repeat(500) {
            val body = prefixes.random(random) + buildString {
                repeat(random.nextInt(1, 512)) { append(alphabet.random(random)) }
            }
            val entries = ProxyParser.parse(body)
            assertTrue(entries.size <= ProxyParser.MAX_RESULTS)
            for (entry in entries) {
                assertTrue(ProxyParser.isValidPort(entry.port))
                assertTrue(entry.host.isNotBlank())
                assertFalse(ProxyParser.isPrivateOrReservedHost(entry.host))
            }
        }
    }

    @Test
    fun authorityUrlsRoundTripAcrossProtocolsPortsAndCredentials() {
        val random = Random(2026)
        for ((scheme, protocol) in listOf("socks5" to ProxyProtocol.SOCKS5, "http" to ProxyProtocol.HTTP, "https" to ProxyProtocol.WEB)) {
            repeat(75) {
                val host = "8.9.${random.nextInt(1, 255)}.${random.nextInt(1, 255)}"
                val port = random.nextInt(1, 65536)
                val parsed = assertNotNull(ProxyParser.fromUrl("$scheme://user:p%40ss@$host:$port"))
                val roundTrip = assertNotNull(ProxyParser.fromUrl(parsed.url))
                assertEquals(protocol, roundTrip.protocol)
                assertEquals(host, roundTrip.host)
                assertEquals(port, roundTrip.port)
                assertEquals("user", roundTrip.username)
                assertEquals("p@ss", roundTrip.password)
            }
        }
    }

    @Test
    fun ignoresProxyAfterInputBudget() {
        val prefix = "# ignored\n".repeat(ProxyParser.MAX_INPUT_CHARS / 10 + 1)
        assertTrue(ProxyParser.parse(prefix + "\nsocks5://8.8.8.8:1080").isEmpty())
    }

    @Test
    fun appliesPrivateHostFilterToEveryTransport() {
        val hosts = listOf("127.0.0.1", "10.1.2.3", "192.168.1.1", "169.254.169.254", "localhost", "[::1]", "[fd00::1]")
        for (host in hosts) {
            for (scheme in listOf("socks5", "http", "https")) {
                assertTrue(ProxyParser.parse("$scheme://$host:1080").isEmpty(), "$scheme $host")
            }
        }
    }
}
