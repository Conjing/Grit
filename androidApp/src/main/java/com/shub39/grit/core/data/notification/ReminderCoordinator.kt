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
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shub39.grit.core.habits.domain.Habit
import com.shub39.grit.core.habits.domain.HabitRepo
import com.shub39.grit.core.habits.domain.isDueOn
import com.shub39.grit.core.settings.domain.ReminderMode
import com.shub39.grit.core.settings.domain.Sections
import com.shub39.grit.core.tasks.domain.Task
import com.shub39.grit.core.tasks.domain.TaskRepo
import com.shub39.grit.domain.SettingsDatastore
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

@Single
class ReminderCoordinator(
    private val context: Context,
    private val settingsDatastore: SettingsDatastore,
    private val habitRepo: HabitRepo,
    private val taskRepo: TaskRepo,
    private val notificationManager: GritNotificationManager,
    private val scheduler: NotificationAlarmScheduler,
    private val runtimeStore: ReminderRuntimeStore,
) {
    companion object {
        private const val TAG = "ReminderCoordinator"
    }

    suspend fun dispatchHabitReminder(habit: Habit, payload: ReminderPayload) {
        if (!settingsDatastore.getReminderEnabledFlow().first()) return

        when (val mode = settingsDatastore.getReminderModeFlow().first()) {
            ReminderMode.SILENT,
            ReminderMode.POPUP -> {
                if (resolveWinnerFor(payload)?.matches(payload) != false) {
                    notificationManager.showHabitNotification(habit, payload, mode)
                }
            }

            ReminderMode.ALARM -> {
                if (shouldStartAlarm(payload)) {
                    startAlarmReminder(payload)
                }
            }
        }
    }

    suspend fun dispatchTaskReminder(task: Task, payload: ReminderPayload) {
        if (!settingsDatastore.getReminderEnabledFlow().first()) return

        when (val mode = settingsDatastore.getReminderModeFlow().first()) {
            ReminderMode.SILENT,
            ReminderMode.POPUP -> {
                if (resolveWinnerFor(payload)?.matches(payload) != false) {
                    notificationManager.showTaskNotification(task, payload, mode)
                }
            }

            ReminderMode.ALARM -> {
                if (shouldStartAlarm(payload)) {
                    startAlarmReminder(payload)
                }
            }
        }
    }

    fun handleReminderOpened(payload: ReminderPayload?) {
        payload ?: return
        clearTrackedReminder(payload)
        cancelDisplayedNotification(payload)
    }

    fun snoozeReminder(payload: ReminderPayload) {
        stopAlarmService()
        cancelDisplayedNotification(payload)

        val triggerAt = Clock.System.now().toEpochMilliseconds() + 5.minutes.inWholeMilliseconds
        scheduler.scheduleFollowUp(payload, triggerAt)
        runtimeStore.setTrackedSession(
            ReminderSession(payload = payload, ringing = false, followUpAtMillis = triggerAt)
        )
    }

    fun dismissReminder(payload: ReminderPayload) {
        clearTrackedReminder(payload)
        cancelDisplayedNotification(payload)
    }

    fun timeoutReminder(payload: ReminderPayload) {
        stopAlarmService()
        cancelDisplayedNotification(payload)

        val nextUnattendedCount = payload.unattendedCount + 1
        if (nextUnattendedCount >= 3) {
            clearTrackedReminder(payload)
            return
        }

        val nextPayload = payload.copy(unattendedCount = nextUnattendedCount)
        val triggerAt = Clock.System.now().toEpochMilliseconds() + 5.minutes.inWholeMilliseconds
        scheduler.scheduleFollowUp(nextPayload, triggerAt)
        runtimeStore.setTrackedSession(
            ReminderSession(
                payload = nextPayload,
                ringing = false,
                followUpAtMillis = triggerAt,
            )
        )
    }

    private suspend fun resolveWinnerFor(payload: ReminderPayload): ReminderPayload? {
        val habitCompletions =
            habitRepo.getHabitStatuses().filter { it.date == payload.occurrenceDate }.map { it.habitId }.toSet()

        val habitPayloads =
            habitRepo.getHabits().asSequence()
                .filter { it.reminder && it.isDueOn(payload.occurrenceDate) }
                .filterNot { it.id in habitCompletions }
                .mapNotNull { habit ->
                    val triggerAt = habit.triggerAtMillis(payload.occurrenceDate)
                    if (triggerAt == payload.originalTriggerAtMillis) {
                        ReminderPayload(
                            type = ReminderTargetType.HABIT,
                            itemId = habit.id,
                            title = habit.title,
                            description = habit.description.ifBlank { null },
                            section = Sections.Habits,
                            occurrenceDate = payload.occurrenceDate,
                            originalTriggerAtMillis = triggerAt,
                        )
                    } else {
                        null
                    }
                }

        val taskPayloads =
            taskRepo.getTasks().asSequence()
                .filter { !it.status && it.reminder != null }
                .mapNotNull { task ->
                    val reminder = task.reminder ?: return@mapNotNull null
                    val triggerAt =
                        reminder.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    if (
                        reminder.date == payload.occurrenceDate &&
                            triggerAt == payload.originalTriggerAtMillis
                    ) {
                        ReminderPayload(
                            type = ReminderTargetType.TASK,
                            itemId = task.id,
                            title = task.title,
                            section = Sections.Tasks,
                            occurrenceDate = reminder.date,
                            originalTriggerAtMillis = triggerAt,
                        )
                    } else {
                        null
                    }
                }

        return (habitPayloads + taskPayloads).maxWithOrNull(
            compareBy<ReminderPayload> { it.originalTriggerAtMillis }
                .thenBy { if (it.type == ReminderTargetType.HABIT) 1 else 0 }
                .thenBy { it.itemId }
        )
    }

    private fun shouldStartAlarm(payload: ReminderPayload): Boolean {
        val tracked = runtimeStore.getTrackedSession() ?: return true
        if (tracked.payload.matches(payload)) return true

        return if (payload.shouldReplace(tracked.payload)) {
            clearTrackedReminder(tracked.payload)
            true
        } else {
            false
        }
    }

    private fun clearTrackedReminder(payload: ReminderPayload) {
        runtimeStore.getTrackedSession()
            ?.takeIf { it.payload.matches(payload) || payload.shouldReplace(it.payload) }
            ?.let { tracked ->
                scheduler.cancelFollowUp(tracked.payload)
                runtimeStore.clearTrackedSession()
            }
        stopAlarmService()
    }

    private fun cancelDisplayedNotification(payload: ReminderPayload) {
        NotificationManagerCompat.from(context).cancel(payload.notificationId())
    }

    private fun stopAlarmService() {
        context.stopService(Intent(context, AlarmReminderService::class.java))
    }

    private fun startAlarmReminder(payload: ReminderPayload) {
        runtimeStore.setTrackedSession(ReminderSession(payload = payload, ringing = false))
        notificationManager.showAlarmNotification(payload)

        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmReminderService::class.java).putReminderPayload(payload),
            )
        }.onSuccess {
            runtimeStore.setTrackedSession(ReminderSession(payload = payload, ringing = true))
        }.onFailure { throwable ->
            Log.w(
                TAG,
                "Foreground alarm service could not start for ${payload.type} ${payload.itemId}; keeping notification-only fallback",
                throwable,
            )
        }
    }

    private fun Habit.triggerAtMillis(date: LocalDate): Long =
        LocalDateTime(date, time.time)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
}
