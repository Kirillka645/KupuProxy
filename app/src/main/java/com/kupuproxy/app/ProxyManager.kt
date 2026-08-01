package com.kupuproxy.app

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.data.source.KortCollectorSource
import com.kupuproxy.app.data.source.RemoteManifestLoader
import com.kupuproxy.app.data.source.UserCustomSourceStore
import com.kupuproxy.app.domain.aggregator.ProxyAggregator
import com.kupuproxy.app.domain.model.SourceResult
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import com.kupuproxy.app.domain.source.ProxySourceRegistry
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

object ProxyManager {

    private const val MAX_PROXIES = MAX_SCAN_PROXIES

    private val client: OkHttpClient = HttpSupport.fastClient()

    suspend fun fetchAllSources(
        context: Context? = null,
        onProgress: (sourceIndex: Int, total: Int, name: String, count: Int) -> Unit =
            { _, _, _, _ ->
            },
    ): FetchResult =
        withContext(Dispatchers.IO) {
            val all = LinkedHashSet<String>()
            val hits = linkedMapOf<String, Int>()
            val mirrors = mutableListOf<String>()

            val registry = availableSources(context)
            val done = AtomicInteger(0)
            val total = registry.size
            val aggregate =
                ProxyAggregator(
                        client = client,
                        parallelism = 10,
                        timeoutMs = 12_000L,
                        maxAttempts = 1,
                    )
                    .collect(registry) { result ->
                    val count =
                        when (result) {
                            is SourceResult.Success -> result.entries.size
                            is SourceResult.Failure -> 0
                        }
                    val index = done.incrementAndGet()
                    synchronized(hits) { hits[result.displayName] = count }
                    if (result is SourceResult.Success) {
                        synchronized(all) { all.addAll(result.entries.map { it.url }) }
                        if (
                            result.sourceId == KortCollectorSource.ID &&
                                context != null &&
                                result.entries.isNotEmpty()
                        ) {
                            ProxyCache.saveKortSnapshot(context, result.entries)
                        }
                    }
                    onProgress(index, total, result.displayName, count)
                }
            all.addAll(aggregate.proxies.map { it.url })
            if (context != null) {
                val kortEntries =
                    aggregate.sourceResults
                        .filterIsInstance<SourceResult.Success>()
                        .firstOrNull { it.sourceId == KortCollectorSource.ID }
                        ?.entries
                        .orEmpty()
                if (kortEntries.isNotEmpty()) {
                    val stats = runCatching { KortCollectorSource.fetchStats(client) }.getOrNull()
                    ProxyCache.saveKortStatus(context, stats, kortEntries.size, source = "network")
                }
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
                fromSeed = fromSeed,
            )
        }

