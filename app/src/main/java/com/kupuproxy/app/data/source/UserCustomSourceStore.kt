package com.kupuproxy.app.data.source

import android.content.Context
import com.kupuproxy.app.data.local.db.AppDatabase
import com.kupuproxy.app.data.local.db.SourceEntity
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.source.ProxySource
import java.util.UUID

/**
 * Пользовательские URL-источники (Room).
 */
class UserCustomSourceStore(context: Context) {
    private val dao = AppDatabase.get(context).sourceDao()

    suspend fun allEnabledSources(): List<ProxySource> {
        return dao.all()
            .filter { it.enabled && it.isUser }
            .map { entity ->
                UrlListProxySource(
                    id = entity.id,
                    displayName = entity.name,
                    urls = listOf(entity.url),
                    kind = runCatching { SourceKind.valueOf(entity.kind) }
                        .getOrDefault(SourceKind.USER_CUSTOM),
                    enabledByDefault = true
                )
            }
    }

    suspend fun add(name: String, url: String, kind: SourceKind = SourceKind.USER_CUSTOM) {
        val id = "user_" + UUID.randomUUID().toString().take(8)
        dao.upsert(
            SourceEntity(
                id = id,
                name = name.ifBlank { url.take(40) },
                url = url.trim(),
                kind = kind.name,
                enabled = true,
                isUser = true
            )
        )
    }

    suspend fun list(): List<SourceEntity> = dao.all().filter { it.isUser }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val cur = dao.all().find { it.id == id } ?: return
        dao.upsert(cur.copy(enabled = enabled))
    }

    /** Export as compact base64 payload for share/QR */
    suspend fun exportBase64(): String {
        val items = list()
        val json = org.json.JSONArray()
        for (s in items) {
            json.put(
                org.json.JSONObject()
                    .put("name", s.name)
                    .put("url", s.url)
                    .put("kind", s.kind)
            )
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
    }

    suspend fun importBase64(payload: String): Int {
        val raw = android.util.Base64.decode(
            payload.trim(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        val arr = org.json.JSONArray(String(raw, Charsets.UTF_8))
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            add(
                name = o.optString("name", "Custom"),
                url = url,
                kind = runCatching { SourceKind.valueOf(o.optString("kind", "USER_CUSTOM")) }
                    .getOrDefault(SourceKind.USER_CUSTOM)
            )
            n++
        }
        return n
    }
}
