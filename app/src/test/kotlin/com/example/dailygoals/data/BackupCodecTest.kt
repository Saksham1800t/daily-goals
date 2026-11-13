package com.example.dailygoals.data

import com.example.dailygoals.data.backup.Backup
import com.example.dailygoals.data.backup.BackupCodec
import com.example.dailygoals.data.backup.BackupException
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val goal = GoalEntity(
        id = 7,
        name = "Study, \"deep\" work",
        type = GoalType.DO,
        state = GoalState.ACTIVE,
        sortOrder = 2,
        schedule = ScheduleColumns(
            frequencyType = Frequency.PER_WEEK,
            activeDays = 31,
            windowStart = 360,
            windowEnd = null,
            startDate = "2026-11-01",
            freqCount = 4,
            freqIntervalDays = 3,
            monthlyNth = MonthlyNth.LAST,
            monthlyWeekday = 5,
            quotaFailureMode = QuotaFailureMode.END_OF_PERIOD,
            graceMinutes = 10,
        ),
        discipline = DisciplineColumns(
            failOnWindowEnd = true,
            failOnDayEnd = false,
            failOnManual = true,
            punishmentIntensity = Intensity.MODERATE,
            blockMinutes = 120,
            blockMessage = "No {app} until {until}",
        ),
        measurement = MeasurementColumns(MeasureType.AMOUNT, "min", 120, 20, "Put on shoes"),
        reminder = ReminderColumns(WarningMode.REPEATING, warningIntervalMin = 15, warningMinute = 1260, strongAlert = true),
        avoidance = AvoidanceColumns("com.instagram.android\ncom.youtube"),
    )
    private val checkIn = CheckInEntity(3, 7, "2026-11-10", DayStatus.FAILED, FailReason.WINDOW_END, null, 555, amount = 15, lateMinutes = null, punishmentSkipped = true)
    private val log = AmountLogEntity(9, 7, "2026-11-10", 15, 444)
    private val period = PeriodResultEntity(1, 7, "2026-11-09", "2026-11-15", 4, 4, 7, 0, 0, 2, PeriodStatus.FAILED, "2026-11-13", 777)
    private val backup = Backup(AppDatabase.VERSION,listOf(goal), listOf(checkIn), listOf(log), listOf(period), dayEndMinute = 1380, minimumWeightPercent = 70)

    @Test
    fun everythingSurvivesAnEncodeAndDecodeRoundTrip() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup))
        assertEquals(backup, decoded)
    }

    @Test
    fun aBackupFromAnEarlierVersionGetsDefaultsForFieldsItLacks() {
        val text = """
            {"app":"skriti","schemaVersion":1,"goals":[{"id":1,"name":"Gym","type":"DO","state":"ACTIVE","sortOrder":0,
            "frequencyType":"DAILY","activeDays":127,"windowStart":null,"windowEnd":null,"startDate":"2026-11-01"}],
            "checkIns":[{"id":1,"goalId":1,"date":"2026-11-02","status":"DONE","failedReason":null,"completedAt":5,"updatedAt":6}]}
        """.trimIndent()
        val decoded = BackupCodec.decode(text)
        val gym = decoded.goals.single()
        assertEquals(5, gym.schedule.graceMinutes)
        assertEquals(MeasureType.CHECK, gym.measurement.measureType)
        assertEquals(false, gym.discipline.failOnManual)
        assertEquals(0, decoded.checkIns.single().amount)
        assertEquals(WarningMode.NONE, gym.reminder.warningMode)
        assertEquals(10, gym.reminder.warningIntervalMin)
        assertEquals(null, decoded.dayEndMinute)
    }

    @Test
    fun aFileThatIsNotABackupIsRejectedWithAReason() {
        assertRejected("not json at all", "not a backup")
        assertRejected("""{"app":"other","schemaVersion":1}""", "not a backup")
        assertRejected("""{"app":"skriti"}""", "no version")
    }

    @Test
    fun aBackupFromANewerVersionIsRejected() {
        assertRejected("""{"app":"skriti","schemaVersion":${AppDatabase.VERSION + 1}}""", "newer version")
    }

    @Test
    fun aDamagedBackupIsRejectedInsteadOfCrashing() {
        assertRejected("""{"app":"skriti","schemaVersion":2,"goals":[{"id":1}]}""", "damaged")
        val unknownStatus = BackupCodec.encode(backup).replace("\"FAILED\"", "\"EXPLODED\"")
        assertRejected(unknownStatus, "does not know")
    }

    private fun assertRejected(text: String, reason: String) {
        try {
            BackupCodec.decode(text)
            fail("Expected the backup to be rejected")
        } catch (error: BackupException) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(reason))
        }
    }

    @Test
    fun csvListsRecordedDaysOldestFirstAndQuotesAwkwardNames() {
        val other = checkIn.copy(id = 4, date = "2026-11-09", status = DayStatus.DONE, failedReason = null, amount = 0, lateMinutes = 3)
        val csv = BackupCodec.csv(listOf(goal), listOf(checkIn, other))
        val lines = csv.trimEnd().split("\n")
        assertEquals("date,goal,status,amount,late_minutes,fail_reason", lines[0])
        assertEquals("2026-11-09,\"Study, \"\"deep\"\" work\",DONE,0,3,", lines[1])
        assertEquals("2026-11-10,\"Study, \"\"deep\"\" work\",FAILED,15,,WINDOW_END", lines[2])
        assertEquals(3, lines.size)
    }
}
