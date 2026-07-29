package com.kupuproxy.app.data.remote

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Blocks custom sources from reaching the device, LAN, or reserved networks.
 * Validation is repeated after DNS resolution and for every redirect.
 */
object SafeUrlPolicy {
    const val MAX_URL_LENGTH = 2_048
    const val MAX_REDIRECTS = 4

    fun validateHttpsUrl(raw: String, resolver: (String) -> Array<InetAddress> = InetAddress::getAllByName): Result<String> {
        return runCatching {
            val value = raw.trim()
            require(value.isNotEmpty()) { "URL пуст" }
            require(value.length <= MAX_URL_LENGTH) { "URL слишком длинный" }

            val uri = URI(value)
            require(uri.scheme.equals("https", ignoreCase = true)) { "Разрешены только HTTPS-источники" }
            require(uri.userInfo == null) { "Логин и пароль в URL запрещены" }
            require(uri.fragment == null) { "Фрагмент URL не поддерживается" }
            require(uri.port == -1 || uri.port == 443) { "Разрешён только HTTPS-порт 443" }

            val host = uri.host?.trim()?.lowercase(Locale.US)
                ?: throw IllegalArgumentException("Некорректный адрес источника")
            require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local")) {
                "Локальные адреса запрещены"
            }

            val addresses = resolver(host)
            require(addresses.isNotEmpty()) { "Адрес источника не найден" }
            require(addresses.none(::isBlockedAddress)) { "Локальные и служебные сети запрещены" }
            uri.toASCIIString()
        }
    }

    fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true

        return when (address) {
            is Inet4Address -> {
                val b = address.address.map { it.toInt() and 0xff }
                val a = b[0]
                val second = b[1]
                a == 0 || a == 10 || a == 127 ||
                    (a == 100 && second in 64..127) ||
                    (a == 169 && second == 254) ||
                    (a == 172 && second in 16..31) ||
                    (a == 192 && second == 0) ||
                    (a == 192 && second == 168) ||
                    (a == 198 && second in 18..19) ||
                    (a == 198 && second == 51 && b[2] == 100) ||
                    (a == 203 && second == 0 && b[2] == 113) ||
                    a >= 224
            }
            is Inet6Address -> {
                val first = address.address[0].toInt() and 0xff
                val second = address.address[1].toInt() and 0xff
                val bytes = address.address
                val documentationRange = first == 0x20 && second == 0x01 &&
                    (bytes[2].toInt() and 0xff) == 0x0d && (bytes[3].toInt() and 0xff) == 0xb8
                first == 0xfc || first == 0xfd || (first == 0xfe && second in 0x80..0xbf) || documentationRange
            }
            else -> true
        }
    }
}
