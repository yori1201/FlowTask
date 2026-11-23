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
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.model.TaskStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID).orEmpty()
        val userId = intent.getStringExtra(ReminderScheduler.EXTRA_USER_ID).orEmpty()
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE).orEmpty().ifBlank { "TaskFlow" }
        val message = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_MESSAGE).orEmpty()
        val notificationId = taskId.ifBlank { title }.hashCode()
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
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
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
                            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(dbId)
                            val path = "users/$signedInUid/tasks/$taskId"
                            try {
                                db.collection("users").document(signedInUid)
                                    .collection("tasks").document(taskId)
                                    .update(
                                        mapOf(
                                            "status" to TaskStatus.COMPLETED.name,
                                            "completedAtMillis" to System.currentTimeMillis(),
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        )
                                    ).await()
                            } catch (e: Exception) {
                                handleFirestoreError(e, OperationType.UPDATE, path)
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
