package com.example.dailygoals.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object LocalTimes {
    /**
     * A local time inside a daylight-saving gap moves to the first valid moment after the gap, and a
     * local time that happens twice uses the earlier occurrence (the standard java.time rule).
     * [minute] may be 1440 for the end of the day.
     */
    fun resolve(date: LocalDate, minute: Int, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(date, LocalTime.MIDNIGHT).plusMinutes(minute.toLong()), zone)
}
