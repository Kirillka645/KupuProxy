package com.kupuproxy.shared.domain.check

import com.kupuproxy.shared.domain.parser.ProxyParser
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ProxyProbeSkipPreflightTest {

    private fun deadPort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun preflightRejectsDeadEndpoint() {
        val entry = ProxyParser.fromUrl("socks5://127.0.0.1:${deadPort()}")!!
        val result = ProxyProbe.probe(entry, 500, 500)
        assertFalse(result.ok)
        assertEquals("tcp_unreachable", result.error)
    }

    @Test
    fun skipPreflightGoesStraightToHandshake() {
        val entry = ProxyParser.fromUrl("socks5://127.0.0.1:${deadPort()}")!!
        val result = ProxyProbe.probe(entry, 500, 500, skipPreflight = true)
        assertFalse(result.ok)
        assertNotEquals("tcp_unreachable", result.error)
    }
}
