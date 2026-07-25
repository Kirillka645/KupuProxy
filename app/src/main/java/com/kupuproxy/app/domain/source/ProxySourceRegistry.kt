package com.kupuproxy.app.domain.source

import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.data.source.MtproXyzSource
import com.kupuproxy.app.data.source.TelegramMegaSource
import com.kupuproxy.app.data.source.TelegramWebPreviewSource
import com.kupuproxy.app.data.source.UrlListProxySource
import com.kupuproxy.app.domain.model.SourceKind

object ProxySourceRegistry {

    /**
     * Встроенные источники.
     * TG: [MtproXyzSource] (hookzof → mtpro.xyz) + [TelegramMegaSource].
     */
    fun builtIn(): List<ProxySource> = listOf(
        // hookzof/socks5_list → mtpro.xyz (~50 MTProto, без t.me)
        MtproXyzSource(),
        // TG mega — скрейпы каналов + зеркала
        TelegramMegaSource(),

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
        // Отдельные каналы — off by default (mega уже покрывает)
        TelegramWebPreviewSource("ProxyMTProto", enabledByDefault = false),
        TelegramWebPreviewSource("mtprotoproxy", enabledByDefault = false),
        TelegramWebPreviewSource("KupuProxy", "TG @KupuProxy", enabledByDefault = false),
        UrlListProxySource(
            id = "paste_example_disabled",
            displayName = "Pastebin (custom)",
            urls = emptyList(),
            kind = SourceKind.HTML_PAGE,
            enabledByDefault = false
        )
    )

    fun byId(id: String): ProxySource? = builtIn().find { it.id == id }

    fun enabled(defaults: Map<String, Boolean> = emptyMap()): List<ProxySource> {
        return builtIn().filter { src ->
            defaults[src.id] ?: src.enabledByDefault
        }
    }
}
