package com.kupuproxy.app.domain.source

/** Locale-neutral identities for built-in sources and names stored by older app versions. */
object BuiltInSourceIdentity {
    private val aliases: Map<String, Set<String>> =
        mapOf(
            "mtpro_xyz" to setOf("MTPro.XYZ (hookzof)"),
            "tg_mega" to setOf("Telegram · mega"),
            "solispirit" to setOf("SoliSpirit Mega"),
            "kort_verified" to setOf("Kort Verified"),
            "kort_ru" to setOf("kort_verified_ru", "RU (Kort)", "Kort Verified RU"),
            "kort_eu" to setOf("kort_verified_eu", "EU (Kort)", "Kort Verified EU"),
            "kort_us" to setOf("kort_verified_us", "US (Kort)", "Kort Verified US"),
            "kort_asia" to setOf("kort_verified_asia", "Asia (Kort)", "Kort Verified ASIA"),
            "surfboard" to setOf("SurfboardV2ray"),
            "shablin_valid" to setOf("Shablin latency", "Shablin latency-sorted"),
            "kupu_mirrored" to setOf("Kupu mirrored feeds"),
            "aliilapro" to setOf("ALIILAPRO"),
            "argh94_scraper" to setOf("Argh94 Scraper"),
            "grim1313" to setOf("Grim1313 list"),
            "dubblebyte" to setOf("Dubblebyte free MTProto"),
            "paste_example_disabled" to setOf("Pastebin (custom)"),
        )

    fun canonicalId(value: String): String? {
        val candidate = value.trim()
        return aliases.entries.firstOrNull { (id, names) ->
            id.equals(candidate, ignoreCase = true) ||
                names.any { it.equals(candidate, ignoreCase = true) }
        }?.key
    }

    fun insightKey(sourceId: String, displayName: String): String =
        canonicalId(sourceId) ?: canonicalId(displayName) ?: displayName
}
