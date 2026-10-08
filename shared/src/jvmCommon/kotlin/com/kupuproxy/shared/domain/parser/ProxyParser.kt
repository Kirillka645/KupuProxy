package com.kupuproxy.shared.domain.parser

import com.kupuproxy.shared.domain.model.ProxyProtocol
import com.kupuproxy.shared.domain.model.RawProxyEntry
import com.kupuproxy.shared.domain.model.SecretType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Парсер конфигураций прокси с валидацией.
 *
 * Поддерживает четыре транспорта:
 *  - **MTProto** — `tg://proxy?server=…&port=…&secret=…`, `https://t.me/proxy?…`
 *  - **SOCKS5** — `socks5://user:pass@host:port`, `tg://socks?…`
 *  - **HTTP** — `http://user:pass@host:port`
 *  - **WEB** — `https://user:pass@host:port` (HTTP CONNECT поверх TLS)
 *
 * Форматы входа: ссылки, `host:port:secret`, JSON, YAML, markdown-таблицы, HTML `<code>`,
 * base64-блоки. Всё ограничено по размеру входа и количеству результатов.
 */
object ProxyParser {

    const val MAX_INPUT_CHARS = 4 * 1024 * 1024
    const val MAX_RESULTS = 15_000

    private val LINK_REGEX = Regex(
        """(?:tg://(?:proxy|socks)|https?://(?:t\.me|telegram\.me)/(?:proxy|socks))\?[^\s<>"'`)\]#,]+""",
        RegexOption.IGNORE_CASE,
    )
    private val SOCKS_LINK_REGEX = Regex(
        """socks5h?://[^\s<>"'`)\]#,]+""",
        RegexOption.IGNORE_CASE,
    )
    private val HTTP_PROXY_REGEX = Regex(
        """https?://[^\s:/@]+(?::[^\s@/]*)?@\[[0-9a-fA-F:.]+\]:\d{1,5}""" +
            """|https?://[^\s:/@]+(?::[^\s@/]*)?@[^\s:/@]+\.[^\s:/@]+:\d{1,5}""",
        RegexOption.IGNORE_CASE,
    )
    private val HTTP_PROXY_NO_AUTH_REGEX = Regex(
        """https?://\[[0-9a-fA-F:.]+\]:\d{1,5}|https?://[^\s:/@]+\.[^\s:/@]+:\d{1,5}""",
        RegexOption.IGNORE_CASE,
    )
    private val QUERY_TRIPLE = Regex(
        """(?i)server=([^\s&"'<>]+)&port=(\d{1,5})&secret=([^\s&"'<>]+)""",
    )
    private val HOST_PORT_SECRET = Regex(
        """(?i)^\s*([a-z0-9.\-\[\]:]+)\s*[:\s]\s*(\d{1,5})\s*[:\s]\s*((?:dd|ee)?[0-9a-fA-F]{32,}[0-9a-zA-Z+/=_\-]*)\s*$""",
    )
    private val HOST_PORT_USER = Regex(
        """(?i)^\s*([a-z0-9.\-\[\]:]+)\s*[:\s]\s*(\d{1,5})\s*[:\s]\s*([^\s:@]{1,64})\s*[:\s]\s*([^\s:@]{1,128})\s*$""",
    )
    private val SECRET_HEX = Regex("""(?i)^(?:dd|ee)?[0-9a-f]{32,}$""")
    private val SECRET_B64ISH = Regex("""(?i)^(?:dd|ee)?[0-9a-z+/=_\-]{32,}$""")

