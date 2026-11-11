package com.example.dailygoals.data

import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.engine.CheckInRules
import com.example.dailygoals.engine.CheckResult
import com.example.dailygoals.engine.Clock
import com.example.dailygoals.model.CheckIn
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalRepository(
    private val goalDao: GoalDao,
    private val checkInDao: CheckInDao,
    private val clock: Clock,
) {
    val goals: Flow<List<Goal>> = goalDao.observeAll().map { list -> list.map(GoalEntity::toModel) }

    fun checkIns(from: LocalDate, to: LocalDate): Flow<List<CheckIn>> =
        checkInDao.observeBetween(from.toString(), to.toString()).map { list -> list.map(CheckInEntity::toModel) }

    suspend fun goal(id: Long): Goal? = goalDao.find(id)?.toModel()

    suspend fun save(goal: Goal) {
        if (goal.id == 0L) {
            goalDao.insert(goal.copy(sortOrder = goalDao.nextSortOrder()).toEntity())
        } else {
            goalDao.update(goal.toEntity())
        }
    }

    suspend fun archive(id: Long) = goalDao.setState(id, GoalState.ARCHIVED)

    suspend fun applyAction(goalId: Long, date: LocalDate, action: CheckAction): CheckResult {
        val now = clock.now()
        val existing = checkInDao.find(goalId, date.toString())
        val result = CheckInRules.apply(existing?.status ?: DayStatus.PENDING, action, date, now.toLocalDate())
        if (result is CheckResult.Changed) {
            val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            checkInDao.save(
                CheckInEntity(
                    id = existing?.id ?: 0,
                    goalId = goalId,
                    date = date.toString(),
                    status = result.status,
                    failedReason = result.failReason,
                    completedAt = nowMillis.takeIf { result.status == DayStatus.DONE },
                    updatedAt = nowMillis,
                ),
            )
        }
        return result
    }
}
