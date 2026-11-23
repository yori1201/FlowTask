package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.SyncMetadata
import com.example.model.SyncState
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.ui.i18n.label
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.PriorityUrgentColor
import com.example.ui.theme.SyncConflictColor
import com.example.ui.theme.SyncPendingColor
import com.example.ui.theme.SyncSyncedColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateFormatters {
    fun formatDateShort(millis: Long): String =
        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))

    fun formatDateTime(millis: Long): String =
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

    fun formatTimeOnly(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

    fun formatFullDate(millis: Long): String =
        SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(Date(millis))

    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    fun isTaskOverdue(task: TaskItem, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val due = task.dueAtMillis ?: return false
        return task.taskStatus != TaskStatus.COMPLETED && !task.archived && due < nowMillis
    }

    fun isTaskDueToday(task: TaskItem, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val due = task.dueAtMillis ?: return false
        return !task.archived && isSameDay(due, nowMillis)
    }

    fun isTaskUpcoming(task: TaskItem, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val due = task.dueAtMillis ?: return false
        return task.taskStatus != TaskStatus.COMPLETED && !task.archived && due > endOfTodayMillis()
    }
}

fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.URGENT -> PriorityUrgentColor
    TaskPriority.HIGH -> PriorityHighColor
    TaskPriority.MEDIUM -> PriorityMediumColor
    TaskPriority.LOW -> PriorityLowColor
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF0F5257)): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}

@Composable
fun SyncStatusBadge(
    metadata: SyncMetadata,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (accent, icon) = when (metadata.state) {
        SyncState.SYNCED -> SyncSyncedColor to Icons.Rounded.CloudDone
        SyncState.SYNCING, SyncState.PENDING_SYNC -> SyncPendingColor to Icons.Rounded.CloudSync
        SyncState.LOCAL_ONLY -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Rounded.CloudOff
        SyncState.SYNC_FAILED, SyncState.CONFLICT_DETECTED -> SyncConflictColor to Icons.Rounded.WarningAmber
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.12f),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(50))
            .testTag("sync_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = metadata.state.label(language),
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = metadata.state.label(language),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCardItem(
    task: TaskItem,
    project: ProjectItem?,
    language: AppLanguage,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDone = task.taskStatus == TaskStatus.COMPLETED
    val isOverdue = DateFormatters.isTaskOverdue(task)
    val railColor = if (isDone) MaterialTheme.colorScheme.tertiary else priorityColor(task.taskPriority)
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("task_card_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Priority Accent Strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(railColor)
            )
            Spacer(Modifier.width(10.dp))

            // Tactile Checkbox
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onToggleComplete)
                    .testTag("task_checkbox_${task.id}"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDone) MaterialTheme.colorScheme.tertiary
                            else Color.Transparent
                        )
                        .border(
                            width = 2.dp,
                            color = if (isDone) MaterialTheme.colorScheme.tertiary else railColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selesai",
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Priority chip
                    MetaPill(
                        text = task.taskPriority.label(language),
                        tint = railColor
                    )

                    // Project or Category chip
                    if (project != null) {
                        MetaPill(
                            text = project.name,
                            tint = parseHexColor(project.colorHex)
                        )
                    } else {
                        MetaPill(
                            text = task.category,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Due Date
                    task.dueAtMillis?.let { due ->
                        MetaPill(
                            text = DateFormatters.formatDateTime(due),
                            icon = Icons.Rounded.Event,
                            tint = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Automatic Reminder indicator
                    task.reminderAtMillis?.let { rem ->
                        MetaPill(
                            text = DateFormatters.formatTimeOnly(rem),
                            icon = Icons.Rounded.Alarm,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Recurrence indicator
                    if (task.recurrenceRule != RecurrenceRule.NONE) {
                        MetaPill(
                            text = task.recurrenceRule.label(language),
                            icon = Icons.Rounded.Repeat,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    // Subtasks count
                    if (task.subtasks.isNotEmpty()) {
                        MetaPill(
                            text = "${task.completedSubtasksCount}/${task.subtasks.size}",
                            icon = Icons.Rounded.Checklist,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (onDuplicate != null || onArchive != null || onDelete != null) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Aksi tugas",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        onDuplicate?.let {
                            DropdownMenuItem(
                                text = { Text(if (language == AppLanguage.ID) "Duplikasi" else "Duplicate") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = {
                                    menuExpanded = false
                                    it()
                                }
                            )
                        }
                        onArchive?.let {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (task.archived) {
                                            if (language == AppLanguage.ID) "Keluarkan dari Arsip" else "Unarchive"
                                        } else {
                                            if (language == AppLanguage.ID) "Arsipkan" else "Archive"
                                        }
                                    )
                                },
                                leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                                onClick = {
                                    menuExpanded = false
                                    it()
                                }
                            )
                        }
                        onDelete?.let {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (language == AppLanguage.ID) "Hapus" else "Delete",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    menuExpanded = false
                                    it()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetaPill(
    text: String,
    tint: Color,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.11f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction, shape = MaterialTheme.shapes.small) {
                    Text(actionLabel)
                }
            }
        }
    }
}
