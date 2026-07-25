package com.kupuproxy.app.domain.source

import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SourceKind
import okhttp3.OkHttpClient

interface ProxySource {
    val id: String
    val displayName: String
    val kind: SourceKind
    val enabledByDefault: Boolean
        get() = true

    suspend fun fetch(client: OkHttpClient): List<RawProxyEntry>
}
