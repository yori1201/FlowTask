package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    COMPLETED;

    companion object {
        fun fromRaw(raw: String?): TaskStatus =
            entries.firstOrNull { it.name == raw } ?: TODO
    }
}

enum class TaskPriority(val weight: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    URGENT(4);

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class RecurrenceRule {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    companion object {
        fun fromRaw(raw: String?): RecurrenceRule =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class ReminderType {
    TASK_REMINDER,
    DEADLINE,
    OVERDUE,
    DAILY_SUMMARY,
    SYNC_ALERT;

    companion object {
        fun fromRaw(raw: String?): ReminderType =
            entries.firstOrNull { it.name == raw } ?: TASK_REMINDER
    }
}

enum class SyncState {
    LOCAL_ONLY,
    PENDING_SYNC,
    SYNCING,
    SYNCED,
    SYNC_FAILED,
    CONFLICT_DETECTED
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { ID, EN }

@Keep
@IgnoreExtraProperties
data class SubtaskItem(
    val id: String = "",
    val title: String = "",
    val done: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "title" to title,
        "done" to done
    )

    companion object {
        fun fromMap(map: Map<*, *>): SubtaskItem = SubtaskItem(
            id = (map["id"] as? String).orEmpty(),
            title = (map["title"] as? String).orEmpty(),
            done = (map["done"] as? Boolean) ?: false
        )
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val lastBackupAtMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProjectItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String? = null,
    val colorHex: String = "#0F5257",
    val iconName: String = "folder",
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val revision: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = TaskStatus.TODO.name,
    val priority: String = TaskPriority.MEDIUM.name,
    val category: String = "Personal",
    val projectId: String? = null,
    val startAtMillis: Long? = null,
    val dueAtMillis: Long? = null,
    val reminderAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val recurrence: String = RecurrenceRule.NONE.name,
    val tags: List<String> = emptyList(),
    val subtasks: List<SubtaskItem> = emptyList(),
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val revision: Long = 1L,
    val deviceId: String = "android",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val taskStatus: TaskStatus get() = TaskStatus.fromRaw(status)
    val taskPriority: TaskPriority get() = TaskPriority.fromRaw(priority)
    val recurrenceRule: RecurrenceRule get() = RecurrenceRule.fromRaw(recurrence)
    val completedSubtasksCount: Int get() = subtasks.count { it.done }
}

@Keep
@IgnoreExtraProperties
data class NoteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "Personal",
    val projectId: String? = null,
    val tags: List<String> = emptyList(),
    val linkedTaskIds: List<String> = emptyList(),
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val wordCount: Long = 0L,
    val revision: Long = 1L,
    val deviceId: String = "android",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReminderEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val taskId: String? = null,
    val title: String = "",
    val message: String = "",
    val reminderType: String = ReminderType.TASK_REMINDER.name,
    val scheduledAtMillis: Long = 0L,
    val snoozedUntilMillis: Long? = null,
    val dismissed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: ReminderType get() = ReminderType.fromRaw(reminderType)
}

@Keep
@IgnoreExtraProperties
data class BackupSnapshot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val formatVersion: Long = 1L,
    val label: String = "",
    val taskCount: Long = 0L,
    val noteCount: Long = 0L,
    val projectCount: Long = 0L,
    val checksum: String = "",
    val snapshotData: Map<String, Any> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class BackupPreview(
    val formatVersion: Int,
    val label: String,
    val checksum: String,
    val checksumValid: Boolean,
    val projects: List<ProjectItem>,
    val tasks: List<TaskItem>,
    val notes: List<NoteItem>
)

data class SyncMetadata(
    val state: SyncState = SyncState.SYNCED,
    val pendingWritesCount: Int = 0,
    val isFromCache: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val lastErrorMessage: String? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

val DefaultCategories = listOf(
    "Personal",
    "Work",
    "Learning",
    "Finance",
    "Health",
    "Side Projects"
)

val ProjectAccentColors = listOf(
    "#0F5257",
    "#D96B27",
    "#237A5B",
    "#5B4B9A",
    "#B83B5E",
    "#2B6CB0"
)
