package com.example.dailygoals.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeZoneTest {
    private val newYork = ZoneId.of("America/New_York")
    private val samoa = ZoneId.of("Pacific/Pago_Pago")
    private val auckland = ZoneId.of("Pacific/Auckland")
    private val landing = Instant.parse("2026-11-11T12:00:00Z")

    @Test
    fun aTimeInsideTheSpringGapMovesToTheFirstValidMoment() {
        val resolved = LocalTimes.resolve(LocalDate.of(2026, 3, 8), 2 * 60 + 30, newYork)
        assertEquals(3, resolved.hour)
        assertEquals(30, resolved.minute)
        assertEquals(ZoneOffset.ofHours(-4), resolved.offset)
    }

    @Test
    fun aTimeThatHappensTwiceUsesTheEarlierOccurrence() {
        val resolved = LocalTimes.resolve(LocalDate.of(2026, 11, 1), 60 + 30, newYork)
        assertEquals(1, resolved.hour)
        assertEquals(ZoneOffset.ofHours(-4), resolved.offset)
    }

    @Test
    fun theEndOfTheDayResolvesToMidnightOfTheNextDate() {
        val resolved = LocalTimes.resolve(LocalDate.of(2026, 12, 31), 24 * 60, ZoneOffset.UTC)
        assertEquals(LocalDate.of(2027, 1, 1), resolved.toLocalDate())
        assertEquals(0, resolved.hour)
    }

    @Test
    fun crossingTheDateLineForwardSkipsADateWithoutFailingIt() {
        val skipped = ZoneChange.skippedDates(LocalDate.of(2026, 11, 10), landing, samoa, auckland)
        assertEquals(setOf(LocalDate.of(2026, 11, 11)), skipped)
    }

    @Test
    fun crossingTheDateLineBackwardSkipsNothing() {
        assertEquals(emptySet<LocalDate>(), ZoneChange.skippedDates(LocalDate.of(2026, 11, 12), landing, auckland, samoa))
    }

    @Test
    fun aSameZoneGapOrAFirstRunSkipsNothing() {
        assertEquals(emptySet<LocalDate>(), ZoneChange.skippedDates(LocalDate.of(2026, 11, 1), landing, auckland, auckland))
        assertEquals(emptySet<LocalDate>(), ZoneChange.skippedDates(null, landing, samoa, auckland))
    }

    @Test
    fun datesAlreadyEvaluatedAreNeverSkippedRetroactively() {
        assertEquals(emptySet<LocalDate>(), ZoneChange.skippedDates(LocalDate.of(2026, 11, 11), landing, samoa, auckland))
    }
}
