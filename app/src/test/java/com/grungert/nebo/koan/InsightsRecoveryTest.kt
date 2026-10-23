package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.FourPoints
import com.grungert.nebo.data.Session
import com.grungert.nebo.ui.RangeKind
import com.grungert.nebo.ui.StatsRange
import com.grungert.nebo.ui.computeStats
import com.grungert.nebo.ui.suggestNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

/**
 * Can the app find what is really there, and only that? Three months of one made-up person, with patterns put in on
 * purpose and noise around them: worry with tight shoulders and "šta ako" in the evenings, tiredness and the phone at
 * night, calm mornings, and breathing that helps more than walking. The app's own code reads it all back.
 */
class InsightsRecoveryTest {
    private val rnd = Random(7)
    private var id = 0L
    private val start = Calendar.getInstance().apply { clear(); set(2026, Calendar.JULY, 27, 0, 0) }.timeInMillis
    private fun at(day: Int, hour: Int, minute: Int = 0) = start + day * 86_400_000L + hour * 3_600_000L + minute * 60_000L

    private val entries = ArrayList<Entry>()
    private val sessions = ArrayList<Session>()
    private val noiseMoods = listOf("radoznalo", "zbunjeno", "ljuto", "tužno", "radosno")
    private val noiseBody = listOf("stisnut stomak", "knedla u grlu", "drhtanje", "stegnuta vilica")

    private fun note(t: Long, moods: List<String>, body: List<String> = emptyList(), urges: List<String> = emptyList(), text: String = "", kind: String = "note", session: Long? = null) {
        entries += Entry(id = ++id, createdAt = t, kind = kind, sessionId = session, text = text.ifBlank { null }, moods = moods,
            points = FourPoints(if (text.isNotBlank()) text else "", body, urges))
    }

    init {
        for (d in 0 until 90) {
            // evenings, 4 days in 7: worry, the shoulders, and the mind running ahead
            if (rnd.nextFloat() < 0.57f) note(at(d, 20, rnd.nextInt(50)), listOf("zabrinuto") + (if (rnd.nextFloat() < 0.3f) listOf("nemirno") else emptyList()),
                if (rnd.nextFloat() < 0.75f) listOf("napeta ramena") else emptyList(), text = if (rnd.nextFloat() < 0.6f) "Šta ako ne stignem sve do petka" else "Sutra je opet rok")
            // late nights, 3 days in 7: tired, and the phone
            if (rnd.nextFloat() < 0.43f) note(at(d, 23, rnd.nextInt(50)), listOf("umorno"), urges = if (rnd.nextFloat() < 0.7f) listOf("da uzmem telefon") else emptyList())
            // mornings, now and then: calm
            if (rnd.nextFloat() < 0.35f) note(at(d, 8, rnd.nextInt(50)), listOf(if (rnd.nextBoolean()) "mirno" else "zahvalno"))
            // noise: a random mood with a random body sensation, at a random hour
            if (rnd.nextFloat() < 0.35f) note(at(d, 7 + rnd.nextInt(16), rnd.nextInt(50)), listOf(noiseMoods.random(rnd)),
                if (rnd.nextFloat() < 0.5f) listOf(noiseBody.random(rnd)) else emptyList())
            // sessions with a note before and after: breathing in the evening leaves it lighter 4 times in 5, a walk 2 in 5
            if (rnd.nextFloat() < 0.5f) {
                val type = if (rnd.nextFloat() < 0.6f) "breath" else "walk"
                val sid = ++id
                val t = at(d, if (type == "breath") 21 else 13, rnd.nextInt(30))
                sessions += Session(id = sid, startedAt = t, type = type, plannedSec = 600, pattern = "46", walk = type == "walk", actualSec = 600)
                note(t - 60_000, listOf("nemirno"), kind = "before", session = sid)
                val lighter = rnd.nextFloat() < (if (type == "breath") 0.8f else 0.4f)
                note(t + 660_000, listOf(if (lighter) "mirno" else "nemirno"), kind = "after", session = sid)
            }
        }
    }

