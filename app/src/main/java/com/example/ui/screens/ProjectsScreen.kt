package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.NoteItem
import com.example.model.ProjectAccentColors
import com.example.model.ProjectItem
import com.example.model.TaskItem
import com.example.model.TaskStatus
import com.example.model.UiState
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.parseHexColor
import com.example.ui.i18n.copyFor

@Composable
fun ProjectsScreen(
    projectsState: UiState<List<ProjectItem>>,
    tasks: List<TaskItem>,
    notes: List<NoteItem>,
    language: AppLanguage,
    onCreateProject: () -> Unit,
    onEditProject: (ProjectItem) -> Unit,
    onToggleArchive: (ProjectItem) -> Unit,
    onSafeDeleteProject: (ProjectItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = remember(language) { copyFor(language) }
    var showArchived by remember { mutableStateOf(false) }
    var pendingDeleteProject by remember { mutableStateOf<ProjectItem?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    text = copy.navProjects,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showArchived,
                        onClick = { showArchived = false },
                        label = { Text(if (language == AppLanguage.ID) "Aktif" else "Active") }
                    )
                    FilterChip(
                        selected = showArchived,
                        onClick = { showArchived = true },
                        label = { Text(if (language == AppLanguage.ID) "Diarsipkan" else "Archived") }
                    )
                }
            }

            when (projectsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        EmptyStatePanel(
                            icon = Icons.Rounded.FolderOpen,
                            title = "Gagal memuat proyek",
                            subtitle = projectsState.message
                        )
                    }
                }

                is UiState.Success -> {
                    val list = remember(projectsState.data, showArchived) {
                        projectsState.data.filter { it.archived == showArchived }
                    }

                    if (list.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            EmptyStatePanel(
                                icon = Icons.Rounded.FolderOpen,
                                title = copy.emptyProjectsTitle,
                                subtitle = copy.emptyProjectsBody,
                                actionLabel = if (!showArchived) {
                                    if (language == AppLanguage.ID) "Buat Proyek" else "New Project"
                                } else null,
                                onAction = if (!showArchived) onCreateProject else null
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(list, key = { it.id }) { project ->
                                val linkedTasks = tasks.filter { it.projectId == project.id && !it.archived }
                                val doneTasks = linkedTasks.count { it.taskStatus == TaskStatus.COMPLETED }
                                val linkedNotesCount = notes.count { it.projectId == project.id && !it.deleted }
                                val progress = if (linkedTasks.isEmpty()) 0f else doneTasks.toFloat() / linkedTasks.size
                                val accent = parseHexColor(project.colorHex)

                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                                        .testTag("project_card_${project.id}")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(accent)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = project.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Row {
                                                IconButton(onClick = { onEditProject(project) }) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit proyek", modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(onClick = { onToggleArchive(project) }) {
                                                    Icon(Icons.Rounded.Archive, contentDescription = "Arsipkan proyek", modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(onClick = { pendingDeleteProject = project }) {
                                                    Icon(
                                                        Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Hapus proyek",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (!project.description.isNullOrBlank()) {
                                            Text(
                                                text = project.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(10.dp))
                                        }

                                        LinearProgressIndicator(
                                            progress = { progress },
                                            color = accent,
                                            trackColor = accent.copy(alpha = 0.14f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "$doneTasks/${linkedTasks.size} ${if (language == AppLanguage.ID) "tugas selesai" else "tasks done"} · $linkedNotesCount ${if (language == AppLanguage.ID) "catatan" else "notes"}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${(progress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // PRD 6.6: Safe project deletion dialog explaining linked tasks/notes remain intact
    pendingDeleteProject?.let { target ->
        val taskCount = tasks.count { it.projectId == target.id }
        val noteCount = notes.count { it.projectId == target.id && !it.deleted }
        AlertDialog(
            onDismissRequest = { pendingDeleteProject = null },
            title = { Text(if (language == AppLanguage.ID) "Hapus Proyek \"${target.name}\"?" else "Delete Project \"${target.name}\"?") },
            text = {
                Text(
                    if (language == AppLanguage.ID) {
                        "Proyek ini memiliki $taskCount tugas dan $noteCount catatan terkait. Menghapus proyek TIDAK akan menghapus tugas atau catatan Anda—keduanya akan dilepaskan dari proyek dan tetap aman."
                    } else {
                        "This project has $taskCount linked tasks and $noteCount linked notes. Deleting it will NOT delete your tasks or notes—they will simply be unlinked and kept safe."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSafeDeleteProject(target)
                        pendingDeleteProject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (language == AppLanguage.ID) "Hapus Proyek Saja" else "Delete Project Only")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProject = null }) {
                    Text(if (language == AppLanguage.ID) "Batal" else "Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorSheet(
    existingProject: ProjectItem?,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, colorHex: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(existingProject?.id) { mutableStateOf(existingProject?.name ?: "") }
    var description by remember(existingProject?.id) { mutableStateOf(existingProject?.description ?: "") }
    var colorHex by remember(existingProject?.id) { mutableStateOf(existingProject?.colorHex ?: ProjectAccentColors.first()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (existingProject == null) {
                    if (language == AppLanguage.ID) "Proyek Baru" else "New Project"
                } else {
                    if (language == AppLanguage.ID) "Edit Proyek" else "Edit Project"
                },
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text(if (language == AppLanguage.ID) "Nama Proyek *" else "Project Name *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_editor_name_input")
            )
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 500) description = it },
                label = { Text(if (language == AppLanguage.ID) "Deskripsi (opsional)" else "Description (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (language == AppLanguage.ID) "Warna Aksen" else "Accent Color",
                style = MaterialTheme.typography.titleSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProjectAccentColors.forEach { hex ->
                    val swatchColor = parseHexColor(hex)
                    val selected = colorHex == hex
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { colorHex = hex }
                    )
                }
            }
            Button(
                onClick = { onSave(name, description.takeIf { it.isNotBlank() }, colorHex) },
                enabled = name.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("project_editor_save_button")
            ) {
                Text(if (language == AppLanguage.ID) "Simpan Proyek" else "Save Project")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
