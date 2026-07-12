package com.kupuproxy.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

enum class NetworkProfileMode {
    AUTO,
    WIFI,
    MOBILE
}

/**
 * Настройки проверки под конкретный тип сети.
 * Wi‑Fi — агрессивнее, LTE — экономнее по батарее/трафику.
 */
data class ProfileSettings(
    val mode: NetworkProfileMode,
    val label: String,
    val batchSize: Int,
    val connectTimeoutMs: Int,
    val maxPingMs: Int,
    val maxToCheck: Int
) {
    companion object {
        fun forMode(mode: NetworkProfileMode, context: Context? = null): ProfileSettings {
            val effective = when (mode) {
                NetworkProfileMode.AUTO -> detect(context)
                else -> mode
            }
            return when (effective) {
                NetworkProfileMode.MOBILE -> ProfileSettings(
                    mode = effective,
                    label = "LTE / мобильный",
                    batchSize = 10,
                    connectTimeoutMs = 4000,
                    maxPingMs = 12000,
                    maxToCheck = 400
                )
                else -> ProfileSettings(
                    mode = NetworkProfileMode.WIFI,
                    label = "Wi‑Fi",
                    batchSize = 16,
                    connectTimeoutMs = 5000,
                    maxPingMs = 15000,
                    maxToCheck = 800
                )
            }
        }

        fun detect(context: Context?): NetworkProfileMode {
            if (context == null) return NetworkProfileMode.WIFI
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkProfileMode.WIFI

            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
                } else {
                    @Suppress("DEPRECATION")
                    when (cm.activeNetworkInfo?.type) {
                        ConnectivityManager.TYPE_MOBILE -> NetworkProfileMode.MOBILE
                        else -> NetworkProfileMode.WIFI
                    }
                }
            } catch (_: Exception) {
                NetworkProfileMode.WIFI
            }
        }

        fun currentLabel(context: Context): String {
            return when (detect(context)) {
                NetworkProfileMode.MOBILE -> "Сейчас: LTE / мобильный"
                else -> "Сейчас: Wi‑Fi"
            }
        }
    }
}
