package com.example.dailygoals.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ZoneChange {
    /**
     * Dates the device never lived through because the time zone moved forward across the date line.
     * They stay neutral: no check-ins, no failures and no broken streak. When the zone has not changed
     * (the app was only closed for a while) nothing is skipped.
     */
    fun skippedDates(lastEvaluatedToday: LocalDate?, at: Instant, oldZone: ZoneId, newZone: ZoneId): Set<LocalDate> {
        if (lastEvaluatedToday == null || oldZone == newZone) return emptySet()
        val expected = at.atZone(oldZone).toLocalDate()
        val actual = at.atZone(newZone).toLocalDate()
        if (actual <= expected) return emptySet()
        return generateSequence(expected) { it.plusDays(1) }
            .takeWhile { it < actual }
            .filter { it > lastEvaluatedToday }
            .toSet()
    }
}
