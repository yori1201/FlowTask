package com.example.backup

import com.example.model.BackupPreview
import com.example.model.NoteItem
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.SubtaskItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object LocalBackupCodec {
    const val CURRENT_FORMAT_VERSION = 1

    fun computeChecksum(canonicalPayload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(canonicalPayload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun buildNativeSnapshotMap(
        projects: List<ProjectItem>,
        tasks: List<TaskItem>,
        notes: List<NoteItem>
    ): Map<String, Any> {
        val projectMaps = projects.filter { !it.deleted }.map { p ->
            mapOf(
                "docId" to p.id,
                "name" to p.name,
                "description" to (p.description ?: ""),
                "colorHex" to p.colorHex,
                "iconName" to p.iconName,
                "archived" to p.archived
            )
        }
        val taskMaps = tasks.filter { !it.deleted }.map { t ->
            buildMap<String, Any> {
                put("docId", t.id)
                put("title", t.title)
                put("description", t.description)
                put("status", t.status)
                put("priority", t.priority)
                put("category", t.category)
                t.projectId?.let { put("projectId", it) }
                t.dueAtMillis?.let { put("dueAtMillis", it) }
                t.reminderAtMillis?.let { put("reminderAtMillis", it) }
                t.completedAtMillis?.let { put("completedAtMillis", it) }
                put("recurrence", t.recurrence)
                put("tags", t.tags)
                put("subtasks", t.subtasks.map { it.toMap() })
                put("archived", t.archived)
            }
        }
        val noteMaps = notes.filter { !it.deleted }.map { n ->
            buildMap<String, Any> {
                put("docId", n.id)
                put("title", n.title)
                put("content", n.content)
                put("category", n.category)
                n.projectId?.let { put("projectId", it) }
                put("tags", n.tags)
                put("linkedTaskIds", n.linkedTaskIds)
                put("pinned", n.pinned)
                put("favorite", n.favorite)
                put("archived", n.archived)
                put("wordCount", n.wordCount)
            }
        }
        return mapOf(
            "projects" to projectMaps,
            "tasks" to taskMaps,
            "notes" to noteMaps
        )
    }

    fun encodeToJson(
        label: String,
        projects: List<ProjectItem>,
        tasks: List<TaskItem>,
        notes: List<NoteItem>
    ): Pair<String, String> {
        val dataObj = JSONObject().apply {
            put("projects", JSONArray().apply {
                projects.filter { !it.deleted }.forEach { p ->
                    put(JSONObject().apply {
                        put("docId", p.id)
                        put("name", p.name)
                        put("description", p.description ?: "")
                        put("colorHex", p.colorHex)
                        put("iconName", p.iconName)
                        put("archived", p.archived)
                    })
                }
            })
            put("tasks", JSONArray().apply {
                tasks.filter { !it.deleted }.forEach { t ->
                    put(JSONObject().apply {
                        put("docId", t.id)
                        put("title", t.title)
                        put("description", t.description)
                        put("status", t.status)
                        put("priority", t.priority)
                        put("category", t.category)
                        if (t.projectId != null) put("projectId", t.projectId)
                        if (t.dueAtMillis != null) put("dueAtMillis", t.dueAtMillis)
                        if (t.reminderAtMillis != null) put("reminderAtMillis", t.reminderAtMillis)
                        if (t.completedAtMillis != null) put("completedAtMillis", t.completedAtMillis)
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
                    })
                }
            })
            put("notes", JSONArray().apply {
                notes.filter { !it.deleted }.forEach { n ->
                    put(JSONObject().apply {
                        put("docId", n.id)
                        put("title", n.title)
                        put("content", n.content)
                        put("category", n.category)
                        if (n.projectId != null) put("projectId", n.projectId)
                        put("tags", JSONArray(n.tags))
                        put("linkedTaskIds", JSONArray(n.linkedTaskIds))
                        put("pinned", n.pinned)
                        put("favorite", n.favorite)
                        put("archived", n.archived)
                        put("wordCount", n.wordCount)
                    })
                }
            })
        }

        val canonical = dataObj.toString()
        val checksum = computeChecksum(canonical)
        val root = JSONObject().apply {
            put("app", "TaskFlow")
            put("formatVersion", CURRENT_FORMAT_VERSION)
            put("label", label)
            put("exportedAtMillis", System.currentTimeMillis())
            put("checksum", checksum)
            put("data", dataObj)
        }
        return root.toString(2) to checksum
    }

    fun decodeFromJson(rawJson: String): Result<BackupPreview> = runCatching {
        val root = JSONObject(rawJson)
        val version = root.optInt("formatVersion", -1)
        require(version in 1..CURRENT_FORMAT_VERSION) {
            "Versi format backup tidak didukung ($version)."
        }
        val label = root.optString("label", "Backup Lokal")
        val expectedChecksum = root.optString("checksum", "")
        val dataObj = root.getJSONObject("data")
        val actualChecksum = computeChecksum(dataObj.toString())
        val checksumValid = expectedChecksum.isNotBlank() && expectedChecksum == actualChecksum

        val projects = mutableListOf<ProjectItem>()
        val projectsArr = dataObj.optJSONArray("projects") ?: JSONArray()
        for (i in 0 until projectsArr.length()) {
            val o = projectsArr.getJSONObject(i)
            projects.add(
                ProjectItem(
                    id = o.optString("docId", ""),
                    name = o.optString("name", "Project").take(80),
                    description = o.optString("description", "").takeIf { it.isNotBlank() },
                    colorHex = o.optString("colorHex", "#0F5257"),
                    iconName = o.optString("iconName", "folder"),
                    archived = o.optBoolean("archived", false)
                )
            )
        }

        val tasks = mutableListOf<TaskItem>()
        val tasksArr = dataObj.optJSONArray("tasks") ?: JSONArray()
        for (i in 0 until tasksArr.length()) {
            val o = tasksArr.getJSONObject(i)
            val tagsList = mutableListOf<String>()
            val tagsArr = o.optJSONArray("tags") ?: JSONArray()
            for (j in 0 until minOf(tagsArr.length(), 10)) {
                tagsList.add(tagsArr.optString(j))
            }
            val subtasksList = mutableListOf<SubtaskItem>()
            val stArr = o.optJSONArray("subtasks") ?: JSONArray()
            for (j in 0 until minOf(stArr.length(), 20)) {
                val st = stArr.getJSONObject(j)
                subtasksList.add(
                    SubtaskItem(
                        id = st.optString("id", "st_$j"),
                        title = st.optString("title", ""),
                        done = st.optBoolean("done", false)
                    )
                )
            }
            tasks.add(
                TaskItem(
                    id = o.optString("docId", ""),
                    title = o.optString("title", "Tugas").take(160),
                    description = o.optString("description", "").take(4000),
                    status = TaskStatus.fromRaw(o.optString("status")).name,
                    priority = TaskPriority.fromRaw(o.optString("priority")).name,
                    category = o.optString("category", "Personal").take(40),
                    projectId = o.optString("projectId", "").takeIf { it.isNotBlank() },
                    dueAtMillis = if (o.has("dueAtMillis")) o.optLong("dueAtMillis") else null,
                    reminderAtMillis = if (o.has("reminderAtMillis")) o.optLong("reminderAtMillis") else null,
                    completedAtMillis = if (o.has("completedAtMillis")) o.optLong("completedAtMillis") else null,
                    recurrence = RecurrenceRule.fromRaw(o.optString("recurrence")).name,
                    tags = tagsList,
                    subtasks = subtasksList,
                    archived = o.optBoolean("archived", false)
                )
            )
        }

        val notes = mutableListOf<NoteItem>()
        val notesArr = dataObj.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notesArr.length()) {
            val o = notesArr.getJSONObject(i)
            val tagsList = mutableListOf<String>()
            val tagsArr = o.optJSONArray("tags") ?: JSONArray()
            for (j in 0 until minOf(tagsArr.length(), 10)) {
                tagsList.add(tagsArr.optString(j))
            }
            val linkedList = mutableListOf<String>()
            val lArr = o.optJSONArray("linkedTaskIds") ?: JSONArray()
            for (j in 0 until minOf(lArr.length(), 20)) {
                linkedList.add(lArr.optString(j))
            }
            notes.add(
                NoteItem(
                    id = o.optString("docId", ""),
                    title = o.optString("title", "").take(160),
                    content = o.optString("content", "").take(50000),
                    category = o.optString("category", "Personal").take(40),
                    projectId = o.optString("projectId", "").takeIf { it.isNotBlank() },
                    tags = tagsList,
                    linkedTaskIds = linkedList,
                    pinned = o.optBoolean("pinned", false),
                    favorite = o.optBoolean("favorite", false),
                    archived = o.optBoolean("archived", false),
                    wordCount = o.optLong("wordCount", 0L)
                )
            )
        }

        BackupPreview(
            formatVersion = version,
            label = label,
            checksum = expectedChecksum.ifBlank { actualChecksum },
            checksumValid = checksumValid,
            projects = projects,
            tasks = tasks,
            notes = notes
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun decodeFromCloudSnapshot(snapshot: com.example.model.BackupSnapshot): BackupPreview {
        val rawProjects = (snapshot.snapshotData["projects"] as? List<Map<String, Any?>>).orEmpty()
        val rawTasks = (snapshot.snapshotData["tasks"] as? List<Map<String, Any?>>).orEmpty()
        val rawNotes = (snapshot.snapshotData["notes"] as? List<Map<String, Any?>>).orEmpty()

        val projects = rawProjects.map { m ->
            ProjectItem(
                id = (m["docId"] as? String).orEmpty(),
                name = (m["name"] as? String).orEmpty().ifBlank { "Project" },
                description = (m["description"] as? String)?.takeIf { it.isNotBlank() },
                colorHex = (m["colorHex"] as? String) ?: "#0F5257",
                iconName = (m["iconName"] as? String) ?: "folder",
                archived = (m["archived"] as? Boolean) ?: false
            )
        }
        val tasks = rawTasks.map { m ->
            val rawSubtasks = (m["subtasks"] as? List<Map<*, *>>).orEmpty()
            TaskItem(
                id = (m["docId"] as? String).orEmpty(),
                title = (m["title"] as? String).orEmpty().ifBlank { "Tugas" },
                description = (m["description"] as? String).orEmpty(),
                status = TaskStatus.fromRaw(m["status"] as? String).name,
                priority = TaskPriority.fromRaw(m["priority"] as? String).name,
                category = (m["category"] as? String) ?: "Personal",
                projectId = (m["projectId"] as? String)?.takeIf { it.isNotBlank() },
                dueAtMillis = (m["dueAtMillis"] as? Number)?.toLong(),
                reminderAtMillis = (m["reminderAtMillis"] as? Number)?.toLong(),
                completedAtMillis = (m["completedAtMillis"] as? Number)?.toLong(),
                recurrence = RecurrenceRule.fromRaw(m["recurrence"] as? String).name,
                tags = (m["tags"] as? List<*>)?.mapNotNull { it as? String }?.take(10).orEmpty(),
                subtasks = rawSubtasks.take(20).map { SubtaskItem.fromMap(it) },
                archived = (m["archived"] as? Boolean) ?: false
            )
        }
        val notes = rawNotes.map { m ->
            NoteItem(
                id = (m["docId"] as? String).orEmpty(),
                title = (m["title"] as? String).orEmpty(),
                content = (m["content"] as? String).orEmpty(),
                category = (m["category"] as? String) ?: "Personal",
                projectId = (m["projectId"] as? String)?.takeIf { it.isNotBlank() },
                tags = (m["tags"] as? List<*>)?.mapNotNull { it as? String }?.take(10).orEmpty(),
                linkedTaskIds = (m["linkedTaskIds"] as? List<*>)?.mapNotNull { it as? String }?.take(20).orEmpty(),
                pinned = (m["pinned"] as? Boolean) ?: false,
                favorite = (m["favorite"] as? Boolean) ?: false,
                archived = (m["archived"] as? Boolean) ?: false,
                wordCount = (m["wordCount"] as? Number)?.toLong() ?: 0L
            )
        }
        return BackupPreview(
            formatVersion = snapshot.formatVersion.toInt(),
            label = snapshot.label,
            checksum = snapshot.checksum,
            checksumValid = snapshot.checksum.length >= 8,
            projects = projects,
            tasks = tasks,
            notes = notes
        )
    }
}
