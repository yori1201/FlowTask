package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.security.MessageDigest

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

/**
 * S-02: penangan error TANPA PII. Versi lama menuliskan userId, email, status
 * verifikasi email, dan email seluruh provider ke logcat — mudah dibaca dari
 * log diagnostik perangkat. Kini hanya kode error, operasi, path yang telah
 * di-redact, status sesi, dan referensi dukungan (hash 8 karakter dari uid)
 * untuk korelasi tiket bantuan tanpa membocorkan identitas.
 */
fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val authState = if (currentUser != null) "signed-in" else "signed-out"
    val supportRef = currentUser?.uid?.let { uid ->
        runCatching {
            MessageDigest.getInstance("SHA-256")
                .digest(uid.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(8)
        }.getOrNull()
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("code", (rootError as? FirebaseFirestoreException)?.code?.name ?: "UNKNOWN")
        put("operationType", operationType.value)
        put("path", path?.replace(Regex("users/[^/]+"), "users/<uid>"))
        put("auth", authState)
        put("supportRef", supportRef)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
