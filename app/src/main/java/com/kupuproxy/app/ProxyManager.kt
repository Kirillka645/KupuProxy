package com.kupuproxy.app

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object ProxyManager {
    private val client = OkHttpClient()
    private const val MAX_PROXIES = 10_000

    val SOURCES = listOf(
        Source(
            name = "Россия (Kort0881)",
            url = "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_ru.txt",
            prefix = "tg://proxy?"
        ),
        Source(
            name = "Европа (Kort0881)",
            url = "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_eu.txt",
            prefix = "tg://proxy?"
        ),
        Source(
            name = "SurfboardV2ray",
            url = "https://raw.githubusercontent.com/Surfboardv2ray/TGProto/refs/heads/main/proxies-tested.txt",
            prefix = "https://t.me/proxy?"
        )
    )

    data class Source(
        val name: String,
        val url: String,
        val prefix: String
    )

    suspend fun fetchAllSources(
        onProgress: (sourceIndex: Int, total: Int, count: Int) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        val allProxies = mutableListOf<String>()
        SOURCES.forEachIndexed { index, source ->
            try {
                val proxies = fetchProxies(source.url, source.prefix)
                allProxies.addAll(proxies)
                onProgress(index + 1, SOURCES.size, proxies.size)
            } catch (_: Exception) {
                onProgress(index + 1, SOURCES.size, 0)
            }
        }
        allProxies
    }

    suspend fun fetchProxies(url: String, urlPrefix: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string().orEmpty()
                body.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.startsWith(urlPrefix) }
                    .map { convertToTgFormat(it, urlPrefix) }
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun convertToTgFormat(url: String, originalPrefix: String): String {
        return when (originalPrefix) {
            "https://t.me/proxy?" -> "tg://proxy?" + url.removePrefix("https://t.me/proxy?")
            "https://t.me/socks?" -> "tg://socks?" + url.removePrefix("https://t.me/socks?")
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
                url.startsWith("https://t.me/proxy?") -> url.removePrefix("https://t.me/proxy?")
                url.startsWith("https://t.me/socks?") -> url.removePrefix("https://t.me/socks?")
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

    suspend fun loadProxiesFromFile(contentResolver: ContentResolver, uri: Uri): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val proxies = mutableListOf<String>()
                contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().useLines { lines ->
                        lines
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(MAX_PROXIES)
                            .forEach { proxies.add(it) }
                    }
                }
                proxies
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun checkProxiesPingParallel(
        proxies: List<String>,
        batchSize: Int = 50,
        onProgress: (processed: Int, total: Int, working: Int) -> Unit
    ): List<ProxyWithPing> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ProxyWithPing>()
        val total = proxies.size
        var processed = 0
        var working = 0

        proxies.chunked(batchSize).forEach { batch ->
            val batchResults = batch.map { proxyUrl ->
                async {
                    val info = parseProxyUrl(proxyUrl) ?: return@async null
                    val ping = measurePing(info.server, info.port.toIntOrNull() ?: 443)
                    if (ping in 1 until 5000) ProxyWithPing(proxyUrl, ping) else null
                }
            }.awaitAll().filterNotNull()

            results.addAll(batchResults)
            working += batchResults.size
            processed += batch.size

            withContext(Dispatchers.Main) {
                onProgress(processed, total, working)
            }
        }

        results.sortedBy { it.pingMs }
    }

    private suspend fun measurePing(server: String, port: Int): Int = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            val start = System.currentTimeMillis()
            socket = Socket()
            socket.connect(InetSocketAddress(server, port), 3000)
            (System.currentTimeMillis() - start).toInt()
        } catch (_: Exception) {
            -1
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    fun parseProxyUrl(url: String): ProxyInfo? {
        return try {
            val cleanUrl = when {
                url.startsWith("tg://proxy?") -> url.removePrefix("tg://proxy?")
                url.startsWith("tg://socks?") -> url.removePrefix("tg://socks?")
                url.startsWith("https://t.me/proxy?") -> url.removePrefix("https://t.me/proxy?")
                url.startsWith("https://t.me/socks?") -> url.removePrefix("https://t.me/socks?")
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
}
