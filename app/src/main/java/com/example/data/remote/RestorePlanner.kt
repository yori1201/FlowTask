package com.example.data.remote

import com.example.model.BackupPreview
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.TaskItem
import com.google.firebase.firestore.FieldValue
import java.util.UUID

/**
 * C-05: rencana pemulihan backup sebagai fungsi murni agar dapat diuji unit.
 *
 * Aturan:
 *  - Dokumen yang belum ada di cloud            -> dibuat (revision 1).
 *  - Dokumen cloud ada dan `deleted = true`     -> dihidupkan kembali dengan
 *    revision cloud + 1 dan createdAt yang dipertahankan (aturan Firestore
 *    mensyaratkan createdAt tidak berubah saat update).
 *  - Dokumen cloud ada dan masih hidup          -> DILEWATI. Tidak ada
 *    penimpaan senyap (PRD prinsip #4); data cloud yang lebih baru menang.
 *  - Flag `archived`, `pinned`, `favorite`, `completedAtMillis`, dan `status`
 *    dipertahankan dari backup (dulu hilang karena dipulihkan lewat
 *    createTask/createNote yang meng-hardcode nilai default).
 *  - ID dokumen dari backup divalidasi regex; ID invalid/kosong mendapat ID
 *    baru agar payload jahat tidak bisa menyuntik path koleksi.
 */
object RestorePlanner {

    private val DOC_ID_PATTERN = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

    /** Kondisi dokumen cloud saat restore dijalankan. */
    data class ExistingDoc(
        val revision: Long,
        val deleted: Boolean,
        val createdAt: Any? = null
    )

    /** Satu operasi tulis yang direncanakan. */
    data class PlannedWrite(
        val collection: String,
        val docId: String,
        val payload: Map<String, Any>
    )

    data class RestoreOutcome(
        val restoredCount: Int,
        val skippedCount: Int,
        val writes: List<PlannedWrite>
    )

    fun buildRestorePlan(
        uid: String,
        deviceId: String,
        preview: BackupPreview,
        existingProjects: Map<String, ExistingDoc>,
        existingTasks: Map<String, ExistingDoc>,
        existingNotes: Map<String, ExistingDoc>
    ): RestoreOutcome {
        val writes = mutableListOf<PlannedWrite>()
        var skipped = 0

        // null = lewati; selain itu = nilai revision untuk dokumen hasil restore.
        fun decideRevision(existing: ExistingDoc?): Long? = when {
            existing == null -> 1L
            existing.deleted -> existing.revision + 1L
            else -> null
        }

        fun createdAtFor(existing: ExistingDoc?): Any =
            existing?.createdAt ?: FieldValue.serverTimestamp()

        val cleanDevice = deviceId.trim().take(80).ifEmpty { "android" }

        preview.projects.forEach { p ->
            val existing = existingProjects[p.id]
            val revision = decideRevision(existing)
            if (revision == null) {
                skipped++
                return@forEach
            }
            writes.add(
                PlannedWrite(
                    collection = "projects",
                    docId = sanitizeDocId(p.id, "prj"),
                    payload = buildProjectPayload(uid, p, revision, createdAtFor(existing))
                )
            )
        }

        preview.tasks.forEach { t ->
            val existing = existingTasks[t.id]
            val revision = decideRevision(existing)
            if (revision == null) {
                skipped++
                return@forEach
            }
            writes.add(
                PlannedWrite(
                    collection = "tasks",
                    docId = sanitizeDocId(t.id, "tsk"),
                    payload = buildTaskPayload(uid, cleanDevice, t, revision, createdAtFor(existing))
                )
            )
        }

        preview.notes.forEach { n ->
            val existing = existingNotes[n.id]
            val revision = decideRevision(existing)
            if (revision == null) {
                skipped++
                return@forEach
            }
            writes.add(
                PlannedWrite(
                    collection = "notes",
                    docId = sanitizeDocId(n.id, "nte"),
                    payload = buildNotePayload(uid, cleanDevice, n, revision, createdAtFor(existing))
                )
            )
        }

        return RestoreOutcome(restoredCount = writes.size, skippedCount = skipped, writes = writes)
    }

    /** ID valid dipakai apa adanya; ID kosong/invalid diganti ID baru yang aman. */
    internal fun sanitizeDocId(rawId: String, prefix: String): String {
        if (rawId.matches(DOC_ID_PATTERN)) return rawId
        return "${prefix}_rst_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    }

    internal fun countWords(text: String): Long {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0L
        return trimmed.split(Regex("\\s+")).size.toLong().coerceAtMost(50000L)
    }

    private fun buildProjectPayload(
        uid: String,
        p: ProjectItem,
        revision: Long,
        createdAt: Any
    ): Map<String, Any> {
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "name" to p.name.trim().take(80).ifEmpty { "Proyek" },
            "description" to p.description?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
            "colorHex" to p.colorHex.trim().take(9).ifEmpty { "#0F5257" },
            "iconName" to p.iconName.trim().take(40).ifEmpty { "folder" },
            "archived" to p.archived,
            "deleted" to false,
            "revision" to revision,
            "createdAt" to createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        @Suppress("UNCHECKED_CAST")
        return payload as Map<String, Any>
    }

    private fun buildTaskPayload(
        uid: String,
        deviceId: String,
        t: TaskItem,
        revision: Long,
        createdAt: Any
    ): Map<String, Any> {
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "title" to t.title.trim().take(160).ifEmpty { "Tugas" },
            "description" to t.description.take(4000),
            "status" to t.status,
            "priority" to t.priority,
            "category" to t.category.trim().take(40).ifEmpty { "Personal" },
            "projectId" to t.projectId?.takeIf { it.isNotBlank() },
            "dueAtMillis" to t.dueAtMillis?.takeIf { it >= 0L },
            "reminderAtMillis" to t.reminderAtMillis?.takeIf { it >= 0L },
            "completedAtMillis" to t.completedAtMillis?.takeIf { it >= 0L },
            "recurrence" to t.recurrence,
            "tags" to t.tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10),
            "subtasks" to t.subtasks.filter { it.title.isNotBlank() }.take(20).map { it.toMap() },
            "archived" to t.archived,
            "deleted" to false,
            "revision" to revision,
            "deviceId" to deviceId,
            "createdAt" to createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        @Suppress("UNCHECKED_CAST")
        return payload as Map<String, Any>
    }

    private fun buildNotePayload(
        uid: String,
        deviceId: String,
        n: NoteItem,
        revision: Long,
        createdAt: Any
    ): Map<String, Any> {
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "title" to n.title.trim().take(160),
            "content" to n.content.take(50000),
            "category" to n.category.trim().take(40).ifEmpty { "Personal" },
            "projectId" to n.projectId?.takeIf { it.isNotBlank() },
            "tags" to n.tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10),
            "linkedTaskIds" to n.linkedTaskIds.distinct().take(20),
            "pinned" to n.pinned,
            "favorite" to n.favorite,
            "archived" to n.archived,
            "deleted" to false,
            "wordCount" to countWords("${n.title.trim()} ${n.content}"),
            "revision" to revision,
            "deviceId" to deviceId,
            "createdAt" to createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        @Suppress("UNCHECKED_CAST")
        return payload as Map<String, Any>
    }
}
