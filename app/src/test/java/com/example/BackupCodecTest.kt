package com.example

import com.example.backup.LocalBackupCodec
import com.example.model.BackupSnapshot
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-04: verifikasi checksum backup cloud kini membandingkan hash sungguhan. (JVM murni — org.json disediakan testImplementation) */
class BackupCodecTest {

    private fun sampleData(): Triple<List<ProjectItem>, List<TaskItem>, List<NoteItem>> {
        val project = ProjectItem(id = "prj_1", name = "Kantor", colorHex = "#0F5257", userId = "u1")
        val task = TaskItem(
            id = "tsk_1",
            title = "Kirim laporan",
            description = "Bulananan",
            status = "IN_PROGRESS",
            priority = "HIGH",
            dueAtMillis = 1_736_000_000_000L,
            tags = listOf("kerja"),
            userId = "u1"
        )
        val note = NoteItem(
            id = "nte_1",
            title = "Agenda",
            content = "isi agenda",
            pinned = true,
            wordCount = 2L,
            userId = "u1"
        )
        return Triple(listOf(project), listOf(task), listOf(note))
    }

    @Test
    fun `export lalu import file json lolos checksum`() {
        val (projects, tasks, notes) = sampleData()
        val (json, _) = LocalBackupCodec.encodeToJson("Backup Uji", projects, tasks, notes)

        val result = LocalBackupCodec.decodeFromJson(json)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().checksumValid)
        assertEquals(1, result.getOrThrow().tasks.size)
    }

    @Test
    fun `isi file yang diubah gagal checksum`() {
        val (projects, tasks, notes) = sampleData()
        val (json, _) = LocalBackupCodec.encodeToJson("Backup Uji", projects, tasks, notes)
        val tampered = json.replace("Kirim laporan", "Kirim laporan PALSU")

        val result = LocalBackupCodec.decodeFromJson(tampered)

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().checksumValid)
    }

    @Test
    fun `snapshot cloud utuh lolos verifikasi checksum`() {
        val (projects, tasks, notes) = sampleData()
        val snapshotMap = LocalBackupCodec.buildNativeSnapshotMap(projects, tasks, notes)
        val checksum = LocalBackupCodec.computeSnapshotChecksum(projects, tasks, notes)
        val snapshot = BackupSnapshot(
            id = "bkp_1",
            userId = "u1",
            formatVersion = 1L,
            label = "Cloud",
            taskCount = tasks.size.toLong(),
            noteCount = notes.size.toLong(),
            projectCount = projects.size.toLong(),
            checksum = checksum,
            snapshotData = snapshotMap
        )

        val preview = LocalBackupCodec.decodeFromCloudSnapshot(snapshot)

        assertTrue(preview.checksumValid)
        assertEquals(1, preview.tasks.size)
        assertEquals(1, preview.notes.size)
    }

    @Test
    fun `snapshot cloud yang dimanipulasi gagal verifikasi checksum`() {
        val (projects, tasks, notes) = sampleData()
        val snapshotMap = LocalBackupCodec.buildNativeSnapshotMap(projects, tasks, notes)
        val snapshot = BackupSnapshot(
            id = "bkp_1",
            userId = "u1",
            formatVersion = 1L,
            label = "Cloud",
            taskCount = tasks.size.toLong(),
            noteCount = notes.size.toLong(),
            projectCount = projects.size.toLong(),
            checksum = "panjangnya_delapan", // dulu lolos karena length >= 8
            snapshotData = snapshotMap
        )

        val preview = LocalBackupCodec.decodeFromCloudSnapshot(snapshot)

        // checksum lama "lolos" hanya karena panjangnya >= 8 karakter;
        // sekarang harus gagal karena tidak cocok dengan hash data.
        assertFalse(preview.checksumValid)
    }
}
