package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.LocalBackupCodec
import com.example.data.local.NoteDraftEntity
import com.example.data.local.SyncConflictEntity
import com.example.data.local.SyncLogEntity
import com.example.data.local.UserPreferencesStore
import com.example.data.local.UserSettings
import com.example.data.remote.TaskFlowRepository
import com.example.model.AppLanguage
import com.example.model.BackupPreview
import com.example.model.BackupSnapshot
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.ReminderEvent
import com.example.model.SubtaskItem
import com.example.model.SyncMetadata
import com.example.model.SyncState
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskFlowViewModel(
    private val repository: TaskFlowRepository,
    private val preferencesStore: UserPreferencesStore,
    val currentUserId: String,
    private val onSyncLocalReminders: (List<TaskItem>, UserSettings) -> Unit,
    private val onScheduleReminder: (TaskItem, UserSettings) -> Unit,
    private val onCancelReminder: (String) -> Unit,
    private val onSnoozeLocalReminder: (String, String, String, String, Int) -> Unit
) : ViewModel() {

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _pendingRestorePreview = MutableStateFlow<BackupPreview?>(null)
    val pendingRestorePreview: StateFlow<BackupPreview?> = _pendingRestorePreview.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var deviceId: String = "android-device"

    val userSettings: StateFlow<UserSettings> = preferencesStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UserSettings())

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .restartOnTransientError { attempt, error ->
            Log.w(TAG, "Profile stream retry #$attempt: ${error.localizedMessage}")
        }
        .catch { error ->
            unwrapFirestoreOrRethrow(error, "profile")
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    val tasksState: StateFlow<UiState<List<TaskItem>>> = repository.observeTasks(currentUserId)
        .toUiState("Gagal memuat tugas")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UiState.Loading)

    val notesState: StateFlow<UiState<List<NoteItem>>> = repository.observeNotes(currentUserId)
        .toUiState("Gagal memuat catatan")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UiState.Loading)

    val projectsState: StateFlow<UiState<List<ProjectItem>>> = repository.observeProjects(currentUserId)
        .toUiState("Gagal memuat proyek")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UiState.Loading)

    val remindersState: StateFlow<UiState<List<ReminderEvent>>> = repository.observeReminders(currentUserId)
        .toUiState("Gagal memuat pengingat")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UiState.Loading)

    val backupsState: StateFlow<UiState<List<BackupSnapshot>>> = repository.observeBackups(currentUserId)
        .toUiState("Gagal memuat backup cloud")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UiState.Loading)

    val syncLogs: StateFlow<List<SyncLogEntity>> = repository.observeSyncLogs(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val conflicts: StateFlow<List<SyncConflictEntity>> = repository.observeConflicts(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val syncMetadata: StateFlow<SyncMetadata> = combine(
        repository.syncMetadata,
        conflicts
    ) { meta, openConflicts ->
        if (openConflicts.isNotEmpty()) {
            meta.copy(state = SyncState.CONFLICT_DETECTED)
        } else {
            meta
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SyncMetadata())

    init {
        viewModelScope.launch {
            deviceId = preferencesStore.getOrCreateDeviceId()
            repository.ensureUserProfile()
        }
        viewModelScope.launch {
            combine(tasksState, userSettings) { state, settings ->
                (state as? UiState.Success)?.data to settings
            }.collect { (tasks, settings) ->
                if (tasks != null) {
                    onSyncLocalReminders(tasks, settings)
                }
            }
        }
    }

    /**
     * C-03: dulu `.catch { emit(UiState.Error) }` bersifat terminal — satu
     * exception menghentikan stream selamanya. Kini error transien (jaringan)
     * di-resubscribe dengan backoff eksponensial; hanya error fatal yang
     * berakhir menjadi UiState.Error. Data terakhir tetap tampil selama
     * pemulihan karena StateFlow menyimpan nilai sukses sebelumnya.
     */
    private fun <T> Flow<T>.toUiState(fallbackMessage: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .restartOnTransientError { attempt, error ->
                Log.w(TAG, "Stream retry #$attempt: ${error.localizedMessage}")
            }
            .catch { error ->
                val root = unwrapFirestoreOrRethrow(error, fallbackMessage)
                emit(UiState.Error(root.localizedMessage ?: fallbackMessage))
            }

    private fun unwrapFirestoreOrRethrow(error: Throwable, tag: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Stream error ($tag): ${root.message}", root)
        return root
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    // --- TASK ACTIONS ---
    fun saveTask(
        existing: TaskItem?,
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
        subtasks: List<SubtaskItem>,
        onSaved: () -> Unit = {}
    ) {
        if (title.isBlank()) {
            showMessage("Judul tugas wajib diisi.")
            return
        }
        viewModelScope.launch {
            if (existing == null) {
                val res = repository.createTask(
                    title = title,
                    description = description,
                    status = status,
                    priority = priority,
                    category = category,
                    projectId = projectId,
                    dueAtMillis = dueAtMillis,
                    reminderAtMillis = reminderAtMillis,
                    recurrence = recurrence,
                    tags = tags,
                    subtasks = subtasks,
                    deviceId = deviceId
                )
                res.onSuccess { created ->
                    if (created.reminderAtMillis != null) {
                        onScheduleReminder(created, userSettings.value)
                    }
                    showMessage("Tugas tersimpan & disinkronkan.")
                    onSaved()
                }.onFailure { err ->
                    showMessage(err.localizedMessage ?: "Gagal menyimpan tugas.")
                }
            } else {
                val res = repository.updateTask(
                    existing = existing,
                    title = title,
                    description = description,
                    status = status,
                    priority = priority,
                    category = category,
                    projectId = projectId,
                    dueAtMillis = dueAtMillis,
                    reminderAtMillis = reminderAtMillis,
                    recurrence = recurrence,
                    tags = tags,
                    subtasks = subtasks,
                    deviceId = deviceId
                )
                res.onSuccess { applied ->
                    if (!applied) {
                        showMessage("Konflik revisi terdeteksi! Buka Sinkronisasi & Backup untuk memilih versi.")
                    } else {
                        val updatedTask = existing.copy(
                            title = title,
                            description = description,
                            status = status.name,
                            reminderAtMillis = reminderAtMillis
                        )
                        if (reminderAtMillis != null && status != TaskStatus.COMPLETED) {
                            onScheduleReminder(updatedTask, userSettings.value)
                        } else {
                            onCancelReminder(existing.id)
                        }
                        showMessage("Perubahan tugas tersimpan.")
                        onSaved()
                    }
                }.onFailure { err ->
                    showMessage(err.localizedMessage ?: "Gagal memperbarui tugas.")
                }
            }
        }
    }

    fun toggleTaskCompletion(task: TaskItem) {
        viewModelScope.launch {
            val res = repository.toggleTaskCompletion(task, deviceId)
            res.onSuccess { nextRecurring ->
                if (task.taskStatus != TaskStatus.COMPLETED) {
                    onCancelReminder(task.id)
                }
                if (nextRecurring != null) {
                    if (nextRecurring.reminderAtMillis != null) {
                        onScheduleReminder(nextRecurring, userSettings.value)
                    }
                    showMessage("Tugas selesai! Jadwal berulang berikutnya dibuat otomatis.")
                }
            }.onFailure {
                showMessage("Gagal mengubah status tugas.")
            }
        }
    }

    fun toggleSubtask(task: TaskItem, subtaskId: String) {
        viewModelScope.launch {
            repository.toggleSubtask(task, subtaskId, deviceId)
        }
    }

    fun duplicateTask(task: TaskItem) {
        viewModelScope.launch {
            repository.createTask(
                title = "${task.title} (Salinan)".take(160),
                description = task.description,
                status = TaskStatus.TODO,
                priority = task.taskPriority,
                category = task.category,
                projectId = task.projectId,
                dueAtMillis = task.dueAtMillis,
                reminderAtMillis = null,
                recurrence = task.recurrenceRule,
                tags = task.tags,
                subtasks = task.subtasks.map { it.copy(done = false) },
                deviceId = deviceId
            ).onSuccess {
                showMessage("Tugas diduplikasi.")
            }.onFailure {
                showMessage("Gagal menduplikasi tugas.")
            }
        }
    }

    fun rescheduleTask(task: TaskItem, newDueMillis: Long?) {
        viewModelScope.launch {
            repository.rescheduleTask(task, newDueMillis, deviceId)
                .onSuccess { showMessage("Tanggal tugas diperbarui.") }
                .onFailure { showMessage("Gagal mengubah tanggal tugas.") }
        }
    }

    fun archiveTask(task: TaskItem, archive: Boolean) {
        viewModelScope.launch {
            repository.archiveTask(task, archive, deviceId)
                .onSuccess {
                    if (archive) onCancelReminder(task.id)
                    showMessage(if (archive) "Tugas diarsipkan." else "Tugas dikembalikan dari arsip.")
                }
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            repository.softDeleteTask(task, deviceId)
                .onSuccess {
                    onCancelReminder(task.id)
                    showMessage("Tugas dihapus.")
                }
                .onFailure { showMessage("Gagal menghapus tugas.") }
        }
    }

    // --- NOTES ACTIONS ---
    suspend fun loadLocalNoteDraft(noteId: String?): NoteDraftEntity? =
        repository.getNoteDraftLocally(currentUserId, noteId)

    fun saveNoteDraftLocally(
        noteId: String?,
        title: String,
        content: String,
        category: String,
        projectId: String?
    ) {
        viewModelScope.launch {
            repository.saveNoteDraftLocally(currentUserId, noteId, title, content, category, projectId)
        }
    }

    fun saveNote(
        existing: NoteItem?,
        title: String,
        content: String,
        category: String,
        projectId: String?,
        tags: List<String>,
        linkedTaskIds: List<String>,
        silent: Boolean = false,
        onSaved: (NoteItem?) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (existing == null) {
                repository.createNote(
                    title = title,
                    content = content,
                    category = category,
                    projectId = projectId,
                    tags = tags,
                    linkedTaskIds = linkedTaskIds,
                    deviceId = deviceId
                ).onSuccess { created ->
                    if (!silent) showMessage("Catatan tersimpan.")
                    onSaved(created)
                }.onFailure { err ->
                    if (!silent) showMessage(err.localizedMessage ?: "Gagal menyimpan catatan.")
                }
            } else {
                repository.updateNote(
                    existing = existing,
                    title = title,
                    content = content,
                    category = category,
                    projectId = projectId,
                    tags = tags,
                    linkedTaskIds = linkedTaskIds,
                    deviceId = deviceId
                ).onSuccess { applied ->
                    if (!applied) {
                        showMessage("Konflik revisi catatan terdeteksi! Periksa Sinkronisasi & Backup.")
                    } else {
                        if (!silent) showMessage("Catatan diperbarui.")
                        onSaved(
                            existing.copy(
                                title = title,
                                content = content,
                                category = category,
                                projectId = projectId,
                                tags = tags,
                                linkedTaskIds = linkedTaskIds,
                                revision = existing.revision + 1L
                            )
                        )
                    }
                }.onFailure { err ->
                    if (!silent) showMessage(err.localizedMessage ?: "Gagal memperbarui catatan.")
                }
            }
        }
    }

    fun updateNoteFlags(
        note: NoteItem,
        pinned: Boolean = note.pinned,
        favorite: Boolean = note.favorite,
        archived: Boolean = note.archived,
        deleted: Boolean = note.deleted
    ) {
        viewModelScope.launch {
            repository.updateNoteFlags(note, pinned, favorite, archived, deleted, deviceId)
                .onSuccess {
                    if (deleted && !note.deleted) showMessage("Catatan dipindahkan ke Sampah (dapat dipulihkan).")
                    if (!deleted && note.deleted) showMessage("Catatan dipulihkan.")
                }
        }
    }

    fun createTaskFromNote(note: NoteItem, taskTitle: String) {
        val cleanTitle = taskTitle.trim().ifBlank { note.title.ifBlank { "Tindak lanjut catatan" } }
        viewModelScope.launch {
            repository.createTask(
                title = cleanTitle,
                description = "Dibuat dari catatan: ${note.title.ifBlank { "Catatan" }}\n\n${note.content.take(500)}",
                status = TaskStatus.TODO,
                priority = TaskPriority.MEDIUM,
                category = note.category,
                projectId = note.projectId,
                dueAtMillis = null,
                reminderAtMillis = null,
                recurrence = RecurrenceRule.NONE,
                tags = note.tags,
                subtasks = emptyList(),
                deviceId = deviceId
            ).onSuccess { createdTask ->
                val updatedLinks = (note.linkedTaskIds + createdTask.id).distinct().take(20)
                repository.updateNote(
                    existing = note,
                    title = note.title,
                    content = note.content,
                    category = note.category,
                    projectId = note.projectId,
                    tags = note.tags,
                    linkedTaskIds = updatedLinks,
                    deviceId = deviceId,
                    forceOverwrite = true
                )
                showMessage("Tugas \"$cleanTitle\" dibuat dan ditautkan ke catatan.")
            }.onFailure {
                showMessage("Gagal membuat tugas dari catatan.")
            }
        }
    }

    // --- PROJECT ACTIONS ---
    fun saveProject(
        existing: ProjectItem?,
        name: String,
        description: String?,
        colorHex: String,
        onSaved: () -> Unit = {}
    ) {
        if (name.isBlank()) {
            showMessage("Nama proyek wajib diisi.")
            return
        }
        viewModelScope.launch {
            if (existing == null) {
                repository.createProject(name, description, colorHex)
                    .onSuccess {
                        showMessage("Proyek dibuat.")
                        onSaved()
                    }
                    .onFailure { showMessage("Gagal membuat proyek.") }
            } else {
                repository.updateProject(existing, name, description, colorHex)
                    .onSuccess {
                        showMessage("Proyek diperbarui.")
                        onSaved()
                    }
                    .onFailure { showMessage("Gagal memperbarui proyek.") }
            }
        }
    }

    fun toggleArchiveProject(project: ProjectItem) {
        viewModelScope.launch {
            repository.updateProject(
                existing = project,
                name = project.name,
                description = project.description,
                colorHex = project.colorHex,
                archived = !project.archived
            ).onSuccess {
                showMessage(if (!project.archived) "Proyek diarsipkan." else "Proyek diaktifkan kembali.")
            }
        }
    }

    fun safeDeleteProject(project: ProjectItem) {
        val currentTasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
            .filter { it.projectId == project.id }
        val currentNotes = (notesState.value as? UiState.Success)?.data.orEmpty()
            .filter { it.projectId == project.id && !it.deleted }
        viewModelScope.launch {
            repository.safeDeleteProject(project, currentTasks, currentNotes, deviceId)
                .onSuccess {
                    showMessage("Proyek dihapus. ${currentTasks.size} tugas & ${currentNotes.size} catatan tetap dipertahankan.")
                }
                .onFailure { showMessage("Gagal menghapus proyek.") }
        }
    }

    // --- REMINDERS ---
    fun snoozeReminder(event: ReminderEvent, minutes: Int = 15) {
        viewModelScope.launch {
            repository.snoozeReminderEvent(event, minutes).onSuccess {
                onSnoozeLocalReminder(
                    event.taskId ?: event.id,
                    currentUserId,
                    event.title,
                    event.message,
                    minutes
                )
                showMessage("Pengingat ditunda $minutes menit.")
            }
        }
    }

    fun dismissReminder(event: ReminderEvent) {
        viewModelScope.launch {
            repository.dismissReminderEvent(event)
        }
    }

    // --- SYNC, CONFLICT & BACKUP ---
    fun triggerManualSync() {
        viewModelScope.launch {
            _isBusy.value = true
            repository.triggerManualSync(currentUserId)
                .onSuccess { showMessage("Sinkronisasi cloud selesai & terverifikasi.") }
                .onFailure { showMessage("Sinkronisasi tertunda. Data tetap aman di perangkat ini.") }
            _isBusy.value = false
        }
    }

    fun resolveConflict(
        conflict: SyncConflictEntity,
        chosenTitle: String,
        chosenContent: String,
        resolutionLabel: String
    ) {
        viewModelScope.launch {
            repository.resolveConflict(conflict, chosenTitle, chosenContent, resolutionLabel, deviceId)
                .onSuccess { showMessage("Konflik diselesaikan ($resolutionLabel).") }
                .onFailure { showMessage("Gagal menyelesaikan konflik.") }
        }
    }

    fun createCloudBackup(label: String) {
        val projects = (projectsState.value as? UiState.Success)?.data.orEmpty()
        val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
        val notes = (notesState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _isBusy.value = true
            repository.createCloudBackup(label, projects, tasks, notes)
                .onSuccess {
                    showMessage("Backup cloud berhasil (${tasks.size} tugas, ${notes.size} catatan).")
                }
                .onFailure {
                    showMessage("Backup cloud gagal. Coba kembali saat koneksi stabil.")
                }
            _isBusy.value = false
        }
    }

    fun buildExportJson(label: String): String {
        val projects = (projectsState.value as? UiState.Success)?.data.orEmpty()
        val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
        val notes = (notesState.value as? UiState.Success)?.data.orEmpty()
        return LocalBackupCodec.encodeToJson(label, projects, tasks, notes).first
    }

    fun inspectImportJson(rawJson: String) {
        LocalBackupCodec.decodeFromJson(rawJson)
            .onSuccess { preview ->
                _pendingRestorePreview.value = preview
            }
            .onFailure { err ->
                showMessage("File backup tidak valid atau rusak: ${err.localizedMessage ?: "format salah"}")
            }
    }

    fun inspectCloudBackup(snapshot: BackupSnapshot) {
        _pendingRestorePreview.value = LocalBackupCodec.decodeFromCloudSnapshot(snapshot)
    }

    fun dismissRestorePreview() {
        _pendingRestorePreview.value = null
    }

    fun confirmRestorePreview() {
        val preview = _pendingRestorePreview.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.restoreFromPreview(preview, deviceId)
                .onSuccess { result ->
                    _pendingRestorePreview.value = null
                    showMessage(
                        "Pemulihan selesai: ${result.restoredCount} item dipulihkan" +
                            ", ${result.skippedCount} dilewati karena sudah ada di cloud."
                    )
                }
                .onFailure {
                    showMessage("Pemulihan backup gagal. Data aktif tidak diubah.")
                }
            _isBusy.value = false
        }
    }

    // --- SETTINGS ---
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesStore.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesStore.setLanguage(language) }
    }

    fun setLargeText(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setLargeText(enabled) }
    }

    fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) {
        viewModelScope.launch { preferencesStore.setQuietHours(enabled, startHour, endHour) }
    }

    fun setDailySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setDailySummaryEnabled(enabled) }
    }

    private companion object {
        const val TAG = "TaskFlowVM"
        const val STOP_TIMEOUT = 5000L
    }
}
