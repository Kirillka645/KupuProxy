package com.kupuproxy.app.data.export

import android.content.Context
import android.content.Intent
import com.kupuproxy.app.domain.model.ProxyEndpoint
import com.kupuproxy.app.domain.parser.ProxyParser
import org.json.JSONArray
import org.json.JSONObject

object ProxyExporter {

    fun toTgLines(urls: List<String>): String = urls.joinToString("\n")

    fun toJson(endpoints: List<ProxyEndpoint>): String {
        val arr = JSONArray()
        for (e in endpoints) {
            arr.put(
                JSONObject()
                    .put("server", e.host)
                    .put("port", e.port)
                    .put("secret", e.secret)
                    .put("url", e.url)
                    .put("reliability", e.reliabilityScore)
            )
        }
        return arr.toString(2)
    }

    fun toCsv(endpoints: List<ProxyEndpoint>): String {
        val sb = StringBuilder("server,port,secret,url,score\n")
        for (e in endpoints) {
            sb.append(e.host).append(',')
                .append(e.port).append(',')
                .append(e.secret).append(',')
                .append(e.url).append(',')
                .append(e.reliabilityScore).append('\n')
        }
        return sb.toString()
    }

    fun toTmeLines(urls: List<String>): String {
        return urls.mapNotNull { url ->
            val e = ProxyParser.fromUrl(url) ?: return@mapNotNull null
            ProxyParser.toTmeUrl(e.host, e.port, e.secret)
        }.joinToString("\n")
    }

    fun shareText(context: Context, title: String, body: String) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, body)
                },
                title
            )
        )
    }
}
