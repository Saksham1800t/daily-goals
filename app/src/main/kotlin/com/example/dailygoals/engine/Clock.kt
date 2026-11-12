package com.example.dailygoals.engine

import java.time.LocalDateTime
import java.time.ZoneId

/** All rules read time from here, so tests and the debug time shift can move it freely. */
interface Clock {
    fun now(): LocalDateTime

    val zone: ZoneId get() = ZoneId.systemDefault()
}

val LocalDateTime.minuteOfDay: Int
    get() = hour * 60 + minute
