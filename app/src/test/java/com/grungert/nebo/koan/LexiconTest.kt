package com.grungert.nebo.koan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LexiconTest {
    @Test
    fun matchesWholeWordsAndTheirForms() {
        assertTrue("nemir" in Lexicon.themes("Stalno jurim za poslom, napeta sam"))
        assertTrue("svakodnevica" in Lexicon.themes("Stalno jurim za poslom"))
        assertTrue("odnosi" in Lexicon.themes("Svađao sam se s bratom"))
        assertTrue("ljutnja" in Lexicon.themes("još me drži ljutnja"))
        assertTrue("usamljenost" in Lexicon.themes("Osjećam da me niko ne razumije"))
        assertTrue("sumnja" in Lexicon.themes("Ne  znam šta da radim"))
    }

    @Test
    fun ignoresWordsThatOnlyContainAStem() {
        assertFalse("ego" in Lexicon.themes("Bolje je nego juče, njegov savjet je pomogao"))
        assertFalse("odnosi" in Lexicon.themes("Imam rezultat koji sam čekao"))
        assertFalse("svakodnevica" in Lexicon.themes("Poslije meditacije sam mirniji"))
        assertFalse("kontrola" in Lexicon.themes("Gledao sam planine"))
        assertEquals(listOf("smirenost"), Lexicon.themes("Poslije meditacije sam mirnije"))
    }
}
