package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.backup.LocalBackupCodec
import com.example.data.local.NoteDraftEntity
import com.example.data.local.SyncConflictEntity
import com.example.data.local.SyncLogEntity
import com.example.data.local.TaskFlowLocalDatabase
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
import java.util.Calendar
import java.util.UUID

class TaskFlowRepository(
    private val db: FirebaseFirestore,
    private val localDb: TaskFlowLocalDatabase,
    private val auth: FirebaseAuth = Firebase.auth
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
        localDb = TaskFlowLocalDatabase.getInstance(context.applicationContext)
    )

    private val _syncMetadata = MutableStateFlow(SyncMetadata())
    val syncMetadata: StateFlow<SyncMetadata> = _syncMetadata.asStateFlow()

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

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
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

    // --- REALTIME COLLECTION STREAMS ---
    fun observeTasks(userId: String): Flow<List<TaskItem>> = flow {
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

    fun observeNotes(userId: String): Flow<List<NoteItem>> = flow {
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

    fun observeProjects(userId: String): Flow<List<ProjectItem>> = flow {
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

    fun observeReminders(userId: String): Flow<List<ReminderEvent>> = flow {
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

    fun observeBackups(userId: String): Flow<List<BackupSnapshot>> = flow {
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

    // --- MANUAL SYNC CHECK ---
    suspend fun triggerManualSync(userId: String): Result<Unit> {
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

            // Conflict Detection: inspect current cloud revision before overwriting
            if (!forceOverwrite) {
                val serverSnap = docRef.get().await()
                val remoteRev = serverSnap.getLong("revision") ?: existing.revision
                val remoteTitle = serverSnap.getString("title") ?: existing.title
                val remoteDesc = serverSnap.getString("description") ?: existing.description
                if (remoteRev > existing.revision && (remoteTitle != existing.title || remoteDesc != existing.description)) {
                    localDb.syncConflictDao().upsertConflict(
                        SyncConflictEntity(
                            conflictId = "task_${existing.id}_$remoteRev",
                            userId = uid,
                            entityType = "TASK",
                            entityId = existing.id,
                            entityTitle = existing.title,
                            localTitle = cleanTitle,
                            localContent = cleanDesc,
                            localRevision = existing.revision + 1L,
                            cloudTitle = remoteTitle,
                            cloudContent = remoteDesc,
                            cloudRevision = remoteRev
                        )
                    )
                    _syncMetadata.value = _syncMetadata.value.copy(state = SyncState.CONFLICT_DETECTED)
                    recordLog(uid, "TASK", existing.id, "CONFLICT", "CONFLICT_DETECTED", "Revisi cloud #$remoteRev berbeda dari lokal #${existing.revision}")
                    return Result.success(false)
                }
            }

            val nextRevision = existing.revision + 1L
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
                "revision" to nextRevision,
                "deviceId" to cleanDevice,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            recordLog(uid, "TASK", existing.id, "UPDATE", "SYNCED", "$cleanTitle (rev #$nextRevision)")

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
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val completing = task.taskStatus != TaskStatus.COMPLETED
            val nextStatus = if (completing) TaskStatus.COMPLETED else TaskStatus.TODO
            val nextRev = task.revision + 1L
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docPath = docRef.path

            val updates = mapOf<String, Any>(
                "status" to nextStatus.name,
                "completedAtMillis" to if (completing) System.currentTimeMillis() else FieldValue.delete(),
                "revision" to nextRev,
                "deviceId" to deviceId.ifBlank { "android" },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            recordLog(uid, "TASK", task.id, if (completing) "COMPLETE" else "REOPEN", "SYNCED", task.title)

            // PRD FR-026: Recurring task spawns next occurrence while preserving completed history
            var nextInstance: TaskItem? = null
            if (completing && task.recurrenceRule != RecurrenceRule.NONE) {
                val nextDue = computeNextOccurrenceMillis(task.dueAtMillis ?: System.currentTimeMillis(), task.recurrenceRule)
                val nextReminder = task.reminderAtMillis?.let { computeNextOccurrenceMillis(it, task.recurrenceRule) }
                val spawned = createTask(
                    title = task.title,
                    description = task.description,
                    status = TaskStatus.TODO,
                    priority = task.taskPriority,
                    category = task.category,
                    projectId = task.projectId,
                    dueAtMillis = nextDue,
                    reminderAtMillis = nextReminder,
                    recurrence = task.recurrenceRule,
                    tags = task.tags,
                    subtasks = task.subtasks.map { it.copy(done = false) },
                    deviceId = deviceId
                )
                nextInstance = spawned.getOrNull()
            }
            Result.success(nextInstance)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleSubtask(task: TaskItem, subtaskId: String, deviceId: String): Result<Unit> {
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
                    "revision" to (task.revision + 1L),
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
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "dueAtMillis" to (newDueMillis ?: FieldValue.delete()),
                    "revision" to (task.revision + 1L),
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
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "archived" to archive,
                    "revision" to (task.revision + 1L),
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
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "deleted" to true,
                    "revision" to (task.revision + 1L),
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

            if (!forceOverwrite) {
                val snap = docRef.get().await()
                val remoteRev = snap.getLong("revision") ?: existing.revision
                val remoteTitle = snap.getString("title") ?: existing.title
                val remoteContent = snap.getString("content") ?: existing.content
                if (remoteRev > existing.revision && (remoteTitle != existing.title || remoteContent != existing.content)) {
                    localDb.syncConflictDao().upsertConflict(
                        SyncConflictEntity(
                            conflictId = "note_${existing.id}_$remoteRev",
                            userId = uid,
                            entityType = "NOTE",
                            entityId = existing.id,
                            entityTitle = existing.title.ifBlank { "Catatan" },
                            localTitle = cleanTitle,
                            localContent = cleanContent,
                            localRevision = existing.revision + 1L,
                            cloudTitle = remoteTitle,
                            cloudContent = remoteContent,
                            cloudRevision = remoteRev
                        )
                    )
                    _syncMetadata.value = _syncMetadata.value.copy(state = SyncState.CONFLICT_DETECTED)
                    recordLog(uid, "NOTE", existing.id, "CONFLICT", "CONFLICT_DETECTED", "Revisi cloud #$remoteRev berbeda")
                    return Result.success(false)
                }
            }

            val nextRev = existing.revision + 1L
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "category" to cleanCat,
                    "projectId" to (projectId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
                    "tags" to cleanTags,
                    "linkedTaskIds" to cleanLinks,
                    "wordCount" to words,
                    "revision" to nextRev,
                    "deviceId" to cleanDevice,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            clearNoteDraftLocally(uid, existing.id)
            recordLog(uid, "NOTE", existing.id, "UPDATE", "SYNCED", "${cleanTitle.ifBlank { "Catatan" }} (rev #$nextRev)")
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
                    "revision" to (note.revision + 1L),
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
                    "revision" to (existing.revision + 1L),
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
                        "revision" to (t.revision + 1L),
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
                        "revision" to (n.revision + 1L),
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
                    "revision" to (project.revision + 1L),
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
        var docPath = "users/${conflict.userId}/${conflict.entityType.lowercase()}s/${conflict.entityId}"
        return try {
            val uid = requireUserId()
            val nextRev = maxOf(conflict.localRevision, conflict.cloudRevision) + 1L
            if (conflict.entityType == "TASK") {
                val ref = db.collection("users").document(uid).collection("tasks").document(conflict.entityId)
                docPath = ref.path
                ref.update(
                    mapOf(
                        "title" to chosenTitle.trim().take(160).ifEmpty { "Tugas" },
                        "description" to chosenContent.trim().take(4000),
                        "revision" to nextRev,
                        "deviceId" to deviceId.ifBlank { "android" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val ref = db.collection("users").document(uid).collection("notes").document(conflict.entityId)
                docPath = ref.path
                ref.update(
                    mapOf(
                        "title" to chosenTitle.trim().take(160),
                        "content" to chosenContent.take(50000),
                        "wordCount" to countWords("$chosenTitle $chosenContent"),
                        "revision" to nextRev,
                        "deviceId" to deviceId.ifBlank { "android" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
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

    suspend fun restoreFromPreview(
        preview: BackupPreview,
        existingProjectIds: Set<String>,
        existingTaskIds: Set<String>,
        existingNoteIds: Set<String>,
        deviceId: String
    ): Result<Int> {
        return try {
            val uid = requireUserId()
            var restoredCount = 0

            preview.projects.forEach { p ->
                if (p.id.isBlank() || p.id !in existingProjectIds) {
                    val res = createProject(
                        name = p.name,
                        description = p.description,
                        colorHex = p.colorHex,
                        iconName = p.iconName,
                        explicitDocId = p.id.takeIf { it.isNotBlank() }
                    )
                    if (res.isSuccess) restoredCount++
                }
            }
            preview.tasks.forEach { t ->
                if (t.id.isBlank() || t.id !in existingTaskIds) {
                    val res = createTask(
                        title = t.title,
                        description = t.description,
                        status = t.taskStatus,
                        priority = t.taskPriority,
                        category = t.category,
                        projectId = t.projectId,
                        dueAtMillis = t.dueAtMillis,
                        reminderAtMillis = t.reminderAtMillis,
                        recurrence = t.recurrenceRule,
                        tags = t.tags,
                        subtasks = t.subtasks,
                        deviceId = deviceId,
                        explicitDocId = t.id.takeIf { it.isNotBlank() }
                    )
                    if (res.isSuccess) restoredCount++
                }
            }
            preview.notes.forEach { n ->
                if (n.id.isBlank() || n.id !in existingNoteIds) {
                    val res = createNote(
                        title = n.title,
                        content = n.content,
                        category = n.category,
                        projectId = n.projectId,
                        tags = n.tags,
                        linkedTaskIds = n.linkedTaskIds,
                        deviceId = deviceId,
                        explicitDocId = n.id.takeIf { it.isNotBlank() }
                    )
                    if (res.isSuccess) restoredCount++
                }
            }
            recordLog(uid, "BACKUP", "restore", "RESTORE", "SYNCED", "Memulihkan $restoredCount item tanpa duplikasi")
            Result.success(restoredCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/restore")
            Result.failure(e)
        }
    }

    companion object {
        fun countWords(text: String): Long {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return 0L
            return trimmed.split(Regex("\\s+")).size.toLong().coerceAtMost(50000L)
        }

        fun computeNextOccurrenceMillis(baseMillis: Long, rule: RecurrenceRule): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = baseMillis }
            when (rule) {
                RecurrenceRule.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                RecurrenceRule.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                RecurrenceRule.MONTHLY -> cal.add(Calendar.MONTH, 1)
                RecurrenceRule.YEARLY -> cal.add(Calendar.YEAR, 1)
                RecurrenceRule.NONE -> Unit
            }
            return cal.timeInMillis
        }
    }
}
