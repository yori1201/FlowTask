package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.R
import com.example.data.local.UserSettings
import com.example.model.TaskItem
import com.example.model.TaskStatus
import java.util.Calendar

object ReminderScheduler {
    const val CHANNEL_ID = "taskflow_reminders_channel"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_MESSAGE = "extra_task_message"
    const val EXTRA_USER_ID = "extra_user_id"
    const val ACTION_TRIGGER_REMINDER = "com.example.action.TRIGGER_REMINDER"
    const val ACTION_SNOOZE_REMINDER = "com.example.action.SNOOZE_REMINDER"
    const val ACTION_COMPLETE_TASK = "com.example.action.COMPLETE_TASK"

    /**
     * C-09: request code stabil per tugas. ID TaskFlow berakhiran heksadesimal,
     * jadi 8 karakter terakhir diparse menjadi int 32-bit penuh — lebih merata
     * daripada String.hashCode dan bebas tabrakan antar ID yang mirip.
     */
    fun stableRequestCode(taskId: String): Int {
        val hex = taskId.takeLast(8).lowercase()
        return try {
            Integer.parseUnsignedInt(hex, 16)
        } catch (_: IllegalArgumentException) {
            kotlin.math.abs(taskId.hashCode())
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun adjustForQuietHours(triggerAtMillis: Long, settings: UserSettings): Long {
        if (!settings.quietHoursEnabled) return triggerAtMillis
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val start = settings.quietStartHour
        val end = settings.quietEndHour
        val isQuiet = if (start > end) {
            hour >= start || hour < end
        } else {
            hour in start until end
        }
        if (!isQuiet) return triggerAtMillis

        if (start > end && hour >= start) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, end)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun scheduleTaskReminder(
        context: Context,
        task: TaskItem,
        settings: UserSettings = UserSettings()
    ) {
        val rawTrigger = task.reminderAtMillis ?: return
        if (task.deleted || task.archived || task.taskStatus == TaskStatus.COMPLETED) {
            cancelTaskReminder(context, task.id)
            return
        }
        val triggerAt = adjustForQuietHours(rawTrigger, settings)
        if (triggerAt <= System.currentTimeMillis() - 60_000L) return

        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildReminderPendingIntent(
            context = context,
            taskId = task.id,
            userId = task.userId,
            title = task.title,
            message = task.description.ifBlank { "Waktunya menyelesaikan tugas: ${task.title}" }
        )
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Log.w("ReminderScheduler", "Fallback alarm scheduling: ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun scheduleSnooze(
        context: Context,
        taskId: String,
        userId: String,
        title: String,
        message: String,
        delayMinutes: Int = 15
    ) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val pendingIntent = buildReminderPendingIntent(context, taskId, userId, title, message)
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelTaskReminder(context: Context, taskId: String) {
        if (taskId.isBlank()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
        }
        val pending = PendingIntent.getBroadcast(
            context,
            stableRequestCode(taskId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    fun syncReminders(context: Context, tasks: List<TaskItem>, settings: UserSettings) {
        ensureChannel(context)
        val now = System.currentTimeMillis()
        tasks.forEach { task ->
            if (task.deleted || task.archived || task.taskStatus == TaskStatus.COMPLETED || task.reminderAtMillis == null) {
                cancelTaskReminder(context, task.id)
            } else if (task.reminderAtMillis > now - 60_000L) {
                scheduleTaskReminder(context, task, settings)
            }
        }
    }

    private fun buildReminderPendingIntent(
        context: Context,
        taskId: String,
        userId: String,
        title: String,
        message: String
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_MESSAGE, message)
        }
        return PendingIntent.getBroadcast(
            context,
            stableRequestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
