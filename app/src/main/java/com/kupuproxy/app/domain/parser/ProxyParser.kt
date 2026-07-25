package com.kupuproxy.app.domain.parser

import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SecretType
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Автоопределение формата и извлечение MTProto proxy-записей.
 */
object ProxyParser {

    private val LINK_REGEX = Regex(
        """(?:tg://(?:proxy|socks)|https?://(?:t\.me|telegram\.me)/(?:proxy|socks))\?[^\s<>"'`)\]#,]+""",
        RegexOption.IGNORE_CASE
    )

    /** server=…&port=…&secret=… даже без tg:// (HTML/JSON/markdown) */
    private val QUERY_TRIPLE = Regex(
        """(?i)server=([^\s&"'<>]+)&port=(\d{2,5})&secret=([^\s&"'<>]+)"""
    )

    private val HOST_PORT_SECRET = Regex(
        """(?i)^\s*([a-z0-9.\-\[\]]+)\s*[:\s]\s*(\d{1,5})\s*[:\s]\s*((?:dd|ee)?[0-9a-fA-F]{32,}[0-9a-zA-Z+/=_\-]*)\s*$"""
    )

    private val SECRET_HEX = Regex("""(?i)^(?:dd|ee)?[0-9a-fA-F]{32,}$""")
    private val SECRET_B64ISH = Regex("""(?i)^(?:dd|ee)?[0-9a-zA-Z+/=_\-]{32,}$""")

    fun parse(
        body: String,
        sourceId: String = "",
        sourceName: String = ""
    ): List<RawProxyEntry> {
        if (body.isBlank()) return emptyList()

        val decoded = tryDecodeBase64Whole(body) ?: body
        val collected = LinkedHashMap<String, RawProxyEntry>()

        fun addAll(list: List<RawProxyEntry>) {
            for (e in list) {
                val key = "${e.host.lowercase()}:${e.port}:${e.secret.lowercase()}"
                collected.putIfAbsent(key, e.copy(sourceId = sourceId, sourceName = sourceName))
            }
        }

        val unescaped = unescapeProxyText(decoded)
        addAll(parseLinks(unescaped))
        addAll(parseQueryTriples(unescaped))
        addAll(parseJson(unescaped))
        addAll(parseLineFormat(unescaped))
        addAll(parseHtml(unescaped))
        addAll(parseYamlMtproto(unescaped))
        addAll(parseMarkdownTables(unescaped))

        return collected.values.toList()
    }

    fun unescapeProxyText(text: String): String {
        return text
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
    }

    fun parseLinks(text: String): List<RawProxyEntry> {
        return LINK_REGEX.findAll(text).mapNotNull { m ->
            normalizeLink(m.value)?.let { fromUrl(it) }
        }.toList()
    }

    fun parseQueryTriples(text: String): List<RawProxyEntry> {
        return QUERY_TRIPLE.findAll(text).mapNotNull { m ->
            val host = m.groupValues[1].trim()
            val port = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val secret = m.groupValues[3].trim().trimEnd(')', ']', '"', '\'', '\\')
            if (!isValidPort(port) || !looksLikeSecret(secret)) return@mapNotNull null
            val type = classifySecret(secret)
            RawProxyEntry(
                url = toTgUrl(host, port, secret),
                host = host,
                port = port,
                secret = secret,
                secretType = type,
                sniDomain = extractSni(secret, type)
            )
        }.toList()
    }

