package com.kupuproxy.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kupuproxy.app.BuildConfig
import com.kupuproxy.app.MainActivity
import com.kupuproxy.app.R
import com.kupuproxy.app.data.remote.HttpSupport
import com.kupuproxy.app.updater.UpdateCheckResult
import com.kupuproxy.app.updater.UpdateChecker
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!UpdatePreferences.isAutoCheckEnabled(applicationContext)) return Result.success()
        val checker = UpdateChecker(applicationContext, HttpSupport.defaultClient())
        return when (val result = checker.checkForUpdate(BuildConfig.VERSION_NAME)) {
            is UpdateCheckResult.UpdateAvailable -> {
                val tag = result.release.tagName
                if (UpdatePreferences.shouldNotifyFor(applicationContext, tag) && canPostNotifications()) {
                    notifyUpdate(tag)
                    UpdatePreferences.markNotified(applicationContext, tag)
                }
                UpdatePreferences.saveLastCheck(applicationContext, System.currentTimeMillis())
                Result.success()
            }
            UpdateCheckResult.UpToDate -> {
                UpdatePreferences.saveLastCheck(applicationContext, System.currentTimeMillis())
                Result.success()
            }
            is UpdateCheckResult.Failure -> Result.retry()
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun notifyUpdate(tag: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_CHECK_UPDATES, true)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(applicationContext, 4301, intent, flags)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_telegram)
            .setContentTitle(applicationContext.getString(R.string.update_notification_title, tag))
            .setContentText(applicationContext.getString(R.string.update_notification_body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(4301, notification)
    }

    companion object {
        private const val UNIQUE = "kupu_update_check"
        private const val CHANNEL_ID = "kupu_updates"

        fun schedule(context: Context, enabled: Boolean = UpdatePreferences.isAutoCheckEnabled(context)) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(UNIQUE)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS, 60, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
