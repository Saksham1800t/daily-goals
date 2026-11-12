package com.example.dailygoals.data.backup

import com.example.dailygoals.data.AmountLogEntity
import com.example.dailygoals.data.AppDatabase
import com.example.dailygoals.data.CheckInEntity
import com.example.dailygoals.data.DisciplineColumns
import com.example.dailygoals.data.GoalEntity
import com.example.dailygoals.data.MeasurementColumns
import com.example.dailygoals.data.PeriodResultEntity
import com.example.dailygoals.data.ScheduleColumns
import com.example.dailygoals.engine.PeriodStatus
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.model.MeasureType
import com.example.dailygoals.model.MonthlyNth
import com.example.dailygoals.model.QuotaFailureMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupException(message: String) : Exception(message)

data class Backup(
    val schemaVersion: Int,
    val goals: List<GoalEntity>,
    val checkIns: List<CheckInEntity>,
    val amountLogs: List<AmountLogEntity>,
    val periodResults: List<PeriodResultEntity>,
    val dayEndMinute: Int?,
    val minimumWeightPercent: Int?,
)

enum class RestoreMode { MERGE, REPLACE }

object BackupCodec {
    private const val APP_ID = "skriti"

    fun encode(backup: Backup): String = JSONObject().apply {
        put("app", APP_ID)
        put("schemaVersion", backup.schemaVersion)
        backup.dayEndMinute?.let { put("dayEndMinute", it) }
        backup.minimumWeightPercent?.let { put("minimumWeightPercent", it) }
        put("goals", JSONArray(backup.goals.map(::goalJson)))
        put("checkIns", JSONArray(backup.checkIns.map(::checkInJson)))
        put("amountLogs", JSONArray(backup.amountLogs.map(::logJson)))
        put("periodResults", JSONArray(backup.periodResults.map(::periodJson)))
    }.toString(1)

    /** Rejects files that are not a backup or were written by a newer version than this app understands. */
    fun decode(text: String): Backup {
        val root = try {
            JSONObject(text)
        } catch (error: JSONException) {
            throw BackupException("This file is not a backup.")
        }
        if (root.optString("app") != APP_ID) throw BackupException("This file is not a backup from this app.")
        val version = root.optInt("schemaVersion", -1)
        if (version < 1) throw BackupException("The backup has no version.")
        if (version > AppDatabase.VERSION) throw BackupException("The backup was made by a newer version of the app.")
        return try {
            Backup(
                schemaVersion = version,
                goals = root.objects("goals").map(::goalFrom),
                checkIns = root.objects("checkIns").map(::checkInFrom),
                amountLogs = root.objects("amountLogs").map(::logFrom),
                periodResults = root.objects("periodResults").map(::periodFrom),
                dayEndMinute = root.optInt("dayEndMinute", -1).takeIf { it >= 0 },
                minimumWeightPercent = root.optInt("minimumWeightPercent", -1).takeIf { it >= 0 },
            )
        } catch (error: JSONException) {
            throw BackupException("The backup is damaged: ${error.message}")
        } catch (error: IllegalArgumentException) {
            throw BackupException("The backup has a value this version does not know: ${error.message}")
        }
    }

    /** One row per recorded day, oldest first, with fields quoted so names may contain commas. */
    fun csv(goals: List<GoalEntity>, checkIns: List<CheckInEntity>): String {
        val names = goals.associate { it.id to it.name }
        val rows = checkIns.sortedWith(compareBy({ it.date }, { names[it.goalId].orEmpty() })).map { row ->
            listOf(row.date, names[row.goalId].orEmpty(), row.status.name, row.amount.toString(), row.lateMinutes?.toString().orEmpty(), row.failedReason?.name.orEmpty())
        }
        return (listOf(listOf("date", "goal", "status", "amount", "late_minutes", "fail_reason")) + rows)
            .joinToString("\n") { cells -> cells.joinToString(",") { quote(it) } } + "\n"
    }

