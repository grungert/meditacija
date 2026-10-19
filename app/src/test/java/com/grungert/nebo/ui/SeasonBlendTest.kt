package com.grungert.nebo.ui

import com.grungert.nebo.sky.Season
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SeasonBlendTest {
    private fun on(month: Int, day: Int) = seasonBlend(Calendar.getInstance().apply { clear(); set(2026, month, day, 12, 0) })

    @Test
    fun theTreeTurnsAroundTheChangeOfSeason() {
        // deep in a season there is nothing to blend
        val mid = on(Calendar.OCTOBER, 19)
        assertEquals(Season.AUTUMN, mid.first); assertEquals(0f, mid.third, 0.001f)
        // a week before winter, some winter shows already
        val before = on(Calendar.NOVEMBER, 24)
        assertEquals(Season.AUTUMN, before.first); assertEquals(Season.WINTER, before.second)
        assertEquals(0.5f * (1f - 7f / 21f), before.third, 0.02f)
        // on the day itself it is half and half, now counted as winter with autumn fading
        val day = on(Calendar.DECEMBER, 1)
        assertEquals(Season.WINTER, day.first); assertEquals(Season.AUTUMN, day.second); assertEquals(0.5f, day.third, 0.03f)
        // and in late February the spring begins to show
        val feb = on(Calendar.FEBRUARY, 20)
        assertEquals(Season.WINTER, feb.first); assertEquals(Season.SPRING, feb.second)
    }
}
