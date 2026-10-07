package com.kupuproxy.shared.domain.model

/** Транспорт прокси. Определяет и формат ссылки, и способ проверки доступности. */
enum class ProxyProtocol {
    /** Telegram MTProto (`tg://proxy?server=…&port=…&secret=…`). */
    MTPROTO,

    /** SOCKS5 (`socks5://user:pass@host:port`, `tg://socks?…`). */
    SOCKS5,

    /** HTTP CONNECT прокси (`http://user:pass@host:port`). */
    HTTP,

    /** WEB-прокси: HTTP CONNECT поверх TLS (`https://user:pass@host:port`). */
    WEB,
    ;

    val isTelegramNative: Boolean get() = this == MTPROTO

    companion object {
        fun fromScheme(scheme: String?): ProxyProtocol? = when (scheme?.lowercase()) {
            "tg", "mtproto" -> MTPROTO
            "socks", "socks5", "socks5h" -> SOCKS5
            "http" -> HTTP
            "https", "web", "wss" -> WEB
            else -> null
        }
    }
}

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

/**
 * Разобранная запись прокси «как есть» — до агрегации и проверки.
 *
 * [secret] заполняется только для [ProxyProtocol.MTPROTO]; для остальных протоколов
 * используются [username] / [password].
 */
data class RawProxyEntry(
    val url: String,
    val host: String,
    val port: Int,
    val secret: String,
    val protocol: ProxyProtocol = ProxyProtocol.MTPROTO,
    val secretType: SecretType = SecretType.UNKNOWN,
    val sniDomain: String? = null,
    val username: String? = null,
    val password: String? = null,
    val sourceId: String = "",
    val sourceName: String = "",
    val region: String? = null,
    val upstreamPingMs: Int? = null,
    val verificationMethod: String? = null,
    val probeResistant: Boolean? = null,
    val snapshotTimestamp: String? = null,
) {
    /** Ключ дедупликации: один и тот же эндпоинт с разными секретами — разные прокси. */
    val dedupeKey: String
        get() = "${protocol.name}:${host.lowercase()}:$port:${identityPart.lowercase()}"

    private val identityPart: String
        get() = when (protocol) {
            ProxyProtocol.MTPROTO -> secret
            else -> "$username:$password"
        }
}

data class ProxyEndpoint(
    val url: String,
    val host: String,
    val port: Int,
    val secret: String,
    val protocol: ProxyProtocol = ProxyProtocol.MTPROTO,
    val secretType: SecretType,
    val sniDomain: String? = null,
    val username: String? = null,
    val password: String? = null,
    val sourceIds: Set<String> = emptySet(),
    val reliabilityScore: Int = 1,
    val countryCode: String? = null,
    val asn: String? = null,
    val region: String? = null,
    val upstreamPingMs: Int? = null,
    val verificationMethod: String? = null,
    val probeResistant: Boolean? = null,
    val snapshotTimestamp: String? = null,
) {
    val dedupeKey: String
        get() = "${protocol.name}:${host.lowercase()}:$port:$secret.lowercase()"

    /** «host:port» — то, что показываем в списке и в трее. */
    val endpointLabel: String get() = "$host:$port"
}

sealed class SourceResult {
    abstract val sourceId: String
    abstract val displayName: String

    data class Success(
        override val sourceId: String,
        override val displayName: String,
        val entries: List<RawProxyEntry>,
        val mirrorUsed: String? = null,
    ) : SourceResult()

    data class Failure(
        override val sourceId: String,
        override val displayName: String,
        val error: ProxyError,
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
    val failureCount: Int,
) {
    val summary: String
        get() = "$successCount / ${sourceResults.size}"
}