package com.kupuproxy.app.core

object Constants {
    const val TELEGRAM_CHANNEL_USERNAME = "KupuProxy"
    const val TELEGRAM_CHANNEL_URL = "https://t.me/$TELEGRAM_CHANNEL_USERNAME"
    const val TELEGRAM_CHANNEL_DEEP_LINK = "tg://resolve?domain=$TELEGRAM_CHANNEL_USERNAME"
    const val TELEGRAM_CHANNEL_PREVIEW = "https://t.me/s/$TELEGRAM_CHANNEL_USERNAME"

    /** Параллелизм источников в мега-скане. */
    const val AGGREGATOR_PARALLELISM = 6
    /** Таймаут на один источник (tg_mega сам режет внутренние таймауты). */
    const val SOURCE_TIMEOUT_MS = 45_000L
    const val CHECK_PARALLELISM = 20

    val GITHUB_CDN_TEMPLATES = listOf(
        "https://cdn.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}",
        "https://fastly.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}",
        "https://gcore.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}",
        "https://raw.githack.com/{owner}/{repo}/{ref}/{path}",
        "https://rawcdn.githack.com/{owner}/{repo}/{ref}/{path}",
        "https://ghproxy.net/https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path}",
        "https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path}"
    )
}