    private fun quote(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    private fun goalJson(goal: GoalEntity) = JSONObject().apply {
        put("id", goal.id)
        put("name", goal.name)
        put("type", goal.type.name)
        put("state", goal.state.name)
        put("sortOrder", goal.sortOrder)
        put("frequencyType", goal.schedule.frequencyType.name)
        put("activeDays", goal.schedule.activeDays)
        put("windowStart", goal.schedule.windowStart ?: JSONObject.NULL)
        put("windowEnd", goal.schedule.windowEnd ?: JSONObject.NULL)
        put("startDate", goal.schedule.startDate)
        put("freqCount", goal.schedule.freqCount)
        put("freqIntervalDays", goal.schedule.freqIntervalDays)
        put("monthlyNth", goal.schedule.monthlyNth.name)
        put("monthlyWeekday", goal.schedule.monthlyWeekday)
        put("quotaFailureMode", goal.schedule.quotaFailureMode.name)
        put("graceMinutes", goal.schedule.graceMinutes)
        put("failOnWindowEnd", goal.discipline.failOnWindowEnd)
        put("failOnDayEnd", goal.discipline.failOnDayEnd)
        put("failOnManual", goal.discipline.failOnManual)
        put("measureType", goal.measurement.measureType.name)
        put("unit", goal.measurement.unit)
        put("targetAmount", goal.measurement.targetAmount)
        put("minimumAmount", goal.measurement.minimumAmount)
        put("minimumLabel", goal.measurement.minimumLabel ?: JSONObject.NULL)
    }

    private fun goalFrom(json: JSONObject) = GoalEntity(
        id = json.getLong("id"),
        name = json.getString("name"),
        type = GoalType.valueOf(json.getString("type")),
        state = GoalState.valueOf(json.getString("state")),
        sortOrder = json.getInt("sortOrder"),
        schedule = ScheduleColumns(
            frequencyType = Frequency.valueOf(json.getString("frequencyType")),
            activeDays = json.getInt("activeDays"),
            windowStart = json.intOrNull("windowStart"),
            windowEnd = json.intOrNull("windowEnd"),
            startDate = json.getString("startDate"),
            freqCount = json.optInt("freqCount", 1),
            freqIntervalDays = json.optInt("freqIntervalDays", 2),
            monthlyNth = MonthlyNth.valueOf(json.optString("monthlyNth", "FIRST")),
            monthlyWeekday = json.optInt("monthlyWeekday", 1),
            quotaFailureMode = QuotaFailureMode.valueOf(json.optString("quotaFailureMode", "WHEN_UNREACHABLE")),
            graceMinutes = json.optInt("graceMinutes", 5),
        ),
        discipline = DisciplineColumns(
            failOnWindowEnd = json.optBoolean("failOnWindowEnd", false),
            failOnDayEnd = json.optBoolean("failOnDayEnd", false),
            failOnManual = json.optBoolean("failOnManual", false),
        ),
        measurement = MeasurementColumns(
            measureType = MeasureType.valueOf(json.optString("measureType", "CHECK")),
            unit = json.optString("unit", ""),
            targetAmount = json.optInt("targetAmount", 1),
            minimumAmount = json.optInt("minimumAmount", 1),
            minimumLabel = json.stringOrNull("minimumLabel"),
        ),
    )

    private fun checkInJson(row: CheckInEntity) = JSONObject().apply {
        put("id", row.id)
        put("goalId", row.goalId)
        put("date", row.date)
        put("status", row.status.name)
        put("failedReason", row.failedReason?.name ?: JSONObject.NULL)
        put("completedAt", row.completedAt ?: JSONObject.NULL)
        put("updatedAt", row.updatedAt)
        put("amount", row.amount)
        put("lateMinutes", row.lateMinutes ?: JSONObject.NULL)
    }

    private fun checkInFrom(json: JSONObject) = CheckInEntity(
        id = json.getLong("id"),
        goalId = json.getLong("goalId"),
        date = json.getString("date"),
        status = DayStatus.valueOf(json.getString("status")),
        failedReason = json.stringOrNull("failedReason")?.let(FailReason::valueOf),
        completedAt = if (json.isNull("completedAt")) null else json.getLong("completedAt"),
        updatedAt = json.getLong("updatedAt"),
        amount = json.optInt("amount", 0),
        lateMinutes = json.intOrNull("lateMinutes"),
    )

    private fun logJson(log: AmountLogEntity) = JSONObject().apply {
        put("id", log.id)
        put("goalId", log.goalId)
        put("date", log.date)
        put("delta", log.delta)
        put("loggedAt", log.loggedAt)
    }

    private fun logFrom(json: JSONObject) = AmountLogEntity(
        id = json.getLong("id"),
        goalId = json.getLong("goalId"),
        date = json.getString("date"),
        delta = json.getInt("delta"),
        loggedAt = json.getLong("loggedAt"),
    )

    private fun periodJson(result: PeriodResultEntity) = JSONObject().apply {
        put("id", result.id)
        put("goalId", result.goalId)
        put("periodStart", result.periodStart)
        put("periodEnd", result.periodEnd)
        put("originalRequired", result.originalRequired)
        put("adjustedRequired", result.adjustedRequired)
        put("eligibleDays", result.eligibleDays)
        put("pausedDays", result.pausedDays)
        put("rescuedDays", result.rescuedDays)
        put("completed", result.completed)
        put("status", result.status.name)
        put("failedAt", result.failedAt ?: JSONObject.NULL)
        put("recalculatedAt", result.recalculatedAt)
    }

    private fun periodFrom(json: JSONObject) = PeriodResultEntity(
        id = json.getLong("id"),
        goalId = json.getLong("goalId"),
        periodStart = json.getString("periodStart"),
        periodEnd = json.getString("periodEnd"),
        originalRequired = json.getInt("originalRequired"),
        adjustedRequired = json.getInt("adjustedRequired"),
        eligibleDays = json.getInt("eligibleDays"),
        pausedDays = json.optInt("pausedDays", 0),
        rescuedDays = json.optInt("rescuedDays", 0),
        completed = json.getInt("completed"),
        status = PeriodStatus.valueOf(json.getString("status")),
        failedAt = json.stringOrNull("failedAt"),
        recalculatedAt = json.getLong("recalculatedAt"),
    )
}
