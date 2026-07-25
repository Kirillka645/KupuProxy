package com.kupuproxy.app.domain.source

import com.kupuproxy.app.data.source.TelegramWebPreviewSource
import com.kupuproxy.app.data.source.UrlListProxySource
import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.domain.model.SourceKind

object ProxySourceRegistry {

    /** Built-in seed registry (можно дополнить remote manifest). */
    fun builtIn(): List<ProxySource> = listOf(
        UrlListProxySource(
            id = "solispirit",
            displayName = "SoliSpirit Mega",
            urls = HttpSupport.githubCdnUrls("SoliSpirit", "mtproto", "master", "all_proxies.txt")
        ),
        UrlListProxySource(
            id = "kort_all",
            displayName = "Kort All",
            urls = HttpSupport.githubCdnUrls(
                "kort0881", "telegram-proxy-collector", "main", "proxy_all.txt"
            )
        ),
        UrlListProxySource(
            id = "kort_ru",
            displayName = "Россия (Kort)",
            urls = HttpSupport.githubCdnUrls(
                "kort0881", "telegram-proxy-collector", "main", "proxy_ru.txt"
            )
        ),
        UrlListProxySource(
            id = "kort_eu",
            displayName = "Европа (Kort)",
            urls = HttpSupport.githubCdnUrls(
                "kort0881", "telegram-proxy-collector", "main", "proxy_eu.txt"
            )
        ),
        UrlListProxySource(
            id = "surfboard",
            displayName = "SurfboardV2ray",
            urls = HttpSupport.githubCdnUrls("Surfboardv2ray", "TGProto", "main", "proxies.txt") +
                HttpSupport.githubCdnUrls("Surfboardv2ray", "TGProto", "main", "proxies-tested.txt")
        ),
        UrlListProxySource(
            id = "aliilapro",
            displayName = "ALIILAPRO",
            urls = HttpSupport.githubCdnUrls("ALIILAPRO", "MTProtoProxy", "main", "mtproto.txt")
        ),
        UrlListProxySource(
            id = "argh94_scraper",
            displayName = "Argh94 Scraper",
            urls = HttpSupport.githubCdnUrls(
                "Argh94", "telegram-proxy-scraper", "main", "proxy.txt"
            )
        ),
        UrlListProxySource(
            id = "grim1313",
            displayName = "Grim1313 list",
            urls = HttpSupport.githubCdnUrls(
                "Grim1313", "mtproto-for-telegram", "master", "all_proxies.txt"
            )
        ),
        UrlListProxySource(
            id = "yagami200",
            displayName = "Yagami200 free",
            urls = HttpSupport.githubCdnUrls(
                "Yagami200", "free-mtproto-proxies", "main", "all_proxies.txt"
            ) + HttpSupport.githubCdnUrls(
                "Yagami200", "free-mtproto-proxies", "main", "proxies.json"
            )
        ),
        UrlListProxySource(
            id = "paste_example_disabled",
            displayName = "Pastebin (custom)",
            urls = emptyList(),
            kind = SourceKind.HTML_PAGE,
            enabledByDefault = false
        ),
        // TG-каналы: при блокировке t.me — зеркала (Jina / RSSHub / allorigins)
        // Держим умеренный набор, чтобы не жечь сеть при недоступности всех зеркал.
        TelegramWebPreviewSource("ProxyMTProto"),
        TelegramWebPreviewSource("mtprotoproxy"),
        TelegramWebPreviewSource("ProxyOFF"),
        TelegramWebPreviewSource("proxies_for_telegram"),
        TelegramWebPreviewSource("MTProto_proxy"),
        TelegramWebPreviewSource("FreeMTProto"),
        TelegramWebPreviewSource("KupuProxy", "TG @KupuProxy", enabledByDefault = true),
        TelegramWebPreviewSource("proxytelegram", enabledByDefault = false),
        TelegramWebPreviewSource("proxy_mtproto_list", enabledByDefault = false),
        TelegramWebPreviewSource("socks5_list", enabledByDefault = false)
    )

    fun byId(id: String): ProxySource? = builtIn().find { it.id == id }

    fun enabled(defaults: Map<String, Boolean> = emptyMap()): List<ProxySource> {
        return builtIn().filter { src ->
            defaults[src.id] ?: src.enabledByDefault
        }.filter {
            // skip empty URL list placeholders
            when (it) {
                is UrlListProxySource -> true
                else -> true
            }
        }
    }
}
