package com.example.dailygoals.data

/**
 * Migration statements as plain SQL, so the JVM migration test can run exactly what the app runs.
 * Existing rows are kept; new columns get the defaults that match the Room entities.
 */
object MigrationSql {
    val V2_TO_V3: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `warningMode` TEXT NOT NULL DEFAULT 'NONE'",
        "ALTER TABLE `goals` ADD COLUMN `warningIntervalMin` INTEGER NOT NULL DEFAULT 10",
        "ALTER TABLE `goals` ADD COLUMN `warningMinute` INTEGER NOT NULL DEFAULT 1200",
        "ALTER TABLE `goals` ADD COLUMN `strongAlert` INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS `warning_marks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`goalId` INTEGER NOT NULL, `date` TEXT NOT NULL, `lastIndex` INTEGER NOT NULL, `level` TEXT NOT NULL, " +
            "`firedAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_warning_marks_goalId_date` ON `warning_marks` (`goalId`, `date`)",
    )

    val V1_TO_V2: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `freqCount` INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE `goals` ADD COLUMN `freqIntervalDays` INTEGER NOT NULL DEFAULT 2",
        "ALTER TABLE `goals` ADD COLUMN `monthlyNth` TEXT NOT NULL DEFAULT 'FIRST'",
        "ALTER TABLE `goals` ADD COLUMN `monthlyWeekday` INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE `goals` ADD COLUMN `quotaFailureMode` TEXT NOT NULL DEFAULT 'WHEN_UNREACHABLE'",
        "ALTER TABLE `goals` ADD COLUMN `graceMinutes` INTEGER NOT NULL DEFAULT 5",
        "ALTER TABLE `goals` ADD COLUMN `failOnWindowEnd` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `goals` ADD COLUMN `failOnDayEnd` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `goals` ADD COLUMN `failOnManual` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `goals` ADD COLUMN `measureType` TEXT NOT NULL DEFAULT 'CHECK'",
        "ALTER TABLE `goals` ADD COLUMN `unit` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `goals` ADD COLUMN `targetAmount` INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE `goals` ADD COLUMN `minimumAmount` INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE `goals` ADD COLUMN `minimumLabel` TEXT",
        "UPDATE `goals` SET `failOnManual` = 1 WHERE `type` = 'AVOID'",
        "ALTER TABLE `check_ins` ADD COLUMN `amount` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `check_ins` ADD COLUMN `lateMinutes` INTEGER",
        "CREATE TABLE IF NOT EXISTS `amount_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`goalId` INTEGER NOT NULL, `date` TEXT NOT NULL, `delta` INTEGER NOT NULL, `loggedAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        "CREATE INDEX IF NOT EXISTS `index_amount_logs_goalId_date` ON `amount_logs` (`goalId`, `date`)",
        "CREATE TABLE IF NOT EXISTS `period_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`goalId` INTEGER NOT NULL, `periodStart` TEXT NOT NULL, `periodEnd` TEXT NOT NULL, " +
            "`originalRequired` INTEGER NOT NULL, `adjustedRequired` INTEGER NOT NULL, `eligibleDays` INTEGER NOT NULL, " +
            "`pausedDays` INTEGER NOT NULL DEFAULT 0, `rescuedDays` INTEGER NOT NULL DEFAULT 0, " +
            "`completed` INTEGER NOT NULL, `status` TEXT NOT NULL, `failedAt` TEXT, `recalculatedAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_period_results_goalId_periodStart` ON `period_results` (`goalId`, `periodStart`)",
    )
}
