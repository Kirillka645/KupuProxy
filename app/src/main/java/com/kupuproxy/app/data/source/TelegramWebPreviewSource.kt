package com.kupuproxy.app.data.source

import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.data.remote.TelegramBypass
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import okhttp3.OkHttpClient

/**
 * Один публичный TG-канал через **параллельный race** зеркал (не последовательный обход).
 * Для мега-скана предпочтительнее [TelegramMegaSource].
 */
class TelegramWebPreviewSource(
    private val channelUsername: String,
    override val displayName: String = "TG @$channelUsername",
    override val enabledByDefault: Boolean = false
) : ProxySource {

    override val id: String = "tg_$channelUsername"
    override val kind: SourceKind = SourceKind.TELEGRAM_CHANNEL

    override suspend fun fetch(client: OkHttpClient): List<RawProxyEntry> {
        val fast = HttpSupport.fastClient()
        val urls = TelegramBypass.channelFastUrls(channelUsername)
        val raced = HttpSupport.downloadRace(
            client = fast,
            urls = urls,
            headers = TelegramBypass.browserHeaders(),
            minUsefulBytes = 80,
            perUrlTimeoutMs = 6_500L,
            overallTimeoutMs = 10_000L
        ) ?: return emptyList()

        val body = TelegramMegaSource.normalizeBody(raced.first)
        val parsed = ProxyParser.parse(body, id, displayName)
        if (parsed.isNotEmpty()) return parsed
        return ProxyParser.parse(body, id, displayName)
    }
}