    private suspend fun availableSources(context: Context?): List<ProxySource> {
        val builtIn = ProxySourceRegistry.builtIn().filter { it.enabledByDefault }
        val remote =
            try {
                RemoteManifestLoader.loadExtraSources(client)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        val custom =
            try {
                context?.let { UserCustomSourceStore(it).allEnabledSources() }.orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        return (builtIn + remote + custom).distinctBy { it.id }
    }

    suspend fun fetchSourceById(sourceId: String, context: Context? = null): List<String> {
        val source =
            ProxySourceRegistry.byId(sourceId)
                ?: availableSources(context).find { it.id == sourceId }
        val entries =
            if (source != null) {
                try {
                    source.fetch(client)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        if (
            entries.isNotEmpty() &&
                context != null &&
                (sourceId == KortCollectorSource.ID || sourceId == "kort_all")
        ) {
            ProxyCache.saveKortSnapshot(context, entries)
            val stats = runCatching { KortCollectorSource.fetchStats(client) }.getOrNull()
            ProxyCache.saveKortStatus(context, stats, entries.size, source = "network")
        }
        val list = entries.map { it.url }
        /* Source network errors fall through to collector/cache/seed fallback below. */
        if (list.isNotEmpty()) {
            val unique = deduplicateProxies(list)
            context?.let { ProxyCache.saveRawList(it, unique) }
            return unique
        }
        context?.let {
            if (sourceId == KortCollectorSource.ID || sourceId.startsWith("kort_")) {
                val region =
                    sourceId.removePrefix("kort_").takeIf {
                        it in KortCollectorSource.SUPPORTED_REGIONS
                    }
                ProxyCache.loadKortSnapshot(it, region)
                    .map { entry -> entry.url }
                    .takeIf(List<String>::isNotEmpty)
                    ?.let { cached ->
                        return cached
                    }
            }
            ProxyCache.loadRawList(it).takeIf(List<String>::isNotEmpty)?.let { cached ->
                return cached
            }
            ProxyCache.loadSeedFromAssets(it).takeIf(List<String>::isNotEmpty)?.let { seed ->
                return seed
            }
        }
        return emptyList()
    }

    fun parseProxyLinks(body: String): List<String> =
        ProxyParser.parse(body).asSequence().map { it.url }.distinct().take(MAX_PROXIES).toList()

    fun deduplicateProxies(proxies: List<String>): List<String> = proxies.distinctBy {
        normalizeProxyKey(it)
    }

    fun normalizeProxyKey(url: String): String {
        return try {
            val paramsPart =
                when {
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
                val separator = param.indexOf('=')
                if (separator <= 0) return@forEach
                val key = param.substring(0, separator).lowercase(Locale.US)
                val raw = param.substring(separator + 1).substringBefore('#').substringBefore('@')
                val value = runCatching {
                    URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
                }
                    .getOrDefault(raw)
                when (key) {
                    "server",
                    "host",
                    "ip" -> if (server.isEmpty()) server = value
                    "port" -> if (port.isEmpty()) port = value
                    "secret",
                    "password" -> if (secret.isEmpty()) secret = value
                }
            }

            server = server.trim().trim('[', ']').lowercase(Locale.US)
            secret = secret.trim().lowercase(Locale.US)
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
     * Для LTE берём перемешанную выборку до maxToCheck, для Wi‑Fi — до maxToCheck без жёсткого
     * shuffle приоритета.
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
     * Полная проверка «как Telegram» (fast) + live callback. [onFound] вызывается на Main при
     * каждом рабочем прокси.
     */
    suspend fun checkProxiesPingParallel(
        proxies: List<String>,
        settings: ProfileSettings,
        profileLabel: String = settings.label,
        onProgress: (processed: Int, total: Int, working: Int) -> Unit,
        onFound: (ProxyWithPing) -> Unit = {},
        onChecked: (ProxyObservation) -> Unit = {},
    ): List<ProxyWithPing> =
        withContext(Dispatchers.IO) {
            if (proxies.isEmpty()) return@withContext emptyList()

            val total = proxies.size
            val concurrency = settings.batchSize.coerceIn(16, 96).coerceAtMost(total)
            val connectMs = settings.connectTimeoutMs.coerceIn(700, 1800)
            val responseMs = (settings.connectTimeoutMs + 600).coerceIn(1100, 2400)
            val stopAt = settings.stopWhenFound
            val cursor = AtomicInteger(0)
            val processed = AtomicInteger(0)
            val working = AtomicInteger(0)
            val stopped = AtomicBoolean(false)
            val results = java.util.Collections.synchronizedList(mutableListOf<ProxyWithPing>())

            // A large collector often contains several secrets for the same dead host. Probe each
            // host:port only once with a short TCP timeout, then spend the heavier Telegram
            // handshake only on endpoints that accepted a connection.
            val endpointGroups = linkedMapOf<String, MutableList<String>>()
            proxies.forEach { url ->
                val parsed = if (url.contains("socks?", ignoreCase = true)) null
                    else MtprotoChecker.parseProxy(url)
                if (parsed == null) {
                    val done = processed.incrementAndGet()
                    onChecked(ProxyObservation(url, ok = false, pingMs = -1))
                    if (done == total || done % 16 == 0) {
                        withContext(Dispatchers.Main) { onProgress(done, total, working.get()) }
                    }
                } else {
                    val key = "${parsed.host.lowercase()}:${parsed.port}"
                    endpointGroups.getOrPut(key) { mutableListOf() }.add(url)
                }
            }

            val reachable = java.util.Collections.synchronizedList(mutableListOf<String>())
            val endpointCursor = AtomicInteger(0)
            val endpoints = endpointGroups.entries.toList()
            val probeTimeoutMs = (connectMs / 2).coerceIn(500, 800)
            coroutineScope {
                List(minOf(128, endpoints.size)) {
                        async {
                            while (currentCoroutineContext().isActive) {
                                val index = endpointCursor.getAndIncrement()
                                if (index >= endpoints.size) break
                                val (_, urls) = endpoints[index]
                                val parsed = MtprotoChecker.parseProxy(urls.first())
                                val online =
                                    parsed != null &&
                                        MtprotoChecker.isTcpReachable(
                                            parsed.host,
                                            parsed.port,
                                            probeTimeoutMs,
                                        )
                                if (online) {
                                    reachable.addAll(urls)
                                } else {
                                    urls.forEach { url ->
                                        val done = processed.incrementAndGet()
                                        onChecked(ProxyObservation(url, ok = false, pingMs = -1))
                                        if (done == total || done % 16 == 0) {
                                            withContext(Dispatchers.Main) {
                                                onProgress(done, total, working.get())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .awaitAll()
            }

            if (reachable.isEmpty()) {
                withContext(Dispatchers.Main) { onProgress(total, total, 0) }
                return@withContext emptyList()
            }

            coroutineScope {
                List(concurrency) {
                        async {
                            while (currentCoroutineContext().isActive && !stopped.get()) {
                                val index = cursor.getAndIncrement()
                                if (index >= reachable.size) break
                                val proxyUrl = reachable[index]

                                val item =
                                    if (proxyUrl.contains("socks?", ignoreCase = true)) {
                                        null
                                    } else {
                                        val result =
                                            try {
                                                MtprotoChecker.checkUrl(
                                                    proxyUrl,
                                                    connectMs,
                                                    responseMs,
                                                )
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                MtprotoChecker.CheckResult(false, -1, "error")
                                            }
                                        if (
                                            result.ok &&
                                                result.rttMs in
                                                    1 until settings.maxPingMs.coerceAtLeast(5000)
                                        ) {
                                            ProxyWithPing(
                                                url = proxyUrl,
                                                pingMs = result.rttMs,
                                                profileLabel = profileLabel,
                                                status = ProxyStatus.AVAILABLE,
                                                statusText = "Доступен",
                                            )
                                        } else null
                                    }

                                if (item != null) {
                                    results.add(item)
                                    val foundCount = working.incrementAndGet()
                                    if (stopAt > 0 && foundCount >= stopAt) stopped.set(true)
                                }
                                val processedCount = processed.incrementAndGet()
                                val workingCount = working.get()
                                onChecked(
                                    ProxyObservation(
                                        url = proxyUrl,
                                        ok = item != null,
                                        pingMs = item?.pingMs ?: -1,
                                    )
                                )
                                if (item != null || processedCount == total || processedCount % 8 == 0) {
                                    withContext(Dispatchers.Main) {
                                        onProgress(processedCount, total, workingCount)
                                        if (item != null) onFound(item)
                                    }
                                }
                            }
                        }
                    }
                    .awaitAll()
            }
            results.toList().sortedBy { it.pingMs }
        }

    fun parseProxyUrl(url: String): ProxyInfo? {
        return try {
            val cleanUrl =
                when {
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

    private suspend fun saveProxiesToFile(context: Context, proxies: List<String>): File? =
        withContext(Dispatchers.IO) {
            try {
                val timestamp =
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "kupuproxy_$timestamp.txt"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values =
                        ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                    val resolver = context.contentResolver
                    val uri =
                        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: return@withContext null
                    try {
                        resolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                            writer ->
                            proxies.forEach { writer.append(it).append('\n') }
                        } ?: error("Не удалось открыть файл")
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        File(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS
                            ),
                            fileName,
                        )
                    } catch (error: Exception) {
                        resolver.delete(uri, null, null)
                        throw error
                    }
                } else {
                    val downloadsDir =
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        )
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val file = File(downloadsDir, fileName)
                    FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { writer ->
                        proxies.forEach { writer.append(it).append('\n') }
                    }
                    file
                }
            } catch (_: Exception) {
                null
            }
        }

    /** Сохраняет список в кэш приложения и Downloads через MediaStore. */
    suspend fun saveProxiesEverywhere(context: Context, proxies: List<String>): File? {
        ProxyCache.saveRawList(context, proxies)
        return saveProxiesToFile(context, proxies)
    }

    suspend fun loadProxiesFromFile(contentResolver: ContentResolver, uri: Uri): List<String> =
        withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val body =
                        input.bufferedReader().use { reader ->
                            val buffer = CharArray(16 * 1024)
                            val output = StringBuilder()
                            while (output.length < ProxyParser.MAX_INPUT_CHARS) {
                                val remaining = ProxyParser.MAX_INPUT_CHARS - output.length
                                val read = reader.read(buffer, 0, minOf(buffer.size, remaining))
                                if (read < 0) break
                                output.append(buffer, 0, read)
                            }
                            output.toString()
                        }
                    parseProxyLinks(body).ifEmpty {
                        body
                            .lineSequence()
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
