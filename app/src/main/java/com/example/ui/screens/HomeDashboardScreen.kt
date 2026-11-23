package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.SyncMetadata
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.UiState
import com.example.ui.components.DateFormatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.SyncStatusBadge
import com.example.ui.components.TaskCardItem
import com.example.ui.i18n.copyFor
import java.util.Calendar

@Composable
fun HomeDashboardScreen(
    displayName: String,
    tasksState: UiState<List<TaskItem>>,
    notesState: UiState<List<NoteItem>>,
    projects: List<ProjectItem>,
    syncMetadata: SyncMetadata,
    language: AppLanguage,
    onOpenSyncCenter: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateNote: () -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onOpenNote: (NoteItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = remember(language) { copyFor(language) }
    val projectById = remember(projects) { projects.associateBy { it.id } }
    val greeting = remember(language) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> copy.greetingMorning
            hour < 18 -> copy.greetingAfternoon
            else -> copy.greetingEvening
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (tasksState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    EmptyStatePanel(
                        icon = Icons.Rounded.WarningAmber,
                        title = "Gagal memuat data",
                        subtitle = tasksState.message
                    )
                }
            }

            is UiState.Success -> {
                val activeTasks = remember(tasksState.data) { tasksState.data.filter { !it.archived } }
                val overdueTasks = remember(activeTasks) { activeTasks.filter { DateFormatters.isTaskOverdue(it) } }
                val todayTasks = remember(activeTasks) {
                    activeTasks.filter { DateFormatters.isTaskDueToday(it) && !DateFormatters.isTaskOverdue(it) }
                }
                val highPriorityTasks = remember(activeTasks) {
                    activeTasks.filter {
                        it.taskStatus != TaskStatus.COMPLETED &&
                            (it.taskPriority == TaskPriority.URGENT || it.taskPriority == TaskPriority.HIGH)
                    }.sortedByDescending { it.taskPriority.weight }
                }
                val upcomingTasks = remember(activeTasks) {
                    activeTasks.filter { DateFormatters.isTaskUpcoming(it) }
                        .sortedBy { it.dueAtMillis ?: Long.MAX_VALUE }
                        .take(5)
                }
                val recentNotes = remember(notesState) {
                    (notesState as? UiState.Success)?.data
                        .orEmpty()
                        .filter { !it.deleted && !it.archived }
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                        .take(3)
                }

                val totalActive = activeTasks.size
                val completedCount = activeTasks.count { it.taskStatus == TaskStatus.COMPLETED }
                val progressFraction = if (totalActive == 0) 0f else completedCount.toFloat() / totalActive.toFloat()

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header with Greeting & Live Cloud Sync Pill
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = DateFormatters.formatFullDate(System.currentTimeMillis()).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "$greeting, ${displayName.substringBefore(" ")}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            SyncStatusBadge(
                                metadata = syncMetadata,
                                language = language,
                                onClick = onOpenSyncCenter
                            )
                        }
                    }

                    // Signature Editorial Focus Hero Card with Canvas Progress Ring
                    item {
                        FocusHeroCard(
                            title = copy.todayFocus,
                            completedCount = completedCount,
                            totalCount = totalActive,
                            overdueCount = overdueTasks.size,
                            todayCount = todayTasks.size,
                            highPriorityCount = highPriorityTasks.size,
                            progressFraction = progressFraction,
                            language = language
                        )
                    }

                    // Quick Action Shortcuts (PRD FR-015)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onCreateTask,
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("home_quick_add_task")
                            ) {
                                Icon(Icons.Rounded.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(copy.quickNewTask, style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = onCreateNote,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("home_quick_add_note")
                            ) {
                                Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(copy.quickNewNote, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // Empty Workspace First-Launch State
                    if (activeTasks.isEmpty() && recentNotes.isEmpty()) {
                        item {
                            EmptyStatePanel(
                                icon = Icons.Rounded.TaskAlt,
                                title = copy.emptyTasksTitle,
                                subtitle = copy.emptyTasksBody,
                                actionLabel = copy.quickNewTask,
                                onAction = onCreateTask
                            )
                        }
                    }

                    // Overdue Section (PRD FR-010)
                    if (overdueTasks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = copy.overdueTitle,
                                count = overdueTasks.size,
                                accent = MaterialTheme.colorScheme.error
                            )
                        }
                        items(overdueTasks, key = { "overdue_${it.id}" }) { task ->
                            TaskCardItem(
                                task = task,
                                project = projectById[task.projectId],
                                language = language,
                                onToggleComplete = { onToggleTask(task) },
                                onClick = { onEditTask(task) }
                            )
                        }
                    }

                    // Due Today Section (PRD FR-009)
                    if (todayTasks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = copy.todayTitle,
                                count = todayTasks.size,
                                accent = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(todayTasks, key = { "today_${it.id}" }) { task ->
                            TaskCardItem(
                                task = task,
                                project = projectById[task.projectId],
                                language = language,
                                onToggleComplete = { onToggleTask(task) },
                                onClick = { onEditTask(task) }
                            )
                        }
                    }

                    // High Priority Section (PRD FR-011)
                    if (highPriorityTasks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = copy.highPriorityTitle,
                                count = highPriorityTasks.size,
                                accent = MaterialTheme.colorScheme.secondary
                            )
                        }
                        items(highPriorityTasks.take(4), key = { "high_${it.id}" }) { task ->
                            TaskCardItem(
                                task = task,
                                project = projectById[task.projectId],
                                language = language,
                                onToggleComplete = { onToggleTask(task) },
                                onClick = { onEditTask(task) }
                            )
                        }
                    }

                    // Upcoming Section (PRD FR-012)
                    if (upcomingTasks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = copy.upcomingTitle,
                                count = upcomingTasks.size,
                                accent = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        items(upcomingTasks, key = { "upcoming_${it.id}" }) { task ->
                            TaskCardItem(
                                task = task,
                                project = projectById[task.projectId],
                                language = language,
                                onToggleComplete = { onToggleTask(task) },
                                onClick = { onEditTask(task) }
                            )
                        }
                    }

                    // Recent Notes Section (PRD FR-014)
                    if (recentNotes.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = copy.recentNotesTitle,
                                count = recentNotes.size,
                                accent = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(recentNotes, key = { "note_${it.id}" }) { note ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onOpenNote(note) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.title.ifBlank { "Catatan Tanpa Judul" },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = note.content.replace("\n", " ").ifBlank { "Kosong" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${note.wordCount}w",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FocusHeroCard(
    title: String,
    completedCount: Int,
    totalCount: Int,
    overdueCount: Int,
    todayCount: Int,
    highPriorityCount: Int,
    progressFraction: Float,
    language: AppLanguage
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val percent = (progressFraction * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(primary, primary.copy(alpha = 0.84f))
                )
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onPrimary.copy(alpha = 0.78f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "$completedCount / $totalCount",
                    style = MaterialTheme.typography.displayLarge,
                    color = onPrimary
                )
                Text(
                    text = if (language == AppLanguage.ID) "tugas selesai dari daftar aktif"
                    else "completed tasks in active list",
                    style = MaterialTheme.typography.bodySmall,
                    color = onPrimary.copy(alpha = 0.84f)
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroMetricBadge(
                        label = if (language == AppLanguage.ID) "Hari Ini" else "Today",
                        value = "$todayCount",
                        containerColor = onPrimary.copy(alpha = 0.16f),
                        contentColor = onPrimary
                    )
                    HeroMetricBadge(
                        label = if (language == AppLanguage.ID) "Terlambat" else "Overdue",
                        value = "$overdueCount",
                        containerColor = if (overdueCount > 0) Color(0xFFE5484D).copy(alpha = 0.90f)
                        else onPrimary.copy(alpha = 0.16f),
                        contentColor = Color.White
                    )
                    HeroMetricBadge(
                        label = if (language == AppLanguage.ID) "Prioritas" else "High",
                        value = "$highPriorityCount",
                        containerColor = secondary.copy(alpha = 0.90f),
                        contentColor = Color.White
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Custom Canvas Progress Ring
            Box(
                modifier = Modifier.size(84.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(84.dp)) {
                    val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2f
                    val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                    drawArc(
                        color = onPrimary.copy(alpha = 0.20f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke
                    )
                    drawArc(
                        color = Color(0xFFFDE5D4),
                        startAngle = -90f,
                        sweepAngle = 360f * progressFraction.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke
                    )
                }
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    color = onPrimary
                )
            }
        }
    }
}

@Composable
private fun HeroMetricBadge(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = contentColor)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.86f))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
    }
}
