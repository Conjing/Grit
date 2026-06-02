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

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.shub39.grit.R
import com.shub39.grit.domain.SettingsDatastore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class AlarmReminderService : Service(), KoinComponent {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null
    private var timeoutHandled = false

    private val notificationManager by lazy { get<GritNotificationManager>() }
    private val settingsDatastore by lazy { get<SettingsDatastore>() }
    private val reminderCoordinator by lazy { get<ReminderCoordinator>() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val payload = intent?.getReminderPayloadOrNull() ?: return START_NOT_STICKY
        timeoutHandled = false
        startForegroundNotification(payload)
        startMediaAndVibration(payload)
        scheduleTimeout(payload)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMediaAndVibration()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotification(payload: ReminderPayload) {
        val notification = notificationManager.buildAlarmNotification(payload)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                payload.notificationId(),
                notification,
                ServiceInfoCompat.mediaPlaybackFlag(),
            )
        } else {
            startForeground(payload.notificationId(), notification)
        }
    }

    private fun startMediaAndVibration(payload: ReminderPayload) {
        stopMediaAndVibration()

        serviceScope.launch {
            val customPath = settingsDatastore.getAlarmSoundPathFlow().first()
            val soundUri = resolveSoundUri(customPath)

            mediaPlayer =
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(this@AlarmReminderService, soundUri)
                    isLooping = true
                    prepare()
                    start()
                }

            vibrator().vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 800, 350, 800, 350), 0)
            )
        }
    }

    private fun scheduleTimeout(payload: ReminderPayload) {
        serviceScope.launch {
            delay(30_000)
            if (!timeoutHandled) {
                timeoutHandled = true
                reminderCoordinator.timeoutReminder(payload)
                stopSelf()
            }
        }
    }

    private fun stopMediaAndVibration() {
        mediaPlayer?.runCatching {
            stop()
            release()
        }
        mediaPlayer = null
        vibrator().cancel()
    }

    private fun resolveSoundUri(customPath: String?): Uri {
        val defaultUri = Uri.parse("android.resource://$packageName/${R.raw.grit_alarm_default}")
        if (customPath.isNullOrBlank()) return defaultUri

        val customUri = Uri.parse(customPath)
        return runCatching { contentResolver.openInputStream(customUri)?.close() }.fold(
            onSuccess = { customUri },
            onFailure = {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: defaultUri
            },
        )
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VIBRATOR_MANAGER_SERVICE).let { service ->
                (service as VibratorManager).defaultVibrator
            }
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
}

private object ServiceInfoCompat {
    fun mediaPlaybackFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
}
