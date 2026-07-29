package com.kupuproxy.app.data.remote

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeUrlPolicyTest {

    @Test
    fun acceptsPublicHttpsHost() {
        val result = SafeUrlPolicy.validateHttpsUrl("https://example.com/proxies.txt") {
            arrayOf(InetAddress.getByName("8.8.8.8"))
        }

        assertEquals("https://example.com/proxies.txt", result.getOrThrow())
    }

    @Test
    fun rejectsNonHttpsAndEmbeddedCredentials() {
        val resolver: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByName("8.8.8.8"))
        }

        assertTrue(SafeUrlPolicy.validateHttpsUrl("http://example.com/list", resolver).isFailure)
        assertTrue(SafeUrlPolicy.validateHttpsUrl("https://user:pass@example.com/list", resolver).isFailure)
        assertTrue(SafeUrlPolicy.validateHttpsUrl("https://example.com:8443/list", resolver).isFailure)
    }

    @Test
    fun rejectsLocalAndReservedIpv4Ranges() {
        val blocked = listOf(
            "127.0.0.1",
            "10.0.0.1",
            "169.254.1.1",
            "172.16.0.1",
            "192.168.1.1",
            "100.64.0.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1"
        )

        blocked.forEach { raw ->
            assertTrue("Expected $raw to be blocked", SafeUrlPolicy.isBlockedAddress(InetAddress.getByName(raw)))
        }
    }

    @Test
    fun rejectsPrivateAndDocumentationIpv6Ranges() {
        val blocked = listOf("::1", "fc00::1", "fd00::1", "fe80::1", "2001:db8::1")

        blocked.forEach { raw ->
            assertTrue("Expected $raw to be blocked", SafeUrlPolicy.isBlockedAddress(InetAddress.getByName(raw)))
        }
    }

    @Test
    fun rejectsDnsRebindingToPrivateAddress() {
        val result = SafeUrlPolicy.validateHttpsUrl("https://source.example/proxies") {
            arrayOf(InetAddress.getByName("192.168.0.10"))
        }

        assertTrue(result.isFailure)
    }
}
