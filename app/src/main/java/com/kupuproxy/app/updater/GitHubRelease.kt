package com.kupuproxy.app.updater

data class GitHubRelease(
    val tagName: String,
    val changelog: String,
    val apkUrl: String,
    val htmlUrl: String
)
