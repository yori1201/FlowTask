package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.SyncConflictEntity
import com.example.data.local.SyncLogEntity
import com.example.model.AppLanguage
import com.example.model.BackupPreview
import com.example.model.BackupSnapshot
import com.example.model.SyncMetadata
import com.example.model.UiState
import com.example.ui.components.DateFormatters
import com.example.ui.components.SyncStatusBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncBackupScreen(
    syncMetadata: SyncMetadata,
    syncLogs: List<SyncLogEntity>,
    conflicts: List<SyncConflictEntity>,
    backupsState: UiState<List<BackupSnapshot>>,
    pendingRestorePreview: BackupPreview?,
    lastBackupAtMillis: Long?,
    isBusy: Boolean,
    language: AppLanguage,
    onManualSync: () -> Unit,
    onResolveConflict: (SyncConflictEntity, String, String, String) -> Unit,
    onCreateCloudBackup: (String) -> Unit,
    onBuildExportJson: (String) -> String,
    onInspectImportJson: (String) -> Unit,
    onInspectCloudBackup: (BackupSnapshot) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                val json = onBuildExportJson("Export Lokal $stamp")
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
                onShowMessage("File backup JSON berhasil diekspor.")
            } catch (e: Exception) {
                onShowMessage("Gagal mengekspor file backup: ${e.localizedMessage ?: "error"}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    onInspectImportJson(content)
                } else {
                    onShowMessage("File backup kosong.")
                }
            } catch (e: Exception) {
                onShowMessage("Gagal membaca file backup: ${e.localizedMessage ?: "error"}")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (language == AppLanguage.ID) "Sinkronisasi & Backup" else "Sync & Backup",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }

            // Live Sync Engine Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (language == AppLanguage.ID) "Status Sinkronisasi Cloud" else "Cloud Sync Status",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = syncMetadata.lastSyncedAtMillis?.let {
                                        "Terakhir: ${DateFormatters.formatDateTime(it)}"
                                    } ?: "Menunggu verifikasi server",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SyncStatusBadge(
                                metadata = syncMetadata,
                                language = language,
                                onClick = onManualSync
                            )
                        }

                        if (syncMetadata.lastErrorMessage != null) {
                            Text(
                                text = syncMetadata.lastErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = onManualSync,
                            enabled = !isBusy,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_sync_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (language == AppLanguage.ID) "Sinkronkan Sekarang" else "Sync Now")
                        }
                    }
                }
            }

            // Conflict Resolution Center (PRD 6.8 — NEVER silent overwrite!)
            if (conflicts.isNotEmpty()) {
                item {
                    Text(
                        text = if (language == AppLanguage.ID) "Konflik Perubahan (${conflicts.size})" else "Detected Conflicts (${conflicts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                items(conflicts, key = { it.conflictId }) { conflict ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), MaterialTheme.shapes.large)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${conflict.entityType}: ${conflict.entityTitle}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("Versi Lokal (rev #${conflict.localRevision})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(conflict.localTitle, style = MaterialTheme.typography.titleSmall)
                                    if (conflict.localContent.isNotBlank()) {
                                        Text(conflict.localContent.take(140), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("Versi Cloud (rev #${conflict.cloudRevision})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                    Text(conflict.cloudTitle, style = MaterialTheme.typography.titleSmall)
                                    if (conflict.cloudContent.isNotBlank()) {
                                        Text(conflict.cloudContent.take(140), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onResolveConflict(conflict, conflict.localTitle, conflict.localContent, "Gunakan Versi Lokal")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Lokal")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onResolveConflict(conflict, conflict.cloudTitle, conflict.cloudContent, "Gunakan Versi Cloud")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cloud")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val mergedContent = buildString {
                                            append(conflict.localContent)
                                            if (conflict.cloudContent.isNotBlank() && conflict.cloudContent != conflict.localContent) {
                                                append("\n\n--- Cloud ---\n")
                                                append(conflict.cloudContent)
                                            }
                                        }
                                        onResolveConflict(conflict, conflict.localTitle, mergedContent, "Gabung Manual")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Gabung")
                                }
                            }
                        }
                    }
                }
            }

            // Backup & Restore Actions (Cloud + Local SAF JSON)
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Backup, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (language == AppLanguage.ID) "Backup & Pemulihan Data" else "Backup & Recovery",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = lastBackupAtMillis?.let {
                                        "Backup terakhir: ${DateFormatters.formatDateTime(it)}"
                                    } ?: "Belum ada backup cloud",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val label = "Backup ${SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date())}"
                                onCreateCloudBackup(label)
                            },
                            enabled = !isBusy,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_cloud_backup_button")
                        ) {
                            Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (language == AppLanguage.ID) "Buat Backup Cloud" else "Create Cloud Backup")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportLauncher.launch("taskflow_backup_$stamp.json")
                                },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_json_backup_button")
                            ) {
                                Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (language == AppLanguage.ID) "Export JSON" else "Export JSON")
                            }
                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_json_backup_button")
                            ) {
                                Icon(Icons.Rounded.UploadFile, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (language == AppLanguage.ID) "Import JSON" else "Import JSON")
                            }
                        }
                    }
                }
            }

            // Cloud Backups History
            val backups = (backupsState as? UiState.Success)?.data.orEmpty()
            if (backups.isNotEmpty()) {
                item {
                    Text(
                        text = if (language == AppLanguage.ID) "Riwayat Backup Cloud (${backups.size})" else "Cloud Backup History (${backups.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(backups, key = { it.id }) { snap ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(snap.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${snap.taskCount} tugas · ${snap.noteCount} catatan · ${snap.projectCount} proyek",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "v${snap.formatVersion} · sha256:${snap.checksum.take(10)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            OutlinedButton(onClick = { onInspectCloudBackup(snap) }) {
                                Icon(Icons.Rounded.Restore, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pulihkan")
                            }
                        }
                    }
                }
            }

            // Local Room Sync Audit Log
            if (syncLogs.isNotEmpty()) {
                item {
                    Text(
                        text = if (language == AppLanguage.ID) "Log Aktivitas Sinkronisasi Lokal" else "Local Sync Activity Log",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(syncLogs.take(15), key = { it.id }) { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${log.entityType} · ${log.action} (${log.status})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = log.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = DateFormatters.formatTimeOnly(log.createdAtMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // PRD 6.9: Restore Validation Preview Dialog before confirming
    pendingRestorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = onDismissRestore,
            icon = {
                Icon(
                    imageVector = if (preview.checksumValid) Icons.Rounded.VerifiedUser else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = if (preview.checksumValid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Pratinjau Pemulihan Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Label: ${preview.label}", style = MaterialTheme.typography.titleSmall)
                    Text("Format v${preview.formatVersion} · Checksum: ${if (preview.checksumValid) "Valid ✓" else "Tidak terverifikasi"}")
                    Text("• ${preview.projects.size} proyek\n• ${preview.tasks.size} tugas\n• ${preview.notes.size} catatan")
                    Text(
                        text = "Pemulihan hanya menambahkan item yang belum ada (idempotent) dan tidak menimpa data aktif Anda secara diam-diam.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmRestore,
                    enabled = preview.checksumValid && !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Konfirmasi Pulihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestore) {
                    Text("Batal")
                }
            }
        )
    }
}
