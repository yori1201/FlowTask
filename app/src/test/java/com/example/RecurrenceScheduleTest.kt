package com.example

import com.example.data.remote.RecurrenceSchedule
import com.example.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * C-06: pengulangan bulanan/tahunan harus di-clamp ke akhir bulan, bukan
 * bergeser (rollover) ke bulan berikutnya seperti perilaku Calendar.add lama.
 */
class RecurrenceScheduleTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisOf(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun dateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    @Test
    fun `daily menambah satu hari`() {
        val next = RecurrenceSchedule.nextOccurrenceMillis(millisOf(LocalDate.of(2026, 11, 23)), RecurrenceRule.DAILY)
        assertEquals(LocalDate.of(2026, 11, 24), dateOf(next))
    }

    @Test
    fun `weekly menambah tujuh hari`() {
        val next = RecurrenceSchedule.nextOccurrenceMillis(millisOf(LocalDate.of(2026, 11, 23)), RecurrenceRule.WEEKLY)
        assertEquals(LocalDate.of(2026, 11, 30), dateOf(next))
    }

    @Test
    fun `monthly mempertahankan tanggal`() {
        val next = RecurrenceSchedule.nextOccurrenceMillis(millisOf(LocalDate.of(2026, 1, 15)), RecurrenceRule.MONTHLY)
        assertEquals(LocalDate.of(2026, 2, 15), dateOf(next))
    }

    @Test
    fun `monthly 31 Januari di-clamp ke 28 Februari, bukan rollover ke Maret`() {
        val next = RecurrenceSchedule.nextOccurrenceMillis(millisOf(LocalDate.of(2026, 1, 31)), RecurrenceRule.MONTHLY)
        assertEquals(LocalDate.of(2026, 2, 28), dateOf(next))
    }

    @Test
    fun `monthly tahun kabisat 31 Januari menuju 29 Februari`() {
        val next = RecurrenceSchedule.nextOccurrenceMillis(millisOf(LocalDate.of(2028, 1, 31)), RecurrenceRule.MONTHLY)
        assertEquals(LocalDate.of(2028, 2, 29), dateOf(next))
    }

    @Test
    fun `monthly melewati batas tahun`() {
        val next = RecurrenceSchedule.nextOccurrenceMillis(millisOf(LocalDate.of(2026, 12, 15)), RecurrenceRule.MONTHLY)
        assertEquals(LocalDate.of(2027, 1, 15), dateOf(next))
    }

    @Test
    fun `yearly 29 Februari di-clamp ke 28 Februari tahun non-kabisat`() {
        val next = RecurrenceSchedule.nextOccurrenceMillis(millisOf(LocalDate.of(2028, 2, 29)), RecurrenceRule.YEARLY)
        assertEquals(LocalDate.of(2029, 2, 28), dateOf(next))
    }

    @Test
    fun `none mengembalikan instan yang sama`() {
        val base = millisOf(LocalDate.of(2026, 5, 5))
        assertEquals(base, RecurrenceSchedule.nextOccurrenceMillis(base, RecurrenceRule.NONE))
    }
}
