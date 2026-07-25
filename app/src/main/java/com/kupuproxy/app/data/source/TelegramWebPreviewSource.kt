package com.kupuproxy.app.data.source

import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import okhttp3.OkHttpClient

/**
 * Публичный веб-превью t.me/s/<channel> — без авторизации.
 */
class TelegramWebPreviewSource(
    private val channelUsername: String,
    override val displayName: String = "TG @$channelUsername",
    override val enabledByDefault: Boolean = true
) : ProxySource {

    override val id: String = "tg_$channelUsername"
    override val kind: SourceKind = SourceKind.TELEGRAM_CHANNEL

    override suspend fun fetch(client: OkHttpClient): List<RawProxyEntry> {
        val url = "https://t.me/s/$channelUsername"
        val (body, _) = HttpSupport.downloadText(client, url)
        if (body.isNullOrBlank()) return emptyList()
        // recent posts only roughly — full page is fine, parser extracts links
        return ProxyParser.parse(body, id, displayName)
    }
}
