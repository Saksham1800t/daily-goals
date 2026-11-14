package com.example.dailygoals.blocker

import android.content.Context
import com.example.dailygoals.data.PunishmentSink
import com.example.dailygoals.engine.ActiveBlock
import com.example.dailygoals.engine.BlockKind
import com.example.dailygoals.engine.PunishmentRules
import com.example.dailygoals.model.Goal
import com.example.dailygoals.notify.FailureNotifier
import java.time.LocalDate

/** Carries out a goal's punishment when it fails: the notification, then the app blocks. */
class PunishmentRunner(
    private val context: Context,
    private val registry: BlockRegistry,
    private val notifier: FailureNotifier,
) : PunishmentSink {
    override fun onFailed(goal: Goal, date: LocalDate, failedAtMillis: Long): Boolean {
        val plan = PunishmentRules.onFailure(
            intensity = goal.discipline.intensity,
            packages = goal.avoidance.temptingPackages,
            blockMinutes = goal.discipline.blockMinutes,
            failedAtMillis = failedAtMillis,
            pauseActive = registry.pauseActive(failedAtMillis),
            safeApps = registry.safeApps,
            ownPackage = context.packageName,
        )
        val until = plan.blockUntilMillis
        if (until != null) {
            plan.blockPackages.forEach { pkg ->
                registry.addBlock(ActiveBlock(goal.id, goal.name, pkg, BlockKind.PUNISHMENT, until, goal.discipline.blockMessage, date.toString()))
            }
            BlockerWatchdog.schedule(context)
            BlockerService.instance?.recheckFront()
        }
        if (plan.notify) notifier.post(goal, date, plan)
        return plan.skippedByPause
    }

    override fun onCorrected(goalId: Long, date: LocalDate) {
        registry.removeFailure(goalId, date.toString())
        notifier.cancel(goalId, date)
        BlockerService.instance?.recheckFront()
    }
}
