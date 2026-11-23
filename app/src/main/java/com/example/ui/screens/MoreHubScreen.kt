package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.SyncConflictEntity
import com.example.data.local.SyncLogEntity
import com.example.data.local.UserSettings
import com.example.model.AppLanguage
import com.example.model.BackupPreview
import com.example.model.BackupSnapshot
import com.example.model.ProjectItem
import com.example.model.ReminderEvent
import com.example.model.SyncMetadata
import com.example.model.TaskItem
import com.example.model.TaskStatus
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.DateFormatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.parseHexColor
import com.example.ui.i18n.copyFor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MoreSection { MENU, REMINDERS, INSIGHTS, SYNC_BACKUP, SETTINGS }

@Composable
fun MoreHubScreen(
    activeSection: MoreSection,
    onSelectSection: (MoreSection) -> Unit,
    userProfile: UserProfile?,
    userEmail: String?,
    settings: UserSettings,
    tasks: List<TaskItem>,
    projects: List<ProjectItem>,
    remindersState: UiState<List<ReminderEvent>>,
    backupsState: UiState<List<BackupSnapshot>>,
    syncMetadata: SyncMetadata,
    syncLogs: List<SyncLogEntity>,
    conflicts: List<SyncConflictEntity>,
    pendingRestorePreview: BackupPreview?,
    isBusy: Boolean,
    onSnoozeReminder: (ReminderEvent) -> Unit,
    onDismissReminder: (ReminderEvent) -> Unit,
    onManualSync: () -> Unit,
    onResolveConflict: (SyncConflictEntity, String, String, String) -> Unit,
    onCreateCloudBackup: (String) -> Unit,
    onBuildExportJson: (String) -> String,
    onInspectImportJson: (String) -> Unit,
    onInspectCloudBackup: (BackupSnapshot) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    onShowMessage: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onLargeTextChange: (Boolean) -> Unit,
    onQuietHoursChange: (Boolean, Int, Int) -> Unit,
    onDailySummaryChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = settings.language
    val copy = remember(language) { copyFor(language) }

    if (activeSection != MoreSection.MENU) {
        BackHandler { onSelectSection(MoreSection.MENU) }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (activeSection) {
            MoreSection.MENU -> {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = copy.navMore,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Account summary banner
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userProfile?.displayName?.ifBlank { "Pengguna TaskFlow" } ?: "Pengguna TaskFlow",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = userEmail ?: userProfile?.email ?: "Akun Google Terverifikasi",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        MoreMenuRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = copy.moreReminders,
                            subtitle = if (language == AppLanguage.ID) "Jadwal pengingat lokal, Quiet Hours & riwayat" else "Local reminders, Quiet Hours & history",
                            badge = (remindersState as? UiState.Success)?.data?.count { !it.dismissed }?.takeIf { it > 0 }?.toString(),
                            onClick = { onSelectSection(MoreSection.REMINDERS) },
                            testTag = "more_reminders_item"
                        )
                    }

                    item {
                        MoreMenuRow(
                            icon = Icons.Rounded.BarChart,
                            title = copy.moreInsights,
                            subtitle = if (language == AppLanguage.ID) "Statistik aktual penyelesaian tugas & tren 7 hari" else "Actual task completion stats & 7-day trend",
                            onClick = { onSelectSection(MoreSection.INSIGHTS) },
                            testTag = "more_insights_item"
                        )
                    }

                    item {
                        MoreMenuRow(
                            icon = Icons.Rounded.CloudSync,
                            title = copy.moreSyncBackup,
                            subtitle = if (language == AppLanguage.ID) "Sinkronisasi cloud, resolusi konflik & backup JSON" else "Cloud sync, conflict resolution & JSON backup",
                            badge = conflicts.size.takeIf { it > 0 }?.let { "$it!" },
                            onClick = { onSelectSection(MoreSection.SYNC_BACKUP) },
                            testTag = "more_sync_backup_item"
                        )
                    }

                    item {
                        MoreMenuRow(
                            icon = Icons.Rounded.Settings,
                            title = copy.moreSettings,
                            subtitle = if (language == AppLanguage.ID) "Tema, bahasa Indonesia/English, ukuran teks & keluar" else "Theme, language, text size & sign out",
                            onClick = { onSelectSection(MoreSection.SETTINGS) },
                            testTag = "more_settings_item"
                        )
                    }
                }
            }

            MoreSection.REMINDERS -> ReminderCenterSection(
                remindersState = remindersState,
                tasks = tasks,
                settings = settings,
                onSnooze = onSnoozeReminder,
                onDismiss = onDismissReminder,
                onQuietHoursChange = onQuietHoursChange,
                onDailySummaryChange = onDailySummaryChange,
                onBack = { onSelectSection(MoreSection.MENU) }
            )

            MoreSection.INSIGHTS -> ProductivityInsightsSection(
                tasks = tasks,
                projects = projects,
                language = language,
                onBack = { onSelectSection(MoreSection.MENU) }
            )

            MoreSection.SYNC_BACKUP -> SyncBackupScreen(
                syncMetadata = syncMetadata,
                syncLogs = syncLogs,
                conflicts = conflicts,
                backupsState = backupsState,
                pendingRestorePreview = pendingRestorePreview,
                lastBackupAtMillis = userProfile?.lastBackupAtMillis,
                isBusy = isBusy,
                language = language,
                onManualSync = onManualSync,
                onResolveConflict = onResolveConflict,
                onCreateCloudBackup = onCreateCloudBackup,
                onBuildExportJson = onBuildExportJson,
                onInspectImportJson = onInspectImportJson,
                onInspectCloudBackup = onInspectCloudBackup,
                onConfirmRestore = onConfirmRestore,
                onDismissRestore = onDismissRestore,
                onShowMessage = onShowMessage,
                onBack = { onSelectSection(MoreSection.MENU) }
            )

            MoreSection.SETTINGS -> SettingsAccountSection(
                userProfile = userProfile,
                userEmail = userEmail,
                settings = settings,
                onThemeModeChange = onThemeModeChange,
                onLanguageChange = onLanguageChange,
                onLargeTextChange = onLargeTextChange,
                onSignOut = onSignOut,
                onBack = { onSelectSection(MoreSection.MENU) }
            )
        }
    }
}

