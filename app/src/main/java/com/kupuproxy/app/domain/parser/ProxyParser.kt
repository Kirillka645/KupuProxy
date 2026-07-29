package com.kupuproxy.app.domain.parser

import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SecretType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Fast, bounded multi-format MTProto proxy parser. */
object ProxyParser {
    const val MAX_INPUT_CHARS = 4 * 1024 * 1024
    const val MAX_RESULTS = 15_000

    private val LINK_REGEX = Regex(
        """(?:tg://(?:proxy|socks)|https?://(?:t\.me|telegram\.me)/(?:proxy|socks))\?[^\s<>"'`)\]#,]+""",
        RegexOption.IGNORE_CASE
    )
    private val QUERY_TRIPLE = Regex(
        """(?i)server=([^\s&"'<>]+)&port=(\d{1,5})&secret=([^\s&"'<>]+)"""
    )
    private val HOST_PORT_SECRET = Regex(
        """(?i)^\s*([a-z0-9.\-\[\]:]+)\s*[:\s]\s*(\d{1,5})\s*[:\s]\s*((?:dd|ee)?[0-9a-fA-F]{32,}[0-9a-zA-Z+/=_\-]*)\s*$"""
    )
    private val SECRET_HEX = Regex("""(?i)^(?:dd|ee)?[0-9a-f]{32,}$""")
    private val SECRET_B64ISH = Regex("""(?i)^(?:dd|ee)?[0-9a-z+/=_\-]{32,}$""")
    private val HTML_CODE = Regex("""<code[^>]*>(.*?)</code>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BASE64_WHOLE = Regex("""^[A-Za-z0-9+/=_-]+$""")
    private val YAML_TYPE = Regex("""(?i)^-?\s*type:\s*mtproto\s*$""")
    private val YAML_HOST = Regex("""(?i)^(?:server|host|ip):\s*(.+)$""")
    private val YAML_PORT = Regex("""(?i)^port:\s*(\d+)$""")
    private val YAML_SECRET = Regex("""(?i)^(?:secret|password):\s*(.+)$""")

    fun parse(body: String, sourceId: String = "", sourceName: String = ""): List<RawProxyEntry> {
        if (body.isBlank()) return emptyList()
        val bounded = if (body.length > MAX_INPUT_CHARS) body.take(MAX_INPUT_CHARS) else body
        val decoded = tryDecodeBase64Whole(bounded) ?: bounded
        val text = unescapeProxyText(decoded)
        val collected = LinkedHashMap<String, RawProxyEntry>(minOf(1_024, text.length / 48))

        fun add(entry: RawProxyEntry?) {
            if (entry == null || collected.size >= MAX_RESULTS) return
            val host = entry.host.trim()
            val secret = entry.secret.trim()
            if (!isValidPort(entry.port) || !looksLikeSecret(secret)) return
            val key = "${host.lowercase(Locale.US)}:${entry.port}:${secret.lowercase(Locale.US)}"
            collected.putIfAbsent(
                key,
                entry.copy(
                    url = toTgUrl(host, entry.port, secret),
                    host = host,
                    secret = secret,
                    sourceId = sourceId,
                    sourceName = sourceName
                )
            )
        }

        parseLinks(text).forEach(::add)
        parseQueryTriples(text).forEach(::add)

        val trimmed = text.trimStart()
        if (trimmed.startsWith('{') || trimmed.startsWith('[')) parseJson(text).forEach(::add)
        parseLines(text, includeYaml = text.contains("mtproto", ignoreCase = true), includeMarkdown = text.indexOf('|') >= 0)
            .forEach(::add)
        if (text.indexOf('<') >= 0) {
            HTML_CODE.findAll(text).forEach { match ->
                parseLines(match.groupValues[1], includeYaml = false, includeMarkdown = false).forEach(::add)
            }
        }
        return collected.values.toList()
    }

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
        .replace("%26", "&", ignoreCase = true)

    fun parseLinks(text: String): List<RawProxyEntry> {
        val out = ArrayList<RawProxyEntry>()
        for (match in LINK_REGEX.findAll(text)) {
            fromUrl(match.value)?.let(out::add)
            if (out.size >= MAX_RESULTS) break
        }
        return out
    }

    fun parseQueryTriples(text: String): List<RawProxyEntry> {
        val out = ArrayList<RawProxyEntry>()
        for (match in QUERY_TRIPLE.findAll(text)) {
            val host = decodeQueryValue(match.groupValues[1])
            val secret = decodeQueryValue(match.groupValues[3])
            makeEntry(host, match.groupValues[2].toIntOrNull(), secret)?.let(out::add)
            if (out.size >= MAX_RESULTS) break
        }
        return out
    }

    fun fromUrl(rawUrl: String): RawProxyEntry? {
        val url = normalizeLink(rawUrl) ?: return null
        val query = url.substringAfter('?', "")
        var host: String? = null
        var port: Int? = null
        var secret: String? = null
        var start = 0
        while (start <= query.length) {
            val end = query.indexOf('&', start).let { if (it < 0) query.length else it }
            val equals = query.indexOf('=', start)
            if (equals in (start + 1) until end) {
                val key = query.substring(start, equals).lowercase(Locale.US)
                val value = decodeQueryValue(query.substring(equals + 1, end))
                when (key) {
                    "server", "host", "ip" -> if (host == null) host = value
                    "port" -> if (port == null) port = value?.toIntOrNull()
                    "secret", "password" -> if (secret == null) secret = value
                }
            }
            if (end == query.length) break
            start = end + 1
        }
        return makeEntry(host, port, secret)
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
    fun parseHtml(text: String): List<RawProxyEntry> = if (text.indexOf('<') < 0) emptyList() else buildList {
        addAll(parseLinks(text))
        HTML_CODE.findAll(text).forEach { addAll(parseLines(it.groupValues[1], false, false)) }
    }.distinctBy { it.url.lowercase(Locale.US) }
    fun parseYamlMtproto(text: String): List<RawProxyEntry> = parseLines(text, true, false)
    fun parseMarkdownTables(text: String): List<RawProxyEntry> = parseLines(text, false, true)

    private fun parseLines(text: String, includeYaml: Boolean, includeMarkdown: Boolean): List<RawProxyEntry> {
        val out = ArrayList<RawProxyEntry>()
        var yamlHost: String? = null
        var yamlPort: Int? = null
        var yamlSecret: String? = null
        var inYaml = false
        for (raw in text.lineSequence()) {
            if (out.size >= MAX_RESULTS) break
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            if (line.startsWith("tg://", true) || line.startsWith("http://t.me/", true) || line.startsWith("https://t.me/", true)) {
                fromUrl(line)?.let(out::add)
            }
            val hash = line.indexOf('#')
            if (hash > 0) line = line.substring(0, hash).trim()
            HOST_PORT_SECRET.matchEntire(line)?.let { match ->
                makeEntry(match.groupValues[1], match.groupValues[2].toIntOrNull(), match.groupValues[3])?.let(out::add)
            }

            if (includeMarkdown && line.indexOf('|') >= 0) {
                val cells = line.split('|').asSequence().map(String::trim).filter(String::isNotEmpty).toList()
                cells.forEach { cell -> if (cell.contains("proxy?", true)) fromUrl(cell.trim('`'))?.let(out::add) }
                if (cells.size >= 3) {
                    makeEntry(cells[0].trim('`'), cells[1].filter(Char::isDigit).toIntOrNull(), cells[2].trim('`'))?.let(out::add)
                }
            }

            if (includeYaml) {
                if (YAML_TYPE.matches(line)) {
                    inYaml = true
                    yamlHost = null; yamlPort = null; yamlSecret = null
                    continue
                }
                if (inYaml) {
                    YAML_HOST.matchEntire(line)?.let { yamlHost = it.groupValues[1].trim().trim('\"', '\'') }
                    YAML_PORT.matchEntire(line)?.let { yamlPort = it.groupValues[1].toIntOrNull() }
                    YAML_SECRET.matchEntire(line)?.let { yamlSecret = it.groupValues[1].trim().trim('\"', '\'') }
                    if (yamlHost != null && yamlPort != null && yamlSecret != null) {
                        makeEntry(yamlHost, yamlPort, yamlSecret)?.let(out::add)
                        inYaml = false
                    }
                }
            }
        }
        return out
    }

    private fun makeEntry(hostValue: String?, port: Int?, secretValue: String?): RawProxyEntry? {
        val host = hostValue?.trim()?.trim('[', ']') ?: return null
        val secret = secretValue?.trim()?.trimEnd(')', ']', '"', '\'', '\\', ',', ';') ?: return null
        if (host.isBlank() || port == null || !isValidPort(port) || !looksLikeSecret(secret)) return null
        val type = classifySecret(secret)
        return RawProxyEntry(toTgUrl(host, port, secret), host, port, secret, type, extractSni(secret, type))
    }

    fun classifySecret(secret: String): SecretType {
        val value = secret.trim()
        val lower = value.lowercase(Locale.US)
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
        return value.length in 32..512 && (SECRET_HEX.matches(value) || SECRET_B64ISH.matches(value))
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun isPrivateOrReservedHost(host: String): Boolean {
        val value = host.trim().lowercase(Locale.US).trim('[', ']')
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

    fun toTgUrl(host: String, port: Int, secret: String): String =
        "tg://proxy?server=$host&port=$port&secret=$secret"
    fun toTmeUrl(host: String, port: Int, secret: String): String =
        "https://t.me/proxy?server=$host&port=$port&secret=$secret"

    private fun normalizeLink(raw: String): String? {
        var value = raw.trim().trimEnd(')', ']', ',', '"', '\'', '`')
        value = when {
            value.startsWith("https://t.me/", true) || value.startsWith("http://t.me/", true) ||
                value.startsWith("https://telegram.me/", true) -> "tg://proxy?" + value.substringAfter('?', "")
            value.startsWith("tg://", true) -> value
            else -> return null
        }
        return value.takeIf { it.contains("proxy?", true) || it.contains("socks?", true) }
    }

    private fun decodeQueryValue(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()

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
        return makeEntry(value("host", "server", "ip", "address"), value("port")?.toIntOrNull(), value("secret", "password", "key"))
    }

    private fun tryDecodeBase64Whole(body: String): String? {
        val compact = body.trim().replace("\n", "").replace("\r", "")
        if (compact.length !in 64..MAX_INPUT_CHARS || !BASE64_WHOLE.matches(compact)) return null
        return decodeBase64Flexible(compact)?.let { bytes ->
            String(bytes, StandardCharsets.UTF_8).takeIf { it.contains("proxy", true) || it.contains(':') }
        }
    }

    private fun decodeBase64Flexible(text: String): ByteArray? {
        val normalized = text.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return runCatching { java.util.Base64.getDecoder().decode(padded) }.getOrElse {
            runCatching {
                val clazz = Class.forName("android.util.Base64")
                clazz.getMethod("decode", String::class.java, Int::class.javaPrimitiveType).invoke(null, text, 0) as ByteArray
            }.getOrNull()
        }
    }

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
}
