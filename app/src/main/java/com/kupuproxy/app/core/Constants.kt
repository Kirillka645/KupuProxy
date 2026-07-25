package com.kupuproxy.app.core

object Constants {
    const val TELEGRAM_CHANNEL_USERNAME = "KupuProxy"
    const val TELEGRAM_CHANNEL_URL = "https://t.me/$TELEGRAM_CHANNEL_USERNAME"
    const val TELEGRAM_CHANNEL_DEEP_LINK = "tg://resolve?domain=$TELEGRAM_CHANNEL_USERNAME"
    const val TELEGRAM_CHANNEL_PREVIEW = "https://t.me/s/$TELEGRAM_CHANNEL_USERNAME"

    const val AGGREGATOR_PARALLELISM = 6
    const val SOURCE_TIMEOUT_MS = 10_000L
    const val CHECK_PARALLELISM = 20

    val GITHUB_CDN_TEMPLATES = listOf(
        "https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path}",
        "https://cdn.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}",
        "https://fastly.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}",
        "https://raw.githack.com/{owner}/{repo}/{ref}/{path}",
        "https://ghproxy.net/https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path}"
    )
}
