package com.kupuproxy.app.domain.aggregator

import com.kupuproxy.app.core.Constants
import com.kupuproxy.app.domain.model.AggregateScanResult
import com.kupuproxy.app.domain.model.ProxyEndpoint
import com.kupuproxy.app.domain.model.ProxyError
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceResult
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import java.net.InetAddress
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

class ProxyAggregator(
    private val client: OkHttpClient,
    private val parallelism: Int = Constants.AGGREGATOR_PARALLELISM,
    private val timeoutMs: Long = Constants.SOURCE_TIMEOUT_MS
) {

    suspend fun collect(
        sources: List<ProxySource>,
        resolveDnsForDedupe: Boolean = false,
        onSourceDone: (SourceResult) -> Unit = {}
    ): AggregateScanResult = withContext(Dispatchers.IO) {
        val sem = Semaphore(parallelism)
        val results = coroutineScope {
            sources.map { source ->
                async {
                    sem.withPermit {
                        val r = fetchWithRetry(source)
                        onSourceDone(r)
                        r
                    }
                }
            }.awaitAll()
        }

        val success = results.filterIsInstance<SourceResult.Success>()
        val merged = dedupe(
            success.flatMap { it.entries },
            resolveDns = resolveDnsForDedupe
        )

        AggregateScanResult(
            proxies = merged,
            sourceResults = results,
            successCount = success.size,
            failureCount = results.size - success.size
        )
    }

    private suspend fun fetchWithRetry(source: ProxySource): SourceResult {
        var lastError: ProxyError = ProxyError.Unknown("unknown")
        val delays = longArrayOf(500L, 2000L, 8000L)
        repeat(3) { attempt ->
            try {
                val entries = withTimeout(timeoutMs) {
                    source.fetch(client)
                }
                return SourceResult.Success(
                    sourceId = source.id,
                    displayName = source.displayName,
                    entries = entries
                )
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastError = ProxyError.Timeout()
            } catch (e: Exception) {
                lastError = ProxyError.Network(e.message ?: e.javaClass.simpleName)
            }
            if (attempt < delays.lastIndex) {
                val jitter = Random.nextLong(0, 200)
                delay(delays[attempt] + jitter)
            }
        }
        return SourceResult.Failure(source.id, source.displayName, lastError)
    }

    fun dedupe(
        entries: List<RawProxyEntry>,
        resolveDns: Boolean = false
    ): List<ProxyEndpoint> {
        data class Acc(
            var url: String,
            val host: String,
            val port: Int,
            val secret: String,
            val type: com.kupuproxy.app.domain.model.SecretType,
            val sni: String?,
            val sources: MutableSet<String>
        )

        val byKey = linkedMapOf<String, Acc>()
        for (e in entries) {
            if (!ProxyParser.isValidPort(e.port)) continue
            if (ProxyParser.isPrivateOrReservedHost(e.host)) continue
            if (!ProxyParser.looksLikeSecret(e.secret)) continue

            val hostKey = if (resolveDns) {
                try {
                    InetAddress.getByName(e.host).hostAddress ?: e.host
                } catch (_: Exception) {
                    e.host
                }
            } else e.host

            val key = "${hostKey.lowercase()}:${e.port}:${e.secret.lowercase()}"
            val acc = byKey.getOrPut(key) {
                Acc(
                    url = e.url,
                    host = e.host,
                    port = e.port,
                    secret = e.secret,
                    type = e.secretType,
                    sni = e.sniDomain,
                    sources = mutableSetOf()
                )
            }
            if (e.sourceId.isNotBlank()) acc.sources += e.sourceId
            if (e.sourceName.isNotBlank()) acc.sources += e.sourceName
        }

        return byKey.values.map {
            ProxyEndpoint(
                url = it.url,
                host = it.host,
                port = it.port,
                secret = it.secret,
                secretType = it.type,
                sniDomain = it.sni,
                sourceIds = it.sources.toSet(),
                reliabilityScore = it.sources.size.coerceAtLeast(1)
            )
        }.sortedByDescending { it.reliabilityScore }
    }
}
