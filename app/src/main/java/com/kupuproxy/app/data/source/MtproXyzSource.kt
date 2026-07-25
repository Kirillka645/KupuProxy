package com.kupuproxy.app.data.source

import android.util.Base64
import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.data.remote.TelegramBypass
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import okhttp3.OkHttpClient
import org.json.JSONArray

/**
 * Источник из экосистемы [hookzof/socks5_list](https://github.com/hookzof/socks5_list):
 * репозиторий больше не хранит сырой `tg/mtproto.txt` — в `tg/mtproto.json` только
 * редирект на https://mtpro.xyz/mtproto. Список прокси вшит в страницу WordPress
 * как `eval(atob('...'))` с JSON `[{host,port,secret,...}]` (~50 штук, обновляется часто).
 *
 * Берём WP REST `pages?slug=mtproto` (или ru), декодируем atob, парсим JSON.
 * Не требует Telegram/t.me.
 */
class MtproXyzSource(
    override val enabledByDefault: Boolean = true
) : ProxySource {

    override val id: String = "mtpro_xyz"
    override val displayName: String = "MTPro.XYZ (hookzof)"
    override val kind: SourceKind = SourceKind.JSON_API

    override suspend fun fetch(client: OkHttpClient): List<RawProxyEntry> {
        val urls = listOf(
            "https://mtpro.xyz/wp-json/wp/v2/pages?slug=mtproto",
            "https://mtpro.xyz/wp-json/wp/v2/pages?slug=mtproto-ru",
            "https://cdn.jsdelivr.net/gh/hookzof/socks5_list@master/tg/mtproto.json",
            "https://raw.githubusercontent.com/hookzof/socks5_list/master/tg/mtproto.json"
        )
        val bodies = HttpSupport.downloadAllParallel(
            client = client,
            urls = urls,
            headers = TelegramBypass.browserHeaders(),
            minUsefulBytes = 20,
            maxParallel = 4,
            perUrlTimeoutMs = 12_000L
        )
        val bag = LinkedHashMap<String, RawProxyEntry>()
        for ((body, url) in bodies) {
            // mtproto.json is just "https://mtpro.xyz/mtproto\n" — skip pure URL pointers
            val trimmed = body.trim()
            if (trimmed.startsWith("http") && !trimmed.contains("atob") && trimmed.length < 80) {
                continue
            }
            extractFromAtob(body).forEach { e ->
                val key = "${e.host.lowercase()}:${e.port}:${e.secret.lowercase()}"
                bag.putIfAbsent(key, e.copy(sourceId = id, sourceName = displayName))
            }
            // also try generic parse if page has tg:// links
            ProxyParser.parse(body, id, displayName).forEach { e ->
                val key = "${e.host.lowercase()}:${e.port}:${e.secret.lowercase()}"
                bag.putIfAbsent(key, e)
            }
        }
        return bag.values.toList()
    }

    companion object {
        private val ATOB = Regex("""atob\('([^']+)'\)""")

        fun extractFromAtob(text: String): List<RawProxyEntry> {
            val out = ArrayList<RawProxyEntry>()
            for (m in ATOB.findAll(text)) {
                val b64 = m.groupValues[1]
                val decoded = try {
                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                } catch (_: Exception) {
                    continue
                }
                out += parseHostPortSecretJson(decoded)
            }
            return out
        }

        /**
         * Декодированный payload вида:
         * `(function(){ ... return [{"host":"...","port":443,"secret":"..."}, ...]; })`
         */
        fun parseHostPortSecretJson(decoded: String): List<RawProxyEntry> {
            val start = decoded.indexOf("[{")
            if (start < 0) return emptyList()
            // find matching end of array
            var depth = 0
            var end = -1
            for (i in start until decoded.length) {
                when (decoded[i]) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }
            if (end < 0) return emptyList()
            val json = decoded.substring(start, end + 1)
            return try {
                val arr = JSONArray(json)
                val list = ArrayList<RawProxyEntry>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val host = o.optString("host").ifBlank { o.optString("server") }
                    val port = o.optInt("port", 0)
                    val secret = o.optString("secret").ifBlank { o.optString("password") }
                    if (host.isBlank() || !ProxyParser.isValidPort(port)) continue
                    // API отдаёт hex и base64-секреты разной длины — не режем строго 32+
                    if (secret.length < 16) continue
                    val type = ProxyParser.classifySecret(secret)
                    list += RawProxyEntry(
                        url = ProxyParser.toTgUrl(host, port, secret),
                        host = host.trim(),
                        port = port,
                        secret = secret.trim(),
                        secretType = type,
                        sniDomain = ProxyParser.extractSni(secret, type)
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
