package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import com.kupuproxy.shared.domain.model.RawProxyEntry
import com.kupuproxy.shared.domain.parser.ProxyParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Встроенные («стоковые») источники прокси.
 *
 * Это зеркала из каталога `proxy-feeds/` репозитория, которые GitHub Action обновляет раз
 * в 4 часа. Клиент берёт их в таком порядке:
 *
 * 1. `raw.githubusercontent.com` — самая свежая копия ветки `main`;
 * 2. jsDelivr CDN — когда raw.githubusercontent недоступен (частая ситуация в РФ/ИР);
 * 3. снимок, вшитый в сборку (`stock-feeds/` в ресурсах), — когда сети нет вообще.
 *
 * Так кнопка «Запустить скан» работает сразу после установки, без поиска файлов и URL.
 */
data class StockFeed(
    val id: String,
    val title: String,
    val description: String,
    /** Имя файла в `proxy-feeds/` и в ресурсах `stock-feeds/`. */
    val file: String,
    val protocol: ProxyProtocol,
    /** Строки вида `host:port` без схемы трактуются как этот протокол. */
    val bareHostPortAs: ProxyProtocol? = null,
    /** Сколько записей максимум брать из фида (крупные списки перемешиваются и режутся). */
    val limit: Int = ProxyParser.MAX_RESULTS,
    val enabledByDefault: Boolean = true,
)

/** Откуда в итоге был взят фид. */
enum class FeedOrigin(val label: String) {
    GITHUB("GitHub"),
    CDN("jsDelivr"),
    BUNDLED("встроенный снимок"),
}

data class LoadedFeed(
    val feed: StockFeed,
    val origin: FeedOrigin?,
    val entries: List<RawProxyEntry>,
    val error: String? = null,
)

object StockFeeds {

    const val OWNER = "Kirillka645"
    const val REPO = "KupuProxy"
    const val BRANCH = "main"
    const val RESOURCE_DIR = "stock-feeds"

    /** Ограничение на размер одного фида: hookzof весит ~0.8 МБ, оставляем запас. */
    const val MAX_FEED_BYTES = 8 * 1024 * 1024

    val all: List<StockFeed> = listOf(
        StockFeed(
            id = "mtproto_merged",
            title = "MTProto · сводный список",
            description = "Kort, Shablin, ALIILAPRO и dubblebyte без дублей",
            file = "mtproto_merged.txt",
            protocol = ProxyProtocol.MTPROTO,
        ),
        StockFeed(
            id = "kort_socks5",
            title = "SOCKS5 · Kort",
            description = "Отобранные SOCKS5 из telegram-proxy-collector",
            file = "kort_socks5.txt",
            protocol = ProxyProtocol.SOCKS5,
        ),
        StockFeed(
            id = "hookzof_socks5",
            title = "SOCKS5 · hookzof",
            description = "Большой публичный список; берётся случайная выборка",
            file = "hookzof_socks5.txt",
            protocol = ProxyProtocol.SOCKS5,
            bareHostPortAs = ProxyProtocol.SOCKS5,
            limit = 1_500,
            enabledByDefault = false,
        ),
    )

    val defaultEnabledIds: Set<String> = all.filter { it.enabledByDefault }.map { it.id }.toSet()

    fun byId(id: String): StockFeed? = all.firstOrNull { it.id == id }

    fun githubUrl(feed: StockFeed): String =
        "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/proxy-feeds/${feed.file}"

    fun cdnUrl(feed: StockFeed): String =
        "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$BRANCH/proxy-feeds/${feed.file}"

    /** Дата вшитого снимка (пишется Gradle-задачей при сборке), либо `null`. */
    fun bundledSnapshotDate(): String? = snapshotDate

    private val snapshotDate: String? by lazy {
        runCatching {
            resourceStream("snapshot.properties")?.use { stream ->
                java.util.Properties().apply { load(stream) }.getProperty("generated")
            }
        }.getOrNull()
    }

