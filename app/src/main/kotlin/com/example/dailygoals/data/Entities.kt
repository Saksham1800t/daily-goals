package com.example.dailygoals.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.dailygoals.engine.PeriodStatus
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.model.MeasureType
import com.example.dailygoals.model.MonthlyNth
import com.example.dailygoals.model.QuotaFailureMode

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: GoalType,
    val state: GoalState,
    val sortOrder: Int,
    @Embedded val schedule: ScheduleColumns,
    @Embedded val discipline: DisciplineColumns,
    @Embedded val measurement: MeasurementColumns,
)

/** Dates are local yyyy-MM-dd text and times are minutes since midnight. */
data class ScheduleColumns(
    val frequencyType: Frequency,
    val activeDays: Int,
    val windowStart: Int?,
    val windowEnd: Int?,
    val startDate: String,
    @ColumnInfo(defaultValue = "1") val freqCount: Int,
    @ColumnInfo(defaultValue = "2") val freqIntervalDays: Int,
    @ColumnInfo(defaultValue = "'FIRST'") val monthlyNth: MonthlyNth,
    @ColumnInfo(defaultValue = "1") val monthlyWeekday: Int,
    @ColumnInfo(defaultValue = "'WHEN_UNREACHABLE'") val quotaFailureMode: QuotaFailureMode,
    @ColumnInfo(defaultValue = "5") val graceMinutes: Int,
)

data class DisciplineColumns(
    @ColumnInfo(defaultValue = "0") val failOnWindowEnd: Boolean,
    @ColumnInfo(defaultValue = "0") val failOnDayEnd: Boolean,
    @ColumnInfo(defaultValue = "0") val failOnManual: Boolean,
)

data class MeasurementColumns(
    @ColumnInfo(defaultValue = "'CHECK'") val measureType: MeasureType,
    @ColumnInfo(defaultValue = "''") val unit: String,
    @ColumnInfo(defaultValue = "1") val targetAmount: Int,
    @ColumnInfo(defaultValue = "1") val minimumAmount: Int,
    val minimumLabel: String?,
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
    @ColumnInfo(defaultValue = "0") val amount: Int = 0,
    val lateMinutes: Int? = null,
)

@Entity(
    tableName = "amount_logs",
    indices = [Index(value = ["goalId", "date"])],
    foreignKeys = [
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"]),
    ],
)
data class AmountLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val date: String,
    val delta: Int,
    val loggedAt: Long,
)

@Entity(
    tableName = "period_results",
    indices = [Index(value = ["goalId", "periodStart"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"]),
    ],
)
data class PeriodResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val periodStart: String,
    val periodEnd: String,
    val originalRequired: Int,
    val adjustedRequired: Int,
    val eligibleDays: Int,
    @ColumnInfo(defaultValue = "0") val pausedDays: Int,
    @ColumnInfo(defaultValue = "0") val rescuedDays: Int,
    val completed: Int,
    val status: PeriodStatus,
    val failedAt: String?,
    val recalculatedAt: Long,
)
