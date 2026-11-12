package com.example.dailygoals.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dailygoals.MainActivity
import com.example.dailygoals.R
import com.example.dailygoals.engine.DeliveryLevel
import com.example.dailygoals.engine.Warning
import com.example.dailygoals.engine.WarningKind
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalType
import java.time.LocalDate

class WarningNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun message(goal: Goal, kind: WarningKind): String = when (kind) {
        WarningKind.Start -> context.getString(if (goal.schedule.window != null) R.string.warn_window_open else R.string.warn_check_in)
        WarningKind.StillOpen -> context.getString(R.string.warn_still_open)
        is WarningKind.Grace -> context.getString(R.string.warn_grace, kind.minutesLeft)
        is WarningKind.Late -> context.getString(R.string.warn_late, kind.minutesLate)
    }

    @SuppressLint("MissingPermission")
    fun post(goal: Goal, warning: Warning, level: DeliveryLevel) {
        if (!manager.areNotificationsEnabled()) return
        val text = message(goal, warning.kind)
        val builder = NotificationCompat.Builder(context, NotificationChannels.WARNINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(goal.name)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.warn_action_done), action(goal, warning.date, ActionReceiver.ACTION_DONE))
        if (goal.discipline.failOnManual || goal.type == GoalType.AVOID) {
            builder.addAction(0, context.getString(R.string.warn_action_failed), action(goal, warning.date, ActionReceiver.ACTION_FAILED))
        }
        if (level == DeliveryLevel.FULL_SCREEN) {
            builder.setFullScreenIntent(alertScreen(goal, warning.date, text), true)
        }
        try {
            manager.notify(warning.date.toString(), goal.id.toInt(), builder.build())
        } catch (error: SecurityException) {
            // Notifications were switched off between the check and the post; the in-app view still shows it.
        }
    }

    fun cancel(goalId: Long) {
        manager.activeNotifications.filter { it.id == goalId.toInt() }.forEach { manager.cancel(it.tag, it.id) }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(goal: Goal, date: LocalDate, name: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        (goal.id.toInt() * REQUESTS_PER_GOAL) + if (name == ActionReceiver.ACTION_DONE) 1 else 2,
        Intent(context, ActionReceiver::class.java)
            .setAction(name)
            .putExtra(ActionReceiver.EXTRA_GOAL_ID, goal.id)
            .putExtra(ActionReceiver.EXTRA_DATE, date.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alertScreen(goal: Goal, date: LocalDate, text: String): PendingIntent = PendingIntent.getActivity(
        context,
        goal.id.toInt() * REQUESTS_PER_GOAL + ALERT_OFFSET,
        Intent(context, AlertActivity::class.java)
            .putExtra(ActionReceiver.EXTRA_GOAL_ID, goal.id)
            .putExtra(ActionReceiver.EXTRA_DATE, date.toString())
            .putExtra(AlertActivity.EXTRA_TITLE, goal.name)
            .putExtra(AlertActivity.EXTRA_TEXT, text),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUESTS_PER_GOAL = 10
        const val ALERT_OFFSET = 3
    }
}