    @Test
    fun theLinksPutInComeBack() {
        val w = Links.web(entries)
        report("LINKS", w.links.take(12).joinToString("\n") { "  ${it.a.label} + ${it.b.label}: ${it.together}" + (it.lift?.let { l -> " · %.1f×".format(l) } ?: "") })
        val worried = Thing(FourPointsReader.EMOTIONS, "zabrinuto")
        val shoulders = Thing(FourPointsReader.BODY, "napeta ramena")
        val whatIf = Thing(FourPointsReader.THOUGHTS, "„šta ako“")
        val tired = Thing(FourPointsReader.EMOTIONS, "umorno")
        val phone = Thing(FourPointsReader.URGES, "da uzmem telefon")
        fun linked(a: Thing, b: Thing) = w.links.any { (it.a == a && it.b == b) || (it.a == b && it.b == a) }
        assertTrue(linked(worried, shoulders)); assertTrue(linked(worried, whatIf)); assertTrue(linked(tired, phone))
        // the strongest links are the ones put in, and they say how much more often than chance
        val top = w.links.take(4).map { setOf(it.a, it.b) }
        assertTrue(setOf(worried, shoulders) in top && setOf(tired, phone) in top)
        assertTrue(w.links.first().lift!! > 1.5f)
        // noise brings no link of its own as strong as those
        val noise = w.links.filter { l -> listOf(l.a, l.b).any { it.label in noiseMoods || it.label in noiseBody } }
        assertTrue(noise.all { it.together < w.links.first().together / 2 })
        // two frequent things that meet only by chance are no link (restless before every session, tight shoulders in the evening)
        assertTrue(w.links.all { (it.lift ?: 2f) >= Links.MIN_LIFT })
    }

    @Test
    fun theHourOfAThingIsFound() {
        val f = Links.focus(entries, Thing(FourPointsReader.EMOTIONS, "zabrinuto"))
        assertEquals(2, f.part)
        assertEquals("napeta ramena", f.with.first().thing.label)
        val phone = Links.focus(entries, Thing(FourPointsReader.URGES, "da uzmem telefon"))
        assertEquals(3, phone.part)
        report("FOCUS", "  ${f.sentence()}\n  ${phone.sentence()}")
    }

    @Test
    fun whatHelpsIsFound() {
        val h = WhatHelps.find(sessions, entries)
        assertEquals("breath", h.kinds.first().type)
        val breath = h.kinds.first()
        assertTrue(breath.lighter.toFloat() / breath.count > 0.65f)
        report("HELPS", h.kinds.joinToString("\n") { "  ${it.type}: ${it.lighter} od ${it.count}" })
    }

    @Test
    fun theSuggestionFitsTheHourAndWhatHelps() {
        val h = WhatHelps.find(sessions, entries)
        val evening = suggestNow(entries, h, 2)
        val night = suggestNow(entries, h, 3)
        val morning = suggestNow(entries, h, 0)
        // the evening's worry, met with what has been shown to help: breathing, not a rule of thumb
        assertEquals("breath", evening.type)
        assertEquals("iz tvojih bilješki i iskustva", evening.basis)
        assertTrue(evening.why.contains("pomaže 17 od 20"))
        assertEquals("iz tvojih bilješki i iskustva", night.basis)
        // nothing heavy in the mornings: what helps, from experience
        assertEquals("iz tvog iskustva", morning.basis); assertEquals("breath", morning.type)
        report("SUGGEST", "  uveče: ${evening.type} · ${evening.why}\n  noću: ${night.type} · ${night.why}\n  ujutru: ${morning.type} · ${morning.why}")
    }

    @Test
    fun theOverviewCountsTheMonth() {
        val now = at(89, 22)
        val d = computeStats(sessions, entries, emptyList(), StatsRange.of(RangeKind.MONTH, now), now)
        assertNotNull(d.helps)
        assertTrue(d.flowPairs >= 5)
        assertTrue(d.habits.any { it.first == "staako" })
        report("OVERVIEW", "  ${d.minutes} min, ${d.sessions} sesija, ${d.notes} zapisa, dani ${d.practicedDays}/${d.daysSoFar}, tok ${d.flowPairs}, navike ${d.habits}")
    }

    private fun report(title: String, body: String) {
        java.io.File(System.getProperty("java.io.tmpdir"), "insights_recovery.txt").appendText("== $title\n$body\n")
    }
}
