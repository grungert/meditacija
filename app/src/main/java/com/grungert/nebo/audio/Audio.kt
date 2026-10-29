package com.grungert.nebo.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

const val SAMPLE_RATE = 16_000

/** Records 16 kHz mono PCM (what Whisper wants) straight into a WAV file. */
class Recorder {
    @Volatile private var recording = false
    @Volatile var level = 0f; private set
    /** Loudest moment of the last recording, 0..1; near zero means the microphone heard nothing. */
    @Volatile var peak = 0f; private set

    val isRecording get() = recording

    @SuppressLint("MissingPermission")
    suspend fun record(out: File, maxSeconds: Int, onLevel: (Float) -> Unit = {}, onSamples: ((ShortArray, Int) -> Unit)? = null): Float = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SAMPLE_RATE))
        out.parentFile?.mkdirs()
        val raf = RandomAccessFile(out, "rw")
        raf.setLength(0)
        raf.write(ByteArray(44))
        val buf = ShortArray(SAMPLE_RATE / 10)
        val bytes = ByteBuffer.allocate(buf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        var total = 0
        peak = 0f
        recording = true
        try {
            rec.startRecording()
            while (recording && total < maxSeconds * SAMPLE_RATE) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                bytes.clear()
                var peak = 0
                for (i in 0 until n) { bytes.putShort(buf[i]); peak = maxOf(peak, abs(buf[i].toInt())) }
                raf.write(bytes.array(), 0, n * 2)
                total += n
                level = peak / 32768f
                this@Recorder.peak = maxOf(this@Recorder.peak, level)
                onLevel(level)
                onSamples?.invoke(buf, n)
            }
        } finally {
            recording = false
            runCatching { rec.stop() }; rec.release()
            writeWavHeader(raf, total)
            raf.close()
        }
        total / SAMPLE_RATE.toFloat()
    }

    fun stop() { recording = false }

    private fun writeWavHeader(raf: RandomAccessFile, samples: Int) {
        val dataLen = samples * 2
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + dataLen); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(SAMPLE_RATE)
        h.putInt(SAMPLE_RATE * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(dataLen)
        raf.seek(0); raf.write(h.array())
    }

    companion object {
        /** Reads a 16-bit mono WAV written by [record] as floats in -1..1. */
        /** Below this peak a recording is treated as silence (a muted or missing microphone). */
        const val SILENCE = 0.01f

        fun readWav(file: File): FloatArray {
            val b = file.readBytes()
            val bb = ByteBuffer.wrap(b, 44, b.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            return FloatArray(bb.remaining()) { bb.get(it) / 32768f }
        }
    }
}

/** A singing-bowl bell synthesised on the spot: a few inharmonic partials with long, slow decays. */
object Bell {
    private val partials = listOf(
        floatArrayOf(1f, 1f, 7f), floatArrayOf(2.71f, 0.55f, 5f), floatArrayOf(5.12f, 0.28f, 3.2f),
        floatArrayOf(8.25f, 0.12f, 2f), floatArrayOf(1.006f, 0.6f, 7f),
    )

    private fun render(baseHz: Float, seconds: Float, volume: Float): ShortArray {
        val sr = 44_100
        val n = (sr * seconds).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i / sr.toFloat()
            var s = 0f
            for (p in partials) s += p[1] * exp(-t / p[2] * 2.2f) * sin(2 * PI.toFloat() * baseHz * p[0] * t)
            val attack = (t / 0.004f).coerceAtMost(1f)
            out[i] = (s * attack * volume * 0.32f * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    // A playing track must stay referenced: once nothing holds it, garbage collection releases it mid-ring.
    private val playing = java.util.Collections.synchronizedSet(HashSet<AudioTrack>())

    /** Rings once and returns as soon as the bell has started; it always sounds to its end. */
    suspend fun ring(soft: Boolean = false) = withContext(Dispatchers.Default) {
        play(render(if (soft) 392f else 262f, if (soft) 5f else 9f, if (soft) 0.5f else 0.9f))
    }

    /** A glass wind chime touched by the wind: high, bright and short, quieter than the bell. */
    suspend fun chime(volume: Float = 0.35f) = withContext(Dispatchers.Default) {
        val sr = 44_100
        val base = listOf(1568f, 1760f, 1975f).random()
        val n = (sr * 2.4f).toInt()
        val pcm = ShortArray(n)
        val parts = listOf(floatArrayOf(1f, 1f, 1.6f), floatArrayOf(2.76f, 0.35f, 0.7f), floatArrayOf(5.4f, 0.12f, 0.35f))
        for (i in 0 until n) {
            val t = i / sr.toFloat()
            var s = 0f
            for (p in parts) s += p[1] * exp(-t / p[2] * 2.2f) * sin(2 * PI.toFloat() * base * p[0] * t)
            // the clapper touches twice, the second time softly
            if (t > 0.18f) { val u = t - 0.18f; s += 0.45f * exp(-u / 1.2f * 2.2f) * sin(2 * PI.toFloat() * base * 1.002f * u) }
            pcm[i] = (s * (t / 0.002f).coerceAtMost(1f) * volume * 0.25f * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        play(pcm)
    }

    private fun play(pcm: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44_100).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size - 1)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) { playing.remove(t); t.release() }
            override fun onPeriodicNotification(t: AudioTrack) {}
        }, android.os.Handler(android.os.Looper.getMainLooper()))
        playing += track
        track.play()
    }
}

/** Plays back a recorded note. */
class NotePlayer {
    private var player: MediaPlayer? = null
    /** Plays a recording; false when it cannot be played (gone, or damaged), and nothing breaks. */
    fun play(path: String, onDone: () -> Unit): Boolean {
        stop()
        if (!java.io.File(path).exists()) return false
        val p = MediaPlayer()
        return try {
            p.setDataSource(path)
            p.setOnCompletionListener { onDone(); stop() }
            p.prepare(); p.start()
            player = p
            true
        } catch (e: Exception) {
            p.release()
            false
        }
    }
    fun stop() { player?.release(); player = null }
}
