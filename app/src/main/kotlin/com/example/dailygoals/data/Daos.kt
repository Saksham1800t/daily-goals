package com.example.dailygoals.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dailygoals.model.GoalState
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY sortOrder, id")
    suspend fun all(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun find(id: Long): GoalEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM goals")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: GoalState)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins")
    fun observeAll(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins")
    suspend fun all(): List<CheckInEntity>

    @Query("SELECT * FROM check_ins WHERE goalId = :goalId AND date = :date")
    suspend fun find(goalId: Long, date: String): CheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(checkIn: CheckInEntity)

    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()
}

@Dao
interface AmountLogDao {
    @Query("SELECT * FROM amount_logs")
    suspend fun all(): List<AmountLogEntity>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM amount_logs WHERE goalId = :goalId AND date = :date")
    suspend fun sum(goalId: Long, date: String): Int

    @Query("SELECT * FROM amount_logs WHERE goalId = :goalId AND date = :date ORDER BY id DESC LIMIT 1")
    suspend fun latest(goalId: Long, date: String): AmountLogEntity?

    @Insert
    suspend fun insert(log: AmountLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(log: AmountLogEntity): Long

    @Query("DELETE FROM amount_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM amount_logs WHERE goalId = :goalId AND date = :date")
    suspend fun deleteFor(goalId: Long, date: String)

    @Query("DELETE FROM amount_logs")
    suspend fun deleteAll()
}

@Dao
interface WarningMarkDao {
    @Query("SELECT * FROM warning_marks WHERE date = :date")
    suspend fun forDate(date: String): List<WarningMarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mark: WarningMarkEntity)

    @Query("DELETE FROM warning_marks WHERE date < :date")
    suspend fun deleteBefore(date: String)
}

@Dao
interface PeriodResultDao {
    @Query("SELECT * FROM period_results")
    suspend fun all(): List<PeriodResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: PeriodResultEntity)

    @Query("DELETE FROM period_results WHERE goalId = :goalId AND periodStart = :periodStart")
    suspend fun deleteFor(goalId: Long, periodStart: String)

    @Query("DELETE FROM period_results")
    suspend fun deleteAll()
}
