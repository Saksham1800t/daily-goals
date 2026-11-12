package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.GoalMeasurement
import com.example.dailygoals.model.MeasureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountRulesTest {
    private val study = GoalMeasurement(MeasureType.AMOUNT, unit = "min", target = 120, minimum = 20)

    private fun resolve(
        amount: Int,
        previous: DayStatus = DayStatus.PENDING,
        reason: FailReason? = null,
        fired: FailReason? = null,
    ) = AmountRules.resolve(study, amount, previous, reason, fired)

    @Test
    fun levelsFollowTheMinimumAndTheTarget() {
        assertEquals(Level.NONE, AmountRules.levelOf(study, 19))
        assertEquals(Level.MINIMUM, AmountRules.levelOf(study, 20))
        assertEquals(Level.MINIMUM, AmountRules.levelOf(study, 119))
        assertEquals(Level.TARGET, AmountRules.levelOf(study, 120))
    }

    @Test
    fun loggingTheMinimumKeepsTheGoalAndTheTargetCompletesIt() {
        assertEquals(Resolved(DayStatus.MINIMUM, null), resolve(20))
        assertEquals(Resolved(DayStatus.DONE, null), resolve(120, previous = DayStatus.MINIMUM))
    }

    @Test
    fun belowTheMinimumStaysPendingUntilAFailureRuleFires() {
        assertEquals(Resolved(DayStatus.PENDING, null), resolve(15))
        assertEquals(Resolved(DayStatus.FAILED, FailReason.WINDOW_END), resolve(15, fired = FailReason.WINDOW_END))
    }

    @Test
    fun undoingLogsRecalculatesTheLevel() {
        assertEquals(DayStatus.PENDING, resolve(0, previous = DayStatus.MINIMUM).status)
        assertEquals(DayStatus.FAILED, resolve(0, previous = DayStatus.DONE, fired = FailReason.DAY_END).status)
        assertEquals(DayStatus.MINIMUM, resolve(30, previous = DayStatus.DONE).status)
    }

    @Test
    fun aGoalMissedByHandStaysMissedUntilTheMinimumIsLogged() {
        assertEquals(Resolved(DayStatus.FAILED, FailReason.MANUAL), resolve(5, DayStatus.FAILED, FailReason.MANUAL))
        assertEquals(DayStatus.MINIMUM, resolve(25, DayStatus.FAILED, FailReason.MANUAL).status)
    }

    @Test
    fun equalMinimumAndTargetBehavesAsYesOrNo() {
        val plain = GoalMeasurement(MeasureType.AMOUNT, unit = "x", target = 3, minimum = 3)
        assertFalse(plain.hasMinimumStep)
        assertEquals(DayStatus.PENDING, AmountRules.resolve(plain, 2, DayStatus.PENDING, null, null).status)
        assertEquals(DayStatus.DONE, AmountRules.resolve(plain, 3, DayStatus.PENDING, null, null).status)
        assertTrue(study.hasMinimumStep)
    }

    @Test
    fun amountsNeverGoBelowZeroOrAboveTheCap() {
        assertEquals(0, AmountRules.add(5, -10))
        assertEquals(35, AmountRules.add(20, 15))
        assertEquals(AmountRules.MAX_AMOUNT, AmountRules.add(AmountRules.MAX_AMOUNT, 5))
    }

    @Test
    fun quickChipsComeFromTheTarget() {
        assertEquals(listOf(10, 30, 60), QuickLog.chips(120))
        assertEquals(listOf(5, 15, 30), QuickLog.chips(60))
        assertEquals(listOf(1), QuickLog.chips(1))
        assertEquals(listOf(1, 2), QuickLog.chips(5))
    }

    @Test
    fun timerCountsWholeMinutesAndSurvivesABackwardsClock() {
        assertEquals(0, TimerRules.elapsedMinutes(1_000L, 1_000L + 59_999L))
        assertEquals(1, TimerRules.elapsedMinutes(1_000L, 1_000L + 60_000L))
        assertEquals(180, TimerRules.elapsedMinutes(0L, 3 * 60 * 60_000L))
        assertEquals(0, TimerRules.elapsedMinutes(5_000L, 1_000L))
    }

    @Test
    fun minimumVersionOfACheckGoalNeedsALabel() {
        assertFalse(GoalMeasurement().hasMinimumStep)
        assertTrue(GoalMeasurement(minimumLabel = "Put on shoes").hasMinimumStep)
        assertFalse(GoalMeasurement(minimumLabel = "  ").hasMinimumStep)
    }
}
