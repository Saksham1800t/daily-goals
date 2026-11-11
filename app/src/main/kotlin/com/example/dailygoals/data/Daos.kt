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

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun find(id: Long): GoalEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM goals")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: GoalState)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: String, to: String): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE goalId = :goalId AND date = :date")
    suspend fun find(goalId: Long, date: String): CheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(checkIn: CheckInEntity)
}
