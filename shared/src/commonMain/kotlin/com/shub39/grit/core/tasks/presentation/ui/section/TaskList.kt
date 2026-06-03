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
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import grit.shared.generated.resources.zoom_in
import grit.shared.generated.resources.zoom_out
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

private val TimelineMinimumTaskHeight = 40.dp
private val TimelineTopPadding = 10.dp
private val TimelineLabelMinHeight = 20.dp
private val TimelineBottomContentPadding = 96.dp

private enum class TimelineZoomLevel(
    val hourHeight: Dp,
    val minorTickMinutes: Int,
    val label: String,
) {
    HOUR(hourHeight = 48.dp, minorTickMinutes = 60, label = "1h"),
    HALF_HOUR(hourHeight = 72.dp, minorTickMinutes = 30, label = "30m"),
    QUARTER_HOUR(hourHeight = 120.dp, minorTickMinutes = 15, label = "15m"),
    TEN_MINUTES(hourHeight = 168.dp, minorTickMinutes = 10, label = "10m"),
}

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
        var zoomLevelIndex by remember { mutableIntStateOf(0) }
        var pendingZoomCenterMinute by remember { mutableStateOf<Float?>(null) }
        var resolveTimelineCenterMinute by remember { mutableStateOf<(() -> Float?)?>(null) }
        var zoomIndicatorNonce by remember { mutableIntStateOf(0) }
        var zoomAnchorNonce by remember { mutableIntStateOf(0) }
        var showZoomIndicator by remember { mutableStateOf(false) }
        var hasAppliedPersistedZoom by remember { mutableStateOf(false) }

        LaunchedEffect(state.taskUiPrefsLoaded, state.timelineZoomLevelIndex) {
            if (state.taskUiPrefsLoaded && !hasAppliedPersistedZoom) {
                zoomLevelIndex = state.timelineZoomLevelIndex.coerceIn(0, TimelineZoomLevel.entries.lastIndex)
                hasAppliedPersistedZoom = true
            }
        }

        LaunchedEffect(zoomIndicatorNonce) {
            if (zoomIndicatorNonce == 0) return@LaunchedEffect
            showZoomIndicator = true
            delay(1100)
            showZoomIndicator = false
        }

        LaunchedEffect(zoomAnchorNonce) {
            if (zoomAnchorNonce == 0) return@LaunchedEffect
            delay(180)
            pendingZoomCenterMinute = null
        }

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
                        onZoomIn = {
                            pendingZoomCenterMinute =
                                pendingZoomCenterMinute ?: resolveTimelineCenterMinute?.invoke()
                            zoomAnchorNonce++
                            zoomIndicatorNonce++
                            val nextZoomLevelIndex =
                                (zoomLevelIndex + 1).coerceAtMost(TimelineZoomLevel.entries.lastIndex)
                            zoomLevelIndex = nextZoomLevelIndex
                            onAction(TaskAction.ChangeTimelineZoomLevel(nextZoomLevelIndex))
                        },
                        onZoomOut = {
                            pendingZoomCenterMinute =
                                pendingZoomCenterMinute ?: resolveTimelineCenterMinute?.invoke()
                            zoomAnchorNonce++
                            zoomIndicatorNonce++
                            val nextZoomLevelIndex = (zoomLevelIndex - 1).coerceAtLeast(0)
                            zoomLevelIndex = nextZoomLevelIndex
                            onAction(TaskAction.ChangeTimelineZoomLevel(nextZoomLevelIndex))
                        },
                        addEnabled = selectedCategory != null,
                        zoomInEnabled = zoomLevelIndex < TimelineZoomLevel.entries.lastIndex,
                        zoomOutEnabled = zoomLevelIndex > 0,
                    )

                    TimelineScaffold(
                        tasks = selectedTasks,
                        is24Hour = state.is24Hour,
                        zoomLevel = TimelineZoomLevel.entries[zoomLevelIndex],
                        shouldAutoCenterCurrentTime = state.taskUiPrefsLoaded && hasAppliedPersistedZoom,
                        showZoomIndicator = showZoomIndicator,
                        pendingZoomCenterMinute = pendingZoomCenterMinute,
                        onPendingZoomCenterConsumed = {},
                        onResolveTimelineCenterMinute = { resolveTimelineCenterMinute = it },
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
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    addEnabled: Boolean,
    zoomInEnabled: Boolean,
    zoomOutEnabled: Boolean,
) {
    val navigatorButtonSize = 34.dp
    val navigatorIconSize = 18.dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val dateTextStyle =
            MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        val fullDateText = remember(selectedDate) { selectedDate.toFormattedString() }
        val compactDateText = remember(fullDateText) { fullDateText.substringBeforeLast(' ') }
        val textMeasurer = rememberTextMeasurer()

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            modifier =
                Modifier.weight(1f)
                    .heightIn(min = navigatorButtonSize)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(onClick = onDateClick),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableTextWidth =
                    (maxWidth - 18.dp - 15.dp - 5.dp).coerceAtLeast(0.dp)
                val fullDateFits =
                    with(LocalDensity.current) {
                        textMeasurer.measure(
                            text = AnnotatedString(fullDateText),
                            style = dateTextStyle,
                            maxLines = 1,
                        ).size.width <= availableTextWidth.roundToPx()
                    }
                val dateText = if (fullDateFits) fullDateText else compactDateText

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.calendar_month),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = dateText,
                        style = dateTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        FilledTonalIconButton(
            onClick = onZoomOut,
            enabled = zoomOutEnabled,
            modifier = Modifier.size(navigatorButtonSize),
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.zoom_out),
                contentDescription = "Zoom Out",
                modifier = Modifier.size(navigatorIconSize),
            )
        }

        FilledTonalIconButton(
            onClick = onZoomIn,
            enabled = zoomInEnabled,
            modifier = Modifier.size(navigatorButtonSize),
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.zoom_in),
                contentDescription = "Zoom In",
                modifier = Modifier.size(navigatorIconSize),
            )
        }

        FilledTonalIconButton(
            onClick = onAddTaskClick,
            enabled = addEnabled,
            modifier = Modifier.size(navigatorButtonSize),
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.add),
                contentDescription = "Add Task",
                modifier = Modifier.size(19.dp),
            )
        }

        FilledTonalIconButton(onClick = onPreviousDay, modifier = Modifier.size(navigatorButtonSize)) {
            Icon(
                imageVector = vectorResource(Res.drawable.arrow_back),
                contentDescription = "Previous Day",
                modifier = Modifier.size(19.dp),
            )
        }

        FilledTonalIconButton(onClick = onNextDay, modifier = Modifier.size(navigatorButtonSize)) {
            Icon(
                imageVector = vectorResource(Res.drawable.arrow_forward),
                contentDescription = "Next Day",
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun TimelineScaffold(
    tasks: List<Task>,
    is24Hour: Boolean,
    zoomLevel: TimelineZoomLevel,
    shouldAutoCenterCurrentTime: Boolean,
    showZoomIndicator: Boolean,
    pendingZoomCenterMinute: Float?,
    onPendingZoomCenterConsumed: () -> Unit,
    onResolveTimelineCenterMinute: (((() -> Float?)?) -> Unit),
    onToggleTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourHeight = zoomLevel.hourHeight
    val placements = remember(tasks) { tasks.toTimelinePlacements() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = LocalDensity.current
    var hasAutoCenteredCurrentTime by remember { mutableStateOf(false) }

    LaunchedEffect(shouldAutoCenterCurrentTime, hourHeight) {
        if (!shouldAutoCenterCurrentTime || hasAutoCenteredCurrentTime) return@LaunchedEffect
        delay(80)

        val viewportHeight = listState.layoutInfo.viewportSize.height
        if (viewportHeight <= 0) return@LaunchedEffect

        val hourHeightPx = with(density) { hourHeight.toPx() }
        val topPaddingPx = with(density) { TimelineTopPadding.toPx() }
        val bottomPaddingPx = with(density) { TimelineBottomContentPadding.toPx() }
        val currentMinuteOffset =
            topPaddingPx + (LocalTime.now().toMinuteOfDay() * (hourHeightPx / 60f))
        val fullContentHeightPx = topPaddingPx + (hourHeightPx * 24f) + bottomPaddingPx
        val maxScroll = max(0, (fullContentHeightPx - viewportHeight).roundToInt())
        val targetScroll =
            (currentMinuteOffset - (viewportHeight / 2f))
                .roundToInt()
                .coerceIn(0, maxScroll)

        listState.scrollToItem(0, targetScroll)
        hasAutoCenteredCurrentTime = true
    }

    DisposableEffect(listState, density, hourHeight) {
        onResolveTimelineCenterMinute {
            timelineCenterMinute(
                scrollOffsetPx = listState.firstVisibleItemScrollOffset.toFloat(),
                viewportHeightPx = listState.layoutInfo.viewportSize.height.toFloat(),
                hourHeightPx = with(density) { hourHeight.toPx() },
                topPaddingPx = with(density) { TimelineTopPadding.toPx() },
            )
        }

        onDispose { onResolveTimelineCenterMinute(null) }
    }

    SideEffect {
        val centerMinute = pendingZoomCenterMinute ?: return@SideEffect
        val currentHourHeightPx = with(density) { hourHeight.toPx() }
        val viewportHeight = listState.layoutInfo.viewportSize.height

        if (viewportHeight > 0) {
            val topPaddingPx = with(density) { TimelineTopPadding.toPx() }
            val bottomPaddingPx = with(density) { TimelineBottomContentPadding.toPx() }
            val clampedCenterMinute = centerMinute.coerceIn(0f, (24 * 60).toFloat())
            val fullContentHeightPx = topPaddingPx + (currentHourHeightPx * 24f) + bottomPaddingPx
            val maxScroll = max(0, (fullContentHeightPx - viewportHeight).roundToInt())
            val targetScroll =
                (
                    topPaddingPx +
                        (clampedCenterMinute * (currentHourHeightPx / 60f)) -
                        (viewportHeight / 2f)
                )
                    .roundToInt()
                    .coerceIn(0, maxScroll)

            listState.requestScrollToItem(0, targetScroll)
            onPendingZoomCenterConsumed()
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding =
                PaddingValues(start = 16.dp, end = 16.dp, bottom = TimelineBottomContentPadding),
        ) {
            item {
                TimelineCanvas(
                    hourHeight = hourHeight,
                    zoomLevel = zoomLevel,
                    placements = placements,
                    is24Hour = is24Hour,
                    onToggleTask = onToggleTask,
                    onEditTask = onEditTask,
                )
            }
        }

        if (showZoomIndicator) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 20.dp),
            ) {
                Text(
                    text = zoomLevel.label,
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun timelineCenterMinute(
    scrollOffsetPx: Float,
    viewportHeightPx: Float,
    hourHeightPx: Float,
    topPaddingPx: Float,
): Float? {
    if (viewportHeightPx <= 0f || hourHeightPx <= 0f) return null

    return ((scrollOffsetPx + (viewportHeightPx / 2f)) - topPaddingPx) / (hourHeightPx / 60f)
}

@Composable
private fun TimelineCanvas(
    hourHeight: Dp,
    zoomLevel: TimelineZoomLevel,
    placements: List<TimelineTaskPlacement>,
    is24Hour: Boolean,
    onToggleTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val majorLabelStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 14.sp,
        )
    val minorLabelStyle = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp)
    val labelWidth =
        with(density) {
            val widestLabelPx =
                buildList {
                    repeat(24) { hour ->
                        add(
                            textMeasurer.measure(
                                text = AnnotatedString(LocalTime(hour, 0).toFormattedString(is24Hour).trim()),
                                style = majorLabelStyle,
                                maxLines = 1,
                            ).size.width
                        )
                        if (zoomLevel.minorTickMinutes < 60) {
                            (zoomLevel.minorTickMinutes until 60 step zoomLevel.minorTickMinutes).forEach { minute ->
                                add(
                                    textMeasurer.measure(
                                        text = AnnotatedString(LocalTime(hour, minute).toFormattedString(is24Hour).trim()),
                                        style = minorLabelStyle,
                                        maxLines = 1,
                                    ).size.width
                                )
                            }
                        }
                    }
                }.maxOrNull() ?: 0
            (widestLabelPx.toDp() + 3.dp).coerceAtLeast(34.dp)
        }
    val labelHeight =
        with(density) {
            val tallestLabelPx =
                max(
                    textMeasurer.measure(
                        text = AnnotatedString(LocalTime(12, 0).toFormattedString(is24Hour).trim()),
                        style = majorLabelStyle,
                        maxLines = 1,
                    ).size.height,
                    textMeasurer.measure(
                        text = AnnotatedString(LocalTime(12, zoomLevel.minorTickMinutes % 60).toFormattedString(is24Hour).trim()),
                        style = minorLabelStyle,
                        maxLines = 1,
                    ).size.height,
                )
            (tallestLabelPx.toDp() + 2.dp).coerceAtLeast(TimelineLabelMinHeight)
        }
    val taskTitleTextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 16.sp,
        )
    val taskTimeTextStyle =
        MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 12.sp,
        )
    val taskDurationTextStyle =
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val minimumTaskHeight =
        with(density) {
            val topRowHeightPx =
                max(
                    textMeasurer.measure(
                        text = AnnotatedString("Ag"),
                        style = taskTitleTextStyle,
                        maxLines = 1,
                    ).size.height,
                    textMeasurer.measure(
                        text = AnnotatedString("88h 88m"),
                        style = taskDurationTextStyle,
                        maxLines = 1,
                    ).size.height,
                )
            val timeRowHeightPx =
                textMeasurer.measure(
                    text = AnnotatedString("23:59-23:59"),
                    style = taskTimeTextStyle,
                    maxLines = 1,
                ).size.height
            (topRowHeightPx.toDp() + timeRowHeightPx.toDp() + 10.dp)
                .coerceAtLeast(TimelineMinimumTaskHeight)
        }
    val axisGap = 3.dp
    val laneGap = 4.dp
    val taskAreaStart = labelWidth + axisGap + 4.dp
    val dayHeight = hourHeight * 24f
    val canvasHeight = TimelineTopPadding + dayHeight
    val minuteHeight = hourHeight / 60f

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(canvasHeight)) {
        val taskAreaWidth = maxWidth - taskAreaStart

        repeat(24) { hour ->
            val lineY = TimelineTopPadding + (hourHeight * hour.toFloat())

            TimelineTickLabel(
                text = LocalTime(hour, 0).toFormattedString(is24Hour).trim(),
                y = lineY,
                width = labelWidth,
                height = labelHeight,
                color = MaterialTheme.colorScheme.onSurface,
                isMajor = true,
            )

            Box(
                modifier =
                    Modifier.offset(x = labelWidth + axisGap, y = lineY)
                        .width(2.dp)
                        .height(hourHeight)
                        .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Box(
                modifier =
                    Modifier.offset(x = labelWidth + axisGap, y = lineY)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )

            val minorTicks =
                (zoomLevel.minorTickMinutes until 60 step zoomLevel.minorTickMinutes).toList()
            minorTicks.forEach { minute ->
                val minuteOffset = minuteHeight * minute.toFloat()
                val tickY = lineY + minuteOffset

                Box(
                    modifier =
                        Modifier.offset(x = labelWidth + axisGap, y = tickY)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
                            )
                )

                TimelineTickLabel(
                    text = LocalTime(hour, minute).toFormattedString(is24Hour).trim(),
                    y = tickY,
                    width = labelWidth,
                    height = labelHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    isMajor = false,
                )
            }
        }

        Box(
            modifier =
                Modifier.offset(x = labelWidth + axisGap, y = TimelineTopPadding + dayHeight)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )

        placements.forEach { placement ->
            val columnWidth =
                (taskAreaWidth - (laneGap * (placement.columnCount - 1).toFloat())) /
                    placement.columnCount.toFloat()
            val x = taskAreaStart + ((columnWidth + laneGap) * placement.column.toFloat())
            val y = TimelineTopPadding + (minuteHeight * placement.startMinute.toFloat())
            val height =
                maxOf(
                    minimumTaskHeight,
                    minuteHeight * (placement.endMinute - placement.startMinute).toFloat(),
                )

            TimelineTaskBlock(
                placement = placement,
                is24Hour = is24Hour,
                onToggleTask = onToggleTask,
                onEditTask = onEditTask,
                titleTextStyle = taskTitleTextStyle,
                timeTextStyle = taskTimeTextStyle,
                durationTextStyle = taskDurationTextStyle,
                modifier = Modifier.offset(x = x, y = y).width(columnWidth).height(height),
            )
        }
    }
}

