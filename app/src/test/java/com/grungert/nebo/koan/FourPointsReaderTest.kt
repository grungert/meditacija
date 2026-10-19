package com.grungert.nebo.koan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FourPointsReaderTest {
    private fun read(t: String) = FourPointsReader.read(t)

    @Test
    fun everydayWordsAreNotTheBody() {
        assertFalse("napeta ramena" in read("Vratio sam se kući i zatvorio vrata").body)
        assertFalse("lupanje srca" in read("Hvala ti od srca za sve").body)
        assertFalse("toplina" in read("Popio sam toplu kafu na terasi").body)
        assertTrue("napeta ramena" in read("Boli me vrat i ramena su mi kao kamen").body)
        assertTrue("lupanje srca" in read("Srce mi lupa kao ludo").body)
    }

    @Test
    fun aPullNeedsToBeAPull() {
        assertFalse("da uzmem telefon" in read("Pričala sam telefonom s mamom").urges)
        assertFalse("da jedem" in read("Slatko sam spavao cijelu noć").urges)
        assertFalse("da se raspravljam" in read("Svađao sam se sa sestrom oko sitnica").urges)
        assertTrue("da uzmem telefon" in read("Vuče me telefon čim legnem").urges)
        assertTrue("da uzmem telefon" in read("Ležim i skrolujem do ponoći").urges)
        assertTrue("da jedem" in read("Dođe mi da pojedem nešto slatko").urges)
    }

    @Test
    fun aNegatedFeelingIsNotThere() {
        assertFalse("ljuto" in read("Nisam ljut na njega").moods)
        assertFalse("zabrinuto" in read("Ne brinem se više za to").moods)
        assertTrue("ljuto" in read("Ljut sam na sebe").moods)
        assertFalse("ljutnja" in Lexicon.themes("Nisam ljut, samo umoran"))
        assertTrue("umor" in Lexicon.themes("Nisam ljut, samo umoran"))
        assertFalse("strah" in Lexicon.themes("Prošao sam bez straha"))
    }

    @Test
    fun commonWordsDoNotMakeThemes() {
        assertFalse("prisutnost" in Lexicon.themes("Sada idem na posao"))
        assertFalse("tuga" in Lexicon.themes("Ležali smo na žalu"))
        assertFalse("prolaznost" in Lexicon.themes("Na kraju dana sam otišao kraj mora"))
        assertFalse("sumnja" in Lexicon.themes("To ima smisla"))
        assertFalse("strah" in Lexicon.themes("Slika u boji"))
        assertTrue("prisutnost" in Lexicon.themes("Ovdje i sada, samo dišem"))
        assertEquals(MindHabits.Time.FUTURE, MindHabits.time("Osjećam da sutra imam rok"))
    }

    @Test
    fun searchingWorkDoesNotFindAfter() {
        val stems = SearchWords.stems("posao")
        assertTrue(SearchWords.matches("Na poslu opet sastanak", stems))
        assertFalse(SearchWords.matches("Poslije meditacije sam mirniji", stems))
    }
}
