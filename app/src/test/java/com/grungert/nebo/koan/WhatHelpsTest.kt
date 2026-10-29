package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WhatHelpsTest {
    private var id = 0L
    private val sessions = ArrayList<Session>()
    private val entries = ArrayList<Entry>()

    private fun at(day: Int, hour: Int) = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, day, hour, 0) }.timeInMillis

    /** A session with a note before and after it. */
    private fun sit(type: String, day: Int, hour: Int, before: String, after: String) {
        val s = ++id
        sessions += Session(id = s, startedAt = at(day, hour), type = type, plannedSec = 600, pattern = "46", walk = false)
        entries += Entry(id = ++id, createdAt = at(day, hour), kind = "before", sessionId = s, moods = listOf(before))
        entries += Entry(id = ++id, createdAt = at(day, hour) + 600_000, kind = "after", sessionId = s, moods = listOf(after))
    }

    @Test
    fun nothingIsSaidFromTooLittle() {
        repeat(4) { sit("breath", it + 1, 8, "nemirno", "mirno") }
        val f = WhatHelps.find(sessions, entries)
        assertEquals(4, f.pairs)
        assertTrue(f.kinds.isEmpty())
        assertNull(WhatHelps.suggestion(f))
    }

    @Test
    fun theKindThatHelpsMostComesFirst() {
        repeat(6) { sit("breath", it + 1, 8, "nemirno", if (it < 5) "mirno" else "nemirno") }
        repeat(5) { sit("silence", it + 10, 8, "tužno", if (it < 2) "mirno" else "tužno") }
        val f = WhatHelps.find(sessions, entries)
        assertEquals(listOf("breath", "silence"), f.kinds.map { it.type })
        assertEquals(5, f.kinds[0].lighter); assertEquals(6, f.kinds[0].count)
        assertEquals("breath", WhatHelps.suggestion(f))
    }

    @Test
    fun aTimeOfDayThatClearlyHelpsMore() {
        repeat(4) { sit("walk", it + 1, 19, "nemirno", "mirno") }
        repeat(4) { sit("walk", it + 10, 8, "nemirno", "nemirno") }
        val f = WhatHelps.find(sessions, entries)
        assertEquals(Triple("walk", 2, 0), f.partFor)   // evening better than morning
    }

    @Test
    fun sessionsWithoutBothNotesDoNotCount() {
        sessions += Session(id = 999, startedAt = at(1, 8), type = "breath", plannedSec = 600, pattern = "46", walk = false)
        entries += Entry(id = 1000, createdAt = at(1, 8), kind = "after", sessionId = 999, moods = listOf("mirno"))
        assertEquals(0, WhatHelps.find(sessions, entries).pairs)
    }

    @Test
    fun aNoteFromJustBeforeStandsForTheOneNotAskedFor() {
        // an hour before: a note of its own; then the sit with only the note after it
        entries += Entry(id = 2000, createdAt = at(2, 7), kind = "note", moods = listOf("zabrinuto"))
        sessions += Session(id = 2001, startedAt = at(2, 8), type = "breath", plannedSec = 600, pattern = "46", walk = false)
        entries += Entry(id = 2002, createdAt = at(2, 8) + 600_000, kind = "after", sessionId = 2001, moods = listOf("mirno"))
        val p = WhatHelps.pairs(sessions, entries).single()
        assertEquals(2f, p.shift, 0.01f)
    }

    @Test
    fun aNoteFromHoursBeforeSaysNothingAboutTheSit() {
        entries += Entry(id = 2000, createdAt = at(2, 4), kind = "note", moods = listOf("zabrinuto"))
        sessions += Session(id = 2001, startedAt = at(2, 8), type = "breath", plannedSec = 600, pattern = "46", walk = false)
        entries += Entry(id = 2002, createdAt = at(2, 8) + 600_000, kind = "after", sessionId = 2001, moods = listOf("mirno"))
        assertEquals(0, WhatHelps.pairs(sessions, entries).size)
    }

    @Test
    fun moodsSaidInWordsCountWhenNoneWereChosen() {
        sessions += Session(id = 3001, startedAt = at(3, 8), type = "breath", plannedSec = 600, pattern = "46", walk = false)
        entries += Entry(id = 3002, createdAt = at(3, 8), kind = "before", sessionId = 3001, text = "Baš sam nemirna i zabrinuta zbog sutra.")
        entries += Entry(id = 3003, createdAt = at(3, 8) + 600_000, kind = "after", sessionId = 3001, text = "Sad sam mirna.")
        val p = WhatHelps.pairs(sessions, entries).single()
        assertTrue(p.shift > 1f)
    }

    @Test
    fun aFiftyFiftyWithNotesAroundItCounts() {
        // a 50-50 asks for no note; the note just before it and the first one after tell how it went
        entries += Entry(id = 4000, createdAt = at(4, 10), kind = "esm", moods = listOf("nemirno"))
        sessions += Session(id = 4001, startedAt = at(4, 11), type = "fifty", plannedSec = 60, pattern = "46", walk = false, actualSec = 60)
        entries += Entry(id = 4002, createdAt = at(4, 12), kind = "note", moods = listOf("mirno"))
        val p = WhatHelps.pairs(sessions, entries).single()
        assertEquals("fifty", p.type)
        assertEquals(2f, p.shift, 0.01f)
        // but it is never offered as a sit to start
        repeat(6) { i -> sit("fifty", 10 + i, 9, "nemirno", "mirno") }
        assertNull(WhatHelps.suggestion(WhatHelps.find(sessions, entries)))
    }

    @Test
    fun aStartOfAFewSecondsIsNoSitBetweenNotes() {
        entries += Entry(id = 5000, createdAt = at(5, 10), kind = "note", moods = listOf("tužno"))
        sessions += Session(id = 5001, startedAt = at(5, 11), type = "breath", plannedSec = 300, pattern = "46", walk = false, actualSec = 4)
        entries += Entry(id = 5002, createdAt = at(5, 12), kind = "note", moods = listOf("mirno"))
        assertEquals(0, WhatHelps.pairs(sessions, entries).size)
    }
}
