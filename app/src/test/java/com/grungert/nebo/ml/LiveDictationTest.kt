package com.grungert.nebo.ml

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LiveDictationTest {
    private val tenth = 1600   // samples in a tenth of a second at 16 kHz

    private fun voice(tenths: Int) = List(tenths) { ShortArray(tenth) { i -> (8000 * sin(2 * PI * 220 * i / 16000.0)).toInt().toShort() } }
    private fun quiet(tenths: Int) = List(tenths) { ShortArray(tenth) { i -> (40 * sin(i * 0.37)).toInt().toShort() } }

    @Test
    fun piecesEndAtPausesAndTheTailAtTheEnd() = runBlocking {
        val heard = ArrayList<Int>()
        val d = LiveDictation(CoroutineScope(Dispatchers.Default)) { pcm -> heard += pcm.size / tenth; "dio${heard.size}" }
        // quiet before speaking is not kept; two sentences with a breath between them; a last one cut by the stop
        for (c in quiet(5) + voice(20) + quiet(8) + voice(15) + quiet(7) + voice(12)) d.feed(c, c.size)
        val text = d.finish()
        assertEquals("dio1 dio2 dio3", text)
        assertEquals(listOf(26, 21, 12), heard)   // each piece with the quiet that ended it
    }

    @Test
    fun aLongVoiceIsCutEvenWithoutAPause() = runBlocking {
        val heard = ArrayList<Int>()
        val d = LiveDictation(CoroutineScope(Dispatchers.Default)) { pcm -> heard += pcm.size / tenth; "x" }
        for (c in voice(320)) d.feed(c, c.size)
        d.finish()
        assertEquals(listOf(150, 150, 20), heard)
    }

    @Test
    fun aClickIsNoPiece() = runBlocking {
        val d = LiveDictation(CoroutineScope(Dispatchers.Default)) { "riječ" }
        for (c in voice(1) + quiet(20)) d.feed(c, c.size)
        assertEquals("", d.finish())
    }
}
