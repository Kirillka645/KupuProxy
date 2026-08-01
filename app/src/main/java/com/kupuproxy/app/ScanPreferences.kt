package com.kupuproxy.app

import android.content.Context

enum class ScanMode { QUICK, BALANCED, FULL, CUSTOM }

data class ScanConfiguration(
    val mode: ScanMode = ScanMode.BALANCED,
    val customLimit: Int = 5_000,
    val customWorkers: Int = 48,
)

object ScanPreferences {
    private const val PREFS = "scan_configuration"
    private const val KEY_MODE = "mode"
    private const val KEY_LIMIT = "custom_limit"
    private const val KEY_WORKERS = "custom_workers"

    fun load(context: Context): ScanConfiguration {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ScanConfiguration(
            mode = ScanMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) }
                ?: ScanMode.BALANCED,
            customLimit = prefs.getInt(KEY_LIMIT, 5_000).coerceIn(100, MAX_SCAN_PROXIES),
            customWorkers = prefs.getInt(KEY_WORKERS, 48).coerceIn(16, 96),
        )
    }

    fun save(context: Context, configuration: ScanConfiguration) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, configuration.mode.name)
            .putInt(KEY_LIMIT, configuration.customLimit.coerceIn(100, MAX_SCAN_PROXIES))
            .putInt(KEY_WORKERS, configuration.customWorkers.coerceIn(16, 96))
            .apply()
    }

    fun apply(base: ProfileSettings, configuration: ScanConfiguration): ProfileSettings =
        when (configuration.mode) {
            ScanMode.QUICK -> base.copy(maxToCheck = 500, batchSize = minOf(base.batchSize, 64), stopWhenFound = 25)
            ScanMode.BALANCED -> base.copy(maxToCheck = 3_000, stopWhenFound = 0)
            ScanMode.FULL -> base.copy(maxToCheck = MAX_SCAN_PROXIES, stopWhenFound = 0)
            ScanMode.CUSTOM ->
                base.copy(
                    maxToCheck = configuration.customLimit.coerceIn(100, MAX_SCAN_PROXIES),
                    batchSize = configuration.customWorkers.coerceIn(16, 96),
                    stopWhenFound = 0,
                )
        }
}
