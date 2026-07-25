package com.kupuproxy.app.data.remote

/**
 * Когда t.me / Telegram заблокированы, веб-превью канала недоступно напрямую.
 * Собираем зеркала и прокси-ридеры, через которые часто всё ещё читается HTML/RSS/markdown.
 *
 * Порядок: сначала «лёгкие» ридеры (jina), потом RSSHub, потом generic CORS/proxy, в конце прямой t.me.
 */
object TelegramBypass {

    fun channelPreviewUrls(username: String): List<String> {
        val u = username.trim().removePrefix("@")
        if (u.isEmpty()) return emptyList()
        val encoded = java.net.URLEncoder.encode("https://t.me/s/$u", "UTF-8")
        return listOf(
            // AI reader mirrors — часто работают при блокировке t.me
            "https://r.jina.ai/https://t.me/s/$u",
            "https://r.jina.ai/http://t.me/s/$u",
            // Public RSSHub instances (telegram channel → RSS/XML with message text)
            "https://rsshub.app/telegram/channel/$u",
            "https://rsshub.rssforever.com/telegram/channel/$u",
            "https://rsshub.feeded.xyz/telegram/channel/$u",
            // Generic page proxies
            "https://api.allorigins.win/raw?url=$encoded",
            "https://api.codetabs.com/v1/proxy?quest=https://t.me/s/$u",
            // Archive / third-party web views
            "https://telesco.pe/$u",
            // Direct (works only if t.me is reachable)
            "https://t.me/s/$u",
            "https://telegram.me/s/$u"
        )
    }

    /** Browser-like headers help some CDNs and anti-bot on proxies. */
    fun browserHeaders(): Map<String, String> = mapOf(
        "User-Agent" to
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36 KupuProxy/1.3.2",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7",
        "Accept-Language" to "en-US,en;q=0.9,ru;q=0.8"
    )
}
