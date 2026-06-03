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
package com.shub39.grit.widgets.todo_tasks_widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.shub39.grit.R
import com.shub39.grit.app.MainActivity
import com.shub39.grit.core.now
import com.shub39.grit.core.tasks.domain.Category
import com.shub39.grit.core.tasks.domain.CategoryColors
import com.shub39.grit.core.tasks.domain.Task
import com.shub39.grit.core.tasks.domain.TaskRepo
import com.shub39.grit.core.tasks.domain.resolvedEndAt
import com.shub39.grit.domain.AlarmScheduler
import com.shub39.grit.widgets.WidgetSize
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class TodoTasksWidget : GlanceAppWidget(), KoinComponent {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = get<TaskRepo>()
        val scheduler = get<AlarmScheduler>()

        provideContent {
            val scope = rememberCoroutineScope()
            val size = LocalSize.current
            val tasks by repo.getTasksFlow().collectAsState(emptyMap())

            key(size) {
                GlanceTheme {
                    Content(
                        tasks = tasks,
                        onUpdateTaskStatus = { task ->
                            scope.launch {
                                val updatedTask = task.copy(status = !task.status)
                                repo.upsertTask(updatedTask)

                                if (updatedTask.status) {
                                    scheduler.cancel(updatedTask)
                                } else {
                                    scheduler.schedule(updatedTask)
                                }
                            }
                        },
                        onUpdateWidget = {
                            scope.launch { this@TodoTasksWidget.update(context, id) }
                        },
                    )
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val today = LocalDate.now()
        val previewItems =
            mapOf(
                Category(name = "Misc", index = 1, color = CategoryColors.BLUE.color) to
                    listOf(
                        Task(
                            id = 1,
                            categoryId = 1,
                            title = "记账",
                            index = 1,
                            status = false,
                            reminder = kotlinx.datetime.LocalDateTime(today, LocalTime(9, 30)),
                            durationMinutes = 60,
                        ),
                        Task(
                            id = 2,
                            categoryId = 1,
                            title = "运动",
                            index = 2,
                            status = true,
                            reminder = kotlinx.datetime.LocalDateTime(today, LocalTime(18, 0)),
                        ),
                    )
            )

        provideContent {
            Content(
                tasks = previewItems,
                onUpdateTaskStatus = {},
                onUpdateWidget = {},
            )
        }
    }
}

@Composable
@GlanceComposable
private fun Content(
    tasks: Map<Category, List<Task>>,
    onUpdateTaskStatus: (Task) -> Unit,
    onUpdateWidget: () -> Unit,
    modifier: GlanceModifier = GlanceModifier,
) {
    val size = LocalSize.current
    val roundedCornerSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val todayTaskGroups = visibleTodayTaskGroups(tasks)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .then(
                    if (roundedCornerSupported) {
                        GlanceModifier.background(GlanceTheme.colors.widgetBackground)
                            .cornerRadius(24.dp)
                    } else {
                        GlanceModifier.background(
                            imageProvider = ImageProvider(R.drawable.rounded_4dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
                        )
                    }
                )
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "任务",
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 1,
            )

            if (size.width >= WidgetSize.Width2) {
                Box(GlanceModifier.padding(start = 8.dp)) {
                    Image(
                        provider = ImageProvider(R.drawable.refresh),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                        modifier = GlanceModifier.clickable { onUpdateWidget() },
                    )
                }
            }
        }

        LazyColumn(
            modifier = GlanceModifier.padding(horizontal = 12.dp).fillMaxSize(),
            horizontalAlignment = Alignment.Start,
        ) {
            if (todayTaskGroups.isEmpty()) {
                item {
                    Text(
                        text = "今天没有任务",
                        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                            ),
                    )
                }
            }

            todayTaskGroups.forEach { group ->
                item(itemId = categoryHeaderItemId(group.category.id)) {
                    CategoryHeader(group)
                }

                items(group.tasks, itemId = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onUpdateTaskStatus = onUpdateTaskStatus,
                    )
                }
            }

            item { Spacer(modifier = GlanceModifier.height(8.dp)) }
        }
    }
}

