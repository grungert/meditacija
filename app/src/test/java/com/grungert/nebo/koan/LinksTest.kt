package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.FourPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LinksTest {
    private var id = 0L
    private fun at(day: Int, hour: Int) = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, day, hour, 0) }.timeInMillis

    private fun note(day: Int, hour: Int, moods: List<String>, body: List<String> = emptyList(), urges: List<String> = emptyList(), text: String = "") =
        Entry(id = ++id, createdAt = at(day, hour), kind = "note", text = text, moods = moods, points = FourPoints("", body, urges))

    private val worried = Thing(FourPointsReader.EMOTIONS, "zabrinuto")
    private val shoulders = Thing(FourPointsReader.BODY, "napeta ramena")
    private val phone = Thing(FourPointsReader.URGES, "da uzmem telefon")

    @Test
    fun aLinkShowsOnlyOnceItComesBack() {
        val notes = listOf(
            note(1, 20, listOf("zabrinuto"), listOf("napeta ramena")),
            note(2, 20, listOf("zabrinuto"), listOf("napeta ramena")),
            note(3, 23, listOf("umorno"), urges = listOf("da uzmem telefon")),
        )
        val w = Links.web(notes)
        assertEquals(2, w.threshold)
        assertEquals(1, w.links.size)
        assertEquals(setOf(worried, shoulders), setOf(w.links[0].a, w.links[0].b))
        assertEquals(2, w.links[0].together)
        // tiredness and the phone came together once: not yet a link
        assertTrue(w.links.none { phone == it.a || phone == it.b })
    }

    @Test
    fun chosenMoodsAreTrustedAndLooksBackHoldNothing() {
        // moods chosen by hand stand as they are; the words fill in only when none were chosen
        val chosen = Entry(id = 1, createdAt = at(1, 20), kind = "note", text = "Umoran sam, ali mirniji nego jutros", moods = listOf("umorno"))
        assertEquals(setOf(Thing(FourPointsReader.EMOTIONS, "umorno")), Links.things(chosen).filter { it.point == FourPointsReader.EMOTIONS }.toSet())
        val unchosen = Entry(id = 2, createdAt = at(1, 20), kind = "note", text = "Brinem da neću stići do petka")
        assertTrue(Thing(FourPointsReader.EMOTIONS, "zabrinuto") in Links.things(unchosen))
        // the evening look back is written at night about the whole day: it says nothing about when or together
        val evening = Entry(id = 3, createdAt = at(1, 21), kind = "evening", text = "Dobro: šetnja. Teško: brinem za rok, ramena napeta.")
        assertTrue(Links.things(evening).isEmpty())
    }

    @Test
    fun twoMoodsInOneNoteAreNotALink() {
        val notes = List(3) { note(it + 1, 9, listOf("nemirno", "umorno")) }
        assertTrue(Links.web(notes).links.isEmpty())
    }

    @Test
    fun howMuchMoreThanChanceOnlyFromFiveTogether() {
        val few = List(4) { note(it + 1, 20, listOf("zabrinuto"), listOf("napeta ramena")) } + List(4) { note(it + 10, 9, listOf("mirno")) }
        assertNull(Links.web(few).links.single().lift)
        val many = List(5) { note(it + 1, 20, listOf("zabrinuto"), listOf("napeta ramena")) } + List(5) { note(it + 10, 9, listOf("mirno")) }
        val l = Links.web(many).links.single()
        // 5 together, each in 5 of 10 notes: twice as often as by chance
        assertEquals(2f, l.lift!!, 0.001f)
    }

    @Test
    fun aHabitOfMindIsAThing() {
        val notes = List(2) { note(it + 1, 21, listOf("zabrinuto"), text = "Šta ako ne stignem do petka") }
        val w = Links.web(notes)
        assertEquals(Thing(FourPointsReader.THOUGHTS, "„šta ako“"), w.links.single().let { if (it.a.point == FourPointsReader.THOUGHTS) it.a else it.b })
        val f = Links.focus(notes, Thing(FourPointsReader.THOUGHTS, "„šta ako“"))
        assertEquals("Uveče, kad je tu „šta ako“, uz to dođe „zabrinuto“ 2 od 2 puta.", f.sentence())
    }

    @Test
    fun theFocusSaysWhatComesWithItAndWhen() {
        val notes = listOf(
            note(1, 20, listOf("zabrinuto"), listOf("napeta ramena")),
            note(2, 21, listOf("zabrinuto"), listOf("napeta ramena", "stezanje u grudima")),
            note(3, 19, listOf("zabrinuto"), listOf("napeta ramena", "stezanje u grudima")),
            note(4, 20, listOf("zabrinuto")),
            note(5, 9, listOf("mirno")),
        )
        val f = Links.focus(notes, worried)
        assertEquals(4, f.notes)
        assertEquals(listOf("napeta ramena", "stezanje u grudima"), f.with.map { it.thing.label })
        assertEquals(3, f.with[0].together)
        assertEquals(2, f.part)   // all four in the evening
        assertEquals("Uveče, kad je tu „zabrinuto“, uz to dođe „napeta ramena“ 3 od 4 puta.", f.sentence())
    }

    @Test
    fun noPartOfTheDayWhenNoneLeads() {
        val notes = listOf(note(1, 8, listOf("nemirno")), note(2, 14, listOf("nemirno")), note(3, 20, listOf("nemirno")))
        val f = Links.focus(notes, Thing(FourPointsReader.EMOTIONS, "nemirno"))
        assertNull(f.part)
        assertNull(f.sentence())
    }

    @Test
    fun pointsTouchAsShares() {
        val notes = listOf(
            note(1, 20, listOf("zabrinuto"), listOf("napeta ramena")),
            note(2, 20, listOf("tužno")),
        )
        val w = Links.web(notes)
        assertEquals(0.5f, w.pointPairs.getValue(FourPointsReader.EMOTIONS to FourPointsReader.BODY), 0.001f)
        assertEquals(1f, w.pointShare.getValue(FourPointsReader.EMOTIONS), 0.001f)
        assertNotNull(w.pointPairs[FourPointsReader.THOUGHTS to FourPointsReader.URGES])
    }
}
