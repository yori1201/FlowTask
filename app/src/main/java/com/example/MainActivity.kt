package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.local.TaskFlowLocalMode
import com.example.data.local.UserPreferencesStore
import com.example.data.local.UserSettings
import com.example.data.remote.TaskFlowRepository
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.TaskItem
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.notifications.ReminderScheduler
import com.example.ui.i18n.copyFor
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MoreHubScreen
import com.example.ui.screens.MoreSection
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.ProjectEditorSheet
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.TaskEditorSheet
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.TaskFlowTheme
import com.example.viewmodel.TaskFlowViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

enum class MainNavTab(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", Icons.Rounded.Home, Icons.Outlined.Home),
    TASKS("tasks", Icons.Rounded.CheckCircle, Icons.Outlined.CheckCircle),
    NOTES("notes", Icons.Rounded.Description, Icons.Outlined.Description),
    CALENDAR("calendar", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    PROJECTS("projects", Icons.Rounded.Folder, Icons.Outlined.Folder),
    MORE("more", Icons.Rounded.MoreHoriz, Icons.Outlined.MoreHoriz)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.ensureChannel(this)
        setContent {
            val context = LocalContext.current
            val prefsStore = remember(context) { UserPreferencesStore(context.applicationContext) }
            val settings by prefsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TaskFlowTheme(
                darkTheme = darkTheme,
                fontScaleMultiplier = if (settings.largeText) 1.15f else 1.0f
            ) {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var localModeEnabled by remember { mutableStateOf(TaskFlowLocalMode.isEnabled(context)) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    when {
        user != null -> {
            val uid = user.uid
            key(uid) {
                TaskFlowWorkspace(
                    currentUserId = uid,
                    userDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "TaskFlow",
                    userEmail = user.email,
                    isLocalMode = false,
                    onExitLocalMode = { }
                )
            }
        }
        localModeEnabled -> {
            key(TaskFlowLocalMode.LOCAL_USER_ID) {
                TaskFlowWorkspace(
                    currentUserId = TaskFlowLocalMode.LOCAL_USER_ID,
                    userDisplayName = "Pengguna Lokal",
                    userEmail = null,
                    isLocalMode = true,
                    onExitLocalMode = { localModeEnabled = false }
                )
            }
        }
        else -> AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUseLocalMode = {
                TaskFlowLocalMode.enable(context)
                localModeEnabled = true
            }
        )
    }
}

@Composable
private fun TaskFlowWorkspace(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?,
    isLocalMode: Boolean = false,
    onExitLocalMode: () -> Unit = { }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TaskFlowViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                TaskFlowViewModel(
                    repository = TaskFlowRepository(app),
                    preferencesStore = UserPreferencesStore(app),
                    currentUserId = currentUserId,
                    onSyncLocalReminders = { tasks, settings ->
                        ReminderScheduler.syncReminders(app, tasks, settings)
                    },
                    onScheduleReminder = { task, settings ->
                        ReminderScheduler.scheduleTaskReminder(app, task, settings)
                    },
                    onCancelReminder = { taskId ->
                        ReminderScheduler.cancelTaskReminder(app, taskId)
                    },
                    onSnoozeLocalReminder = { taskId, uid, title, msg, mins ->
                        ReminderScheduler.scheduleSnooze(app, taskId, uid, title, msg, mins)
                    }
                )
            }
        }
    )

    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()
    val backupsState by viewModel.backupsState.collectAsStateWithLifecycle()
    val syncMetadata by viewModel.syncMetadata.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val pendingRestorePreview by viewModel.pendingRestorePreview.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val copy = remember(settings.language) { copyFor(settings.language) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var activeTab by rememberSaveable { mutableStateOf(MainNavTab.HOME) }
    var moreSection by rememberSaveable { mutableStateOf(MoreSection.MENU) }
    var showTaskEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteItem?>(null) }
    var showProjectEditor by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<ProjectItem?>(null) }

    val tasksList = (tasksState as? UiState.Success)?.data.orEmpty()
    val notesList = (notesState as? UiState.Success)?.data.orEmpty()
    val projectsList = (projectsState as? UiState.Success)?.data.orEmpty()

    fun tabLabel(tab: MainNavTab): String = when (tab) {
        MainNavTab.HOME -> copy.navHome
        MainNavTab.TASKS -> copy.navTasks
        MainNavTab.NOTES -> copy.navNotes
        MainNavTab.CALENDAR -> copy.navCalendar
        MainNavTab.PROJECTS -> copy.navProjects
        MainNavTab.MORE -> copy.navMore
    }

    if (showNoteEditor) {
        NoteEditorScreen(
            existingNote = editingNote,
            projects = projectsList,
            tasks = tasksList,
            language = settings.language,
            onLoadDraft = { viewModel.loadLocalNoteDraft(it) },
            onSaveDraftLocally = { noteId, title, content, cat, projId ->
                viewModel.saveNoteDraftLocally(noteId, title, content, cat, projId)
            },
            onSaveNote = { existing, title, content, cat, projId, tags, linkedIds, silent, onSaved ->
                viewModel.saveNote(existing, title, content, cat, projId, tags, linkedIds, silent, onSaved)
            },
            onCreateTaskFromNote = { note, taskTitle ->
                viewModel.createTaskFromNote(note, taskTitle)
            },
            onClose = {
                showNoteEditor = false
                editingNote = null
            }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                when (activeTab) {
                    MainNavTab.HOME, MainNavTab.TASKS, MainNavTab.CALENDAR -> {
                        FloatingActionButton(
                            onClick = {
                                editingTask = null
                                showTaskEditor = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_task")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = copy.quickNewTask)
                        }
                    }

                    MainNavTab.NOTES -> {
                        FloatingActionButton(
                            onClick = {
                                editingNote = null
                                showNoteEditor = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_note")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = copy.quickNewNote)
                        }
                    }

                    MainNavTab.PROJECTS -> {
                        FloatingActionButton(
                            onClick = {
                                editingProject = null
                                showProjectEditor = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_project")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Buat proyek")
                        }
                    }

                    MainNavTab.MORE -> Unit
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    activeTab = tab
                                    if (tab != MainNavTab.MORE) moreSection = MoreSection.MENU
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tabLabel(tab)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tabLabel(tab),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    activeTab = tab
                                    if (tab != MainNavTab.MORE) moreSection = MoreSection.MENU
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tabLabel(tab)
                                    )
                                },
                                label = { Text(tabLabel(tab), maxLines = 1) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (activeTab) {
                        MainNavTab.HOME -> HomeDashboardScreen(
                            displayName = userProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName,
                            tasksState = tasksState,
                            notesState = notesState,
                            projects = projectsList,
                            syncMetadata = syncMetadata,
                            language = settings.language,
                            onOpenSyncCenter = {
                                activeTab = MainNavTab.MORE
                                moreSection = MoreSection.SYNC_BACKUP
                            },
                            onCreateTask = {
                                editingTask = null
                                showTaskEditor = true
                            },
                            onCreateNote = {
                                editingNote = null
                                showNoteEditor = true
                            },
                            onEditTask = { task ->
                                editingTask = task
                                showTaskEditor = true
                            },
                            onToggleTask = { viewModel.toggleTaskCompletion(it) },
                            onOpenNote = { note ->
                                editingNote = note
                                showNoteEditor = true
                            }
                        )

                        MainNavTab.TASKS -> TasksScreen(
                            tasksState = tasksState,
                            projects = projectsList,
                            language = settings.language,
                            onCreateTask = {
                                editingTask = null
                                showTaskEditor = true
                            },
                            onEditTask = { task ->
                                editingTask = task
                                showTaskEditor = true
                            },
                            onToggleComplete = { viewModel.toggleTaskCompletion(it) },
                            onDuplicateTask = { viewModel.duplicateTask(it) },
                            onArchiveTask = { task, archive -> viewModel.archiveTask(task, archive) },
                            onDeleteTask = { viewModel.deleteTask(it) }
                        )

                        MainNavTab.NOTES -> NotesScreen(
                            notesState = notesState,
                            projects = projectsList,
                            language = settings.language,
                            onCreateNote = {
                                editingNote = null
                                showNoteEditor = true
                            },
                            onOpenNote = { note ->
                                editingNote = note
                                showNoteEditor = true
                            },
                            onUpdateFlags = { note, pin, fav, arch, del ->
                                viewModel.updateNoteFlags(note, pin, fav, arch, del)
                            }
                        )

                        MainNavTab.CALENDAR -> CalendarScreen(
                            tasksState = tasksState,
                            projects = projectsList,
                            language = settings.language,
                            onEditTask = { task ->
                                editingTask = task
                                showTaskEditor = true
                            },
                            onToggleComplete = { viewModel.toggleTaskCompletion(it) },
                            onRescheduleTask = { task, newDue -> viewModel.rescheduleTask(task, newDue) }
                        )

                        MainNavTab.PROJECTS -> ProjectsScreen(
                            projectsState = projectsState,
                            tasks = tasksList,
                            notes = notesList,
                            language = settings.language,
                            onCreateProject = {
                                editingProject = null
                                showProjectEditor = true
                            },
                            onEditProject = { project ->
                                editingProject = project
                                showProjectEditor = true
                            },
                            onToggleArchive = { viewModel.toggleArchiveProject(it) },
                            onSafeDeleteProject = { viewModel.safeDeleteProject(it) }
                        )

                        MainNavTab.MORE -> MoreHubScreen(
                            activeSection = moreSection,
                            onSelectSection = { moreSection = it },
                            userProfile = userProfile,
                            userEmail = userEmail,
                            settings = settings,
                            tasks = tasksList,
                            projects = projectsList,
                            remindersState = remindersState,
                            backupsState = backupsState,
                            syncMetadata = syncMetadata,
                            syncLogs = syncLogs,
                            conflicts = conflicts,
                            pendingRestorePreview = pendingRestorePreview,
                            isBusy = isBusy,
                            onSnoozeReminder = { viewModel.snoozeReminder(it) },
                            onDismissReminder = { viewModel.dismissReminder(it) },
                            onManualSync = { viewModel.triggerManualSync() },
                            onResolveConflict = { c, t, body, lbl -> viewModel.resolveConflict(c, t, body, lbl) },
                            onCreateCloudBackup = { viewModel.createCloudBackup(it) },
                            onBuildExportJson = { viewModel.buildExportJson(it) },
                            onInspectImportJson = { viewModel.inspectImportJson(it) },
                            onInspectCloudBackup = { viewModel.inspectCloudBackup(it) },
                            onConfirmRestore = { viewModel.confirmRestorePreview() },
                            onDismissRestore = { viewModel.dismissRestorePreview() },
                            onShowMessage = { viewModel.showMessage(it) },
                            onThemeModeChange = { viewModel.setThemeMode(it) },
                            onLanguageChange = { viewModel.setLanguage(it) },
                            onLargeTextChange = { viewModel.setLargeText(it) },
                            onQuietHoursChange = { en, s, e -> viewModel.setQuietHours(en, s, e) },
                            onDailySummaryChange = { viewModel.setDailySummaryEnabled(it) },
                            onSignOut = {
                                if (isLocalMode) {
                                    TaskFlowLocalMode.disable(context)
                                    onExitLocalMode()
                                }
                                signOut(credentialManager = credentialManager, scope = scope)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showTaskEditor) {
        val currentTask = editingTask
        val linkedNotes = remember(notesList, currentTask?.id) {
            if (currentTask == null) emptyList()
            else notesList.filter { !it.deleted && currentTask.id in it.linkedTaskIds }
        }
        TaskEditorSheet(
            existingTask = currentTask,
            projects = projectsList,
            linkedNotes = linkedNotes,
            language = settings.language,
            onDismiss = {
                showTaskEditor = false
                editingTask = null
            },
            onSave = { title, desc, status, priority, category, projectId, dueMillis, remMillis, recurrence, tags, subtasks ->
                viewModel.saveTask(
                    existing = currentTask,
                    title = title,
                    description = desc,
                    status = status,
                    priority = priority,
                    category = category,
                    projectId = projectId,
                    dueAtMillis = dueMillis,
                    reminderAtMillis = remMillis,
                    recurrence = recurrence,
                    tags = tags,
                    subtasks = subtasks,
                    onSaved = {
                        showTaskEditor = false
                        editingTask = null
                    }
                )
            }
        )
    }

    if (showProjectEditor) {
        ProjectEditorSheet(
            existingProject = editingProject,
            language = settings.language,
            onDismiss = {
                showProjectEditor = false
                editingProject = null
            },
            onSave = { name, desc, colorHex ->
                viewModel.saveProject(
                    existing = editingProject,
                    name = name,
                    description = desc,
                    colorHex = colorHex,
                    onSaved = {
                        showProjectEditor = false
                        editingProject = null
                    }
                )
            }
        )
    }
}
