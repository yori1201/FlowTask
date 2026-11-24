package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.backup.LocalBackupCodec
import com.example.data.local.LocalTaskFlowStore
import com.example.data.local.NoteDraftEntity
import com.example.data.local.SyncConflictEntity
import com.example.data.local.SyncLogEntity
import com.example.data.local.TaskFlowLocalDatabase
import com.example.data.local.TaskFlowLocalMode
import com.example.model.BackupPreview
import com.example.model.BackupSnapshot
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.ReminderEvent
import com.example.model.ReminderType
import com.example.model.SubtaskItem
import com.example.model.SyncMetadata
import com.example.model.SyncState
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Kenaikan revision cloud terdeteksi di dalam transaksi (C-01). */
internal class RevisionConflictException(
    val entityId: String,
    val remoteRevision: Long,
    val localRevision: Long,
    val remoteTitle: String,
    val remoteContent: String
) : Exception("Cloud revision #$remoteRevision lebih baru dari lokal #$localRevision untuk $entityId")

/** Hasil restore: jumlah item yang dipulihkan dan yang dilewati (C-05). */
data class RestoreResult(val restoredCount: Int, val skippedCount: Int)

class TaskFlowRepository(
    private val db: FirebaseFirestore,
    private val localDb: TaskFlowLocalDatabase,
    private val auth: FirebaseAuth = Firebase.auth,
    private val isLocalMode: Boolean = false
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ).apply {
            try {
                firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                }
            } catch (_: IllegalStateException) {
                // Settings already initialized on this singleton instance
            }
        },
        localDb = TaskFlowLocalDatabase.getInstance(context.applicationContext),
        isLocalMode = TaskFlowLocalMode.isEnabled(context.applicationContext)
    )

    private val _syncMetadata = MutableStateFlow(SyncMetadata())
    val syncMetadata: StateFlow<SyncMetadata> = _syncMetadata.asStateFlow()

    init {
        if (isLocalMode) {
            _syncMetadata.value = SyncMetadata(state = SyncState.LOCAL_ONLY)
        }
    }

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) { "Pengguna harus masuk dengan Google sebelum mengakses data." }
        return uid
    }

    private fun updateSyncSnapshotState(hasPendingWrites: Boolean, isFromCache: Boolean) {
        val current = _syncMetadata.value
        val nextState = when {
            hasPendingWrites && isFromCache -> SyncState.PENDING_SYNC
            hasPendingWrites -> SyncState.SYNCING
            isFromCache -> SyncState.LOCAL_ONLY
            else -> SyncState.SYNCED
        }
        _syncMetadata.value = current.copy(
            state = nextState,
            isFromCache = isFromCache,
            pendingWritesCount = if (hasPendingWrites) maxOf(1, current.pendingWritesCount) else 0,
            lastSyncedAtMillis = if (!hasPendingWrites && !isFromCache) System.currentTimeMillis() else current.lastSyncedAtMillis,
            lastErrorMessage = if (!hasPendingWrites && !isFromCache) null else current.lastErrorMessage
        )
    }

    private suspend fun recordLog(
        uid: String,
        entityType: String,
        entityId: String,
        action: String,
        status: String,
        detail: String
    ) {
        try {
            localDb.syncLogDao().insertLog(
                SyncLogEntity(
                    userId = uid,
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    status = status,
                    detail = detail
                )
            )
            // C-08: pangkas riwayat log lebih dari 30 hari agar tabel
            // sync_logs tidak tumbuh tanpa batas.
            localDb.syncLogDao().deleteOlderThan(System.currentTimeMillis() - SYNC_LOG_RETENTION_MILLIS)
        } catch (_: Exception) {
            // Best-effort local audit log
        }
    }

    // --- LOCAL ROOM OBSERVERS ---
    fun observeSyncLogs(userId: String): Flow<List<SyncLogEntity>> =
        localDb.syncLogDao().observeLogs(userId)

    fun observeConflicts(userId: String): Flow<List<SyncConflictEntity>> =
        localDb.syncConflictDao().observeOpenConflicts(userId)

    suspend fun saveNoteDraftLocally(
        userId: String,
        noteId: String?,
        title: String,
        content: String,
        category: String,
        projectId: String?
    ) {
        val key = "${userId}_${noteId ?: "new"}"
        localDb.noteDraftDao().saveDraft(
            NoteDraftEntity(
                draftKey = key,
                userId = userId,
                noteId = noteId,
                title = title,
                content = content,
                category = category,
                projectId = projectId,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun getNoteDraftLocally(userId: String, noteId: String?): NoteDraftEntity? {
        val key = "${userId}_${noteId ?: "new"}"
        return localDb.noteDraftDao().getDraft(key)
    }

    suspend fun clearNoteDraftLocally(userId: String, noteId: String?) {
        val key = "${userId}_${noteId ?: "new"}"
        localDb.noteDraftDao().deleteDraft(key)
    }

    // --- USER PROFILE ---
    suspend fun ensureUserProfile(): Result<Unit> {
        if (isLocalMode) return localEnsureUserProfile()
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val displayName = (user.displayName ?: user.email?.substringBefore("@") ?: "Pengguna TaskFlow")
                .trim().take(100).ifBlank { "Pengguna TaskFlow" }
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to user.email?.take(200),
                    "photoUrl" to user.photoUrl?.toString()?.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "displayName" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                user.email?.take(200)?.let { updateMap["email"] = it }
                user.photoUrl?.toString()?.take(500)?.let { updateMap["photoUrl"] = it }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (isLocalMode) return localStore.observeProfile(userId)
        return flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }
    }

    // --- REALTIME COLLECTION STREAMS ---
    fun observeTasks(userId: String): Flow<List<TaskItem>> {
        if (isLocalMode) return localStore.observeTasks(userId)
        return flow {
        val path = "users/$userId/tasks"
        emitAll(
            db.collection("users").document(userId).collection("tasks")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    updateSyncSnapshotState(
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                        isFromCache = snapshot.metadata.isFromCache()
                    )
                    snapshot.toObjects(
                        TaskItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { !it.deleted }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                        _syncMetadata.value = _syncMetadata.value.copy(
                            state = SyncState.SYNC_FAILED,
                            lastErrorMessage = toLog.localizedMessage
                        )
                    }
                    throw error
                }
        )
    }
    }

    fun observeNotes(userId: String): Flow<List<NoteItem>> {
        if (isLocalMode) return localStore.observeNotes(userId)
        return flow {
        val path = "users/$userId/notes"
        emitAll(
            db.collection("users").document(userId).collection("notes")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.toObjects(
                        NoteItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }
    }

    fun observeProjects(userId: String): Flow<List<ProjectItem>> {
        if (isLocalMode) return localStore.observeProjects(userId)
        return flow {
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ProjectItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { !it.deleted }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }
    }

    fun observeReminders(userId: String): Flow<List<ReminderEvent>> {
        if (isLocalMode) return localStore.observeReminders(userId)
        return flow {
        val path = "users/$userId/reminders"
        emitAll(
            db.collection("users").document(userId).collection("reminders")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReminderEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.scheduledAtMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }
    }

    fun observeBackups(userId: String): Flow<List<BackupSnapshot>> {
        if (isLocalMode) return localStore.observeBackups(userId)
        return flow {
        val path = "users/$userId/backups"
        emitAll(
            db.collection("users").document(userId).collection("backups")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BackupSnapshot::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }
    }

    // --- MANUAL SYNC CHECK ---
    suspend fun triggerManualSync(userId: String): Result<Unit> {
        if (isLocalMode) return localTriggerManualSync(userId)
        val path = "users/$userId/tasks"
        _syncMetadata.value = _syncMetadata.value.copy(state = SyncState.SYNCING)
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesi akun berubah." }
            db.waitForPendingWrites().await()
            val snap = db.collection("users").document(uid).collection("tasks").get().await()
            updateSyncSnapshotState(
                hasPendingWrites = snap.metadata.hasPendingWrites(),
                isFromCache = snap.metadata.isFromCache()
            )
            recordLog(uid, "SYNC", "workspace", "MANUAL_SYNC", "SYNCED", "Sinkronisasi cloud terverifikasi")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            _syncMetadata.value = _syncMetadata.value.copy(
                state = SyncState.SYNC_FAILED,
                lastErrorMessage = "Sinkronisasi belum berhasil. Data Anda tetap tersimpan di perangkat ini."
            )
            recordLog(userId, "SYNC", "workspace", "MANUAL_SYNC", "FAILED", e.localizedMessage ?: "Offline")
            Result.failure(e)
        }
    }

    // --- TASK CRUD ---
    suspend fun createTask(
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
        deviceId: String,
        explicitDocId: String? = null
    ): Result<TaskItem> {
        if (isLocalMode) return localCreateTask(
            title, description, status, priority, category, projectId,
            dueAtMillis, reminderAtMillis, recurrence, tags, subtasks, deviceId, explicitDocId
        )
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Judul tugas tidak boleh kosong." }
            val cleanDesc = description.trim().take(4000)
            val cleanCat = category.trim().take(40).ifEmpty { "Personal" }
            val cleanTags = tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10)
            val cleanSubtasks = subtasks.filter { it.title.isNotBlank() }.take(20)
            val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }

            val colRef = db.collection("users").document(uid).collection("tasks")
            val docRef = if (!explicitDocId.isNullOrBlank() && explicitDocId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                colRef.document(explicitDocId)
            } else {
                colRef.document("tsk_${UUID.randomUUID().toString().replace("-", "").take(16)}")
            }
            docPath = docRef.path

            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "title" to cleanTitle,
                "description" to cleanDesc,
                "status" to status.name,
                "priority" to priority.name,
                "category" to cleanCat,
                "projectId" to projectId?.takeIf { it.isNotBlank() },
                "dueAtMillis" to dueAtMillis,
                "reminderAtMillis" to reminderAtMillis,
                "completedAtMillis" to if (status == TaskStatus.COMPLETED) System.currentTimeMillis() else null,
                "recurrence" to recurrence.name,
                "tags" to cleanTags,
                "subtasks" to cleanSubtasks.map { it.toMap() },
                "archived" to false,
                "deleted" to false,
                "revision" to 1L,
                "deviceId" to cleanDevice,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            recordLog(uid, "TASK", docRef.id, "CREATE", "SYNCED", cleanTitle)

            if (reminderAtMillis != null) {
                logReminderEvent(
                    uid = uid,
                    taskId = docRef.id,
                    title = cleanTitle,
                    message = "Pengingat dijadwalkan untuk \"$cleanTitle\"",
                    type = ReminderType.TASK_REMINDER,
                    scheduledAtMillis = reminderAtMillis
                )
            }

            Result.success(
                TaskItem(
                    id = docRef.id,
                    userId = uid,
                    title = cleanTitle,
                    description = cleanDesc,
                    status = status.name,
                    priority = priority.name,
                    category = cleanCat,
                    projectId = projectId?.takeIf { it.isNotBlank() },
                    dueAtMillis = dueAtMillis,
                    reminderAtMillis = reminderAtMillis,
                    completedAtMillis = if (status == TaskStatus.COMPLETED) System.currentTimeMillis() else null,
                    recurrence = recurrence.name,
                    tags = cleanTags,
                    subtasks = cleanSubtasks,
                    revision = 1L,
                    deviceId = cleanDevice
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        existing: TaskItem,
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
        deviceId: String,
        forceOverwrite: Boolean = false
    ): Result<Boolean> {
        if (isLocalMode) return localUpdateTask(
            existing, title, description, status, priority, category, projectId,
            dueAtMillis, reminderAtMillis, recurrence, tags, subtasks, deviceId
        )
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${existing.id}"
        return try {
            val uid = requireUserId()
            require(existing.id.isNotBlank()) { "ID tugas tidak valid." }
            val cleanTitle = title.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Judul tugas tidak boleh kosong." }
            val cleanDesc = description.trim().take(4000)
            val cleanCat = category.trim().take(40).ifEmpty { "Personal" }
            val cleanTags = tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10)
            val cleanSubtasks = subtasks.filter { it.title.isNotBlank() }.take(20)
            val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }

            val docRef = db.collection("users").document(uid).collection("tasks").document(existing.id)
            docPath = docRef.path

            val updates = mutableMapOf<String, Any>(
                "title" to cleanTitle,
                "description" to cleanDesc,
                "status" to status.name,
                "priority" to priority.name,
                "category" to cleanCat,
                "projectId" to (projectId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
                "dueAtMillis" to (dueAtMillis ?: FieldValue.delete()),
                "reminderAtMillis" to (reminderAtMillis ?: FieldValue.delete()),
                "completedAtMillis" to (if (status == TaskStatus.COMPLETED) (existing.completedAtMillis ?: System.currentTimeMillis()) else FieldValue.delete()),
                "recurrence" to recurrence.name,
                "tags" to cleanTags,
                "subtasks" to cleanSubtasks.map { it.toMap() },
                "deviceId" to cleanDevice,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            // C-01: deteksi konflik dan penulisan revision kini atomik dalam satu
            // transaksi — revision dibaca dan dinaikkan di sisi server, bukan
            // dihitung klien dari snapshot yang bisa basi.
            try {
                db.runTransaction { tx ->
                    val serverSnap = tx.get(docRef)
                    val remoteRev = serverSnap.getLong("revision") ?: existing.revision
                    if (!forceOverwrite && remoteRev > existing.revision) {
                        val remoteTitle = serverSnap.getString("title") ?: existing.title
                        val remoteDesc = serverSnap.getString("description") ?: existing.description
                        if (remoteTitle != existing.title || remoteDesc != existing.description) {
                            throw RevisionConflictException(existing.id, remoteRev, existing.revision, remoteTitle, remoteDesc)
                        }
                    }
                    tx.update(docRef, updates + ("revision" to remoteRev + 1L))
                }.await()
            } catch (e: RevisionConflictException) {
                localDb.syncConflictDao().upsertConflict(
                    SyncConflictEntity(
                        conflictId = "task_${existing.id}_${e.remoteRevision}",
                        userId = uid,
                        entityType = "TASK",
                        entityId = existing.id,
                        entityTitle = existing.title,
                        localTitle = cleanTitle,
                        localContent = cleanDesc,
                        localRevision = existing.revision + 1L,
                        cloudTitle = e.remoteTitle,
                        cloudContent = e.remoteContent,
                        cloudRevision = e.remoteRevision
                    )
                )
                _syncMetadata.value = _syncMetadata.value.copy(state = SyncState.CONFLICT_DETECTED)
                recordLog(uid, "TASK", existing.id, "CONFLICT", "CONFLICT_DETECTED", "Revisi cloud #${e.remoteRevision} berbeda dari lokal #${existing.revision}")
                return Result.success(false)
            }
            recordLog(uid, "TASK", existing.id, "UPDATE", "SYNCED", "$cleanTitle (rev server +1)")

            if (reminderAtMillis != null && reminderAtMillis != existing.reminderAtMillis) {
                logReminderEvent(
                    uid = uid,
                    taskId = existing.id,
                    title = cleanTitle,
                    message = "Pengingat diperbarui untuk \"$cleanTitle\"",
                    type = ReminderType.TASK_REMINDER,
                    scheduledAtMillis = reminderAtMillis
                )
            }
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompletion(task: TaskItem, deviceId: String): Result<TaskItem?> {
        if (isLocalMode) return localToggleTaskCompletion(task, deviceId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val completing = task.taskStatus != TaskStatus.COMPLETED
            val nextStatus = if (completing) TaskStatus.COMPLETED else TaskStatus.TODO
            val colRef = db.collection("users").document(uid).collection("tasks")
            val docRef = colRef.document(task.id)
            docPath = docRef.path
            val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }
            var spawned: TaskItem? = null

            // C-01 + C-06: penandaan selesai dan spawn kemunculan berikutnya
            // terjadi dalam SATU transaksi. ID kemunculan deterministik membuat
            // spawn idempoten bila dua perangkat menyelesaikan hampir bersamaan.
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val remoteRev = snap.getLong("revision") ?: task.revision
                tx.update(
                    docRef,
                    mapOf(
                        "status" to nextStatus.name,
                        "completedAtMillis" to if (completing) System.currentTimeMillis() else FieldValue.delete(),
                        "revision" to remoteRev + 1L,
                        "deviceId" to cleanDevice,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                // PRD FR-026: tugas berulang men-spawn kemunculan berikutnya
                // sambil mempertahankan riwayat yang sudah selesai.
                if (completing && task.recurrenceRule != RecurrenceRule.NONE && snap.exists()) {
                    val parent = snap.toObject(TaskItem::class.java) ?: task
                    val nextDue = computeNextOccurrenceMillis(
                        parent.dueAtMillis ?: System.currentTimeMillis(),
                        task.recurrenceRule
                    )
                    val nextReminder = parent.reminderAtMillis?.let {
                        computeNextOccurrenceMillis(it, task.recurrenceRule)
                    }
                    val nextRef = colRef.document(buildOccurrenceDocId(task.id, nextDue))
                    if (!tx.get(nextRef).exists()) {
                        tx.set(nextRef, buildRecurrencePayload(uid, parent, nextDue, nextReminder, cleanDevice))
                        nextReminder?.let {
                            tx.set(
                                db.collection("users").document(uid).collection("reminders")
                                    .document(newReminderDocId()),
                                buildReminderEventPayload(uid, task.id, parent.title, it)
                            )
                        }
                        spawned = TaskItem(
                            id = nextRef.id,
                            userId = uid,
                            title = parent.title,
                            description = parent.description,
                            status = TaskStatus.TODO.name,
                            priority = parent.priority,
                            category = parent.category,
                            projectId = parent.projectId,
                            dueAtMillis = nextDue,
                            reminderAtMillis = nextReminder,
                            recurrence = parent.recurrence,
                            tags = parent.tags,
                            subtasks = parent.subtasks.map { st -> st.copy(done = false) },
                            deviceId = cleanDevice
                        )
                    }
                }
            }.await()
            recordLog(uid, "TASK", task.id, if (completing) "COMPLETE" else "REOPEN", "SYNCED", task.title)
            spawned?.let { recordLog(uid, "TASK", it.id, "SPAWN", "SYNCED", "Kemunculan berikutnya: ${it.title}") }
            Result.success(spawned)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * C-02: aksi "Selesai" dari notifikasi kini melewati jalur transaksi yang
     * sama dengan UI — revision dinaikkan dari nilai server dan deviceId
     * disertakan, sehingga perangkat lain mendeteksinya sebagai revisi baru.
     * Idempoten: bila tugas sudah selesai, tidak ada tulisan ulang. Tugas
     * berulang men-spawn kemunculan berikutnya dengan ID deterministik.
     */
    suspend fun completeTaskFromNotification(taskId: String, deviceId: String): Result<Unit> {
        if (isLocalMode) return localCompleteTaskFromNotification(taskId, deviceId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "ID tugas tidak valid." }
            val colRef = db.collection("users").document(uid).collection("tasks")
            val docRef = colRef.document(taskId)
            docPath = docRef.path
            val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }

            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) return@runTransaction
                val cloudStatus = snap.getString("status") ?: TaskStatus.TODO.name
                if (cloudStatus == TaskStatus.COMPLETED.name) return@runTransaction
                val remoteRev = snap.getLong("revision") ?: 1L
                tx.update(
                    docRef,
                    mapOf(
                        "status" to TaskStatus.COMPLETED.name,
                        "completedAtMillis" to System.currentTimeMillis(),
                        "revision" to remoteRev + 1L,
                        "deviceId" to cleanDevice,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val parent = snap.toObject(TaskItem::class.java) ?: return@runTransaction
                if (parent.recurrenceRule != RecurrenceRule.NONE) {
                    val nextDue = computeNextOccurrenceMillis(
                        parent.dueAtMillis ?: System.currentTimeMillis(),
                        parent.recurrenceRule
                    )
                    val nextReminder = parent.reminderAtMillis?.let {
                        computeNextOccurrenceMillis(it, parent.recurrenceRule)
                    }
                    val nextRef = colRef.document(buildOccurrenceDocId(taskId, nextDue))
                    if (!tx.get(nextRef).exists()) {
                        tx.set(nextRef, buildRecurrencePayload(uid, parent, nextDue, nextReminder, cleanDevice))
                        nextReminder?.let {
                            tx.set(
                                db.collection("users").document(uid).collection("reminders")
                                    .document(newReminderDocId()),
                                buildReminderEventPayload(uid, taskId, parent.title, it)
                            )
                        }
                    }
                }
            }.await()
            recordLog(uid, "TASK", taskId, "COMPLETE", "SYNCED", "Selesai dari notifikasi")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleSubtask(task: TaskItem, subtaskId: String, deviceId: String): Result<Unit> {
        if (isLocalMode) return localToggleSubtask(task, subtaskId, deviceId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val updated = task.subtasks.map {
                if (it.id == subtaskId) it.copy(done = !it.done) else it
            }
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "subtasks" to updated.map { it.toMap() },
                    "revision" to FieldValue.increment(1L),
                    "deviceId" to deviceId.ifBlank { "android" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun rescheduleTask(task: TaskItem, newDueMillis: Long?, deviceId: String): Result<Unit> {
        if (isLocalMode) return localRescheduleTask(task, newDueMillis, deviceId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "dueAtMillis" to (newDueMillis ?: FieldValue.delete()),
                    "revision" to FieldValue.increment(1L),
                    "deviceId" to deviceId.ifBlank { "android" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordLog(uid, "TASK", task.id, "RESCHEDULE", "SYNCED", task.title)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun archiveTask(task: TaskItem, archive: Boolean, deviceId: String): Result<Unit> {
        if (isLocalMode) return localArchiveTask(task, archive, deviceId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "archived" to archive,
                    "revision" to FieldValue.increment(1L),
                    "deviceId" to deviceId.ifBlank { "android" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordLog(uid, "TASK", task.id, if (archive) "ARCHIVE" else "UNARCHIVE", "SYNCED", task.title)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun softDeleteTask(task: TaskItem, deviceId: String): Result<Unit> {
        if (isLocalMode) return localSoftDeleteTask(task, deviceId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "deleted" to true,
                    "revision" to FieldValue.increment(1L),
                    "deviceId" to deviceId.ifBlank { "android" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordLog(uid, "TASK", task.id, "SOFT_DELETE", "SYNCED", task.title)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- NOTES CRUD ---
    suspend fun createNote(
        title: String,
        content: String,
        category: String,
        projectId: String?,
        tags: List<String>,
        linkedTaskIds: List<String>,
        deviceId: String,
        explicitDocId: String? = null
    ): Result<NoteItem> {
        if (isLocalMode) return localCreateNote(
            title, content, category, projectId, tags, linkedTaskIds, deviceId, explicitDocId
        )
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notes"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(160)
            val cleanContent = content.take(50000)
            val cleanCat = category.trim().take(40).ifEmpty { "Personal" }
            val cleanTags = tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10)
            val cleanLinks = linkedTaskIds.distinct().take(20)
            val words = countWords("$cleanTitle $cleanContent")
            val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }

            val colRef = db.collection("users").document(uid).collection("notes")
            val docRef = if (!explicitDocId.isNullOrBlank() && explicitDocId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                colRef.document(explicitDocId)
            } else {
                colRef.document("nte_${UUID.randomUUID().toString().replace("-", "").take(16)}")
            }
            docPath = docRef.path

            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "title" to cleanTitle,
                "content" to cleanContent,
                "category" to cleanCat,
                "projectId" to projectId?.takeIf { it.isNotBlank() },
                "tags" to cleanTags,
                "linkedTaskIds" to cleanLinks,
                "pinned" to false,
                "favorite" to false,
                "archived" to false,
                "deleted" to false,
                "wordCount" to words,
                "revision" to 1L,
                "deviceId" to cleanDevice,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            clearNoteDraftLocally(uid, null)
            recordLog(uid, "NOTE", docRef.id, "CREATE", "SYNCED", cleanTitle.ifBlank { "Catatan tanpa judul" })

            Result.success(
                NoteItem(
                    id = docRef.id,
                    userId = uid,
                    title = cleanTitle,
                    content = cleanContent,
                    category = cleanCat,
                    projectId = projectId?.takeIf { it.isNotBlank() },
                    tags = cleanTags,
                    linkedTaskIds = cleanLinks,
                    wordCount = words,
                    revision = 1L,
                    deviceId = cleanDevice
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        existing: NoteItem,
        title: String,
        content: String,
        category: String,
        projectId: String?,
        tags: List<String>,
        linkedTaskIds: List<String>,
        deviceId: String,
        forceOverwrite: Boolean = false
    ): Result<Boolean> {
        if (isLocalMode) return localUpdateNote(
            existing, title, content, category, projectId, tags, linkedTaskIds, deviceId
        )
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notes/${existing.id}"
        return try {
            val uid = requireUserId()
            require(existing.id.isNotBlank()) { "ID catatan tidak valid." }
            val cleanTitle = title.trim().take(160)
            val cleanContent = content.take(50000)
            val cleanCat = category.trim().take(40).ifEmpty { "Personal" }
            val cleanTags = tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10)
            val cleanLinks = linkedTaskIds.distinct().take(20)
            val words = countWords("$cleanTitle $cleanContent")
            val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }

            val docRef = db.collection("users").document(uid).collection("notes").document(existing.id)
            docPath = docRef.path

            // C-01: pola transaksi atomik yang sama dengan updateTask.
            try {
                db.runTransaction { tx ->
                    val snap = tx.get(docRef)
                    val remoteRev = snap.getLong("revision") ?: existing.revision
                    if (!forceOverwrite && remoteRev > existing.revision) {
                        val remoteTitle = snap.getString("title") ?: existing.title
                        val remoteContent = snap.getString("content") ?: existing.content
                        if (remoteTitle != existing.title || remoteContent != existing.content) {
                            throw RevisionConflictException(existing.id, remoteRev, existing.revision, remoteTitle, remoteContent)
                        }
                    }
                    tx.update(
                        docRef,
                        mapOf(
                            "title" to cleanTitle,
                            "content" to cleanContent,
                            "category" to cleanCat,
                            "projectId" to (projectId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
                            "tags" to cleanTags,
                            "linkedTaskIds" to cleanLinks,
                            "wordCount" to words,
                            "revision" to remoteRev + 1L,
                            "deviceId" to cleanDevice,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()
            } catch (e: RevisionConflictException) {
                localDb.syncConflictDao().upsertConflict(
                    SyncConflictEntity(
                        conflictId = "note_${existing.id}_${e.remoteRevision}",
                        userId = uid,
                        entityType = "NOTE",
                        entityId = existing.id,
                        entityTitle = existing.title.ifBlank { "Catatan" },
                        localTitle = cleanTitle,
                        localContent = cleanContent,
                        localRevision = existing.revision + 1L,
                        cloudTitle = e.remoteTitle,
                        cloudContent = e.remoteContent,
                        cloudRevision = e.remoteRevision
                    )
                )
                _syncMetadata.value = _syncMetadata.value.copy(state = SyncState.CONFLICT_DETECTED)
                recordLog(uid, "NOTE", existing.id, "CONFLICT", "CONFLICT_DETECTED", "Revisi cloud #${e.remoteRevision} berbeda")
                return Result.success(false)
            }
            clearNoteDraftLocally(uid, existing.id)
            recordLog(uid, "NOTE", existing.id, "UPDATE", "SYNCED", "${cleanTitle.ifBlank { "Catatan" }} (rev server +1)")
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateNoteFlags(
        note: NoteItem,
        pinned: Boolean = note.pinned,
        favorite: Boolean = note.favorite,
        archived: Boolean = note.archived,
        deleted: Boolean = note.deleted,
        deviceId: String
    ): Result<Unit> {
        if (isLocalMode) return localUpdateNoteFlags(note, pinned, favorite, archived, deleted, deviceId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notes/${note.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notes").document(note.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "pinned" to pinned,
                    "favorite" to favorite,
                    "archived" to archived,
                    "deleted" to deleted,
                    "revision" to FieldValue.increment(1L),
                    "deviceId" to deviceId.ifBlank { "android" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- PROJECTS CRUD ---
    suspend fun createProject(
        name: String,
        description: String?,
        colorHex: String,
        iconName: String = "folder",
        explicitDocId: String? = null
    ): Result<ProjectItem> {
        if (isLocalMode) return localCreateProject(name, description, colorHex, iconName, explicitDocId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Nama proyek tidak boleh kosong." }
            val cleanDesc = description?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
            val cleanColor = colorHex.trim().take(9).ifEmpty { "#0F5257" }
            val cleanIcon = iconName.trim().take(40).ifEmpty { "folder" }

            val colRef = db.collection("users").document(uid).collection("projects")
            val docRef = if (!explicitDocId.isNullOrBlank() && explicitDocId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                colRef.document(explicitDocId)
            } else {
                colRef.document("prj_${UUID.randomUUID().toString().replace("-", "").take(16)}")
            }
            docPath = docRef.path

            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "name" to cleanName,
                "description" to cleanDesc,
                "colorHex" to cleanColor,
                "iconName" to cleanIcon,
                "archived" to false,
                "deleted" to false,
                "revision" to 1L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            recordLog(uid, "PROJECT", docRef.id, "CREATE", "SYNCED", cleanName)
            Result.success(
                ProjectItem(
                    id = docRef.id,
                    userId = uid,
                    name = cleanName,
                    description = cleanDesc,
                    colorHex = cleanColor,
                    iconName = cleanIcon
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProject(
        existing: ProjectItem,
        name: String,
        description: String?,
        colorHex: String,
        archived: Boolean = existing.archived
    ): Result<Unit> {
        if (isLocalMode) return localUpdateProject(existing, name, description, colorHex, archived)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects/${existing.id}"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Nama proyek tidak boleh kosong." }
            val cleanDesc = description?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
            val docRef = db.collection("users").document(uid).collection("projects").document(existing.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "description" to (cleanDesc ?: FieldValue.delete()),
                    "colorHex" to colorHex.take(9),
                    "archived" to archived,
                    "revision" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * PRD 6.6: Deleting a project NEVER deletes linked tasks or notes automatically.
     * It unlinks them cleanly before soft-deleting the project.
     */
    suspend fun safeDeleteProject(
        project: ProjectItem,
        linkedTasks: List<TaskItem>,
        linkedNotes: List<NoteItem>,
        deviceId: String
    ): Result<Unit> {
        if (isLocalMode) return localSafeDeleteProject(project, linkedTasks, linkedNotes, deviceId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects/${project.id}"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            val userDoc = db.collection("users").document(uid)

            linkedTasks.forEach { t ->
                batch.update(
                    userDoc.collection("tasks").document(t.id),
                    mapOf(
                        "projectId" to FieldValue.delete(),
                        "revision" to FieldValue.increment(1L),
                        "deviceId" to deviceId.ifBlank { "android" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            linkedNotes.forEach { n ->
                batch.update(
                    userDoc.collection("notes").document(n.id),
                    mapOf(
                        "projectId" to FieldValue.delete(),
                        "revision" to FieldValue.increment(1L),
                        "deviceId" to deviceId.ifBlank { "android" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val projRef = userDoc.collection("projects").document(project.id)
            docPath = projRef.path
            batch.update(
                projRef,
                mapOf(
                    "deleted" to true,
                    "revision" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            recordLog(uid, "PROJECT", project.id, "SAFE_DELETE", "SYNCED", "Proyek dihapus, ${linkedTasks.size} tugas & ${linkedNotes.size} catatan tetap aman")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- REMINDER EVENTS ---
    suspend fun logReminderEvent(
        uid: String,
        taskId: String?,
        title: String,
        message: String,
        type: ReminderType,
        scheduledAtMillis: Long
    ): Result<Unit> {
        if (isLocalMode) return localLogReminderEvent(uid, taskId, title, message, type, scheduledAtMillis)
        var docPath = "users/$uid/reminders"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            val docRef = db.collection("users").document(uid).collection("reminders")
                .document("rem_${UUID.randomUUID().toString().replace("-", "").take(16)}")
            docPath = docRef.path
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "taskId" to taskId?.takeIf { it.isNotBlank() },
                "title" to title.trim().take(160).ifEmpty { "Pengingat" },
                "message" to message.trim().take(500),
                "reminderType" to type.name,
                "scheduledAtMillis" to scheduledAtMillis.coerceAtLeast(0L),
                "dismissed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun snoozeReminderEvent(event: ReminderEvent, delayMinutes: Int = 15): Result<Long> {
        if (isLocalMode) return localSnoozeReminderEvent(event, delayMinutes)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/${event.id}"
        return try {
            val uid = requireUserId()
            val newTime = System.currentTimeMillis() + delayMinutes * 60_000L
            val docRef = db.collection("users").document(uid).collection("reminders").document(event.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "snoozedUntilMillis" to newTime,
                    "scheduledAtMillis" to newTime,
                    "dismissed" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(newTime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun dismissReminderEvent(event: ReminderEvent): Result<Unit> {
        if (isLocalMode) return localDismissReminderEvent(event)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/${event.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("reminders").document(event.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "dismissed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- CONFLICT RESOLUTION ---
    suspend fun resolveConflict(
        conflict: SyncConflictEntity,
        chosenTitle: String,
        chosenContent: String,
        resolutionLabel: String,
        deviceId: String
    ): Result<Unit> {
        if (isLocalMode) return localResolveConflict(conflict, chosenTitle, chosenContent, resolutionLabel, deviceId)
        var docPath = "users/${conflict.userId}/${conflict.entityType.lowercase()}s/${conflict.entityId}"
        return try {
            val uid = requireUserId()
            val cleanDevice = deviceId.ifBlank { "android" }
            if (conflict.entityType == "TASK") {
                val ref = db.collection("users").document(uid).collection("tasks").document(conflict.entityId)
                docPath = ref.path
                // C-01: revision resolusi kini dihitung dari nilai server terbaru
                // di dalam transaksi agar tetap monoton bila cloud bergerak lagi.
                db.runTransaction { tx ->
                    val currentRev = tx.get(ref).getLong("revision") ?: 0L
                    tx.update(
                        ref,
                        mapOf(
                            "title" to chosenTitle.trim().take(160).ifEmpty { "Tugas" },
                            "description" to chosenContent.trim().take(4000),
                            "revision" to maxOf(currentRev, conflict.localRevision, conflict.cloudRevision) + 1L,
                            "deviceId" to cleanDevice,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()
            } else {
                val ref = db.collection("users").document(uid).collection("notes").document(conflict.entityId)
                docPath = ref.path
                db.runTransaction { tx ->
                    val currentRev = tx.get(ref).getLong("revision") ?: 0L
                    tx.update(
                        ref,
                        mapOf(
                            "title" to chosenTitle.trim().take(160),
                            "content" to chosenContent.take(50000),
                            "wordCount" to countWords("$chosenTitle $chosenContent"),
                            "revision" to maxOf(currentRev, conflict.localRevision, conflict.cloudRevision) + 1L,
                            "deviceId" to cleanDevice,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()
            }
            localDb.syncConflictDao().markResolved(conflict.conflictId)
            recordLog(uid, conflict.entityType, conflict.entityId, "CONFLICT_RESOLVED", "SYNCED", resolutionLabel)
            _syncMetadata.value = _syncMetadata.value.copy(state = SyncState.SYNCED)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- CLOUD BACKUP & VALIDATED RESTORE ---
    suspend fun createCloudBackup(
        label: String,
        projects: List<ProjectItem>,
        tasks: List<TaskItem>,
        notes: List<NoteItem>
    ): Result<BackupSnapshot> {
        if (isLocalMode) return localCreateCloudBackup(label, projects, tasks, notes)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/backups"
        return try {
            val uid = requireUserId()
            val activeProjects = projects.filter { !it.deleted }
            val activeTasks = tasks.filter { !it.deleted }
            val activeNotes = notes.filter { !it.deleted }
            val (_, checksum) = LocalBackupCodec.encodeToJson(label, activeProjects, activeTasks, activeNotes)
            val snapshotMap = LocalBackupCodec.buildNativeSnapshotMap(activeProjects, activeTasks, activeNotes)

            val docRef = db.collection("users").document(uid).collection("backups")
                .document("bkp_${UUID.randomUUID().toString().replace("-", "").take(16)}")
            docPath = docRef.path

            val payload = mapOf<String, Any>(
                "userId" to uid,
                "formatVersion" to LocalBackupCodec.CURRENT_FORMAT_VERSION.toLong(),
                "label" to label.trim().take(120).ifEmpty { "Cloud Backup" },
                "taskCount" to activeTasks.size.toLong(),
                "noteCount" to activeNotes.size.toLong(),
                "projectCount" to activeProjects.size.toLong(),
                "checksum" to checksum,
                "snapshotData" to snapshotMap,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            // Update lastBackupAtMillis on user profile
            try {
                db.collection("users").document(uid).update(
                    mapOf(
                        "lastBackupAtMillis" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
                // Best effort profile metadata update
            }

            recordLog(uid, "BACKUP", docRef.id, "CLOUD_BACKUP", "SYNCED", "$label (${activeTasks.size} tugas, ${activeNotes.size} catatan)")
            Result.success(
                BackupSnapshot(
                    id = docRef.id,
                    userId = uid,
                    formatVersion = LocalBackupCodec.CURRENT_FORMAT_VERSION.toLong(),
                    label = label,
                    taskCount = activeTasks.size.toLong(),
                    noteCount = activeNotes.size.toLong(),
                    projectCount = activeProjects.size.toLong(),
                    checksum = checksum,
                    snapshotData = snapshotMap
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * C-05: restore dengan WriteBatch ber-chunk (bukan N+1 round-trip),
     * dedup berbasis snapshot cloud SEGAR (bukan StateFlow yang bisa basi),
     * dan pengawetan flag archived/pinned/favorite/status. Dokumen cloud yang
     * masih hidup tidak pernah ditimpa; dokumen ter-soft-delete dihidupkan
     * kembali dengan revision monoton (aturan Firestore menjaga createdAt).
     */
    suspend fun restoreFromPreview(
        preview: BackupPreview,
        deviceId: String
    ): Result<RestoreResult> {
        if (isLocalMode) return localRestoreFromPreview(preview, deviceId)
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)

            suspend fun fetchExisting(collection: String): Map<String, RestorePlanner.ExistingDoc> =
                userDoc.collection(collection).get().await().documents.associate { doc ->
                    doc.id to RestorePlanner.ExistingDoc(
                        revision = doc.getLong("revision") ?: 1L,
                        deleted = doc.getBoolean("deleted") ?: false,
                        createdAt = doc.get("createdAt")
                    )
                }

            val plan = RestorePlanner.buildRestorePlan(
                uid = uid,
                deviceId = deviceId,
                preview = preview,
                existingProjects = fetchExisting("projects"),
                existingTasks = fetchExisting("tasks"),
                existingNotes = fetchExisting("notes")
            )

            var committed = 0
            plan.writes.chunked(BATCH_WRITE_LIMIT).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { write ->
                    batch.set(userDoc.collection(write.collection).document(write.docId), write.payload)
                }
                batch.commit().await()
                committed += chunk.size
            }

            recordLog(
                uid, "BACKUP", "restore", "RESTORE", "SYNCED",
                "Memulihkan $committed item, ${plan.skippedCount} dilewati (sudah ada di cloud)"
            )
            Result.success(RestoreResult(plan.restoredCount, plan.skippedCount))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/restore")
            Result.failure(e)
        }
    }

    // ==================== MODE LOKAL (TANPA FIREBASE) ====================
    //
    // Seluruh operasi di bawah hanya berjalan ketika repository dibuat dengan
    // isLocalMode = true (flag TaskFlowLocalMode). Semantik meniru jalur cloud
    // satu-per-satu: revision monoton per tulisan, soft delete, spawn tugas
    // berulang dengan ID deterministik, idempotensi aksi notifikasi, dan
    // pencatatan log — hanya medianya Room, bukan Firestore.

    private val localStore by lazy { LocalTaskFlowStore(localDb) }

    private fun localUid(): String = TaskFlowLocalMode.LOCAL_USER_ID

    private fun localNow(): Timestamp = Timestamp(
        System.currentTimeMillis() / 1000L,
        ((System.currentTimeMillis() % 1000L) * 1_000_000L).toInt()
    )

    private suspend fun localEnsureUserProfile(): Result<Unit> = try {
        val uid = localUid()
        localStore.ensureProfile(uid, "Pengguna Lokal")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun localTriggerManualSync(userId: String): Result<Unit> {
        _syncMetadata.value = _syncMetadata.value.copy(
            state = SyncState.LOCAL_ONLY,
            pendingWritesCount = 0,
            lastSyncedAtMillis = System.currentTimeMillis(),
            lastErrorMessage = null
        )
        recordLog(userId, "SYNC", "workspace", "MANUAL_SYNC", "LOCAL", "Mode lokal: sinkronisasi cloud nonaktif")
        return Result.success(Unit)
    }

    private suspend fun localCreateTask(
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
        deviceId: String,
        explicitDocId: String?
    ): Result<TaskItem> {
        val uid = localUid()
        return try {
            val cleanTitle = title.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Judul tugas tidak boleh kosong." }
            val cleanDesc = description.trim().take(4000)
            val cleanCat = category.trim().take(40).ifEmpty { "Personal" }
            val cleanTags = tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10)
            val cleanSubtasks = subtasks.filter { it.title.isNotBlank() }.take(20)
            val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }
            val id = if (!explicitDocId.isNullOrBlank() && explicitDocId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                explicitDocId
            } else {
                "tsk_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            }
            val now = localNow()
            val task = TaskItem(
                id = id,
                userId = uid,
                title = cleanTitle,
                description = cleanDesc,
                status = status.name,
                priority = priority.name,
                category = cleanCat,
                projectId = projectId?.takeIf { it.isNotBlank() },
                dueAtMillis = dueAtMillis,
                reminderAtMillis = reminderAtMillis,
                completedAtMillis = if (status == TaskStatus.COMPLETED) System.currentTimeMillis() else null,
                recurrence = recurrence.name,
                tags = cleanTags,
                subtasks = cleanSubtasks,
                revision = 1L,
                deviceId = cleanDevice,
                createdAt = now,
                updatedAt = now
            )
            localStore.upsertTask(uid, task)
            recordLog(uid, "TASK", id, "CREATE", "LOCAL", cleanTitle)
            if (reminderAtMillis != null) {
                logReminderEvent(
                    uid = uid,
                    taskId = id,
                    title = cleanTitle,
                    message = "Pengingat dijadwalkan untuk \"$cleanTitle\"",
                    type = ReminderType.TASK_REMINDER,
                    scheduledAtMillis = reminderAtMillis
                )
            }
            Result.success(task)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordLog(uid, "TASK", "create", "CREATE", "FAILED", e.message ?: "Gagal")
            Result.failure(e)
        }
    }

    private suspend fun localUpdateTask(
        existing: TaskItem,
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
        deviceId: String
    ): Result<Boolean> {
        val uid = localUid()
        return try {
            require(existing.id.isNotBlank()) { "ID tugas tidak valid." }
            val cleanTitle = title.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Judul tugas tidak boleh kosong." }
            val current = localStore.getTask(uid, existing.id) ?: existing
            val updated = current.copy(
                title = cleanTitle,
                description = description.trim().take(4000),
                status = status.name,
                priority = priority.name,
                category = category.trim().take(40).ifEmpty { "Personal" },
                projectId = projectId?.takeIf { it.isNotBlank() },
                dueAtMillis = dueAtMillis,
                reminderAtMillis = reminderAtMillis,
                completedAtMillis = if (status == TaskStatus.COMPLETED) {
                    current.completedAtMillis ?: System.currentTimeMillis()
                } else null,
                recurrence = recurrence.name,
                tags = tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10),
                subtasks = subtasks.filter { it.title.isNotBlank() }.take(20),
                revision = current.revision + 1L,
                deviceId = deviceId.trim().take(80).ifEmpty { "android" },
                updatedAt = localNow()
            )
            localStore.upsertTask(uid, updated)
            recordLog(uid, "TASK", existing.id, "UPDATE", "LOCAL", "$cleanTitle (rev lokal +1)")
            if (reminderAtMillis != null && reminderAtMillis != current.reminderAtMillis) {
                logReminderEvent(
                    uid = uid,
                    taskId = existing.id,
                    title = cleanTitle,
                    message = "Pengingat diperbarui untuk \"$cleanTitle\"",
                    type = ReminderType.TASK_REMINDER,
                    scheduledAtMillis = reminderAtMillis
                )
            }
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordLog(uid, "TASK", existing.id, "UPDATE", "FAILED", e.message ?: "Gagal")
            Result.failure(e)
        }
    }

    private suspend fun localToggleTaskCompletion(task: TaskItem, deviceId: String): Result<TaskItem?> {
        val uid = localUid()
        return try {
            val current = localStore.getTask(uid, task.id) ?: task
            val completing = current.taskStatus != TaskStatus.COMPLETED
            val nextStatus = if (completing) TaskStatus.COMPLETED else TaskStatus.TODO
            val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }
            val updated = current.copy(
                status = nextStatus.name,
                completedAtMillis = if (completing) System.currentTimeMillis() else null,
                revision = current.revision + 1L,
                deviceId = cleanDevice,
                updatedAt = localNow()
            )
            localStore.upsertTask(uid, updated)

            // C-06/FR-026: tugas berulang men-spawn kemunculan berikutnya dengan
            // ID deterministik — idempoten bila aksi diulang.
            var spawned: TaskItem? = null
            if (completing && current.recurrenceRule != RecurrenceRule.NONE) {
                val nextDue = computeNextOccurrenceMillis(
                    current.dueAtMillis ?: System.currentTimeMillis(),
                    current.recurrenceRule
                )
                val nextReminder = current.reminderAtMillis?.let {
                    computeNextOccurrenceMillis(it, current.recurrenceRule)
                }
                val nextId = buildOccurrenceDocId(current.id, nextDue)
                if (localStore.getTask(uid, nextId) == null) {
                    val now = localNow()
                    val spawn = TaskItem(
                        id = nextId,
                        userId = uid,
                        title = current.title,
                        description = current.description,
                        status = TaskStatus.TODO.name,
                        priority = current.priority,
                        category = current.category,
                        projectId = current.projectId,
                        dueAtMillis = nextDue,
                        reminderAtMillis = nextReminder,
                        recurrence = current.recurrence,
                        tags = current.tags,
                        subtasks = current.subtasks.map { it.copy(done = false) },
                        revision = 1L,
                        deviceId = cleanDevice,
                        createdAt = now,
                        updatedAt = now
                    )
                    localStore.upsertTask(uid, spawn)
                    nextReminder?.let {
                        localStore.upsertReminder(
                            uid,
                            ReminderEvent(
                                id = newReminderDocId(),
                                userId = uid,
                                taskId = current.id,
                                title = current.title,
                                message = "Pengingat dijadwalkan untuk \"${current.title}\"",
                                reminderType = ReminderType.TASK_REMINDER.name,
                                scheduledAtMillis = it,
                                dismissed = false,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                    spawned = spawn
                }
            }
            recordLog(uid, "TASK", task.id, if (completing) "COMPLETE" else "REOPEN", "LOCAL", task.title)
            spawned?.let { recordLog(uid, "TASK", it.id, "SPAWN", "LOCAL", "Kemunculan berikutnya: ${it.title}") }
            Result.success(spawned)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordLog(uid, "TASK", task.id, "COMPLETE", "FAILED", e.message ?: "Gagal")
            Result.failure(e)
        }
    }

    private suspend fun localCompleteTaskFromNotification(taskId: String, deviceId: String): Result<Unit> {
        val uid = localUid()
        return try {
            require(taskId.isNotBlank()) { "ID tugas tidak valid." }
            val task = localStore.getTask(uid, taskId)
            // Idempoten: tugas hilang atau sudah selesai tidak ditulis ulang.
            if (task == null || task.taskStatus == TaskStatus.COMPLETED) {
                return Result.success(Unit)
            }
            localToggleTaskCompletion(task, deviceId)
            recordLog(uid, "TASK", taskId, "COMPLETE", "LOCAL", "Selesai dari notifikasi")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordLog(uid, "TASK", taskId, "COMPLETE", "FAILED", e.message ?: "Gagal")
            Result.failure(e)
        }
    }

    private suspend fun localToggleSubtask(task: TaskItem, subtaskId: String, deviceId: String): Result<Unit> {
        val uid = localUid()
        return try {
            val current = localStore.getTask(uid, task.id) ?: task
            val updated = current.copy(
                subtasks = current.subtasks.map {
                    if (it.id == subtaskId) it.copy(done = !it.done) else it
                },
                revision = current.revision + 1L,
                deviceId = deviceId.ifBlank { "android" },
                updatedAt = localNow()
            )
            localStore.upsertTask(uid, updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localRescheduleTask(task: TaskItem, newDueMillis: Long?, deviceId: String): Result<Unit> {
        val uid = localUid()
        return try {
            val current = localStore.getTask(uid, task.id) ?: task
            localStore.upsertTask(
                uid,
                current.copy(
                    dueAtMillis = newDueMillis,
                    revision = current.revision + 1L,
                    deviceId = deviceId.ifBlank { "android" },
                    updatedAt = localNow()
                )
            )
            recordLog(uid, "TASK", task.id, "RESCHEDULE", "LOCAL", task.title)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localArchiveTask(task: TaskItem, archive: Boolean, deviceId: String): Result<Unit> {
        val uid = localUid()
        return try {
            val current = localStore.getTask(uid, task.id) ?: task
            localStore.upsertTask(
                uid,
                current.copy(
                    archived = archive,
                    revision = current.revision + 1L,
                    deviceId = deviceId.ifBlank { "android" },
                    updatedAt = localNow()
                )
            )
            recordLog(uid, "TASK", task.id, if (archive) "ARCHIVE" else "UNARCHIVE", "LOCAL", task.title)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localSoftDeleteTask(task: TaskItem, deviceId: String): Result<Unit> {
        val uid = localUid()
        return try {
            val current = localStore.getTask(uid, task.id) ?: task
            localStore.upsertTask(
                uid,
                current.copy(
                    deleted = true,
                    revision = current.revision + 1L,
                    deviceId = deviceId.ifBlank { "android" },
                    updatedAt = localNow()
                )
            )
            recordLog(uid, "TASK", task.id, "SOFT_DELETE", "LOCAL", task.title)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localCreateNote(
        title: String,
        content: String,
        category: String,
        projectId: String?,
        tags: List<String>,
        linkedTaskIds: List<String>,
        deviceId: String,
        explicitDocId: String?
    ): Result<NoteItem> {
        val uid = localUid()
        return try {
            val cleanTitle = title.trim().take(160)
            val cleanContent = content.take(50000)
            val cleanCat = category.trim().take(40).ifEmpty { "Personal" }
            val cleanTags = tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10)
            val cleanLinks = linkedTaskIds.distinct().take(20)
            val words = countWords("$cleanTitle $cleanContent")
            val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }
            val id = if (!explicitDocId.isNullOrBlank() && explicitDocId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                explicitDocId
            } else {
                "nte_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            }
            val now = localNow()
            val note = NoteItem(
                id = id,
                userId = uid,
                title = cleanTitle,
                content = cleanContent,
                category = cleanCat,
                projectId = projectId?.takeIf { it.isNotBlank() },
                tags = cleanTags,
                linkedTaskIds = cleanLinks,
                wordCount = words,
                revision = 1L,
                deviceId = cleanDevice,
                createdAt = now,
                updatedAt = now
            )
            localStore.upsertNote(uid, note)
            clearNoteDraftLocally(uid, null)
            recordLog(uid, "NOTE", id, "CREATE", "LOCAL", cleanTitle.ifBlank { "Catatan tanpa judul" })
            Result.success(note)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordLog(uid, "NOTE", "create", "CREATE", "FAILED", e.message ?: "Gagal")
            Result.failure(e)
        }
    }

    private suspend fun localUpdateNote(
        existing: NoteItem,
        title: String,
        content: String,
        category: String,
        projectId: String?,
        tags: List<String>,
        linkedTaskIds: List<String>,
        deviceId: String
    ): Result<Boolean> {
        val uid = localUid()
        return try {
            require(existing.id.isNotBlank()) { "ID catatan tidak valid." }
            val cleanTitle = title.trim().take(160)
            val cleanContent = content.take(50000)
            val current = localStore.getNote(uid, existing.id) ?: existing
            val words = countWords("$cleanTitle $cleanContent")
            localStore.upsertNote(
                uid,
                current.copy(
                    title = cleanTitle,
                    content = cleanContent,
                    category = category.trim().take(40).ifEmpty { "Personal" },
                    projectId = projectId?.takeIf { it.isNotBlank() },
                    tags = tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10),
                    linkedTaskIds = linkedTaskIds.distinct().take(20),
                    wordCount = words,
                    revision = current.revision + 1L,
                    deviceId = deviceId.trim().take(80).ifEmpty { "android" },
                    updatedAt = localNow()
                )
            )
            clearNoteDraftLocally(uid, existing.id)
            recordLog(uid, "NOTE", existing.id, "UPDATE", "LOCAL", "${cleanTitle.ifBlank { "Catatan" }} (rev lokal +1)")
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordLog(uid, "NOTE", existing.id, "UPDATE", "FAILED", e.message ?: "Gagal")
            Result.failure(e)
        }
    }

    private suspend fun localUpdateNoteFlags(
        note: NoteItem,
        pinned: Boolean,
        favorite: Boolean,
        archived: Boolean,
        deleted: Boolean,
        deviceId: String
    ): Result<Unit> {
        val uid = localUid()
        return try {
            val current = localStore.getNote(uid, note.id) ?: note
            localStore.upsertNote(
                uid,
                current.copy(
                    pinned = pinned,
                    favorite = favorite,
                    archived = archived,
                    deleted = deleted,
                    revision = current.revision + 1L,
                    deviceId = deviceId.ifBlank { "android" },
                    updatedAt = localNow()
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localCreateProject(
        name: String,
        description: String?,
        colorHex: String,
        iconName: String,
        explicitDocId: String?
    ): Result<ProjectItem> {
        val uid = localUid()
        return try {
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Nama proyek tidak boleh kosong." }
            val cleanDesc = description?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
            val cleanColor = colorHex.trim().take(9).ifEmpty { "#0F5257" }
            val cleanIcon = iconName.trim().take(40).ifEmpty { "folder" }
            val id = if (!explicitDocId.isNullOrBlank() && explicitDocId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                explicitDocId
            } else {
                "prj_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            }
            val now = localNow()
            val project = ProjectItem(
                id = id,
                userId = uid,
                name = cleanName,
                description = cleanDesc,
                colorHex = cleanColor,
                iconName = cleanIcon,
                createdAt = now,
                updatedAt = now
            )
            localStore.upsertProject(uid, project)
            recordLog(uid, "PROJECT", id, "CREATE", "LOCAL", cleanName)
            Result.success(project)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordLog(uid, "PROJECT", "create", "CREATE", "FAILED", e.message ?: "Gagal")
            Result.failure(e)
        }
    }

    private suspend fun localUpdateProject(
        existing: ProjectItem,
        name: String,
        description: String?,
        colorHex: String,
        archived: Boolean
    ): Result<Unit> {
        val uid = localUid()
        return try {
            require(existing.id.isNotBlank()) { "ID proyek tidak valid." }
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Nama proyek tidak boleh kosong." }
            val current = localStore.getProject(uid, existing.id) ?: existing
            localStore.upsertProject(
                uid,
                current.copy(
                    name = cleanName,
                    description = description?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
                    colorHex = colorHex.take(9),
                    archived = archived,
                    revision = current.revision + 1L,
                    updatedAt = localNow()
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** PRD 6.6: hapus proyek tidak pernah menghapus item tertaut — hanya melepas tautan. */
    private suspend fun localSafeDeleteProject(
        project: ProjectItem,
        linkedTasks: List<TaskItem>,
        linkedNotes: List<NoteItem>,
        deviceId: String
    ): Result<Unit> {
        val uid = localUid()
        return try {
            val now = localNow()
            linkedTasks.forEach { t ->
                val current = localStore.getTask(uid, t.id) ?: t
                localStore.upsertTask(
                    uid,
                    current.copy(projectId = null, revision = current.revision + 1L, updatedAt = now)
                )
            }
            linkedNotes.forEach { n ->
                val current = localStore.getNote(uid, n.id) ?: n
                localStore.upsertNote(
                    uid,
                    current.copy(projectId = null, revision = current.revision + 1L, updatedAt = now)
                )
            }
            val current = localStore.getProject(uid, project.id) ?: project
            localStore.upsertProject(
                uid,
                current.copy(deleted = true, revision = current.revision + 1L, updatedAt = now)
            )
            recordLog(
                uid, "PROJECT", project.id, "SAFE_DELETE", "LOCAL",
                "Proyek dihapus, ${linkedTasks.size} tugas & ${linkedNotes.size} catatan tetap aman"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localLogReminderEvent(
        uid: String,
        taskId: String?,
        title: String,
        message: String,
        type: ReminderType,
        scheduledAtMillis: Long
    ): Result<Unit> {
        return try {
            val now = localNow()
            localStore.upsertReminder(
                uid,
                ReminderEvent(
                    id = newReminderDocId(),
                    userId = uid,
                    taskId = taskId?.takeIf { it.isNotBlank() },
                    title = title.trim().take(160).ifEmpty { "Pengingat" },
                    message = message.trim().take(500),
                    reminderType = type.name,
                    scheduledAtMillis = scheduledAtMillis.coerceAtLeast(0L),
                    dismissed = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localSnoozeReminderEvent(event: ReminderEvent, delayMinutes: Int): Result<Long> {
        val uid = localUid()
        return try {
            val newTime = System.currentTimeMillis() + delayMinutes * 60_000L
            localStore.upsertReminder(
                uid,
                event.copy(
                    snoozedUntilMillis = newTime,
                    scheduledAtMillis = newTime,
                    dismissed = false,
                    updatedAt = localNow()
                )
            )
            Result.success(newTime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localDismissReminderEvent(event: ReminderEvent): Result<Unit> {
        val uid = localUid()
        return try {
            localStore.upsertReminder(
                uid,
                event.copy(dismissed = true, updatedAt = localNow())
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localResolveConflict(
        conflict: SyncConflictEntity,
        chosenTitle: String,
        chosenContent: String,
        resolutionLabel: String,
        deviceId: String
    ): Result<Unit> {
        // Konflik lintas perangkat tidak mungkin terjadi di mode lokal —
        // konflik lama (bila ada) cukup ditandai selesai.
        return try {
            localDb.syncConflictDao().markResolved(conflict.conflictId)
            recordLog(conflict.userId, conflict.entityType, conflict.entityId, "CONFLICT_RESOLVED", "LOCAL", resolutionLabel)
            _syncMetadata.value = _syncMetadata.value.copy(state = SyncState.LOCAL_ONLY)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localCreateCloudBackup(
        label: String,
        projects: List<ProjectItem>,
        tasks: List<TaskItem>,
        notes: List<NoteItem>
    ): Result<BackupSnapshot> {
        val uid = localUid()
        return try {
            val activeProjects = projects.filter { !it.deleted }
            val activeTasks = tasks.filter { !it.deleted }
            val activeNotes = notes.filter { !it.deleted }
            val (_, checksum) = LocalBackupCodec.encodeToJson(label, activeProjects, activeTasks, activeNotes)
            val snapshotMap = LocalBackupCodec.buildNativeSnapshotMap(activeProjects, activeTasks, activeNotes)
            val now = localNow()
            val backup = BackupSnapshot(
                id = "bkp_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = uid,
                formatVersion = LocalBackupCodec.CURRENT_FORMAT_VERSION.toLong(),
                label = label.trim().take(120).ifEmpty { "Backup Lokal" },
                taskCount = activeTasks.size.toLong(),
                noteCount = activeNotes.size.toLong(),
                projectCount = activeProjects.size.toLong(),
                checksum = checksum,
                snapshotData = snapshotMap,
                createdAt = now,
                updatedAt = now
            )
            localStore.upsertBackup(uid, backup)
            localStore.updateProfileBackupTime(uid, System.currentTimeMillis())
            recordLog(
                uid, "BACKUP", backup.id, "LOCAL_BACKUP", "LOCAL",
                "$label (${activeTasks.size} tugas, ${activeNotes.size} catatan)"
            )
            Result.success(backup)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordLog(uid, "BACKUP", "backup", "LOCAL_BACKUP", "FAILED", e.message ?: "Gagal")
            Result.failure(e)
        }
    }

    /** C-05: jalur restore yang sama — merge, bukan timpa; dilewati bila masih hidup. */
    private suspend fun localRestoreFromPreview(preview: BackupPreview, deviceId: String): Result<RestoreResult> {
        val uid = localUid()
        return try {
            val plan = RestorePlanner.buildRestorePlan(
                uid = uid,
                deviceId = deviceId,
                preview = preview,
                existingProjects = localStore.listProjects(uid).associate {
                    it.id to RestorePlanner.ExistingDoc(it.revision, it.deleted, it.createdAt?.toDate()?.time)
                },
                existingTasks = localStore.listTasks(uid).associate {
                    it.id to RestorePlanner.ExistingDoc(it.revision, it.deleted, it.createdAt?.toDate()?.time)
                },
                existingNotes = localStore.listNotes(uid).associate {
                    it.id to RestorePlanner.ExistingDoc(it.revision, it.deleted, it.createdAt?.toDate()?.time)
                }
            )
            localStore.applyRestoreWrites(uid, plan.writes)
            recordLog(
                uid, "BACKUP", "restore", "RESTORE", "LOCAL",
                "Memulihkan ${plan.restoredCount} item, ${plan.skippedCount} dilewati (sudah ada)"
            )
            Result.success(RestoreResult(plan.restoredCount, plan.skippedCount))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordLog(uid, "BACKUP", "restore", "RESTORE", "FAILED", e.message ?: "Gagal")
            Result.failure(e)
        }
    }

    // ==================== HELPER PRIVATE ====================

    private fun newReminderDocId(): String =
        "rem_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    /** ID deterministik untuk kemunculan berikutnya agar spawn idempoten (C-06). */
    private fun buildOccurrenceDocId(parentId: String, dueMillis: Long): String =
        "${parentId.take(64)}_occ_$dueMillis"

    private fun buildRecurrencePayload(
        uid: String,
        parent: TaskItem,
        nextDueMillis: Long,
        nextReminderMillis: Long?,
        deviceId: String
    ): Map<String, Any> {
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "title" to parent.title.trim().take(160).ifEmpty { "Tugas" },
            "description" to parent.description.take(4000),
            "status" to TaskStatus.TODO.name,
            "priority" to parent.priority,
            "category" to parent.category.trim().take(40).ifEmpty { "Personal" },
            "projectId" to parent.projectId?.takeIf { it.isNotBlank() },
            "dueAtMillis" to nextDueMillis,
            "reminderAtMillis" to nextReminderMillis,
            "recurrence" to parent.recurrence,
            "tags" to parent.tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10),
            "subtasks" to parent.subtasks.filter { it.title.isNotBlank() }.take(20).map { it.copy(done = false).toMap() },
            "archived" to false,
            "deleted" to false,
            "revision" to 1L,
            "deviceId" to deviceId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        @Suppress("UNCHECKED_CAST")
        return payload as Map<String, Any>
    }

    private fun buildReminderEventPayload(
        uid: String,
        taskId: String?,
        title: String,
        scheduledAtMillis: Long
    ): Map<String, Any> {
        val cleanTitle = title.trim().take(160)
        val payload = mapOf<String, Any?>(
            "userId" to uid,
            "taskId" to taskId?.takeIf { it.isNotBlank() },
            "title" to cleanTitle.ifEmpty { "Pengingat" },
            "message" to "Pengingat dijadwalkan untuk \"$cleanTitle\"".take(500),
            "reminderType" to ReminderType.TASK_REMINDER.name,
            "scheduledAtMillis" to scheduledAtMillis.coerceAtLeast(0L),
            "dismissed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        @Suppress("UNCHECKED_CAST")
        return payload as Map<String, Any>
    }

    companion object {
        private const val BATCH_WRITE_LIMIT = 400
        private const val SYNC_LOG_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

        fun countWords(text: String): Long {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return 0L
            return trimmed.split(Regex("\\s+")).size.toLong().coerceAtMost(50000L)
        }

        fun computeNextOccurrenceMillis(baseMillis: Long, rule: RecurrenceRule): Long =
            RecurrenceSchedule.nextOccurrenceMillis(baseMillis, rule)
    }
}
