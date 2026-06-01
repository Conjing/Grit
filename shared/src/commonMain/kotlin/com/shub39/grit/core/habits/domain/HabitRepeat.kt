/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.shub39.grit.core.habits.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
enum class HabitRepeatMode {
    WEEKLY,
    CUSTOM,
}

@Serializable
enum class HabitIntervalUnit {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

fun Habit.hasValidSchedule(): Boolean {
    return when (repeatMode) {
        HabitRepeatMode.WEEKLY -> days.isNotEmpty()
        HabitRepeatMode.CUSTOM -> intervalValue > 0
    }
}

fun Habit.isDueOn(date: LocalDate): Boolean {
    if (!hasValidSchedule()) return false

    return when (repeatMode) {
        HabitRepeatMode.WEEKLY -> date.dayOfWeek in days
        HabitRepeatMode.CUSTOM -> {
            val anchorDate = time.date
            if (date < anchorDate) return false

            when (intervalUnit) {
                HabitIntervalUnit.DAY -> anchorDate.daysUntil(date) % intervalValue == 0
                HabitIntervalUnit.WEEK -> anchorDate.daysUntil(date) % (intervalValue * 7) == 0
                HabitIntervalUnit.MONTH,
                HabitIntervalUnit.YEAR -> {
                    var occurrence = anchorDate
                    while (occurrence < date) {
                        occurrence = nextCustomOccurrence(occurrence)
                    }
                    occurrence == date
                }
            }
        }
    }
}

fun Habit.nextDueDateOnOrAfter(date: LocalDate): LocalDate? {
    if (!hasValidSchedule()) return null

    return when (repeatMode) {
        HabitRepeatMode.WEEKLY -> {
            var current = date
            while (!isDueOn(current)) {
                current = current.plus(1, DateTimeUnit.DAY)
            }
            current
        }

        HabitRepeatMode.CUSTOM -> {
            val anchorDate = time.date
            var occurrence = if (date <= anchorDate) anchorDate else anchorDate
            while (occurrence < date) {
                occurrence = nextCustomOccurrence(occurrence)
            }
            occurrence
        }
    }
}

fun Habit.dueDatesBetween(
    startInclusive: LocalDate,
    endInclusive: LocalDate,
): List<LocalDate> {
    if (endInclusive < startInclusive || !hasValidSchedule()) return emptyList()

    return when (repeatMode) {
        HabitRepeatMode.WEEKLY -> {
            buildList {
                var current = startInclusive
                while (current <= endInclusive) {
                    if (isDueOn(current)) add(current)
                    current = current.plus(1, DateTimeUnit.DAY)
                }
            }
        }

        HabitRepeatMode.CUSTOM -> {
            val firstDueDate = nextDueDateOnOrAfter(startInclusive) ?: return emptyList()

            buildList {
                var current = firstDueDate
                while (current <= endInclusive) {
                    add(current)
                    current = nextCustomOccurrence(current)
                }
            }
        }
    }
}

private fun Habit.nextCustomOccurrence(date: LocalDate): LocalDate {
    return when (intervalUnit) {
        HabitIntervalUnit.DAY -> date.plus(intervalValue, DateTimeUnit.DAY)
        HabitIntervalUnit.WEEK -> date.plus(intervalValue, DateTimeUnit.WEEK)
        HabitIntervalUnit.MONTH -> date.plus(intervalValue, DateTimeUnit.MONTH)
        HabitIntervalUnit.YEAR -> date.plus(intervalValue, DateTimeUnit.YEAR)
    }
}