    /**
     * base64/base64url-секрет: 16 байт ключа (22 символа), `dd` + ключ (23) или
     * `ee` + ключ + домен (от 23). Раньше требовалось минимум 32 символа, как для hex,
     * и такие секреты отбрасывались — это примерно треть сводного списка proxy-feeds.
     */
    private val SECRET_B64_CHARS = Regex("""^[0-9A-Za-z+/_\-]{22,512}={0,2}$""")
    private val HTML_CODE =
        Regex("""<code[^>]*>(.*?)</code>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BASE64_WHOLE = Regex("""^[A-Za-z0-9+/=_-]+$""")
    private val YAML_TYPE = Regex("""(?i)^-?\s*type:\s*(mtproto|socks5?|http|web)\s*$""")

    /**
     * Поиск YAML-блоков по всему документу. Отдельный regex с MULTILINE: `YAML_TYPE` с
     * `^…$` без этого флага может совпасть только со всем текстом целиком, поэтому
     * `containsMatchIn` по многострочному документу всегда возвращал false и YAML-парсинг
     * в `parse()` был мёртвым кодом.
     */
    private val YAML_TYPE_SCAN = Regex("""(?im)^-?\s*type:\s*(?:mtproto|socks5?|http|web)\s*$""")
    private val YAML_HOST = Regex("""(?i)^(?:server|host|ip):\s*(.+)$""")
    private val YAML_PORT = Regex("""(?i)^port:\s*(\d+)$""")
    private val YAML_SECRET = Regex("""(?i)^(?:secret|password):\s*(.+)$""")
    private val YAML_USER = Regex("""(?i)^(?:username|user|login):\s*(.+)$""")

    // region Публичный API

    fun parse(body: String, sourceId: String = "", sourceName: String = ""): List<RawProxyEntry> {
        if (body.isBlank()) return emptyList()
        val bounded = if (body.length > MAX_INPUT_CHARS) body.take(MAX_INPUT_CHARS) else body
        val decoded = tryDecodeBase64Whole(bounded) ?: bounded
        val text = unescapeProxyText(decoded)
        val collected = LinkedHashMap<String, RawProxyEntry>(minOf(2_048, text.length / 48))

        fun add(entry: RawProxyEntry?) {
            if (entry == null || collected.size >= MAX_RESULTS) return
            if (isPrivateOrReservedHost(entry.host)) return
            collected.putIfAbsent(entry.dedupeKey, entry.copy(sourceId = sourceId, sourceName = sourceName))
        }

        parseLinks(text).forEach(::add)
        parseQueryTriples(text).forEach(::add)

        val trimmed = text.trimStart()
        if (trimmed.startsWith('{') || trimmed.startsWith('[')) parseJson(text).forEach(::add)
        parseLines(
            text,
            includeYaml = YAML_TYPE_SCAN.containsMatchIn(text),
            includeMarkdown = text.indexOf('|') >= 0,
        ).forEach(::add)

        if (text.indexOf('<') >= 0) {
            HTML_CODE.findAll(text).forEach { match ->
                parseLines(match.groupValues[1], includeYaml = false, includeMarkdown = false).forEach(::add)
            }
        }
        return collected.values.toList()
    }

    /** Приводит HTML/JSON-экранированные последовательности к обычному виду. */
    fun unescapeProxyText(text: String): String = text
        .replace("\\u0026", "&")
        .replace("&amp;", "&")
        .replace("&#38;", "&")
        .replace("&quot;", "\"")
        .replace("\\/", "/")
        .replace("%3A", ":", ignoreCase = true)
        .replace("%2F", "/", ignoreCase = true)
        .replace("%3F", "?", ignoreCase = true)
        .replace("%3D", "=", ignoreCase = true)
        .replace("%40", "@", ignoreCase = true)
        .replace("%26", "&", ignoreCase = true)

    fun parseLinks(text: String): List<RawProxyEntry> {
        val out = ArrayList<RawProxyEntry>()
        for (match in LINK_REGEX.findAll(text)) {
            fromUrl(match.value)?.let(out::add)
            if (out.size >= MAX_RESULTS) break
        }
        for (match in SOCKS_LINK_REGEX.findAll(text)) {
            fromUrl(match.value)?.let(out::add)
            if (out.size >= MAX_RESULTS) break
        }
        for (match in HTTP_PROXY_REGEX.findAll(text)) {
            fromUrl(match.value)?.let(out::add)
            if (out.size >= MAX_RESULTS) break
        }
        for (match in HTTP_PROXY_NO_AUTH_REGEX.findAll(text)) {
            // Не тянем обычные http(s)-ссылки на сайты: интересуют только host:port без пути.
            val candidate = match.value
            if (candidate.endsWith("/") || candidate.count { it == '/' } > 2) continue
            fromUrl(candidate)?.let(out::add)
            if (out.size >= MAX_RESULTS) break
        }
        return out
    }

    fun parseQueryTriples(text: String): List<RawProxyEntry> {
        val out = ArrayList<RawProxyEntry>()
        for (match in QUERY_TRIPLE.findAll(text)) {
            val host = decodeQueryValue(match.groupValues[1])
            val secret = decodeQueryValue(match.groupValues[3])
            makeMtprotoEntry(host, match.groupValues[2].toIntOrNull(), secret)?.let(out::add)
            if (out.size >= MAX_RESULTS) break
        }
        return out
    }

    /**
     * Разбирает одну ссылку любого поддерживаемого вида.
     * Возвращает `null`, если формат не распознан или значения не проходят валидацию.
     */
    fun fromUrl(rawUrl: String): RawProxyEntry? {
        val value = rawUrl.trim().trimEnd(')', ']', ',', '"', '\'', '`')
        if (value.isEmpty()) return null
        val lower = value.lowercase()

        return when {
            lower.startsWith("tg://proxy?") || lower.startsWith("tg://socks?") ->
                parseTgQuery(value)

            lower.startsWith("https://t.me/proxy?") || lower.startsWith("http://t.me/proxy?") ||
                lower.startsWith("https://telegram.me/proxy?") || lower.startsWith("http://telegram.me/proxy?") ->
                parseTgQuery("tg://proxy?" + value.substringAfter('?', ""))

            lower.startsWith("https://t.me/socks?") || lower.startsWith("http://t.me/socks?") ||
                lower.startsWith("https://telegram.me/socks?") || lower.startsWith("http://telegram.me/socks?") ->
                parseTgQuery("tg://socks?" + value.substringAfter('?', ""))

            lower.startsWith("socks5://") || lower.startsWith("socks://") || lower.startsWith("socks5h://") ->
                parseAuthorityUrl(value, ProxyProtocol.SOCKS5)

            lower.startsWith("https://") -> parseAuthorityUrl(value, ProxyProtocol.WEB)
            lower.startsWith("http://") -> parseAuthorityUrl(value, ProxyProtocol.HTTP)
            else -> null
        }
    }

    /** `tg://proxy?…` / `tg://socks?…` — query-параметры с сервером, портом и секретом. */
    private fun parseTgQuery(url: String): RawProxyEntry? {
        val isSocks = url.lowercase(Locale.US).contains("socks?")
        val query = url.substringAfter('?', "")
        var host: String? = null
        var port: Int? = null
        var secret: String? = null
        var username: String? = null
        var password: String? = null

        var start = 0
        while (start <= query.length) {
            val end = query.indexOf('&', start).let { if (it < 0) query.length else it }
            val equals = query.indexOf('=', start)
            if (equals in (start + 1) until end) {
                val key = query.substring(start, equals).lowercase(Locale.US)
                val raw = decodeQueryValue(query.substring(equals + 1, end))
                when (key) {
                    "server", "host", "ip" -> if (host == null) host = raw
                    "port" -> if (port == null) port = raw?.toIntOrNull()
                    "secret", "password" -> if (secret == null) secret = raw
                    "username", "user", "login" -> if (username == null) username = raw
                }
            }
            if (end == query.length) break
            start = end + 1
        }

        // Telegram SOCKS-ссылки несут логин/пароль вместо MTProto-секрета.
        if (isSocks) {
            val protocol = ProxyProtocol.SOCKS5
            val normalizedHost = host?.trim()?.trim('[', ']')
            return if (normalizedHost.isNullOrBlank() || port == null || !isValidPort(port)) {
                null
            } else {
                RawProxyEntry(
                    url = toSocksUrl(normalizedHost, port, username, password),
                    host = normalizedHost,
                    port = port,
                    secret = "",
                    protocol = protocol,
                    username = username,
                    password = password,
                )
            }
        }
        return makeMtprotoEntry(host, port, secret)
    }

    /** `socks5://user:pass@host:port`, `http://…`, `https://…` — authority-формат. */
    private fun parseAuthorityUrl(url: String, protocol: ProxyProtocol): RawProxyEntry? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isBlank()) return null

        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isBlank()) return null

        val credentials = authority.substringBeforeLast('@', missingDelimiterValue = "")
        val hostPort = if (credentials.isEmpty()) authority else authority.substringAfterLast('@')

        val username = credentials.substringBefore(':', missingDelimiterValue = "").ifEmpty { null }
            ?.let(::decodeQueryValue)
        val password = credentials.substringAfter(':', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() }?.let(::decodeQueryValue)

        val (host, port) = splitHostPort(hostPort) ?: return null
        if (!isValidPort(port) || host.isBlank()) return null

        return RawProxyEntry(
            url = toAuthorityUrl(protocol, host, port, username, password),
            host = host,
            port = port,
            secret = "",
            protocol = protocol,
            username = username,
            password = password,
        )
    }

    fun parseJson(text: String): List<RawProxyEntry> {
        val trimmed = text.trim()
        return try {
            when {
                trimmed.startsWith('[') -> parseJsonArray(JSONArray(trimmed))
                trimmed.startsWith('{') -> {
                    val root = JSONObject(trimmed)
                    when {
                        root.opt("proxies") is JSONArray -> parseJsonArray(root.getJSONArray("proxies"))
                        root.opt("data") is JSONArray -> parseJsonArray(root.getJSONArray("data"))
                        else -> listOfNotNull(parseJsonObject(root))
                    }
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseLineFormat(text: String): List<RawProxyEntry> = parseLines(text, false, false)

    fun parseHtml(text: String): List<RawProxyEntry> = if (text.indexOf('<') < 0) {
        emptyList()
    } else {
        buildList {
            addAll(parseLinks(text))
            HTML_CODE.findAll(text).forEach { addAll(parseLines(it.groupValues[1], false, false)) }
        }.distinctBy { it.dedupeKey }
    }

    fun parseYamlMtproto(text: String): List<RawProxyEntry> = parseLines(text, true, false)
    fun parseMarkdownTables(text: String): List<RawProxyEntry> = parseLines(text, false, true)

    // endregion

    // region Валидация и сборка записей

    private fun parseLines(text: String, includeYaml: Boolean, includeMarkdown: Boolean): List<RawProxyEntry> {
        val out = ArrayList<RawProxyEntry>()
        // YAML-блок накапливаем целиком: в реальных файлах порядок полей произвольный,
        // и закрывать запись появлением host+port теряло бы username/password.
        var pending = YamlBlock()

        fun flushYaml() {
            pending.build()?.let(out::add)
            pending = YamlBlock()
        }

        for (raw in text.lineSequence()) {
            if (out.size >= MAX_RESULTS) break
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                if (includeYaml && pending.isOpen) flushYaml()
                continue
            }

            if (line.startsWith("tg://", true) || line.startsWith("http://t.me/", true) ||
                line.startsWith("https://t.me/", true) || line.startsWith("socks5://", true) ||
                line.startsWith("socks5h://", true)
            ) {
                if (includeYaml && pending.isOpen) flushYaml()
                fromUrl(line)?.let(out::add)
            }

            val body = line.substringBefore('#').trim()
            HOST_PORT_USER.matchEntire(body)?.let { match ->
                makeAuthEntry(match.groupValues[1], match.groupValues[2].toIntOrNull(), match.groupValues[3], match.groupValues[4])
                    ?.let(out::add)
            }
            HOST_PORT_SECRET.matchEntire(body)?.let { match ->
                makeMtprotoEntry(match.groupValues[1], match.groupValues[2].toIntOrNull(), match.groupValues[3])
                    ?.let(out::add)
            }

            if (includeMarkdown && body.indexOf('|') >= 0) {
                val cells = body.split('|').asSequence().map(String::trim).filter(String::isNotEmpty).toList()
                cells.forEach { cell ->
                    if (cell.contains("proxy?", true) || cell.contains("://", true)) {
                        fromUrl(cell.trim('`'))?.let(out::add)
                    }
                }
                if (cells.size >= 3) {
                    makeMtprotoEntry(cells[0].trim('`'), cells[1].filter(Char::isDigit).toIntOrNull(), cells[2].trim('`'))
                        ?.let(out::add)
                }
            }

            if (!includeYaml) continue

            // Строка `type:` уже обработана. Без continue она попала бы в проверку полей ниже,
            // не совпала бы ни с одним и тут же закрыла бы только что открытый блок.
            val yamlHeader = YAML_TYPE.matchEntire(body)
            if (yamlHeader != null) {
                flushYaml()
                pending = YamlBlock(protocolOf(yamlHeader.groupValues[1].lowercase(Locale.US)))
                continue
            }

            if (pending.isOpen) {
                val consumed = when {
                    YAML_HOST.matchEntire(body)?.let { pending.host = it.groupValues[1].trim().trim('\"', '\''); true } == true -> true
                    YAML_PORT.matchEntire(body)?.let { pending.port = it.groupValues[1].toIntOrNull(); true } == true -> true
                    YAML_SECRET.matchEntire(body)?.let { pending.secret = it.groupValues[1].trim().trim('\"', '\''); true } == true -> true
                    YAML_USER.matchEntire(body)?.let { pending.username = it.groupValues[1].trim().trim('\"', '\''); true } == true -> true
                    else -> false
                }
                // Строка не является полем YAML — блок закрыт.
                if (!consumed) flushYaml()
            }
        }
        if (includeYaml && pending.isOpen) flushYaml()
        return out
    }

    /** Накопитель одного YAML-блока `type: … / server: … / port: …`. */
    private class YamlBlock(val protocol: ProxyProtocol? = null) {
        var host: String? = null
        var port: Int? = null
        var secret: String? = null
        var username: String? = null

        val isOpen: Boolean get() = protocol != null

        fun build(): RawProxyEntry? {
            val h = host ?: return null
            val p = port ?: return null
            if (!isValidPort(p)) return null
            return when (protocol) {
                ProxyProtocol.MTPROTO -> makeMtprotoEntry(h, p, secret)
                ProxyProtocol.SOCKS5 -> makeAuthEntry(h, p, username, secret, ProxyProtocol.SOCKS5)
                ProxyProtocol.HTTP -> makeAuthEntry(h, p, username, secret, ProxyProtocol.HTTP)
                ProxyProtocol.WEB -> makeAuthEntry(h, p, username, secret, ProxyProtocol.WEB)
                null -> null
            }
        }
    }

    private fun protocolOf(type: String): ProxyProtocol = when (type) {
        "socks", "socks5" -> ProxyProtocol.SOCKS5
        "http" -> ProxyProtocol.HTTP
        "web" -> ProxyProtocol.WEB
        else -> ProxyProtocol.MTPROTO
    }

    private fun makeMtprotoEntry(hostValue: String?, port: Int?, secretValue: String?): RawProxyEntry? {
        val host = hostValue?.trim()?.trim('[', ']') ?: return null
        val secret = secretValue?.trim()?.trimEnd(')', ']', '"', '\'', '\\', ',', ';') ?: return null
        if (host.isBlank() || port == null || !isValidPort(port) || !looksLikeSecret(secret)) return null
        val type = classifySecret(secret)
        return RawProxyEntry(
            url = toTgUrl(host, port, secret),
            host = host,
            port = port,
            secret = secret,
            protocol = ProxyProtocol.MTPROTO,
            secretType = type,
            sniDomain = extractSni(secret, type),
        )
    }

    private fun makeAuthEntry(
        hostValue: String?,
        port: Int?,
        user: String?,
        pass: String?,
        protocol: ProxyProtocol = ProxyProtocol.SOCKS5,
    ): RawProxyEntry? {
        val host = hostValue?.trim()?.trim('[', ']') ?: return null
        if (host.isBlank() || port == null || !isValidPort(port)) return null
        val cleanUser = user?.trim()?.trim('\"', '\'')?.ifEmpty { null }
        val cleanPass = pass?.trim()?.trim('\"', '\'')?.ifEmpty { null }
        return RawProxyEntry(
            url = toAuthorityUrl(protocol, host, port, cleanUser, cleanPass),
            host = host,
            port = port,
            secret = "",
            protocol = protocol,
            username = cleanUser,
            password = cleanPass,
        )
    }

    fun classifySecret(secret: String): SecretType {
        val value = secret.trim()
        val lower = value.lowercase(Locale.US)
        if (!SECRET_HEX.matches(value)) {
            decodeBase64Secret(value)?.let { raw ->
                return when {
                    raw.size >= 17 && raw[0] == 0xEE.toByte() -> SecretType.FAKE_TLS
                    raw.size >= 17 && raw[0] == 0xDD.toByte() -> SecretType.PADDED
                    raw.size == 16 -> SecretType.PLAIN
                    else -> SecretType.UNKNOWN
                }
            }
        }
        return when {
            lower.startsWith("ee") -> SecretType.FAKE_TLS
            lower.startsWith("dd") -> SecretType.PADDED
            SECRET_HEX.matches(value) && value.length == 32 -> SecretType.PLAIN
            SECRET_HEX.matches(value) -> SecretType.PADDED
            else -> SecretType.UNKNOWN
        }
    }

    fun extractSni(secret: String, type: SecretType): String? {
        if (type != SecretType.FAKE_TLS) return null
        if (!SECRET_HEX.matches(secret.trim())) {
            val raw = decodeBase64Secret(secret.trim()) ?: return null
            if (raw.size <= 17) return null
            return String(raw.copyOfRange(17, raw.size), StandardCharsets.US_ASCII)
                .trim { it < ' ' || it > '~' }
                .takeIf { it.isNotBlank() && it.contains('.') }
        }
        val hex = secret.drop(2)
        if (hex.length <= 32) return null
        return hexToBytes(hex.substring(32))?.let { bytes ->
            String(bytes, StandardCharsets.US_ASCII)
                .trim { it < ' ' || it > '~' }
                .takeIf { it.isNotBlank() && it.contains('.') }
        }
    }

    fun looksLikeSecret(secret: String): Boolean {
        val value = secret.trim()
        if (value.length in 32..512 && (SECRET_HEX.matches(value) || SECRET_B64ISH.matches(value))) return true
        // Короткие base64-секреты проверяем по длине декодированного ключа, а не по символам.
        return (decodeBase64Secret(value)?.size ?: 0) >= 16
    }

    /** Декодирует base64/base64url-секрет; `null` — не base64 или слишком короткий. */
    private fun decodeBase64Secret(value: String): ByteArray? {
        if (!SECRET_B64_CHARS.matches(value)) return null
        return runCatching {
            val b64 = value.trimEnd('=').replace('-', '+').replace('_', '/')
            val pad = "=".repeat((4 - b64.length % 4) % 4)
            java.util.Base64.getDecoder().decode(b64 + pad)
        }.getOrNull()?.takeIf { it.size >= 16 }
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun isPrivateOrReservedHost(host: String): Boolean {
        val value = host.trim().lowercase(Locale.US).trim('[', ']')
        if (value.isEmpty()) return true
        if (value == "localhost" || value.endsWith(".local") || value == "::1") return true
        if (':' in value) {
            return value.startsWith("fc") || value.startsWith("fd") || value.startsWith("fe8") ||
                value.startsWith("fe9") || value.startsWith("fea") || value.startsWith("feb")
        }
        val ip = parseIpv4(value) ?: return false
        val (first, second) = ip
        return first == 0 || first == 10 || first == 127 ||
            (first == 100 && second in 64..127) || (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) || (first == 192 && second == 168) || first >= 224
    }

    // endregion

    // region Формирование ссылок

    fun toTgUrl(host: String, port: Int, secret: String): String =
        "tg://proxy?server=$host&port=$port&secret=$secret"

    fun toTmeUrl(host: String, port: Int, secret: String): String =
        "https://t.me/proxy?server=$host&port=$port&secret=$secret"

    fun toSocksUrl(host: String, port: Int, username: String?, password: String?): String {
        val credentials = when {
            username.isNullOrEmpty() -> ""
            password.isNullOrEmpty() -> "${username.encode()}@"
            else -> "${username.encode()}:${password.encode()}@"
        }
        return "socks5://$credentials$host:$port"
    }

    fun toAuthorityUrl(
        protocol: ProxyProtocol,
        host: String,
        port: Int,
        username: String?,
        password: String?,
    ): String {
        val scheme = when (protocol) {
            ProxyProtocol.SOCKS5 -> "socks5"
            ProxyProtocol.HTTP -> "http"
            ProxyProtocol.WEB -> "https"
            ProxyProtocol.MTPROTO -> return toTgUrl(host, port, "")
        }
        val credentials = when {
            username.isNullOrEmpty() -> ""
            password.isNullOrEmpty() -> "${username.encode()}@"
            else -> "${username.encode()}:${password.encode()}@"
        }
        return "$scheme://$credentials$host:$port"
    }

    private fun String.encode(): String = URLEncoderCompat.encode(this)

    /** Разбирает `host:port`, включая IPv6 в квадратных скобках. */
    fun splitHostPort(value: String): Pair<String, Int>? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith('[')) {
            val close = trimmed.indexOf(']')
            if (close < 0) return null
            val host = trimmed.substring(1, close)
            val rest = trimmed.substring(close + 1)
            if (!rest.startsWith(":")) return null
            val port = rest.substring(1).toIntOrNull() ?: return null
            return host to port
        }

        val colon = trimmed.lastIndexOf(':')
        if (colon <= 0 || colon == trimmed.length - 1) return null
        // Несколько двоеточий без скобок — это IPv6 без скобок, порт не извлечь.
        if (trimmed.indexOf(':') != colon) return null
        val host = trimmed.substring(0, colon)
        val port = trimmed.substring(colon + 1).toIntOrNull() ?: return null
        return host to port
    }

    // endregion

    // region JSON

    private fun parseJsonArray(array: JSONArray): List<RawProxyEntry> = buildList {
        for (index in 0 until minOf(array.length(), MAX_RESULTS)) {
            when (val value = array.opt(index)) {
                is JSONObject -> parseJsonObject(value)?.let(::add)
                is String -> fromUrl(value)?.let(::add) ?: addAll(parseLineFormat(value))
            }
        }
    }

    private fun parseJsonObject(obj: JSONObject): RawProxyEntry? {
        val keys = HashMap<String, String>()
        obj.keys().forEach { keys[it.lowercase(Locale.US)] = it }

        fun value(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
            keys[name]?.let(obj::opt)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        }

        value("url", "link", "proxy")?.let(::fromUrl)?.let { return it }

        val host = value("host", "server", "ip", "address")
        val port = value("port")?.toIntOrNull()
        val user = value("username", "user", "login")
        val pass = value("password", "pass", "secret")

        val protocol = when (value("type", "protocol", "scheme")?.lowercase(Locale.US)) {
            "socks", "socks5" -> ProxyProtocol.SOCKS5
            "http" -> ProxyProtocol.HTTP
            "web", "https" -> ProxyProtocol.WEB
            else -> null
        }

        // С секретом и без явного типа — это MTProto.
        if (protocol == null) {
            makeMtprotoEntry(host, port, pass)?.let { return it }
            return makeAuthEntry(host, port, user, pass, ProxyProtocol.SOCKS5)
        }

        return when (protocol) {
            ProxyProtocol.MTPROTO -> makeMtprotoEntry(host, port, pass)
            else -> makeAuthEntry(host, port, user, pass, protocol)
        }
    }

    private fun tryDecodeBase64Whole(body: String): String? {
        val compact = body.trim().replace("\n", "").replace("\r", "")
        if (compact.length !in 64..MAX_INPUT_CHARS || !BASE64_WHOLE.matches(compact)) return null
        return decodeBase64Flexible(compact)?.let { bytes ->
            String(bytes, StandardCharsets.UTF_8)
                .takeIf { it.contains("proxy", true) || it.contains("://", true) || it.contains(':') }
        }
    }

    fun decodeBase64Flexible(text: String): ByteArray? {
        val normalized = text.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return runCatching { java.util.Base64.getDecoder().decode(padded) }.getOrNull()
    }

    // endregion

    // region utils

    private fun decodeQueryValue(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0 || hex.any { it !in "0123456789abcdefABCDEF" }) return null
        return ByteArray(hex.length / 2) { index ->
            val high = Character.digit(hex[index * 2], 16)
            val low = Character.digit(hex[index * 2 + 1], 16)
            ((high shl 4) or low).toByte()
        }
    }

    private fun parseIpv4(host: String): Pair<Int, Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val values = parts.map { it.toIntOrNull() ?: return null }
        if (values.any { it !in 0..255 }) return null
        return values[0] to values[1]
    }

    // endregion
}

/** Кодирование userinfo в ссылке. Отдельный объект, чтобы не тянуть URLEncoder в API модуля. */
internal object URLEncoderCompat {
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

    fun encode(value: String): String = buildString(value.length) {
        for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
            val ch = (byte.toInt() and 0xFF).toChar()
            if (ch in UNRESERVED) {
                append(ch)
            } else {
                append('%')
                append(HEX[(byte.toInt() and 0xF0) shr 4])
                append(HEX[byte.toInt() and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}