package com.kupuproxy.app.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyParserTest {

    @Test
    fun parseTgLink() {
        val body = "tg://proxy?server=1.2.3.4&port=443&secret=0123456789abcdef0123456789abcdef"
        val r = ProxyParser.parse(body)
        assertEquals(1, r.size)
        assertEquals("1.2.3.4", r[0].host)
        assertEquals(443, r[0].port)
    }

    @Test
    fun parseTmeLink() {
        val body = "https://t.me/proxy?server=a.example.com&port=8443&secret=dd0123456789abcdef0123456789abcdef"
        val r = ProxyParser.parseLinks(body)
        assertEquals(1, r.size)
        assertTrue(r[0].url.startsWith("tg://proxy?"))
    }

    @Test
    fun parseJsonArray() {
        val json = """
            [
              {"server":"9.9.9.9","port":443,"secret":"0123456789abcdef0123456789abcdef"},
              {"host":"8.8.8.8","port":"8443","password":"ee0123456789abcdef0123456789abcdef77777777777777777777777777777777"}
            ]
        """.trimIndent()
        val r = ProxyParser.parseJson(json)
        assertEquals(2, r.size)
    }

    @Test
    fun parseQueryTripleWithoutScheme() {
        val body = """data server=1.2.3.4&port=443&secret=dd0123456789abcdef0123456789abcdef junk"""
        val r = ProxyParser.parse(body)
        assertEquals(1, r.size)
        assertEquals("1.2.3.4", r[0].host)
    }

    @Test
    fun parseAmpEscapedHtmlLink() {
        val body =
            """href="tg://proxy?server=9.9.9.9&amp;port=8443&amp;secret=0123456789abcdef0123456789abcdef""""
        val r = ProxyParser.parse(body)
        assertEquals(1, r.size)
        assertEquals(8443, r[0].port)
    }

    @Test
    fun parseHostPortSecretLines() {
        val text = """
            # comment
            1.1.1.1:443:0123456789abcdef0123456789abcdef
            2.2.2.2 8443 dd0123456789abcdef0123456789abcdef
            garbage
        """.trimIndent()
        val r = ProxyParser.parseLineFormat(text)
        assertTrue(r.size >= 2)
    }

    @Test
    fun rejectGarbage() {
        assertTrue(ProxyParser.parse("hello world\nno proxies here").isEmpty())
        assertTrue(ProxyParser.parseJson("{not json").isEmpty())
        assertTrue(ProxyParser.parseLineFormat(":::").isEmpty())
    }

    @Test
    fun classifySecrets() {
        assertEquals(
            com.kupuproxy.app.domain.model.SecretType.PLAIN,
            ProxyParser.classifySecret("0123456789abcdef0123456789abcdef")
        )
        assertEquals(
            com.kupuproxy.app.domain.model.SecretType.PADDED,
            ProxyParser.classifySecret("dd0123456789abcdef0123456789abcdef")
        )
        assertEquals(
            com.kupuproxy.app.domain.model.SecretType.FAKE_TLS,
            ProxyParser.classifySecret("ee0123456789abcdef0123456789abcdef")
        )
    }

    @Test
    fun privateIpFilter() {
        assertTrue(ProxyParser.isPrivateOrReservedHost("192.168.0.1"))
        assertTrue(ProxyParser.isPrivateOrReservedHost("10.0.0.5"))
        assertTrue(ProxyParser.isPrivateOrReservedHost("127.0.0.1"))
        assertFalse(ProxyParser.isPrivateOrReservedHost("8.8.8.8"))
    }

    @Test
    fun dedupeViaAggregatorKeys() {
        val a = ProxyParser.parse(
            "tg://proxy?server=1.1.1.1&port=443&secret=0123456789abcdef0123456789abcdef"
        )
        val b = ProxyParser.parse(
            "https://t.me/proxy?server=1.1.1.1&port=443&secret=0123456789ABCDEF0123456789ABCDEF"
        )
        val all = (a + b).distinctBy { "${it.host}:${it.port}:${it.secret.lowercase()}" }
        assertEquals(1, all.size)
    }

    @Test
    fun parseMarkdownTable() {
        val md = """
            | host | port | secret |
            | --- | --- | --- |
            | 3.3.3.3 | 443 | 0123456789abcdef0123456789abcdef |
        """.trimIndent()
        val r = ProxyParser.parseMarkdownTables(md)
        assertTrue(r.isNotEmpty())
    }
}
