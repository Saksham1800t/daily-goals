package com.example.dailygoals.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Duration

/**
 * Keeps exactly one alarm: the next moment the app needs to wake up. Without the exact alarm permission
 * the closest allowed option is used, an inexact alarm that can run a few minutes late.
 */
class AlarmScheduler(private val context: Context, private val permissions: PermissionChecker) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_WAKE_UP)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * [delay] is measured on the app's clock, so a debug time shift still wakes the app after the right
     * real wait.
     */
    fun scheduleIn(delay: Duration) {
        val triggerAt = System.currentTimeMillis() + delay.toMillis().coerceAtLeast(MIN_DELAY_MILLIS)
        val intent = pendingIntent()
        try {
            if (permissions.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        } catch (error: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    companion object {
        const val ACTION_WAKE_UP = "com.example.dailygoals.WAKE_UP"
        private const val REQUEST_CODE = 1
        private const val MIN_DELAY_MILLIS = 1000L
    }
}
