package com.kupuproxy.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object ProxyManager {

    private const val MAX_PROXIES = 15_000

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Источники + зеркала (jsDelivr / githack / ghproxy) — если raw.githubusercontent
     * режется на мобильной сети, подхватываем CDN.
     */
    data class Source(
        val id: String,
        val name: String,
        val description: String,
        val urls: List<String>,
        val region: String = "ALL" // RU, EU, ALL
    )

    val SOURCES: List<Source> = listOf(
        Source(
            id = "solispirit",
            name = "SoliSpirit Mega",
            description = "~250+ авто-обновляемых MTProto",
            urls = listOf(
                "https://fastly.jsdelivr.net/gh/SoliSpirit/mtproto@master/all_proxies.txt",
                "https://gcore.jsdelivr.net/gh/SoliSpirit/mtproto@master/all_proxies.txt",
                "https://raw.githack.com/SoliSpirit/mtproto/master/all_proxies.txt",
                "https://rawcdn.githack.com/SoliSpirit/mtproto/master/all_proxies.txt",
                "https://ghproxy.net/https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt",
                "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt"
            )
        ),
        Source(
            id = "kort_all",
            name = "Kort All",
            description = "RU + EU комбайн",
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/kort0881/telegram-proxy-collector@main/proxy_all.txt",
                "https://fastly.jsdelivr.net/gh/kort0881/telegram-proxy-collector@main/proxy_all.txt",
                "https://raw.githack.com/kort0881/telegram-proxy-collector/main/proxy_all.txt",
                "https://ghproxy.net/https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_all.txt",
                "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_all.txt"
            )
        ),
        Source(
            id = "kort_ru",
            name = "Россия (Kort)",
            description = "Маскировка под RU-сервисы",
            region = "RU",
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/kort0881/telegram-proxy-collector@main/proxy_ru.txt",
                "https://fastly.jsdelivr.net/gh/kort0881/telegram-proxy-collector@main/proxy_ru.txt",
                "https://raw.githack.com/kort0881/telegram-proxy-collector/main/proxy_ru.txt",
                "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_ru.txt"
            )
        ),
        Source(
            id = "kort_eu",
            name = "Европа (Kort)",
            description = "Маскировка под Google/CF",
            region = "EU",
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/kort0881/telegram-proxy-collector@main/proxy_eu.txt",
                "https://fastly.jsdelivr.net/gh/kort0881/telegram-proxy-collector@main/proxy_eu.txt",
                "https://raw.githack.com/kort0881/telegram-proxy-collector/main/proxy_eu.txt",
                "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_eu.txt"
            )
        ),
        Source(
            id = "surfboard",
            name = "SurfboardV2ray",
            description = "Большой список + tested",
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGProto@main/proxies.txt",
                "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGProto@main/proxies-tested.txt",
                "https://fastly.jsdelivr.net/gh/Surfboardv2ray/TGProto@main/proxies.txt",
                "https://raw.githack.com/Surfboardv2ray/TGProto/main/proxies.txt",
                "https://raw.githubusercontent.com/Surfboardv2ray/TGProto/main/proxies-tested.txt"
            )
        ),
        Source(
            id = "aliilapro",
            name = "ALIILAPRO",
            description = "Ежедневный список",
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/ALIILAPRO/MTProtoProxy@main/mtproto.txt",
                "https://fastly.jsdelivr.net/gh/ALIILAPRO/MTProtoProxy@main/mtproto.txt",
                "https://raw.githack.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt",
                "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt"
            )
        ),
        Source(
            id = "argh94_scraper",
            name = "Argh94 Scraper",
            description = "Парсер каналов · 300+",
            urls = listOf(
                "https://raw.githubusercontent.com/Argh94/telegram-proxy-scraper/main/proxy.txt",
                "https://cdn.jsdelivr.net/gh/Argh94/telegram-proxy-scraper@main/proxy.txt",
                "https://raw.githack.com/Argh94/telegram-proxy-scraper/main/proxy.txt",
                "https://ghproxy.net/https://raw.githubusercontent.com/Argh94/telegram-proxy-scraper/main/proxy.txt"
            )
        ),
        Source(
            id = "grim1313",
            name = "Grim1313 list",
            description = "Sync SoliSpirit / fork",
            urls = listOf(
                "https://raw.githack.com/Grim1313/mtproto-for-telegram/master/all_proxies.txt",
                "https://cdn.jsdelivr.net/gh/Grim1313/mtproto-for-telegram@master/all_proxies.txt",
                "https://raw.githubusercontent.com/Grim1313/mtproto-for-telegram/master/all_proxies.txt"
            )
        )
    )

    private val LINK_REGEX = Regex(
        """(?:tg://(?:proxy|socks)|https?://t\.me/(?:proxy|socks))\?[^\s<>"'`)\]#,]+""",
        RegexOption.IGNORE_CASE
    )

    suspend fun fetchSource(source: Source): Pair<List<String>, String?> =
        withContext(Dispatchers.IO) {
            for (url in source.urls) {
                try {
                    val body = downloadText(url) ?: continue
                    val parsed = parseProxyLinks(body)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed to url
                    }
                } catch (_: Exception) {
                }
            }
            emptyList<String>() to null
        }

    suspend fun fetchAllSources(
        context: Context? = null,
        onProgress: (sourceIndex: Int, total: Int, name: String, count: Int) -> Unit = { _, _, _, _ -> }
    ): FetchResult = withContext(Dispatchers.IO) {
        val all = LinkedHashSet<String>()
        val hits = linkedMapOf<String, Int>()
        val mirrors = mutableListOf<String>()

        SOURCES.forEachIndexed { index, source ->
            val (list, mirror) = fetchSource(source)
            hits[source.name] = list.size
            all.addAll(list)
            if (mirror != null) mirrors.add("${source.name} ← $mirror")
            onProgress(index + 1, SOURCES.size, source.name, list.size)
        }

        var fromCache = false
        var fromSeed = false

        if (all.size < 50 && context != null) {
            val cached = ProxyCache.loadRawList(context)
            if (cached.isNotEmpty()) {
                all.addAll(cached)
                fromCache = true
            }
        }

        if (all.size < 50 && context != null) {
            val seed = ProxyCache.loadSeedFromAssets(context)
            if (seed.isNotEmpty()) {
                all.addAll(seed)
                fromSeed = true
            }
        }

        val unique = deduplicateProxies(all.toList())
        if (context != null && unique.isNotEmpty()) {
            ProxyCache.saveRawList(context, unique)
        }

        FetchResult(
            proxies = unique,
            sourceHits = hits,
            usedMirrors = mirrors,
            fromCache = fromCache,
            fromSeed = fromSeed
        )
    }

    suspend fun fetchSourceById(sourceId: String, context: Context? = null): List<String> {
        val source = SOURCES.find { it.id == sourceId } ?: return emptyList()
        val (list, _) = fetchSource(source)
        if (list.isNotEmpty()) {
            context?.let { ProxyCache.saveRawList(it, list) }
            return deduplicateProxies(list)
        }
        // fallback cascade
        context?.let {
            val cache = ProxyCache.loadRawList(it)
            if (cache.isNotEmpty()) return cache
            val seed = ProxyCache.loadSeedFromAssets(it)
            if (seed.isNotEmpty()) return seed
        }
        return emptyList()
    }

    private fun downloadText(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "KupuProxy/1.1 Android")
            .header("Accept", "text/plain,*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    fun parseProxyLinks(body: String): List<String> {
        val result = LinkedHashSet<String>()
        LINK_REGEX.findAll(body).forEach { match ->
            val raw = match.value.trim().trimEnd(')', ']', ',', ';', '"', '\'')
            val normalized = convertToTgFormat(raw)
            if (normalized.startsWith("tg://proxy?") || normalized.startsWith("tg://socks?")) {
                result.add(normalized)
            }
        }
        // also accept plain server=... lines if any
        body.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.startsWith("tg://proxy?") || t.startsWith("tg://socks?")) {
                result.add(t)
            }
        }
        return result.toList().take(MAX_PROXIES)
    }

    private fun convertToTgFormat(url: String): String {
        return when {
            url.startsWith("https://t.me/proxy?", ignoreCase = true) ->
                "tg://proxy?" + url.substringAfter("?")
            url.startsWith("http://t.me/proxy?", ignoreCase = true) ->
                "tg://proxy?" + url.substringAfter("?")
            url.startsWith("https://t.me/socks?", ignoreCase = true) ->
                "tg://socks?" + url.substringAfter("?")
            url.startsWith("http://t.me/socks?", ignoreCase = true) ->
                "tg://socks?" + url.substringAfter("?")
            else -> url
        }
    }

    fun deduplicateProxies(proxies: List<String>): List<String> =
        proxies.distinctBy { normalizeProxyKey(it) }

    fun normalizeProxyKey(url: String): String {
        return try {
            val paramsPart = when {
                url.startsWith("tg://proxy?") -> url.removePrefix("tg://proxy?")
                url.startsWith("tg://socks?") -> url.removePrefix("tg://socks?")
                url.startsWith("https://t.me/proxy?") -> url.substringAfter("?")
                url.startsWith("https://t.me/socks?") -> url.substringAfter("?")
                else -> return url
            }

            var server = ""
            var port = ""
            var secret = ""

            paramsPart.split("&").forEach { param ->
                when {
                    param.startsWith("server=") -> server = param.substringAfter("=")
                    param.startsWith("port=") -> port = param.substringAfter("=")
                    param.startsWith("secret=") -> {
                        val clean = param.substringAfter("=").split("&", "#", "@").first()
                        if (clean.isNotEmpty()) secret = clean
                    }
                }
            }

            when {
                server.isNotEmpty() && port.isNotEmpty() && secret.isNotEmpty() ->
                    "$server:$port:$secret"
                server.isNotEmpty() && port.isNotEmpty() -> "$server:$port"
                else -> url
            }
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Для LTE берём перемешанную выборку до maxToCheck,
     * для Wi‑Fi — до maxToCheck без жёсткого shuffle приоритета.
     */
    fun prepareForProfile(proxies: List<String>, settings: ProfileSettings): List<String> {
        val unique = deduplicateProxies(proxies)
        if (unique.size <= settings.maxToCheck) return unique
        return if (settings.mode == NetworkProfileMode.MOBILE) {
            unique.shuffled().take(settings.maxToCheck)
        } else {
            unique.take(settings.maxToCheck)
        }
    }

    /**
     * Полная проверка «как Telegram» (fast) + live callback.
     * [onFound] вызывается на Main при каждом рабочем прокси.
     */
    suspend fun checkProxiesPingParallel(
        proxies: List<String>,
        settings: ProfileSettings,
        profileLabel: String = settings.label,
        onProgress: (processed: Int, total: Int, working: Int) -> Unit,
        onFound: (ProxyWithPing) -> Unit = {}
    ): List<ProxyWithPing> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ProxyWithPing>()
        val mutex = Mutex()
        val total = proxies.size
        var processed = 0
        var working = 0
        var stop = false

        val concurrency = settings.batchSize.coerceIn(16, 64)
        val connectMs = settings.connectTimeoutMs.coerceIn(800, 2500)
        val responseMs = (settings.connectTimeoutMs + 800).coerceIn(1200, 3500)
        val stopAt = settings.stopWhenFound
        val semaphore = Semaphore(concurrency)

        val jobs = proxies.map { proxyUrl ->
            async {
                if (stop) {
                    mutex.withLock { processed++ }
                    return@async
                }
                if (proxyUrl.contains("socks?", ignoreCase = true)) {
                    mutex.withLock {
                        processed++
                        val p = processed
                        val w = working
                        withContext(Dispatchers.Main) { onProgress(p, total, w) }
                    }
                    return@async
                }

                semaphore.withPermit {
                    if (stop) {
                        mutex.withLock { processed++ }
                        return@withPermit
                    }

                    val result = try {
                        MtprotoChecker.checkUrl(proxyUrl, connectMs, responseMs)
                    } catch (_: Exception) {
                        MtprotoChecker.CheckResult(false, -1, "error")
                    }

                    val item = if (
                        result.ok &&
                        result.rttMs in 1 until settings.maxPingMs.coerceAtLeast(5000)
                    ) {
                        ProxyWithPing(
                            url = proxyUrl,
                            pingMs = result.rttMs,
                            profileLabel = profileLabel,
                            status = ProxyStatus.AVAILABLE,
                            statusText = "Доступен"
                        )
                    } else null

                    val (p, w, found) = mutex.withLock {
                        processed++
                        if (item != null) {
                            results.add(item)
                            working++
                            if (stopAt > 0 && working >= stopAt) {
                                stop = true
                            }
                        }
                        Triple(processed, working, item)
                    }
                    withContext(Dispatchers.Main) {
                        onProgress(p, total, w)
                        if (found != null) onFound(found)
                    }
                }
            }
        }

        jobs.awaitAll()
        results.sortedBy { it.pingMs }
    }

    fun parseProxyUrl(url: String): ProxyInfo? {
        return try {
            val cleanUrl = when {
                url.startsWith("tg://proxy?") -> url.removePrefix("tg://proxy?")
                url.startsWith("tg://socks?") -> url.removePrefix("tg://socks?")
                url.contains("t.me/proxy?") -> url.substringAfter("?")
                url.contains("t.me/socks?") -> url.substringAfter("?")
                else -> return null
            }

            var server = ""
            var port = ""
            cleanUrl.split("&").forEach { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    when (parts[0]) {
                        "server" -> server = parts[1]
                        "port" -> port = parts[1]
                    }
                }
            }

            if (server.isNotEmpty() && port.isNotEmpty()) ProxyInfo(server, port) else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveProxiesToFile(proxies: List<String>): File? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "kupuproxy_$timestamp.txt"
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { output ->
                proxies.forEach { proxy ->
                    output.write((proxy + "\n").toByteArray())
                }
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    /** Сохраняет и в Downloads, и в вечный app-cache. */
    suspend fun saveProxiesEverywhere(context: Context, proxies: List<String>): File? {
        ProxyCache.saveRawList(context, proxies)
        return saveProxiesToFile(proxies)
    }

    suspend fun loadProxiesFromFile(contentResolver: ContentResolver, uri: Uri): List<String> =
        withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val body = input.bufferedReader().readText()
                    parseProxyLinks(body).ifEmpty {
                        body.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(MAX_PROXIES)
                            .toList()
                    }
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
}
