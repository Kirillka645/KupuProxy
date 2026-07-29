package com.kupuproxy.app.updater

data class GitHubRelease(
    val tagName: String,
    val changelog: String,
    val apkUrl: String,
    val htmlUrl: String,
    val apkName: String = "",
    val apkSize: Long = -1L,
    val sha256: String? = null,
    val sha256Url: String? = null
)
