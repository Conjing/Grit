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
package com.shub39.grit.core.habits.presentation.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.habits.domain.Habit
import com.shub39.grit.core.habits.domain.HabitIntervalUnit
import com.shub39.grit.core.habits.domain.HabitRepeatMode
import com.shub39.grit.core.now
import com.shub39.grit.core.shared_ui.ExpressiveSwitch
import com.shub39.grit.core.shared_ui.GritBottomSheet
import com.shub39.grit.core.shared_ui.GritTimePicker
import com.shub39.grit.core.shared_ui.detachedItemShape
import com.shub39.grit.core.shared_ui.endItemShape
import com.shub39.grit.core.shared_ui.leadingItemShape
import com.shub39.grit.core.shared_ui.listItemColors
import com.shub39.grit.core.shared_ui.middleItemShape
import com.shub39.grit.core.theme.GritTheme
import com.shub39.grit.core.theme.flexFontEmphasis
import com.shub39.grit.core.theme.flexFontRounded
import com.shub39.grit.core.toFormattedString
import grit.shared.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
expect fun HabitUpsertSheet(
    habit: Habit,
    onDismissRequest: () -> Unit,
    onUpsertHabit: (Habit) -> Unit,
    is24Hr: Boolean,
    modifier: Modifier = Modifier,
    isEditSheet: Boolean = false,
)

