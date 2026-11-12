package com.example.dailygoals.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.dailygoals.engine.DeliveryLevel
import com.example.dailygoals.engine.DeliveryLevels
import com.example.dailygoals.engine.DeliveryState

data class PermissionStatus(
    val notifications: Boolean,
    val headsUpAllowed: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val batteryExempt: Boolean,
) {
    val state: DeliveryState get() = DeliveryState(notifications, headsUpAllowed, fullScreen)

    /** What an ordinary warning can reach the user as right now. */
    val warningLevel: DeliveryLevel get() = DeliveryLevels.choose(state, strongAlertWanted = false)

    /** What a goal with the strong alert switch on can reach the user as right now. */
    val strongLevel: DeliveryLevel get() = DeliveryLevels.choose(state, strongAlertWanted = true)
}

/** Reads what Android currently allows and builds the intents that lead to the right settings pages. */
class PermissionChecker(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun status() = PermissionStatus(
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        headsUpAllowed = headsUpAllowed(),
        exactAlarms = canScheduleExactAlarms(),
        fullScreen = canUseFullScreen(),
        batteryExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName),
    )

    fun deliveryState(): DeliveryState = status().state

    fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun canUseFullScreen(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notificationManager.canUseFullScreenIntent()

    private fun headsUpAllowed(): Boolean {
        val channel = notificationManager.getNotificationChannel(NotificationChannels.WARNINGS) ?: return true
        return channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun notificationSettingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmSettingsIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        appDetailsIntent()
    }

    fun fullScreenSettingsIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
    } else {
        appDetailsIntent()
    }

    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    private fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
