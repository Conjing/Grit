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
package com.shub39.grit.core.data.notification

import android.content.Intent
import com.shub39.grit.core.settings.domain.Sections
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val EXTRA_REMINDER_PAYLOAD = "reminder_payload"
internal const val EXTRA_REMINDER_TARGET_SECTION = "reminder_target_section"
internal const val EXTRA_STOP_ACTIVE_REMINDER = "stop_active_reminder"

private const val HABIT_NOTIFICATION_OFFSET = 0
private const val TASK_NOTIFICATION_OFFSET = 1000
private const val HABIT_FOLLOW_UP_OFFSET = 100_000
private const val TASK_FOLLOW_UP_OFFSET = 200_000

@Serializable
enum class ReminderTargetType {
    HABIT,
    TASK,
}

@Serializable
data class ReminderPayload(
    val type: ReminderTargetType,
    val itemId: Long,
    val title: String,
    val description: String? = null,
    val section: Sections,
    val occurrenceDate: LocalDate,
    val originalTriggerAtMillis: Long,
    val unattendedCount: Int = 0,
)

@Serializable
internal data class ReminderSession(
    val payload: ReminderPayload,
    val ringing: Boolean,
    val followUpAtMillis: Long? = null,
)

internal fun ReminderPayload.notificationId(): Int =
    itemId.toInt() +
        when (type) {
            ReminderTargetType.HABIT -> HABIT_NOTIFICATION_OFFSET
            ReminderTargetType.TASK -> TASK_NOTIFICATION_OFFSET
        }

internal fun ReminderPayload.followUpRequestCode(): Int {
    val base = Integer.rotateLeft(itemId.toInt(), 5) xor originalTriggerAtMillis.hashCode()
    val offset =
        when (type) {
            ReminderTargetType.HABIT -> HABIT_FOLLOW_UP_OFFSET
            ReminderTargetType.TASK -> TASK_FOLLOW_UP_OFFSET
        }

    return offset + base.absoluteValue()
}

internal fun ReminderPayload.matches(other: ReminderPayload): Boolean =
    type == other.type &&
        itemId == other.itemId &&
        originalTriggerAtMillis == other.originalTriggerAtMillis &&
        occurrenceDate == other.occurrenceDate

internal fun ReminderPayload.shouldReplace(other: ReminderPayload): Boolean =
    when {
        originalTriggerAtMillis != other.originalTriggerAtMillis ->
            originalTriggerAtMillis > other.originalTriggerAtMillis
        type != other.type -> type == ReminderTargetType.HABIT
        else -> itemId > other.itemId
    }

internal fun Intent.putReminderPayload(payload: ReminderPayload): Intent =
    putExtra(EXTRA_REMINDER_PAYLOAD, Json.encodeToString(payload))

internal fun Intent.getReminderPayloadOrNull(): ReminderPayload? =
    getStringExtra(EXTRA_REMINDER_PAYLOAD)?.let { payloadJson ->
        runCatching { Json.decodeFromString<ReminderPayload>(payloadJson) }.getOrNull()
    }

private fun Int.absoluteValue(): Int = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)
