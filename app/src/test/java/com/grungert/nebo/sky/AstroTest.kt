package com.grungert.nebo.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AstroTest {
    private val zone = TimeZone.getTimeZone("Europe/Podgorica")
    private fun day(m: Int, d: Int, h: Int = 12) = Calendar.getInstance(zone).apply { clear(); set(2026, m, d, h, 0) }
    private fun hm(h: Int, m: Int) = h + m / 60f

    @Test
    fun sunriseAndSunsetInPodgorica() {
        // published times for Podgorica (weather2travel, given in UTC+1), within five minutes; on 16 October the
        // clocks are on summer time, an hour ahead of those
        val (r1, s1) = Astro.sunTimes(day(Calendar.OCTOBER, 16))
        assertEquals(hm(6, 57), r1, 0.09f); assertEquals(hm(18, 0), s1, 0.09f)
        val (r2, s2) = Astro.sunTimes(day(Calendar.OCTOBER, 31))
        assertEquals(hm(6, 15), r2, 0.09f); assertEquals(hm(16, 38), s2, 0.09f)
        val (r3, s3) = Astro.sunTimes(day(Calendar.OCTOBER, 1))
        assertEquals(hm(6, 40), r3, 0.09f); assertEquals(hm(18, 25), s3, 0.09f)
    }

    @Test
    fun theSceneFollowsTheRealDay() {
        val (rise, set) = 7.1f to 18.2f
        assertEquals(5.8f, Astro.sceneHour(rise, rise, set), 0.001f)
        assertEquals(19.6f, Astro.sceneHour(set, rise, set), 0.001f)
        // an hour and a half after a December sunset it is already night, not golden hour
        assertTrue(Astro.sceneHour(18f, 7.07f, 16.47f) > 21f)
        // midnight is deep night, and the hours keep going forward all night
        val m = Astro.sceneHour(0f, rise, set)
        assertTrue(m in 0f..4.8f)
        assertTrue(Astro.sceneHour(3f, rise, set) > m)
    }

    @Test
    fun theMoonsMonth() {
        // new moon 10 October 2026, full moon 26 October 2026
        fun at(m: Int, d: Int, h: Int) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, m, d, h, 0) }.timeInMillis
        val new = Astro.moonPhase(at(Calendar.OCTOBER, 10, 16))
        assertTrue("$new", new < 0.03f || new > 0.97f)
        assertEquals(0.5f, Astro.moonPhase(at(Calendar.OCTOBER, 26, 4)), 0.03f)
        assertEquals(1f, Astro.moonLit(0.5f), 0.001f)
        assertEquals(0f, Astro.moonLit(0f), 0.001f)
    }
}
