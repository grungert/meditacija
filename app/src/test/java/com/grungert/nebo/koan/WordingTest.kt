package com.grungert.nebo.koan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Common ways of saying things that the reading of words once got wrong. */
class WordingTest {
    @Test
    fun aNoInTheClauseBeforeDoesNotTurnTheNextOneAround() {
        assertEquals(listOf("tužno"), FourPointsReader.read("Nisam dobro, tužna sam.").moods)
        assertEquals(listOf("umorno"), FourPointsReader.read("Nisam spavala, umorna sam.").moods)
        assertEquals(listOf("ljuto"), FourPointsReader.read("Ne znam. Ljuta sam.").moods)
        assertTrue("tuga" in Lexicon.themes("Nisam dobro, tužna sam."))
        // within its own clause it still does
        assertEquals(emptyList<String>(), FourPointsReader.read("Nisam ljuta.").moods)
        assertEquals(emptyList<String>(), FourPointsReader.read("Nijesam ljuta.").moods)
    }

    @Test
    fun holdingBackIsNoTears() {
        assertFalse("tužno" in FourPointsReader.read("Jedva se suzdržavam da ne uzmem telefon").moods)
        assertFalse("tuga" in Lexicon.themes("Jedva se suzdržavam da ne uzmem telefon"))
        assertTrue("tužno" in FourPointsReader.read("Krenule su mi suze").moods)
    }

    @Test
    fun luckIsNoJoy() {
        assertFalse("radosno" in FourPointsReader.read("Na sreću, nije ništa ozbiljno").moods)
        assertTrue("radosno" in FourPointsReader.read("Osjećam se srećno danas").moods)
    }

    @Test
    fun anUrgeSaidNotToBeThereIsNot() {
        assertEquals(emptyList<String>(), FourPointsReader.read("Ne odustajem, sjedim dalje").urges)
        assertEquals(emptyList<String>(), FourPointsReader.read("Ne pušim već mjesec dana").urges)
        assertEquals(emptyList<String>(), FourPointsReader.read("Već mjesec dana bez cigareta").urges)
        assertEquals(listOf("da zapalim"), FourPointsReader.read("Vuče me da zapalim cigaretu").urges)
        assertEquals(listOf("da uzmem telefon"), FourPointsReader.read("Jedva se suzdržavam da ne uzmem telefon").urges)
    }

    @Test
    fun clenchedTeethAreTheJaw() {
        assertEquals(listOf("stegnuta vilica"), FourPointsReader.read("Imam stisnute zube cijeli dan").body)
    }

    @Test
    fun comparedWithIsNoRelationship() {
        assertFalse("odnosi" in Lexicon.themes("Danas je bolje u odnosu na juče"))
        assertFalse("odnosi" in Lexicon.themes("Bilo je teško, odnosno naporno"))
        assertTrue("odnosi" in Lexicon.themes("Naš odnos je napet"))
    }

    @Test
    fun noMustIsNoMust() {
        assertFalse("moranje" in MindHabits.habits("Danas ne moram nigdje, slobodna sam"))
        assertTrue("moranje" in MindHabits.habits("Moram sve da završim danas"))
    }

    @Test
    fun aSearchFindsThePastOfAVerb() {
        assertTrue(SearchWords.matches("Spavao sam loše", SearchWords.stems("spavao")))
        assertTrue(SearchWords.matches("Plakao sam", SearchWords.stems("plakao")))
        assertTrue(SearchWords.matches("Na poslu je bilo teško", SearchWords.stems("posao")))
    }
}
