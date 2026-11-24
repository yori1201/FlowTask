package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.R
import com.example.data.local.TaskFlowLocalMode
import com.example.data.local.UserPreferencesStore
import com.example.data.remote.OperationType
import com.example.data.remote.TaskFlowRepository
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

/**
 * Menjadwalkan ulang seluruh pengingat setelah reboot atau perubahan zona
 * waktu. Mendukung dua mode:
 *  - Akun Google aktif -> baca dari cache Firestore (jalur asli).
 *  - Mode lokal aktif  -> baca dari penyimpanan Room melalui repository.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_TIMEZONE_CHANGED) return

        val appContext = context.applicationContext
        val signedInUid = Firebase.auth.currentUser?.uid
        val localActive = TaskFlowLocalMode.isEnabled(appContext)
        if (signedInUid == null && !localActive) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = UserPreferencesStore(appContext).settingsFlow.first()
                val tasks: List<TaskItem> = if (signedInUid != null) {
                    // Jalur cloud: cache Firestore tetap tersedia offline.
                    val dbId = appContext.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(dbId)
                    val path = "users/$signedInUid/tasks"
                    try {
                        db.collection("users").document(signedInUid)
                            .collection("tasks")
                            .get(Source.CACHE)
                            .await()
                            .toObjects(
                                TaskItem::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.LIST, path)
                        emptyList()
                    }
                } else {
                    // Jalur mode lokal: Room melalui repository.
                    TaskFlowRepository(appContext)
                        .observeTasks(TaskFlowLocalMode.LOCAL_USER_ID)
                        .first()
                }
                ReminderScheduler.syncReminders(appContext, tasks, settings)
            } catch (_: Exception) {
                // Penjadwalan ulang bersifat best-effort; aplikasi akan
                // menjadwalkan ulang saat dibuka berikutnya.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
