package com.example.dailygoals.engine

import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalAvoidance
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.model.TimeWindow
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingOffTest {
    private val friday = LocalDate.of(2026, 11, 13)
    private val insta = "com.instagram.android"
    private val youtube = "com.youtube"

    private val goal = Goal(
        id = 5,
        name = "No Instagram during work",
        type = GoalType.AVOID,
        schedule = GoalSchedule(frequency = Frequency.WEEKDAYS, activeDays = DayOfWeek.entries.toSet(), window = TimeWindow(540, 1080), startDate = friday.minusDays(7)),
        discipline = GoalDiscipline(intensity = Intensity.MODERATE, blockMinutes = 60, scheduledBlockEnabled = true, allowTempAccess = false),
        avoidance = GoalAvoidance(setOf(insta, youtube)),
    )

    private fun weak(new: Goal) = CoolingOff.weakenings(goal, new, friday)

    @Test
    fun removingAnAppOrSwitchingBlockingOffWeakens() {
        assertEquals(setOf(Weakening.APP_REMOVED), weak(goal.copy(avoidance = GoalAvoidance(setOf(insta)))))
        assertEquals(setOf(Weakening.SCHEDULED_BLOCK_OFF), weak(goal.copy(discipline = goal.discipline.copy(scheduledBlockEnabled = false))))
        assertTrue(Weakening.SCHEDULED_BLOCK_OFF in weak(goal.copy(type = GoalType.DO)))
        assertTrue(Weakening.SCHEDULED_BLOCK_OFF in weak(goal.copy(schedule = goal.schedule.copy(frequency = Frequency.PER_WEEK))))
    }

    @Test
    fun aWeakerOrShorterPunishmentWeakens() {
        assertEquals(setOf(Weakening.PUNISHMENT_WEAKER), weak(goal.copy(discipline = goal.discipline.copy(intensity = Intensity.GENTLE))))
        assertEquals(setOf(Weakening.DURATION_SHORTER), weak(goal.copy(discipline = goal.discipline.copy(blockMinutes = 30))))
    }

    @Test
    fun shorteningTheWindowOrDroppingTodayWeakens() {
        assertEquals(setOf(Weakening.WINDOW_SHORTER), weak(goal.copy(schedule = goal.schedule.copy(window = TimeWindow(540, 1020)))))
        assertEquals(setOf(Weakening.WINDOW_SHORTER), weak(goal.copy(schedule = goal.schedule.copy(window = TimeWindow(600, 1080)))))
        val allDay = goal.copy(schedule = goal.schedule.copy(window = null))
        assertEquals(setOf(Weakening.WINDOW_SHORTER), CoolingOff.weakenings(allDay, goal, friday))
        val noFriday = goal.copy(schedule = goal.schedule.copy(activeDays = DayOfWeek.entries.toSet() - DayOfWeek.FRIDAY))
        assertEquals(setOf(Weakening.NOT_DUE_TODAY), weak(noFriday))
    }

    @Test
    fun archivingOrAllowingTemporaryAccessWeakens() {
        assertEquals(setOf(Weakening.ARCHIVED), weak(goal.copy(state = GoalState.ARCHIVED)))
        assertEquals(setOf(Weakening.TEMP_ACCESS_ALLOWED), weak(goal.copy(discipline = goal.discipline.copy(allowTempAccess = true))))
    }

    @Test
    fun strengtheningChangesAndHarmlessEditsNeverWait() {
        val stronger = goal.copy(
            name = "No Instagram at work",
            avoidance = GoalAvoidance(setOf(insta, youtube, "com.reddit")),
            schedule = goal.schedule.copy(window = TimeWindow(480, 1140)),
            discipline = goal.discipline.copy(intensity = Intensity.STRONG, blockMinutes = 120, blockMessage = "Back to work"),
        )
        assertTrue(weak(stronger).isEmpty())
        assertTrue(weak(goal.copy(schedule = goal.schedule.copy(window = null))).isEmpty())
        val tomorrowOnly = goal.copy(schedule = goal.schedule.copy(activeDays = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY))
        assertTrue(weak(tomorrowOnly).isEmpty())
    }

    @Test
    fun aWeakeningEditWaitsForTheLastRunningBlockOfTheGoal() {
        val weakening = setOf(Weakening.APP_REMOVED)
        assertEquals(9_000L, CoolingOff.goalEditAppliesAt(weakening, listOf(7_000, 9_000), nowMillis = 5_000))
        assertNull(CoolingOff.goalEditAppliesAt(weakening, listOf(4_000), nowMillis = 5_000))
        assertNull(CoolingOff.goalEditAppliesAt(weakening, emptyList(), nowMillis = 5_000))
        assertNull(CoolingOff.goalEditAppliesAt(emptySet(), listOf(9_000), nowMillis = 5_000))
    }

    private val running = listOf(
        ActiveBlock(5, "Work", insta, BlockKind.SCHEDULED, 9_000),
        ActiveBlock(6, "Wake", insta, BlockKind.PUNISHMENT, 12_000),
        ActiveBlock(6, "Wake", youtube, BlockKind.PUNISHMENT, 3_000),
    )

    @Test
    fun addingABlockedAppToAlwaysAllowedWaitsForItsLongestBlock() {
        assertEquals(12_000L, CoolingOff.safeAppAppliesAt(insta, running, nowMillis = 5_000))
        assertNull(CoolingOff.safeAppAppliesAt(youtube, running, nowMillis = 5_000))
        assertNull(CoolingOff.safeAppAppliesAt("com.reddit", running, nowMillis = 5_000))
    }

    @Test
    fun raisingTemporaryAccessLimitsWaitsButLoweringThemDoesNot() {
        val old = TempAccessSettings()
        assertEquals(12_000L, CoolingOff.tempSettingsApplyAt(old, old.copy(maxUsesPerDay = 5), running, nowMillis = 5_000))
        assertEquals(12_000L, CoolingOff.tempSettingsApplyAt(old, old.copy(waitSeconds = 15), running, nowMillis = 5_000))
        assertNull(CoolingOff.tempSettingsApplyAt(old, old.copy(maxUsesPerDay = 1, waitSeconds = 120), running, nowMillis = 5_000))
        assertNull(CoolingOff.tempSettingsApplyAt(old, old.copy(maxUsesPerDay = 5), running, nowMillis = 20_000))
        assertNull(CoolingOff.tempSettingsApplyAt(old, old.copy(maxUsesPerDay = 5), emptyList(), nowMillis = 5_000))
    }

    @Test
    fun replacingAllDataIsRefusedWhileABlockRunsButMergingIsNot() {
        assertFalse(CoolingOff.restoreAllowed(replace = true, blocks = running, nowMillis = 5_000))
        assertTrue(CoolingOff.restoreAllowed(replace = false, blocks = running, nowMillis = 5_000))
        assertTrue(CoolingOff.restoreAllowed(replace = true, blocks = running, nowMillis = 20_000))
        assertTrue(CoolingOff.restoreAllowed(replace = true, blocks = emptyList(), nowMillis = 5_000))
    }

    @Test
    fun aMixedSettingsChangeAppliesItsStricterPartAtOnce() {
        val old = TempAccessSettings(enabled = true, maxUsesPerDay = 2, maxMinutes = 10, waitSeconds = 60)
        val new = TempAccessSettings(enabled = true, maxUsesPerDay = 5, maxMinutes = 5, waitSeconds = 30)
        assertEquals(TempAccessSettings(true, 2, 5, 60), CoolingOff.stricterOf(old, new))
    }
}
