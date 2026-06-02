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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.shub39.grit.core.data.GritIntentReceiver
import com.shub39.grit.core.habits.domain.Habit
import com.shub39.grit.core.habits.domain.nextDueDateOnOrAfter
import com.shub39.grit.core.settings.domain.Sections
import com.shub39.grit.core.tasks.domain.Task
import com.shub39.grit.domain.AlarmScheduler
import com.shub39.grit.domain.IntentActions
import kotlin.time.ExperimentalTime
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

// implementation of AlarmScheduler using AlarmManager
@Single(binds = [AlarmScheduler::class])
@OptIn(ExperimentalTime::class)
class NotificationAlarmScheduler(
    private val context: Context,
    private val runtimeStore: ReminderRuntimeStore,
) : AlarmScheduler {

    companion object {
        private const val TAG = "NotificationAlarmScheduler"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(habit: Habit) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val candidateDate =
            if (habit.time.time <= now.time) {
                now.date.plus(1, DateTimeUnit.DAY)
            } else {
                now.date
            }

        scheduleHabitFrom(habit, candidateDate)
    }

    override fun scheduleNextHabitAfter(habit: Habit, date: LocalDate) {
        scheduleHabitFrom(habit, date.plus(1, DateTimeUnit.DAY))
    }

    private fun scheduleHabitFrom(habit: Habit, candidateDate: LocalDate) {
        cancel(habit)
        if (!habit.reminder) return

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        val scheduleDate = habit.nextDueDateOnOrAfter(candidateDate) ?: return
        val scheduleTime = habit.triggerAt(scheduleDate)

        if (scheduleTime < now) {
            Log.d(TAG, "Habit '${habit.title}' reminder time is in the past")
            return
        }

        val payload =
            ReminderPayload(
                type = ReminderTargetType.HABIT,
                itemId = habit.id,
                title = habit.title,
                description = habit.description.ifBlank { null },
                section = Sections.Habits,
                occurrenceDate = scheduleDate,
                originalTriggerAtMillis =
                    scheduleTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            )

        scheduleBaseAlarm(
            action = IntentActions.HABIT_NOTIFICATION.action,
            requestCode = habit.id.toInt(),
            payload = payload,
            triggerAtMillis = payload.originalTriggerAtMillis,
        )
    }

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(task: Task) {
        cancel(task)
        val scheduleTime = task.reminder ?: return

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        if (scheduleTime < now) {
            Log.d(TAG, "Task '${task.title}' reminder time is in the past")
            return
        }

        val payload =
            ReminderPayload(
                type = ReminderTargetType.TASK,
                itemId = task.id,
                title = task.title,
                section = Sections.Tasks,
                occurrenceDate = scheduleTime.date,
                originalTriggerAtMillis =
                    scheduleTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            )

        scheduleBaseAlarm(
            action = IntentActions.TASK_NOTIFICATION.action,
            requestCode = task.id.toInt(),
            payload = payload,
            triggerAtMillis = payload.originalTriggerAtMillis,
        )
    }

    override fun cancel(habit: Habit) {
        cancelByAction(IntentActions.HABIT_NOTIFICATION.action, habit.id.toInt())
        runtimeStore.getTrackedSession()
            ?.takeIf {
                it.payload.type == ReminderTargetType.HABIT && it.payload.itemId == habit.id
            }?.let { cancelFollowUp(it.payload) }
        Log.d(TAG, "Cancelled: Habit '${habit.title}'")
    }

    override fun cancel(task: Task) {
        cancelByAction(IntentActions.TASK_NOTIFICATION.action, task.id.toInt())
        runtimeStore.getTrackedSession()
            ?.takeIf {
                it.payload.type == ReminderTargetType.TASK && it.payload.itemId == task.id
            }?.let { cancelFollowUp(it.payload) }
        Log.d(TAG, "Cancelled: Task '${task.title}'")
    }

    override fun cancelAll() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            alarmManager.cancelAll()
        }
    }

    fun scheduleFollowUp(payload: ReminderPayload, triggerAtMillis: Long) {
        val followUpPayload = payload
        scheduleBaseAlarm(
            action = reminderActionFor(payload),
            requestCode = followUpPayload.followUpRequestCode(),
            payload = followUpPayload,
            triggerAtMillis = triggerAtMillis,
        )
        Log.d(TAG, "Scheduled follow-up for ${payload.type} ${payload.itemId} at $triggerAtMillis")
    }

    fun cancelFollowUp(payload: ReminderPayload) {
        cancelByAction(reminderActionFor(payload), payload.followUpRequestCode())
        Log.d(TAG, "Cancelled follow-up for ${payload.type} ${payload.itemId}")
    }

    private fun scheduleBaseAlarm(
        action: String,
        requestCode: Int,
        payload: ReminderPayload,
        triggerAtMillis: Long,
    ) {
        val notificationIntent =
            Intent(context, GritIntentReceiver::class.java)
                .setAction(action)
                .putReminderPayload(payload)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } catch (securityException: SecurityException) {
                Log.w(
                    TAG,
                    "Exact alarm access denied for ${payload.type} '${payload.title}', falling back to inexact while-idle alarm",
                    securityException,
                )
                scheduleInexactWhileIdle(triggerAtMillis, pendingIntent)
            }
        } else {
            Log.w(
                TAG,
                "Exact alarm access unavailable for ${payload.type} '${payload.title}', falling back to inexact while-idle alarm",
            )
            scheduleInexactWhileIdle(triggerAtMillis, pendingIntent)
        }

        Log.d(TAG, "Scheduled ${payload.type} '${payload.title}' at $triggerAtMillis")
    }

    private fun scheduleInexactWhileIdle(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun cancelByAction(action: String, requestCode: Int) {
        val cancelIntent = Intent(context, GritIntentReceiver::class.java).setAction(action)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        alarmManager.cancel(pendingIntent)
    }

    private fun reminderActionFor(payload: ReminderPayload): String =
        when (payload.type) {
            ReminderTargetType.HABIT -> IntentActions.HABIT_NOTIFICATION.action
            ReminderTargetType.TASK -> IntentActions.TASK_NOTIFICATION.action
        }

    private fun Habit.triggerAt(date: LocalDate): LocalDateTime =
        LocalDateTime(
            date = date,
            time = LocalTime(hour = time.hour, minute = time.minute),
        )
}
