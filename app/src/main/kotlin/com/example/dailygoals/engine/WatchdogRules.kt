package com.example.dailygoals.engine

enum class WatchdogAction { NOTIFY, CLEAR, NONE }

/**
 * A periodic check that tells the user when blocking has stopped working because the accessibility
 * service was switched off, which Android does when an app is force-stopped.
 */
object WatchdogRules {
    /** One reminder every few hours, so a switched-off service does not buzz on every check. */
    const val NOTIFY_COOLDOWN_MILLIS = 6 * 60 * 60 * 1000L

    fun decide(sessionActive: Boolean, serviceEnabled: Boolean, lastNotifiedMillis: Long, nowMillis: Long): WatchdogAction = when {
        !sessionActive || serviceEnabled -> WatchdogAction.CLEAR
        nowMillis - lastNotifiedMillis >= NOTIFY_COOLDOWN_MILLIS -> WatchdogAction.NOTIFY
        else -> WatchdogAction.NONE
    }
}
