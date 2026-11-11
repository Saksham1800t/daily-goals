package com.example.dailygoals.engine

import java.time.LocalDateTime

fun interface Clock {
    fun now(): LocalDateTime
}

val LocalDateTime.minuteOfDay: Int
    get() = hour * 60 + minute
