package com.kupuproxy.app

import java.io.Serializable

enum class ProxyStatus : Serializable {
    /** Как в Telegram: прокси реально отвечает MTProto */
    AVAILABLE,
    /** TCP есть, но MTProto/secret не прошёл */
    UNAVAILABLE
}

data class ProxyWithPing(
    val url: String,
    val pingMs: Int,
    val profileLabel: String = "",
    val status: ProxyStatus = ProxyStatus.AVAILABLE,
    val statusText: String = "Доступен"
) : Serializable

data class ProxyInfo(
    val server: String,
    val port: String
)

data class FetchResult(
    val proxies: List<String>,
    val sourceHits: Map<String, Int>,
    val usedMirrors: List<String>,
    val fromCache: Boolean = false,
    val fromSeed: Boolean = false
)
