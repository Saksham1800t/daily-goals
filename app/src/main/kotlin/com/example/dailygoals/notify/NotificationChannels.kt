package com.example.dailygoals.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.dailygoals.R

object NotificationChannels {
    const val WARNINGS = "warnings"

    /** Warnings use their own high-importance channel so they can be allowed or muted on their own. */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(WARNINGS, context.getString(R.string.channel_warnings), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_warnings_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
