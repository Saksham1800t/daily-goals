package com.example.dailygoals.engine

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyPauseTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int = 0, day: Int = 13) =
        LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun theWordCanBeTypedInAnyCaseWithSpaces() {
        assertTrue(EmergencyPause.isConfirmedByWord("PAUSE"))
        assertTrue(EmergencyPause.isConfirmedByWord("  pause "))
        assertFalse(EmergencyPause.isConfirmedByWord("pausee"))
        assertFalse(EmergencyPause.isConfirmedByWord(""))
    }

    @Test
    fun aHoldMustLastFiveSeconds() {
        assertFalse(EmergencyPause.isHoldLongEnough(4_999))
        assertTrue(EmergencyPause.isHoldLongEnough(5_000))
    }

    @Test
    fun lengthsAreThirtyMinutesOneHourAndUntilMidnight() {
        val start = at(21, 15)
        assertEquals(at(21, 45), EmergencyPause.endsAt(PauseLength.THIRTY_MINUTES, start, zone))
        assertEquals(at(22, 15), EmergencyPause.endsAt(PauseLength.ONE_HOUR, start, zone))
        assertEquals(at(0, 0, day = 14), EmergencyPause.endsAt(PauseLength.END_OF_TODAY, start, zone))
    }

    @Test
    fun aPauseIsActiveOnlyBetweenItsStartAndEnd() {
        val uses = EmergencyPause.start(emptyList(), PauseLength.ONE_HOUR, at(10), zone)
        assertFalse(EmergencyPause.isActive(uses, at(9, 59)))
        assertTrue(EmergencyPause.isActive(uses, at(10)))
        assertTrue(EmergencyPause.isActive(uses, at(10, 59)))
        assertFalse(EmergencyPause.isActive(uses, at(11)))
        assertEquals(30 * 60_000L, EmergencyPause.millisLeft(uses, at(10, 30)))
        assertEquals(0L, EmergencyPause.millisLeft(uses, at(12)))
    }

    @Test
    fun onlyTwoUsesAreAllowedPerLocalDay() {
        var uses = EmergencyPause.start(emptyList(), PauseLength.THIRTY_MINUTES, at(8), zone)
        assertEquals(1, EmergencyPause.usesLeftToday(uses, at(9), zone))
        uses = EmergencyPause.start(uses, PauseLength.THIRTY_MINUTES, at(9), zone)
        assertEquals(0, EmergencyPause.usesLeftToday(uses, at(12), zone))
        assertFalse(EmergencyPause.canStart(uses, at(12), zone))
        assertThrows(IllegalArgumentException::class.java) { EmergencyPause.start(uses, PauseLength.ONE_HOUR, at(12), zone) }
    }

    @Test
    fun theLimitResetsOnTheNextLocalDate() {
        val uses = EmergencyPause.start(emptyList(), PauseLength.THIRTY_MINUTES, at(8), zone)
            .let { EmergencyPause.start(it, PauseLength.THIRTY_MINUTES, at(9), zone) }
        assertTrue(EmergencyPause.canStart(uses, at(0, 5, day = 14), zone))
    }

    @Test
    fun aSecondPauseCannotStartWhileOneIsRunning() {
        val uses = EmergencyPause.start(emptyList(), PauseLength.ONE_HOUR, at(8), zone)
        assertFalse(EmergencyPause.canStart(uses, at(8, 30), zone))
        assertTrue(EmergencyPause.canStart(uses, at(9, 5), zone))
    }

    @Test
    fun oldUsesAreDroppedFromTheStoredList() {
        val old = PauseUse(at(8, day = 1), at(9, day = 1))
        val recent = PauseUse(at(8, day = 12), at(9, day = 12))
        assertEquals(listOf(recent), EmergencyPause.trimmed(listOf(old, recent), at(10), keepDays = 7))
    }
}
