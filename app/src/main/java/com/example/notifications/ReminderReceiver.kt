package com.example.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.UserPreferencesStore
import com.example.data.remote.TaskFlowRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID).orEmpty()
        val userId = intent.getStringExtra(ReminderScheduler.EXTRA_USER_ID).orEmpty()
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE).orEmpty().ifBlank { "TaskFlow" }
        val message = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_MESSAGE).orEmpty()
        val notificationId = ReminderScheduler.stableRequestCode(taskId.ifBlank { title })
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            ReminderScheduler.ACTION_TRIGGER_REMINDER -> {
                ReminderScheduler.ensureChannel(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) return
                }

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPending = PendingIntent.getActivity(
                    context,
                    notificationId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
                    this.action = ReminderScheduler.ACTION_SNOOZE_REMINDER
                    putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                    putExtra(ReminderScheduler.EXTRA_USER_ID, userId)
                    putExtra(ReminderScheduler.EXTRA_TASK_TITLE, title)
                    putExtra(ReminderScheduler.EXTRA_TASK_MESSAGE, message)
                }
                val snoozePending = PendingIntent.getBroadcast(
                    context,
                    notificationId + 1000,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val completeIntent = Intent(context, ReminderReceiver::class.java).apply {
                    this.action = ReminderScheduler.ACTION_COMPLETE_TASK
                    putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                    putExtra(ReminderScheduler.EXTRA_USER_ID, userId)
                    putExtra(ReminderScheduler.EXTRA_TASK_TITLE, title)
                }
                val completePending = PendingIntent.getBroadcast(
                    context,
                    notificationId + 2000,
                    completeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_reminder)
                    .setContentTitle(title)
                    .setContentText(message.ifBlank { "Pengingat otomatis TaskFlow" })
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openPending)
                    .addAction(0, context.getString(R.string.action_snooze_task), snoozePending)
                    .addAction(0, context.getString(R.string.action_complete_task), completePending)
                    .build()

                notificationManager.notify(notificationId, notification)
            }

            ReminderScheduler.ACTION_SNOOZE_REMINDER -> {
                notificationManager.cancel(notificationId)
                ReminderScheduler.scheduleSnooze(
                    context = context,
                    taskId = taskId,
                    userId = userId,
                    title = title,
                    message = message,
                    delayMinutes = 15
                )
            }

            ReminderScheduler.ACTION_COMPLETE_TASK -> {
                notificationManager.cancel(notificationId)
                val signedInUid = Firebase.auth.currentUser?.uid
                if (taskId.isNotBlank() && !signedInUid.isNullOrBlank() && (userId.isBlank() || userId == signedInUid)) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // C-02: tidak lagi menulis langsung ke Firestore dari
                            // receiver. Jalur data tunggal melalui repository:
                            // revision + deviceId ikut ditulis dan tugas berulang
                            // men-spawn kemunculan berikutnya secara idempoten.
                            val appContext = context.applicationContext
                            val repository = TaskFlowRepository(appContext)
                            val deviceId = UserPreferencesStore(appContext).getOrCreateDeviceId()
                            repository.completeTaskFromNotification(taskId, deviceId)
                        } catch (_: Exception) {
                            // Kegagalan sudah dicatat oleh penangan error repository.
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
