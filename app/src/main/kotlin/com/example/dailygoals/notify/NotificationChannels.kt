package com.example.dailygoals.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.dailygoals.R

object NotificationChannels {
    const val WARNINGS = "warnings"
    const val BLOCKER = "blocker"
    const val SERVICE_ALERTS = "service_alerts"

    /** Warnings use their own high-importance channel so they can be allowed or muted on their own. */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val warnings = NotificationChannel(WARNINGS, context.getString(R.string.channel_warnings), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_warnings_description)
            enableVibration(true)
        }
        val blocker = NotificationChannel(BLOCKER, context.getString(R.string.channel_blocker), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_blocker_description)
        }
        val alerts = NotificationChannel(SERVICE_ALERTS, context.getString(R.string.channel_service_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_service_alerts_description)
        }
        manager.createNotificationChannels(listOf(warnings, blocker, alerts))
    }
}