@Composable
private fun TimelineTickLabel(
    text: String,
    y: Dp,
    width: Dp,
    height: Dp,
    color: Color,
    isMajor: Boolean,
) {
    Box(
        modifier =
            Modifier.offset(y = y - (height / 2f))
                .width(width)
                .height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style =
                if (isMajor) {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                    )
                } else {
                    MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp)
                },
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimelineTaskBlock(
    placement: TimelineTaskPlacement,
    is24Hour: Boolean,
    onToggleTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
    titleTextStyle: TextStyle,
    timeTextStyle: TextStyle,
    durationTextStyle: TextStyle,
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
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = task.title,
                    modifier = Modifier.weight(1f).basicMarquee(),
                    style = titleTextStyle,
                    maxLines = 1,
                    textDecoration =
                        if (task.status) TextDecoration.LineThrough else TextDecoration.None,
                )

                task.durationLabel()?.let { label ->
                    Text(
                        text = label,
                        style = durationTextStyle,
                        maxLines = 1,
                    )
                }
            }

            if (task.reminder != null) {
                AdaptiveTimelineTimeLabel(
                    task = task,
                    is24Hour = is24Hour,
                    style = timeTextStyle,
                )
            }
        }
    }
}

@Composable
private fun AdaptiveTimelineTimeLabel(task: Task, is24Hour: Boolean, style: TextStyle) {
    val labelVariants = remember(task, is24Hour) { task.timelineTimeLabelVariants(is24Hour) }
    if (labelVariants.isEmpty()) return

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val availableWidthPx = with(density) { maxWidth.roundToPx() }
        val resolvedLabel =
            remember(labelVariants, availableWidthPx, style) {
                labelVariants.firstOrNull { label ->
                    textMeasurer.measure(
                        text = AnnotatedString(label),
                        style = style,
                        maxLines = 1,
                    ).size.width <= availableWidthPx
                } ?: labelVariants.last()
            }

        Text(
            text = resolvedLabel,
            modifier = Modifier.fillMaxWidth(),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun Task.timelineTimeLabelVariants(is24Hour: Boolean): List<String> {
    val start = reminder ?: return emptyList()
    val end = resolvedEndAt()

    val normal =
        if (end != null) {
            "${start.time.toFormattedString(is24Hour).trim()}-${end.time.toFormattedString(is24Hour).trim()}"
        } else {
            start.time.toFormattedString(is24Hour).trim()
        }
    val compact =
        if (end != null) {
            "${start.time.toCompactTimelineString(is24Hour)}-${end.time.toCompactTimelineString(is24Hour)}"
        } else {
            start.time.toCompactTimelineString(is24Hour)
        }
    val ultraCompact =
        if (end != null) {
            "${start.time.toUltraCompactTimelineString(is24Hour)}-${end.time.toUltraCompactTimelineString(is24Hour)}"
        } else {
            start.time.toUltraCompactTimelineString(is24Hour)
        }

    return listOf(normal, compact, ultraCompact).distinct()
}

private fun LocalTime.toCompactTimelineString(is24Hour: Boolean): String {
    val minutePart = minute.toString().padStart(2, '0')
    return if (is24Hour) {
        "${hour.toString().padStart(2, '0')}:$minutePart"
    } else {
        "${to12Hour()}:$minutePart${amPmMarkerCompact()}"
    }
}

private fun LocalTime.toUltraCompactTimelineString(is24Hour: Boolean): String {
    val minutePart = minute.toString().padStart(2, '0')
    return if (is24Hour) {
        "${hour.toString().padStart(2, '0')}$minutePart"
    } else {
        "${to12Hour()}$minutePart${amPmMarkerCompact()}"
    }
}

private fun LocalTime.to12Hour(): Int =
    when (val value = hour % 12) {
        0 -> 12
        else -> value
    }

private fun LocalTime.amPmMarkerCompact(): String = if (hour < 12) "A" else "P"

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
                val actualDuration = task.actualTimelineDurationMinutes() ?: 1
                val endMinute =
                    min(24 * 60, startMinute + actualDuration).coerceAtLeast(startMinute + 1)

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
            seeds.filter { other -> seed.startMinute < other.endMinute && other.startMinute < seed.endMinute }
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

private fun Task.actualTimelineDurationMinutes(): Int? {
    val start = reminder ?: return null
    val end = resolvedEndAt() ?: return null
    val minutes =
        (end.toInstant(TimeZone.currentSystemDefault()) -
                start.toInstant(TimeZone.currentSystemDefault()))
            .inWholeMinutes
            .toInt()

    return minutes.takeIf { it > 0 }
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
