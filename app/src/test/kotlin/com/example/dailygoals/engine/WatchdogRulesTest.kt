package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchdogRulesTest {
    private val hour = 60 * 60 * 1000L

    @Test
    fun aSwitchedOffServiceDuringATestSendsOneReminder() {
        assertEquals(WatchdogAction.NOTIFY, WatchdogRules.decide(sessionActive = true, serviceEnabled = false, lastNotifiedMillis = 0, nowMillis = 100 * hour))
    }

    @Test
    fun theReminderWaitsForTheCooldownBeforeComingAgain() {
        val last = 100 * hour
        assertEquals(WatchdogAction.NONE, WatchdogRules.decide(true, false, last, last + 5 * hour))
        assertEquals(WatchdogAction.NOTIFY, WatchdogRules.decide(true, false, last, last + 6 * hour))
    }

    @Test
    fun aHealthyServiceOrNoTestClearsAnyReminder() {
        assertEquals(WatchdogAction.CLEAR, WatchdogRules.decide(sessionActive = true, serviceEnabled = true, lastNotifiedMillis = 0, nowMillis = hour))
        assertEquals(WatchdogAction.CLEAR, WatchdogRules.decide(sessionActive = false, serviceEnabled = false, lastNotifiedMillis = 0, nowMillis = hour))
    }
}
