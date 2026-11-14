package com.example.dailygoals.engine

import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

enum class Weakening {
    APP_REMOVED,
    SCHEDULED_BLOCK_OFF,
    PUNISHMENT_WEAKER,
    DURATION_SHORTER,
    WINDOW_SHORTER,
    NOT_DUE_TODAY,
    ARCHIVED,
    TEMP_ACCESS_ALLOWED,
}

/**
 * Changes that make a running block weaker must not work as a quick way out, so they wait until that
 * block ends. Changes that make it stronger apply at once. The official ways out stay the emergency
 * pause and temporary access.
 */
object CoolingOff {
    /** Every way in which saving [new] over [old] would weaken a block; empty when the edit is harmless. */
    fun weakenings(old: Goal, new: Goal, today: LocalDate): Set<Weakening> = buildSet {
        if ((old.avoidance.temptingPackages - new.avoidance.temptingPackages).isNotEmpty()) add(Weakening.APP_REMOVED)
        val scheduledBefore = old.discipline.scheduledBlockEnabled && ScheduledBlocks.canOffer(old)
        val scheduledAfter = new.discipline.scheduledBlockEnabled && ScheduledBlocks.canOffer(new)
        if (scheduledBefore && !scheduledAfter) add(Weakening.SCHEDULED_BLOCK_OFF)
        if (new.discipline.intensity < old.discipline.intensity) add(Weakening.PUNISHMENT_WEAKER)
        if (PunishmentRules.blocksApps(old.discipline.intensity) && new.discipline.blockMinutes < old.discipline.blockMinutes) add(Weakening.DURATION_SHORTER)
        if (scheduledBefore && scheduledAfter && windowShorter(old, new)) add(Weakening.WINDOW_SHORTER)
        if (DueDates.isDue(old.schedule, today) && !DueDates.isDue(new.schedule, today)) add(Weakening.NOT_DUE_TODAY)
        if (old.state == GoalState.ACTIVE && new.state == GoalState.ARCHIVED) add(Weakening.ARCHIVED)
        if (!old.discipline.allowTempAccess && new.discipline.allowTempAccess) add(Weakening.TEMP_ACCESS_ALLOWED)
    }

    private fun windowShorter(old: Goal, new: Goal): Boolean {
        val before = old.schedule.window
        val after = new.schedule.window ?: return false
        return before == null || after.startMinute > before.startMinute || after.endMinute < before.endMinute
    }

    /**
     * When a goal edit may take effect: null means now. [blockEnds] holds the end of every block this goal
     * has running; with none running any edit applies at once.
     */
    fun goalEditAppliesAt(weakenings: Set<Weakening>, blockEnds: List<Long>, nowMillis: Long): Long? =
        if (weakenings.isEmpty()) null else blockEnds.filter { it > nowMillis }.maxOrNull()

    /** Adding an app to the always-allowed list waits while that app is blocked. */
    fun safeAppAppliesAt(packageName: String, blocks: List<ActiveBlock>, nowMillis: Long): Long? =
        blocks.filter { it.packageName == packageName && nowMillis < it.endsAtMillis }.maxOfOrNull { it.endsAtMillis }

    /** Making temporary access easier waits while any block runs. */
    fun tempSettingsApplyAt(old: TempAccessSettings, new: TempAccessSettings, blocks: List<ActiveBlock>, nowMillis: Long): Long? =
        if (TempAccess.loosens(old, new)) blocks.filter { nowMillis < it.endsAtMillis }.maxOfOrNull { it.endsAtMillis } else null

    /** Replacing all data with a backup would drop the goals behind a running block, so it must wait. Merging only adds. */
    fun restoreAllowed(replace: Boolean, blocks: List<ActiveBlock>, nowMillis: Long): Boolean =
        !replace || blocks.none { nowMillis < it.endsAtMillis }

    /** The part of a settings change that only makes things stricter; it never waits. */
    fun stricterOf(old: TempAccessSettings, new: TempAccessSettings): TempAccessSettings = TempAccessSettings(
        enabled = old.enabled && new.enabled,
        maxUsesPerDay = min(old.maxUsesPerDay, new.maxUsesPerDay),
        maxMinutes = min(old.maxMinutes, new.maxMinutes),
        waitSeconds = max(old.waitSeconds, new.waitSeconds),
    )
}
