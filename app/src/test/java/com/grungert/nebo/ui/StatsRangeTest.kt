package com.grungert.nebo.ui

import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.Session
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class StatsRangeTest {
    private fun at(month: Int, day: Int, hour: Int = 12, year: Int = 2026) = Calendar.getInstance().apply { clear(); set(year, month, day, hour, 0) }.timeInMillis
    private val friday = at(Calendar.OCTOBER, 23)

    @Test
    fun theWeekRunsMondayToMonday() {
        val r = StatsRange.of(RangeKind.WEEK, friday)
        assertEquals(at(Calendar.OCTOBER, 19, 0), r.from)
        assertEquals(at(Calendar.OCTOBER, 26, 0), r.to)
        assertEquals(listOf("P", "U", "S", "Č", "P", "S", "N"), r.buckets().map { it.label })
        assertEquals(at(Calendar.OCTOBER, 12, 0), r.previous().from)
    }

    @Test
    fun aMonthEndsWithToday() {
        val r = StatsRange.of(RangeKind.MONTH, friday)
        assertEquals(30, r.days)
        assertEquals(at(Calendar.OCTOBER, 24, 0), r.to)
        assertEquals(30, r.buckets().size)            // days for the wave
        assertEquals(4, r.buckets(coarse = true).size) // weeks for the moods, the last two days joined to the week before
    }

    @Test
    fun theYearIsTwelveMonths() {
        val r = StatsRange.of(RangeKind.YEAR, friday)
        assertEquals(at(Calendar.NOVEMBER, 1, 0, year = 2025), r.from)
        val b = r.buckets()
        assertEquals(12, b.size)
        assertEquals("nov", b.first().label)
        assertEquals("okt", b.last().label)
    }

    @Test
    fun aChosenPeriodIsWholeDays() {
        val r = StatsRange.custom(at(Calendar.SEPTEMBER, 17, 15), at(Calendar.OCTOBER, 23, 9))
        assertEquals(at(Calendar.SEPTEMBER, 17, 0), r.from)
        assertEquals(at(Calendar.OCTOBER, 24, 0), r.to)
        assertEquals("17.9. – 23.10.", r.title)
        assertEquals(r.days, r.previous().days)
    }

    @Test
    fun theFiguresCountOnlyTheSpan() {
        val sessions = listOf(
            Session(id = 1, startedAt = at(Calendar.OCTOBER, 20, 8), type = "breath", plannedSec = 600, pattern = "46", walk = false, actualSec = 600),
            Session(id = 2, startedAt = at(Calendar.OCTOBER, 21, 8), type = "breath", plannedSec = 600, pattern = "46", walk = false, actualSec = 10),
            Session(id = 3, startedAt = at(Calendar.OCTOBER, 13, 8), type = "breath", plannedSec = 600, pattern = "46", walk = false, actualSec = 300),
        )
        val entries = listOf(Entry(id = 9, createdAt = at(Calendar.OCTOBER, 22, 20), kind = "note", text = "mirno veče", moods = listOf("mirno")))
        val d = computeStats(sessions, entries, emptyList(), StatsRange.of(RangeKind.WEEK, friday), friday)
        assertEquals(10, d.minutes)
        assertEquals(1, d.sessions)          // ten seconds was only a start
        assertEquals(1, d.notes)
        assertEquals(5, d.prevMinutes)
        assertEquals(1, d.practicedDays)
        assertEquals(5, d.daysSoFar)
        assertEquals(listOf(0, 10, 0, 0, 0, -1, -1), d.wave)
    }
}
