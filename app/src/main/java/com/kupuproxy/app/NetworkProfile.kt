package com.kupuproxy.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetworkProfileMode {
    AUTO,
    WIFI,
    MOBILE,
}

const val MAX_SCAN_PROXIES = 15_000

/**
 * Настройки проверки под конкретный тип сети. Wi‑Fi — агрессивнее, LTE — экономнее по
 * батарее/трафику.
 */
data class ProfileSettings(
    val mode: NetworkProfileMode,
    val label: String,
    /** Параллельных MTProto-проверок */
    val batchSize: Int,
    val connectTimeoutMs: Int,
    val maxPingMs: Int,
    val maxToCheck: Int,
    /** Остановить скан, когда нашли столько рабочих (0 = без лимита) */
    val stopWhenFound: Int = 0,
) {
    companion object {
        fun forMode(mode: NetworkProfileMode, context: Context? = null): ProfileSettings {
            val effective =
                when (mode) {
                    NetworkProfileMode.AUTO -> detect(context)
                    else -> mode
                }
            return when (effective) {
                NetworkProfileMode.MOBILE ->
                    ProfileSettings(
                        mode = effective,
                        label = context?.getString(R.string.profile_mobile) ?: "LTE / mobile",
                        batchSize = 64, // параллельных проверок
                        connectTimeoutMs = 1200, // TCP
                        maxPingMs = 6000,
                        maxToCheck = MAX_SCAN_PROXIES,
                        stopWhenFound = 0,
                    )
                else ->
                    ProfileSettings(
                        mode = NetworkProfileMode.WIFI,
                        label = context?.getString(R.string.profile_wifi) ?: "Wi-Fi",
                        batchSize = 80,
                        connectTimeoutMs = 1300,
                        maxPingMs = 8000,
                        maxToCheck = MAX_SCAN_PROXIES,
                        stopWhenFound = 0,
                    )
            }
        }

        fun detect(context: Context?): NetworkProfileMode {
            if (context == null) return NetworkProfileMode.WIFI
            val cm =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return NetworkProfileMode.WIFI

            return try {
                val network = cm.activeNetwork ?: return NetworkProfileMode.WIFI
                val caps = cm.getNetworkCapabilities(network) ?: return NetworkProfileMode.WIFI
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                        NetworkProfileMode.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                        NetworkProfileMode.MOBILE
                    else -> NetworkProfileMode.WIFI
                }
            } catch (_: Exception) {
                NetworkProfileMode.WIFI
            }
        }

        fun currentLabel(context: Context): String {
            return when (detect(context)) {
                NetworkProfileMode.MOBILE -> context.getString(R.string.profile_current_mobile)
                else -> context.getString(R.string.profile_current_wifi)
            }
        }
    }
}
