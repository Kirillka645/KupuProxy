package com.kupuproxy.app.data.source

import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.data.remote.TelegramBypass
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Aggregates independent Telegram proxy feeds under a deadline short enough for
 * ProxyAggregator's per-source budget. A failed mirror or phase never discards
 * useful entries returned by the other phases.
 */
class TelegramMegaSource(override val enabledByDefault: Boolean = true) : ProxySource {

    override val id: String = "tg_mega"
    override val displayName: String = "Telegram · mega"
    override val kind: SourceKind = SourceKind.TELEGRAM_CHANNEL

    override suspend fun fetch(client: OkHttpClient): List<RawProxyEntry> {
        val boundedClient = boundedClient(client)
        val phaseResults =
            collectPhaseResults(
                phaseTimeoutMs = PHASE_TIMEOUT_MS,
                phases =
                    listOf(
                        { MtproXyzSource().fetch(boundedClient) },
                        { fetchScrapedLists(boundedClient) },
                        {
                            fetchChannelsParallel(
                                boundedClient,
                                TelegramBypass.POPULAR_CHANNELS,
                            )
                        },
                    ),
            )

        return mergeEntries(id, displayName, phaseResults)
    }

    /**
     * Each repository is exposed through three equivalent CDNs. Racing each
     * mirror group avoids downloading duplicate copies and keeps this phase
     * within one network round-trip.
     */
    private suspend fun fetchScrapedLists(client: OkHttpClient): List<RawProxyEntry> =
        collectTaskResults(
            timeoutMs = PARTIAL_PHASE_TIMEOUT_MS,
            tasks =
                TelegramBypass.telegramScrapedListUrls()
                    .chunked(SCRAPED_MIRRORS_PER_LIST)
                    .map { mirrorUrls ->
                        suspend {
                            val raced =
                                HttpSupport.downloadRace(
                                    client = client,
                                    urls = mirrorUrls,
                                    headers = TelegramBypass.browserHeaders(),
                                    minUsefulBytes = 40,
                                    perUrlTimeoutMs = MIRROR_TIMEOUT_MS,
                                    overallTimeoutMs = MIRROR_RACE_TIMEOUT_MS,
                                    acceptBody = { body ->
                                        parseSafely(body, displayName).isNotEmpty()
                                    },
                                )
                            raced?.let { parseSafely(it.first, displayName) }.orEmpty()
                        }
                    },
        )

    private suspend fun fetchChannelsParallel(
        client: OkHttpClient,
        channels: List<String>,
    ): List<RawProxyEntry> =
        supervisorScope {
            val semaphore = Semaphore(CHANNEL_PARALLELISM)
            collectTaskResults(
                timeoutMs = PARTIAL_PHASE_TIMEOUT_MS,
                tasks =
                    channels.map { channel ->
                        suspend {
                            semaphore.withPermit {
                                try {
                                    withTimeoutOrNull(CHANNEL_TIMEOUT_MS) {
                                        fetchOneChannel(client, channel)
                                    }.orEmpty()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }
                    },
            )
        }

    private suspend fun fetchOneChannel(
        client: OkHttpClient,
        channel: String,
    ): List<RawProxyEntry> {
        val raced =
            HttpSupport.downloadRace(
                client = client,
                urls = TelegramBypass.channelFastUrls(channel),
                headers = TelegramBypass.browserHeaders(),
                minUsefulBytes = 80,
                perUrlTimeoutMs = MIRROR_TIMEOUT_MS,
                overallTimeoutMs = MIRROR_RACE_TIMEOUT_MS,
                acceptBody = { body ->
                    parseSafely(body, "TG @$channel").isNotEmpty()
                },
            ) ?: return emptyList()
        return parseSafely(raced.first, "TG @$channel")
    }

    private fun parseSafely(body: String, sourceName: String): List<RawProxyEntry> =
        try {
            ProxyParser.parse(normalizeBody(body), id, sourceName)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

    private fun boundedClient(client: OkHttpClient): OkHttpClient =
        client.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(HTTP_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

    companion object {
        private const val PHASE_TIMEOUT_MS = 11_000L
        private const val PARTIAL_PHASE_TIMEOUT_MS = 10_700L
        private const val CONNECT_TIMEOUT_MS = 4_000L
        private const val READ_TIMEOUT_MS = 4_750L
        private const val HTTP_CALL_TIMEOUT_MS = 5_000L
        private const val MIRROR_TIMEOUT_MS = 4_750L
        private const val MIRROR_RACE_TIMEOUT_MS = 5_100L
        private const val CHANNEL_TIMEOUT_MS = 5_250L
        private const val CHANNEL_PARALLELISM = 6
        private const val SCRAPED_MIRRORS_PER_LIST = 3

        /** Returns completed task results even when slower siblings miss the deadline. */
        internal suspend fun collectTaskResults(
            timeoutMs: Long,
            tasks: List<suspend () -> List<RawProxyEntry>>,
        ): List<RawProxyEntry> =
            supervisorScope {
                if (tasks.isEmpty()) return@supervisorScope emptyList()
                val completed = Channel<List<RawProxyEntry>>(tasks.size)
                val jobs =
                    tasks.map { task ->
                        launch {
                            val result =
                                try {
                                    task()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            completed.send(result)
                        }
                    }
                val collected = mutableListOf<RawProxyEntry>()
                withTimeoutOrNull(timeoutMs) {
                    repeat(tasks.size) { collected += completed.receive() }
                }
                jobs.forEach { it.cancel() }
                jobs.joinAll()
                completed.close()
                collected
            }

        /** Runs all phases at once while preserving successful sibling results. */
        internal suspend fun collectPhaseResults(
            phaseTimeoutMs: Long,
            phases: List<suspend () -> List<RawProxyEntry>>,
        ): List<List<RawProxyEntry>> =
            supervisorScope {
                phases
                    .map { phase ->
                        async {
                            try {
                                withTimeoutOrNull(phaseTimeoutMs) { phase() }.orEmpty()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    .awaitAll()
            }

        /** Deduplication happens after concurrent work, so no shared map is mutated by workers. */
        internal fun mergeEntries(
            sourceId: String,
            sourceName: String,
            phaseResults: List<List<RawProxyEntry>>,
        ): List<RawProxyEntry> {
            val unique = LinkedHashMap<String, RawProxyEntry>()
            for (entry in phaseResults.flatten()) {
                val key = "${entry.host.lowercase()}:${entry.port}:${entry.secret.lowercase()}"
                unique.putIfAbsent(
                    key,
                    entry.copy(sourceId = sourceId, sourceName = sourceName),
                )
            }
            return unique.values.toList()
        }

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
