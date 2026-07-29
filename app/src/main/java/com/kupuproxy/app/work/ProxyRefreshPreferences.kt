package com.kupuproxy.app.work

import android.content.Context

object ProxyRefreshPreferences {
    private const val PREFS = "proxy_refresh"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOURS = "hours"
    private const val KEY_WIFI_ONLY = "wifi_only"

    data class Settings(
        val enabled: Boolean = true,
        val hours: Long = 3,
        val wifiOnly: Boolean = false
    )

    fun load(context: Context): Settings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            hours = prefs.getLong(KEY_HOURS, 3L).takeIf { it in setOf(3L, 6L, 12L, 24L) } ?: 3L,
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false)
        )
    }

    fun save(context: Context, settings: Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putLong(KEY_HOURS, settings.hours.takeIf { it in setOf(3L, 6L, 12L, 24L) } ?: 3L)
            .putBoolean(KEY_WIFI_ONLY, settings.wifiOnly)
            .apply()
        ProxyRescanWorker.schedule(context, settings)
    }
}