@Composable
@GlanceComposable
private fun CategoryHeader(group: WidgetTaskGroup) {
    val completedCount = group.tasks.count { it.status }

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.category.name,
            modifier = GlanceModifier.defaultWeight(),
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )

        Spacer(GlanceModifier.width(8.dp))

        Text(
            text = "$completedCount/${group.tasks.size}今天已完成",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1,
        )
    }
}

@Composable
@GlanceComposable
private fun TaskRow(
    task: Task,
    onUpdateTaskStatus: (Task) -> Unit,
) {
    val status = task.status
    val titleColor =
        if (status) {
            GlanceTheme.colors.onSurfaceVariant
        } else {
            GlanceTheme.colors.onSurface
        }
    val timeColor = GlanceTheme.colors.onSurfaceVariant

    Row(
        modifier =
            GlanceModifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable {
                    onUpdateTaskStatus(task)
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(if (status) R.drawable.check_circle else R.drawable.circle_border),
            contentDescription = null,
            colorFilter =
                ColorFilter.tint(
                    if (status) {
                        GlanceTheme.colors.onTertiaryContainer
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    }
                ),
        )

        Spacer(GlanceModifier.width(10.dp))

        Text(
            text = task.title,
            modifier = GlanceModifier.defaultWeight(),
            style =
                TextStyle(
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    textDecoration =
                        if (status) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                ),
            maxLines = 2,
        )

        Spacer(GlanceModifier.width(8.dp))

        Text(
            text = formatTaskTime(task),
            style =
                TextStyle(
                    color = timeColor,
                ),
            maxLines = 1,
        )
    }
}

private data class WidgetTaskGroup(val category: Category, val tasks: List<Task>)

private fun categoryHeaderItemId(categoryId: Long): Long = Long.MAX_VALUE - categoryId

private fun visibleTodayTaskGroups(tasks: Map<Category, List<Task>>): List<WidgetTaskGroup> {
    val today = LocalDate.now()

    return tasks.entries
        .sortedWith(compareBy({ it.key.index }, { it.key.name }, { it.key.id }))
        .mapNotNull { entry ->
            val todayTasks =
                entry.value
                .filter { task -> task.reminder?.date == today }
                .sortedWith(
                    compareBy<Task> { it.reminder }
                        .thenBy { it.resolvedEndAt() ?: it.reminder }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.id }
                )

            if (todayTasks.isEmpty()) {
                null
            } else {
                WidgetTaskGroup(category = entry.key, tasks = todayTasks)
            }
        }
}

private fun formatTaskTime(task: Task): String {
    val start = task.reminder ?: return ""
    val end = task.resolvedEndAt()

    return if (end != null) {
        "${formatTime(start.time)}-${formatTime(end.time)}"
    } else {
        formatTime(start.time)
    }
}

private fun formatTime(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(heightDp = 200, widthDp = 300)
@Composable
private fun GlancePreview() {
    val today = LocalDate.now()

    Content(
        tasks =
            mapOf(
                Category(id = 1, name = "Misc", index = 1, color = CategoryColors.BLUE.color) to
                    listOf(
                        Task(
                            id = 1,
                            categoryId = 1,
                            title = "记账",
                            reminder = kotlinx.datetime.LocalDateTime(today, LocalTime(9, 30)),
                            durationMinutes = 45,
                        ),
                        Task(
                            id = 2,
                            categoryId = 1,
                            title = "运动",
                            status = true,
                            reminder = kotlinx.datetime.LocalDateTime(today, LocalTime(18, 0)),
                            durationMinutes = 60,
                        ),
                    )
        ),
        onUpdateTaskStatus = {},
        onUpdateWidget = {},
    )
}
