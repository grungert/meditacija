package com.grungert.nebo.ml

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.grungert.nebo.audio.Recorder
import com.grungert.nebo.data.Db
import com.grungert.nebo.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object WhisperNative {
    init { System.loadLibrary("nebo_whisper") }
    external fun initContext(modelPath: String): Long
    external fun freeContext(ptr: Long)
    external fun transcribe(ptr: Long, samples: FloatArray, language: String, prompt: String): ByteArray
}

/** One loaded speech model: turns 16 kHz mono samples into text. Close it to free the memory. */
private interface SpeechEngine : AutoCloseable {
    fun transcribe(pcm: FloatArray): String
}

private class WhisperEngine(path: String, private val language: String) : SpeechEngine {
    private val ptr = WhisperNative.initContext(path)
    val ok get() = ptr != 0L
    override fun transcribe(pcm: FloatArray) =
        Transcriber.clean(String(WhisperNative.transcribe(ptr, pcm, language, Transcriber.PROMPT), Charsets.UTF_8))
    override fun close() { if (ptr != 0L) WhisperNative.freeContext(ptr) }
}

/** NVIDIA Parakeet TDT through sherpa-onnx: reads the whole recording at once, and finds the language itself. */
private class ParakeetEngine(models: ModelStore, choice: ModelChoice) : SpeechEngine {
    private val recognizer = OfflineRecognizer(null, OfflineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
        modelConfig = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = models.file(choice.files[0].name).absolutePath,
                decoder = models.file(choice.files[1].name).absolutePath,
                joiner = models.file(choice.files[2].name).absolutePath,
            ),
            tokens = models.file(choice.files[3].name).absolutePath,
            // phones pair a few fast cores with many slow ones; more threads than fast cores only waits on the slow ones
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
            modelType = "nemo_transducer",
        ),
    ))
    override fun transcribe(pcm: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(pcm, 16000)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.replace(Regex("\\s+"), " ").trim()
        } finally {
            stream.release()
        }
    }
    override fun close() = recognizer.release()
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

    /** The model id that would transcribe right now, or null when no speech model is on the phone. */
    fun readyModel(): String? = models.readyWhisper()?.id

    // Loading a model takes seconds, so the loaded one is kept for a minute after its last use
    // (and loading starts as soon as the person begins to speak). Only touched under [lock].
    private var loaded: SpeechEngine? = null
    private var loadedKey: String? = null
    private var release: Job? = null

    private fun open(choice: ModelChoice): SpeechEngine? {
        val key = choice.id + "/" + settings.whisperLang
        if (loadedKey == key) loaded?.let { return it }
        loaded?.close(); loaded = null; loadedKey = null
        return try {
            val t0 = System.nanoTime()
            (if (choice.engine == "parakeet") ParakeetEngine(models, choice)
            else WhisperEngine(models.file(choice.files[0].name).absolutePath, settings.whisperLang).takeIf { it.ok })
                ?.also {
                    loaded = it; loadedKey = key
                    val took = (System.nanoTime() - t0) / 1e9f
                    settings.measuredSpeech(choice.id, loadSeconds = took)
                    Log.i(TAG, "loaded ${choice.id} in %.1f s".format(took))
                }
        } catch (t: Throwable) {
            Log.e(TAG, "could not load ${choice.id}", t); null
        }
    }

    /** Lets the loaded model go a minute after the last use; it holds hundreds of megabytes. */
    private fun releaseLater() {
        release?.cancel()
        release = scope.launch {
            delay(60_000)
            lock.withLock { loaded?.close(); loaded = null; loadedKey = null }
        }
    }

    /** Starts loading the speech model while the person is still talking, so the text comes quickly after. */
    fun prepare() {
        scope.launch {
            lock.withLock { models.readyWhisper()?.let { open(it) } }
            releaseLater()
        }
    }

    /** Silence makes speech models invent words, so they never get to hear it. */
    private fun heard(engine: SpeechEngine, pcm: FloatArray): String =
        if (pcm.isEmpty() || pcm.maxOf { kotlin.math.abs(it) } < Recorder.SILENCE) "" else {
            val t0 = System.nanoTime()
            engine.transcribe(pcm).also {
                val took = (System.nanoTime() - t0) / 1e9f
                val audio = pcm.size / 16000f
                loadedKey?.substringBefore('/')?.let { id -> settings.measuredSpeech(id, rate = took / audio.coerceAtLeast(1f)) }
                Log.i(TAG, "%.1f s of audio in %.2f s".format(audio, took))
            }
        }

    /**
     * About how many seconds turning a recording of this length into text will take, from the last measurements
     * on this phone: loading the model (unless it is still loaded) plus the work per second of audio.
     */
    fun estimateSeconds(audioSeconds: Float): Float {
        val choice = models.readyWhisper() ?: return 0f
        val load = if (loadedKey?.startsWith(choice.id + "/") == true) 0f else settings.speechLoadSeconds(choice.id)
        return load + settings.speechRate(choice.id) * audioSeconds + 0.5f
    }

    /** Transcribes one recording straight away, for showing the text while the person is still on the screen. */
    suspend fun transcribeNow(file: File): String? = withContext(Dispatchers.Default) {
        lock.withLock {
            val choice = models.readyWhisper() ?: return@withLock null
            // the take may have been thrown away (a new take, Preskoči) while this one waited for the model
            val pcm = (if (file.exists()) runCatching { Recorder.readWav(file) }.getOrNull() else null) ?: return@withLock null
            if (pcm.isEmpty() || pcm.maxOf { kotlin.math.abs(it) } < Recorder.SILENCE) return@withLock ""
            open(choice)?.let { heard(it, pcm) }
        }.also { releaseLater() }
    }

    /** A transcript worked out while the person was still on the screen, given to the note they saved. */
    fun deliver(entryId: Long, text: String, model: String) {
        if (db.setTranscript(entryId, text, model)) _done.value = entryId
    }

    fun drain() {
        scope.launch {
            lock.withLock {
                val choice = models.readyWhisper() ?: return@withLock
                val pending = db.pendingTranscriptions()
                if (pending.isEmpty()) return@withLock
                val engine = open(choice) ?: return@withLock
                for (e in pending) {
                    val f = File(e.audioPath ?: continue)
                    // a recording that is gone (deleted with its note meanwhile, or lost) is heard as silence
                    val pcm = if (f.exists()) runCatching { Recorder.readWav(f) }.getOrDefault(FloatArray(0)) else FloatArray(0)
                    val text = heard(engine, pcm)
                    // only the transcript, and only if none arrived meanwhile: the note may have been starred, edited
                    // or linked to a koan while this one was being heard
                    if (db.setTranscript(e.id, text, choice.id)) _done.value = e.id
                }
            }
            releaseLater()
        }
    }

    companion object {
        /** Drops Whisper's bracketed guesses such as "[muzika]" or "(tišina)". */
        fun clean(raw: String) = raw.replace(Regex("[\\[(][^\\])]*[\\])]"), " ").replace(Regex("\\s+"), " ").trim()

        private const val TAG = "NeboTranscriber"
        /** Nudges Whisper towards Latin-script ijekavian and everyday words about inner life. */
        const val PROMPT = "Ovo je kratka bilješka poslije meditacije, na crnogorskom, latinicom. Osjećam se mirnije, ali i umorno."
    }
}
