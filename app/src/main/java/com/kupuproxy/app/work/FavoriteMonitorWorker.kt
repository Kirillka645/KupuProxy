package com.kupuproxy.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kupuproxy.app.InsightsStore
import com.kupuproxy.app.NetworkProfileMode
import com.kupuproxy.app.ProfileSettings
import com.kupuproxy.app.ProxyCache
import com.kupuproxy.app.ProxyManager
import com.kupuproxy.app.ProxyObservation
import com.kupuproxy.app.R
import java.util.concurrent.TimeUnit

object FavoriteMonitorPreferences {
    private const val PREFS = "favorite_monitor"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        FavoriteMonitorWorker.schedule(context, enabled)
    }
}

class FavoriteMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!FavoriteMonitorPreferences.isEnabled(applicationContext)) return Result.success()
        val favorites = ProxyCache.getFavorites(applicationContext).toList()
        if (favorites.isEmpty()) return Result.success()
        return try {
            val observations = mutableListOf<ProxyObservation>()
            val base = ProfileSettings.forMode(NetworkProfileMode.AUTO, applicationContext)
            ProxyManager.checkProxiesPingParallel(
                proxies = favorites,
                settings = base.copy(maxToCheck = favorites.size, batchSize = minOf(24, favorites.size.coerceAtLeast(1)), stopWhenFound = 0),
                profileLabel = base.label,
                onProgress = { _, _, _ -> },
                onChecked = observations::add,
            )
            InsightsStore.record(applicationContext, observations)
            val failed = observations.count { !it.ok }
            if (failed > 0) notifyFailed(failed, favorites.size)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notifyFailed(failed: Int, total: Int) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, applicationContext.getString(R.string.favorite_monitor_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        manager.notify(
            4203,
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_telegram)
                .setContentTitle(applicationContext.getString(R.string.favorite_monitor_notification_title))
                .setContentText(applicationContext.getString(R.string.favorite_monitor_notification_body, failed, total))
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val UNIQUE = "kupu_favorite_monitor"
        private const val CHANNEL = "kupu_favorite_monitor"

        fun schedule(context: Context, enabled: Boolean = FavoriteMonitorPreferences.isEnabled(context)) {
            if (!enabled) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
                return
            }
            val request = PeriodicWorkRequestBuilder<FavoriteMonitorWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
