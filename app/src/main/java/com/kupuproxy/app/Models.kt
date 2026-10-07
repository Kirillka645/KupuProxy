package com.kupuproxy.app

import com.kupuproxy.shared.domain.check.LinkQuality
import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.Serializable

enum class ProxyStatus : Serializable {
    /** Прокси реально отвечает по своему протоколу (MTProto / SOCKS5 / HTTP / WEB). */
    AVAILABLE,

    /** TCP до endpoint есть, но рукопожатие протокола не прошло. */
    UNAVAILABLE,
}

data class ProxyWithPing(
    val url: String,
    val pingMs: Int,
    val profileLabel: String = "",
    val status: ProxyStatus = ProxyStatus.AVAILABLE,
    val statusText: String = "Доступен",
    /** Транспорт прокси — MTProto, SOCKS5, HTTP или WEB. */
    val protocol: ProxyProtocol = ProxyProtocol.MTPROTO,
    /** Разброс замеров, мс. 0 при одиночном замере. */
    val jitterMs: Int = 0,
    /** Число успешных замеров, по которым посчитана статистика. */
    val samples: Int = 1,
) : Serializable {
    /** Готовая шкала качества для индикатора в списке. */
    val quality: LinkQuality get() = LinkQuality.of(pingMs)
}

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