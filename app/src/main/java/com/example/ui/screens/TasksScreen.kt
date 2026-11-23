package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.AppLanguage
import com.example.model.DefaultCategories
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.SubtaskItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.UiState
import com.example.ui.components.DateFormatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.TaskCardItem
import com.example.ui.i18n.copyFor
import com.example.ui.i18n.label
import java.util.Calendar
import java.util.UUID

private enum class TaskFilterTab { ALL, ACTIVE, COMPLETED, OVERDUE, ARCHIVED }

@Composable
fun TasksScreen(
    tasksState: UiState<List<TaskItem>>,
    projects: List<ProjectItem>,
    language: AppLanguage,
    onCreateTask: () -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onToggleComplete: (TaskItem) -> Unit,
    onDuplicateTask: (TaskItem) -> Unit,
    onArchiveTask: (TaskItem, Boolean) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = remember(language) { copyFor(language) }
    val projectById = remember(projects) { projects.associateBy { it.id } }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(TaskFilterTab.ACTIVE) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    text = copy.navTasks,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(copy.searchTasksHint) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Cari tugas") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tasks_search_input")
                )
                Spacer(Modifier.height(10.dp))

                // Status filter row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        TaskFilterTab.ACTIVE to (if (language == AppLanguage.ID) "Aktif" else "Active"),
                        TaskFilterTab.ALL to (if (language == AppLanguage.ID) "Semua" else "All"),
                        TaskFilterTab.OVERDUE to (if (language == AppLanguage.ID) "Terlambat" else "Overdue"),
                        TaskFilterTab.COMPLETED to (if (language == AppLanguage.ID) "Selesai" else "Done"),
                        TaskFilterTab.ARCHIVED to (if (language == AppLanguage.ID) "Arsip" else "Archived")
                    )
                    tabs.forEach { (tab, label) ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(label) }
                        )
                    }
                }

                // Category filter row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(if (language == AppLanguage.ID) "Semua Kategori" else "All Categories") }
                    )
                    DefaultCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            when (tasksState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        EmptyStatePanel(
                            icon = Icons.Rounded.TaskAlt,
                            title = "Gagal memuat tugas",
                            subtitle = tasksState.message
                        )
                    }
                }

                is UiState.Success -> {
                    val filtered = remember(tasksState.data, searchQuery, selectedTab, selectedCategory) {
                        val q = searchQuery.trim().lowercase()
                        tasksState.data.filter { task ->
                            val matchesTab = when (selectedTab) {
                                TaskFilterTab.ALL -> !task.archived
                                TaskFilterTab.ACTIVE -> !task.archived && task.taskStatus != TaskStatus.COMPLETED
                                TaskFilterTab.COMPLETED -> !task.archived && task.taskStatus == TaskStatus.COMPLETED
                                TaskFilterTab.OVERDUE -> DateFormatters.isTaskOverdue(task)
                                TaskFilterTab.ARCHIVED -> task.archived
                            }
                            val matchesCat = selectedCategory == null || task.category == selectedCategory
                            val matchesQuery = q.isEmpty() ||
                                task.title.lowercase().contains(q) ||
                                task.description.lowercase().contains(q) ||
                                task.category.lowercase().contains(q) ||
                                task.tags.any { it.lowercase().contains(q) }
                            matchesTab && matchesCat && matchesQuery
                        }.sortedWith(
                            compareBy<TaskItem> { it.taskStatus == TaskStatus.COMPLETED }
                                .thenByDescending { it.taskPriority.weight }
                                .thenBy { it.dueAtMillis ?: Long.MAX_VALUE }
                        )
                    }

                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            EmptyStatePanel(
                                icon = Icons.Rounded.TaskAlt,
                                title = copy.emptyTasksTitle,
                                subtitle = copy.emptyTasksBody,
                                actionLabel = copy.quickNewTask,
                                onAction = onCreateTask
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { task ->
                                TaskCardItem(
                                    task = task,
                                    project = projectById[task.projectId],
                                    language = language,
                                    onToggleComplete = { onToggleComplete(task) },
                                    onClick = { onEditTask(task) },
                                    onDuplicate = { onDuplicateTask(task) },
                                    onArchive = { onArchiveTask(task, !task.archived) },
                                    onDelete = { onDeleteTask(task) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    existingTask: TaskItem?,
    projects: List<ProjectItem>,
    linkedNotes: List<NoteItem>,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        status: TaskStatus,
        priority: TaskPriority,
        category: String,
        projectId: String?,
        dueAtMillis: Long?,
        reminderAtMillis: Long?,
        recurrence: RecurrenceRule,
        tags: List<String>,
        subtasks: List<SubtaskItem>
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(existingTask?.id) { mutableStateOf(existingTask?.title ?: "") }
    var description by remember(existingTask?.id) { mutableStateOf(existingTask?.description ?: "") }
    var status by remember(existingTask?.id) { mutableStateOf(existingTask?.taskStatus ?: TaskStatus.TODO) }
    var priority by remember(existingTask?.id) { mutableStateOf(existingTask?.taskPriority ?: TaskPriority.MEDIUM) }
    var category by remember(existingTask?.id) { mutableStateOf(existingTask?.category ?: "Personal") }
    var projectId by remember(existingTask?.id) { mutableStateOf(existingTask?.projectId) }
    var dueAtMillis by remember(existingTask?.id) { mutableStateOf(existingTask?.dueAtMillis) }
    var reminderAtMillis by remember(existingTask?.id) { mutableStateOf(existingTask?.reminderAtMillis) }
    var recurrence by remember(existingTask?.id) { mutableStateOf(existingTask?.recurrenceRule ?: RecurrenceRule.NONE) }
    var tagInput by remember { mutableStateOf("") }
    val tags = remember(existingTask?.id) {
        mutableStateListOf<String>().apply { addAll(existingTask?.tags.orEmpty()) }
    }
    var subtaskInput by remember { mutableStateOf("") }
    val subtasks = remember(existingTask?.id) {
        mutableStateListOf<SubtaskItem>().apply { addAll(existingTask?.subtasks.orEmpty()) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun pickDateTime(initialMillis: Long?, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply {
            if (initialMillis != null) timeInMillis = initialMillis
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val picked = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onPicked(picked.timeInMillis)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingTask == null) {
                        if (language == AppLanguage.ID) "Tugas Baru" else "New Task"
                    } else {
                        if (language == AppLanguage.ID) "Edit Tugas (rev #${existingTask.revision})" else "Edit Task (rev #${existingTask.revision})"
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 160) title = it },
                label = { Text(if (language == AppLanguage.ID) "Judul Tugas *" else "Task Title *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_editor_title_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 4000) description = it },
                label = { Text(if (language == AppLanguage.ID) "Deskripsi / Detail" else "Description") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Status & Priority
            Text(
                text = if (language == AppLanguage.ID) "Status" else "Status",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskStatus.entries.forEach { st ->
                    FilterChip(
                        selected = status == st,
                        onClick = { status = st },
                        label = { Text(st.label(language)) }
                    )
                }
            }

            Text(
                text = if (language == AppLanguage.ID) "Prioritas" else "Priority",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskPriority.entries.forEach { pr ->
                    FilterChip(
                        selected = priority == pr,
                        onClick = { priority = pr },
                        label = { Text(pr.label(language)) }
                    )
                }
            }

            // Deadline & Automatic Reminder
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (language == AppLanguage.ID) "Jadwal & Pengingat Otomatis" else "Schedule & Automatic Reminder",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = dueAtMillis?.let { DateFormatters.formatDateTime(it) }
                                    ?: (if (language == AppLanguage.ID) "Belum ada deadline" else "No deadline"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row {
                            if (dueAtMillis != null) {
                                TextButton(onClick = { dueAtMillis = null }) {
                                    Text(if (language == AppLanguage.ID) "Hapus" else "Clear")
                                }
                            }
                            TextButton(onClick = {
                                pickDateTime(dueAtMillis) { picked ->
                                    dueAtMillis = picked
                                    if (reminderAtMillis == null) {
                                        ensureNotificationPermission()
                                        reminderAtMillis = (picked - 30 * 60_000L).coerceAtLeast(System.currentTimeMillis() + 60_000L)
                                    }
                                }
                            }) {
                                Text(if (language == AppLanguage.ID) "Atur" else "Set")
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Alarm, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = reminderAtMillis?.let { DateFormatters.formatDateTime(it) }
                                    ?: (if (language == AppLanguage.ID) "Pengingat mati" else "Reminder off"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row {
                            if (reminderAtMillis != null) {
                                TextButton(onClick = { reminderAtMillis = null }) {
                                    Text(if (language == AppLanguage.ID) "Matikan" else "Off")
                                }
                            }
                            TextButton(onClick = {
                                ensureNotificationPermission()
                                pickDateTime(reminderAtMillis ?: dueAtMillis) { picked ->
                                    reminderAtMillis = picked
                                }
                            }) {
                                Text(if (language == AppLanguage.ID) "Waktu" else "Time")
                            }
                        }
                    }

                    // Quick Reminder Presets
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            "+15m" to (System.currentTimeMillis() + 15 * 60_000L),
                            "+1 Jam" to (System.currentTimeMillis() + 60 * 60_000L),
                            "Besok 08:00" to Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 8)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                            }.timeInMillis
                        )
                        presets.forEach { (lbl, ts) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    ensureNotificationPermission()
                                    reminderAtMillis = ts
                                    if (dueAtMillis == null) dueAtMillis = ts
                                },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
            }

            // Recurrence (PRD FR-026)
            Text(
                text = if (language == AppLanguage.ID) "Pengulangan" else "Recurrence",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecurrenceRule.entries.forEach { rec ->
                    FilterChip(
                        selected = recurrence == rec,
                        onClick = { recurrence = rec },
                        label = { Text(rec.label(language)) }
                    )
                }
            }

            // Category & Project
            Text(
                text = if (language == AppLanguage.ID) "Kategori" else "Category",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DefaultCategories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) }
                    )
                }
            }

            if (projects.isNotEmpty()) {
                Text(
                    text = if (language == AppLanguage.ID) "Proyek" else "Project",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = projectId == null,
                        onClick = { projectId = null },
                        label = { Text(if (language == AppLanguage.ID) "Tanpa Proyek" else "No Project") }
                    )
                    projects.filter { !it.archived }.forEach { proj ->
                        FilterChip(
                            selected = projectId == proj.id,
                            onClick = { projectId = proj.id },
                            label = { Text(proj.name) }
                        )
                    }
                }
            }

            // Subtasks Checklist (PRD FR-023)
            Text(
                text = if (language == AppLanguage.ID) "Subtasks / Checklist (${subtasks.size}/20)" else "Subtasks (${subtasks.size}/20)",
                style = MaterialTheme.typography.titleSmall
            )
            subtasks.forEachIndexed { idx, st ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = st.done,
                        onCheckedChange = { checked ->
                            subtasks[idx] = st.copy(done = checked)
                        }
                    )
                    Text(
                        text = st.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { subtasks.removeAt(idx) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Hapus subtask", modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (subtasks.size < 20) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = subtaskInput,
                        onValueChange = { subtaskInput = it },
                        placeholder = { Text(if (language == AppLanguage.ID) "Tambah langkah..." else "Add step...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val clean = subtaskInput.trim()
                            if (clean.isNotEmpty() && subtasks.size < 20) {
                                subtasks.add(
                                    SubtaskItem(
                                        id = UUID.randomUUID().toString().take(8),
                                        title = clean,
                                        done = false
                                    )
                                )
                                subtaskInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Tambah subtask")
                    }
                }
            }

            // Tags (PRD FR-025)
            Text(
                text = "Tags (${tags.size}/10)",
                style = MaterialTheme.typography.titleSmall
            )
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tg ->
                        InputChip(
                            selected = false,
                            onClick = { tags.remove(tg) },
                            label = { Text("#$tg") },
                            trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(14.dp)) }
                        )
                    }
                }
            }
            if (tags.size < 10) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it.take(32) },
                        placeholder = { Text("label") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val clean = tagInput.trim().removePrefix("#")
                            if (clean.isNotEmpty() && clean !in tags && tags.size < 10) {
                                tags.add(clean)
                                tagInput = ""
                            }
                        }
                    ) {
                        Text("+ Tag")
                    }
                }
            }

            // Linked Notes preview
            if (linkedNotes.isNotEmpty()) {
                Text(
                    text = if (language == AppLanguage.ID) "Catatan Terkait (${linkedNotes.size})" else "Linked Notes (${linkedNotes.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                linkedNotes.forEach { n ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(10.dp)
                    ) {
                        Text(n.title.ifBlank { "Catatan" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    onSave(
                        title,
                        description,
                        status,
                        priority,
                        category,
                        projectId,
                        dueAtMillis,
                        reminderAtMillis,
                        recurrence,
                        tags.toList(),
                        subtasks.toList()
                    )
                },
                enabled = title.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("task_editor_save_button")
            ) {
                Text(
                    text = if (language == AppLanguage.ID) "Simpan Tugas" else "Save Task",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
