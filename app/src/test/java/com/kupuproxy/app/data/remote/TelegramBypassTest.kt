package com.kupuproxy.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramBypassTest {

    @Test
    fun channelPreviewUrls_includeMirrorsAndDirect() {
        val urls = TelegramBypass.channelPreviewUrls("KupuProxy")
        assertTrue(urls.any { it.contains("r.jina.ai") })
        assertTrue(urls.any { it.contains("rsshub") })
        assertTrue(urls.any { it.contains("allorigins") })
        assertTrue(urls.any { it.endsWith("/s/KupuProxy") || it.contains("t.me/s/KupuProxy") })
        assertFalse(urls.any { it.contains("@@") })
    }

    @Test
    fun channelPreviewUrls_stripsAt() {
        val urls = TelegramBypass.channelPreviewUrls("@ProxyMTProto")
        assertTrue(urls.any { it.contains("ProxyMTProto") })
        assertFalse(urls.any { it.contains("%40") || it.contains("@Proxy") })
    }

    @Test
    fun looksLikeBlockedPage_detectsCloudflare() {
        assertTrue(
            HttpSupport.looksLikeBlockedPage(
                "<html>Just a moment... cf-browser-verification</html>"
            )
        )
        assertFalse(
            HttpSupport.looksLikeBlockedPage(
                """post with tg://proxy?server=1.2.3.4&port=443&secret=0123456789abcdef0123456789abcdef"""
            )
        )
    }
}
