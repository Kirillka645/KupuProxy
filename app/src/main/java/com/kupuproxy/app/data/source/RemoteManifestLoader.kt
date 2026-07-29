package com.kupuproxy.app.data.source

import com.kupuproxy.app.BuildConfig
import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.source.ProxySource
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Remote source manifest from KupuProxy repo (fallback to seed if unavailable).
 */
object RemoteManifestLoader {

    suspend fun loadExtraSources(client: OkHttpClient): List<ProxySource> {
        return try {
            val (body, _) = HttpSupport.downloadText(
                client,
                BuildConfig.SOURCES_MANIFEST_URL,
                maxBytes = HttpSupport.MAX_MANIFEST_BYTES
            )
            if (body.isNullOrBlank()) return emptyList()
            parseManifest(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseManifest(json: String): List<ProxySource> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("sources") ?: return emptyList()
        val out = mutableListOf<ProxySource>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.optBoolean("enabled", true)) continue
            val id = o.optString("id")
            val name = o.optString("name", id)
            when (o.optString("kind")) {
                "TELEGRAM_CHANNEL" -> {
                    val ch = o.optString("channel")
                    if (ch.isNotBlank()) {
                        out += TelegramWebPreviewSource(ch, name)
                    }
                }
                "GITHUB_RAW" -> {
                    val owner = o.optString("owner")
                    val repo = o.optString("repo")
                    val ref = o.optString("ref", "main")
                    val path = o.optString("path")
                    if (owner.isNotBlank() && repo.isNotBlank() && path.isNotBlank()) {
                        out += UrlListProxySource(
                            id = id,
                            displayName = name,
                            urls = HttpSupport.githubCdnUrls(owner, repo, ref, path),
                            kind = SourceKind.GITHUB_RAW
                        )
                    }
                }
                "HTML_PAGE", "JSON_API" -> {
                    val url = o.optString("url")
                    if (url.isNotBlank()) {
                        out += UrlListProxySource(
                            id = id,
                            displayName = name,
                            urls = listOf(url),
                            kind = SourceKind.HTML_PAGE
                        )
                    }
                }
            }
        }
        return out
    }
}