    fun bundledText(feed: StockFeed): String? = runCatching {
        resourceStream(feed.file)?.use { readBounded(it).toString(StandardCharsets.UTF_8) }
    }.getOrNull()

    /**
     * Загружает фид: сеть → CDN → вшитый снимок. Пустой или нераспознанный ответ считается
     * ошибкой — иначе «сломанное» зеркало перекрыло бы рабочий снимок.
     */
    fun load(
        feed: StockFeed,
        offline: Boolean = false,
        fetch: (String) -> String = ::download,
    ): LoadedFeed {
        val errors = ArrayList<String>()
        if (!offline) {
            for ((origin, url) in listOf(FeedOrigin.GITHUB to githubUrl(feed), FeedOrigin.CDN to cdnUrl(feed))) {
                val entries = runCatching { parse(feed, fetch(url)) }
                    .onFailure { errors += "${origin.label}: ${it.message ?: it.javaClass.simpleName}" }
                    .getOrNull()
                if (!entries.isNullOrEmpty()) return LoadedFeed(feed, origin, entries)
                if (entries != null) errors += "${origin.label}: пустой список"
            }
        }
        val bundled = bundledText(feed)?.let { parse(feed, it) }.orEmpty()
        if (bundled.isNotEmpty()) return LoadedFeed(feed, FeedOrigin.BUNDLED, bundled, errors.joinToString("; ").ifEmpty { null })
        return LoadedFeed(feed, null, emptyList(), errors.joinToString("; ").ifEmpty { "фид недоступен" })
    }

    /** Разбирает текст фида с учётом особенностей конкретного источника. */
    fun parse(feed: StockFeed, body: String, random: kotlin.random.Random = kotlin.random.Random.Default): List<RawProxyEntry> {
        val prepared = feed.bareHostPortAs?.let { normalizeBareHostPort(body, it) } ?: body
        val parsed = ProxyParser.parse(prepared, sourceId = "stock:${feed.id}", sourceName = feed.title)
            .filter { it.protocol == feed.protocol }
        return if (parsed.size > feed.limit) parsed.shuffled(random).take(feed.limit) else parsed
    }

    /**
     * Превращает строки `host:port` в ссылки протокола. Общий парсер такие строки не
     * принимает — у них нет ни схемы, ни секрета, и протокол по ним не определить.
     */
    fun normalizeBareHostPort(body: String, protocol: ProxyProtocol): String {
        val scheme = when (protocol) {
            ProxyProtocol.SOCKS5 -> "socks5"
            ProxyProtocol.HTTP -> "http"
            ProxyProtocol.WEB -> "https"
            ProxyProtocol.MTPROTO -> return body
        }
        return body.lineSequence().joinToString("\n") { raw ->
            val line = raw.trim()
            if (BARE_HOST_PORT.matches(line)) "$scheme://$line" else raw
        }
    }

    private val BARE_HOST_PORT = Regex("""^[A-Za-z0-9.\-]+:\d{1,5}$""")

    /** HTTP-загрузка с таймаутами, лимитом размера и только по http/https. */
    fun download(url: String): String {
        val uri = URI(url)
        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) { "поддерживаются только http и https" }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "KupuProxy-Desktop/$DESKTOP_VERSION")
            connection.setRequestProperty("Accept", "text/plain, application/json, */*")
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val declared = connection.contentLengthLong
            if (declared > MAX_FEED_BYTES) error("слишком большой ответ ($declared байт)")
            return connection.inputStream.use { readBounded(it) }.toString(StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    internal fun readBounded(input: InputStream, limit: Int = MAX_FEED_BYTES): ByteArrayOutputStream {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) error("ответ больше ${limit / 1024 / 1024} МБ")
            out.write(buffer, 0, read)
        }
        return out
    }

    private fun resourceStream(name: String): InputStream? =
        StockFeeds::class.java.classLoader?.getResourceAsStream("$RESOURCE_DIR/$name")
}
