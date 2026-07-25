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
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun downloadText(
        client: OkHttpClient,
        url: String,
        etag: String? = null
    ): Pair<String?, String?> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "KupuProxy/1.4.0")
            .header("Accept", "*/*")
            .apply {
                if (!etag.isNullOrBlank()) header("If-None-Match", etag)
            }
            .build()
        client.newCall(req).execute().use { resp ->
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

    suspend fun downloadWithRetry(
        client: OkHttpClient,
        urls: List<String>,
        attempts: Int = 3
    ): Pair<String, String>? {
        val delays = longArrayOf(500, 2000, 8000)
        for (url in urls) {
            repeat(attempts) { attempt ->
                try {
                    val (body, _) = downloadText(client, url)
                    if (!body.isNullOrBlank()) return body to url
                } catch (_: Exception) {
                }
                if (attempt < delays.lastIndex) {
                    delay(delays[attempt] + Random.nextLong(0, 150))
                }
            }
        }
        return null
    }
}
