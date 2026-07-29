package com.kupuproxy.app.data.source

import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Serializable
data class KortCollectorRecord(
    val type: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val secret: String? = null,
    val link: String? = null,
    val ping: Double? = null,
    val region: String? = null,
    val domain: String? = null,
    val method: String? = null,
    @SerialName("probe_resistant") val probeResistant: Boolean? = null
)

@Serializable
data class KortCollectorStats(
    val timestamp: String,
    val total: Int = 0,
    @SerialName("by_region") val byRegion: Map<String, Int> = emptyMap(),
    val top: Int = 0
) {
    fun normalized(): KortCollectorStats {
        val parsed = try {
            Instant.parse(timestamp)
        } catch (_: DateTimeParseException) {
            null
        }
        require(parsed != null) { "Некорректное время снимка Kort" }
        return copy(
            total = total.coerceAtLeast(0),
            top = top.coerceAtLeast(0),
            byRegion = byRegion.mapKeys { it.key.lowercase(Locale.US) }
                .filterKeys { it in KortCollectorSource.SUPPORTED_REGIONS }
                .mapValues { it.value.coerceAtLeast(0) }
        )
    }
}

class KortCollectorSource(
    private val regionFilter: String? = null,
    override val id: String = regionFilter?.let { "kort_verified_$it" } ?: ID,
    override val displayName: String = regionFilter?.let {
        "Kort Verified ${it.uppercase(Locale.US)}"
    } ?: "Kort Verified",
    override val enabledByDefault: Boolean = regionFilter == null
) : ProxySource {

    override val kind: SourceKind = SourceKind.JSON_API

    override suspend fun fetch(client: OkHttpClient): List<RawProxyEntry> {
        val downloaded = HttpSupport.downloadWithRetry(
            client = client,
            urls = verifiedUrls(),
            minUsefulBytes = 32
        )
        val parsed = downloaded?.first?.let { parseVerifiedJson(it, regionFilter) }.orEmpty()
        if (parsed.isNotEmpty()) return parsed

        val fallback = HttpSupport.downloadWithRetry(
            client = client,
            urls = mtprotoFallbackUrls(),
            minUsefulBytes = 16
        ) ?: return emptyList()
        return ProxyParser.parse(fallback.first, id, displayName)
    }

    companion object {
        const val ID = "kort_verified"
        const val MAX_RECORDS = 15_000
        val SUPPORTED_REGIONS = setOf("ru", "eu", "us", "asia")
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun verifiedUrls(): List<String> = HttpSupport.githubCdnUrls(
            "Kirillka645", "KupuProxy", "main", "proxy-feeds/kort_verified.json"
        ) + HttpSupport.githubCdnUrls(
            "kort0881", "telegram-proxy-collector", "main", "verified/proxy_all_verified.json"
        )

        fun statsUrls(): List<String> = HttpSupport.githubCdnUrls(
            "Kirillka645", "KupuProxy", "main", "proxy-feeds/kort_stats.json"
        ) + HttpSupport.githubCdnUrls(
            "kort0881", "telegram-proxy-collector", "main", "verified/proxy_stats_verified.json"
        )

        fun mtprotoFallbackUrls(): List<String> = HttpSupport.githubCdnUrls(
            "Kirillka645", "KupuProxy", "main", "proxy-feeds/kort_all.txt"
        ) + HttpSupport.githubCdnUrls(
            "kort0881", "telegram-proxy-collector", "main", "proxy_all_mtproto.txt"
        )

        fun parseVerifiedJson(body: String, regionFilter: String? = null): List<RawProxyEntry> {
            require(body.length <= HttpSupport.MAX_SOURCE_BYTES) { "Снимок Kort слишком большой" }
            val normalizedFilter = regionFilter?.lowercase(Locale.US)
            require(normalizedFilter == null || normalizedFilter in SUPPORTED_REGIONS) {
                "Неизвестный регион Kort"
            }
            val records = runCatching {
                json.decodeFromString<List<KortCollectorRecord>>(body)
            }.getOrElse { return emptyList() }

            val result = LinkedHashMap<String, RawProxyEntry>()
            for (record in records.take(MAX_RECORDS)) {
                if (!record.type.equals("mtproto", ignoreCase = true)) continue
                val host = record.host?.trim()?.trim('[', ']')?.trimEnd('.') ?: continue
                val port = record.port ?: continue
                val secret = record.secret?.trim() ?: continue
                val region = record.region?.lowercase(Locale.US)?.takeIf(SUPPORTED_REGIONS::contains)
                if (normalizedFilter != null && region != normalizedFilter) continue
                if (host.isBlank() || host.length > 253 || !ProxyParser.isValidPort(port)) continue
                if (ProxyParser.isPrivateOrReservedHost(host) || !ProxyParser.looksLikeSecret(secret)) continue

                val secretType = ProxyParser.classifySecret(secret)
                val extractedSni = ProxyParser.extractSni(secret, secretType)
                val declaredDomain = sanitizeDomain(record.domain)
                val pingMs = record.ping
                    ?.takeIf { it.isFinite() && it >= 0.0 && it <= 60.0 }
                    ?.let { (it * 1000.0).toInt().coerceAtLeast(1) }
                val method = record.method?.trim()?.take(40)?.takeIf { it.isNotBlank() }
                val url = ProxyParser.toTgUrl(host, port, secret)
                val entry = RawProxyEntry(
                    url = url,
                    host = host,
                    port = port,
                    secret = secret,
                    secretType = secretType,
                    sniDomain = extractedSni ?: declaredDomain,
                    sourceId = ID,
                    sourceName = "Kort Verified",
                    region = region,
                    upstreamPingMs = pingMs,
                    verificationMethod = method,
                    probeResistant = record.probeResistant
                )
                result.putIfAbsent("${host.lowercase(Locale.US)}:$port:${secret.lowercase(Locale.US)}", entry)
            }
            return result.values.toList()
        }

        fun parseStats(body: String): KortCollectorStats? {
            if (body.length > HttpSupport.MAX_MANIFEST_BYTES) return null
            return runCatching {
                json.decodeFromString<KortCollectorStats>(body).normalized()
            }.getOrNull()
        }

        suspend fun fetchStats(client: OkHttpClient): KortCollectorStats? {
            val downloaded = HttpSupport.downloadWithRetry(
                client = client,
                urls = statsUrls(),
                minUsefulBytes = 20
            ) ?: return null
            return parseStats(downloaded.first)
        }

        private fun sanitizeDomain(value: String?): String? {
            val candidate = value?.trim()?.lowercase(Locale.US)?.trimEnd('.') ?: return null
            if (candidate.length !in 4..253 || candidate.any(Char::isWhitespace)) return null
            if (!candidate.contains('.') || candidate.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return null
            return candidate
        }
    }
}
