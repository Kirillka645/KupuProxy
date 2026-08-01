package com.kupuproxy.app

import android.content.Context

data class HomeSourceLayout(val order: List<String>, val hidden: Set<String>)

object HomeLayoutPreferences {
    val defaultOrder = listOf("solispirit", "shablin_valid", "dubblebyte", "surfboard", "argh94_scraper")
    private const val PREFS = "home_layout"
    private const val KEY_ORDER = "order"
    private const val KEY_HIDDEN = "hidden"

    fun load(context: Context): HomeSourceLayout {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ORDER, null)?.split(',')?.filter { it in defaultOrder }.orEmpty()
        return HomeSourceLayout(
            order = (saved + defaultOrder).distinct(),
            hidden = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().filterTo(mutableSetOf()) { it in defaultOrder },
        )
    }

    fun save(context: Context, layout: HomeSourceLayout) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, layout.order.filter { it in defaultOrder }.distinct().joinToString(","))
            .putStringSet(KEY_HIDDEN, layout.hidden.filterTo(mutableSetOf()) { it in defaultOrder })
            .apply()
    }
}
