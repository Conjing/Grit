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
package com.shub39.grit.core.tasks.presentation.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.now
import com.shub39.grit.core.shared_ui.GritBottomSheet
import com.shub39.grit.core.shared_ui.GritTimePicker
import com.shub39.grit.core.shared_ui.detachedItemShape
import com.shub39.grit.core.shared_ui.listItemColors
import com.shub39.grit.core.tasks.domain.Category
import com.shub39.grit.core.tasks.domain.Task
import com.shub39.grit.core.tasks.domain.TaskTimeMode
import com.shub39.grit.core.theme.flexFontEmphasis
import com.shub39.grit.core.toFormattedString
import grit.shared.generated.resources.Res
import grit.shared.generated.resources.add
import grit.shared.generated.resources.add_task
import grit.shared.generated.resources.calendar_month
import grit.shared.generated.resources.cancel
import grit.shared.generated.resources.close
import grit.shared.generated.resources.delete
import grit.shared.generated.resources.done
import grit.shared.generated.resources.edit
import grit.shared.generated.resources.edit_task
import grit.shared.generated.resources.schedule
import grit.shared.generated.resources.save
import grit.shared.generated.resources.task_date
import grit.shared.generated.resources.task_duration_hours
import grit.shared.generated.resources.task_duration_minutes
import grit.shared.generated.resources.task_end_after_start_error
import grit.shared.generated.resources.task_end_time
import grit.shared.generated.resources.task_optional_time_note
import grit.shared.generated.resources.task_start_time
import grit.shared.generated.resources.task_time_mode_duration
import grit.shared.generated.resources.task_time_mode_end
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
expect fun TaskUpsertSheet(
    task: Task,
    categories: List<Category>,
    onDismissRequest: () -> Unit,
    onUpsert: (Task) -> Unit,
    onDelete: () -> Unit,
    is24Hr: Boolean,
    modifier: Modifier = Modifier,
    isEditSheet: Boolean = false,
)

