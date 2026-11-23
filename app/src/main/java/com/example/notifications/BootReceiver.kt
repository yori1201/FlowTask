package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.R
import com.example.data.local.UserPreferencesStore
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.model.TaskItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_TIMEZONE_CHANGED) return
        val uid = Firebase.auth.currentUser?.uid ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val dbId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                val settings = UserPreferencesStore(appContext).settingsFlow.first()
                val path = "users/$uid/tasks"
                try {
                    val snapshot = db.collection("users").document(uid)
                        .collection("tasks")
                        .get(Source.CACHE)
                        .await()
                    val tasks = snapshot.toObjects(
                        TaskItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    ReminderScheduler.syncReminders(appContext, tasks, settings)
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.LIST, path)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