@Composable
fun HabitUpsertSheetContent(
    newHabit: Habit,
    updateHabit: (Habit) -> Unit,
    onDismissRequest: () -> Unit,
    onUpsertHabit: (Habit) -> Unit,
    is24Hr: Boolean,
    isEditSheet: Boolean = false,
    notificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var timePickerDialog by remember { mutableStateOf(false) }
    var startDatePickerDialog by remember { mutableStateOf(false) }
    var intervalText by remember { mutableStateOf(newHabit.intervalValue.toString()) }

    val titleTextFieldState =
        rememberTextFieldState(
            initialText = newHabit.title,
            initialSelection = TextRange(newHabit.title.length),
        )

    val descTextFieldState =
        rememberTextFieldState(
            initialText = newHabit.description,
            initialSelection = TextRange(newHabit.description.length),
        )

    val parsedInterval = intervalText.toIntOrNull()
    val hasValidCustomInterval = parsedInterval != null && parsedInterval > 0
    val hasValidSchedule =
        when (newHabit.repeatMode) {
            HabitRepeatMode.WEEKLY -> newHabit.days.isNotEmpty()
            HabitRepeatMode.CUSTOM -> hasValidCustomInterval
        }
    val showStartDate = newHabit.repeatMode == HabitRepeatMode.CUSTOM
    val showReminder = hasValidSchedule
    val showReminderTime = showReminder && newHabit.reminder

    LaunchedEffect(Unit) {
        delay(400)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    GritBottomSheet(
        onDismissRequest = onDismissRequest,
        padding = 0.dp,
        modifier = modifier.imePadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(50.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialShapes.Pill.toShape(),
                        ),
            ) {
                Icon(
                    imageVector =
                        vectorResource(if (isEditSheet) Res.drawable.edit else Res.drawable.add),
                    contentDescription = "Edit Habit",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Text(
                text =
                    stringResource(
                        if (isEditSheet) Res.string.edit_habit else Res.string.add_habit
                    ),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = flexFontEmphasis()),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                OutlinedTextField(
                    state = titleTextFieldState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next,
                        ),
                    label = {
                        if (titleTextFieldState.text.length <= 20) {
                            Text(
                                text =
                                    stringResource(
                                        if (isEditSheet) Res.string.update_title
                                        else Res.string.title
                                    )
                            )
                        } else {
                            Text(text = stringResource(Res.string.too_long))
                        }
                    },
                    isError = titleTextFieldState.text.length > 20,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }

            item {
                OutlinedTextField(
                    state = descTextFieldState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        if (descTextFieldState.text.length <= 50) {
                            Text(
                                text =
                                    stringResource(
                                        if (isEditSheet) Res.string.update_description
                                        else Res.string.description
                                    )
                            )
                        } else {
                            Text(text = stringResource(Res.string.too_long))
                        }
                    },
                    isError = descTextFieldState.text.length > 50,
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Card(
                        shape =
                            when {
                                !showStartDate && !showReminder -> detachedItemShape()
                                else -> leadingItemShape()
                            },
                        modifier = Modifier.animateContentSize(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(text = stringResource(Res.string.repeat))

                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                            ) {
                                HabitRepeatMode.entries.forEach { repeatMode ->
                                    ToggleButton(
                                        checked = newHabit.repeatMode == repeatMode,
                                        onCheckedChange = {
                                            updateHabit(newHabit.copy(repeatMode = repeatMode))
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                                        content = {
                                            Text(
                                                text =
                                                    stringResource(
                                                        when (repeatMode) {
                                                            HabitRepeatMode.WEEKLY ->
                                                                Res.string.weekly
                                                            HabitRepeatMode.CUSTOM ->
                                                                Res.string.custom
                                                        }
                                                    )
                                            )
                                        },
                                    )
                                }
                            }

                            if (newHabit.repeatMode == HabitRepeatMode.WEEKLY) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = stringResource(Res.string.select_days))

                                    Row(
                                        horizontalArrangement =
                                            Arrangement.spacedBy(
                                                ButtonGroupDefaults.ConnectedSpaceBetween
                                            )
                                    ) {
                                        DayOfWeek.entries.forEach { dayOfWeek ->
                                            ToggleButton(
                                                checked = newHabit.days.contains(dayOfWeek),
                                                onCheckedChange = {
                                                    updateHabit(
                                                        newHabit.copy(
                                                            days =
                                                                if (it) {
                                                                    newHabit.days + dayOfWeek
                                                                } else {
                                                                    newHabit.days - dayOfWeek
                                                                }
                                                        )
                                                    )
                                                },
                                                enabled =
                                                    !(newHabit.days.size == 1 &&
                                                        newHabit.days.contains(dayOfWeek)),
                                                modifier = Modifier.weight(1f),
                                                colors =
                                                    ToggleButtonDefaults.tonalToggleButtonColors(),
                                                content = { Text(text = dayOfWeek.name.take(1)) },
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = intervalText,
                                        onValueChange = { intervalText = it.filter(Char::isDigit) },
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.medium,
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done,
                                            ),
                                        label = { Text(text = stringResource(Res.string.every)) },
                                        supportingText = {
                                            if (!hasValidCustomInterval) {
                                                Text(
                                                    text =
                                                        stringResource(
                                                            Res.string.interval_must_be_positive
                                                        ),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        },
                                        isError = !hasValidCustomInterval,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Row(
                                        horizontalArrangement =
                                            Arrangement.spacedBy(
                                                ButtonGroupDefaults.ConnectedSpaceBetween
                                            )
                                    ) {
                                        HabitIntervalUnit.entries.forEach { intervalUnit ->
                                            ToggleButton(
                                                checked = newHabit.intervalUnit == intervalUnit,
                                                onCheckedChange = {
                                                    updateHabit(
                                                        newHabit.copy(intervalUnit = intervalUnit)
                                                    )
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors =
                                                    ToggleButtonDefaults.tonalToggleButtonColors(),
                                                content = {
                                                    Text(
                                                        text =
                                                            stringResource(
                                                                when (intervalUnit) {
                                                                    HabitIntervalUnit.DAY ->
                                                                        Res.string.days_unit
                                                                    HabitIntervalUnit.WEEK ->
                                                                        Res.string.weeks_unit
                                                                    HabitIntervalUnit.MONTH ->
                                                                        Res.string.months_unit
                                                                    HabitIntervalUnit.YEAR ->
                                                                        Res.string.years_unit
                                                                }
                                                            ),
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Clip,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showStartDate) {
                        ListItem(
                            colors = listItemColors(),
                            modifier =
                                Modifier.clip(
                                    if (showReminder) middleItemShape() else endItemShape()
                                ),
                            headlineContent = {
                                Text(
                                    text = newHabit.time.date.toFormattedString(),
                                    style =
                                        MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = flexFontRounded()
                                        ),
                                )
                            },
                            supportingContent = { Text(text = stringResource(Res.string.start_date)) },
                            leadingContent = {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.calendar_month),
                                    contentDescription = "Start Date",
                                )
                            },
                            trailingContent = {
                                FilledTonalIconButton(onClick = { startDatePickerDialog = true }) {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.edit),
                                        contentDescription = "Pick Start Date",
                                    )
                                }
                            },
                        )
                    }

                    if (showReminder) {
                        ListItem(
                            colors = listItemColors(),
                            modifier =
                                Modifier.clip(
                                    if (showReminderTime) middleItemShape() else endItemShape()
                                ),
                            headlineContent = {
                                Text(text = stringResource(Res.string.add_reminder))
                            },
                            supportingContent = {
                                Text(
                                    text =
                                        stringResource(
                                            if (showStartDate) {
                                                Res.string.add_reminder_desc_schedule
                                            } else {
                                                Res.string.add_reminder_desc
                                            }
                                        ),
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee(),
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.alarm),
                                    contentDescription = "Alarm Icon",
                                )
                            },
                            trailingContent = {
                                ExpressiveSwitch(
                                    checked = newHabit.reminder,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (notificationPermission) {
                                                updateHabit(newHabit.copy(reminder = true))
                                            } else {
                                                onRequestPermission()
                                            }
                                        } else {
                                            updateHabit(newHabit.copy(reminder = false))
                                        }
                                    },
                                )
                            },
                        )

                        if (showReminderTime) {
                            ListItem(
                                colors = listItemColors(),
                                modifier = Modifier.clip(endItemShape()),
                                headlineContent = {
                                    Text(
                                        text =
                                            newHabit.time.time.toFormattedString(is24Hr = is24Hr),
                                        style =
                                            MaterialTheme.typography.titleLarge.copy(
                                                fontFamily = flexFontRounded()
                                            ),
                                    )
                                },
                                trailingContent = {
                                    FilledTonalIconButton(onClick = { timePickerDialog = true }) {
                                        Icon(
                                            imageVector = vectorResource(Res.drawable.edit),
                                            contentDescription = "Pick Time",
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onUpsertHabit(
                            newHabit.copy(
                                title = titleTextFieldState.text.toString(),
                                description = descTextFieldState.text.toString(),
                                intervalValue = parsedInterval ?: newHabit.intervalValue,
                            )
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth(),
                    enabled =
                        descTextFieldState.text.length <= 50 &&
                            titleTextFieldState.text.length <= 20 &&
                            titleTextFieldState.text.isNotBlank() &&
                            hasValidSchedule,
                ) {
                    Text(
                        text =
                            stringResource(
                                if (isEditSheet) {
                                    Res.string.save
                                } else {
                                    Res.string.add_habit
                                }
                            )
                    )
                }
            }
        }

        if (timePickerDialog) {
            val timePickerState =
                rememberTimePickerState(
                    initialHour = newHabit.time.hour,
                    initialMinute = newHabit.time.minute,
                    is24Hour = is24Hr,
                )

            GritTimePicker(
                onDismissRequest = { timePickerDialog = false },
                state = timePickerState,
                onConfirm = {
                    updateHabit(
                        newHabit.copy(
                            time =
                                LocalDateTime(
                                    date = newHabit.time.date,
                                    time =
                                        LocalTime(
                                            minute = timePickerState.minute,
                                            hour = timePickerState.hour,
                                        ),
                                )
                        )
                    )
                    timePickerDialog = false
                },
            )
        }

        if (startDatePickerDialog) {
            val datePickerState =
                rememberDatePickerState(
                    initialSelectedDateMillis =
                        newHabit.time.date.toEpochDays() * 24L * 60L * 60L * 1000L
                )

            DatePickerDialog(
                onDismissRequest = { startDatePickerDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedDate =
                                datePickerState.selectedDateMillis?.let {
                                    Instant.fromEpochMilliseconds(it)
                                        .toLocalDateTime(TimeZone.UTC)
                                        .date
                                } ?: return@TextButton

                            updateHabit(
                                newHabit.copy(
                                    time = LocalDateTime(date = selectedDate, time = newHabit.time.time)
                                )
                            )
                            startDatePickerDialog = false
                        },
                        enabled = datePickerState.selectedDateMillis != null,
                    ) {
                        Text(text = stringResource(Res.string.done))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { startDatePickerDialog = false }) {
                        Text(text = stringResource(Res.string.cancel))
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    GritTheme {
        HabitUpsertSheet(
            habit =
                Habit(
                    id = 1,
                    title = "New Habit",
                    description = "A new Habit",
                    time = LocalDateTime.now(),
                    days = DayOfWeek.entries.toSet(),
                    index = 1,
                    reminder = false,
                    repeatMode = HabitRepeatMode.CUSTOM,
                    intervalUnit = HabitIntervalUnit.DAY,
                    intervalValue = 3,
                ),
            onDismissRequest = {},
            onUpsertHabit = {},
            is24Hr = true,
            isEditSheet = true,
        )
    }
}
