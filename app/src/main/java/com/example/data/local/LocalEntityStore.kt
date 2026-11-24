package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.data.remote.RestorePlanner
import com.example.model.BackupSnapshot
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.ReminderEvent
import com.example.model.ReminderType
import com.example.model.RecurrenceRule
import com.example.model.SubtaskItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Penyimpanan data utama untuk MODE LOKAL (tanpa Firebase).
 *
 * Semua entitas (profil, proyek, tugas, catatan, pengingat, backup)
 * disimpan pada SATU tabel Room dengan payload JSON per entitas.
 * Format field payload identik dengan nama field dokumen Firestore
 * sehingga jalur restore (RestorePlanner) dapat dipakai ulang tanpa
 * perubahan, dan codec JSON menjadi satu-satunya titik konversi.
 */
@Entity(tableName = "local_entities", primaryKeys = ["ownerId", "entityType", "entityId"])
data class LocalEntityRow(
    val ownerId: String,
    val entityType: String,
    val entityId: String,
    val payload: String,
    val revision: Long,
    val deleted: Boolean,
    val updatedAtMillis: Long
)

@Dao
interface LocalEntityDao {

    @Query("SELECT * FROM local_entities WHERE ownerId = :ownerId AND entityType = :entityType ORDER BY updatedAtMillis DESC")
    fun observeByType(ownerId: String, entityType: String): Flow<List<LocalEntityRow>>

    @Query("SELECT * FROM local_entities WHERE ownerId = :ownerId AND entityType = :entityType")
    suspend fun getByType(ownerId: String, entityType: String): List<LocalEntityRow>

    @Query(
        "SELECT * FROM local_entities WHERE ownerId = :ownerId AND entityType = :entityType " +
            "AND entityId = :entityId LIMIT 1"
    )
    suspend fun get(ownerId: String, entityType: String, entityId: String): LocalEntityRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: LocalEntityRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<LocalEntityRow>)

    @Query("DELETE FROM local_entities WHERE ownerId = :ownerId AND entityType = :entityType AND entityId = :entityId")
    suspend fun delete(ownerId: String, entityType: String, entityId: String)
}

class LocalTaskFlowStore(private val localDb: TaskFlowLocalDatabase) {

    companion object {
        const val TYPE_PROFILE = "profile"
        const val TYPE_TASK = "task"
        const val TYPE_NOTE = "note"
        const val TYPE_PROJECT = "project"
        const val TYPE_REMINDER = "reminder"
        const val TYPE_BACKUP = "backup"
    }

    private val dao get() = localDb.localEntityDao()

    // ---------- OBSERVERS (semantik sama dengan stream Firestore) ----------

    fun observeProfile(ownerId: String): Flow<UserProfile?> =
        dao.observeByType(ownerId, TYPE_PROFILE).map { rows ->
            rows.firstOrNull()?.let { profileFromJson(JSONObject(it.payload), it.entityId) }
        }

    fun observeTasks(ownerId: String): Flow<List<TaskItem>> =
        dao.observeByType(ownerId, TYPE_TASK).map { rows ->
            rows.mapNotNull { taskFromJson(JSONObject(it.payload), it.entityId) }
                .filter { !it.deleted }
        }

    fun observeNotes(ownerId: String): Flow<List<NoteItem>> =
        dao.observeByType(ownerId, TYPE_NOTE).map { rows ->
            rows.mapNotNull { noteFromJson(JSONObject(it.payload), it.entityId) }
        }

    fun observeProjects(ownerId: String): Flow<List<ProjectItem>> =
        dao.observeByType(ownerId, TYPE_PROJECT).map { rows ->
            rows.mapNotNull { projectFromJson(JSONObject(it.payload), it.entityId) }
                .filter { !it.deleted }
        }

    fun observeReminders(ownerId: String): Flow<List<ReminderEvent>> =
        dao.observeByType(ownerId, TYPE_REMINDER).map { rows ->
            rows.mapNotNull { reminderFromJson(JSONObject(it.payload), it.entityId) }
                .sortedByDescending { it.scheduledAtMillis }
        }

