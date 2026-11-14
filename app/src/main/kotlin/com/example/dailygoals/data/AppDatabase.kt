package com.example.dailygoals.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        GoalEntity::class, CheckInEntity::class, AmountLogEntity::class, PeriodResultEntity::class, WarningMarkEntity::class,
        BlockAttemptEntity::class, TempAccessEntity::class, PendingChangeEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun checkInDao(): CheckInDao
    abstract fun amountLogDao(): AmountLogDao
    abstract fun periodResultDao(): PeriodResultDao
    abstract fun warningMarkDao(): WarningMarkDao
    abstract fun blockAttemptDao(): BlockAttemptDao
    abstract fun tempAccessDao(): TempAccessDao
    abstract fun pendingChangeDao(): PendingChangeDao

    companion object {
        const val VERSION = 5
        private const val FILE_NAME = "daily_goals.db"

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V4_TO_V5.forEach(db::execSQL)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V3_TO_V4.forEach(db::execSQL)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V2_TO_V3.forEach(db::execSQL)
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V1_TO_V2.forEach(db::execSQL)
            }
        }

        /** There is no destructive fallback: every version step is a written migration. */
        fun create(context: Context): AppDatabase {
            backUpBeforeMigration(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }

        /** Copies an older database file aside before Room upgrades it, so a bad migration can be undone by hand. */
        private fun backUpBeforeMigration(context: Context) {
            val file = context.getDatabasePath(FILE_NAME)
            if (!file.exists()) return
            val version = runCatching {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            }.getOrDefault(VERSION)
            if (version >= VERSION) return
            val copy = File(file.parentFile, "$FILE_NAME.v$version.bak")
            if (!copy.exists()) file.copyTo(copy)
        }
    }
}
