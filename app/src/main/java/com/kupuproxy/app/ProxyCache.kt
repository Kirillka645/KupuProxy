package com.kupuproxy.app

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Долгосрочное локальное хранилище:
 * - кэш последнего успешного списка (обновляется при каждой загрузке)
 * - избранное
 * - последние рабочие прокси по профилю Wi‑Fi / LTE
 * - seed из assets (вшит в APK навсегда)
 */
object ProxyCache {
    private const val CACHE_FILE = "proxies_cache.txt"
    private const val FAVORITES = "favorites.json"
    private const val LAST_WIFI = "last_wifi.json"
    private const val LAST_MOBILE = "last_mobile.json"
    private const val SEED_ASSET = "seed_proxies.txt"

    fun cacheDir(context: Context): File =
        File(context.filesDir, "proxy_store").also { if (!it.exists()) it.mkdirs() }

    fun saveRawList(context: Context, proxies: List<String>) {
        try {
            File(cacheDir(context), CACHE_FILE).writeText(proxies.joinToString("\n"))
        } catch (_: Exception) {
        }
    }

    fun loadRawList(context: Context): List<String> {
        return try {
            val file = File(cacheDir(context), CACHE_FILE)
            if (!file.exists()) emptyList()
            else file.readLines().map { it.trim() }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadSeedFromAssets(context: Context): List<String> {
        return try {
            context.assets.open(SEED_ASSET).bufferedReader().use { reader ->
                reader.readLines().map { it.trim() }.filter { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveWorking(
        context: Context,
        profile: NetworkProfileMode,
        proxies: List<ProxyWithPing>
    ) {
        val name = if (profile == NetworkProfileMode.MOBILE) LAST_MOBILE else LAST_WIFI
        try {
            val arr = JSONArray()
            proxies.take(200).forEach { p ->
                arr.put(JSONObject().put("url", p.url).put("ping", p.pingMs))
            }
            File(cacheDir(context), name).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun loadWorking(context: Context, profile: NetworkProfileMode): List<ProxyWithPing> {
        val name = if (profile == NetworkProfileMode.MOBILE) LAST_MOBILE else LAST_WIFI
        return try {
            val file = File(cacheDir(context), name)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(ProxyWithPing(o.getString("url"), o.getInt("ping")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getFavorites(context: Context): MutableSet<String> {
        return try {
            val file = File(cacheDir(context), FAVORITES)
            if (!file.exists()) return mutableSetOf()
            val arr = JSONArray(file.readText())
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            set
        } catch (_: Exception) {
            mutableSetOf()
        }
    }

    fun saveFavorites(context: Context, favorites: Set<String>) {
        try {
            val arr = JSONArray()
            favorites.forEach { arr.put(it) }
            File(cacheDir(context), FAVORITES).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun toggleFavorite(context: Context, url: String): Boolean {
        val set = getFavorites(context)
        val added = if (set.contains(url)) {
            set.remove(url)
            false
        } else {
            set.add(url)
            true
        }
        saveFavorites(context, set)
        return added
    }

    fun isFavorite(context: Context, url: String): Boolean =
        getFavorites(context).contains(url)
}
