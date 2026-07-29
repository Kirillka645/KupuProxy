package com.kupuproxy.app.domain.model

enum class SourceKind {
    GITHUB_RAW,
    TELEGRAM_CHANNEL,
    JSON_API,
    HTML_PAGE,
    USER_CUSTOM,
    SEED,
    MANIFEST
}

enum class SecretType {
    PLAIN,
    PADDED,
    FAKE_TLS,
    UNKNOWN
}

data class RawProxyEntry(
    val url: String,
    val host: String,
    val port: Int,
    val secret: String,
    val secretType: SecretType = SecretType.UNKNOWN,
    val sniDomain: String? = null,
    val sourceId: String = "",
    val sourceName: String = "",
    val region: String? = null,
    val upstreamPingMs: Int? = null,
    val verificationMethod: String? = null,
    val probeResistant: Boolean? = null,
    val snapshotTimestamp: String? = null
)

data class ProxyEndpoint(
    val url: String,
    val host: String,
    val port: Int,
    val secret: String,
    val secretType: SecretType,
    val sniDomain: String? = null,
    val sourceIds: Set<String> = emptySet(),
    val reliabilityScore: Int = 1,
    val countryCode: String? = null,
    val asn: String? = null,
    val region: String? = null,
    val upstreamPingMs: Int? = null,
    val verificationMethod: String? = null,
    val probeResistant: Boolean? = null,
    val snapshotTimestamp: String? = null
) {
    val dedupeKey: String
        get() = "${host.lowercase()}:$port:${secret.lowercase()}"
}

sealed class SourceResult {
    abstract val sourceId: String
    abstract val displayName: String

    data class Success(
        override val sourceId: String,
        override val displayName: String,
        val entries: List<RawProxyEntry>,
        val mirrorUsed: String? = null
    ) : SourceResult()

    data class Failure(
        override val sourceId: String,
        override val displayName: String,
        val error: ProxyError
    ) : SourceResult()
}

sealed class ProxyError(open val message: String) {
    data class Network(override val message: String) : ProxyError(message)
    data class Timeout(override val message: String = "timeout") : ProxyError(message)
    data class Parse(override val message: String) : ProxyError(message)
    data class Http(val code: Int, override val message: String) : ProxyError(message)
    data class Unknown(override val message: String) : ProxyError(message)
}

data class AggregateScanResult(
    val proxies: List<ProxyEndpoint>,
    val sourceResults: List<SourceResult>,
    val successCount: Int,
    val failureCount: Int
) {
    val summary: String
        get() = "$successCount / ${sourceResults.size}"
}
