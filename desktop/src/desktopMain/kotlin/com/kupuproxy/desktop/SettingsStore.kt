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
    /** Включённые встроенные источники из `proxy-feeds/`. */
    val stockFeeds: Set<String> = StockFeeds.defaultEnabledIds,
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

    private val file: File by lazy { File(AppPaths.dataDir(), "settings.properties") }

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
            stockFeeds = if (props.containsKey("stockFeeds")) {
                props.getProperty("stockFeeds").orEmpty()
                    .split(',')
                    .map(String::trim)
                    .filter { StockFeeds.byId(it) != null }
                    .toSet()
            } else {
                StockFeeds.defaultEnabledIds
            },
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
        props.setProperty("stockFeeds", settings.stockFeeds.joinToString(","))

        // Пишем через временный файл: прерванная запись не должна терять настройки.
        // Properties.load(InputStream) читает ISO-8859-1, а store(Writer) пишет символы как есть —
        // поэтому пишем через OutputStream: не-ASCII (кириллица в URL избранного) экранируется \uXXXX.
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { props.store(it, null) }
            runCatching {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            }.recoverCatching {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }.getOrThrow()
        }.onFailure { System.err.println("[KupuProxy] не удалось сохранить настройки: ${it.message}") }
    }
}

/** Источник списка прокси для проверки: локальный файл или URL с текстовым списком. */
data class ProxyInput(val label: String, val body: String)

object ProxySource {

    fun fromFile(file: File): ProxyInput {
        require(file.isFile) { "Файл не найден: ${file.name}" }
        require(file.length() <= StockFeeds.MAX_FEED_BYTES) { "Файл больше ${StockFeeds.MAX_FEED_BYTES / 1024 / 1024} МБ" }
        val text = file.readText(StandardCharsets.UTF_8)
        return ProxyInput(file.name, text)
    }

    /** Загрузка блокирующая — вызывать из фонового потока. */
    fun fromUrl(url: String): ProxyInput {
        val text = StockFeeds.download(url)
        return ProxyInput(url.substringAfterLast('/').substringBefore('?').ifBlank { url }, text)
    }
}