package com.example.dailygoals.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dailygoals.MainActivity
import com.example.dailygoals.R
import com.example.dailygoals.blocker.BlockScreens
import com.example.dailygoals.engine.PunishmentPlan
import com.example.dailygoals.model.Goal
import java.time.LocalDate

/** Tells the user about a failure and what it costs; a strong goal also gets a full-screen alert. */
class FailureNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    fun post(goal: Goal, date: LocalDate, plan: PunishmentPlan) {
        if (!manager.areNotificationsEnabled()) return
        val text = text(goal, plan)
        val builder = NotificationCompat.Builder(context, NotificationChannels.WARNINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.failure_title, goal.name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp())
        if (plan.fullScreenAlert) builder.setFullScreenIntent(alertScreen(goal, date, text), true)
        runCatching { manager.notify(tag(date), goal.id.toInt(), builder.build()) }
    }

    fun cancel(goalId: Long, date: LocalDate) = manager.cancel(tag(date), goalId.toInt())

    private fun text(goal: Goal, plan: PunishmentPlan): String {
        val until = plan.blockUntilMillis ?: return context.getString(R.string.failure_text)
        val pm = context.packageManager
        val names = plan.blockPackages.map { pkg -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
        return context.getString(R.string.failure_text_blocked, names.sorted().joinToString(", "), BlockScreens.untilText(until, System.currentTimeMillis()))
    }

    private fun tag(date: LocalDate) = "failure-$date"

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alertScreen(goal: Goal, date: LocalDate, text: String): PendingIntent = PendingIntent.getActivity(
        context,
        goal.id.toInt() * REQUESTS_PER_GOAL + ALERT_OFFSET,
        Intent(context, AlertActivity::class.java)
            .putExtra(AlertActivity.EXTRA_TITLE, context.getString(R.string.failure_title, goal.name))
            .putExtra(AlertActivity.EXTRA_TEXT, text)
            .putExtra(AlertActivity.EXTRA_FAILURE, true)
            .putExtra(ActionReceiver.EXTRA_DATE, date.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUESTS_PER_GOAL = 10
        const val ALERT_OFFSET = 4
    }
}
