package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties

/** Настройки десктоп-клиента. Хранятся в properties-файле рядом с другими данными. */
data class DesktopSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autostart: Boolean = false,
    val minimizeToTray: Boolean = true,
    val startMinimized: Boolean = false,
    val localProxyEnabled: Boolean = false,
    val jitterSamples: Int = 3,
    val maxToCheck: Int = 2000,
    val maxLatencyFilterMs: Int = 5000,
    val protocolFilter: Set<ProxyProtocol> = ProxyProtocol.entries.toSet(),
    val favorites: Set<String> = emptySet(),
) {
    fun toScanConfig(): ScanConfig = ScanConfig(
        jitterSamples = jitterSamples,
        maxToCheck = maxToCheck,
        maxLatencyFilterMs = maxLatencyFilterMs,
        protocolFilter = protocolFilter,
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object SettingsStore {

    private val file: File by lazy {
        val dir = File(System.getProperty("user.home"), ".kupuproxy")
        dir.mkdirs()
        File(dir, "settings.properties")
    }

    fun load(): DesktopSettings {
        if (!file.exists()) return DesktopSettings()
        val props = Properties()
        runCatching { file.inputStream().use(props::load) }
        val favorites = props.getProperty("favorites").orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        val protocols = props.getProperty("protocols").orEmpty()
            .split(',')
            .mapNotNull { ProxyProtocol.entries.firstOrNull { p -> p.name.equals(it.trim(), true) } }
            .toSet()

        return DesktopSettings(
            themeMode = runCatching { ThemeMode.valueOf(props.getProperty("theme", "SYSTEM")) }
                .getOrDefault(ThemeMode.SYSTEM),
            autostart = props.getProperty("autostart", "false").toBoolean(),
            minimizeToTray = props.getProperty("minimizeToTray", "true").toBoolean(),
            startMinimized = props.getProperty("startMinimized", "false").toBoolean(),
            localProxyEnabled = props.getProperty("localProxy", "false").toBoolean(),
            jitterSamples = props.getProperty("jitterSamples", "3").toIntOrNull()?.coerceIn(1, 5) ?: 3,
            maxToCheck = props.getProperty("maxToCheck", "2000").toIntOrNull()?.coerceIn(50, 15_000) ?: 2000,
            maxLatencyFilterMs = props.getProperty("maxLatency", "5000").toIntOrNull()?.coerceIn(100, 15_000) ?: 5000,
            protocolFilter = protocols.ifEmpty { ProxyProtocol.entries.toSet() },
            favorites = favorites,
        )
    }

    fun save(settings: DesktopSettings) {
        val props = Properties()
        props.setProperty("theme", settings.themeMode.name)
        props.setProperty("autostart", settings.autostart.toString())
        props.setProperty("minimizeToTray", settings.minimizeToTray.toString())
        props.setProperty("startMinimized", settings.startMinimized.toString())
        props.setProperty("localProxy", settings.localProxyEnabled.toString())
        props.setProperty("jitterSamples", settings.jitterSamples.toString())
        props.setProperty("maxToCheck", settings.maxToCheck.toString())
        props.setProperty("maxLatency", settings.maxLatencyFilterMs.toString())
        props.setProperty("protocols", settings.protocolFilter.joinToString(",") { it.name })
        props.setProperty("favorites", settings.favorites.joinToString("|"))

        // Пишем через временный файл: прерванная запись не должна терять настройки.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        val buffer = java.io.StringWriter()
        props.store(buffer, null)
        tmp.writeText(buffer.toString(), StandardCharsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
            tmp.delete()
        }
    }
}

/** Источник списка прокси для проверки: локальный файл или URL с текстовым списком. */
data class ProxyInput(val label: String, val body: String)

object ProxySource {

    fun fromFile(file: File): ProxyInput {
        val text = file.readText(StandardCharsets.UTF_8)
        return ProxyInput(file.name, text)
    }

    suspend fun fromUrl(url: String): ProxyInput {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            error("HTTP $code")
        }
        val text = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        connection.disconnect()
        return ProxyInput(url.substringAfterLast('/'), text)
    }
}