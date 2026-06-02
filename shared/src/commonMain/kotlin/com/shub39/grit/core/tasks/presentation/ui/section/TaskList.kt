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
package com.shub39.grit.core.tasks.presentation.ui.section

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.LocalWindowSizeClass
import com.shub39.grit.core.now
import com.shub39.grit.core.shared_ui.PageFill
import com.shub39.grit.core.tasks.domain.Category
import com.shub39.grit.core.tasks.domain.CategoryColors
import com.shub39.grit.core.tasks.domain.Task
import com.shub39.grit.core.tasks.domain.hasExplicitSpan
import com.shub39.grit.core.tasks.domain.resolvedEndAt
import com.shub39.grit.core.tasks.presentation.TaskAction
import com.shub39.grit.core.tasks.presentation.TaskState
import com.shub39.grit.core.tasks.presentation.ui.component.CategoryUpsertSheet
import com.shub39.grit.core.tasks.presentation.ui.component.TaskUpsertSheet
import com.shub39.grit.core.theme.flexFontEmphasis
import com.shub39.grit.core.theme.flexFontRounded
import com.shub39.grit.core.toFormattedString
import grit.shared.generated.resources.Res
import grit.shared.generated.resources.add
import grit.shared.generated.resources.add_category
import grit.shared.generated.resources.arrow_back
import grit.shared.generated.resources.arrow_forward
import grit.shared.generated.resources.calendar_month
import grit.shared.generated.resources.cancel
import grit.shared.generated.resources.completed
import grit.shared.generated.resources.done
import grit.shared.generated.resources.edit
import grit.shared.generated.resources.edit_categories
import grit.shared.generated.resources.tasks
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun TaskList(state: TaskState, onAction: (TaskAction) -> Unit, onEditCategories: () -> Unit) =
    PageFill {
        val windowSizeClass = LocalWindowSizeClass.current
        val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        var showDatePicker by remember { mutableStateOf(false) }
        var showTaskAddSheet by remember { mutableStateOf(false) }
        var showCategoryAddSheet by remember { mutableStateOf(false) }
        var editTask: Task? by remember { mutableStateOf(null) }

        val selectedCategory = state.currentCategory
        val selectedTasks =
            remember(state.tasks, selectedCategory, selectedDate) {
                state.tasks[selectedCategory]
                    .orEmpty()
                    .filter { it.reminder?.date == selectedDate }
                    .sortedWith(
                        compareBy<Task> { it.reminder?.time ?: LocalTime(23, 59) }
                            .thenBy { it.index }
                    )
            }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
        ) {
            TaskTopAppBar(
                completedTodayCount = selectedTasks.count { it.status },
                totalTodayCount = selectedTasks.size,
            )

            CategorySelector(
                state = state,
                onAction = onAction,
                onAddCategoryClick = { showCategoryAddSheet = true },
                onEditCategoriesClick = onEditCategories,
                isExpanded = isExpanded,
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = if (isExpanded) 16.dp else 0.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    DateNavigator(
                        selectedDate = selectedDate,
                        onPreviousDay = { selectedDate = selectedDate.plus(-1, DateTimeUnit.DAY) },
                        onNextDay = { selectedDate = selectedDate.plus(1, DateTimeUnit.DAY) },
                        onDateClick = { showDatePicker = true },
                        onAddTaskClick = { showTaskAddSheet = true },
                        addEnabled = selectedCategory != null,
                    )

                    TimelineScaffold(
                        scrollKey = selectedDate,
                        tasks = selectedTasks,
                        is24Hour = state.is24Hour,
                        onToggleTask = { task ->
                            onAction(TaskAction.UpsertTask(task.copy(status = !task.status)))
                        },
                        onEditTask = { editTask = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (showCategoryAddSheet) {
            CategoryUpsertSheet(
                onDismiss = { showCategoryAddSheet = false },
                category = Category(name = "", color = CategoryColors.GRAY.color),
                onUpsertCategory = {
                    onAction(TaskAction.AddCategory(it))
                    showCategoryAddSheet = false
                },
            )
        }

        if (showTaskAddSheet && selectedCategory != null) {
            TaskUpsertSheet(
                task =
                    Task(
                        categoryId = selectedCategory.id,
                        title = "",
                        index = state.tasks[selectedCategory]?.size ?: 0,
                        status = false,
                        reminder = LocalDateTime(selectedDate, LocalTime.now()),
                    ),
                is24Hr = state.is24Hour,
                categories = state.tasks.keys.toList(),
                onDismissRequest = { showTaskAddSheet = false },
                onUpsert = { onAction(TaskAction.UpsertTask(it)) },
                onDelete = {},
            )
        }

        if (editTask != null) {
            TaskUpsertSheet(
                task = editTask!!,
                categories = state.tasks.keys.toList(),
                onDismissRequest = { editTask = null },
                isEditSheet = true,
                is24Hr = state.is24Hour,
                onUpsert = { onAction(TaskAction.UpsertTask(it)) },
                onDelete = {
                    editTask?.let { onAction(TaskAction.DeleteTask(it)) }
                    editTask = null
                },
            )
        }

        if (showDatePicker) {
            val datePickerState =
                rememberDatePickerState(initialSelectedDateMillis = selectedDate.toDatePickerMillis())

            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedDateMillis = datePickerState.selectedDateMillis
                            if (selectedDateMillis != null) {
                                selectedDate = selectedDateMillis.toDatePickerLocalDate()
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
    }

@Composable
private fun TaskTopAppBar(completedTodayCount: Int, totalTodayCount: Int) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)

    LaunchedEffect(topAppBarState.heightOffsetLimit) {
        if (topAppBarState.heightOffsetLimit != -Float.MAX_VALUE) {
            topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        }
    }

    LargeFlexibleTopAppBar(
        scrollBehavior = scrollBehavior,
        colors =
            TopAppBarDefaults.topAppBarColors(
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            ),
        title = { Text(text = stringResource(Res.string.tasks), fontFamily = flexFontEmphasis()) },
        subtitle = {
            Text(
                text = "$completedTodayCount/$totalTodayCount " + stringResource(Res.string.completed),
                fontFamily = flexFontRounded(),
            )
        },
    )
}

@Composable
private fun CategorySelector(
    state: TaskState,
    onAction: (TaskAction) -> Unit,
    onAddCategoryClick: () -> Unit,
    onEditCategoriesClick: () -> Unit,
    isExpanded: Boolean,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
    ) {
        if (!isExpanded) {
            items(state.tasks.keys.toList(), key = { it.id }) { category ->
                ToggleButton(
                    checked = category == state.currentCategory,
                    onCheckedChange = { onAction(TaskAction.ChangeCategory(category)) },
                ) {
                    Text(text = category.name)
                }
            }
            item {
                Spacer(modifier = Modifier.width(4.dp))
                FilledTonalIconButton(onClick = onAddCategoryClick) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.add),
                        contentDescription = "Add Category",
                    )
                }
                FilledTonalIconButton(onClick = onEditCategoriesClick) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.edit),
                        contentDescription = "Edit Categories",
                    )
                }
            }
        } else {
            item {
                FilledTonalButton(onClick = onAddCategoryClick) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.add),
                        contentDescription = "Add Category",
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(Res.string.add_category))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onEditCategoriesClick) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.edit),
                        contentDescription = "Edit Categories",
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(Res.string.edit_categories))
                }
            }
        }
    }
}

