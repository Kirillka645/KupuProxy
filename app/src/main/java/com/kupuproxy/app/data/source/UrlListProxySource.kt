package com.kupuproxy.app.data.source

import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceKind
import com.kupuproxy.app.domain.parser.ProxyParser
import com.kupuproxy.app.domain.source.ProxySource
import okhttp3.OkHttpClient

class UrlListProxySource(
    override val id: String,
    override val displayName: String,
    private val urls: List<String>,
    override val kind: SourceKind = SourceKind.GITHUB_RAW,
    override val enabledByDefault: Boolean = true
) : ProxySource {

    override suspend fun fetch(client: OkHttpClient): List<RawProxyEntry> {
        val downloaded = HttpSupport.downloadWithRetry(client, urls)
            ?: return emptyList()
        return ProxyParser.parse(downloaded.first, id, displayName)
    }
}
