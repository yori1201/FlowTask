package com.example.viewmodel

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.IOException

/**
 * C-03: kebijakan pemulihan stream.
 *
 * Pola lama `.catch { emit(UiState.Error) }` bersifat terminal: satu exception
 * (misalnya PERMISSION_DENIED saat balapan sign-out) menghentikan StateFlow
 * selamanya dan UI tertahan di Error sampai ViewModel dibuat ulang. Helper di
 * file ini memisahkan error transien (jaringan) yang layak dicoba ulang dengan
 * backoff eksponensial, dari error fatal yang memang harus menghentikan stream.
 */
object StreamRecovery {

    private val TRANSIENT_FIRESTORE_CODES = setOf(
        FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.CANCELLED,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.INTERNAL,
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.UNKNOWN
    )

    /**
     * Error transien = gangguan jaringan/infrastruktur yang bisa sembuh
     * sendiri. Error fatal (PERMISSION_DENIED, UNAUTHENTICATED, argument
     * tidak valid) tidak akan membaik dengan mencoba ulang.
     */
    fun isTransientError(error: Throwable): Boolean {
        if (error is CancellationException) return false
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is FirebaseFirestoreException) return cause.code in TRANSIENT_FIRESTORE_CODES
            if (cause is IOException) return true
            cause = cause.cause
        }
        return false
    }

    /** Backoff eksponensial 1s, 2s, 4s, ... maksimum 60 detik. */
    fun retryDelayMillis(attempt: Long): Long {
        val bounded = attempt.coerceIn(0L, 6L)
        return minOf(60_000L, 1_000L shl bounded.toInt())
    }
}

/**
 * Menyalin ulang (resubscribe) upstream setiap kali error transien terjadi.
 * Error fatal diteruskan ke downstream (mis. untuk diubah menjadi UiState.Error).
 * Flow Firestore selesai normal hanya jika memang ditutup — loop berhenti.
 */
fun <T> Flow<T>.restartOnTransientError(
    onRetry: (suspend (attempt: Long, error: Throwable) -> Unit)? = null
): Flow<T> = flow {
    var attempt = 0L
    while (currentCoroutineContext().isActive) {
        try {
            emitAll(this@restartOnTransientError)
            break
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!StreamRecovery.isTransientError(e)) throw e
            attempt += 1
            onRetry?.invoke(attempt, e)
            delay(StreamRecovery.retryDelayMillis(attempt))
        }
    }
}
