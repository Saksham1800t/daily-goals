package com.example.dailygoals.data

import androidx.room.withTransaction
import com.example.dailygoals.engine.AmountRules
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.engine.CheckInRules
import com.example.dailygoals.engine.CheckResult
import com.example.dailygoals.engine.Clock
import com.example.dailygoals.engine.DeliveryLevel
import com.example.dailygoals.engine.FailureEvaluator
import com.example.dailygoals.engine.Warning
import com.example.dailygoals.engine.WarningPlan
import com.example.dailygoals.engine.WarningPlanner
import com.example.dailygoals.engine.PeriodStatus
import com.example.dailygoals.engine.Periods
import com.example.dailygoals.engine.QuotaRules
import com.example.dailygoals.engine.TimerRules
import com.example.dailygoals.engine.ZoneChange
import com.example.dailygoals.model.CheckIn
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.isMet
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WarningBatch(val plan: WarningPlan, val goals: Map<Long, Goal>, val resolvedToday: Set<Long>, val now: java.time.Instant)

class GoalRepository(
    private val database: AppDatabase,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private val goalDao = database.goalDao()
    private val checkInDao = database.checkInDao()
    private val amountLogDao = database.amountLogDao()
    private val periodDao = database.periodResultDao()
    private val warningDao = database.warningMarkDao()
    private val evaluationLock = Mutex()

    val goals: Flow<List<Goal>> = goalDao.observeAll().map { list -> list.map(GoalEntity::toModel) }

    val checkIns: Flow<List<CheckIn>> = checkInDao.observeAll().map { list -> list.map(CheckInEntity::toModel) }

    /** Goal id to the epoch millis at which its running timer started. */
    val timers: StateFlow<Map<Long, Long>> = settings.timers

    suspend fun goal(id: Long): Goal? = goalDao.find(id)?.toModel()

    suspend fun save(goal: Goal) {
        if (goal.id == 0L) {
            goalDao.insert(goal.copy(sortOrder = goalDao.nextSortOrder()).toEntity())
        } else {
            goalDao.update(goal.toEntity())
        }
    }

    suspend fun archive(id: Long) = goalDao.setState(id, GoalState.ARCHIVED)

    suspend fun applyAction(goalId: Long, date: LocalDate, action: CheckAction): CheckResult = database.withTransaction {
        val goal = goalDao.find(goalId)?.toModel() ?: return@withTransaction CheckResult.NoChange
        val today = clock.now().toLocalDate()
        if (!CheckInRules.canEdit(date, today)) return@withTransaction CheckResult.FutureDate
        val existing = checkInDao.find(goalId, date.toString())
        if (goal.measurement.isAmount) amountAction(goal, date, action, existing) else checkAction(goal, date, action, existing)
    }

    private suspend fun checkAction(goal: Goal, date: LocalDate, action: CheckAction, existing: CheckInEntity?): CheckResult {
        if (action == CheckAction.MARK_MINIMUM && !goal.measurement.hasMinimumStep) return CheckResult.NoChange
        val now = clock.now()
        val result = CheckInRules.apply(existing?.status ?: DayStatus.PENDING, action, date, now.toLocalDate())
        if (result is CheckResult.Changed) {
            val late = CheckInRules.lateMinutes(goal.schedule.window, date, now, clock.zone).takeIf { result.status.isMet }
            write(goal.id, date, result.status, result.failReason, existing?.amount ?: 0, late, existing)
        }
        return result
    }

    /** Ticking an amount goal logs what is left to the target; clearing it removes the day's logs. */
    private suspend fun amountAction(goal: Goal, date: LocalDate, action: CheckAction, existing: CheckInEntity?): CheckResult {
        val status = existing?.status ?: DayStatus.PENDING
        val amount = existing?.amount ?: 0
        when (action) {
            CheckAction.TOGGLE_DONE ->
                if (status == DayStatus.DONE) clearDay(goal, date, existing) else logInternal(goal, date, (goal.measurement.target - amount).coerceAtLeast(1))
            CheckAction.CLEAR -> clearDay(goal, date, existing)
            CheckAction.MARK_FAILED -> write(goal.id, date, DayStatus.FAILED, FailReason.MANUAL, amount, null, existing)
            CheckAction.MARK_MINIMUM -> return CheckResult.NoChange
        }
        val after = checkInDao.find(goal.id, date.toString())
        return CheckResult.Changed(after?.status ?: DayStatus.PENDING, after?.failedReason)
    }

    private suspend fun clearDay(goal: Goal, date: LocalDate, existing: CheckInEntity?) {
        amountLogDao.deleteFor(goal.id, date.toString())
        write(goal.id, date, DayStatus.PENDING, null, 0, null, existing)
    }

    suspend fun logAmount(goalId: Long, date: LocalDate, delta: Int) = database.withTransaction {
        val goal = goalDao.find(goalId)?.toModel() ?: return@withTransaction
        if (!goal.measurement.isAmount || delta <= 0) return@withTransaction
        if (!CheckInRules.canEdit(date, clock.now().toLocalDate())) return@withTransaction
        logInternal(goal, date, delta)
    }

    suspend fun undoLastLog(goalId: Long, date: LocalDate) = database.withTransaction {
        val goal = goalDao.find(goalId)?.toModel() ?: return@withTransaction
        val latest = amountLogDao.latest(goalId, date.toString()) ?: return@withTransaction
        amountLogDao.delete(latest.id)
        recalculate(goal, date)
    }

    private suspend fun logInternal(goal: Goal, date: LocalDate, delta: Int) {
        amountLogDao.insert(AmountLogEntity(goalId = goal.id, date = date.toString(), delta = delta, loggedAt = epochMillis()))
        recalculate(goal, date)
    }

    private suspend fun recalculate(goal: Goal, date: LocalDate) {
        val now = clock.now()
        val amount = AmountRules.add(0, amountLogDao.sum(goal.id, date.toString()))
        val existing = checkInDao.find(goal.id, date.toString())
        val fired = FailureEvaluator.firedReason(goal, date, now, clock.zone, settings.settings.value.dayEndMinute)
        val resolved = AmountRules.resolve(goal.measurement, amount, existing?.status ?: DayStatus.PENDING, existing?.failedReason, fired)
        val wasMet = existing?.status?.isMet == true
        val late = when {
            !resolved.status.isMet -> null
            wasMet -> existing?.lateMinutes
            else -> CheckInRules.lateMinutes(goal.schedule.window, date, now, clock.zone)
        }
        write(goal.id, date, resolved.status, resolved.failReason, amount, late, existing)
    }

    private suspend fun write(
        goalId: Long,
        date: LocalDate,
        status: DayStatus,
        reason: FailReason?,
        amount: Int,
        lateMinutes: Int?,
        existing: CheckInEntity?,
    ) {
        val millis = epochMillis()
        checkInDao.save(
            CheckInEntity(
                id = existing?.id ?: 0,
                goalId = goalId,
                date = date.toString(),
                status = status,
                failedReason = reason,
                completedAt = millis.takeIf { status.isMet },
                updatedAt = millis,
                amount = amount,
                lateMinutes = lateMinutes,
            ),
        )
    }

    fun startTimer(goalId: Long) = settings.startTimer(goalId, epochMillis())

    /** Stops the timer and logs the whole minutes it ran for today; returns the minutes logged. */
    suspend fun stopTimer(goalId: Long): Int {
        val start = settings.stopTimer(goalId) ?: return 0
        val minutes = TimerRules.elapsedMinutes(start, epochMillis())
        if (minutes > 0) logAmount(goalId, clock.now().toLocalDate(), minutes)
        return minutes
    }

    /**
     * Applies the window-end and day-end rules and judges quota periods. Safe to run often: dates that
     * were already closed are never reopened, and results the user set are never overwritten.
     */
    suspend fun evaluate() = evaluationLock.withLock {
        val now = clock.now()
        val zone = clock.zone
        val state = settings.evalState()
        val oldZone = state.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
        val neutral = state.neutralDates + ZoneChange.skippedDates(state.lastToday, now.atZone(zone).toInstant(), oldZone, zone)
        val dayEnd = settings.settings.value.dayEndMinute
        database.withTransaction {
            val models = goalDao.all().map(GoalEntity::toModel)
            val rows = checkInDao.all()
            val byKey = rows.associateBy { it.goalId to LocalDate.parse(it.date) }
            val failures = FailureEvaluator.newFailures(
                goals = models,
                statusOf = { goalId, date -> byKey[goalId to date]?.status ?: DayStatus.PENDING },
                now = now,
                zone = zone,
                dayEndMinute = dayEnd,
                finalisedThrough = state.finalisedThrough,
                neutralDates = neutral,
            )
            failures.forEach { failure ->
                val existing = byKey[failure.goalId to failure.date]
                write(failure.goalId, failure.date, DayStatus.FAILED, failure.reason, existing?.amount ?: 0, null, existing)
            }
            judgeQuotaPeriods(models, rows, neutral, state.finalisedThrough)
        }
        settings.saveEvalState(
            state.copy(
                finalisedThrough = FailureEvaluator.finalisedAfter(now, state.finalisedThrough),
                lastToday = now.toLocalDate(),
                zoneId = zone.id,
                neutralDates = neutral,
            ),
        )
    }

    private suspend fun judgeQuotaPeriods(goals: List<Goal>, rows: List<CheckInEntity>, neutral: Set<LocalDate>, finalisedThrough: LocalDate?) {
        val now = clock.now()
        val dayEnd = settings.settings.value.dayEndMinute
        goals.filter { it.state == GoalState.ACTIVE && it.schedule.isQuota }.forEach { goal ->
            val frequency = goal.schedule.frequency
            val metDates = rows.filter { it.goalId == goal.id && it.status.isMet }.map { LocalDate.parse(it.date) }.toSet()
            val first = Periods.of(frequency, goal.schedule.startDate) ?: return@forEach
            generateSequence(first) { Periods.of(frequency, it.end.plusDays(1)) }
                .takeWhile { it.start <= now.toLocalDate() }
                .filter { finalisedThrough == null || it.end > finalisedThrough }
                .forEach { period ->
                    val outcome = QuotaRules.evaluate(goal.schedule, period, metDates, neutral, now, dayEnd)
                    if (outcome.status == PeriodStatus.OPEN) {
                        periodDao.deleteFor(goal.id, period.start.toString())
                    } else {
                        periodDao.upsert(
                            PeriodResultEntity(
                                goalId = goal.id,
                                periodStart = period.start.toString(),
                                periodEnd = period.end.toString(),
                                originalRequired = outcome.originalRequired,
                                adjustedRequired = outcome.adjustedRequired,
                                eligibleDays = outcome.eligibleDays,
                                pausedDays = 0,
                                rescuedDays = 0,
                                completed = outcome.completed,
                                status = outcome.status,
                                failedAt = outcome.failedOn?.toString(),
                                recalculatedAt = epochMillis(),
                            ),
                        )
                    }
                }
        }
    }

    /** Fires whenever goals or results change, so alarms can be rebuilt from the database. */
    val changes: Flow<Unit> = kotlinx.coroutines.flow.combine(goals, checkIns) { _, _ -> }

    /** What to warn about right now and when the app next needs to wake up. */
    suspend fun warningBatch(): WarningBatch {
        val now = clock.now()
        val today = now.toLocalDate()
        val models = goalDao.all().map(GoalEntity::toModel)
        val statuses = checkInDao.all().associate { (it.goalId to LocalDate.parse(it.date)) to it.status }
        val marks = warningDao.forDate(today.toString()).associate { (it.goalId to LocalDate.parse(it.date)) to it.lastIndex }
        val plan = WarningPlanner.plan(
            goals = models,
            statusOf = { goalId, date -> statuses[goalId to date] ?: DayStatus.PENDING },
            marks = marks,
            now = now,
            zone = clock.zone,
            dayEndMinute = settings.settings.value.dayEndMinute,
        )
        val resolved = models.map { it.id }.filter { (statuses[it to today] ?: DayStatus.PENDING) != DayStatus.PENDING }.toSet()
        return WarningBatch(plan, models.associateBy { it.id }, resolved, now.atZone(clock.zone).toInstant())
    }

    suspend fun statusOn(goalId: Long, date: LocalDate): DayStatus =
        checkInDao.find(goalId, date.toString())?.status ?: DayStatus.PENDING

    suspend fun markWarningShown(warning: Warning, level: DeliveryLevel) {
        warningDao.upsert(
            WarningMarkEntity(goalId = warning.goalId, date = warning.date.toString(), lastIndex = warning.index, level = level, firedAt = epochMillis()),
        )
        warningDao.deleteBefore(clock.now().toLocalDate().minusDays(KEEP_MARK_DAYS).toString())
    }

    fun neutralDates(): Set<LocalDate> = settings.evalState().neutralDates

    private companion object {
        const val KEEP_MARK_DAYS = 7L
    }

    private fun epochMillis(): Long = clock.now().atZone(clock.zone).toInstant().toEpochMilli()
}
