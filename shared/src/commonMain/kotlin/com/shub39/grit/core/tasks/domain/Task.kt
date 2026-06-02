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
package com.shub39.grit.core.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
enum class TaskTimeMode {
    DURATION,
    END_TIME,
}

/**
 * Model for Tasks
 *
 * @param categoryId : Id of the category the task belongs to
 * @param title : the content of the task
 * @param status : task completion status
 * @param index : used for sorting in UI
 * @param reminder : task start date/time, also used as the reminder trigger
 */
@Serializable
data class Task(
    val id: Long = 0,
    val categoryId: Long,
    val title: String,
    val index: Int = 0,
    val status: Boolean = false,
    val reminder: LocalDateTime? = null,
    val timeMode: TaskTimeMode = TaskTimeMode.DURATION,
    val durationMinutes: Int? = null,
    val endAt: LocalDateTime? = null,
)

fun Task.resolvedEndAt(): LocalDateTime? =
    when (timeMode) {
        TaskTimeMode.DURATION ->
            if (reminder != null && durationMinutes != null && durationMinutes > 0) {
                reminder
                    .toInstant(TimeZone.currentSystemDefault())
                    .plus(durationMinutes, DateTimeUnit.MINUTE)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
            } else {
                null
            }

        TaskTimeMode.END_TIME ->
            endAt?.takeIf { candidateEnd ->
                reminder != null && candidateEnd > reminder
            }
    }

fun Task.hasExplicitSpan(): Boolean = resolvedEndAt() != null
