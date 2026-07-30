package com.kupuproxy.app

import android.content.Context
import com.kupuproxy.app.data.source.KortCollectorStats
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SecretType
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

import java.io.IOException

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
    private const val KORT_SNAPSHOT = "kort_snapshot.json"
    private const val KORT_STATUS = "kort_status.json"
    private const val KORT_STALE_MS = 12L * 60 * 60 * 1000

    data class KortStatus(
        val upstreamTimestamp: String? = null,
        val refreshedAtMs: Long = 0,
        val proxyCount: Int = 0,
        val regionalCounts: Map<String, Int> = emptyMap(),
        val source: String = "none",
        val error: String? = null
    ) {
        fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
            refreshedAtMs <= 0 || nowMs - refreshedAtMs > KORT_STALE_MS
    }

    fun cacheDir(context: Context): File =
        File(context.filesDir, "proxy_store").also { if (!it.exists()) it.mkdirs() }

    fun migrateCleanup(context: Context) {
        try {
            val raw = File(cacheDir(context), CACHE_FILE)
            if (!raw.exists()) return
            val lines = raw.readLines().map { it.trim() }.filter { it.isNotBlank() }
            val cleaned = lines.filter { line ->
                !line.contains("yagami", ignoreCase = true) &&
                    !line.contains("Yagami200", ignoreCase = true)
            }
            if (cleaned.size < lines.size) {
                raw.writeText(cleaned.joinToString("\n"))
            }
        } catch (_: IOException) {
        }
    }

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

    fun saveKortSnapshot(context: Context, entries: List<RawProxyEntry>) {
        if (entries.isEmpty()) return
        val array = JSONArray()
        entries.take(15_000).forEach { entry ->
            array.put(
                JSONObject()
                    .put("url", entry.url)
                    .put("host", entry.host)
                    .put("port", entry.port)
                    .put("secret", entry.secret)
                    .put("secretType", entry.secretType.name)
                    .put("sni", entry.sniDomain)
                    .put("region", entry.region)
                    .put("upstreamPingMs", entry.upstreamPingMs)
                    .put("method", entry.verificationMethod)
                    .put("probeResistant", entry.probeResistant)
            )
        }
        atomicWrite(File(cacheDir(context), KORT_SNAPSHOT), array.toString())
    }

    fun loadKortSnapshot(context: Context, region: String? = null): List<RawProxyEntry> = try {
        val file = File(cacheDir(context), KORT_SNAPSHOT)
        if (!file.exists()) {
            emptyList()
        } else {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until minOf(array.length(), 15_000)) {
                    val obj = array.optJSONObject(index) ?: continue
                    val entryRegion = obj.optString("region").takeIf { it.isNotBlank() && it != "null" }
                    if (region != null && entryRegion != region) continue
                    val host = obj.optString("host")
                    val port = obj.optInt("port")
                    val secret = obj.optString("secret")
                    if (host.isBlank() || port !in 1..65535 || secret.isBlank()) continue
                    add(
                        RawProxyEntry(
                            url = obj.optString("url"),
                            host = host,
                            port = port,
                            secret = secret,
                            secretType = runCatching { SecretType.valueOf(obj.optString("secretType")) }
                                .getOrDefault(SecretType.UNKNOWN),
                            sniDomain = obj.optString("sni").takeIf { it.isNotBlank() && it != "null" },
                            sourceId = "kort_verified",
                            sourceName = "Kort Verified",
                            region = entryRegion,
                            upstreamPingMs = obj.optInt("upstreamPingMs").takeIf { it > 0 },
                            verificationMethod = obj.optString("method").takeIf { it.isNotBlank() && it != "null" },
                            probeResistant = if (obj.has("probeResistant") && !obj.isNull("probeResistant")) {
                                obj.optBoolean("probeResistant")
                            } else null
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveKortStatus(
        context: Context,
        stats: KortCollectorStats?,
        proxyCount: Int,
        source: String,
        error: String? = null
    ) {
        val regions = JSONObject()
        stats?.byRegion?.forEach { (key, value) -> regions.put(key, value) }
        val obj = JSONObject()
            .put("upstreamTimestamp", stats?.timestamp)
            .put("refreshedAtMs", System.currentTimeMillis())
            .put("proxyCount", proxyCount.coerceAtLeast(0))
            .put("regionalCounts", regions)
            .put("source", source.take(24))
            .put("error", error?.take(240))
        atomicWrite(File(cacheDir(context), KORT_STATUS), obj.toString())
    }

    fun loadKortStatus(context: Context): KortStatus = try {
        val file = File(cacheDir(context), KORT_STATUS)
        if (!file.exists()) {
            KortStatus()
        } else {
            val obj = JSONObject(file.readText())
            val regionsObj = obj.optJSONObject("regionalCounts") ?: JSONObject()
            val regions = buildMap {
                regionsObj.keys().forEach { key -> put(key, regionsObj.optInt(key).coerceAtLeast(0)) }
            }
            KortStatus(
                upstreamTimestamp = obj.optString("upstreamTimestamp").takeIf { it.isNotBlank() && it != "null" },
                refreshedAtMs = obj.optLong("refreshedAtMs"),
                proxyCount = obj.optInt("proxyCount").coerceAtLeast(0),
                regionalCounts = regions,
                source = obj.optString("source", "none"),
                error = obj.optString("error").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    } catch (_: Exception) {
        KortStatus()
    }

    private fun atomicWrite(target: File, content: String) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        val backup = File(target.parentFile, "${target.name}.bak")
        try {
            temporary.writeText(content)
            if (backup.exists()) backup.delete()
            if (target.exists() && !target.renameTo(backup)) {
                temporary.delete()
                return
            }
            if (!temporary.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                return
            }
            backup.delete()
        } catch (_: Exception) {
            temporary.delete()
            if (!target.exists() && backup.exists()) backup.renameTo(target)
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
