package com.example.dailygoals.engine

import java.time.Instant
import java.time.ZoneId

enum class PauseLength { THIRTY_MINUTES, ONE_HOUR, END_OF_TODAY }

data class PauseUse(val startedAtMillis: Long, val endsAtMillis: Long)

/**
 * The emergency pause lifts every block for a while. It is deliberately a little hard to start (hold a
 * button, or type a word) and limited to two uses per local day, so it cannot become the normal way out.
 */
object EmergencyPause {
    const val HOLD_MILLIS = 5_000L
    const val CONFIRM_WORD = "PAUSE"
    const val MAX_USES_PER_DAY = 2

    fun isConfirmedByWord(typed: String): Boolean = typed.trim().equals(CONFIRM_WORD, ignoreCase = true)

    fun isHoldLongEnough(heldMillis: Long): Boolean = heldMillis >= HOLD_MILLIS

    fun usesToday(uses: List<PauseUse>, nowMillis: Long, zone: ZoneId): Int {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return uses.count { Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() == today }
    }

    fun usesLeftToday(uses: List<PauseUse>, nowMillis: Long, zone: ZoneId): Int =
        (MAX_USES_PER_DAY - usesToday(uses, nowMillis, zone)).coerceAtLeast(0)

    fun canStart(uses: List<PauseUse>, nowMillis: Long, zone: ZoneId): Boolean =
        !isActive(uses, nowMillis) && usesLeftToday(uses, nowMillis, zone) > 0

    fun endsAt(length: PauseLength, startMillis: Long, zone: ZoneId): Long = when (length) {
        PauseLength.THIRTY_MINUTES -> startMillis + 30 * 60_000L
        PauseLength.ONE_HOUR -> startMillis + 60 * 60_000L
        PauseLength.END_OF_TODAY ->
            Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun start(uses: List<PauseUse>, length: PauseLength, nowMillis: Long, zone: ZoneId): List<PauseUse> {
        require(canStart(uses, nowMillis, zone)) { "emergency pause cannot start now" }
        return uses + PauseUse(nowMillis, endsAt(length, nowMillis, zone))
    }

    fun active(uses: List<PauseUse>, nowMillis: Long): PauseUse? =
        uses.firstOrNull { nowMillis >= it.startedAtMillis && nowMillis < it.endsAtMillis }

    fun isActive(uses: List<PauseUse>, nowMillis: Long): Boolean = active(uses, nowMillis) != null

    fun millisLeft(uses: List<PauseUse>, nowMillis: Long): Long = active(uses, nowMillis)?.let { it.endsAtMillis - nowMillis } ?: 0L

    /** Keeps only recent uses so the stored list does not grow for ever; the review reads a few weeks. */
    fun trimmed(uses: List<PauseUse>, nowMillis: Long, keepDays: Int = 60): List<PauseUse> =
        uses.filter { it.endsAtMillis >= nowMillis - keepDays * 86_400_000L }
}
