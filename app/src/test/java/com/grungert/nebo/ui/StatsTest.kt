package com.grungert.nebo.ui

import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.FourPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {
    @Test
    fun percentagesAddUpToAHundred() {
        assertEquals(100, percents(listOf(1, 1, 1)).sum())
        assertEquals(listOf(34, 33, 33), percents(listOf(1, 1, 1)))
        assertEquals(listOf(0, 0, 0), percents(listOf(0, 0, 0)))
    }

    private fun e(id: Long, body: List<String>, urges: List<String>) =
        Entry(id = id, createdAt = id, kind = "note", moods = listOf("tužno"), points = FourPoints(body = body, urges = urges))

    @Test
    fun aChainCountsOnlyNotesThatHoldAllOfIt() {
        // seven sad notes; the stomach in two, the phone in two other ones, never together
        val notes = listOf(
            e(1, listOf("stisnut stomak"), emptyList()), e(2, listOf("stisnut stomak"), emptyList()),
            e(3, emptyList(), listOf("da uzmem telefon")), e(4, emptyList(), listOf("da uzmem telefon")),
            e(5, listOf("plitak dah"), emptyList()), e(6, emptyList(), listOf("da pobjegnem")), e(7, listOf("toplina"), emptyList()),
        )
        val link = pointLinks(notes).single()
        // not "tužno → stomak → telefon 7×": the two never came together, so one stands alone, counted where it was
        assertEquals("stisnut stomak", link.body); assertNull(link.urge); assertEquals(2, link.times)
    }
}