    fun fromUrl(rawUrl: String): RawProxyEntry? {
        val url = normalizeLink(rawUrl) ?: return null
        val q = url.substringAfter('?', "")
        val params = q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null
            else {
                val k = it.substring(0, i).lowercase(Locale.US)
                val v = URLDecoder.decode(it.substring(i + 1), "UTF-8")
                k to v
            }
        }.toMap()
        val host = params["server"] ?: params["host"] ?: params["ip"] ?: return null
        val port = params["port"]?.toIntOrNull() ?: return null
        val secret = params["secret"] ?: params["password"] ?: return null
        if (!isValidPort(port) || !looksLikeSecret(secret)) return null
        val type = classifySecret(secret)
        val sni = extractSni(secret, type)
        return RawProxyEntry(
            url = toTgUrl(host, port, secret),
            host = host.trim(),
            port = port,
            secret = secret.trim(),
            secretType = type,
            sniDomain = sni
        )
    }

    fun parseJson(text: String): List<RawProxyEntry> {
        val t = text.trim()
        if (!(t.startsWith("[") || t.startsWith("{"))) return emptyList()
        return try {
            when {
                t.startsWith("[") -> parseJsonArray(JSONArray(t))
                else -> {
                    val obj = JSONObject(t)
                    when {
                        obj.has("proxies") -> parseJsonArray(obj.getJSONArray("proxies"))
                        obj.has("data") -> parseJsonArray(obj.getJSONArray("data"))
                        else -> listOfNotNull(parseJsonObject(obj))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseLineFormat(text: String): List<RawProxyEntry> {
        val out = mutableListOf<RawProxyEntry>()
        for (raw in text.lineSequence()) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#") || line.startsWith("//")) continue
            val hash = line.indexOf('#')
            if (hash > 0) line = line.substring(0, hash).trim()
            val m = HOST_PORT_SECRET.matchEntire(line) ?: continue
            val host = m.groupValues[1].trim()
            val port = m.groupValues[2].toIntOrNull() ?: continue
            val secret = m.groupValues[3].trim()
            if (!isValidPort(port) || !looksLikeSecret(secret)) continue
            val type = classifySecret(secret)
            out += RawProxyEntry(
                url = toTgUrl(host, port, secret),
                host = host,
                port = port,
                secret = secret,
                secretType = type,
                sniDomain = extractSni(secret, type)
            )
        }
        return out
    }

    fun parseHtml(text: String): List<RawProxyEntry> {
        if (!text.contains('<', ignoreCase = true)) return emptyList()
        val links = parseLinks(text)
        val codes = Regex(
            """(?is)<code[^>]*>(.*?)</code>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(text).flatMap { parseLinks(it.groupValues[1]) + parseLineFormat(it.groupValues[1]) }
        return (links + codes).distinctBy { it.url.lowercase() }
    }

    fun parseYamlMtproto(text: String): List<RawProxyEntry> {
        if (!text.contains("mtproto", ignoreCase = true)) return emptyList()
        val out = mutableListOf<RawProxyEntry>()
        var host: String? = null
        var port: Int? = null
        var secret: String? = null
        var inMt = false
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.matches(Regex("""(?i)type:\s*mtproto"""))) {
                inMt = true
                continue
            }
            if (!inMt) continue
            when {
                t.matches(Regex("""(?i)(server|host|ip):\s*(.+)""")) -> {
                    host = t.substringAfter(':').trim().trim('"', '\'')
                }
                t.matches(Regex("""(?i)port:\s*(\d+)""")) -> {
                    port = t.substringAfter(':').trim().toIntOrNull()
                }
                t.matches(Regex("""(?i)(secret|password):\s*(.+)""")) -> {
                    secret = t.substringAfter(':').trim().trim('"', '\'')
                }
            }
            if (host != null && port != null && secret != null) {
                if (isValidPort(port!!) && looksLikeSecret(secret!!)) {
                    val type = classifySecret(secret!!)
                    out += RawProxyEntry(
                        url = toTgUrl(host!!, port!!, secret!!),
                        host = host!!,
                        port = port!!,
                        secret = secret!!,
                        secretType = type,
                        sniDomain = extractSni(secret!!, type)
                    )
                }
                host = null; port = null; secret = null; inMt = false
            }
        }
        return out
    }

    fun parseMarkdownTables(text: String): List<RawProxyEntry> {
        if (!text.contains('|')) return emptyList()
        val out = mutableListOf<RawProxyEntry>()
        for (line in text.lineSequence()) {
            if (!line.contains('|')) continue
            val cells = line.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            if (cells.size < 2) continue
            // try find link or host/port/secret in cells
            for (c in cells) {
                out += parseLinks(c)
            }
            if (cells.size >= 3) {
                val host = cells[0].removePrefix("`").removeSuffix("`")
                val port = cells[1].filter { it.isDigit() }.toIntOrNull()
                val secret = cells.getOrNull(2)?.removePrefix("`")?.removeSuffix("`")
                if (port != null && secret != null && looksLikeSecret(secret) && isValidPort(port)) {
                    val type = classifySecret(secret)
                    out += RawProxyEntry(
                        url = toTgUrl(host, port, secret),
                        host = host,
                        port = port,
                        secret = secret,
                        secretType = type,
                        sniDomain = extractSni(secret, type)
                    )
                }
            }
        }
        return out.distinctBy { it.url.lowercase() }
    }

    fun classifySecret(secret: String): SecretType {
        val s = secret.trim()
        val lower = s.lowercase(Locale.US)
        return when {
            lower.startsWith("ee") -> SecretType.FAKE_TLS
            lower.startsWith("dd") -> SecretType.PADDED
            SECRET_HEX.matches(s) && s.length == 32 -> SecretType.PLAIN
            SECRET_HEX.matches(s) -> SecretType.PADDED
            else -> SecretType.UNKNOWN
        }
    }

    fun extractSni(secret: String, type: SecretType): String? {
        if (type != SecretType.FAKE_TLS) return null
        val hex = secret.removePrefix("ee").removePrefix("EE")
        if (hex.length <= 32) return null
        val domainHex = hex.substring(32)
        return try {
            val bytes = hexToBytes(domainHex) ?: return null
            String(bytes, StandardCharsets.US_ASCII).trim { it < ' ' || it > '~' }
                .takeIf { it.isNotBlank() && it.contains('.') }
        } catch (_: Exception) {
            null
        }
    }

    fun looksLikeSecret(secret: String): Boolean {
        val s = secret.trim()
        return SECRET_HEX.matches(s) || SECRET_B64ISH.matches(s)
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun isPrivateOrReservedHost(host: String): Boolean {
        val h = host.trim().lowercase(Locale.US)
        if (h == "localhost" || h.endsWith(".local")) return true
        val ip = parseIpv4(h) ?: return false
        val (a, b) = ip
        return when {
            a == 10 -> true
            a == 127 -> true
            a == 0 -> true
            a == 169 && b == 254 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            a == 100 && b in 64..127 -> true // CGNAT
            a >= 224 -> true
            else -> false
        }
    }

    fun toTgUrl(host: String, port: Int, secret: String): String =
        "tg://proxy?server=$host&port=$port&secret=$secret"

    fun toTmeUrl(host: String, port: Int, secret: String): String =
        "https://t.me/proxy?server=$host&port=$port&secret=$secret"

    private fun normalizeLink(raw: String): String? {
        var s = raw.trim().trimEnd(')', ']', ',', '"', '\'', '`')
        if (s.isEmpty()) return null
        s = when {
            s.startsWith("https://t.me/", ignoreCase = true) ||
                s.startsWith("http://t.me/", ignoreCase = true) ->
                "tg://proxy?" + s.substringAfter('?', missingDelimiterValue = "")
            s.startsWith("tg://", ignoreCase = true) -> s
            else -> return null
        }
        if ("proxy?" !in s.lowercase() && "socks?" !in s.lowercase()) return null
        return s
    }

    private fun parseJsonArray(arr: JSONArray): List<RawProxyEntry> {
        val out = mutableListOf<RawProxyEntry>()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i) ?: continue
            when (v) {
                is JSONObject -> parseJsonObject(v)?.let { out += it }
                is String -> {
                    fromUrl(v)?.let { out += it }
                    out += parseLineFormat(v)
                }
            }
        }
        return out
    }

    private fun parseJsonObject(o: JSONObject): RawProxyEntry? {
        fun anyKey(vararg keys: String): String? {
            val map = o.keys().asSequence().associateBy { it.lowercase(Locale.US) }
            for (k in keys) {
                val real = map[k.lowercase(Locale.US)] ?: continue
                val v = o.opt(real)?.toString()?.trim()
                if (!v.isNullOrEmpty() && v != "null") return v
            }
            return null
        }
        // nested link
        anyKey("url", "link", "proxy")?.let { fromUrl(it) }?.let { return it }

        val host = anyKey("host", "server", "ip", "address") ?: return null
        val port = anyKey("port")?.toIntOrNull() ?: return null
        val secret = anyKey("secret", "password", "key") ?: return null
        if (!isValidPort(port) || !looksLikeSecret(secret)) return null
        val type = classifySecret(secret)
        return RawProxyEntry(
            url = toTgUrl(host, port, secret),
            host = host,
            port = port,
            secret = secret,
            secretType = type,
            sniDomain = extractSni(secret, type)
        )
    }

    private fun tryDecodeBase64Whole(body: String): String? {
        val t = body.trim().replace("\n", "").replace("\r", "")
        if (t.length < 64 || t.length % 4 != 0) return null
        if (!t.matches(Regex("^[A-Za-z0-9+/=_\\-]+$"))) return null
        return try {
            val bytes = decodeBase64Flexible(t) ?: return null
            val s = String(bytes, StandardCharsets.UTF_8)
            if (s.contains("proxy", ignoreCase = true) || s.contains(':')) s else null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBase64Flexible(t: String): ByteArray? {
        val normalized = t.replace('-', '+').replace('_', '/')
        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> normalized
        }
        return try {
            // Prefer java.util (works in unit tests + API 26+)
            java.util.Base64.getDecoder().decode(padded)
        } catch (_: Exception) {
            try {
                // Android API 24–25 fallback via reflection
                val clazz = Class.forName("android.util.Base64")
                val decode = clazz.getMethod("decode", String::class.java, Int::class.javaPrimitiveType)
                decode.invoke(null, t, 0) as ByteArray
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val clean = if (hex.length % 2 == 0) hex else "0$hex"
        if (clean.any { it !in "0123456789abcdefABCDEF" }) return null
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun parseIpv4(host: String): Pair<Int, Int>? {
        val p = host.split('.')
        if (p.size != 4) return null
        val nums = p.map { it.toIntOrNull() ?: return null }
        if (nums.any { it !in 0..255 }) return null
        return nums[0] to nums[1]
    }
}
