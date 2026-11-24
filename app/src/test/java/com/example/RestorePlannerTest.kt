package com.example

import com.example.data.remote.RestorePlanner
import com.example.model.BackupPreview
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C-05: rencana restore — tanpa penimpaan dokumen cloud yang hidup, resurrect
 * dokumen terhapus dengan revision monoton, dan pengawetan flag dari backup.
 */
class RestorePlannerTest {

    private fun previewOf(
        projects: List<ProjectItem> = emptyList(),
        tasks: List<TaskItem> = emptyList(),
        notes: List<NoteItem> = emptyList()
    ) = BackupPreview(
        formatVersion = 1,
        label = "test",
        checksum = "abcd1234abcd1234",
        checksumValid = true,
        projects = projects,
        tasks = tasks,
        notes = notes
    )

    @Test
    fun `dokumen baru dipulihkan dengan revision 1`() {
        val task = TaskItem(id = "tsk_abc123", title = "Belajar", userId = "u1")
        val plan = RestorePlanner.buildRestorePlan(
            uid = "u1",
            deviceId = "dev-1",
            preview = previewOf(tasks = listOf(task)),
            existingProjects = emptyMap(),
            existingTasks = emptyMap(),
            existingNotes = emptyMap()
        )
        assertEquals(1, plan.restoredCount)
        assertEquals(0, plan.skippedCount)
        val write = plan.writes.single()
        assertEquals("tasks", write.collection)
        assertEquals("tsk_abc123", write.docId)
        assertEquals(1L, write.payload["revision"])
        assertEquals("u1", write.payload["userId"])
        assertEquals("dev-1", write.payload["deviceId"])
        assertEquals(false, write.payload["deleted"])
    }

    @Test
    fun `dokumen cloud yang masih hidup tidak ditimpa`() {
        val task = TaskItem(id = "tsk_abc123", title = "Belajar", userId = "u1")
        val plan = RestorePlanner.buildRestorePlan(
            uid = "u1",
            deviceId = "dev-1",
            preview = previewOf(tasks = listOf(task)),
            existingProjects = emptyMap(),
            existingTasks = mapOf("tsk_abc123" to RestorePlanner.ExistingDoc(revision = 7L, deleted = false)),
            existingNotes = emptyMap()
        )
        assertEquals(0, plan.restoredCount)
        assertEquals(1, plan.skippedCount)
        assertTrue(plan.writes.isEmpty())
    }

    @Test
    fun `dokumen terhapus dihidupkan kembali dengan revision plus satu dan createdAt dipertahankan`() {
        val createdAtMarker = Any()
        val task = TaskItem(id = "tsk_abc123", title = "Belajar", userId = "u1")
        val plan = RestorePlanner.buildRestorePlan(
            uid = "u1",
            deviceId = "dev-1",
            preview = previewOf(tasks = listOf(task)),
            existingProjects = emptyMap(),
            existingTasks = mapOf("tsk_abc123" to RestorePlanner.ExistingDoc(revision = 5L, deleted = true, createdAt = createdAtMarker)),
            existingNotes = emptyMap()
        )
        assertEquals(1, plan.restoredCount)
        val payload = plan.writes.single().payload
        assertEquals(6L, payload["revision"])
        assertEquals(false, payload["deleted"])
        assertEquals(createdAtMarker, payload["createdAt"])
    }

    @Test
    fun `flag note dipertahankan dari backup`() {
        val note = NoteItem(
            id = "nte_1",
            title = "Ide",
            content = "isi catatan",
            pinned = true,
            favorite = true,
            archived = true,
            wordCount = 3L,
            userId = "u1"
        )
        val plan = RestorePlanner.buildRestorePlan(
            uid = "u1",
            deviceId = "dev-1",
            preview = previewOf(notes = listOf(note)),
            existingProjects = emptyMap(),
            existingTasks = emptyMap(),
            existingNotes = emptyMap()
        )
        val payload = plan.writes.single().payload
        assertEquals(true, payload["pinned"])
        assertEquals(true, payload["favorite"])
        assertEquals(true, payload["archived"])
        assertEquals(3L, payload["wordCount"])
    }

    @Test
    fun `flag task dipertahankan dari backup`() {
        val task = TaskItem(
            id = "tsk_1",
            title = "Rapat",
            status = "COMPLETED",
            completedAtMillis = 1_735_000_000_000L,
            archived = true,
            userId = "u1"
        )
        val plan = RestorePlanner.buildRestorePlan(
            uid = "u1",
            deviceId = "dev-1",
            preview = previewOf(tasks = listOf(task)),
            existingProjects = emptyMap(),
            existingTasks = emptyMap(),
            existingNotes = emptyMap()
        )
        val payload = plan.writes.single().payload
        assertEquals("COMPLETED", payload["status"])
        assertEquals(1_735_000_000_000L, payload["completedAtMillis"])
        assertEquals(true, payload["archived"])
    }

    @Test
    fun `id dokumen invalid diganti id baru yang aman`() {
        val task = TaskItem(id = "../etc/passwd", title = "Jahat", userId = "u1")
        val plan = RestorePlanner.buildRestorePlan(
            uid = "u1",
            deviceId = "dev-1",
            preview = previewOf(tasks = listOf(task)),
            existingProjects = emptyMap(),
            existingTasks = emptyMap(),
            existingNotes = emptyMap()
        )
        val write = plan.writes.single()
        assertNotEquals("../etc/passwd", write.docId)
        assertTrue(write.docId.startsWith("tsk_rst_"))
    }

    @Test
    fun `project archived dipertahankan`() {
        val project = ProjectItem(id = "prj_1", name = "Kerjaan", archived = true, userId = "u1")
        val plan = RestorePlanner.buildRestorePlan(
            uid = "u1",
            deviceId = "dev-1",
            preview = previewOf(projects = listOf(project)),
            existingProjects = emptyMap(),
            existingTasks = emptyMap(),
            existingNotes = emptyMap()
        )
        assertEquals(true, plan.writes.single().payload["archived"])
    }
}
