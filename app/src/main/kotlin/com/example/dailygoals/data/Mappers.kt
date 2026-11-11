package com.example.dailygoals.data

import com.example.dailygoals.model.CheckIn
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.TimeWindow
import java.time.DayOfWeek
import java.time.LocalDate

fun Set<DayOfWeek>.toDayMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

fun Int.toDaySet(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

fun GoalEntity.toModel(): Goal = Goal(
    id = id,
    name = name,
    type = type,
    state = state,
    sortOrder = sortOrder,
    schedule = GoalSchedule(
        frequency = schedule.frequencyType,
        activeDays = schedule.activeDays.toDaySet(),
        window = if (schedule.windowStart != null && schedule.windowEnd != null) {
            TimeWindow(schedule.windowStart, schedule.windowEnd)
        } else {
            null
        },
        startDate = LocalDate.parse(schedule.startDate),
    ),
)

fun Goal.toEntity(): GoalEntity = GoalEntity(
    id = id,
    name = name,
    type = type,
    state = state,
    sortOrder = sortOrder,
    schedule = ScheduleColumns(
        frequencyType = schedule.frequency,
        activeDays = schedule.activeDays.toDayMask(),
        windowStart = schedule.window?.startMinute,
        windowEnd = schedule.window?.endMinute,
        startDate = schedule.startDate.toString(),
    ),
)

fun CheckInEntity.toModel(): CheckIn = CheckIn(
    goalId = goalId,
    date = LocalDate.parse(date),
    status = status,
    failReason = failedReason,
)
