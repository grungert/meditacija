package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FollowUpTest {
    private val today = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 20, 0, 0) }
    private fun at(dayOffset: Int, hour: Int) = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset); set(Calendar.HOUR_OF_DAY, hour) }.timeInMillis
    private fun note(id: Long, dayOffset: Int, hour: Int, vararg moods: String, kind: String = "note", follows: Long? = null) =
        Entry(id = id, createdAt = at(dayOffset, hour), kind = kind, text = "riječi", moods = moods.toList(), follows = follows)

    @Test
    fun whatCountsAsHard() {
        assertTrue(FollowUp.isHard(note(1, -1, 9, "tužno")))
        assertTrue(FollowUp.isHard(note(1, -1, 9, kind = "rain")))
        assertFalse(FollowUp.isHard(note(1, -1, 9, "tužno", "mirno", "zahvalno")))
        assertFalse(FollowUp.isHard(note(1, -1, 9, "umorno")))
        assertFalse(FollowUp.isHard(note(1, -1, 9, "tužno", kind = "followup")))
    }

    @Test
    fun yesterdaysLatestHardNoteOnceUntilAnswered() {
        val notes = listOf(note(1, -1, 9, "ljuto"), note(2, -1, 22, "zabrinuto"), note(3, -2, 12, "tužno"), note(4, 0, 8, "mirno"))
        assertEquals(2L, FollowUp.pending(notes, today)?.id)
        // answered today: nothing more to ask today, not even about yesterday's other hard note
        assertNull(FollowUp.pending(notes + note(5, 0, 11, "mirno", kind = "followup", follows = 2), today))
        // asked at the hour it was written, kept between ten and eight in the evening
        assertEquals(at(0, 20), FollowUp.askAt(notes[1], today))
        assertEquals(at(0, 10), FollowUp.askAt(note(9, -1, 7, "tužno"), today))
    }
}
