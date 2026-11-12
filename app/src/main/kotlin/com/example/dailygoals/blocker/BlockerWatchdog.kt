package com.example.dailygoals.blocker

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dailygoals.MainActivity
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.ForegroundRules
import com.example.dailygoals.engine.WatchdogAction
import com.example.dailygoals.engine.WatchdogRules
import com.example.dailygoals.notify.NotificationChannels

/** What Android currently says about the blocker's switches. */
object BlockerStatus {
    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = ComponentName(context, BlockerService::class.java)
        return enabled.split(':').any { it == component.flattenToString() || it == component.flattenToShortString() }
    }

    fun canDrawOverBlockedApps(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isBatteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** The system's own dialog for exempting this app from battery optimisation. */
    @SuppressLint("BatteryLife")
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun overlayPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
}

/**
 * A job that survives a reboot and runs every 15 minutes while a test is on. If the accessibility
 * service has been switched off (Android does this when the app is force-stopped) it tells the user,
 * because from then on nothing is blocked.
 */
class BlockerWatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as SkritiApp
        val store = app.pocStore
        val session = store.session()?.takeIf { ForegroundRules.isActive(it, System.currentTimeMillis()) }
        val enabled = BlockerStatus.isServiceEnabled(this)
        when (WatchdogRules.decide(session != null, enabled, store.lastWatchdogNotice, System.currentTimeMillis())) {
            WatchdogAction.NOTIFY -> {
                notifyServiceOff()
                store.lastWatchdogNotice = System.currentTimeMillis()
                store.log("watchdog: the service is switched off, reminder sent")
            }
            WatchdogAction.CLEAR -> {
                NotificationManagerCompat.from(this).cancel(NOTICE_ID)
                store.lastWatchdogNotice = 0L
            }
            WatchdogAction.NONE -> Unit
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    @SuppressLint("MissingPermission")
    private fun notifyServiceOff() {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.SERVICE_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.watchdog_title))
            .setContentText(getString(R.string.watchdog_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { manager.notify(NOTICE_ID, notification) }
    }

    private companion object {
        const val NOTICE_ID = 7712
    }
}

object BlockerWatchdog {
    private const val JOB_ID = 7711
    private const val PERIOD_MILLIS = 15 * 60 * 1000L

    fun schedule(context: Context) {
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, BlockerWatchdogJob::class.java))
            .setPersisted(true)
            .setPeriodic(PERIOD_MILLIS)
            .build()
        runCatching { context.getSystemService(JobScheduler::class.java).schedule(job) }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(JobScheduler::class.java).cancel(JOB_ID) }
    }
}
