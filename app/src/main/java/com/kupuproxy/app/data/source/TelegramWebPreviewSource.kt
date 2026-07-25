package com.kupuproxy.app.data.source

import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.data.remote.TelegramBypass
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import okhttp3.OkHttpClient

/**
 * Публичные Telegram-каналы без API-ключа.
 *
 * При блокировке t.me ходим через зеркала (Jina reader, RSSHub, allorigins, telesco.pe).
 * Парсер вытаскивает tg:// / t.me/proxy из HTML, RSS и markdown.
 */
class TelegramWebPreviewSource(
    private val channelUsername: String,
    override val displayName: String = "TG @$channelUsername",
    override val enabledByDefault: Boolean = true
) : ProxySource {

    override val id: String = "tg_$channelUsername"
    override val kind: SourceKind = SourceKind.TELEGRAM_CHANNEL

    override suspend fun fetch(client: OkHttpClient): List<RawProxyEntry> {
        val urls = TelegramBypass.channelPreviewUrls(channelUsername)
        val downloaded = HttpSupport.downloadWithRetry(
            client = client,
            urls = urls,
            attempts = 2,
            headers = TelegramBypass.browserHeaders(),
            minUsefulBytes = 80
        ) ?: return emptyList()

        val body = downloaded.first
        // jina returns markdown; RSS is XML — both fine for ProxyParser
        val parsed = ProxyParser.parse(body, id, displayName)
        if (parsed.isNotEmpty()) return parsed

        // Sometimes mirrors wrap content; second pass on unescaped links
        val loose = body
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("\\/", "/")
        return ProxyParser.parse(loose, id, displayName)
    }
}
