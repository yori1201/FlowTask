package com.example

import com.example.viewmodel.StreamRecovery
import com.example.viewmodel.restartOnTransientError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** C-03: klasifikasi error dan backoff pemulihan stream. */
class StreamRecoveryTest {

    @Test
    fun `IOException adalah transien`() {
        assertTrue(StreamRecovery.isTransientError(IOException("offline")))
    }

    @Test
    fun `UNAVAILABLE adalah transien`() {
        val e = FirebaseFirestoreException("network", FirebaseFirestoreException.Code.UNAVAILABLE)
        assertTrue(StreamRecovery.isTransientError(e))
    }

    @Test
    fun `PERMISSION_DENIED fatal`() {
        val e = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        assertFalse(StreamRecovery.isTransientError(e))
    }

    @Test
    fun `error terbungkus tetap terdeteksi dari cause`() {
        val wrapped = RuntimeException("wrapper", IOException("root"))
        assertTrue(StreamRecovery.isTransientError(wrapped))
    }

    @Test
    fun `CancellationException bukan transien`() {
        assertFalse(StreamRecovery.isTransientError(CancellationException("cancelled")))
    }

    @Test
    fun `backoff eksponensial dengan batas 60 detik`() {
        assertEquals(1_000L, StreamRecovery.retryDelayMillis(0))
        assertEquals(2_000L, StreamRecovery.retryDelayMillis(1))
        assertEquals(4_000L, StreamRecovery.retryDelayMillis(2))
        assertEquals(32_000L, StreamRecovery.retryDelayMillis(5))
        assertEquals(60_000L, StreamRecovery.retryDelayMillis(6))
        assertEquals(60_000L, StreamRecovery.retryDelayMillis(20))
    }

    @Test
    fun `stream bangkit setelah error transien`() = runTest {
        var attempts = 0
        val flow = flow {
            attempts += 1
            if (attempts < 3) throw IOException("sambungan terputus")
            emit("pulih")
        }.restartOnTransientError()

        assertEquals(listOf("pulih"), flow.toList())
        assertEquals(3, attempts)
    }

    @Test
    fun `error fatal diteruskan ke downstream`() = runTest {
        val flow = flow<Int> {
            throw FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }.restartOnTransientError()

        val thrown = runCatching { flow.toList() }.exceptionOrNull()
        assertTrue(thrown is FirebaseFirestoreException)
    }
}
