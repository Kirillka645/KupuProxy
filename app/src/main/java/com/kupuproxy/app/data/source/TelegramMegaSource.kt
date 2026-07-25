package com.kupuproxy.app.data.source

import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.data.remote.TelegramBypass
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Быстрый «TG mega»-источник:
 * 1) параллельно тянет GitHub-скрейпы TG-каналов (CDN) — основной объём 50–300+;
 * 2) параллельно race-зеркала по популярным каналам (если t.me мёртв — jina/rsshub);
 * 3) early-exit при [TARGET_MIN] найденных.
 */
class TelegramMegaSource(
    private val targetMin: Int = TARGET_MIN,
    private val targetSoft: Int = TARGET_SOFT,
    override val enabledByDefault: Boolean = true
) : ProxySource {

    override val id: String = "tg_mega"
    override val displayName: String = "TG каналы · mega"
    override val kind: SourceKind = SourceKind.TELEGRAM_CHANNEL

    override suspend fun fetch(client: OkHttpClient): List<RawProxyEntry> {
        val fast = HttpSupport.fastClient()
        val bag = LinkedHashMap<String, RawProxyEntry>()

        fun add(list: List<RawProxyEntry>) {
            for (e in list) {
                val key = "${e.host.lowercase()}:${e.port}:${e.secret.lowercase()}"
                bag.putIfAbsent(key, e.copy(sourceId = id, sourceName = displayName))
            }
        }

        // --- Phase 1: scraped lists from GitHub (no Telegram needed) ---
        val scrapeUrls = TelegramBypass.telegramScrapedListUrls()
        val scraped = HttpSupport.downloadAllParallel(
            client = client,
            urls = scrapeUrls,
            headers = TelegramBypass.browserHeaders(),
            minUsefulBytes = 40,
            maxParallel = 8,
            perUrlTimeoutMs = 9_000L
        )
        for ((body, _) in scraped) {
            add(ProxyParser.parse(normalizeBody(body), id, displayName))
            if (bag.size >= targetSoft) return bag.values.toList()
        }

        // --- Phase 2: live channel mirrors (parallel, few channels) ---
        if (bag.size < targetMin) {
            val channels = TelegramBypass.POPULAR_CHANNELS
            val channelHits = fetchChannelsParallel(fast, channels, need = targetMin - bag.size)
            add(channelHits)
        }

        // --- Phase 3: if still thin, race remaining scrape mirrors one more pass ---
        if (bag.size < targetMin) {
            val extra = HttpSupport.downloadAllParallel(
                client = client,
                urls = scrapeUrls.shuffled().take(12),
                headers = TelegramBypass.browserHeaders(),
                minUsefulBytes = 40,
                maxParallel = 6,
                perUrlTimeoutMs = 8_000L
            )
            for ((body, _) in extra) {
                add(ProxyParser.parse(normalizeBody(body), id, displayName))
            }
        }

        return bag.values.toList()
    }

    private suspend fun fetchChannelsParallel(
        client: OkHttpClient,
        channels: List<String>,
        need: Int
    ): List<RawProxyEntry> = coroutineScope {
        val bag = LinkedHashMap<String, RawProxyEntry>()
        val sem = Semaphore(4)
        channels.map { ch ->
            async {
                sem.withPermit {
                    withTimeoutOrNull(11_000L) {
                        fetchOneChannel(client, ch)
                    }.orEmpty()
                }
            }
        }.awaitAll().forEach { list ->
            for (e in list) {
                val key = "${e.host.lowercase()}:${e.port}:${e.secret.lowercase()}"
                bag.putIfAbsent(key, e)
            }
        }
        bag.values.toList()
    }

    private suspend fun fetchOneChannel(
        client: OkHttpClient,
        channel: String
    ): List<RawProxyEntry> {
        val urls = TelegramBypass.channelFastUrls(channel)
        val raced = HttpSupport.downloadRace(
            client = client,
            urls = urls,
            headers = TelegramBypass.browserHeaders(),
            minUsefulBytes = 80,
            perUrlTimeoutMs = 6_500L,
            overallTimeoutMs = 9_000L
        ) ?: return emptyList()
        return ProxyParser.parse(normalizeBody(raced.first), id, "TG @$channel")
    }

    companion object {
        const val TARGET_MIN = 50
        const val TARGET_SOFT = 120

        fun normalizeBody(body: String): String {
            return body
                .replace("\\u0026", "&")
                .replace("&amp;", "&")
                .replace("&#38;", "&")
                .replace("\\/", "/")
                .replace("%3A", ":")
                .replace("%2F", "/")
                .replace("%3F", "?")
                .replace("%3D", "=")
                .replace("%26", "&")
        }
    }
}
