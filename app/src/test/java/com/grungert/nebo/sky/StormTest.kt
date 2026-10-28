package com.grungert.nebo.sky

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StormTest {
    @After fun reset() { Storm.thunder = true }

    @Test
    fun theRainWandersFromADrizzleToADownpour() {
        val i = (0 until 3600).map { Storm.intensity(it * 2.0) }
        assertTrue(i.all { it in 0.12f..1f })
        assertTrue("some drizzle", i.count { it < 0.3f } > 100)
        assertTrue("some downpour", i.count { it > 0.7f } > 50)
        // never a jump: it changes over seconds, not from one moment to the next
        assertTrue(i.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) < 0.12f })
    }

    @Test
    fun lightningOnlyInHeavyRainAndThunderAfterIt() {
        val strikes = (0L until 2000L).mapNotNull { Storm.strikeIn(it) }
        assertTrue("it does strike now and then", strikes.size in 20..800)
        for (s in strikes) {
            assertTrue(Storm.intensity(s.at) > 0.55f)
            assertTrue(s.heard > s.at + 1.0)
            assertEquals(s.at, Storm.lastStrike(s.at + 0.01)!!.at, 1e-9)
            assertTrue(Storm.flash(s.at + 0.01) > 0.2f)
            assertEquals(0f, Storm.flash(s.at + 2.0), 0f)
        }
    }

    @Test
    fun noThunderWhenItIsOff() {
        Storm.thunder = false
        assertTrue((0L until 2000L).all { Storm.strikeIn(it) == null })
        assertNull(Storm.lastStrike(12345.0))
    }

    @Test
    fun gustsComeAndGoWithQuietBetween() {
        val g = (0 until 6000).map { Storm.gust(it * 0.5) }
        assertTrue(g.all { it in 0f..1f })
        val windy = g.count { it > 0.3f } / g.size.toFloat()
        assertTrue("windy now and then, not always: $windy", windy in 0.08f..0.5f)
        // it rises over a second or two, never in a jump
        val fine = (0 until 30000).map { Storm.gust(it * 0.1) }
        assertTrue(fine.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) < 0.12f })
    }
}
