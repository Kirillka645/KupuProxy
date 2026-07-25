package com.kupuproxy.app.data.remote

import com.kupuproxy.app.core.Constants
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request

object HttpSupport {

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(22, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(28, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadText(
        client: OkHttpClient,
        url: String,
        etag: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Pair<String?, String?> {
        val reqBuilder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                headers["User-Agent"] ?: "KupuProxy/1.3.2 (Android; MTProto aggregator)"
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
        attempts: Int = 3,
        headers: Map<String, String> = emptyMap(),
        minUsefulBytes: Int = 16
    ): Pair<String, String>? {
        if (urls.isEmpty()) return null
        val delays = longArrayOf(400, 1500, 4000)
        for (url in urls) {
            repeat(attempts.coerceAtLeast(1)) { attempt ->
                try {
                    val (body, _) = downloadText(client, url, headers = headers)
                    if (!body.isNullOrBlank() && body.length >= minUsefulBytes && !looksLikeBlockedPage(body)) {
                        return body to url
                    }
                } catch (_: Exception) {
                }
                if (attempt < delays.lastIndex) {
                    delay(delays[attempt] + Random.nextLong(0, 120))
                }
            }
        }
        return null
    }

    /** Типичные ответы блокировок / captcha / empty mirror shells. */
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
        return markers.any { s.contains(it) } && !s.contains("tg://proxy") && !s.contains("t.me/proxy")
    }
}
