package com.kupuproxy.desktop

import org.json.JSONObject

/** Доступное обновление десктоп-клиента. */
data class DesktopUpdate(val version: String, val releaseUrl: String)

/**
 * Проверка обновлений по `update_manifest.json` из репозитория (тот же манифест, что у
 * Android-клиента): GitHub → jsDelivr, как и для встроенных источников.
 */
object UpdateChecker {

    private val manifestUrls = listOf(
        "https://raw.githubusercontent.com/Kirillka645/KupuProxy/main/update_manifest.json",
        "https://cdn.jsdelivr.net/gh/Kirillka645/KupuProxy@main/update_manifest.json",
    )

    const val RELEASES_URL = "https://github.com/Kirillka645/KupuProxy/releases/latest"

    /** Блокирующий вызов. `null` — обновлений нет или манифест недоступен. */
    fun check(current: String = DESKTOP_VERSION): DesktopUpdate? {
        for (url in manifestUrls) {
            val body = runCatching { StockFeeds.download(url) }.getOrNull() ?: continue
            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            val version = json.optString("version").trim()
            if (version.isEmpty()) continue
            return if (isNewer(version, current)) {
                DesktopUpdate(version, json.optString("release_url").ifBlank { RELEASES_URL })
            } else {
                null
            }
        }
        return null
    }

    /** Сравнивает версии вида 1.4.0.4 / v1.4.0.4-hotfix по числовым компонентам. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