@Composable
private fun MoreMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondary)
                }
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReminderCenterSection(
    remindersState: UiState<List<ReminderEvent>>,
    tasks: List<TaskItem>,
    settings: UserSettings,
    onSnooze: (ReminderEvent) -> Unit,
    onDismiss: (ReminderEvent) -> Unit,
    onQuietHoursChange: (Boolean, Int, Int) -> Unit,
    onDailySummaryChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPermission = granted }

    val activeTaskReminders = remember(tasks) {
        tasks.filter { !it.archived && it.taskStatus != TaskStatus.COMPLETED && it.reminderAtMillis != null }
            .sortedBy { it.reminderAtMillis }
    }
    val reminderHistory = (remindersState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 840.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                }
                Text("Pusat Pengingat", style = MaterialTheme.typography.headlineSmall)
            }
        }

        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Izin Notifikasi Diperlukan", style = MaterialTheme.typography.titleSmall)
                            Text("Aktifkan izin notifikasi Android agar pengingat otomatis muncul tepat waktu.", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Izinkan")
                        }
                    }
                }
            }
        }

        // Quiet Hours & Daily Summary
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quiet Hours (${settings.quietStartHour}:00 - ${settings.quietEndHour}:00)", style = MaterialTheme.typography.titleSmall)
                            Text("Tunda pengingat lokal selama jam istirahat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.quietHoursEnabled,
                            onCheckedChange = { onQuietHoursChange(it, settings.quietStartHour, settings.quietEndHour) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ringkasan Harian", style = MaterialTheme.typography.titleSmall)
                            Text("Rangkuman jadwal tugas setiap pagi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.dailySummaryEnabled,
                            onCheckedChange = onDailySummaryChange
                        )
                    }
                }
            }
        }

        item {
            Text("Pengingat Tugas Terjadwal (${activeTaskReminders.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (activeTaskReminders.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Belum ada pengingat aktif",
                    subtitle = "Atur waktu pengingat saat membuat atau mengedit tugas."
                )
            }
        } else {
            items(activeTaskReminders, key = { "sched_${it.id}" }) { task ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                            task.reminderAtMillis?.let {
                                Text(DateFormatters.formatDateTime(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        if (reminderHistory.isNotEmpty()) {
            item {
                Text("Riwayat Notifikasi (${reminderHistory.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(reminderHistory.take(15), key = { it.id }) { event ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleSmall)
                            Text(event.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(DateFormatters.formatDateTime(event.scheduledAtMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (!event.dismissed) {
                            Row {
                                IconButton(onClick = { onSnooze(event) }) {
                                    Icon(Icons.Rounded.Snooze, contentDescription = "Tunda 15m")
                                }
                                TextButton(onClick = { onDismiss(event) }) {
                                    Text("Tutup")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductivityInsightsSection(
    tasks: List<TaskItem>,
    projects: List<ProjectItem>,
    language: AppLanguage,
    onBack: () -> Unit
) {
    val active = remember(tasks) { tasks.filter { !it.archived } }
    val completed = remember(active) { active.count { it.taskStatus == TaskStatus.COMPLETED } }
    val pending = active.size - completed
    val overdue = remember(active) { active.count { DateFormatters.isTaskOverdue(it) } }
    val completionPct = if (active.isEmpty()) 0 else ((completed * 100f) / active.size).toInt()

    // Real 7-day completion trend from completedAtMillis
    val last7Days = remember(active) {
        val today = Calendar.getInstance()
        (6 downTo 0).map { daysAgo ->
            val cal = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val label = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(cal.timeInMillis))
            val count = active.count { t ->
                val doneAt = t.completedAtMillis ?: return@count false
                DateFormatters.isSameDay(doneAt, cal.timeInMillis)
            }
            label to count
        }
    }

    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 840.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                }
                Text(
                    if (language == AppLanguage.ID) "Ringkasan Produktivitas" else "Productivity Insights",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        // Actual KPI Bento Row (PRD 6.10 — real computed counts only)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightStatCard("Selesai", "$completed", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                InsightStatCard("Tertunda", "$pending", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                InsightStatCard("Terlambat", "$overdue", MaterialTheme.colorScheme.error, Modifier.weight(1f))
                InsightStatCard("Rasio", "$completionPct%", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }

        // 7-Day Real Completion Trend Bar Chart
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (language == AppLanguage.ID) "Tren Penyelesaian 7 Hari Terakhir" else "7-Day Completion Trend",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    val maxVal = (last7Days.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        val slotWidth = size.width / last7Days.size
                        val barWidth = slotWidth * 0.48f
                        last7Days.forEachIndexed { idx, (_, count) ->
                            val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(x, 0f),
                                size = Size(barWidth, size.height),
                                cornerRadius = CornerRadius(12f, 12f)
                            )
                            if (count > 0) {
                                val h = (count.toFloat() / maxVal) * size.height
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(x, size.height - h),
                                    size = Size(barWidth, h),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        last7Days.forEach { (dayLabel, count) ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Project Progress Breakdown
        if (projects.isNotEmpty()) {
            item {
                Text(
                    text = if (language == AppLanguage.ID) "Progres Proyek" else "Project Progress",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(projects.filter { !it.archived }, key = { "ins_${it.id}" }) { proj ->
                val projTasks = active.filter { it.projectId == proj.id }
                val projDone = projTasks.count { it.taskStatus == TaskStatus.COMPLETED }
                val ratio = if (projTasks.isEmpty()) 0f else projDone.toFloat() / projTasks.size
                val accent = parseHexColor(proj.colorHex)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(proj.name, style = MaterialTheme.typography.titleSmall)
                            Text("$projDone/${projTasks.size}", style = MaterialTheme.typography.labelMedium, color = accent)
                        }
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = accent,
                            trackColor = accent.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightStatCard(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsAccountSection(
    userProfile: UserProfile?,
    userEmail: String?,
    settings: UserSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onLargeTextChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .widthIn(max = 840.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                }
                Text("Pengaturan & Akun", style = MaterialTheme.typography.headlineSmall)
            }
        }

        // Theme & Typography
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Tampilan (Light / Dark Mode)", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                            label = { Text("Sistem") }
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            label = { Text("Terang") }
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            label = { Text("Gelap") }
                        )
                    }

                    Text("Bahasa / Language", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.language == AppLanguage.ID,
                            onClick = { onLanguageChange(AppLanguage.ID) },
                            label = { Text("Indonesia") }
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.EN,
                            onClick = { onLanguageChange(AppLanguage.EN) },
                            label = { Text("English") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Teks Lebih Besar", style = MaterialTheme.typography.titleSmall)
                            Text("Perbesar skala tipografi untuk keterbacaan ekstra.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.largeText, onCheckedChange = onLargeTextChange)
                    }
                }
            }
        }

        // Account & Device Info
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Akun & Perangkat", style = MaterialTheme.typography.titleSmall)
                    Text("Nama: ${userProfile?.displayName ?: "Pengguna TaskFlow"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Email: ${userEmail ?: userProfile?.email ?: "-"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Device ID: ${settings.deviceId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Keluar dari Akun")
                    }
                }
            }
        }
    }
}
