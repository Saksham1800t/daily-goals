package com.example.dailygoals.engine

import java.time.Instant
import java.time.ZoneId

data class TempAccessSettings(
    val enabled: Boolean = true,
    val maxUsesPerDay: Int = 2,
    val maxMinutes: Int = 10,
    val waitSeconds: Int = 60,
) {
    fun clamped(): TempAccessSettings = copy(
        maxUsesPerDay = maxUsesPerDay.coerceIn(USES_RANGE),
        maxMinutes = maxMinutes.coerceIn(MINUTES_RANGE),
        waitSeconds = waitSeconds.coerceIn(WAIT_RANGE),
    )

    companion object {
        val USES_RANGE = 0..10
        val MINUTES_RANGE = 1..60
        val WAIT_RANGE = 15..300
    }
}

data class TempAccessUse(val packageName: String, val startedAtMillis: Long, val endsAtMillis: Long)

enum class TempAccessOffer { AVAILABLE, OFF, NONE_LEFT, GOAL_FORBIDS }

enum class TempAccessError { WAIT_NOT_OVER, REASON_MISSING, MINUTES_OUT_OF_RANGE }

/**
 * Temporary access unlocks one blocked app for a few minutes, with friction: a wait, a typed reason and a
 * daily limit that counts across all apps. It never cancels a block, a failure or a punishment.
 */
object TempAccess {
    private val MINUTE_STEPS = listOf(1, 2, 5, 10, 15, 20, 30, 45, 60)

    fun isOn(settings: TempAccessSettings): Boolean = settings.enabled && settings.maxUsesPerDay > 0

    fun usesToday(uses: List<TempAccessUse>, nowMillis: Long, zone: ZoneId): Int {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return uses.count { Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() == today }
    }

    fun usesLeft(settings: TempAccessSettings, uses: List<TempAccessUse>, nowMillis: Long, zone: ZoneId): Int =
        if (isOn(settings)) (settings.maxUsesPerDay - usesToday(uses, nowMillis, zone)).coerceAtLeast(0) else 0

    /** [goalsAllow] has one entry per goal blocking the app; a single goal that forbids it is enough to refuse. */
    fun offer(settings: TempAccessSettings, uses: List<TempAccessUse>, goalsAllow: List<Boolean>, nowMillis: Long, zone: ZoneId): TempAccessOffer = when {
        !isOn(settings) -> TempAccessOffer.OFF
        goalsAllow.any { !it } -> TempAccessOffer.GOAL_FORBIDS
        usesLeft(settings, uses, nowMillis, zone) == 0 -> TempAccessOffer.NONE_LEFT
        else -> TempAccessOffer.AVAILABLE
    }

    fun minuteChoices(settings: TempAccessSettings): List<Int> =
        (MINUTE_STEPS.filter { it < settings.maxMinutes } + settings.maxMinutes).let { all ->
            if (all.size <= MAX_CHOICES) all else List(MAX_CHOICES) { all[it * (all.size - 1) / (MAX_CHOICES - 1)] }
        }

    fun errors(settings: TempAccessSettings, waitedSeconds: Int, reason: String, minutes: Int): Set<TempAccessError> = buildSet {
        if (waitedSeconds < settings.waitSeconds) add(TempAccessError.WAIT_NOT_OVER)
        if (reason.isBlank()) add(TempAccessError.REASON_MISSING)
        if (minutes !in 1..settings.maxMinutes) add(TempAccessError.MINUTES_OUT_OF_RANGE)
    }

    fun start(uses: List<TempAccessUse>, packageName: String, minutes: Int, nowMillis: Long): List<TempAccessUse> =
        uses + TempAccessUse(packageName, nowMillis, nowMillis + minutes * MILLIS_PER_MINUTE)

    fun active(uses: List<TempAccessUse>, packageName: String, nowMillis: Long): TempAccessUse? =
        uses.firstOrNull { it.packageName == packageName && nowMillis >= it.startedAtMillis && nowMillis < it.endsAtMillis }

    fun unlockedPackages(uses: List<TempAccessUse>, nowMillis: Long): Set<String> =
        uses.filter { nowMillis >= it.startedAtMillis && nowMillis < it.endsAtMillis }.map { it.packageName }.toSet()

    /** Locking again early ends the access now; the use still counts for today. */
    fun endEarly(uses: List<TempAccessUse>, packageName: String, nowMillis: Long): List<TempAccessUse> =
        uses.map { if (it == active(uses, packageName, nowMillis)) it.copy(endsAtMillis = nowMillis) else it }

    fun trimmed(uses: List<TempAccessUse>, nowMillis: Long, keepDays: Int = 60): List<TempAccessUse> =
        uses.filter { it.endsAtMillis >= nowMillis - keepDays * MILLIS_PER_DAY }

    /** True when [new] makes temporary access easier than [old] in any way. */
    fun loosens(old: TempAccessSettings, new: TempAccessSettings): Boolean =
        (new.enabled && !old.enabled) || new.maxUsesPerDay > old.maxUsesPerDay || new.maxMinutes > old.maxMinutes || new.waitSeconds < old.waitSeconds

    private const val MAX_CHOICES = 4
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_DAY = 86_400_000L
}
