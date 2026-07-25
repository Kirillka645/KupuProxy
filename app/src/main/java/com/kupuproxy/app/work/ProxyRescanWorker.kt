package com.kupuproxy.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kupuproxy.app.ProxyCache
import com.kupuproxy.app.R
import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.domain.aggregator.ProxyAggregator
import com.kupuproxy.app.domain.source.ProxySourceRegistry
import java.util.concurrent.TimeUnit

class ProxyRescanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val client = HttpSupport.defaultClient()
            val aggregator = ProxyAggregator(client)
            val sources = ProxySourceRegistry.builtIn().filter { it.enabledByDefault }.take(8)
            val result = aggregator.collect(sources)
            if (result.proxies.isNotEmpty()) {
                ProxyCache.saveRawList(
                    applicationContext,
                    result.proxies.map { it.url }
                )
            }
            val fav = ProxyCache.getFavorites(applicationContext)
            // soft notify if favorites empty after rescan (informational)
            if (fav.isEmpty() && result.proxies.isNotEmpty()) {
                notifyOk(result.proxies.size)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notifyOk(count: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "kupu_rescan"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    applicationContext.getString(R.string.rescan_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val n = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_telegram)
            .setContentTitle(applicationContext.getString(R.string.rescan_done_title))
            .setContentText(applicationContext.getString(R.string.rescan_done_body, count))
            .setAutoCancel(true)
            .build()
        nm.notify(4201, n)
    }

    companion object {
        private const val UNIQUE = "kupu_proxy_rescan"

        fun schedule(context: Context, hours: Long) {
            if (hours <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
                return
            }
            val req = PeriodicWorkRequestBuilder<ProxyRescanWorker>(
                hours.coerceIn(1, 24),
                TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }
    }
}