@Composable
private fun DateNavigator(
    selectedDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onDateClick: () -> Unit,
    onAddTaskClick: () -> Unit,
    addEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            modifier =
                Modifier.weight(1f)
                    .height(38.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(onClick = onDateClick),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.calendar_month),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = selectedDate.toFormattedString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        FilledTonalIconButton(
            onClick = onAddTaskClick,
            enabled = addEnabled,
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.add),
                contentDescription = "Add Task",
                modifier = Modifier.size(21.dp),
            )
        }

        FilledTonalIconButton(onClick = onPreviousDay, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = vectorResource(Res.drawable.arrow_back),
                contentDescription = "Previous Day",
                modifier = Modifier.size(21.dp),
            )
        }

        FilledTonalIconButton(onClick = onNextDay, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = vectorResource(Res.drawable.arrow_forward),
                contentDescription = "Next Day",
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun TimelineScaffold(
    scrollKey: LocalDate,
    tasks: List<Task>,
    is24Hour: Boolean,
    onToggleTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourHeight = 48.dp
    val placements = remember(tasks) { tasks.toTimelinePlacements() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(scrollKey) {
        delay(80)

        val viewportHeight = listState.layoutInfo.viewportSize.height
        if (viewportHeight <= 0) return@LaunchedEffect

        val hourHeightPx = with(density) { hourHeight.toPx() }
        val currentMinuteOffset = LocalTime.now().toMinuteOfDay() * (hourHeightPx / 60f)
        val dayHeightPx = hourHeightPx * 24f
        val maxScroll = max(0, (dayHeightPx - viewportHeight).roundToInt())
        val targetScroll =
            (currentMinuteOffset - (viewportHeight / 2f))
                .roundToInt()
                .coerceIn(0, maxScroll)

        listState.scrollToItem(0, targetScroll)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
    ) {
        item {
            TimelineCanvas(
                hourHeight = hourHeight,
                placements = placements,
                is24Hour = is24Hour,
                onToggleTask = onToggleTask,
                onEditTask = onEditTask,
            )
        }
    }
}

@Composable
private fun TimelineCanvas(
    hourHeight: Dp,
    placements: List<TimelineTaskPlacement>,
    is24Hour: Boolean,
    onToggleTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
) {
    val labelWidth = 40.dp
    val axisGap = 4.dp
    val laneGap = 4.dp
    val taskAreaStart = labelWidth + axisGap + 6.dp
    val dayHeight = hourHeight * 24f
    val minuteHeight = hourHeight / 60f

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(dayHeight)) {
        val taskAreaWidth = maxWidth - taskAreaStart

        repeat(24) { hour ->
            val top = hourHeight * hour.toFloat()

            Text(
                text = LocalTime(hour, 0).toFormattedString(is24Hour).trim(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = top + 4.dp).width(labelWidth),
            )

            Box(
                modifier =
                    Modifier.offset(x = labelWidth + axisGap, y = top)
                        .width(2.dp)
                        .height(hourHeight)
                        .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Box(
                modifier =
                    Modifier.offset(x = labelWidth + axisGap, y = top)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )
        }

        Box(
            modifier =
                Modifier.offset(x = labelWidth + axisGap, y = dayHeight)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )

        placements.forEach { placement ->
            val columnWidth =
                (taskAreaWidth - (laneGap * (placement.columnCount - 1).toFloat())) /
                    placement.columnCount.toFloat()
            val x = taskAreaStart + ((columnWidth + laneGap) * placement.column.toFloat())
            val y = minuteHeight * placement.startMinute.toFloat()
            val height =
                maxOf(
                    44.dp,
                    minuteHeight * (placement.endMinute - placement.startMinute).toFloat(),
                )

            TimelineTaskBlock(
                placement = placement,
                is24Hour = is24Hour,
                onToggleTask = onToggleTask,
                onEditTask = onEditTask,
                modifier = Modifier.offset(x = x, y = y).width(columnWidth).height(height),
            )
        }
    }
}

@Composable
private fun TimelineTaskBlock(
    placement: TimelineTaskPlacement,
    is24Hour: Boolean,
    onToggleTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = placement.task
    val activeColors =
        listOf(
            Color(0xFF9CE8F3) to Color(0xFF20393D),
            Color(0xFFD9E1FF) to Color(0xFF27304F),
            Color(0xFFD9EED3) to Color(0xFF243B27),
            Color(0xFFFFDFB9) to Color(0xFF4A321D),
            Color(0xFFEAD8FF) to Color(0xFF3B2B4F),
            Color(0xFFFFD7E6) to Color(0xFF4B2836),
        )
    val activeColorPair = activeColors[placement.colorIndex % activeColors.size]
    val containerColor =
        if (task.status) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            activeColorPair.first
        }
    val contentColor =
        if (task.status) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            activeColorPair.second
        }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        modifier =
            modifier.combinedClickable(
                onClick = { onToggleTask(task) },
                onLongClick = { onEditTask(task) },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = task.title,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 18.sp,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration =
                        if (task.status) TextDecoration.LineThrough else TextDecoration.None,
                )
                task.timeRangeLabel(is24Hour)?.let { timeRange ->
                    Text(
                        text = timeRange,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                            ),
                        maxLines = 1,
                    )
                }
            }

            task.durationLabel()?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun Task.timeRangeLabel(is24Hour: Boolean): String? {
    if (!hasExplicitSpan() || timelineDurationMinutes() < 60) return null

    val start = reminder ?: return null
    val end = resolvedEndAt() ?: return null

    return "${start.time.toFormattedString(is24Hour).trim()}-${end.time.toFormattedString(is24Hour).trim()}"
}