    fun observeBackups(ownerId: String): Flow<List<BackupSnapshot>> =
        dao.observeByType(ownerId, TYPE_BACKUP).map { rows ->
            rows.mapNotNull { backupFromJson(JSONObject(it.payload), it.entityId) }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    // ---------- PEMBACAAN SATUAN (untuk logika tulis) ----------

    suspend fun getTask(ownerId: String, id: String): TaskItem? =
        dao.get(ownerId, TYPE_TASK, id)?.let { taskFromJson(JSONObject(it.payload), it.entityId) }

    suspend fun getNote(ownerId: String, id: String): NoteItem? =
        dao.get(ownerId, TYPE_NOTE, id)?.let { noteFromJson(JSONObject(it.payload), it.entityId) }

    suspend fun getProject(ownerId: String, id: String): ProjectItem? =
        dao.get(ownerId, TYPE_PROJECT, id)?.let { projectFromJson(JSONObject(it.payload), it.entityId) }

    suspend fun listTasks(ownerId: String): List<TaskItem> =
        dao.getByType(ownerId, TYPE_TASK).mapNotNull { taskFromJson(JSONObject(it.payload), it.entityId) }

    suspend fun listNotes(ownerId: String): List<NoteItem> =
        dao.getByType(ownerId, TYPE_NOTE).mapNotNull { noteFromJson(JSONObject(it.payload), it.entityId) }

    suspend fun listProjects(ownerId: String): List<ProjectItem> =
        dao.getByType(ownerId, TYPE_PROJECT).mapNotNull { projectFromJson(JSONObject(it.payload), it.entityId) }

    // ---------- PENULISAN ----------

    suspend fun upsertProfile(ownerId: String, profile: UserProfile) {
        dao.upsert(
            LocalEntityRow(
                ownerId = ownerId,
                entityType = TYPE_PROFILE,
                entityId = ownerId,
                payload = profileToJson(profile).toString(),
                revision = 1L,
                deleted = false,
                updatedAtMillis = nowMillis()
            )
        )
    }

    /** Buat profil hanya bila belum ada — tidak menimpa data yang sudah ada. */
    suspend fun ensureProfile(ownerId: String, displayName: String) {
        if (dao.get(ownerId, TYPE_PROFILE, ownerId) != null) return
        upsertProfile(ownerId, UserProfile(userId = ownerId, displayName = displayName))
    }

    suspend fun updateProfileBackupTime(ownerId: String, backupAtMillis: Long) {
        val existing = dao.get(ownerId, TYPE_PROFILE, ownerId)
            ?: return upsertProfile(
                ownerId,
                UserProfile(userId = ownerId, displayName = "Pengguna Lokal", lastBackupAtMillis = backupAtMillis)
            )
        val profile = profileFromJson(JSONObject(existing.payload), ownerId)
            .copy(lastBackupAtMillis = backupAtMillis, updatedAt = nowTimestamp())
        dao.upsert(
            existing.copy(
                payload = profileToJson(profile).toString(),
                updatedAtMillis = nowMillis()
            )
        )
    }

    suspend fun upsertTask(ownerId: String, task: TaskItem) {
        dao.upsert(
            LocalEntityRow(
                ownerId = ownerId,
                entityType = TYPE_TASK,
                entityId = task.id,
                payload = taskToJson(task).toString(),
                revision = task.revision,
                deleted = task.deleted,
                updatedAtMillis = nowMillis()
            )
        )
    }

    suspend fun upsertNote(ownerId: String, note: NoteItem) {
        dao.upsert(
            LocalEntityRow(
                ownerId = ownerId,
                entityType = TYPE_NOTE,
                entityId = note.id,
                payload = noteToJson(note).toString(),
                revision = note.revision,
                deleted = note.deleted,
                updatedAtMillis = nowMillis()
            )
        )
    }

    suspend fun upsertProject(ownerId: String, project: ProjectItem) {
        dao.upsert(
            LocalEntityRow(
                ownerId = ownerId,
                entityType = TYPE_PROJECT,
                entityId = project.id,
                payload = projectToJson(project).toString(),
                revision = project.revision,
                deleted = project.deleted,
                updatedAtMillis = nowMillis()
            )
        )
    }

    suspend fun upsertReminder(ownerId: String, event: ReminderEvent) {
        dao.upsert(
            LocalEntityRow(
                ownerId = ownerId,
                entityType = TYPE_REMINDER,
                entityId = event.id,
                payload = reminderToJson(event).toString(),
                revision = 1L,
                deleted = event.dismissed,
                updatedAtMillis = nowMillis()
            )
        )
    }

    suspend fun upsertBackup(ownerId: String, backup: BackupSnapshot) {
        dao.upsert(
            LocalEntityRow(
                ownerId = ownerId,
                entityType = TYPE_BACKUP,
                entityId = backup.id,
                payload = backupToJson(backup).toString(),
                revision = 1L,
                deleted = false,
                updatedAtMillis = nowMillis()
            )
        )
    }

    /**
     * Terapkan rencana restore (C-05) ke penyimpanan lokal. Payload Firestore
     * (FieldValue.serverTimestamp) diterjemahkan menjadi waktu sekarang.
     */
    suspend fun applyRestoreWrites(ownerId: String, writes: List<RestorePlanner.PlannedWrite>) {
        val now = nowMillis()
        val rows = writes.map { write ->
            val json = mapToJson(write.payload, now)
            LocalEntityRow(
                ownerId = ownerId,
                entityType = collectionToType(write.collection),
                entityId = write.docId,
                payload = json.toString(),
                revision = (write.payload["revision"] as? Number)?.toLong() ?: 1L,
                deleted = (write.payload["deleted"] as? Boolean) ?: false,
                updatedAtMillis = now
            )
        }
        dao.upsertAll(rows)
    }

    private fun collectionToType(collection: String): String = when (collection) {
        "tasks" -> TYPE_TASK
        "notes" -> TYPE_NOTE
        "projects" -> TYPE_PROJECT
        else -> TYPE_TASK
    }

    // ==================== CODEC JSON ====================

    private fun nowMillis(): Long = System.currentTimeMillis()
    private fun nowTimestamp(): Timestamp = timestampFromMillis(nowMillis())
        ?: Timestamp(nowMillis() / 1000L, 0)

    private fun timestampFromMillis(millis: Long?): Timestamp? =
        millis?.let { Timestamp(it / 1000L, ((it % 1000L) * 1_000_000L).toInt()) }

    private fun Timestamp?.toMillisOrNull(): Long? =
        this?.let { it.seconds * 1000L + it.nanoseconds / 1_000_000L }

    fun taskToJson(t: TaskItem): JSONObject = JSONObject().apply {
        put("userId", t.userId)
        put("title", t.title)
        put("description", t.description)
        put("status", t.status)
        put("priority", t.priority)
        put("category", t.category)
        t.projectId?.let { put("projectId", it) }
        t.startAtMillis?.let { put("startAtMillis", it) }
        t.dueAtMillis?.let { put("dueAtMillis", it) }
        t.reminderAtMillis?.let { put("reminderAtMillis", it) }
        t.completedAtMillis?.let { put("completedAtMillis", it) }
        put("recurrence", t.recurrence)
        put("tags", JSONArray(t.tags))
        put("subtasks", JSONArray().apply {
            t.subtasks.forEach { st ->
                put(JSONObject().apply {
                    put("id", st.id)
                    put("title", st.title)
                    put("done", st.done)
                })
            }
        })
        put("archived", t.archived)
        put("deleted", t.deleted)
        put("revision", t.revision)
        put("deviceId", t.deviceId)
        t.createdAt?.toMillisOrNull()?.let { put("createdAt", it) }
        t.updatedAt?.toMillisOrNull()?.let { put("updatedAt", it) }
    }

    fun taskFromJson(o: JSONObject, id: String): TaskItem = TaskItem(
        id = id,
        userId = o.optString("userId", ""),
        title = o.optString("title", ""),
        description = o.optString("description", ""),
        status = TaskStatus.fromRaw(o.optString("status")).name,
        priority = TaskPriority.fromRaw(o.optString("priority")).name,
        category = o.optString("category", "Personal"),
        projectId = o.optString("projectId", "").takeIf { it.isNotBlank() },
        startAtMillis = o.optLongOrNull("startAtMillis"),
        dueAtMillis = o.optLongOrNull("dueAtMillis"),
        reminderAtMillis = o.optLongOrNull("reminderAtMillis"),
        completedAtMillis = o.optLongOrNull("completedAtMillis"),
        recurrence = RecurrenceRule.fromRaw(o.optString("recurrence")).name,
        tags = o.optJSONArray("tags").toStringList(),
        subtasks = o.optJSONArray("subtasks")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                (arr.optJSONObject(i))?.let { st ->
                    SubtaskItem(
                        id = st.optString("id"),
                        title = st.optString("title"),
                        done = st.optBoolean("done", false)
                    )
                }
            }
        } ?: emptyList(),
        archived = o.optBoolean("archived", false),
        deleted = o.optBoolean("deleted", false),
        revision = o.optLong("revision", 1L),
        deviceId = o.optString("deviceId", "android"),
        createdAt = timestampFromMillis(o.optLongOrNull("createdAt")),
        updatedAt = timestampFromMillis(o.optLongOrNull("updatedAt"))
    )

    fun noteToJson(n: NoteItem): JSONObject = JSONObject().apply {
        put("userId", n.userId)
        put("title", n.title)
        put("content", n.content)
        put("category", n.category)
        n.projectId?.let { put("projectId", it) }
        put("tags", JSONArray(n.tags))
        put("linkedTaskIds", JSONArray(n.linkedTaskIds))
        put("pinned", n.pinned)
        put("favorite", n.favorite)
        put("archived", n.archived)
        put("deleted", n.deleted)
        put("wordCount", n.wordCount)
        put("revision", n.revision)
        put("deviceId", n.deviceId)
        n.createdAt?.toMillisOrNull()?.let { put("createdAt", it) }
        n.updatedAt?.toMillisOrNull()?.let { put("updatedAt", it) }
    }

    fun noteFromJson(o: JSONObject, id: String): NoteItem = NoteItem(
        id = id,
        userId = o.optString("userId", ""),
        title = o.optString("title", ""),
        content = o.optString("content", ""),
        category = o.optString("category", "Personal"),
        projectId = o.optString("projectId", "").takeIf { it.isNotBlank() },
        tags = o.optJSONArray("tags").toStringList(),
        linkedTaskIds = o.optJSONArray("linkedTaskIds").toStringList(),
        pinned = o.optBoolean("pinned", false),
        favorite = o.optBoolean("favorite", false),
        archived = o.optBoolean("archived", false),
        deleted = o.optBoolean("deleted", false),
        wordCount = o.optLong("wordCount", 0L),
        revision = o.optLong("revision", 1L),
        deviceId = o.optString("deviceId", "android"),
        createdAt = timestampFromMillis(o.optLongOrNull("createdAt")),
        updatedAt = timestampFromMillis(o.optLongOrNull("updatedAt"))
    )

    fun projectToJson(p: ProjectItem): JSONObject = JSONObject().apply {
        put("userId", p.userId)
        put("name", p.name)
        p.description?.let { put("description", it) }
        put("colorHex", p.colorHex)
        put("iconName", p.iconName)
        put("archived", p.archived)
        put("deleted", p.deleted)
        put("revision", p.revision)
        p.createdAt?.toMillisOrNull()?.let { put("createdAt", it) }
        p.updatedAt?.toMillisOrNull()?.let { put("updatedAt", it) }
    }

    fun projectFromJson(o: JSONObject, id: String): ProjectItem = ProjectItem(
        id = id,
        userId = o.optString("userId", ""),
        name = o.optString("name", "Project"),
        description = o.optString("description", "").takeIf { it.isNotBlank() },
        colorHex = o.optString("colorHex", "#0F5257"),
        iconName = o.optString("iconName", "folder"),
        archived = o.optBoolean("archived", false),
        deleted = o.optBoolean("deleted", false),
        revision = o.optLong("revision", 1L),
        createdAt = timestampFromMillis(o.optLongOrNull("createdAt")),
        updatedAt = timestampFromMillis(o.optLongOrNull("updatedAt"))
    )

    fun reminderToJson(r: ReminderEvent): JSONObject = JSONObject().apply {
        put("userId", r.userId)
        r.taskId?.let { put("taskId", it) }
        put("title", r.title)
        put("message", r.message)
        put("reminderType", r.reminderType)
        put("scheduledAtMillis", r.scheduledAtMillis)
        r.snoozedUntilMillis?.let { put("snoozedUntilMillis", it) }
        put("dismissed", r.dismissed)
        r.createdAt?.toMillisOrNull()?.let { put("createdAt", it) }
        r.updatedAt?.toMillisOrNull()?.let { put("updatedAt", it) }
    }

    fun reminderFromJson(o: JSONObject, id: String): ReminderEvent = ReminderEvent(
        id = id,
        userId = o.optString("userId", ""),
        taskId = o.optString("taskId", "").takeIf { it.isNotBlank() },
        title = o.optString("title", ""),
        message = o.optString("message", ""),
        reminderType = ReminderType.fromRaw(o.optString("reminderType")).name,
        scheduledAtMillis = o.optLong("scheduledAtMillis", 0L),
        snoozedUntilMillis = o.optLongOrNull("snoozedUntilMillis"),
        dismissed = o.optBoolean("dismissed", false),
        createdAt = timestampFromMillis(o.optLongOrNull("createdAt")),
        updatedAt = timestampFromMillis(o.optLongOrNull("updatedAt"))
    )

    fun backupToJson(b: BackupSnapshot): JSONObject = JSONObject().apply {
        put("userId", b.userId)
        put("formatVersion", b.formatVersion)
        put("label", b.label)
        put("taskCount", b.taskCount)
        put("noteCount", b.noteCount)
        put("projectCount", b.projectCount)
        put("checksum", b.checksum)
        put("snapshotData", mapToJsonValue(b.snapshotData))
        b.createdAt?.toMillisOrNull()?.let { put("createdAt", it) }
        b.updatedAt?.toMillisOrNull()?.let { put("updatedAt", it) }
    }

    fun backupFromJson(o: JSONObject, id: String): BackupSnapshot = BackupSnapshot(
        id = id,
        userId = o.optString("userId", ""),
        formatVersion = o.optLong("formatVersion", 1L),
        label = o.optString("label", ""),
        taskCount = o.optLong("taskCount", 0L),
        noteCount = o.optLong("noteCount", 0L),
        projectCount = o.optLong("projectCount", 0L),
        checksum = o.optString("checksum", ""),
        snapshotData = (o.opt("snapshotData") as? JSONObject)?.toMapValue()
            ?: emptyMap(),
        createdAt = timestampFromMillis(o.optLongOrNull("createdAt")),
        updatedAt = timestampFromMillis(o.optLongOrNull("updatedAt"))
    )

    fun profileToJson(p: UserProfile): JSONObject = JSONObject().apply {
        put("userId", p.userId)
        put("displayName", p.displayName)
        p.email?.let { put("email", it) }
        p.photoUrl?.let { put("photoUrl", it) }
        p.lastBackupAtMillis?.let { put("lastBackupAtMillis", it) }
        p.createdAt?.toMillisOrNull()?.let { put("createdAt", it) }
        p.updatedAt?.toMillisOrNull()?.let { put("updatedAt", it) }
    }

    fun profileFromJson(o: JSONObject, id: String): UserProfile = UserProfile(
        id = id,
        userId = o.optString("userId", ""),
        displayName = o.optString("displayName", "Pengguna Lokal"),
        email = o.optString("email", "").takeIf { it.isNotBlank() },
        photoUrl = o.optString("photoUrl", "").takeIf { it.isNotBlank() },
        lastBackupAtMillis = o.optLongOrNull("lastBackupAtMillis"),
        createdAt = timestampFromMillis(o.optLongOrNull("createdAt")),
        updatedAt = timestampFromMillis(o.optLongOrNull("updatedAt"))
    )

    /** Konversi Map Firestore-style (mungkin berisi FieldValue) menjadi JSONObject. */
    private fun mapToJson(payload: Map<String, Any>, nowMillis: Long): JSONObject {
        val json = JSONObject()
        payload.forEach { (key, value) ->
            when {
                value == null -> Unit
                value is FieldValue -> json.put(key, nowMillis) // serverTimestamp -> waktu lokal
                else -> json.put(key, mapToJsonValue(value))
            }
        }
        return json
    }

    private fun mapToJsonValue(value: Any): Any = when (value) {
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (k, v) -> if (k != null && v != null) put(k.toString(), mapToJsonValue(v)) }
        }
        is List<*> -> JSONArray().apply {
            value.forEach { v -> if (v != null) put(mapToJsonValue(v)) }
        }
        else -> value
    }

    private fun JSONObject.toMapValue(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key ->
            when (val v = opt(key)) {
                is JSONObject -> map[key] = v.toMapValue()
                is JSONArray -> map[key] = v.toListValue()
                null, JSONObject.NULL -> Unit
                else -> map[key] = v as Any
            }
        }
        return map
    }

    private fun JSONArray.toListValue(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until length()) {
            when (val v = opt(i)) {
                is JSONObject -> list.add(v.toMapValue())
                is JSONArray -> list.add(v.toListValue())
                null, JSONObject.NULL -> Unit
                else -> list.add(v as Any)
            }
        }
        return list
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            list.add(optString(i))
        }
        return list
    }
}
