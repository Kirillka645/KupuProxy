package com.kupuproxy.app.core.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.snackbar.Snackbar
import com.kupuproxy.app.R
import com.kupuproxy.app.core.Constants

object TelegramIntents {

    fun openTelegramChannel(context: Context, anchor: android.view.View? = null) {
        val deep = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.TELEGRAM_CHANNEL_DEEP_LINK))
        try {
            context.startActivity(deep)
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: Exception) {
        }

        try {
            CustomTabsIntent.Builder().build()
                .launchUrl(context, Uri.parse(Constants.TELEGRAM_CHANNEL_URL))
            return
        } catch (_: Exception) {
        }

        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(Constants.TELEGRAM_CHANNEL_URL))
            )
            return
        } catch (_: Exception) {
        }

        val msg = context.getString(R.string.channel_open_failed)
        if (anchor != null) {
            Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG)
                .setAction(R.string.copy_link) { copyChannelLink(context) }
                .show()
        } else {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            copyChannelLink(context)
        }
    }

    fun copyChannelLink(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("KupuProxy", Constants.TELEGRAM_CHANNEL_URL))
        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }
}
