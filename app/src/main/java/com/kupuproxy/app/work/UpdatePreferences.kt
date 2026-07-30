package com.kupuproxy.app.work

import android.content.Context

/** Настройки автопроверки обновлений приложения. */
object UpdatePreferences {
    private const val PREFS = "kupu_updates"
    private const val KEY_AUTO_CHECK = "auto_check"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_NOTIFIED_TAG = "notified_tag"

    fun isAutoCheckEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CHECK, true)

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        UpdateCheckWorker.schedule(context, enabled)
    }

    fun lastCheck(context: Context): Long = prefs(context).getLong(KEY_LAST_CHECK, 0L)

    fun saveLastCheck(context: Context, timestamp: Long) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, timestamp).apply()
    }

    /** Не спамим одним и тем же тегом больше одного раза. */
    fun shouldNotifyFor(context: Context, tag: String): Boolean =
        prefs(context).getString(KEY_NOTIFIED_TAG, null) != tag

    fun markNotified(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_NOTIFIED_TAG, tag).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
