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
package com.shub39.grit.habits.data.repository

import com.shub39.grit.core.habits.domain.Habit
import com.shub39.grit.core.habits.domain.HabitStatus
import com.shub39.grit.core.habits.domain.WeekDayFrequencyData
import com.shub39.grit.core.habits.domain.WeeklyComparisonData
import com.shub39.grit.core.habits.domain.dueDatesBetween
import com.shub39.grit.core.habits.domain.isDueOn
import com.shub39.grit.core.now
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

fun countCurrentStreak(
    dates: List<LocalDate>,
    habit: Habit,
): Int {
    if (dates.isEmpty()) return 0

    val today = LocalDate.now()
    val completedDates = dates.filter { habit.isDueOn(it) }.toSet()
    val firstDate = dates.minOrNull() ?: return 0
    val dueDates = habit.dueDatesBetween(startInclusive = firstDate, endInclusive = today)
    if (dueDates.isEmpty() || dueDates.last() !in completedDates) return 0

    var streak = 0
    for (dueDate in dueDates.asReversed()) {
        if (dueDate in completedDates) streak++ else break
    }

    return streak
}

fun countBestStreak(
    dates: List<LocalDate>,
    habit: Habit,
): Int {
    if (dates.isEmpty()) return 0

    val completedDates = dates.filter { habit.isDueOn(it) }.toSet()
    val firstDate = dates.minOrNull() ?: return 0
    val lastDate = dates.maxOrNull() ?: return 0
    val dueDates = habit.dueDatesBetween(startInclusive = firstDate, endInclusive = lastDate)
    if (dueDates.isEmpty()) return 0

    var maxConsecutive = 0
    var currentConsecutive = 0

    dueDates.forEach { dueDate ->
        if (dueDate in completedDates) {
            currentConsecutive++
        } else {
            maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
            currentConsecutive = 0
        }
    }

    return maxOf(maxConsecutive, currentConsecutive)
}

fun prepareLineChartData(
    firstDay: DayOfWeek,
    habitStatuses: List<HabitStatus>,
): WeeklyComparisonData {
    val today = LocalDate.now()
    val totalWeeks = 52

    // Find the start date of the 15-week period
    val startDateOfTodayWeek =
        today.minus(today.dayOfWeek.isoDayNumber - firstDay.isoDayNumber, DateTimeUnit.DAY)
    val startDateOfPeriod = startDateOfTodayWeek.minus(totalWeeks, DateTimeUnit.WEEK)

    val habitCompletionByWeek =
        habitStatuses
            .filter { it.date in startDateOfPeriod..today }
            .groupBy {
                // Calculate the start date of the week for the given habit date
                val daysFromFirstDay =
                    (it.date.dayOfWeek.isoDayNumber - firstDay.isoDayNumber + 7) % 7
                it.date.minus(daysFromFirstDay, DateTimeUnit.DAY)
            }
            .mapValues { (_, habitStatuses) -> habitStatuses.size }

    val values =
        (0..totalWeeks).map { i ->
            // The start of the current week in the loop, starting from 15 weeks ago
            val currentWeekStart = startDateOfPeriod.plus(i, DateTimeUnit.WEEK)

            // The key for the map is the LocalDate representing the start of the week
            val weekKey = currentWeekStart

            (habitCompletionByWeek[weekKey]?.toDouble() ?: 0.0).coerceIn(0.0, 7.0)
        }
    return values
}

fun prepareWeekDayFrequencyData(dates: List<LocalDate>): WeekDayFrequencyData {
    val dayFrequency = dates.groupingBy { it.dayOfWeek }.eachCount()

    return DayOfWeek.entries.associate { dayOfWeek ->
        val weekName = DayOfWeekNames.ENGLISH_ABBREVIATED.names[dayOfWeek.isoDayNumber - 1]

        weekName to (dayFrequency[dayOfWeek] ?: 0)
    }
}

fun prepareHeatMapData(habitData: List<HabitStatus>): Map<LocalDate, Int> {
    val allDates = habitData.map { it.date }
    val dateFrequency = allDates.groupingBy { it }.eachCount()

    return dateFrequency
}

fun calculateConsistency(
    dates: List<LocalDate>,
    habit: Habit,
): Float {
    val completedDueDates = dates.filter { habit.isDueOn(it) }.distinct().sorted()
    val firstCompletionDate = completedDueDates.firstOrNull() ?: return 0f
    val today = LocalDate.now()
    val totalEligibleDays =
        habit.dueDatesBetween(startInclusive = firstCompletionDate, endInclusive = today).size

    return if (totalEligibleDays > 0) completedDueDates.size.toFloat() / totalEligibleDays else 0f
}
