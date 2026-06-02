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
package com.shub39.grit.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shub39.grit.R
import com.shub39.grit.core.data.notification.EXTRA_REMINDER_TARGET_SECTION
import com.shub39.grit.core.data.notification.ReminderCoordinator
import com.shub39.grit.core.data.notification.ReminderPayload
import com.shub39.grit.core.data.notification.getReminderPayloadOrNull
import com.shub39.grit.core.theme.GritTheme
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject

class ReminderAlertActivity : ComponentActivity() {
    private val reminderCoordinator: ReminderCoordinator by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLockScreenPresentation()

        val payload = intent.getReminderPayloadOrNull()
        if (payload == null) {
            finish()
            return
        }

        setContent {
            GritTheme {
                ReminderAlertScreen(
                    payload = payload,
                    onOpenApp = { openApp(payload) },
                    onSnooze = {
                        reminderCoordinator.snoozeReminder(payload)
                        finish()
                    },
                    onDismiss = {
                        reminderCoordinator.dismissReminder(payload)
                        finish()
                    },
                    onTimeout = { finish() },
                )
            }
        }
    }

    private fun openApp(payload: ReminderPayload) {
        reminderCoordinator.handleReminderOpened(payload)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_REMINDER_TARGET_SECTION, payload.section.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun enableLockScreenPresentation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}

@Composable
private fun ReminderAlertScreen(
    payload: ReminderPayload,
    onOpenApp: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onTimeout: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    LaunchedEffect(payload) {
        delay(30_000)
        onTimeout()
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpenApp() },
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.alarm),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = payload.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                payload.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = stringResource(R.string.tap_card_to_open_app),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    ) {
                        Text(text = stringResource(R.string.close_for_today))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onSnooze,
                    ) {
                        Text(text = stringResource(R.string.snooze_5_min))
                    }
                }
            }
        }
    }
}
