package com.example.dailygoals.data.backup

import androidx.room.withTransaction
import com.example.dailygoals.data.AppDatabase
import com.example.dailygoals.data.SettingsStore

class BackupService(private val database: AppDatabase, private val settings: SettingsStore) {
    suspend fun snapshot(): Backup = Backup(
        schemaVersion = AppDatabase.VERSION,
        goals = database.goalDao().all(),
        checkIns = database.checkInDao().all(),
        amountLogs = database.amountLogDao().all(),
        periodResults = database.periodResultDao().all(),
        dayEndMinute = settings.settings.value.dayEndMinute,
        minimumWeightPercent = settings.settings.value.minimumWeightPercent,
    )

    suspend fun exportJson(): String = BackupCodec.encode(snapshot())

    suspend fun exportCsv(): String {
        val snapshot = snapshot()
        return BackupCodec.csv(snapshot.goals, snapshot.checkIns)
    }

    /**
     * REPLACE swaps everything for the backup. MERGE keeps what is already here: goals and amount logs
     * are matched by id, and for a day with a result on both sides the later update wins, so a day is
     * never stored twice.
     */
    suspend fun restore(backup: Backup, mode: RestoreMode) {
        database.withTransaction {
            val goals = database.goalDao()
            val checkIns = database.checkInDao()
            val logs = database.amountLogDao()
            val periods = database.periodResultDao()
            if (mode == RestoreMode.REPLACE) {
                database.pendingChangeDao().deleteAll()
                database.blockAttemptDao().deleteAll()
                database.tempAccessDao().deleteAll()
                periods.deleteAll()
                logs.deleteAll()
                checkIns.deleteAll()
                goals.deleteAll()
                backup.goals.forEach { goals.upsert(it) }
                backup.checkIns.forEach { checkIns.save(it) }
                backup.amountLogs.forEach { logs.insertIfAbsent(it) }
                backup.periodResults.forEach { periods.upsert(it) }
            } else {
                backup.goals.forEach { goals.insertIfAbsent(it) }
                backup.checkIns.forEach { incoming ->
                    val existing = checkIns.find(incoming.goalId, incoming.date)
                    if (existing == null) {
                        checkIns.save(incoming.copy(id = 0))
                    } else if (incoming.updatedAt > existing.updatedAt) {
                        checkIns.save(incoming.copy(id = existing.id))
                    }
                }
                backup.amountLogs.forEach { logs.insertIfAbsent(it) }
                backup.periodResults.forEach { periods.upsert(it.copy(id = 0)) }
            }
        }
        settings.update { current ->
            if (mode == RestoreMode.REPLACE) {
                current.copy(
                    dayEndMinute = backup.dayEndMinute ?: current.dayEndMinute,
                    minimumWeightPercent = backup.minimumWeightPercent ?: current.minimumWeightPercent,
                )
            } else {
                current
            }
        }
    }
}
