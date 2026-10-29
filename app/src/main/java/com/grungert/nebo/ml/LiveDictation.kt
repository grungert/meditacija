package com.grungert.nebo.ml

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Text while the person speaks. The voice is cut at its own pauses (a breath, the end of a sentence): each piece is
 * turned into text as soon as it ends, and the text grows on the screen piece by piece. When the recording stops, only
 * the last piece is left to hear, so the whole text is there almost at once.
 *
 * Pieces are cut where the voice rests: after at least four seconds of speech, at the first 0.6 s quiet; a piece never
 * runs past 15 s even without a pause. "Quiet" follows the room: a little above the quietest it has been. Shorter
 * pieces came back in the wrong language: a model that finds the language itself needs a few sentences' worth to
 * tell ours from Slovak or English. What is shown is a first draft; the whole take is read again at the end.
 */
class LiveDictation(private val scope: CoroutineScope, private val hear: suspend (FloatArray) -> String?) {
    private val _text = MutableStateFlow("")
    /** What has been heard so far, all pieces together. */
    val text: StateFlow<String> = _text

    private val pieces = Channel<FloatArray>(Channel.UNLIMITED)
    private val worker = scope.launch {
        for (p in pieces) {
            val t = runCatching { hear(p) }.getOrNull()?.trim().orEmpty()
            if (t.isNotEmpty()) _text.value = (_text.value + " " + t).trim()
        }
    }

    private val buf = ArrayList<FloatArray>()      // the current piece, in tenths of a second
    private var speech = 0                         // tenths of a second of voice in it
    private var quiet = 0                          // tenths of a second of quiet since the voice last spoke
    private var floor = 0.02f                      // the room's quiet, as it is learned

    /** One tenth of a second (or so) of 16-bit samples, as it comes from the microphone. Called off the main thread. */
    @Synchronized
    fun feed(samples: ShortArray, n: Int) {
        val f = FloatArray(n) { samples[it] / 32768f }
        var sum = 0.0
        for (x in f) sum += x * x
        val rms = sqrt(sum / n.coerceAtLeast(1)).toFloat()
        // the room's quiet: it falls fast to a quieter moment and rises only slowly, so a long voice never becomes the floor
        floor = if (rms < floor) floor * 0.8f + rms * 0.2f else minOf(floor * 1.002f, rms)
        val voiced = rms > maxOf(0.006f, floor * 2.5f)
        if (buf.isEmpty() && !voiced) return            // quiet before speaking is not kept
        buf += f
        if (voiced) { speech++; quiet = 0 } else quiet++
        if ((speech >= 40 && quiet >= 6) || buf.size >= 150) cut()
    }

    private fun cut() {
        if (speech >= 3) pieces.trySend(join(buf))
        buf.clear(); speech = 0; quiet = 0
    }

    private fun join(parts: List<FloatArray>): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var i = 0
        for (p in parts) { p.copyInto(out, i); i += p.size }
        return out
    }

    /** The recording has stopped: the last piece is heard, and the whole text comes back once every piece is in. */
    suspend fun finish(): String {
        synchronized(this) { cut() }
        pieces.close()
        worker.join()
        return _text.value
    }

    /** The take is thrown away: nothing more is heard. */
    fun cancel() { pieces.close(); worker.cancel() }
}
