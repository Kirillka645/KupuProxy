package com.kupuproxy.app.data.remote

import com.kupuproxy.app.core.Constants
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

object HttpSupport {

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /** Короткий клиент для race зеркал TG. */
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
        headers: Map<String, String> = emptyMap()
    ): Pair<String?, String?> {
        val reqBuilder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                headers["User-Agent"] ?: "KupuProxy/1.3.3.1 (Android; MTProto aggregator)"
            )
            .header("Accept", headers["Accept"] ?: "*/*")
        headers.forEach { (k, v) ->
            if (k.equals("User-Agent", true) || k.equals("Accept", true)) return@forEach
            reqBuilder.header(k, v)
        }
        if (!etag.isNullOrBlank()) reqBuilder.header("If-None-Match", etag)

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 304) return null to (resp.header("ETag") ?: etag)
            if (!resp.isSuccessful) return null to resp.header("ETag")
            val body = resp.body?.string()
            return body to resp.header("ETag")
        }
    }

    fun githubCdnUrls(owner: String, repo: String, ref: String, path: String): List<String> {
        return Constants.GITHUB_CDN_TEMPLATES.map {
            it.replace("{owner}", owner)
                .replace("{repo}", repo)
                .replace("{ref}", ref)
                .replace("{path}", path)
        }
    }

    /**
     * Пробует URL по очереди. [minUsefulBytes] отсекает пустые/error-страницы зеркал.
     */
    suspend fun downloadWithRetry(
        client: OkHttpClient,
        urls: List<String>,
        attempts: Int = 2,
        headers: Map<String, String> = emptyMap(),
        minUsefulBytes: Int = 16
    ): Pair<String, String>? {
        if (urls.isEmpty()) return null
        val delays = longArrayOf(200, 800)
        for (url in urls) {
            repeat(attempts.coerceAtLeast(1)) { attempt ->
                try {
                    val (body, _) = downloadText(client, url, headers = headers)
                    if (!body.isNullOrBlank() &&
                        body.length >= minUsefulBytes &&
                        !looksLikeBlockedPage(body)
                    ) {
                        return body to url
                    }
                } catch (_: Exception) {
                }
                if (attempt < delays.lastIndex) {
                    delay(delays[attempt] + Random.nextLong(0, 80))
                }
            }
        }
        return null
    }

    /**
     * Параллельный race: кто первый вернул полезное тело — тот и победил.
     * Остальные отменяются по факту (не ждём всю цепочку).
     */
    suspend fun downloadRace(
        client: OkHttpClient,
        urls: List<String>,
        headers: Map<String, String> = emptyMap(),
        minUsefulBytes: Int = 16,
        perUrlTimeoutMs: Long = 7_000L,
        overallTimeoutMs: Long = 12_000L
    ): Pair<String, String>? = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope null
        val winner = AtomicReference<Pair<String, String>?>(null)
        val deferreds = urls.distinct().map { url ->
            async {
                withTimeoutOrNull(perUrlTimeoutMs) {
                    try {
                        val (body, _) = downloadText(client, url, headers = headers)
                        if (!body.isNullOrBlank() &&
                            body.length >= minUsefulBytes &&
                            !looksLikeBlockedPage(body)
                        ) {
                            val pair = body to url
                            winner.compareAndSet(null, pair)
                            pair
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
        withTimeoutOrNull(overallTimeoutMs) {
            // ждём первый успешный
            while (winner.get() == null && deferreds.any { it.isActive }) {
                val done = deferreds.firstOrNull { it.isCompleted && it.getCompleted() != null }
                if (done != null) {
                    return@withTimeoutOrNull done.getCompleted()
                }
                // poll lightly
                delay(40)
                winner.get()?.let { return@withTimeoutOrNull it }
            }
            winner.get() ?: deferreds.mapNotNull {
                runCatching { it.getCompleted() }.getOrNull()
            }.firstOrNull()
        } ?: winner.get()
    }

    /**
     * Скачивает несколько URL параллельно, мержит все успешные тела.
     */
    suspend fun downloadAllParallel(
        client: OkHttpClient,
        urls: List<String>,
        headers: Map<String, String> = emptyMap(),
        minUsefulBytes: Int = 16,
        maxParallel: Int = 6,
        perUrlTimeoutMs: Long = 10_000L
    ): List<Pair<String, String>> = coroutineScope {
        val sem = Semaphore(maxParallel)
        urls.distinct().map { url ->
            async {
                sem.withPermit {
                    withTimeoutOrNull(perUrlTimeoutMs) {
                        try {
                            val (body, _) = downloadText(client, url, headers = headers)
                            if (!body.isNullOrBlank() &&
                                body.length >= minUsefulBytes &&
                                !looksLikeBlockedPage(body)
                            ) {
                                body to url
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    fun looksLikeBlockedPage(body: String): Boolean {
        val s = body.lowercase()
        if (body.length < 40) return true
        val markers = listOf(
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
        val hasProxy = s.contains("tg://proxy") ||
            s.contains("t.me/proxy") ||
            s.contains("server=") && s.contains("secret=")
        return markers.any { s.contains(it) } && !hasProxy
    }
}
