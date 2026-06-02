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
package com.shub39.grit.core.settings.presentation.ui.section

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.shared_ui.detachedItemShape
import com.shub39.grit.core.shared_ui.endItemShape
import com.shub39.grit.core.shared_ui.leadingItemShape
import com.shub39.grit.core.shared_ui.listItemColors
import com.shub39.grit.core.shared_ui.middleItemShape
import grit.shared.generated.resources.Res
import grit.shared.generated.resources.alarm
import grit.shared.generated.resources.arrow_forward
import grit.shared.generated.resources.notification_channel_settings
import grit.shared.generated.resources.notification_channel_settings_desc
import grit.shared.generated.resources.reminder_system_settings
import grit.shared.generated.resources.reminder_system_settings_desc
import grit.shared.generated.resources.background_activity_settings
import grit.shared.generated.resources.background_activity_settings_desc
import grit.shared.generated.resources.exact_alarm_settings
import grit.shared.generated.resources.exact_alarm_settings_desc
import grit.shared.generated.resources.full_screen_reminder_settings
import grit.shared.generated.resources.full_screen_reminder_settings_desc
import grit.shared.generated.resources.schedule
import grit.shared.generated.resources.settings
import grit.shared.generated.resources.view_day
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

actual fun LazyListScope.reminderSystemSettingsSection() {
    item {
        ReminderSystemSettingsSection()
    }
}

@Composable
private fun ReminderSystemSettingsSection() {
    val context = LocalContext.current
    val items =
        buildList {
            add(
                ReminderSystemSettingItem(
                    title = stringResource(Res.string.notification_channel_settings),
                    description = stringResource(Res.string.notification_channel_settings_desc),
                    leadingIcon = Res.drawable.settings,
                    onClick = { openReminderNotificationSettings(context) },
                )
            )
            add(
                ReminderSystemSettingItem(
                    title = stringResource(Res.string.exact_alarm_settings),
                    description = stringResource(Res.string.exact_alarm_settings_desc),
                    leadingIcon = Res.drawable.schedule,
                    onClick = { openReminderExactAlarmSettings(context) },
                )
            )
            add(
                ReminderSystemSettingItem(
                    title = stringResource(Res.string.background_activity_settings),
                    description = stringResource(Res.string.background_activity_settings_desc),
                    leadingIcon = Res.drawable.alarm,
                    onClick = { openReminderBackgroundSettings(context) },
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(
                    ReminderSystemSettingItem(
                        title = stringResource(Res.string.full_screen_reminder_settings),
                        description = stringResource(Res.string.full_screen_reminder_settings_desc),
                        leadingIcon = Res.drawable.view_day,
                        onClick = { openReminderFullScreenSettings(context) },
                    )
                )
            }
        }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ListItem(
            headlineContent = { Text(text = stringResource(Res.string.reminder_system_settings)) },
            supportingContent = {
                Text(text = stringResource(Res.string.reminder_system_settings_desc))
            },
            colors = listItemColors(),
            modifier = Modifier.clip(leadingItemShape()),
        )

        items.forEachIndexed { index, item ->
            ListItem(
                headlineContent = { Text(text = item.title) },
                supportingContent = { Text(text = item.description) },
                leadingContent = {
                    Icon(
                        painter = painterResource(item.leadingIcon),
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(Res.drawable.arrow_forward),
                        contentDescription = null,
                    )
                },
                colors = listItemColors(),
                modifier =
                    Modifier.clip(
                            when {
                                items.size == 1 -> detachedItemShape()
                                index == items.lastIndex -> endItemShape()
                                else -> middleItemShape()
                            }
                        )
                        .clickable { item.onClick() },
            )
        }
    }
}

fun openReminderNotificationSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    context.startSettingsIntent(intent)
}

fun openReminderExactAlarmSettings(context: Context) {
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            context.appDetailsIntent()
        }
    context.startSettingsIntent(intent)
}

fun openReminderBackgroundSettings(context: Context) {
    val intents = manufacturerBackgroundIntents(context) + genericBackgroundIntents(context)
    context.startFirstAvailableIntent(intents)
}

fun openReminderFullScreenSettings(context: Context) {
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
    context.startSettingsIntent(intent)
}

private fun Context.startSettingsIntent(intent: Intent) {
    val fallback = appDetailsIntent()
    runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.recoverCatching {
        if (it is ActivityNotFoundException) {
            startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            throw it
        }
    }
}

private fun Context.startFirstAvailableIntent(intents: List<Intent>) {
    intents.firstOrNull { intent ->
        intent.resolveActivity(packageManager) != null
    }?.let { startSettingsIntent(it) } ?: startSettingsIntent(appDetailsIntent())
}

private fun Context.appDetailsIntent(): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$packageName"),
    )

private fun manufacturerBackgroundIntents(context: Context): List<Intent> {
    val packageName = context.packageName

    return when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" ->
            listOf(
                Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
                Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                ).putExtra("package_name", packageName)
                    .putExtra("package_label", packageName),
            )

        "oppo", "realme", "oneplus" ->
            listOf(
                Intent().setClassName(
                    "com.oplus.safecenter",
                    "com.oplus.startupapp.view.StartupAppListActivity",
                ),
                Intent().setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity",
                ),
                Intent().setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                ),
            )

        "vivo", "iqoo" ->
            listOf(
                Intent().setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                ),
                Intent().setClassName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                ),
            )

        "huawei", "honor" ->
            listOf(
                Intent().setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
                Intent().setClassName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
            )

        else -> emptyList()
    }
}

private fun genericBackgroundIntents(context: Context): List<Intent> =
    listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        context.appDetailsIntent(),
    )

private data class ReminderSystemSettingItem(
    val title: String,
    val description: String,
    val leadingIcon: org.jetbrains.compose.resources.DrawableResource,
    val onClick: () -> Unit,
)
