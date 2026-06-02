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

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shub39.grit.R
import com.shub39.grit.app.MainActivity
import com.shub39.grit.app.ReminderAlertActivity
import com.shub39.grit.core.now
import com.shub39.grit.core.data.GritIntentReceiver
import com.shub39.grit.core.habits.domain.Habit
import com.shub39.grit.core.settings.domain.ReminderMode
import com.shub39.grit.core.tasks.domain.Task
import com.shub39.grit.domain.IntentActions
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single

@Single
class GritNotificationManager(
    private val context: Context,
    private val runtimeStore: ReminderRuntimeStore,
    private val scheduler: NotificationAlarmScheduler,
) {
    companion object {
        private const val TAG = "NotificationManager"
        private const val SILENT_CHANNEL_ID = "grit.reminders.silent"
        private const val POPUP_CHANNEL_ID = "grit.reminders.popup"
        private const val ALARM_CHANNEL_ID = "grit.reminders.alarm"

        fun createNotificationChannel(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val silentChannel =
                NotificationChannel(
                    SILENT_CHANNEL_ID,
                    "Silent Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                    description = "Silent reminder notifications that stay in the shade."
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }

            val popupChannel =
                NotificationChannel(
                    POPUP_CHANNEL_ID,
                    "Popup Reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Heads-up reminder notifications with one-time alerts."
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build(),
                    )
                    setShowBadge(true)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }

            val alarmChannel =
                NotificationChannel(
                    ALARM_CHANNEL_ID,
                    "Alarm Reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Alarm-style reminder notifications for urgent habit check-ins."
                    setSound(
                        null,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build(),
                    )
                    enableVibration(false)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }

            notificationManager.createNotificationChannels(
                listOf(silentChannel, popupChannel, alarmChannel)
            )
        }
    }

    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    fun showHabitNotification(habit: Habit, payload: ReminderPayload, mode: ReminderMode) {
        val openIntent = buildOpenAppPendingIntent(payload, stopActiveReminder = false)
        val markDoneIntent =
            PendingIntent.getBroadcast(
                context,
                payload.notificationId() + 10_000,
                Intent(context, GritIntentReceiver::class.java)
                    .setAction(IntentActions.ADD_HABIT_STATUS.action)
                    .putExtra("habit_id", habit.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            baseReminderBuilder(
                payload = payload,
                mode = mode,
                contentIntent = openIntent,
            ).addAction(R.drawable.notif_icon, context.getString(R.string.done), markDoneIntent)

        notify(payload.notificationId(), builder.build())
    }

    fun showTaskNotification(task: Task, payload: ReminderPayload, mode: ReminderMode) {
        val openIntent = buildOpenAppPendingIntent(payload, stopActiveReminder = false)
        val markDoneIntent =
            PendingIntent.getBroadcast(
                context,
                payload.notificationId() + 20_000,
                Intent(context, GritIntentReceiver::class.java)
                    .setAction(IntentActions.MARK_TASK_DONE.action)
                    .putExtra("task_id", task.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            baseReminderBuilder(
                payload = payload,
                mode = mode,
                contentIntent = openIntent,
            ).addAction(R.drawable.notif_icon, context.getString(R.string.done), markDoneIntent)

        notify(payload.notificationId(), builder.build())
    }

    fun buildAlarmNotification(payload: ReminderPayload): Notification {
        val openIntent = buildOpenAppPendingIntent(payload, stopActiveReminder = true)
        val fullScreenIntent = buildAlarmFullScreenPendingIntent(payload)
        val snoozeIntent =
            PendingIntent.getBroadcast(
                context,
                payload.notificationId() + 30_000,
                Intent(context, GritIntentReceiver::class.java)
                    .setAction(IntentActions.REMINDER_SNOOZE.action)
                    .putReminderPayload(payload),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val dismissIntent =
            PendingIntent.getBroadcast(
                context,
                payload.notificationId() + 40_000,
                Intent(context, GritIntentReceiver::class.java)
                    .setAction(IntentActions.REMINDER_DISMISS.action)
                    .putReminderPayload(payload),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(payload.title)
                .setContentText(payload.description ?: "")
                .setStyle(NotificationCompat.BigTextStyle().bigText(payload.description ?: ""))
                .setContentIntent(openIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(
                    R.drawable.schedule,
                    context.getString(R.string.snooze_5_min),
                    snoozeIntent,
                )
                .addAction(
                    R.drawable.close,
                    context.getString(R.string.close_for_today),
                    dismissIntent,
                )

        return builder.build()
    }

    fun showAlarmNotification(payload: ReminderPayload) {
        notify(payload.notificationId(), buildAlarmNotification(payload))
    }

    fun cancelNotification(habitId: Int) {
        notificationManager.cancel(habitId)
        clearTrackedReminderIfMatches(ReminderTargetType.HABIT, habitId.toLong())
    }

    fun cancelNotification(task: Task) {
        notificationManager.cancel(task.id.toInt() + 1000)
        clearTrackedReminderIfMatches(ReminderTargetType.TASK, task.id)
    }

    private fun baseReminderBuilder(
        payload: ReminderPayload,
        mode: ReminderMode,
        contentIntent: PendingIntent,
    ): NotificationCompat.Builder {
        val (channelId, priority) =
            when (mode) {
                ReminderMode.SILENT -> SILENT_CHANNEL_ID to NotificationCompat.PRIORITY_DEFAULT
                ReminderMode.POPUP -> POPUP_CHANNEL_ID to NotificationCompat.PRIORITY_HIGH
                ReminderMode.ALARM -> ALARM_CHANNEL_ID to NotificationCompat.PRIORITY_MAX
            }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.notif_icon)
            .setContentTitle(payload.title)
            .setContentText(payload.description ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.description ?: ""))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(mode == ReminderMode.SILENT)
    }

    private fun buildOpenAppPendingIntent(
        payload: ReminderPayload,
        stopActiveReminder: Boolean,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_REMINDER_TARGET_SECTION, payload.section.name)
                .putReminderPayload(payload)
                .putExtra(EXTRA_STOP_ACTIVE_REMINDER, stopActiveReminder)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            context,
            payload.notificationId() + if (stopActiveReminder) 50_000 else 60_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildAlarmFullScreenPendingIntent(payload: ReminderPayload): PendingIntent {
        val intent =
            Intent(context, ReminderAlertActivity::class.java)
                .putReminderPayload(payload)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )

        return PendingIntent.getActivity(
            context,
            payload.notificationId() + 70_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        if (
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(id, notification)
        } else {
            Log.e(TAG, "Notification permission denied!")
        }
    }

    private fun clearTrackedReminderIfMatches(type: ReminderTargetType, itemId: Long) {
        val tracked = runtimeStore.getTrackedSession() ?: return
        if (
            tracked.payload.type == type &&
                tracked.payload.itemId == itemId &&
                tracked.payload.occurrenceDate == LocalDate.now()
        ) {
            scheduler.cancelFollowUp(tracked.payload)
            runtimeStore.clearTrackedSession()
            context.stopService(Intent(context, AlarmReminderService::class.java))
        }
    }
}
