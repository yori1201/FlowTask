package com.example.ui.i18n

import com.example.model.AppLanguage
import com.example.model.RecurrenceRule
import com.example.model.SyncState
import com.example.model.TaskPriority
import com.example.model.TaskStatus

data class TaskFlowCopy(
    val navHome: String,
    val navTasks: String,
    val navNotes: String,
    val navCalendar: String,
    val navProjects: String,
    val navMore: String,
    val greetingMorning: String,
    val greetingAfternoon: String,
    val greetingEvening: String,
    val todayFocus: String,
    val overdueTitle: String,
    val todayTitle: String,
    val highPriorityTitle: String,
    val upcomingTitle: String,
    val recentNotesTitle: String,
    val quickNewTask: String,
    val quickNewNote: String,
    val searchTasksHint: String,
    val searchNotesHint: String,
    val emptyTasksTitle: String,
    val emptyTasksBody: String,
    val emptyNotesTitle: String,
    val emptyNotesBody: String,
    val emptyProjectsTitle: String,
    val emptyProjectsBody: String,
    val moreReminders: String,
    val moreInsights: String,
    val moreSyncBackup: String,
    val moreSettings: String
)

fun copyFor(language: AppLanguage): TaskFlowCopy = when (language) {
    AppLanguage.ID -> TaskFlowCopy(
        navHome = "Beranda",
        navTasks = "Tugas",
        navNotes = "Catatan",
        navCalendar = "Kalender",
        navProjects = "Proyek",
        navMore = "Lainnya",
        greetingMorning = "Selamat pagi",
        greetingAfternoon = "Selamat siang",
        greetingEvening = "Selamat malam",
        todayFocus = "Fokus Hari Ini",
        overdueTitle = "Terlambat",
        todayTitle = "Jadwal Hari Ini",
        highPriorityTitle = "Prioritas Tinggi",
        upcomingTitle = "Mendatang",
        recentNotesTitle = "Catatan Terbaru",
        quickNewTask = "Tugas Baru",
        quickNewNote = "Catatan Baru",
        searchTasksHint = "Cari tugas, kategori, atau tag...",
        searchNotesHint = "Cari judul atau isi catatan...",
        emptyTasksTitle = "Belum ada tugas",
        emptyTasksBody = "Tambahkan tugas harian pertamamu beserta deadline & pengingat otomatis.",
        emptyNotesTitle = "Belum ada catatan",
        emptyNotesBody = "Tulis ide, ringkasan, atau checklist dan hubungkan langsung ke tugas.",
        emptyProjectsTitle = "Belum ada proyek",
        emptyProjectsBody = "Kelompokkan tugas dan catatan berdasarkan proyek pribadi.",
        moreReminders = "Pusat Pengingat",
        moreInsights = "Ringkasan Produktivitas",
        moreSyncBackup = "Sinkronisasi & Backup",
        moreSettings = "Pengaturan & Akun"
    )

    AppLanguage.EN -> TaskFlowCopy(
        navHome = "Home",
        navTasks = "Tasks",
        navNotes = "Notes",
        navCalendar = "Calendar",
        navProjects = "Projects",
        navMore = "More",
        greetingMorning = "Good morning",
        greetingAfternoon = "Good afternoon",
        greetingEvening = "Good evening",
        todayFocus = "Today's Focus",
        overdueTitle = "Overdue",
        todayTitle = "Due Today",
        highPriorityTitle = "High Priority",
        upcomingTitle = "Upcoming",
        recentNotesTitle = "Recent Notes",
        quickNewTask = "New Task",
        quickNewNote = "New Note",
        searchTasksHint = "Search tasks, categories, or tags...",
        searchNotesHint = "Search note titles or content...",
        emptyTasksTitle = "No tasks yet",
        emptyTasksBody = "Create your first daily task with deadline and automatic reminder.",
        emptyNotesTitle = "No notes yet",
        emptyNotesBody = "Capture ideas or checklists and link them directly to tasks.",
        emptyProjectsTitle = "No projects yet",
        emptyProjectsBody = "Organize tasks and notes under personal projects.",
        moreReminders = "Reminder Center",
        moreInsights = "Productivity Insights",
        moreSyncBackup = "Sync & Backup",
        moreSettings = "Settings & Account"
    )
}

fun TaskPriority.label(lang: AppLanguage): String = when (this) {
    TaskPriority.LOW -> if (lang == AppLanguage.ID) "Rendah" else "Low"
    TaskPriority.MEDIUM -> if (lang == AppLanguage.ID) "Sedang" else "Medium"
    TaskPriority.HIGH -> if (lang == AppLanguage.ID) "Tinggi" else "High"
    TaskPriority.URGENT -> if (lang == AppLanguage.ID) "Mendesak" else "Urgent"
}

fun TaskStatus.label(lang: AppLanguage): String = when (this) {
    TaskStatus.TODO -> "To Do"
    TaskStatus.IN_PROGRESS -> if (lang == AppLanguage.ID) "Dikerjakan" else "In Progress"
    TaskStatus.COMPLETED -> if (lang == AppLanguage.ID) "Selesai" else "Completed"
}

fun RecurrenceRule.label(lang: AppLanguage): String = when (this) {
    RecurrenceRule.NONE -> if (lang == AppLanguage.ID) "Sekali" else "Once"
    RecurrenceRule.DAILY -> if (lang == AppLanguage.ID) "Harian" else "Daily"
    RecurrenceRule.WEEKLY -> if (lang == AppLanguage.ID) "Mingguan" else "Weekly"
    RecurrenceRule.MONTHLY -> if (lang == AppLanguage.ID) "Bulanan" else "Monthly"
    RecurrenceRule.YEARLY -> if (lang == AppLanguage.ID) "Tahunan" else "Yearly"
}

fun SyncState.label(lang: AppLanguage): String = when (this) {
    SyncState.LOCAL_ONLY -> if (lang == AppLanguage.ID) "Offline Lokal" else "Local Only"
    SyncState.PENDING_SYNC -> if (lang == AppLanguage.ID) "Antre Sync" else "Pending Sync"
    SyncState.SYNCING -> if (lang == AppLanguage.ID) "Menyinkronkan..." else "Syncing..."
    SyncState.SYNCED -> if (lang == AppLanguage.ID) "Tersinkronisasi" else "Synced"
    SyncState.SYNC_FAILED -> if (lang == AppLanguage.ID) "Sync Tertunda" else "Sync Failed"
    SyncState.CONFLICT_DETECTED -> if (lang == AppLanguage.ID) "Konflik Revisi" else "Conflict Detected"
}
