package com.example.dailygoals.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.model.DayStatus
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Runs [work] on the app's scope and keeps the broadcast alive until it is done. */
private fun BroadcastReceiver.goAsyncWork(context: Context, work: suspend (SkritiApp) -> Unit) {
    val pending = goAsync()
    val app = context.applicationContext as SkritiApp
    app.scope.launch {
        try {
            work(app)
        } finally {
            pending.finish()
        }
    }
}

/** The alarm for the next warning, failure deadline or midnight. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_WAKE_UP) return
        goAsyncWork(context) { it.warnings.run() }
    }
}

/** Rebuilds the alarm after a reboot, an app update, a clock or time zone change, or a permission change. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWork(context) { it.warnings.run() }
    }
}

/** The Done and I failed buttons on a warning. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, 0L)
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return
        val action = when (intent.action) {
            ACTION_DONE -> CheckAction.TOGGLE_DONE
            ACTION_FAILED -> CheckAction.MARK_FAILED
            else -> return
        }
        if (goalId == 0L) return
        goAsyncWork(context) { app ->
            if (app.repository.statusOn(goalId, date) == DayStatus.PENDING) app.repository.applyAction(goalId, date, action)
            app.warnings.run()
        }
    }

    companion object {
        const val ACTION_DONE = "com.example.dailygoals.ACTION_DONE"
        const val ACTION_FAILED = "com.example.dailygoals.ACTION_FAILED"
        const val EXTRA_GOAL_ID = "goal_id"
        const val EXTRA_DATE = "date"
    }
}
