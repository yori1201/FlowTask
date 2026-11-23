package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.NoteDraftEntity
import com.example.data.remote.TaskFlowRepository
import com.example.model.AppLanguage
import com.example.model.DefaultCategories
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.TaskItem
import com.example.model.UiState
import com.example.ui.components.DateFormatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.MetaPill
import com.example.ui.components.parseHexColor
import com.example.ui.i18n.copyFor
import kotlinx.coroutines.delay

private enum class NoteFilterTab { ALL, PINNED, FAVORITE, ARCHIVED, TRASH }

@Composable
fun NotesScreen(
    notesState: UiState<List<NoteItem>>,
    projects: List<ProjectItem>,
    language: AppLanguage,
    onCreateNote: () -> Unit,
    onOpenNote: (NoteItem) -> Unit,
    onUpdateFlags: (NoteItem, Boolean, Boolean, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = remember(language) { copyFor(language) }
    val projectById = remember(projects) { projects.associateBy { it.id } }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(NoteFilterTab.ALL) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    text = copy.navNotes,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(copy.searchNotesHint) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Cari catatan") },
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
                        .testTag("notes_search_input")
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        NoteFilterTab.ALL to (if (language == AppLanguage.ID) "Semua" else "All"),
                        NoteFilterTab.PINNED to (if (language == AppLanguage.ID) "Disematkan" else "Pinned"),
                        NoteFilterTab.FAVORITE to (if (language == AppLanguage.ID) "Favorit" else "Favorites"),
                        NoteFilterTab.ARCHIVED to (if (language == AppLanguage.ID) "Arsip" else "Archived"),
                        NoteFilterTab.TRASH to (if (language == AppLanguage.ID) "Sampah" else "Trash")
                    )
                    tabs.forEach { (tab, lbl) ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(lbl) }
                        )
                    }
                }
            }

            when (notesState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        EmptyStatePanel(
                            icon = Icons.Rounded.Description,
                            title = "Gagal memuat catatan",
                            subtitle = notesState.message
                        )
                    }
                }

                is UiState.Success -> {
                    val filtered = remember(notesState.data, searchQuery, selectedTab) {
                        val q = searchQuery.trim().lowercase()
                        notesState.data.filter { note ->
                            val tabMatch = when (selectedTab) {
                                NoteFilterTab.ALL -> !note.deleted && !note.archived
                                NoteFilterTab.PINNED -> !note.deleted && !note.archived && note.pinned
                                NoteFilterTab.FAVORITE -> !note.deleted && !note.archived && note.favorite
                                NoteFilterTab.ARCHIVED -> !note.deleted && note.archived
                                NoteFilterTab.TRASH -> note.deleted
                            }
                            val qMatch = q.isEmpty() ||
                                note.title.lowercase().contains(q) ||
                                note.content.lowercase().contains(q) ||
                                note.category.lowercase().contains(q)
                            tabMatch && qMatch
                        }.sortedWith(
                            compareByDescending<NoteItem> { it.pinned }
                                .thenByDescending { it.updatedAt?.seconds ?: 0L }
                        )
                    }

                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            EmptyStatePanel(
                                icon = Icons.Rounded.Description,
                                title = copy.emptyNotesTitle,
                                subtitle = copy.emptyNotesBody,
                                actionLabel = if (selectedTab == NoteFilterTab.TRASH) null else copy.quickNewNote,
                                onAction = if (selectedTab == NoteFilterTab.TRASH) null else onCreateNote
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered, key = { it.id }) { note ->
                                NoteCardItem(
                                    note = note,
                                    project = projectById[note.projectId],
                                    language = language,
                                    onClick = { if (!note.deleted) onOpenNote(note) },
                                    onTogglePin = { onUpdateFlags(note, !note.pinned, note.favorite, note.archived, note.deleted) },
                                    onToggleFavorite = { onUpdateFlags(note, note.pinned, !note.favorite, note.archived, note.deleted) },
                                    onToggleArchive = { onUpdateFlags(note, note.pinned, note.favorite, !note.archived, note.deleted) },
                                    onToggleTrash = { onUpdateFlags(note, note.pinned, note.favorite, note.archived, !note.deleted) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCardItem(
    note: NoteItem,
    project: ProjectItem?,
    language: AppLanguage,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleTrash: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (note.pinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("note_card_${note.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.title.ifBlank { if (language == AppLanguage.ID) "Catatan Tanpa Judul" else "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    if (note.deleted) {
                        TextButton(onClick = onToggleTrash) {
                            Icon(Icons.Rounded.RestoreFromTrash, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (language == AppLanguage.ID) "Pulihkan" else "Restore")
                        }
                    } else {
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Sematkan",
                                tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (note.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Favorit",
                                tint = if (note.favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onToggleArchive) {
                            Icon(
                                imageVector = Icons.Rounded.Archive,
                                contentDescription = "Arsipkan",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onToggleTrash) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Hapus ke sampah",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (note.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (project != null) {
                    MetaPill(text = project.name, tint = parseHexColor(project.colorHex))
                } else {
                    MetaPill(text = note.category, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (note.linkedTaskIds.isNotEmpty()) {
                    MetaPill(
                        text = "${note.linkedTaskIds.size} ${if (language == AppLanguage.ID) "tugas" else "tasks"}",
                        icon = Icons.Rounded.Link,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                MetaPill(
                    text = "${note.wordCount} ${if (language == AppLanguage.ID) "kata" else "words"}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                note.updatedAt?.toDate()?.time?.let { millis ->
                    MetaPill(
                        text = DateFormatters.formatDateTime(millis),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun NoteEditorScreen(
    existingNote: NoteItem?,
    projects: List<ProjectItem>,
    tasks: List<TaskItem>,
    language: AppLanguage,
    onLoadDraft: suspend (String?) -> NoteDraftEntity?,
    onSaveDraftLocally: (String?, String, String, String, String?) -> Unit,
    onSaveNote: (
        existing: NoteItem?,
        title: String,
        content: String,
        category: String,
        projectId: String?,
        tags: List<String>,
        linkedTaskIds: List<String>,
        silent: Boolean,
        onSaved: (NoteItem?) -> Unit
    ) -> Unit,
    onCreateTaskFromNote: (NoteItem, String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    var activeNote by remember(existingNote?.id) { mutableStateOf(existingNote) }
    var title by remember(existingNote?.id) { mutableStateOf(existingNote?.title ?: "") }
    var content by remember(existingNote?.id) { mutableStateOf(existingNote?.content ?: "") }
    var category by remember(existingNote?.id) { mutableStateOf(existingNote?.category ?: "Personal") }
    var projectId by remember(existingNote?.id) { mutableStateOf(existingNote?.projectId) }
    val linkedTaskIds = remember(existingNote?.id) {
        mutableStateListOf<String>().apply { addAll(existingNote?.linkedTaskIds.orEmpty()) }
    }
    var saveStatus by remember { mutableStateOf(if (existingNote == null) "Draft baru" else "Tersinkronisasi") }
    var showTaskLinkDialog by remember { mutableStateOf(false) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitleInput by remember { mutableStateOf("") }
    var draftChecked by remember(existingNote?.id) { mutableStateOf(false) }

    // Restore local Room draft if app closed unexpectedly while editing
    LaunchedEffect(existingNote?.id) {
        val draft = onLoadDraft(existingNote?.id)
        if (draft != null && (draft.title.isNotBlank() || draft.content.isNotBlank())) {
            val remoteMillis = existingNote?.updatedAt?.toDate()?.time ?: 0L
            if (existingNote == null || draft.updatedAtMillis > remoteMillis) {
                title = draft.title
                content = draft.content
                category = draft.category
                projectId = draft.projectId
                saveStatus = "Draft lokal dipulihkan"
            }
        }
        draftChecked = true
    }

    // PRD FR-033: Debounced local + cloud auto-save
    LaunchedEffect(title, content, category, projectId, linkedTaskIds.toList(), draftChecked) {
        if (!draftChecked) return@LaunchedEffect
        if (title.isBlank() && content.isBlank()) return@LaunchedEffect
        saveStatus = "Menyimpan draft lokal..."
        onSaveDraftLocally(activeNote?.id, title, content, category, projectId)
        delay(900L)
        saveStatus = "Menyinkronkan..."
        onSaveNote(
            activeNote,
            title,
            content,
            category,
            projectId,
            activeNote?.tags.orEmpty(),
            linkedTaskIds.toList(),
            true
        ) { saved ->
            if (saved != null) {
                activeNote = saved
                saveStatus = "Tersimpan otomatis"
            }
        }
    }

    val wordCount = remember(title, content) {
        TaskFlowRepository.countWords("$title $content")
    }

    fun appendMarkdown(snippet: String) {
        content = if (content.isEmpty() || content.endsWith("\n")) "$content$snippet" else "$content\n$snippet"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Editor Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                    Column {
                        Text(
                            text = saveStatus,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$wordCount ${if (language == AppLanguage.ID) "kata" else "words"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showTaskLinkDialog = true }) {
                        Icon(Icons.Rounded.Link, contentDescription = "Tautkan tugas")
                    }
                    if (activeNote != null) {
                        IconButton(onClick = {
                            newTaskTitleInput = title.ifBlank { content.lines().firstOrNull()?.take(80).orEmpty() }
                            showCreateTaskDialog = true
                        }) {
                            Icon(Icons.Rounded.AddTask, contentDescription = "Buat tugas dari catatan")
                        }
                    }
                    Button(
                        onClick = {
                            onSaveNote(
                                activeNote,
                                title,
                                content,
                                category,
                                projectId,
                                activeNote?.tags.orEmpty(),
                                linkedTaskIds.toList(),
                                false
                            ) { saved ->
                                if (saved != null) activeNote = saved
                                onClose()
                            }
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.testTag("note_editor_save_button")
                    ) {
                        Text(if (language == AppLanguage.ID) "Simpan" else "Save")
                    }
                }
            }

            // Markdown Formatting Toolbar (PRD 6.4)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tools = listOf(
                    "H1" to "# ",
                    "H2" to "## ",
                    "H3" to "### ",
                    "B" to "**teks tebal**",
                    "I" to "*teks miring*",
                    "• List" to "- ",
                    "1. List" to "1. ",
                    "☑ Check" to "- [ ] ",
                    "> Quote" to "> ",
                    "</> Code" to "```\n\n```",
                    "───" to "\n---\n"
                )
                tools.forEach { (label, syntax) ->
                    Surface(
                        onClick = { appendMarkdown(syntax) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 160) title = it },
                    placeholder = { Text(if (language == AppLanguage.ID) "Judul catatan..." else "Note title...") },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_editor_title_input")
                )

                // Category & Project selectors
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                if (linkedTaskIds.isNotEmpty()) {
                    val linkedNames = tasks.filter { it.id in linkedTaskIds }.map { it.title }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Terhubung dengan ${linkedTaskIds.size} tugas: ${linkedNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 50000) content = it },
                    placeholder = {
                        Text(
                            if (language == AppLanguage.ID) "Tulis catatan, checklist (- [ ]), atau ide di sini..."
                            else "Write notes, checklists (- [ ]), or ideas here..."
                        )
                    },
                    minLines = 12,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_editor_content_input")
                )
            }
        }
    }

    if (showTaskLinkDialog) {
        AlertDialog(
            onDismissRequest = { showTaskLinkDialog = false },
            title = { Text(if (language == AppLanguage.ID) "Hubungkan dengan Tugas" else "Link to Tasks") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (tasks.isEmpty()) {
                        Text("Belum ada tugas untuk dihubungkan.")
                    } else {
                        tasks.filter { !it.archived }.forEach { t ->
                            val isLinked = t.id in linkedTaskIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isLinked) linkedTaskIds.remove(t.id)
                                        else if (linkedTaskIds.size < 20) linkedTaskIds.add(t.id)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isLinked, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTaskLinkDialog = false }) {
                    Text("Selesai")
                }
            }
        )
    }

    if (showCreateTaskDialog && activeNote != null) {
        AlertDialog(
            onDismissRequest = { showCreateTaskDialog = false },
            title = { Text(if (language == AppLanguage.ID) "Buat Tugas dari Catatan" else "Create Task from Note") },
            text = {
                OutlinedTextField(
                    value = newTaskTitleInput,
                    onValueChange = { newTaskTitleInput = it.take(160) },
                    label = { Text("Judul tugas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateTaskFromNote(activeNote!!, newTaskTitleInput)
                        showCreateTaskDialog = false
                    }
                ) {
                    Text("Buat Tugas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTaskDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