private data class TimelineTaskPlacement(
    val task: Task,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
    val colorIndex: Int,
)

private data class TimelineTaskSeed(
    val task: Task,
    val startMinute: Int,
    val endMinute: Int,
    var column: Int = 0,
)

private fun List<Task>.toTimelinePlacements(): List<TimelineTaskPlacement> {
    val seeds =
        mapNotNull { task ->
                val start = task.reminder ?: return@mapNotNull null
                val startMinute = start.time.toMinuteOfDay()
                val duration = task.timelineDurationMinutes()
                val endMinute = min(24 * 60, startMinute + duration).coerceAtLeast(startMinute + 1)

                TimelineTaskSeed(
                    task = task,
                    startMinute = startMinute,
                    endMinute = endMinute,
                )
            }
            .sortedWith(compareBy<TimelineTaskSeed> { it.startMinute }.thenBy { it.endMinute })

    val active = mutableListOf<TimelineTaskSeed>()
    seeds.forEach { seed ->
        active.removeAll { it.endMinute <= seed.startMinute }
        val usedColumns = active.map { it.column }.toSet()
        seed.column = generateSequence(0) { it + 1 }.first { it !in usedColumns }
        active += seed
    }

    return seeds.mapIndexed { index, seed ->
        val overlappingSeeds =
            seeds.filter { other ->
                seed.startMinute < other.endMinute && other.startMinute < seed.endMinute
            }
        val columnCount = max(1, overlappingSeeds.maxOfOrNull { it.column + 1 } ?: 1)

        TimelineTaskPlacement(
            task = seed.task,
            startMinute = seed.startMinute,
            endMinute = seed.endMinute,
            column = seed.column,
            columnCount = columnCount,
            colorIndex = index,
        )
    }
}

private fun Task.timelineDurationMinutes(): Int {
    val start = reminder ?: return 60
    val end = resolvedEndAt() ?: return 60
    val minutes =
        (end.toInstant(TimeZone.currentSystemDefault()) -
                start.toInstant(TimeZone.currentSystemDefault()))
            .inWholeMinutes
            .toInt()

    return minutes.takeIf { it > 0 } ?: 60
}

private fun LocalTime.toMinuteOfDay(): Int = (hour * 60) + minute

private fun Task.durationLabel(): String? {
    if (!hasExplicitSpan()) return null

    val minutes =
        durationMinutes
            ?: run {
                val start = reminder ?: return null
                val end = resolvedEndAt() ?: return null
                (end.toInstant(TimeZone.currentSystemDefault()) -
                        start.toInstant(TimeZone.currentSystemDefault()))
                    .inWholeMinutes
                    .toInt()
                    .takeIf { it > 0 }
            }
            ?: return null

    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun LocalDate.toDatePickerMillis(): Long = toEpochDays() * 86_400_000L

private fun Long.toDatePickerLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
