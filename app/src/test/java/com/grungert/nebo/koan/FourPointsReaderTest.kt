package com.grungert.nebo.koan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FourPointsReaderTest {
    @Test
    fun readsAllFourFromOneNote() {
        val r = FourPointsReader.read("Stalno mislim šta ako ne završim projekat na vrijeme. Osjećam nemir i strah, " +
            "stomak mi je stisnut i ramena napeta, a vuče me da uzmem telefon i skrolujem.")
        assertTrue(r.thoughts, r.thoughts.contains("šta ako ne završim"))
        assertTrue(r.moods.toString(), r.moods.containsAll(listOf("nemirno", "zabrinuto")))
        assertTrue(r.body.toString(), r.body.containsAll(listOf("stisnut stomak", "napeta ramena")))
        assertTrue(r.urges.toString(), "da uzmem telefon" in r.urges)
        assertEquals(4, r.found.size)
    }

    @Test
    fun wholeWordsOnly() {
        // "dahom" is not "dah"-the-sensation by accident of a prefix rule, "srećom" (luckily) is not happiness
        val r = FourPointsReader.read("Jednom sam samo sjedio. Danas je bilo dosta posla.")
        assertTrue(r.toString(), r.body.isEmpty() && r.urges.isEmpty() && r.moods.isEmpty())
    }

    @Test
    fun unlistedUrgeKeepsItsWords() {
        val r = FourPointsReader.read("Dođe mi da sve ostavim i odem na more")
        assertTrue(r.urges.toString(), r.urges.any { it.contains("sve ostavim") } || "da pobjegnem" in r.urges)
    }

    @Test
    fun unplacedPieceGoesByMeaning() {
        val r = FourPointsReader.read("Danas sam bio na sastanku cijelo prijepodne") { FourPointsReader.THOUGHTS }
        assertTrue(r.thoughts.contains("sastanku"))
    }
}
