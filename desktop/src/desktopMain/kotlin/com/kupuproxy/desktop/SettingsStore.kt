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
    val accent: AccentMode = AccentMode.SYSTEM,
    /** Путь к Telegram Desktop, если автопоиск не нашёл его (portable-версия). */
    val telegramPath: String = "",
    /** Потоков на рукопожатие; префлайт получает втрое больше. */
    val scanThreads: Int = 64,
    val connectTimeoutMs: Int = 1200,
    val sortMode: SortMode = SortMode.PING,
    val favoritesFirst: Boolean = true,
    val notifyOnScanEnd: Boolean = true,
    val checkUpdates: Boolean = true,
    val scanOnStart: Boolean = false,
    val navCollapsed: Boolean = false,
) {
    fun toScanConfig(): ScanConfig = ScanConfig(
        connectTimeoutMs = connectTimeoutMs,
        responseTimeoutMs = connectTimeoutMs + 600,
        jitterSamples = jitterSamples,
        maxToCheck = maxToCheck,
        parallelism = scanThreads,
        preflightParallelism = (scanThreads * 3).coerceIn(64, 384),
        preflightTimeoutMs = (connectTimeoutMs * 6 / 10).coerceIn(400, 1200),
        maxLatencyFilterMs = maxLatencyFilterMs,
        protocolFilter = protocolFilter,
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Акцентный цвет: системный (Windows), фирменный бирюзовый или синий Fluent. */
enum class AccentMode { SYSTEM, TEAL, BLUE }

/** Сортировка списка прокси. */
enum class SortMode(val title: String) {
    PING("По задержке"),
    JITTER("По стабильности"),
    PROTOCOL("По протоколу"),
    NEWEST("Сначала новые"),
}

object SettingsStore {

    private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: default

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
            accent = enumOr(props.getProperty("accent"), AccentMode.SYSTEM),
            telegramPath = props.getProperty("telegramPath", ""),
            scanThreads = props.getProperty("scanThreads", "64").toIntOrNull()?.coerceIn(8, 256) ?: 64,
            connectTimeoutMs = props.getProperty("connectTimeout", "1200").toIntOrNull()?.coerceIn(500, 4000) ?: 1200,
            sortMode = enumOr(props.getProperty("sort"), SortMode.PING),
            favoritesFirst = props.getProperty("favoritesFirst", "true").toBoolean(),
            notifyOnScanEnd = props.getProperty("notifyOnScanEnd", "true").toBoolean(),
            checkUpdates = props.getProperty("checkUpdates", "true").toBoolean(),
            scanOnStart = props.getProperty("scanOnStart", "false").toBoolean(),
            navCollapsed = props.getProperty("navCollapsed", "false").toBoolean(),
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
        props.setProperty("accent", settings.accent.name)
        props.setProperty("telegramPath", settings.telegramPath)
        props.setProperty("scanThreads", settings.scanThreads.toString())
        props.setProperty("connectTimeout", settings.connectTimeoutMs.toString())
        props.setProperty("sort", settings.sortMode.name)
        props.setProperty("favoritesFirst", settings.favoritesFirst.toString())
        props.setProperty("notifyOnScanEnd", settings.notifyOnScanEnd.toString())
        props.setProperty("checkUpdates", settings.checkUpdates.toString())
        props.setProperty("scanOnStart", settings.scanOnStart.toString())
        props.setProperty("navCollapsed", settings.navCollapsed.toString())

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
/**
 * Результаты последнего скана: после перезапуска список не пустой, а показывает
 * найденное в прошлый раз (с пометкой времени проверки).
 */
object ResultsStore {

    private val file: File by lazy { File(AppPaths.dataDir(), "last-results.tsv") }

    fun load(target: File = file): List<ProxyRow> {
        if (!target.isFile) return emptyList()
        return runCatching {
            target.readLines(StandardCharsets.UTF_8).mapNotNull(::decode)
        }.getOrDefault(emptyList())
    }

    fun save(rows: List<ProxyRow>, target: File = file) {
        runCatching {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(rows.take(2000).joinToString("\n", transform = ::encode), StandardCharsets.UTF_8)
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { System.err.println("[KupuProxy] не удалось сохранить результаты: ${it.message}") }
    }

    internal fun encode(row: ProxyRow): String = listOf(
        row.url, row.protocol.name, row.host, row.port, row.latencyMs, row.jitterMs, row.samples,
        row.source.replace('\t', ' ').replace('\n', ' '), row.checkedAt,
    ).joinToString("\t")

    internal fun decode(line: String): ProxyRow? {
        val p = line.split('\t')
        if (p.size < 9) return null
        val protocol = ProxyProtocol.entries.firstOrNull { it.name == p[1] } ?: return null
        return ProxyRow(
            url = p[0],
            host = p[2],
            port = p[3].toIntOrNull() ?: return null,
            protocol = protocol,
            latencyMs = p[4].toIntOrNull() ?: return null,
            jitterMs = p[5].toIntOrNull() ?: 0,
            samples = p[6].toIntOrNull() ?: 1,
            source = p[7],
            checkedAt = p[8].toLongOrNull() ?: 0,
        )
    }
}
