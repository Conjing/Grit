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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.shub39.grit.R
import com.shub39.grit.core.settings.presentation.ui.section.openReminderNotificationSettings
import com.shub39.grit.core.shared_ui.GritDialog

private const val PREFS_NAME = "grit_reminder_setup"
private const val NOTIFICATION_PERMISSION_REQUESTED_KEY = "notification_permission_requested"
private const val REMINDER_SETUP_PROMPT_SHOWN_KEY = "reminder_setup_prompt_shown"

@Composable
fun ReminderSetupController() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var showSetupPrompt by remember { mutableStateOf(false) }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
            prefs.edit().putBoolean(NOTIFICATION_PERMISSION_REQUESTED_KEY, true).apply()
            if (!prefs.getBoolean(REMINDER_SETUP_PROMPT_SHOWN_KEY, false)) {
                showSetupPrompt = true
            }
        }

    LaunchedEffect(Unit) {
        val permissionGranted = context.hasNotificationPermission()
        val alreadyRequested = prefs.getBoolean(NOTIFICATION_PERMISSION_REQUESTED_KEY, false)
        val alreadyPrompted = prefs.getBoolean(REMINDER_SETUP_PROMPT_SHOWN_KEY, false)

        when {
            !permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !alreadyRequested -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            !alreadyPrompted -> {
                showSetupPrompt = true
            }
        }
    }

    if (showSetupPrompt) {
        GritDialog(
            onDismissRequest = {
                prefs.edit().putBoolean(REMINDER_SETUP_PROMPT_SHOWN_KEY, true).apply()
                showSetupPrompt = false
            }
        ) {
            Text(
                text = stringResource(R.string.reminder_setup_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.reminder_setup_desc),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = {
                        prefs.edit().putBoolean(REMINDER_SETUP_PROMPT_SHOWN_KEY, true).apply()
                        showSetupPrompt = false
                    }
                ) {
                    Text(text = stringResource(R.string.later))
                }

                Button(
                    onClick = {
                        prefs.edit().putBoolean(REMINDER_SETUP_PROMPT_SHOWN_KEY, true).apply()
                        showSetupPrompt = false
                        openReminderNotificationSettings(context)
                    }
                ) {
                    Text(text = stringResource(R.string.open_notification_settings))
                }
            }
        }
    }
}

private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
