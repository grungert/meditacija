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
        // calm breath is no short breath, a warm cup no warmth in the body, tense shoulders no restless mood
        assertFalse("plitak dah" in read("Kiša pada, slušam je i dišem polako").body)
        assertTrue("plitak dah" in read("Dišem plitko i fali mi vazduha").body)
        assertFalse("toplina" in read("Primjećujem miris i toplinu šoljice").body)
        assertTrue("toplina" in read("Osjećam toplinu u grudima").body)
        assertFalse("nemirno" in read("Ramena su mi bila napeta cijeli dan").moods)
        assertTrue("nemirno" in read("Sav sam napet pred sastanak").moods)
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

    @Test
    fun ownLabelsAreReadFromTheWords() {
        val before = FourPointsReader.custom
        try {
            FourPointsReader.custom = mapOf(FourPointsReader.BODY to listOf("bol u leđima"), FourPointsReader.URGES to listOf("da sve odgodim"),
                FourPointsReader.EMOTIONS to listOf("usamljeno"))
            val r = FourPointsReader.read("Opet me bole leđa i osjećam se usamljeno, a najradije bih sve odgodio za sutra")
            assertTrue("bol u leđima" in r.body)
            assertTrue("usamljeno" in r.moods)
            assertTrue("da sve odgodim" in r.urges)
            // all of a label's words are needed: "leđa" alone is not "bol u leđima"
            assertFalse("bol u leđima" in FourPointsReader.read("Ležao sam na leđima i gledao nebo").body)
        } finally { FourPointsReader.custom = before }
    }
}
