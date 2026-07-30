package com.kupuproxy.app.data.remote

import com.kupuproxy.app.BuildConfig
import com.kupuproxy.app.core.Constants
import com.kupuproxy.app.core.util.readAllBounded
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

object HttpSupport {
    const val MAX_SOURCE_BYTES = 4L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 512L * 1024

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun fastClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun downloadText(
        client: OkHttpClient,
        url: String,
        etag: String? = null,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = MAX_SOURCE_BYTES,
        enforceSafeUrl: Boolean = false
    ): Pair<String?, String?> {
        require(maxBytes in 1..MAX_SOURCE_BYTES) { "Некорректный лимит ответа" }
        val requestClient = if (enforceSafeUrl) {
            client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        val addresses = java.net.InetAddress.getAllByName(hostname).toList()
                        require(addresses.isNotEmpty() && addresses.none(SafeUrlPolicy::isBlockedAddress)) {
                            "Локальные и служебные сети запрещены"
                        }
                        return addresses
                    }
                })
                .build()
        } else client

        var currentUrl = if (enforceSafeUrl) {
            SafeUrlPolicy.validateHttpsUrl(url).getOrThrow()
        } else url
        var redirects = 0

        while (true) {
            val reqBuilder = Request.Builder()
                .url(currentUrl)
                .header(
                    "User-Agent",
                    headers["User-Agent"] ?: "KupuProxy/${BuildConfig.VERSION_NAME} (Android; MTProto aggregator)"
                )
                .header("Accept", headers["Accept"] ?: "*/*")
            headers.forEach { (key, value) ->
                if (!key.equals("User-Agent", true) && !key.equals("Accept", true)) {
                    reqBuilder.header(key, value)
                }
            }
            if (!etag.isNullOrBlank()) reqBuilder.header("If-None-Match", etag)

            requestClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.code == 304) return null to (response.header("ETag") ?: etag)

                if (enforceSafeUrl && response.isRedirect) {
                    require(redirects++ < SafeUrlPolicy.MAX_REDIRECTS) { "Слишком много перенаправлений" }
                    val location = response.header("Location")
                        ?: throw IllegalStateException("Перенаправление без адреса")
                    val resolved = URI(currentUrl).resolve(location).toASCIIString()
                    currentUrl = SafeUrlPolicy.validateHttpsUrl(resolved).getOrThrow()
                    return@use
                }

                if (!response.isSuccessful) return null to response.header("ETag")
                val body = response.body ?: return null to response.header("ETag")
                val declared = body.contentLength()
                require(declared < 0 || declared <= maxBytes) { "Ответ источника слишком большой" }
                val bytes = body.source().readAllBounded(maxBytes)
                return bytes.toString(Charsets.UTF_8) to response.header("ETag")
            }
        }
    }

    fun githubCdnUrls(owner: String, repo: String, ref: String, path: String): List<String> =
        Constants.GITHUB_CDN_TEMPLATES.map {
            it.replace("{owner}", owner)
                .replace("{repo}", repo)
                .replace("{ref}", ref)
                .replace("{path}", path)
        }

    suspend fun downloadWithRetry(
        client: OkHttpClient,
        urls: List<String>,
        attempts: Int = 2,
        headers: Map<String, String> = emptyMap(),
        minUsefulBytes: Int = 16,
        enforceSafeUrl: Boolean = false
    ): Pair<String, String>? {
        if (urls.isEmpty()) return null
        val delays = longArrayOf(200, 800)
        for (url in urls.distinct()) {
            repeat(attempts.coerceIn(1, 3)) { attempt ->
                try {
                    val (body, _) = downloadText(
                        client = client,
                        url = url,
                        headers = headers,
                        enforceSafeUrl = enforceSafeUrl
                    )
                    if (!body.isNullOrBlank() && body.length >= minUsefulBytes && !looksLikeBlockedPage(body)) {
                        return body to url
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
                if (attempt < attempts - 1 && attempt < delays.size) {
                    delay(delays[attempt] + Random.nextLong(0, 80))
                }
            }
        }
        return null
    }

    suspend fun downloadRace(
        client: OkHttpClient,
        urls: List<String>,
        headers: Map<String, String> = emptyMap(),
        minUsefulBytes: Int = 16,
        perUrlTimeoutMs: Long = 7_000L,
        overallTimeoutMs: Long = 12_000L
    ): Pair<String, String>? = coroutineScope {
        val candidates = urls.distinct()
        if (candidates.isEmpty()) return@coroutineScope null
        val results = Channel<Pair<String, String>?>(candidates.size)
        val jobs = candidates.map { url ->
            launch {
                val result = withTimeoutOrNull(perUrlTimeoutMs) {
                    try {
                        val (body, _) = downloadText(client, url, headers = headers)
                        body?.takeIf { it.length >= minUsefulBytes && !looksLikeBlockedPage(it) }?.let { it to url }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
                results.send(result)
            }
        }
        val winner = withTimeoutOrNull(overallTimeoutMs) {
            repeat(candidates.size) {
                results.receive()?.let { return@withTimeoutOrNull it }
            }
            null
        }
        jobs.forEach { it.cancel() }
        results.close()
        winner
    }

    suspend fun downloadAllParallel(
        client: OkHttpClient,
        urls: List<String>,
        headers: Map<String, String> = emptyMap(),
        minUsefulBytes: Int = 16,
        maxParallel: Int = 6,
        perUrlTimeoutMs: Long = 10_000L
    ): List<Pair<String, String>> = coroutineScope {
        val semaphore = Semaphore(maxParallel.coerceIn(1, 12))
        urls.distinct().map { url ->
            async {
                semaphore.withPermit {
                    withTimeoutOrNull(perUrlTimeoutMs) {
                        try {
                            val (body, _) = downloadText(client, url, headers = headers)
                            body?.takeIf { it.length >= minUsefulBytes && !looksLikeBlockedPage(it) }?.let { it to url }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    fun looksLikeBlockedPage(body: String): Boolean {
        if (body.length < 40) return true
        val text = body.lowercase()
        val hasProxy = text.contains("tg://proxy") || text.contains("t.me/proxy") ||
            (text.contains("server=") && text.contains("secret="))
        if (hasProxy) return false
        return BLOCKED_MARKERS.any(text::contains)
    }

    private val BLOCKED_MARKERS = listOf(
        "access denied",
        "just a moment",
        "cf-browser-verification",
        "attention required",
        "ошибка доступа",
        "доступ ограничен",
        "this site can’t be reached",
        "err_connection",
        "blocked by"
    )
}
