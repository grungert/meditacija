package com.grungert.nebo.ml

import android.util.Log
import com.grungert.nebo.audio.Recorder
import com.grungert.nebo.data.Db
import com.grungert.nebo.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

object WhisperNative {
    init { System.loadLibrary("nebo_whisper") }
    external fun initContext(modelPath: String): Long
    external fun freeContext(ptr: Long)
    external fun transcribe(ptr: Long, samples: FloatArray, language: String, prompt: String): ByteArray
}

/**
 * Turns recorded notes into text in the background. The audio is always kept, so a note can be
 * transcribed again later with a better model; entries without a transcript are picked up on every start.
 */
class Transcriber(private val db: Db, private val models: ModelStore, private val settings: Settings) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val _done = MutableStateFlow(0L)
    /** Bumps with the id of each entry that just got its transcript. */
    val done: StateFlow<Long> = _done

    fun drain() {
        scope.launch {
            lock.withLock {
                val choice = models.readyWhisper() ?: return@withLock
                val pending = db.pendingTranscriptions()
                if (pending.isEmpty()) return@withLock
                val ptr = WhisperNative.initContext(models.file(choice.files[0].name).absolutePath)
                if (ptr == 0L) { Log.e(TAG, "could not load ${choice.id}"); return@withLock }
                try {
                    for (e in pending) {
                        val f = File(e.audioPath ?: continue)
                        val pcm = if (f.exists()) Recorder.readWav(f) else FloatArray(0)
                        // silence makes Whisper invent words, so it never gets to hear it
                        val text = if (pcm.isEmpty() || pcm.maxOf { kotlin.math.abs(it) } < Recorder.SILENCE) "" else
                            clean(String(WhisperNative.transcribe(ptr, pcm, settings.whisperLang, PROMPT), Charsets.UTF_8))
                        db.updateEntry(e.copy(transcript = text, transcriptModel = choice.id))
                        _done.value = e.id
                    }
                } finally {
                    WhisperNative.freeContext(ptr)
                }
            }
        }
    }

    companion object {
        /** Drops Whisper's bracketed guesses such as "[muzika]" or "(tišina)". */
        fun clean(raw: String) = raw.replace(Regex("[\\[(][^\\])]*[\\])]"), " ").replace(Regex("\\s+"), " ").trim()

        private const val TAG = "NeboTranscriber"
        /** Nudges Whisper towards Latin-script ijekavian and everyday words about inner life. */
        private const val PROMPT = "Ovo je kratka bilješka poslije meditacije, na crnogorskom, latinicom. Osjećam se mirnije, ali i umorno."
    }
}
