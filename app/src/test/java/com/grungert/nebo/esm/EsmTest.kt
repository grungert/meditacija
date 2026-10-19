package com.grungert.nebo.esm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EsmTest {
    private lateinit var zone: TimeZone

    @Before fun montenegro() { zone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Europe/Podgorica")) }
    @After fun back() { TimeZone.setDefault(zone) }

    private fun on(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m, d, h, 0) }

    @Test
    fun theNightTheClockGoesBackStillEndsAtMidnight() {
        // 25 October 2026 has 25 hours; the next midnight is 25 hours after this one, not 24
        val day = on(2026, Calendar.OCTOBER, 25, 9)
        val today = Esm.midnight(0, day).timeInMillis
        val tomorrow = Esm.midnight(1, day)
        assertEquals(25 * 3_600_000L, tomorrow.timeInMillis - today)
        assertEquals(26, tomorrow.get(Calendar.DAY_OF_MONTH)); assertEquals(0, tomorrow.get(Calendar.HOUR_OF_DAY))
        // and the spring day has 23
        val spring = on(2027, Calendar.MARCH, 28, 9)
        assertEquals(23 * 3_600_000L, Esm.midnight(1, spring).timeInMillis - Esm.midnight(0, spring).timeInMillis)
    }

    @Test
    fun aMondayLooksBackOnTheWeekThatJustEnded() {
        val monday = Esm.reviewWeekStart(on(2026, Calendar.OCTOBER, 19))
        assertEquals(12, monday.get(Calendar.DAY_OF_MONTH)); assertEquals(Calendar.MONDAY, monday.get(Calendar.DAY_OF_WEEK))
        val sunday = Esm.reviewWeekStart(on(2026, Calendar.OCTOBER, 18, 19))
        assertEquals(12, sunday.get(Calendar.DAY_OF_MONTH))
        val saturday = Esm.reviewWeekStart(on(2026, Calendar.OCTOBER, 24))
        assertEquals(19, saturday.get(Calendar.DAY_OF_MONTH))
        // a review on Monday marks the week it looked at, which is the one Sunday's reminder asks about
        assertEquals(Esm.weekKey(on(2026, Calendar.OCTOBER, 18, 19)), Esm.weekKey(monday))
    }

    @Test
    fun theLastWeekOfTheYearKeepsOneKey() {
        // 28 December 2026 – 3 January 2027 is ISO week 53 of 2026 throughout
        val dec = Esm.weekKey(on(2026, Calendar.DECEMBER, 31))
        val jan = Esm.weekKey(on(2027, Calendar.JANUARY, 3))
        assertEquals(202653, dec); assertEquals(dec, jan)
        assertTrue(Esm.weekKey(on(2027, Calendar.JANUARY, 4)) == 202701)
    }

    @Test
    fun randomMomentsStayInsideTheWindowAndApart() {
        val from = 1_000_000L; val to = from + 11 * 3_600_000L
        repeat(50) {
            val ts = Esm.randomTimes(from, to, 4)
            assertEquals(4, ts.size)
            assertTrue(ts.all { it in from until to })
            assertTrue(ts.zipWithNext().all { (a, b) -> b - a >= 45 * 60_000L })
        }
    }
}
