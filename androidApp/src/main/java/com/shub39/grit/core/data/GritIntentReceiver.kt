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
package com.shub39.grit.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shub39.grit.core.now
import com.shub39.grit.core.data.notification.GritNotificationManager
import com.shub39.grit.core.data.notification.ReminderCoordinator
import com.shub39.grit.core.data.notification.ReminderPayload
import com.shub39.grit.core.data.notification.ReminderTargetType
import com.shub39.grit.core.data.notification.getReminderPayloadOrNull
import com.shub39.grit.core.habits.domain.HabitRepo
import com.shub39.grit.core.habits.domain.HabitStatus
import com.shub39.grit.core.habits.domain.isDueOn
import com.shub39.grit.core.tasks.domain.TaskRepo
import com.shub39.grit.domain.AlarmScheduler
import com.shub39.grit.domain.IntentActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class GritIntentReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        private const val TAG = "GritIntentReceiver"
    }

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Received intent")
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                if (intent == null) return@launch

                when (intent.action) {
                    IntentActions.HABIT_NOTIFICATION.action -> habitNotification(intent)

                    IntentActions.ADD_HABIT_STATUS.action -> addHabitStatus(intent)

                    IntentActions.MARK_TASK_DONE.action -> markTaskDone(intent)

                    IntentActions.TASK_NOTIFICATION.action -> taskNotification(intent)

                    IntentActions.REMINDER_SNOOZE.action ->
                        intent.getReminderPayloadOrNull()?.let { get<ReminderCoordinator>().snoozeReminder(it) }

                    IntentActions.REMINDER_DISMISS.action ->
                        intent.getReminderPayloadOrNull()?.let { get<ReminderCoordinator>().dismissReminder(it) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error: ", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun markTaskDone(intent: Intent) {
        Log.d(TAG, "Mark task done intent received")
        val taskId = intent.getLongExtra("task_id", -1)
        if (taskId < 0) return

        val taskRepo = get<TaskRepo>()
        val task = taskRepo.getTaskById(taskId) ?: return
        taskRepo.upsertTask(task.copy(status = true))

        Log.d(TAG, "Task marked as complete successfully")
        get<GritNotificationManager>().cancelNotification(task)
    }

    private suspend fun addHabitStatus(intent: Intent) {
        Log.d(TAG, "Add habit status intent received")
        val habitId = intent.getLongExtra("habit_id", -1)
        if (habitId < 0) return

        val habitRepo = get<HabitRepo>()
        habitRepo.insertHabitStatus(HabitStatus(habitId = habitId, date = LocalDate.now()))

        Log.d(TAG, "Habit status added successfully")
        get<GritNotificationManager>().cancelNotification(habitId.toInt())
    }

    private suspend fun taskNotification(intent: Intent) {
        val payload =
            intent.getReminderPayloadOrNull()
                ?: createTaskPayload(intent.getLongExtra("task_id", -1))
                ?: return

        val taskRepo = get<TaskRepo>()
        val task = taskRepo.getTaskById(payload.itemId) ?: return
        if (task.status || task.reminder == null) return

        Log.d(TAG, "Dispatching task reminder")
        get<ReminderCoordinator>().dispatchTaskReminder(task, payload)
    }

    private suspend fun habitNotification(intent: Intent) {
        val payload =
            intent.getReminderPayloadOrNull()
                ?: createHabitPayload(intent.getLongExtra("habit_id", -1))
                ?: return

        val habitRepo = get<HabitRepo>()
        val habit = habitRepo.getHabitById(payload.itemId) ?: return
        if (!habit.reminder) return

        if (!habit.isDueOn(payload.occurrenceDate)) {
            get<AlarmScheduler>().schedule(habit)
            return
        }

        val habitStatus = habitRepo.getStatusForHabit(payload.itemId)
        if (habitStatus.any { it.date == payload.occurrenceDate }) {
            Log.d(TAG, "Habit already completed for ${payload.occurrenceDate}")
        } else {
            get<ReminderCoordinator>().dispatchHabitReminder(habit, payload)
        }

        get<AlarmScheduler>().schedule(habit)
    }

    private suspend fun createHabitPayload(habitId: Long): ReminderPayload? {
        if (habitId < 0L) return null
        val habitRepo = get<HabitRepo>()
        val habit = habitRepo.getHabitById(habitId) ?: return null
        val occurrenceDate = LocalDate.now()

        return ReminderPayload(
            type = ReminderTargetType.HABIT,
            itemId = habit.id,
            title = habit.title,
            description = habit.description.ifBlank { null },
            section = com.shub39.grit.core.settings.domain.Sections.Habits,
            occurrenceDate = occurrenceDate,
            originalTriggerAtMillis =
                kotlinx.datetime.LocalDateTime(occurrenceDate, habit.time.time)
                    .toInstant(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds(),
        )
    }

    private suspend fun createTaskPayload(taskId: Long): ReminderPayload? {
        if (taskId < 0L) return null
        val taskRepo = get<TaskRepo>()
        val task = taskRepo.getTaskById(taskId) ?: return null
        val reminder = task.reminder ?: return null

        return ReminderPayload(
            type = ReminderTargetType.TASK,
            itemId = task.id,
            title = task.title,
            section = com.shub39.grit.core.settings.domain.Sections.Tasks,
            occurrenceDate = reminder.date,
            originalTriggerAtMillis =
                reminder.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
        )
    }
}
