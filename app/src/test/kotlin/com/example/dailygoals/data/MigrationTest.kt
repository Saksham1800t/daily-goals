package com.example.dailygoals.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the real migration SQL on a plain SQLite database built from the exported schema files,
 * so a migration is checked without a phone: rows survive, defaults are filled, and the result has
 * exactly the structure of a freshly created database.
 */
class MigrationTest {
    private val schemaDir = File("schemas/com.example.dailygoals.data.AppDatabase")
    private lateinit var connection: Connection

    @Before
    fun open() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
    }

    @After
    fun close() = connection.close()

    private fun schemaOf(version: Int): JSONObject =
        JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database")

    private fun createStatements(version: Int): List<String> {
        val entities = schemaOf(version).getJSONArray("entities")
        return buildList {
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                add(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) add(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
    }

    private fun tableNames(version: Int): List<String> {
        val entities = schemaOf(version).getJSONArray("entities")
        return List(entities.length()) { entities.getJSONObject(it).getString("tableName") }
    }

    private fun execute(sql: String) = connection.createStatement().use { it.execute(sql) }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): List<T> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(read(rows)) } }
        }

    private fun createVersion(version: Int) = createStatements(version).forEach(::execute)

    private fun insertVersionOneData() {
        execute(
            "INSERT INTO goals (id, name, type, state, sortOrder, frequencyType, activeDays, windowStart, windowEnd, startDate) " +
                "VALUES (1, 'Wake up early', 'DO', 'ACTIVE', 0, 'WEEKDAYS', 31, 360, 420, '2026-11-01')",
        )
        execute(
            "INSERT INTO goals (id, name, type, state, sortOrder, frequencyType, activeDays, windowStart, windowEnd, startDate) " +
                "VALUES (2, 'No smoking', 'AVOID', 'ACTIVE', 1, 'DAILY', 127, NULL, NULL, '2026-11-01')",
        )
        execute("INSERT INTO check_ins (id, goalId, date, status, failedReason, completedAt, updatedAt) VALUES (1, 1, '2026-11-10', 'DONE', NULL, 111, 222)")
        execute("INSERT INTO check_ins (id, goalId, date, status, failedReason, completedAt, updatedAt) VALUES (2, 2, '2026-11-10', 'FAILED', 'MANUAL', NULL, 333)")
    }

    private fun migrate() = MigrationSql.V1_TO_V2.forEach(::execute)

    private fun migrateToThree() = MigrationSql.V2_TO_V3.forEach(::execute)

    private fun migrateToFour() = MigrationSql.V3_TO_V4.forEach(::execute)

    private fun migrateToFive() = MigrationSql.V4_TO_V5.forEach(::execute)

    /** Builds a fresh database of [version] beside the migrated one and compares structure table by table. */
    private fun assertStructureEqualsFresh(version: Int) {
        val migrated = tableNames(version).associateWith(::structureOf)
        val original = connection
        val fresh = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection = fresh
        createVersion(version)
        val created = tableNames(version).associateWith(::structureOf)
        fresh.close()
        connection = original
        tableNames(version).forEach { table -> assertEquals("structure of $table", created.getValue(table), migrated.getValue(table)) }
    }

    @Test
    fun versionThreeKeepsRowsAndTurnsWarningsOffForExistingGoals() {
        createVersion(2)
        insertVersionOneData()
        migrateToThree()
        val reminders = query("SELECT id, name, warningMode, warningIntervalMin, warningMinute, strongAlert FROM goals ORDER BY id") {
            listOf(it.getLong(1), it.getString(2), it.getString(3), it.getInt(4), it.getInt(5), it.getInt(6))
        }
        assertEquals(
            listOf(listOf(1L, "Wake up early", "NONE", 10, 1200, 0), listOf(2L, "No smoking", "NONE", 10, 1200, 0)),
            reminders,
        )
        assertEquals(listOf(2), query("SELECT COUNT(*) FROM check_ins") { it.getInt(1) })
        assertEquals(listOf(0), query("SELECT COUNT(*) FROM warning_marks") { it.getInt(1) })
        execute("INSERT INTO warning_marks (goalId, date, lastIndex, level, firedAt) VALUES (1, '2026-11-11', 2, 'HEADS_UP', 5)")
        assertEquals(listOf(2), query("SELECT lastIndex FROM warning_marks") { it.getInt(1) })
    }

    @Test
    fun versionThreeStructureEqualsAFreshVersionThreeDatabase() {
        createVersion(2)
        migrateToThree()
        assertStructureEqualsFresh(3)
    }

    @Test
    fun migratingStraightFromVersionOneToThreeKeepsEverything() {
        createVersion(1)
        insertVersionOneData()
        migrate()
        migrateToThree()
        assertEquals(listOf(2), query("SELECT COUNT(*) FROM goals") { it.getInt(1) })
        assertEquals(listOf(listOf(1, 1)), query("SELECT SUM(failOnManual = 0), SUM(failOnManual = 1) FROM goals") { listOf(it.getInt(1), it.getInt(2)) })
        assertStructureEqualsFresh(3)
    }

    @Test
    fun versionFourKeepsRowsAndStartsEveryExistingGoalWithNoPunishment() {
        createVersion(3)
        insertVersionOneData()
        migrateToFour()
        val goals = query("SELECT id, punishmentIntensity, blockMinutes, blockMessage, temptingPackages FROM goals ORDER BY id") {
            listOf(it.getLong(1), it.getString(2), it.getInt(3), it.getString(4), it.getString(5))
        }
        assertEquals(listOf(listOf(1L, "NONE", 60, null, ""), listOf(2L, "NONE", 60, null, "")), goals)
        assertEquals(listOf(listOf(0, 0)), query("SELECT SUM(punishmentSkipped), COUNT(*) - 2 FROM check_ins") { listOf(it.getInt(1), it.getInt(2)) })
        assertEquals(listOf("No smoking"), query("SELECT name FROM goals WHERE id = 2") { it.getString(1) })
    }

    @Test
    fun versionFourStructureEqualsAFreshVersionFourDatabase() {
        createVersion(3)
        migrateToFour()
        assertStructureEqualsFresh(4)
    }

    @Test
    fun migratingStraightFromVersionOneToFourKeepsEverything() {
        createVersion(1)
        insertVersionOneData()
        migrate()
        migrateToThree()
        migrateToFour()
        assertEquals(listOf(2), query("SELECT COUNT(*) FROM goals") { it.getInt(1) })
        assertEquals(listOf(2), query("SELECT COUNT(*) FROM check_ins") { it.getInt(1) })
        assertStructureEqualsFresh(4)
    }

    @Test
    fun versionFiveKeepsRowsAndLeavesScheduledBlockingOff() {
        createVersion(4)
        insertVersionOneData()
        migrateToFive()
        val goals = query("SELECT id, scheduledBlockEnabled, autoKeepAtWindowEnd, allowTempAccess FROM goals ORDER BY id") {
            listOf(it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4))
        }
        assertEquals(listOf(listOf(1, 0, 1, 1), listOf(2, 0, 1, 1)), goals)
        assertEquals(listOf(2), query("SELECT COUNT(*) FROM check_ins") { it.getInt(1) })
        execute("INSERT INTO block_attempts (goalId, packageName, kind, attemptedAt) VALUES (2, 'com.a', 'SCHEDULED', 5)")
        execute("INSERT INTO temp_access (packageName, goalIds, reason, startedAt, endsAt, minutes) VALUES ('com.a', '2', 'bank', 1, 2, 5)")
        execute("INSERT INTO pending_changes (goalId, settingKey, newValue, appliesAt, createdAt) VALUES (2, NULL, '{}', 9, 1)")
        assertEquals(listOf(0), query("SELECT endedEarly FROM temp_access") { it.getInt(1) })
    }

    @Test
    fun versionFiveStructureEqualsAFreshVersionFiveDatabase() {
        createVersion(4)
        migrateToFive()
        assertStructureEqualsFresh(5)
    }

    @Test
    fun migratingStraightFromVersionOneToFiveKeepsEverything() {
        createVersion(1)
        insertVersionOneData()
        migrate()
        migrateToThree()
        migrateToFour()
        migrateToFive()
        assertEquals(listOf(2), query("SELECT COUNT(*) FROM goals") { it.getInt(1) })
        assertEquals(listOf(2), query("SELECT COUNT(*) FROM check_ins") { it.getInt(1) })
        assertStructureEqualsFresh(5)
    }

    @Test
    fun schemaFileForTheCurrentVersionMatchesTheCode() {
        assertEquals(AppDatabase.VERSION, schemaOf(AppDatabase.VERSION).getInt("version"))
        assertTrue(File(schemaDir, "1.json").exists())
    }

    @Test
    fun migrationKeepsEveryRowAndFillsTheNewColumnsWithDefaults() {
        createVersion(1)
        insertVersionOneData()
        migrate()

        val goals = query("SELECT id, name, type, frequencyType, activeDays, windowStart, windowEnd, startDate FROM goals ORDER BY id") {
            listOf(it.getLong(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5), it.getInt(6), it.getInt(7), it.getString(8))
        }
        assertEquals(
            listOf(
                listOf(1L, "Wake up early", "DO", "WEEKDAYS", 31, 360, 420, "2026-11-01"),
                listOf(2L, "No smoking", "AVOID", "DAILY", 127, 0, 0, "2026-11-01"),
            ),
            goals,
        )
        val checkIns = query("SELECT id, goalId, date, status, failedReason, completedAt, updatedAt FROM check_ins ORDER BY id") {
            listOf(it.getLong(1), it.getLong(2), it.getString(3), it.getString(4), it.getString(5), it.getLong(6), it.getLong(7))
        }
        assertEquals(
            listOf(
                listOf(1L, 1L, "2026-11-10", "DONE", null, 111L, 222L),
                listOf(2L, 2L, "2026-11-10", "FAILED", "MANUAL", 0L, 333L),
            ),
            checkIns,
        )
    }

    @Test
    fun existingGoalsGetTheDefaultRulesAndOnlyAvoidGoalsKeepTheManualButton() {
        createVersion(1)
        insertVersionOneData()
        migrate()
        val rules = query("SELECT id, failOnWindowEnd, failOnDayEnd, failOnManual, graceMinutes, measureType, unit, targetAmount, minimumAmount, minimumLabel, freqCount, monthlyNth, quotaFailureMode FROM goals ORDER BY id") {
            listOf(it.getLong(1), it.getInt(2), it.getInt(3), it.getInt(4), it.getInt(5), it.getString(6), it.getString(7), it.getInt(8), it.getInt(9), it.getString(10), it.getInt(11), it.getString(12), it.getString(13))
        }
        assertEquals(listOf(1L, 0, 0, 0, 5, "CHECK", "", 1, 1, null, 1, "FIRST", "WHEN_UNREACHABLE"), rules[0])
        assertEquals(listOf(2L, 0, 0, 1, 5, "CHECK", "", 1, 1, null, 1, "FIRST", "WHEN_UNREACHABLE"), rules[1])
        val extras = query("SELECT amount, lateMinutes FROM check_ins ORDER BY id") { listOf(it.getInt(1), it.getObject(2)) }
        assertEquals(listOf(listOf(0, null), listOf(0, null)), extras)
    }

    @Test
    fun newTablesStartEmptyAndAcceptRowsLinkedToGoals() {
        createVersion(1)
        insertVersionOneData()
        migrate()
        assertEquals(listOf(0), query("SELECT COUNT(*) FROM amount_logs") { it.getInt(1) })
        assertEquals(listOf(0), query("SELECT COUNT(*) FROM period_results") { it.getInt(1) })
        execute("INSERT INTO amount_logs (goalId, date, delta, loggedAt) VALUES (1, '2026-11-10', 25, 999)")
        execute(
            "INSERT INTO period_results (goalId, periodStart, periodEnd, originalRequired, adjustedRequired, eligibleDays, completed, status, failedAt, recalculatedAt) " +
                "VALUES (1, '2026-11-09', '2026-11-15', 4, 4, 7, 4, 'MET', NULL, 1)",
        )
        assertEquals(listOf(25), query("SELECT delta FROM amount_logs") { it.getInt(1) })
    }

    @Test
    fun migratedStructureEqualsAFreshVersionTwoDatabase() {
        createVersion(1)
        migrate()
        assertStructureEqualsFresh(2)
    }

    /** Columns (name, type, not null, default, key), indices and foreign keys, ignoring column order. */
    private fun structureOf(table: String): Map<String, Set<List<Any?>>> = mapOf(
        "columns" to query("PRAGMA table_info(`$table`)") { listOf(it.getString("name"), it.getString("type"), it.getInt("notnull"), it.getString("dflt_value"), it.getInt("pk")) }.toSet(),
        "indices" to query("PRAGMA index_list(`$table`)") { listOf(it.getString("name"), it.getInt("unique")) }.toSet(),
        "foreignKeys" to query("PRAGMA foreign_key_list(`$table`)") { listOf(it.getString("table"), it.getString("from"), it.getString("to"), it.getString("on_update"), it.getString("on_delete")) }.toSet(),
    )

    @Test
    fun runningTheMigrationOnADatabaseWithNoRowsWorks() {
        createVersion(1)
        migrate()
        assertEquals(listOf(0), query("SELECT COUNT(*) FROM goals") { it.getInt(1) })
    }
}
