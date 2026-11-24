package com.example.data.remote

import com.example.model.RecurrenceRule
import java.time.Instant
import java.time.ZoneId

/**
 * C-06: perhitungan kemunculan berikutnya yang sadar-kalender.
 *
 * Implementasi lama memakai `Calendar.add(Calendar.MONTH, 1)` yang lenient:
 * 31 Januari + 1 bulan bergeser (rollover) ke 2/3 Maret, lalu terus jatuh di
 * tanggal tsb — jadwal bergeser permanen. `java.time` memaksa clamp ke akhir
 * bulan (31 Jan -> 28/29 Feb), perilaku yang sama dengan aplikasi kalender
 * pada umumnya. `plusYears` juga melakukan clamp 29 Feb -> 28 Feb pada tahun
 * non-kabisat.
 */
object RecurrenceSchedule {

    fun nextOccurrenceMillis(baseMillis: Long, rule: RecurrenceRule): Long {
        val base = Instant.ofEpochMilli(baseMillis).atZone(ZoneId.systemDefault())
        val next = when (rule) {
            RecurrenceRule.DAILY -> base.plusDays(1)
            RecurrenceRule.WEEKLY -> base.plusWeeks(1)
            RecurrenceRule.MONTHLY -> base.plusMonths(1)
            RecurrenceRule.YEARLY -> base.plusYears(1)
            RecurrenceRule.NONE -> base
        }
        return next.toInstant().toEpochMilli()
    }
}
