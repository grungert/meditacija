package com.grungert.nebo.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LatinTest {
    @Test
    fun cyrillicBecomesLatin() {
        assertEquals("Danas sam bio na poslu i sada sam umoran.", Latin.of("Данас сам био на послу и сада сам уморан."))
        assertEquals("Ljubav, njega, džep, đak, ćup, čaša, šuma, žaba", Latin.of("Љубав, њега, џеп, ђак, ћуп, чаша, шума, жаба"))
        assertEquals("LJUBAV i Ljubav", Latin.of("ЉУБАВ и Љубав"))
        // the Montenegrin letters
        assertEquals("śekira, źenica", Latin.of("с́екира, з́еница"))
        // what is already Latin stays exactly as it is
        val latin = "Šta je sada tu? Možda ništa."
        assertEquals(latin, Latin.of(latin))
        assertFalse(Latin.hasCyrillic(latin))
    }
}
