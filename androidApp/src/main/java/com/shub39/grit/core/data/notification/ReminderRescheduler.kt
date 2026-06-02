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

import android.content.Context
import android.util.Log
import com.shub39.grit.core.now
import com.shub39.grit.core.habits.domain.HabitRepo
import com.shub39.grit.core.tasks.domain.TaskRepo
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single

@Single
class ReminderRescheduler(
    private val context: Context,
    private val scheduler: NotificationAlarmScheduler,
    private val runtimeStore: ReminderRuntimeStore,
    private val habitRepo: HabitRepo,
    private val taskRepo: TaskRepo,
) {
    companion object {
        private const val TAG = "ReminderRescheduler"
    }

    suspend fun rescheduleAll(reason: String) {
        GritNotificationManager.createNotificationChannel(context)

        habitRepo.getHabits().forEach { scheduler.schedule(it) }
        taskRepo.getTasks().forEach { scheduler.schedule(it) }

        restoreTrackedSession()
        Log.d(TAG, "Rescheduled reminders due to $reason")
    }

    private fun restoreTrackedSession() {
        val tracked = runtimeStore.getTrackedSession() ?: return
        val today = LocalDate.now()

        when {
            tracked.payload.occurrenceDate < today -> {
                scheduler.cancelFollowUp(tracked.payload)
                runtimeStore.clearTrackedSession()
            }

            tracked.followUpAtMillis != null -> {
                val triggerAt =
                    maxOf(
                        tracked.followUpAtMillis,
                        Clock.System.now().toEpochMilliseconds() + 1_000L,
                    )
                scheduler.scheduleFollowUp(tracked.payload, triggerAt)
                runtimeStore.setTrackedSession(
                    tracked.copy(ringing = false, followUpAtMillis = triggerAt)
                )
            }

            else -> {
                // Active alarm sessions cannot be reconstructed safely across process restarts,
                // so we clear stale in-memory state instead of blocking future reminders.
                runtimeStore.clearTrackedSession()
            }
        }
    }
}
