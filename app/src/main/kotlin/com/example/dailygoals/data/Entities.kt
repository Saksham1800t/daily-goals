package com.example.dailygoals.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: GoalType,
    val state: GoalState,
    val sortOrder: Int,
    @Embedded val schedule: ScheduleColumns,
)

/** Dates are local yyyy-MM-dd text and times are minutes since midnight. */
data class ScheduleColumns(
    val frequencyType: Frequency,
    val activeDays: Int,
    val windowStart: Int?,
    val windowEnd: Int?,
    val startDate: String,
)

@Entity(
    tableName = "check_ins",
    indices = [Index(value = ["goalId", "date"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"]),
    ],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val date: String,
    val status: DayStatus,
    val failedReason: FailReason?,
    val completedAt: Long?,
    val updatedAt: Long,
)
