package com.kupuproxy.shared.domain.parser

import com.kupuproxy.shared.domain.model.ProxyProtocol
import com.kupuproxy.shared.domain.model.SecretType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyParserTest {

    private val secretHex = "d41d8cd98f00b204e9800998ecf8427e"

    // hex("www.google.com")
    private val secretFakeTls = "ee" + secretHex + "7777772e676f6f676c652e636f6d"

    // region MTProto

    @Test
    fun parsesTgProxyLink() {
        val entry = ProxyParser.fromUrl("tg://proxy?server=1.2.3.4&port=443&secret=$secretHex")
        assertNotNull(entry)
        assertEquals(ProxyProtocol.MTPROTO, entry.protocol)
        assertEquals("1.2.3.4", entry.host)
        assertEquals(443, entry.port)
        assertEquals(SecretType.PLAIN, entry.secretType)
    }

    @Test
    fun parsesTmeWebLink() {
        val entry = ProxyParser.fromUrl("https://t.me/proxy?server=proxy.example.org&port=1080&secret=$secretHex")
        assertNotNull(entry)
        assertEquals(ProxyProtocol.MTPROTO, entry.protocol)
        assertEquals("proxy.example.org", entry.host)
        assertEquals(1080, entry.port)
    }

    @Test
    fun detectsPaddedAndFakeTlsSecrets() {
        val padded = assertNotNull(ProxyParser.fromUrl("tg://proxy?server=1.1.1.1&port=80&secret=dd$secretHex"))
        assertEquals(SecretType.PADDED, padded.secretType)

        val fake = assertNotNull(ProxyParser.fromUrl("tg://proxy?server=1.1.1.1&port=443&secret=$secretFakeTls"))
        assertEquals(SecretType.FAKE_TLS, fake.secretType)
        assertEquals("www.google.com", fake.sniDomain)
    }

    @Test
    fun rejectsShortOrInvalidSecrets() {
        assertNull(ProxyParser.fromUrl("tg://proxy?server=1.2.3.4&port=443&secret=deadbeef"))
        assertNull(ProxyParser.fromUrl("tg://proxy?server=1.2.3.4&port=0&secret=$secretHex"))
        assertNull(ProxyParser.fromUrl("tg://proxy?server=1.2.3.4&port=443"))
    }

    // endregion

    // region SOCKS5

    @Test
    fun parsesSocks5WithCredentials() {
        val entry = ProxyParser.fromUrl("socks5://user:pass@1.2.3.4:1080")
        assertNotNull(entry)
        assertEquals(ProxyProtocol.SOCKS5, entry.protocol)
        assertEquals("1.2.3.4", entry.host)
        assertEquals(1080, entry.port)
        assertEquals("user", entry.username)
        assertEquals("pass", entry.password)
    }

    @Test
    fun parsesSocks5WithoutCredentials() {
        val entry = ProxyParser.fromUrl("socks5://1.2.3.4:1080")
        assertNotNull(entry)
        assertEquals(ProxyProtocol.SOCKS5, entry.protocol)
        assertNull(entry.username)
    }

    @Test
    fun parsesTelegramSocksLinkAsSocksNotMtproto() {
        val entry = ProxyParser.fromUrl("tg://socks?server=1.2.3.4&port=1080")
        assertNotNull(entry)
        assertEquals(ProxyProtocol.SOCKS5, entry.protocol)
        assertEquals("1.2.3.4", entry.host)
        assertEquals(1080, entry.port)
    }

    @Test
    fun parsesSocks5WithIpv6Host() {
        val entry = ProxyParser.fromUrl("socks5://[2001:db8::1]:1080")
        assertNotNull(entry)
        assertEquals("2001:db8::1", entry.host)
        assertEquals(1080, entry.port)
    }

    // endregion

    // region HTTP / WEB

    @Test
    fun parsesHttpProxyWithAuth() {
        val entry = ProxyParser.fromUrl("http://alice:secret@proxy.example.com:8080")
        assertNotNull(entry)
        assertEquals(ProxyProtocol.HTTP, entry.protocol)
        assertEquals("proxy.example.com", entry.host)
        assertEquals(8080, entry.port)
        assertEquals("alice", entry.username)
        assertEquals("secret", entry.password)
    }

    @Test
    fun parsesWebProxyAsTlsHttp() {
        val entry = ProxyParser.fromUrl("https://user:pw@secure.example.com:8443")
        assertNotNull(entry)
        assertEquals(ProxyProtocol.WEB, entry.protocol)
        assertEquals("secure.example.com", entry.host)
        assertEquals(8443, entry.port)
    }

    @Test
    fun encodesCredentialsInGeneratedUrl() {
        val entry = ProxyParser.fromUrl("socks5://user:p@ss@1.2.3.4:1080")
        assertNotNull(entry)
        // '@' внутри пароля обязан быть экранирован, иначе адрес нечитаем.
        assertTrue(entry.url.contains("%40"), "expected escaped @ in ${entry.url}")
        assertTrue(entry.url.startsWith("socks5://"))
    }

    // endregion

    // region Смешанные документы

    @Test
    fun parsesMixedDocumentWithAllProtocols() {
        val body = buildString {
            appendLine("tg://proxy?server=1.1.1.1&port=443&secret=$secretHex")
            appendLine("socks5://user:pass@2.2.2.2:1080")
            appendLine("http://u:p@3.3.3.3:8080")
            appendLine("https://u:p@4.4.4.4:8443")
        }
        val parsed = ProxyParser.parse(body)
        assertEquals(4, parsed.size)
        assertEquals(
            setOf(
                ProxyProtocol.MTPROTO,
                ProxyProtocol.SOCKS5,
                ProxyProtocol.HTTP,
                ProxyProtocol.WEB,
            ),
            parsed.map { it.protocol }.toSet(),
        )
    }

    @Test
    fun parsesHostPortSecretLines() {
        val parsed = ProxyParser.parse("5.6.7.8:443:$secretHex")
        assertEquals(1, parsed.size)
        assertEquals("5.6.7.8", parsed[0].host)
        assertEquals(ProxyProtocol.MTPROTO, parsed[0].protocol)
    }

    @Test
    fun parsesYamlSocksBlock() {
        val body = """
            - type: socks5
              server: 7.7.7.7
              port: 1080
              username: bob
              password: hunter2
        """.trimIndent()
        val parsed = ProxyParser.parse(body)
        assertEquals(1, parsed.size)
        assertEquals(ProxyProtocol.SOCKS5, parsed[0].protocol)
        assertEquals("bob", parsed[0].username)
        assertEquals("hunter2", parsed[0].password)
    }

    @Test
    fun parsesJsonProxyObjects() {
        val body = """
            {"proxies":[
              {"type":"socks5","host":"9.9.9.9","port":1080,"username":"a","password":"b"},
              {"url":"tg://proxy?server=8.8.8.8&port=443&secret=$secretHex"}
            ]}
        """.trimIndent()
        val parsed = ProxyParser.parse(body)
        assertEquals(2, parsed.size)
        // Порядок не фиксируем: ссылки из текста разбираются раньше JSON-объектов.
        val byProtocol = parsed.associateBy { it.protocol }
        assertEquals("9.9.9.9", byProtocol[ProxyProtocol.SOCKS5]?.host)
        assertEquals("8.8.8.8", byProtocol[ProxyProtocol.MTPROTO]?.host)
    }

    @Test
    fun parsesHtmlCodeBlocks() {
        val body = "<code>tg://proxy?server=6.6.6.6&port=443&secret=$secretHex</code>"
        val parsed = ProxyParser.parse(body)
        assertEquals(1, parsed.size)
        assertEquals("6.6.6.6", parsed[0].host)
    }

    @Test
    fun parsesBase64EncodedList() {
        val inner = "tg://proxy?server=5.5.5.5&port=443&secret=$secretHex"
        val encoded = java.util.Base64.getEncoder().encodeToString(inner.toByteArray())
        val parsed = ProxyParser.parse(encoded)
        assertEquals(1, parsed.size)
        assertEquals("5.5.5.5", parsed[0].host)
    }

    @Test
    fun unescapesHtmlEntities() {
        val body = "tg://proxy?server=1.2.3.4&amp;port=443&amp;secret=$secretHex"
        assertEquals(1, ProxyParser.parse(body).size)
    }

    // endregion

    // region Фильтрация и дедупликация

    @Test
    fun dropsPrivateAndReservedHosts() {
        val body = listOf(
            "192.168.1.1", "10.0.0.5", "127.0.0.1", "169.254.1.1", "localhost",
        ).map { "$it:443:$secretHex" }.joinToString("\n")
        assertTrue(ProxyParser.parse(body).isEmpty(), "private hosts must be filtered")
    }

    @Test
    fun deduplicatesSameEndpointWithSameSecret() {
        val body = """
            tg://proxy?server=1.1.1.1&port=443&secret=$secretHex
            tg://proxy?server=1.1.1.1&port=443&secret=$secretHex
        """.trimIndent()
        assertEquals(1, ProxyParser.parse(body).size)
    }

    @Test
    fun keepsSameHostWithDifferentSecrets() {
        val other = "aa1d8cd98f00b204e9800998ecf8427e"
        val body = """
            tg://proxy?server=1.1.1.1&port=443&secret=$secretHex
            tg://proxy?server=1.1.1.1&port=443&secret=$other
        """.trimIndent()
        assertEquals(2, ProxyParser.parse(body).size)
    }

    @Test
    fun treatsSameHostDifferentProtocolAsDistinct() {
        val body = """
            socks5://1.1.1.1:443
            http://1.1.1.1:443
            https://1.1.1.1:443
        """.trimIndent()
        assertEquals(3, ProxyParser.parse(body).size)
    }

    @Test
    fun skipsCommentsAndBlankLines() {
        val body = """
            # comment
            // another

            tg://proxy?server=2.2.2.2&port=443&secret=$secretHex
        """.trimIndent()
        assertEquals(1, ProxyParser.parse(body).size)
    }

    @Test
    fun handlesEmptyAndGarbageInput() {
        assertTrue(ProxyParser.parse("").isEmpty())
        assertTrue(ProxyParser.parse("   ").isEmpty())
        assertTrue(ProxyParser.parse("just some random text without proxies").isEmpty())
    }

    @Test
    fun respectsResultCap() {
        val body = (1..(ProxyParser.MAX_RESULTS + 500)).joinToString("\n") { index ->
            "${index % 254 + 1}.${index % 253 + 1}.${index % 251 + 1}.${index % 249 + 1}:443:$secretHex"
        }
        assertTrue(ProxyParser.parse(body).size <= ProxyParser.MAX_RESULTS)
    }

    // endregion

    // region Утилиты

    @Test
    fun splitsHostPortVariants() {
        assertEquals("1.2.3.4" to 8080, ProxyParser.splitHostPort("1.2.3.4:8080"))
        assertEquals("host.tld" to 443, ProxyParser.splitHostPort("host.tld:443"))
        assertEquals("::1" to 1, ProxyParser.splitHostPort("[::1]:1"))
        assertNull(ProxyParser.splitHostPort("1.2.3.4"))
        assertNull(ProxyParser.splitHostPort("1.2.3.4:abc"))
    }

    @Test
    fun validatesPorts() {
        assertTrue(ProxyParser.isValidPort(1))
        assertTrue(ProxyParser.isValidPort(65535))
        assertTrue(!ProxyParser.isValidPort(0))
        assertTrue(!ProxyParser.isValidPort(65536))
    }

    @Test
    fun detectsReservedHosts() {
        assertTrue(ProxyParser.isPrivateOrReservedHost("192.168.0.1"))
        assertTrue(ProxyParser.isPrivateOrReservedHost("10.1.2.3"))
        assertTrue(ProxyParser.isPrivateOrReservedHost("169.254.1.1"))
        assertTrue(ProxyParser.isPrivateOrReservedHost("172.16.0.1"))
        assertTrue(ProxyParser.isPrivateOrReservedHost("localhost"))
        assertTrue(ProxyParser.isPrivateOrReservedHost("fd00::1"))
        assertTrue(!ProxyParser.isPrivateOrReservedHost("8.8.8.8"))
        assertTrue(!ProxyParser.isPrivateOrReservedHost("proxy.example.com"))
    }

    @Test
    fun roundTripsGeneratedUrls() {
        val original = "tg://proxy?server=1.2.3.4&port=443&secret=$secretHex"
        val reparsed = assertNotNull(ProxyParser.fromUrl(original))
        assertEquals("1.2.3.4", reparsed.host)
        assertEquals(443, reparsed.port)
        assertEquals(secretHex, reparsed.secret)
    }

    // endregion

    @Test
    fun acceptsShortBase64Secrets() {
        // Реальные записи из proxy-feeds/mtproto_merged.txt: base64url-секреты короче 32 символов.
        val body = listOf(
            "tg://proxy?server=161.0.16.189&port=443&secret=7miqLUFWdxDDUaK14c3hRjl2ay5jb20",
            "tg://proxy?server=194.120.230.106&port=443&secret=3XnnAQIAAQAH8AMDhuJMOt0",
        ).joinToString("\n")
        val entries = ProxyParser.parse(body)
        assertEquals(2, entries.size)
        val fakeTls = entries.first { it.host == "161.0.16.189" }
        assertEquals(SecretType.FAKE_TLS, fakeTls.secretType)
        assertEquals("vk.com", fakeTls.sniDomain)
        assertEquals(SecretType.PADDED, entries.first { it.host == "194.120.230.106" }.secretType)
    }

    @Test
    fun rejectsTooShortSecrets() {
        assertFalse(ProxyParser.looksLikeSecret("abc"))
        assertFalse(ProxyParser.looksLikeSecret("QUJDREVGR0hJSktMTU5P")) // 15 байт
        assertTrue(ProxyParser.looksLikeSecret("QUJDREVGR0hJSktMTU5PUA")) // 16 байт
    }
}
