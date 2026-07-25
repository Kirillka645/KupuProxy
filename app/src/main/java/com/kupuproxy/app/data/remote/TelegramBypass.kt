package com.kupuproxy.app.data.remote

/**
 * Зеркала для публичных TG-каналов, когда t.me недоступен.
 * Короткий список «быстрых» URL для race + полный для fallback.
 */
object TelegramBypass {

    fun channelUsername(raw: String): String =
        raw.trim().removePrefix("@").removePrefix("https://t.me/").removePrefix("t.me/")
            .substringBefore('/').substringBefore('?')

    /** Только самые живые зеркала — для параллельного race. */
    fun channelFastUrls(username: String): List<String> {
        val u = channelUsername(username)
        if (u.isEmpty()) return emptyList()
        val enc = java.net.URLEncoder.encode("https://t.me/s/$u", "UTF-8")
        return listOf(
            "https://r.jina.ai/https://t.me/s/$u",
            "https://rsshub.app/telegram/channel/$u",
            "https://rsshub.rssforever.com/telegram/channel/$u",
            "https://api.allorigins.win/raw?url=$enc",
            "https://t.me/s/$u"
        )
    }

    fun channelPreviewUrls(username: String): List<String> {
        val u = channelUsername(username)
        if (u.isEmpty()) return emptyList()
        val encoded = java.net.URLEncoder.encode("https://t.me/s/$u", "UTF-8")
        return listOf(
            "https://r.jina.ai/https://t.me/s/$u",
            "https://r.jina.ai/http://t.me/s/$u",
            "https://rsshub.app/telegram/channel/$u",
            "https://rsshub.rssforever.com/telegram/channel/$u",
            "https://rsshub.feeded.xyz/telegram/channel/$u",
            "https://api.allorigins.win/raw?url=$encoded",
            "https://api.codetabs.com/v1/proxy?quest=https://t.me/s/$u",
            "https://telesco.pe/$u",
            "https://t.me/s/$u",
            "https://telegram.me/s/$u"
        )
    }

    /**
     * GitHub/CDN списки, которые **уже скрапят TG-каналы** (без живого t.me).
     * Это основной способ набрать 50–100+ прокси при блокировке Telegram.
     */
    fun telegramScrapedListUrls(): List<String> {
        val lists = listOf(
            Triple("Argh94", "telegram-proxy-scraper", "proxy.txt") to "main",
            Triple("SoliSpirit", "mtproto", "all_proxies.txt") to "master",
            Triple("Yagami200", "free-mtproto-proxies", "all_proxies.txt") to "main",
            Triple("Yagami200", "free-mtproto-proxies", "proxies.json") to "main",
            Triple("ALIILAPRO", "MTProtoProxy", "mtproto.txt") to "main",
            Triple("Surfboardv2ray", "TGProto", "proxies.txt") to "main",
            Triple("Surfboardv2ray", "TGProto", "proxies-tested.txt") to "main",
            Triple("kort0881", "telegram-proxy-collector", "proxy_all.txt") to "main",
            Triple("Grim1313", "mtproto-for-telegram", "all_proxies.txt") to "master",
            Triple("mheidari98", "MTProtoProxyList", "mtproto-proxy.txt") to "main"
            // hookzof: tg/mtproto.txt НЕ существует — только tg/mtproto.json → mtpro.xyz
            // (см. MtproXyzSource)
        )
        return lists.flatMap { (triple, ref) ->
            val (owner, repo, path) = triple
            // только быстрые CDN (без raw.githubusercontent первым)
            listOf(
                "https://cdn.jsdelivr.net/gh/$owner/$repo@$ref/$path",
                "https://fastly.jsdelivr.net/gh/$owner/$repo@$ref/$path",
                "https://raw.githack.com/$owner/$repo/$ref/$path"
            )
        }
    }

    /** Популярные публичные MTProto-каналы (live preview). */
    val POPULAR_CHANNELS: List<String> = listOf(
        "ProxyMTProto",
        "mtprotoproxy",
        "ProxyOFF",
        "proxies_for_telegram",
        "MTProto_proxy",
        "FreeMTProto",
        "proxytelegram",
        "proxy_mtproto_list",
        "KupuProxy",
        "TelegramProxies",
        "mtproto_proxy_list",
        "ProxiesMTProto"
    )

    fun browserHeaders(): Map<String, String> = mapOf(
        "User-Agent" to
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36 KupuProxy/1.3.3.1",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7",
        "Accept-Language" to "en-US,en;q=0.9,ru;q=0.8"
    )
}
