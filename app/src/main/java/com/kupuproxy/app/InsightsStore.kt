package com.kupuproxy.app

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class ProxyObservation(val url: String, val ok: Boolean, val pingMs: Int)

data class ProxyInsight(
    val url: String,
    val successes: Int,
    val failures: Int,
    val lastPingMs: Int,
    val lastCheckedAt: Long,
    val lastOkAt: Long,
) {
    val reliability: Int
        get() = ((successes * 100.0) / (successes + failures).coerceAtLeast(1)).toInt()
}

data class SourceInsight(val name: String, val lastCount: Int, val lastScanAt: Long)

object InsightsStore {
    private const val FILE_NAME = "scan_insights.json"
    private const val MAX_PROXY_INSIGHTS = 3_000

    @Synchronized
    fun record(
        context: Context,
        observations: List<ProxyObservation>,
        sourceHits: Map<String, Int> = emptyMap(),
        now: Long = System.currentTimeMillis(),
    ) {
        val proxies = loadProxyInsights(context).associateByTo(linkedMapOf(), ProxyInsight::url)
        observations.distinctBy(ProxyObservation::url).forEach { observation ->
            val old = proxies[observation.url]
            proxies[observation.url] =
                ProxyInsight(
                    url = observation.url,
                    successes = (old?.successes ?: 0) + if (observation.ok) 1 else 0,
                    failures = (old?.failures ?: 0) + if (observation.ok) 0 else 1,
                    lastPingMs = if (observation.ok) observation.pingMs else old?.lastPingMs ?: -1,
                    lastCheckedAt = now,
                    lastOkAt = if (observation.ok) now else old?.lastOkAt ?: 0,
                )
        }
        val sources = loadSourceInsights(context).associateByTo(linkedMapOf(), SourceInsight::name)
        sourceHits.forEach { (name, count) -> sources[name] = SourceInsight(name, count, now) }
        write(
            context,
            proxies.values.sortedByDescending(ProxyInsight::lastCheckedAt).take(MAX_PROXY_INSIGHTS),
            sources.values.sortedByDescending(SourceInsight::lastScanAt).take(200),
        )
    }

    fun loadProxyInsights(context: Context): List<ProxyInsight> =
        read(context).first

    fun loadSourceInsights(context: Context): List<SourceInsight> =
        read(context).second

    fun topReliable(context: Context, limit: Int = 100): List<ProxyInsight> =
        loadProxyInsights(context)
            .filter { it.successes > 0 }
            .sortedWith(
                compareByDescending<ProxyInsight> { it.reliability }
                    .thenByDescending { it.successes }
                    .thenBy { if (it.lastPingMs > 0) it.lastPingMs else Int.MAX_VALUE }
            )
            .take(limit.coerceIn(1, 500))

    private fun file(context: Context) = File(ProxyCache.cacheDir(context), FILE_NAME)

    private fun read(context: Context): Pair<List<ProxyInsight>, List<SourceInsight>> {
        return try {
            val file = file(context)
            if (!file.exists()) emptyList<ProxyInsight>() to emptyList()
            else {
            val root = JSONObject(file.readText())
            val proxiesArray = root.optJSONArray("proxies") ?: JSONArray()
            val sourcesArray = root.optJSONArray("sources") ?: JSONArray()
            val proxies = buildList {
                for (index in 0 until minOf(proxiesArray.length(), MAX_PROXY_INSIGHTS)) {
                    val item = proxiesArray.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    if (url.isBlank()) continue
                    add(
                        ProxyInsight(
                            url = url,
                            successes = item.optInt("successes").coerceAtLeast(0),
                            failures = item.optInt("failures").coerceAtLeast(0),
                            lastPingMs = item.optInt("lastPingMs", -1),
                            lastCheckedAt = item.optLong("lastCheckedAt"),
                            lastOkAt = item.optLong("lastOkAt"),
                        )
                    )
                }
            }
            val sources = buildList {
                for (index in 0 until minOf(sourcesArray.length(), 200)) {
                    val item = sourcesArray.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    if (name.isBlank()) continue
                    add(SourceInsight(name, item.optInt("lastCount"), item.optLong("lastScanAt")))
                }
            }
            proxies to sources
            }
        } catch (_: Exception) {
            emptyList<ProxyInsight>() to emptyList()
        }
    }

    private fun write(
        context: Context,
        proxies: List<ProxyInsight>,
        sources: List<SourceInsight>,
    ) {
        try {
            val proxyArray = JSONArray()
            proxies.forEach { item ->
                proxyArray.put(
                    JSONObject()
                        .put("url", item.url)
                        .put("successes", item.successes)
                        .put("failures", item.failures)
                        .put("lastPingMs", item.lastPingMs)
                        .put("lastCheckedAt", item.lastCheckedAt)
                        .put("lastOkAt", item.lastOkAt)
                )
            }
            val sourceArray = JSONArray()
            sources.forEach { item ->
                sourceArray.put(
                    JSONObject()
                        .put("name", item.name)
                        .put("lastCount", item.lastCount)
                        .put("lastScanAt", item.lastScanAt)
                )
            }
            val target = file(context)
            val temporary = File(target.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(JSONObject().put("proxies", proxyArray).put("sources", sourceArray).toString())
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (_: Exception) {
        }
    }
}
