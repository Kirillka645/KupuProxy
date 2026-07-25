package com.kupuproxy.app.core

import com.kupuproxy.app.BuildConfig

object FeatureFlags {
    val channelFeedEnabled: Boolean = BuildConfig.FEATURE_CHANNEL_FEED
}
