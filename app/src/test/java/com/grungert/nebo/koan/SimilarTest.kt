package com.grungert.nebo.koan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Notes as e5 sees them: a large part every note in our language shares, and a small part of their own. Two notes on
 * the same thing share also a little of that own part.
 */
class SimilarTest {
    private val r = Random(5)
    private val dim = 64
    private fun unit(v: FloatArray): FloatArray { val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat(); return FloatArray(v.size) { v[it] / n } }
    private fun noise() = FloatArray(dim) { (r.nextFloat() - 0.5f) }
    private val shared = unit(noise())
    /** A note: mostly the shared part, a little of its own, and, if given, some of a topic. */
    private fun note(topic: FloatArray? = null): FloatArray {
        val own = unit(noise())
        return unit(FloatArray(dim) { shared[it] * 3f + own[it] * 0.8f + (topic?.get(it) ?: 0f) * 0.8f })
    }
    private fun cos(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }.toFloat()

    @Test
    fun rawSimilaritySaysEverythingIsAlike() {
        val notes = List(40) { note() }
        val raw = notes.drop(1).map { cos(notes[0], it) }
        // every unrelated note is close by the raw measure, as with e5
        assertTrue(raw.all { it > 0.85f })
    }

    @Test
    fun centredNothingAlikeShowsNothing() {
        val notes = List(40) { note() }
        val mean = meanOf(notes)
        val target = centred(notes[0], mean)
        val scores = notes.drop(1).map { cos(target, centred(it, mean)) }
        assertEquals(emptyList<Int>(), closeOnes(scores))
    }

    @Test
    fun centredAlikeIsFound() {
        val topic = unit(noise())
        val notes = List(38) { note() } + note(topic) + note(topic)
        val mean = meanOf(notes)
        val target = centred(notes.last(), mean)
        val scores = notes.dropLast(1).map { cos(target, centred(it, mean)) }
        assertEquals(listOf(38), closeOnes(scores))
    }

    @Test
    fun amongFewOnlyCloseOutright() {
        assertEquals(listOf(1), closeOnes(listOf(0.05f, 0.4f, -0.1f)))
    }

    @Test
    fun withFewNotesThereIsNoUsualNoteYet() {
        val topic = unit(noise())
        val few = List(12) { note() } + note(topic) + note(topic)
        assertTrue(nearByMeaning(few.last(), few.dropLast(1), few).isEmpty())
        val enough = List(20) { note() } + note(topic) + note(topic)
        assertEquals(listOf(20), nearByMeaning(enough.last(), enough.dropLast(1), enough).map { it.first })
    }
}
