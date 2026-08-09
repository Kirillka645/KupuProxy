package com.kupuproxy.app.ui

import androidx.annotation.StringRes
import com.kupuproxy.app.R
import com.kupuproxy.app.domain.source.BuiltInSourceIdentity

/** Maps stable built-in source ids and legacy display names to localized UI resources. */
@StringRes
internal fun sourceNameResource(value: String): Int? =
    when (BuiltInSourceIdentity.canonicalId(value)) {
        "mtpro_xyz" -> R.string.source_mtpro_xyz_title
        "tg_mega" -> R.string.source_telegram_mega_title
        "solispirit" -> R.string.source_solispirit_title
        "kort_verified" -> R.string.source_kort_verified_title
        "kort_ru" -> R.string.source_kort_ru_title
        "kort_eu" -> R.string.source_kort_eu_title
        "kort_us" -> R.string.source_kort_us_title
        "kort_asia" -> R.string.source_kort_asia_title
        "surfboard" -> R.string.source_surfboard_title
        "shablin_valid" -> R.string.source_shablin_title
        "kupu_mirrored" -> R.string.source_kupu_mirrored_title
        "aliilapro" -> R.string.source_aliilapro_title
        "argh94_scraper" -> R.string.source_argh94_title
        "grim1313" -> R.string.source_grim1313_title
        "dubblebyte" -> R.string.source_dubblebyte_title
        "paste_example_disabled" -> R.string.source_pastebin_title
        else -> null
    }
