package com.example.dailygoals.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.dailygoals.engine.BlockKind
import com.example.dailygoals.engine.DeliveryLevel
import com.example.dailygoals.engine.Intensity
import com.example.dailygoals.engine.PeriodStatus
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.model.MeasureType
import com.example.dailygoals.model.MonthlyNth
import com.example.dailygoals.model.QuotaFailureMode
import com.example.dailygoals.model.WarningMode

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
    @Embedded val reminder: ReminderColumns,
    @Embedded val avoidance: AvoidanceColumns,
)

data class ReminderColumns(
    @ColumnInfo(defaultValue = "'NONE'") val warningMode: WarningMode,
    @ColumnInfo(defaultValue = "10") val warningIntervalMin: Int,
    @ColumnInfo(defaultValue = "1200") val warningMinute: Int,
    @ColumnInfo(defaultValue = "0") val strongAlert: Boolean,
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
    @ColumnInfo(defaultValue = "'NONE'") val punishmentIntensity: Intensity,
    @ColumnInfo(defaultValue = "60") val blockMinutes: Int,
    val blockMessage: String?,
    @ColumnInfo(defaultValue = "0") val scheduledBlockEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val autoKeepAtWindowEnd: Boolean = true,
    @ColumnInfo(defaultValue = "1") val allowTempAccess: Boolean = true,
)

/** Package names, one per line. */
data class AvoidanceColumns(
    @ColumnInfo(defaultValue = "''") val temptingPackages: String,
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
    @ColumnInfo(defaultValue = "0") val punishmentSkipped: Boolean = false,
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

/** The last warning shown for a goal on a date, so a warning fires once and the level used is recorded. */
@Entity(
    tableName = "warning_marks",
    indices = [Index(value = ["goalId", "date"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"]),
    ],
)
data class WarningMarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val date: String,
    val lastIndex: Int,
    val level: DeliveryLevel,
    val firedAt: Long,
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

/** One try to open a blocked app. An attempt is never a failure. */
@Entity(tableName = "block_attempts", indices = [Index(value = ["goalId", "attemptedAt"])])
data class BlockAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val packageName: String,
    val kind: BlockKind,
    val attemptedAt: Long,
)

/** One use of temporary access; [goalIds] is a comma-separated list of the goals that blocked the app. */
@Entity(tableName = "temp_access", indices = [Index(value = ["startedAt"])])
data class TempAccessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val goalIds: String,
    val reason: String,
    val startedAt: Long,
    val endsAt: Long,
    val minutes: Int,
    @ColumnInfo(defaultValue = "0") val endedEarly: Boolean = false,
)

/** A change that would weaken a running block, held until that block ends. */
@Entity(tableName = "pending_changes", indices = [Index(value = ["appliesAt"])])
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long?,
    val settingKey: String?,
    val newValue: String,
    val appliesAt: Long,
    val createdAt: Long,
)