@Composable
fun TaskUpsertSheetContent(
    task: Task,
    categories: List<Category>,
    onDismissRequest: () -> Unit,
    onUpsert: (Task) -> Unit,
    onDelete: () -> Unit,
    is24Hr: Boolean,
    isEditSheet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val defaultStart = remember(task) { task.reminder ?: LocalDateTime.now() }
    val originalTask = remember(task) { task.normalizeForEditor(defaultStart) }

    var newTask by remember(task) { mutableStateOf(originalTask) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val textFieldState =
        rememberTextFieldState(
            initialText = originalTask.title,
            initialSelection = TextRange(originalTask.title.length),
        )

    var durationHoursText by
        remember(task) {
            mutableStateOf(
                originalTask.durationMinutes
                    ?.takeIf { it >= 60 }
                    ?.let { (it / 60).toString() }
                    .orEmpty()
            )
        }
    var durationMinutesText by
        remember(task) {
            mutableStateOf(
                originalTask.durationMinutes
                    ?.let { it % 60 }
                    ?.takeIf { it > 0 }
                    ?.toString()
                    .orEmpty()
            )
        }

    val startAt = newTask.reminder ?: defaultStart
    val normalizedDuration = durationInputToMinutes(durationHoursText, durationMinutesText)
    val normalizedEnd = newTask.endAt?.let { LocalDateTime(startAt.date, it.time) }
    val endTimeValid = normalizedEnd == null || normalizedEnd > startAt
    val saveCandidate =
        newTask.copy(
            title = textFieldState.text.toString(),
            reminder = startAt,
            durationMinutes = if (newTask.timeMode == TaskTimeMode.DURATION) normalizedDuration else null,
            endAt = if (newTask.timeMode == TaskTimeMode.END_TIME && endTimeValid) normalizedEnd else null,
        )
    val isChanged = saveCandidate != originalTask

    GritBottomSheet(
        modifier = modifier.imePadding(),
        padding = 0.dp,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialShapes.Pill.toShape(),
                        ),
            ) {
                Icon(
                    imageVector =
                        vectorResource(if (isEditSheet) Res.drawable.edit else Res.drawable.add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Text(
                text = stringResource(if (isEditSheet) Res.string.edit_task else Res.string.add_task),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = flexFontEmphasis()),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    categories.forEach { category ->
                        ToggleButton(
                            checked = category.id == newTask.categoryId,
                            onCheckedChange = { newTask = newTask.copy(categoryId = category.id) },
                            colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                            content = { Text(category.name) },
                        )
                    }
                }
            }

            item {
                val keyboardController = LocalSoftwareKeyboardController.current
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(Unit) {
                    delay(400.milliseconds)
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }

                OutlinedTextField(
                    state = textFieldState,
                    shape = MaterialTheme.shapes.medium,
                    placeholder = { Text(text = stringResource(Res.string.add_task)) },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.None,
                        ),
                    onKeyboardAction = { defaultAction ->
                        textFieldState.edit { append("\n") }
                        defaultAction()
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }

            item {
                ListItem(
                    modifier = Modifier.clip(detachedItemShape()).clickable { showDatePicker = true },
                    colors = listItemColors(),
                    leadingContent = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.calendar_month),
                            contentDescription = null,
                        )
                    },
                    headlineContent = { Text(text = stringResource(Res.string.task_date)) },
                    supportingContent = { Text(text = startAt.date.toFormattedString()) },
                )
            }

            item {
                ListItem(
                    modifier = Modifier.clip(detachedItemShape()).clickable { showStartTimePicker = true },
                    colors = listItemColors(),
                    leadingContent = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.schedule),
                            contentDescription = null,
                        )
                    },
                    headlineContent = { Text(text = stringResource(Res.string.task_start_time)) },
                    supportingContent = { Text(text = startAt.time.toFormattedString(is24Hr)) },
                )
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        checked = newTask.timeMode == TaskTimeMode.DURATION,
                        onCheckedChange = { newTask = newTask.copy(timeMode = TaskTimeMode.DURATION) },
                        colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                    ) {
                        Text(stringResource(Res.string.task_time_mode_duration))
                    }

                    ToggleButton(
                        checked = newTask.timeMode == TaskTimeMode.END_TIME,
                        onCheckedChange = { newTask = newTask.copy(timeMode = TaskTimeMode.END_TIME) },
                        colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                    ) {
                        Text(stringResource(Res.string.task_time_mode_end))
                    }
                }
            }

            if (newTask.timeMode == TaskTimeMode.DURATION) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = durationHoursText,
                            onValueChange = { durationHoursText = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(Res.string.task_duration_hours)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = durationMinutesText,
                            onValueChange = {
                                durationMinutesText = it.filter(Char::isDigit).take(2)
                            },
                            label = { Text(stringResource(Res.string.task_duration_minutes)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
            } else {
                item {
                    ListItem(
                        modifier = Modifier.clip(detachedItemShape()).clickable { showEndTimePicker = true },
                        colors = listItemColors(),
                        leadingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.schedule),
                                contentDescription = null,
                            )
                        },
                        headlineContent = { Text(text = stringResource(Res.string.task_end_time)) },
                        supportingContent = {
                            normalizedEnd?.time?.let { Text(text = it.toFormattedString(is24Hr)) }
                        },
                        trailingContent = {
                            if (normalizedEnd != null) {
                                IconButton(onClick = { newTask = newTask.copy(endAt = null) }) {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.close),
                                        contentDescription = null,
                                    )
                                }
                            }
                        },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(Res.string.task_optional_time_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!endTimeValid) {
                item {
                    Text(
                        text = stringResource(Res.string.task_end_after_start_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isEditSheet) {
                            OutlinedButton(
                                onClick = onDelete,
                                shapes =
                                    ButtonShapes(
                                        shape = MaterialTheme.shapes.extraLarge,
                                        pressedShape = MaterialTheme.shapes.small,
                                    ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(Res.string.delete))
                            }
                        }

                        Button(
                            onClick = {
                                onUpsert(saveCandidate)
                                onDismissRequest()
                            },
                            shapes =
                                ButtonShapes(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    pressedShape = MaterialTheme.shapes.small,
                                ),
                            modifier = Modifier.weight(1f),
                            enabled =
                                textFieldState.text.isNotBlank() &&
                                    textFieldState.text.length <= 100 &&
                                    endTimeValid &&
                                    isChanged,
                        ) {
                            Text(
                                stringResource(
                                    if (isEditSheet) Res.string.save else Res.string.add_task
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(initialSelectedDateMillis = startAt.date.toDatePickerMillis())

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDateMillis = datePickerState.selectedDateMillis
                        if (selectedDateMillis != null) {
                            val selectedDate = selectedDateMillis.toDatePickerLocalDate()
                            val updatedStart = LocalDateTime(selectedDate, startAt.time)
                            newTask = newTask.withStartAt(updatedStart)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }

    if (showStartTimePicker) {
        val startTimePickerState =
            rememberTimePickerState(
                initialHour = startAt.time.hour,
                initialMinute = startAt.time.minute,
                is24Hour = is24Hr,
            )

        GritTimePicker(
            onDismissRequest = { showStartTimePicker = false },
            state = startTimePickerState,
            onConfirm = {
                newTask =
                    newTask.withStartAt(
                        LocalDateTime(
                            date = startAt.date,
                            time = LocalTime(startTimePickerState.hour, startTimePickerState.minute),
                        )
                    )
                showStartTimePicker = false
            },
        )
    }

    if (showEndTimePicker) {
        val fallbackEnd = normalizedEnd?.time ?: startAt.time
        val endTimePickerState =
            rememberTimePickerState(
                initialHour = fallbackEnd.hour,
                initialMinute = fallbackEnd.minute,
                is24Hour = is24Hr,
            )

        GritTimePicker(
            onDismissRequest = { showEndTimePicker = false },
            state = endTimePickerState,
            onConfirm = {
                newTask =
                    newTask.copy(
                        endAt =
                            LocalDateTime(
                                date = startAt.date,
                                time = LocalTime(endTimePickerState.hour, endTimePickerState.minute),
                            )
                    )
                showEndTimePicker = false
            },
        )
    }
}

private fun Task.normalizeForEditor(defaultStart: LocalDateTime): Task {
    val start = reminder ?: defaultStart
    val normalizedDuration = durationMinutes?.takeIf { it > 0 }
    val normalizedEnd = endAt?.let { LocalDateTime(start.date, it.time) }

    return copy(
        reminder = start,
        durationMinutes = normalizedDuration,
        endAt = normalizedEnd?.takeIf { it > start },
    )
}

private fun Task.withStartAt(updatedStartAt: LocalDateTime): Task =
    copy(
        reminder = updatedStartAt,
        endAt = endAt?.let { LocalDateTime(updatedStartAt.date, it.time) },
    )

private fun durationInputToMinutes(hoursText: String, minutesText: String): Int? {
    val hours = hoursText.toIntOrNull() ?: 0
    val minutes = minutesText.toIntOrNull() ?: 0
    val totalMinutes = (hours * 60) + minutes

    return totalMinutes.takeIf { it > 0 }
}

private fun LocalDate.toDatePickerMillis(): Long = toEpochDays() * 86_400_000L

private fun Long.toDatePickerLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
