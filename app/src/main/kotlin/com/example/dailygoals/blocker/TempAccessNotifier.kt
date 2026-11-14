package com.example.dailygoals.blocker

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.TempAccessUse
import com.example.dailygoals.notify.NotificationChannels
import kotlinx.coroutines.launch

/** The ongoing notice for a running temporary access: the time left and a way to lock again early. */
class TempAccessNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    fun show(use: TempAccessUse) {
        if (!manager.areNotificationsEnabled()) return
        val pm = context.packageManager
        val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(use.packageName, 0)).toString() }.getOrDefault(use.packageName)
        val lock = PendingIntent.getBroadcast(
            context,
            use.packageName.hashCode(),
            Intent(context, TempAccessReceiver::class.java).setAction(TempAccessReceiver.ACTION_LOCK).putExtra(TempAccessReceiver.EXTRA_PACKAGE, use.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.BLOCKER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.temp_notice_title, appName))
            .setContentText(context.getString(R.string.temp_notice_text))
            .setOngoing(true)
            .setWhen(use.endsAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter((use.endsAtMillis - System.currentTimeMillis()).coerceAtLeast(1))
            .addAction(0, context.getString(R.string.temp_lock_now), lock)
            .build()
        runCatching { manager.notify(use.packageName, NOTICE_ID, notification) }
    }

    fun cancel(packageName: String) = manager.cancel(packageName, NOTICE_ID)

    private companion object {
        const val NOTICE_ID = 7720
    }
}

/** "Lock again now": ends the access at once. The use still counts for today. */
class TempAccessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOCK) return
        val target = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val app = context.applicationContext as SkritiApp
        val now = System.currentTimeMillis()
        val ended = app.blocks.endTempAccess(target, now)
        TempAccessNotifier(context).cancel(target)
        BlockerService.instance?.recheckFront()
        if (ended == null) return
        val pending = goAsync()
        app.scope.launch {
            try {
                app.repository.markTempAccessEndedEarly(ended, now)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOCK = "com.example.dailygoals.TEMP_ACCESS_LOCK"
        const val EXTRA_PACKAGE = "package"
    }
}
