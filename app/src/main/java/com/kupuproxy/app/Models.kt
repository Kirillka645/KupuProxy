package com.kupuproxy.app

import java.io.Serializable

data class ProxyWithPing(
    val url: String,
    val pingMs: Int,
    val profileLabel: String = ""
) : Serializable

data class ProxyInfo(
    val server: String,
    val port: String
)

data class FetchResult(
    val proxies: List<String>,
    val sourceHits: Map<String, Int>,
    val usedMirrors: List<String>,
    val fromCache: Boolean = false,
    val fromSeed: Boolean = false
)
