package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalTaskFlowStore
import com.example.data.local.TaskFlowLocalDatabase
import com.example.data.local.TaskFlowLocalMode
import com.example.model.BackupPreview
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.ReminderEvent
import com.example.model.ReminderType
import com.example.model.SubtaskItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pengujian LocalTaskFlowStore: codec JSON round-trip, observer Room,
 * semantik revision monoton, spawn tugas berulang, dan restore merge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class LocalStoreTest {

    private lateinit var db: TaskFlowLocalDatabase
    private lateinit var store: LocalTaskFlowStore
    private val owner = TaskFlowLocalMode.LOCAL_USER_ID

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaskFlowLocalDatabase::class.java
        ).allowMainThreadQueries().build()
        store = LocalTaskFlowStore(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // ---------- CODEC ROUND-TRIP ----------

    @Test
    fun `task codec round-trip preserves all fields`() {
        val task = TaskItem(
            id = "tsk_abc123",
            userId = owner,
            title = "Rapat mingguan",
            description = "Agenda sprint",
            status = TaskStatus.IN_PROGRESS.name,
            priority = TaskPriority.HIGH.name,
            category = "Kerja",
            projectId = "prj_1",
            startAtMillis = 1000L,
            dueAtMillis = 2000L,
            reminderAtMillis = 1500L,
            completedAtMillis = null,
            recurrence = RecurrenceRule.WEEKLY.name,
            tags = listOf("rapat", "sprint"),
            subtasks = listOf(SubtaskItem("st1", "Siapkan materi", true)),
            archived = true,
            deleted = false,
            revision = 7L,
            deviceId = "android-test",
            createdAt = com.google.firebase.Timestamp(1000L, 0),
            updatedAt = com.google.firebase.Timestamp(2000L, 0)
        )
        val json = store.taskToJson(task)
        val decoded = store.taskFromJson(json, "tsk_abc123")
        assertEquals(task, decoded)
    }

    @Test
    fun `note codec round-trip preserves flags and links`() {
        val note = NoteItem(
            id = "nte_1",
            userId = owner,
            title = "Catatan penting",
            content = "Isi catatan",
            category = "Personal",
            projectId = "prj_1",
            tags = listOf("ide"),
            linkedTaskIds = listOf("tsk_1", "tsk_2"),
            pinned = true,
            favorite = true,
            archived = false,
            deleted = true,
            wordCount = 3L,
            revision = 5L,
            deviceId = "android-test"
        )
        val decoded = store.noteFromJson(store.noteToJson(note), "nte_1")
        assertEquals(note, decoded)
    }

    // ---------- OBSERVER & UPsert ----------

    @Test
    fun `upsert task emits to observer and hides deleted`() = runTest {
        val task = TaskItem(id = "tsk_1", userId = owner, title = "Tugas A", revision = 1L)
        store.upsertTask(owner, task)
        assertEquals(1, store.observeTasks(owner).first().size)

        store.upsertTask(owner, task.copy(deleted = true, revision = 2L))
        assertEquals(0, store.observeTasks(owner).first().size)
        // Data ter-soft-delete tetap ada untuk jalur restore:
        assertEquals(1, store.listTasks(owner).size)
    }

    @Test
    fun `ensure profile creates once and update preserves it`() = runTest {
        store.ensureProfile(owner, "Pengguna Lokal")
        store.ensureProfile(owner, "Nama Lain") // tidak boleh menimpa
        val profile = store.observeProfile(owner).first()
        assertNotNull(profile)
        assertEquals("Pengguna Lokal", profile?.displayName)

        store.updateProfileBackupTime(owner, 12345L)
        val updated = store.observeProfile(owner).first()
        assertEquals(12345L, updated?.lastBackupAtMillis)
        assertEquals("Pengguna Lokal", updated?.displayName)
    }

    // ---------- SEMANTIK REVISION & SPAWN ----------

    @Test
    fun `soft delete bumps revision and keeps task retrievable`() = runTest {
        val task = TaskItem(id = "tsk_1", userId = owner, title = "Tugas", revision = 3L)
        store.upsertTask(owner, task)
        store.upsertTask(
            owner,
            (store.getTask(owner, "tsk_1") ?: task).copy(deleted = true, revision = 4L)
        )
        val stored = store.getTask(owner, "tsk_1")
        assertNotNull(stored)
        assertEquals(4L, stored?.revision)
        assertTrue(stored?.deleted == true)
    }

    @Test
    fun `spawn occurrence uses deterministic id`() = runTest {
        val parent = TaskItem(
            id = "tsk_parent",
            userId = owner,
            title = "Olahraga",
            recurrence = RecurrenceRule.DAILY.name,
            dueAtMillis = 1_700_000_000_000L,
            revision = 1L
        )
        store.upsertTask(owner, parent)
        val nextDue = com.example.data.remote.TaskFlowRepository
            .computeNextOccurrenceMillis(parent.dueAtMillis!!, RecurrenceRule.DAILY)
        val spawnId = "${parent.id}_occ_$nextDue"
        store.upsertTask(
            owner,
            parent.copy(id = spawnId, status = TaskStatus.TODO.name, revision = 1L)
        )
        // Idempoten: menulis ulang spawn dengan ID sama tidak menduplikasi baris.
        store.upsertTask(
            owner,
            parent.copy(id = spawnId, status = TaskStatus.TODO.name, revision = 1L)
        )
        assertEquals(1, store.listTasks(owner).count { it.id == spawnId })
    }

    // ---------- REMINDER & BACKUP ----------

    @Test
    fun `reminder observer sorts by scheduled time descending`() = runTest {
        store.upsertReminder(
            owner,
            ReminderEvent(id = "rem_1", userId = owner, title = "A", scheduledAtMillis = 100L)
        )
        store.upsertReminder(
            owner,
            ReminderEvent(id = "rem_2", userId = owner, title = "B", scheduledAtMillis = 200L)
        )
        val events = store.observeReminders(owner).first()
        assertEquals(listOf("rem_2", "rem_1"), events.map { it.id })
    }

    @Test
    fun `backup round-trip preserves counts and snapshot data`() = runTest {
        val snapshotData = mapOf(
            "tasks" to listOf(
                mapOf("docId" to "tsk_1", "title" to "Tugas 1", "status" to "TODO", "subtasks" to emptyList<Any>())
            )
        )
        val backup = com.example.model.BackupSnapshot(
            id = "bkp_1",
            userId = owner,
            label = "Backup Uji",
            taskCount = 1L,
            checksum = "abc123",
            snapshotData = snapshotData
        )
        store.upsertBackup(owner, backup)
        val decoded = store.observeBackups(owner).first().first()
        assertEquals("bkp_1", decoded.id)
        assertEquals(1L, decoded.taskCount)
        assertEquals("Backup Uji", decoded.label)
        val tasks = decoded.snapshotData["tasks"] as? List<*>
        assertEquals(1, tasks?.size)
    }

    // ---------- RESTORE (jalur mode lokal) ----------

    @Test
    fun `apply restore writes creates rows and decodes back`() = runTest {
        val write = com.example.data.remote.RestorePlanner.PlannedWrite(
            collection = "tasks",
            docId = "tsk_restore_1",
            payload = mapOf(
                "userId" to owner,
                "title" to "Tugas Restore",
                "description" to "",
                "status" to "TODO",
                "priority" to "MEDIUM",
                "category" to "Personal",
                "recurrence" to "NONE",
                "tags" to emptyList<String>(),
                "subtasks" to emptyList<Map<String, Any>>(),
                "archived" to false,
                "deleted" to false,
                "revision" to 1L,
                "deviceId" to "android-test",
                "createdAt" to 111L,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        )
        store.applyRestoreWrites(owner, listOf(write))
        val task = store.getTask(owner, "tsk_restore_1")
        assertNotNull(task)
        assertEquals("Tugas Restore", task?.title)
        assertEquals(1L, task?.revision)
        // FieldValue.serverTimestamp() diterjemahkan menjadi waktu lokal:
        assertNotNull(task?.updatedAt)
        assertEquals(111L, task?.createdAt?.toDate()?.time)
    }

    @Test
    fun `restore planner skips live docs and revives deleted ones locally`() = runTest {
        // Dokumen hidup -> dilewati; dokumen ter-soft-delete -> dihidupkan.
        val live = TaskItem(id = "tsk_live", userId = owner, title = "Hidup", revision = 4L)
        val dead = TaskItem(id = "tsk_dead", userId = owner, title = "Mati", revision = 2L, deleted = true)
        store.upsertTask(owner, live)
        store.upsertTask(owner, dead)

        val preview = BackupPreview(
            formatVersion = 1,
            label = "Backup",
            checksum = "x",
            checksumValid = true,
            projects = emptyList(),
            tasks = listOf(live, dead.copy(deleted = false, title = "Mati Pulih")),
            notes = emptyList()
        )
        val plan = com.example.data.remote.RestorePlanner.buildRestorePlan(
            uid = owner,
            deviceId = "android-test",
            preview = preview,
            existingProjects = emptyMap(),
            existingTasks = store.listTasks(owner).associate {
                it.id to com.example.data.remote.RestorePlanner.ExistingDoc(it.revision, it.deleted, null)
            },
            existingNotes = emptyMap()
        )
        assertEquals(1, plan.restoredCount)
        assertEquals(1, plan.skippedCount)

        store.applyRestoreWrites(owner, plan.writes)
        val revived = store.getTask(owner, "tsk_dead")
        assertNotNull(revived)
        assertFalse(revived!!.deleted)
        assertEquals("Mati Pulih", revived.title)
        assertEquals(3L, revived.revision) // revision cloud + 1
        // Dokumen hidup tidak ditimpa:
        assertEquals("Hidup", store.getTask(owner, "tsk_live")?.title)
        assertEquals(4L, store.getTask(owner, "tsk_live")?.revision)
    }
}
