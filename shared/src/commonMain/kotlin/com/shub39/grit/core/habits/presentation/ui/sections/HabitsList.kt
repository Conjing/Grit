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
package com.shub39.grit.core.habits.presentation.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.LocalWindowSizeClass
import com.shub39.grit.core.habits.domain.Habit
import com.shub39.grit.core.habits.domain.HabitWithAnalytics
import com.shub39.grit.core.habits.domain.isDueOn
import com.shub39.grit.core.habits.presentation.HabitState
import com.shub39.grit.core.habits.presentation.HabitsAction
import com.shub39.grit.core.habits.presentation.ui.component.HabitCard
import com.shub39.grit.core.habits.presentation.ui.component.HabitUpsertSheet
import com.shub39.grit.core.now
import com.shub39.grit.core.shared_ui.Empty
import com.shub39.grit.core.shared_ui.detachedItemShape
import com.shub39.grit.core.shared_ui.endItemShape
import com.shub39.grit.core.shared_ui.leadingItemShape
import com.shub39.grit.core.shared_ui.listItemColors
import com.shub39.grit.core.shared_ui.middleItemShape
import grit.shared.generated.resources.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun HabitsList(
    state: HabitState,
    lazyListState: LazyListState,
    onAction: (HabitsAction) -> Unit,
    onNavigateToAnalytics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val today = LocalDate.now()
    val todayHabits = state.habitsWithAnalytics.filter { it.habit.isDueOn(today) }
    val nonTodayHabits = state.habitsWithAnalytics.filterNot { it.habit.isDueOn(today) }
    var nonTodayExpanded by rememberSaveable { mutableStateOf(true) }

    val reorderableListState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            onAction(HabitsAction.OnTransientHabitReorder(from.index, to.index))
        }

    Column(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxHeight(),
        ) {
            if (state.editState) {
                itemsIndexed(state.habitsWithAnalytics, key = { _, it -> it.habit.id }) {
                    index,
                    habitWithAnalytics ->
                    ReorderableItem(reorderableListState, key = habitWithAnalytics.habit.id) {
                        HabitListCard(
                            habitWithAnalytics = habitWithAnalytics,
                            completed = state.completedHabitIds.contains(habitWithAnalytics.habit.id),
                            index = index,
                            totalCount = state.habitsWithAnalytics.size,
                            state = state,
                            onAction = onAction,
                            onNavigateToAnalytics = onNavigateToAnalytics,
                            analyticsEnabled =
                                state.analyticsHabitId != habitWithAnalytics.habit.id ||
                                    windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded,
                            reorderHandle = {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.drag_indicator),
                                    contentDescription = "Drag Indicator",
                                    modifier =
                                        Modifier.draggableHandle(
                                            onDragStopped = { onAction(HabitsAction.ReorderHabits) }
                                        ),
                                )
                            },
                        )
                    }
                }
            } else {
                itemsIndexed(todayHabits, key = { _, it -> it.habit.id }) { index, habitWithAnalytics
                    ->
                    HabitListCard(
                        habitWithAnalytics = habitWithAnalytics,
                        completed = state.completedHabitIds.contains(habitWithAnalytics.habit.id),
                        index = index,
                        totalCount = todayHabits.size,
                        state = state,
                        onAction = onAction,
                        onNavigateToAnalytics = onNavigateToAnalytics,
                        analyticsEnabled =
                            state.analyticsHabitId != habitWithAnalytics.habit.id ||
                                windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded,
                    )
                }

                if (nonTodayHabits.isNotEmpty()) {
                    item {
                        ListItem(
                            modifier =
                                Modifier.padding(top = if (todayHabits.isNotEmpty()) 8.dp else 0.dp)
                                    .clip(detachedItemShape(radius = 28))
                                    .clickable { nonTodayExpanded = !nonTodayExpanded },
                            colors = listItemColors(),
                            headlineContent = {
                                Text(
                                    text = stringResource(Res.string.non_today),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.arrow_forward),
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (nonTodayExpanded) 90f else 0f),
                                )
                            },
                        )
                    }

                    if (nonTodayExpanded) {
                        itemsIndexed(nonTodayHabits, key = { _, it -> it.habit.id }) {
                            index,
                            habitWithAnalytics ->
                            HabitListCard(
                                habitWithAnalytics = habitWithAnalytics,
                                completed =
                                    state.completedHabitIds.contains(habitWithAnalytics.habit.id),
                                index = index,
                                totalCount = nonTodayHabits.size,
                                state = state,
                                onAction = onAction,
                                onNavigateToAnalytics = onNavigateToAnalytics,
                                analyticsEnabled =
                                    state.analyticsHabitId != habitWithAnalytics.habit.id ||
                                        windowSizeClass.widthSizeClass !=
                                            WindowWidthSizeClass.Expanded,
                            )
                        }
                    }
                }
            }

            if (state.habitsWithAnalytics.isEmpty()) {
                item {
                    Empty(
                        modifier = Modifier.padding(top = 150.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    if (state.showHabitAddSheet) {
        HabitUpsertSheet(
            habit =
                Habit(
                    title = "",
                    description = "",
                    time = LocalDateTime.now(),
                    days = DayOfWeek.entries.toSet(),
                    index = state.habitsWithAnalytics.size,
                    reminder = false,
                ),
            onDismissRequest = { onAction(HabitsAction.DismissAddHabitDialog) },
            onUpsertHabit = { onAction(HabitsAction.AddHabit(it)) },
            is24Hr = state.is24Hr,
        )
    }
}

@Composable
private fun HabitListCard(
    habitWithAnalytics: HabitWithAnalytics,
    completed: Boolean,
    index: Int,
    totalCount: Int,
    state: HabitState,
    onAction: (HabitsAction) -> Unit,
    onNavigateToAnalytics: () -> Unit,
    analyticsEnabled: Boolean,
    reorderHandle: @Composable () -> Unit = {},
) {
    HabitCard(
        habitWithAnalytics = habitWithAnalytics,
        completed = completed,
        action = onAction,
        startingDay = state.startingDay,
        editState = state.editState,
        onNavigateToAnalytics = onNavigateToAnalytics,
        is24Hr = state.is24Hr,
        reorderHandle = reorderHandle,
        shape = habitCardShape(index = index, totalCount = totalCount, completed = completed),
        compactView = state.compactHabitView,
        analyticsEnabled = analyticsEnabled,
    )
}

private fun habitCardShape(
    index: Int,
    totalCount: Int,
    completed: Boolean,
): Shape {
    return when {
        totalCount == 1 || !completed -> detachedItemShape(radius = 28)
        index == 0 -> leadingItemShape(topRadius = 28, bottomRadius = 8)
        index == totalCount - 1 -> endItemShape(bottomRadius = 28, topRadius = 8)
        else -> middleItemShape(radius = 8)
    }
}
