package com.grungert.nebo.koan

import com.grungert.nebo.data.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PracticeTest {
    private var id = 0L
    private fun at(day: Int, hour: Int) = Calendar.getInstance().apply { clear(); set(2026, Calendar.SEPTEMBER, day, hour, 0) }.timeInMillis
    private fun sit(day: Int, hour: Int, minutes: Int, sat: Int, type: String = "breath", thoughts: Int = 0) =
        Session(id = ++id, startedAt = at(day, hour), type = type, plannedSec = minutes * 60, pattern = "46", walk = false, actualSec = sat, thoughts = thoughts)

    @Test
    fun shortSessionsAreSatThroughMoreOften() {
        val s = List(5) { sit(it + 1, 8, 5, 300) } + List(5) { sit(it + 10, 8, 20, if (it < 1) 1200 else 400) }
        val c = Practice.completion(s)
        assertEquals(6, c.done); assertEquals(10, c.count)
        assertEquals("Sesije od 5 min završiš 5 od 5 puta, a od 20 min 1 od 5.", c.sentence())
    }

    @Test
    fun aStartIsNoSessionAndAnOpenOneHasNoPlan() {
        val s = listOf(sit(1, 8, 10, 10), sit(2, 8, 10, 20), sit(3, 8, 0, 900), sit(4, 8, 10, 600))
        val c = Practice.completion(s)
        assertEquals(1, c.count)
        assertNull(c.sentence())
    }

    @Test
    fun thePartOfTheDayWhenLengthsSayNothing() {
        val s = List(4) { sit(it + 1, 8, 10, 600) } + List(4) { sit(it + 10, 23, 10, 200) }
        assertEquals("Ujutru završiš 4 od 4 sesija, a noću 0 od 4.", Practice.completion(s).sentence())
    }

    @Test
    fun fewerThoughtsAsTheWeeksGoBy() {
        val s = List(9) { i -> sit(i + 1, 8, 5, 300, "thoughts", thoughts = if (i < 3) 10 else if (i < 6) 7 else 4) }
        val c = Practice.clouds(s)
        assertEquals(2f, c.early!!, 0.01f); assertEquals(0.8f, c.late!!, 0.01f)
        assertTrue(c.sentence()!!.startsWith("Misli dolaze rjeđe nego na početku: 2,0 u minuti tada, 0,8 sada."))
    }

    @Test
    fun cloudsSayNothingFromFewSessions() {
        val s = List(5) { i -> sit(i + 1, 8, 5, 300, "thoughts", thoughts = 5) } + sit(9, 8, 5, 30, "thoughts", thoughts = 3)
        val c = Practice.clouds(s)
        assertEquals(5, c.sessions.size)   // under a minute is left out
        assertNull(c.sentence())
    }
}
