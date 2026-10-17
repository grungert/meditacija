package com.grungert.nebo.koan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchWordsTest {
    @Test
    fun findsOtherFormsOfAWord() {
        assertEquals("posl", SearchWords.stem("posao"))
        assertTrue(SearchWords.matches("Na poslu je bilo naporno", SearchWords.stems("posao")))
        assertTrue(SearchWords.matches("Porodica mi je sve", SearchWords.stems("porodici")))
        assertTrue(SearchWords.matches("Ne mogu da spavam", SearchWords.stems("spavanje")))
        assertTrue(SearchWords.matches("Mami sam rekao", SearchWords.stems("mama")))
    }

    @Test
    fun littleWordsAndInsidesOfWordsDoNotCount() {
        assertEquals(listOf("problem", "posl"), SearchWords.stems("problemi na poslu"))
        assertFalse(SearchWords.matches("Osjećam nemir", SearchWords.stems("mir")))
        assertFalse(SearchWords.matches("Premotavam naše posljednje razgovore", SearchWords.stems("posao")))
        assertTrue(SearchWords.matches("Poslovi se gomilaju", SearchWords.stems("posao")))
        assertFalse(SearchWords.matches("Na poslu je bilo dobro", SearchWords.stems("problemi na poslu")))
    }
}
