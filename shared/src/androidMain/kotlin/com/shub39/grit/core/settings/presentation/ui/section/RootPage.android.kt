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

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.shared_ui.detachedItemShape
import com.shub39.grit.core.shared_ui.endItemShape
import com.shub39.grit.core.shared_ui.leadingItemShape
import com.shub39.grit.core.shared_ui.listItemColors
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import grit.shared.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

actual fun LazyListScope.languagePicker(onClick: () -> Unit) {
    if (Build.VERSION.SDK_INT >= 33) {
        item {
            ListItem(
                colors = listItemColors(),
                leadingContent = {
                    Icon(
                        painter = painterResource(Res.drawable.language),
                        contentDescription = null,
                    )
                },
                headlineContent = { Text(text = stringResource(Res.string.language)) },
                supportingContent = {
                    Text(text = LocalLocale.current.platformLocale.displayLanguage)
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(Res.drawable.arrow_forward),
                        contentDescription = "Navigate",
                    )
                },
                modifier = Modifier.clip(detachedItemShape()).clickable { onClick() },
            )
        }
    }
}

@Composable
actual fun AlarmSoundSettings(
    soundName: String?,
    onPick: (path: String, label: String) -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ListItem(
            colors = listItemColors(),
            leadingContent = {
                Icon(
                    painter = painterResource(Res.drawable.alarm),
                    contentDescription = null,
                )
            },
            headlineContent = { Text(text = stringResource(Res.string.alarm_sound)) },
            supportingContent = {
                Text(
                    text = soundName ?: stringResource(Res.string.alarm_sound_default_desc),
                    color =
                        if (soundName == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                )
            },
            trailingContent = {
                Icon(
                    painter = painterResource(Res.drawable.arrow_forward),
                    contentDescription = null,
                )
            },
            modifier =
                Modifier.clip(leadingItemShape()).clickable {
                    scope.launch {
                        val file =
                            FileKit.openFilePicker(
                                type = FileKitType.File("mp3", "wav", "ogg", "m4a", "aac", "flac")
                            )

                        if (file != null) {
                            val pickedUri = Uri.parse(file.path)
                            if (pickedUri.scheme == "content") {
                                runCatching {
                                    context.contentResolver.takePersistableUriPermission(
                                        pickedUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                    )
                                }
                            }
                            onPick(file.path, file.name)
                        }
                    }
                },
        )

        ListItem(
            colors = listItemColors(),
            headlineContent = { Text(text = stringResource(Res.string.alarm_sound_reset)) },
            supportingContent = {
                Text(text = stringResource(Res.string.alarm_sound_reset_desc))
            },
            modifier =
                Modifier.clip(endItemShape()).clickable(enabled = soundName != null) { onReset() },
        )
    }
}
