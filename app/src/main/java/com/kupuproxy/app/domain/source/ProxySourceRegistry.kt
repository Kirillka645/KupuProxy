package com.kupuproxy.app.domain.source

import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.data.source.KortCollectorSource
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
        KortCollectorSource(),
        KortCollectorSource(regionFilter = "ru", id = "kort_ru", displayName = "Россия (Kort)"),
        KortCollectorSource(regionFilter = "eu", id = "kort_eu", displayName = "Европа (Kort)"),
        KortCollectorSource(regionFilter = "us", id = "kort_us", displayName = "США (Kort)"),
        KortCollectorSource(regionFilter = "asia", id = "kort_asia", displayName = "Азия (Kort)"),
        UrlListProxySource(
            id = "surfboard",
            displayName = "SurfboardV2ray",
            urls = HttpSupport.githubCdnUrls("Surfboardv2ray", "TGProto", "main", "proxies.txt") +
                HttpSupport.githubCdnUrls("Surfboardv2ray", "TGProto", "main", "proxies-tested.txt")
        ),
        UrlListProxySource(
            id = "shablin_valid",
            displayName = "Shablin latency-sorted",
            urls = HttpSupport.githubCdnUrls(
                "Kirillka645", "KupuProxy", "main", "proxy-feeds/shablin_valid.txt"
            ) + HttpSupport.githubCdnUrls(
                "shablin", "mtproto-proxy", "main", "data/valid_proxy.txt"
            )
        ),
        UrlListProxySource(
            id = "kupu_mirrored",
            displayName = "Kupu mirrored feeds",
            urls = HttpSupport.githubCdnUrls(
                "Kirillka645", "KupuProxy", "main", "proxy-feeds/mtproto_merged.txt"
            ),
            enabledByDefault = false
        ),
        UrlListProxySource(
            id = "aliilapro",
            displayName = "ALIILAPRO",
            urls = HttpSupport.githubCdnUrls(
                "Kirillka645", "KupuProxy", "main", "proxy-feeds/aliilapro_mtproto.txt"
            ) + HttpSupport.githubCdnUrls("ALIILAPRO", "MTProtoProxy", "main", "mtproto.txt")
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
            id = "dubblebyte",
            displayName = "Dubblebyte free MTProto",
            urls = HttpSupport.githubCdnUrls(
                "Kirillka645", "KupuProxy", "main", "proxy-feeds/dubblebyte_all.txt"
            ) + HttpSupport.githubCdnUrls(
                "dubblebyte", "free-mtproto-proxies", "main", "all_proxies.txt"
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

    fun byId(id: String): ProxySource? = if (id == "kort_all") {
        builtIn().find { it.id == KortCollectorSource.ID }
    } else {
        builtIn().find { it.id == id }
    }

    fun backgroundRefreshSources(): List<ProxySource> {
        val ids = setOf(
            KortCollectorSource.ID,
            "solispirit",
            "shablin_valid",
            "surfboard",
            "aliilapro",
            "dubblebyte"
        )
        return builtIn().filter { it.id in ids }
    }

    fun enabled(defaults: Map<String, Boolean> = emptyMap()): List<ProxySource> {
        return builtIn().filter { src ->
            defaults[src.id] ?: src.enabledByDefault
        }
    }
}
