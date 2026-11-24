package com.example.data.local

import android.content.Context

/**
 * Mode lokal: seluruh data tersimpan di Room pada perangkat ini tanpa
 * akun Google dan tanpa akses Firebase. Dipilih dari AuthScreen lewat
 * tombol "Lanjutkan tanpa akun".
 *
 * Flag ini juga dibaca oleh TaskFlowRepository agar seluruh operasi data
 * bercabang ke LocalTaskFlowStore (bukan Firestore), oleh ReminderReceiver
 * agar aksi "Selesai" dari notifikasi tetap berfungsi, dan oleh BootReceiver
 * agar pengingat tetap dijadwalkan ulang setelah reboot.
 */
object TaskFlowLocalMode {

    /** ID pemilik data mode lokal — pengganti Firebase Auth UID. */
    const val LOCAL_USER_ID = "local-user"

    private const val PREFS_NAME = "taskflow_mode"
    private const val KEY_ENABLED = "local_mode_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun enable(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    /**
     * Keluar dari mode lokal. Data yang sudah tersimpan TIDAK dihapus —
     * bila pengguna masuk kembali ke mode lokal, datanya masih ada.
     */
    fun disable(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()
    }
}
