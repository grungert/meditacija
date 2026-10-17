package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.FourPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ThreadFaceTest {
    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 17, 12, 0, 0) }.timeInMillis

    /** A note [daysAgo] days back at [hour]. */
    private fun note(id: Long, daysAgo: Int, hour: Int, words: String, moods: List<String> = emptyList(), points: FourPoints = FourPoints()) =
        Entry(id = id, createdAt = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, hour) }.timeInMillis,
            kind = "note", text = words, moods = moods, points = points)

    @Test
    fun anEveningPullToThePhone() {
        val urge = FourPoints(urges = listOf("da uzmem telefon"))
        val face = ThreadFaces.of(listOf(
            note(1, 30, 21, "Moram još da odgovorim na poruke", listOf("nemirno"), urge),
            note(2, 20, 20, "Opet skrolujem do kasno", listOf("nemirno"), urge),
            note(3, 9, 21, "Vuče me telefon prije spavanja", listOf("umorno"), urge),
        ), now)
        assertEquals(FourPointsReader.URGES, face.dominant)
        assertEquals("hourglass", face.practice)
        assertTrue(face.sentence, face.sentence.startsWith("Najčešće uveče") && "poriv „da uzmem telefon“" in face.sentence)
        assertEquals(ThreadPhase.RETURNING, face.phase)
    }

    @Test
    fun phases() {
        val quiet = ThreadFaces.of(listOf(note(1, 60, 9, "Posao me pritiska"), note(2, 40, 9, "Opet posao")), now)
        assertEquals(ThreadPhase.SETTLING, quiet.phase)
        val fresh = ThreadFaces.of(listOf(note(1, 6, 9, "Selidba"), note(2, 2, 9, "Kutije svuda")), now)
        assertEquals(ThreadPhase.WAKING, fresh.phase)
        val lighter = ThreadFaces.of(listOf(
            note(1, 12, 9, "Teško", listOf("tužno")), note(2, 10, 9, "Teško", listOf("tužno")),
            note(3, 5, 9, "Bolje", listOf("mirno")), note(4, 3, 9, "Bolje", listOf("zahvalno")),
        ), now)
        assertEquals(ThreadPhase.SETTLING, lighter.phase)
    }
}
