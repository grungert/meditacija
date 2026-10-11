package com.grungert.nebo.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The sound of the meadow, synthesised as it plays: wind through grass that swells on the out-breath,
 * birds by day and crickets by night. Nothing is recorded; every sound comes from filtered noise and sines.
 */
class Ambience {
    @Volatile var volume = 0.6f
    @Volatile var wind = 1f        // the engine's wind: low while the breath is held
    @Volatile var swell = 0f       // 0..1, rises through each out-breath
    @Volatile var day = 1f         // birds
    @Volatile var night = 0f       // crickets
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "nebo-ambience").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    private fun loop() {
        val sr = 22_050
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, 4096 * 2))
            .build()
        track.play()
        val buf = ShortArray(1024)
        val rnd = Random(3)
        var t = 0.0
        var lp1 = 0f; var lp2 = 0f
        var fade = 0f
        val a2 = 1f - exp(-2f * PI.toFloat() * 110f / sr)
        // birds
        var birdWait = 2.0; var notesLeft = 0; var noteT = 0.0; var noteDur = 0.1; var f0 = 3000.0; var f1 = 4000.0; var gap = 0.0; var phaseB = 0.0
        // crickets: two of them, slightly out of step
        val cricketHz = doubleArrayOf(4400.0, 4920.0)
        val cricketPeriod = doubleArrayOf(0.62, 0.77)
        val cricketPhase = doubleArrayOf(0.0, 0.31)
        val cricketOsc = doubleArrayOf(0.0, 0.0)

        while (running || fade > 0.001f) {
            val target = if (running) volume else 0f
            for (i in buf.indices) {
                fade += (target - fade) * 0.0005f
                t += 1.0 / sr
                // wind: noise through two one-pole filters, opening with gusts and the out-breath
                val gust = 0.5f + 0.3f * sin(t * 0.21).toFloat() + 0.2f * sin(t * 0.077 + 1.3).toFloat()
                val fc = 280f + 900f * (0.4f * gust + 0.35f * wind + 0.4f * swell)
                val a1 = 1f - exp(-2f * PI.toFloat() * fc / sr)
                val n = rnd.nextFloat() * 2f - 1f
                lp1 += a1 * (n - lp1)
                lp2 += a2 * (lp1 - lp2)
                var s = (lp1 - 0.7f * lp2) * (0.18f + 0.32f * wind * gust + 0.45f * swell)

                // birds by day: short rising or falling chirps in little phrases
                if (day > 0.02f) {
                    if (notesLeft == 0) {
                        birdWait -= 1.0 / sr
                        if (birdWait <= 0) { notesLeft = 2 + rnd.nextInt(4); noteT = 0.0; gap = 0.0; birdWait = 2.5 + rnd.nextDouble() * 6.0 }
                    } else if (gap > 0) {
                        gap -= 1.0 / sr
                    } else {
                        if (noteT == 0.0) {
                            noteDur = 0.05 + rnd.nextDouble() * 0.09
                            f0 = 2300.0 + rnd.nextDouble() * 1600.0
                            f1 = f0 + (rnd.nextDouble() - 0.35) * 2200.0
                        }
                        val k = noteT / noteDur
                        val f = f0 + (f1 - f0) * k
                        phaseB += 2 * PI * f / sr
                        s += (0.10 * day * sin(PI * k) * sin(phaseB)).toFloat()
                        noteT += 1.0 / sr
                        if (noteT >= noteDur) { noteT = 0.0; notesLeft--; gap = 0.03 + rnd.nextDouble() * 0.07 }
                    }
                }
                // crickets by night: trains of three quick pulses
                if (night > 0.02f) {
                    for (c in 0..1) {
                        cricketOsc[c] += 2 * PI * cricketHz[c] / sr
                        val ph = ((t + cricketPhase[c]) % cricketPeriod[c])
                        val pulse = (ph / 0.04).toInt()
                        val within = (ph % 0.04) / 0.018
                        if (pulse < 3 && within < 1.0) s += (0.035 * night * sin(PI * within) * sin(cricketOsc[c])).toFloat()
                    }
                }
                buf[i] = (s * fade * 0.9f * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            }
            track.write(buf, 0, buf.size)
        }
        track.stop(); track.release()
    }
}

/** Soft tones for breathing with eyes closed: a rising one to breathe in, a lower one to breathe out. */
object Cue {
    private fun tone(hz: Float, seconds: Float, rise: Boolean): ShortArray {
        val sr = 22_050
        return ShortArray((sr * seconds).toInt()) { i ->
            val t = i / sr.toFloat()
            val k = t / seconds
            val f = if (rise) hz * (1f + 0.12f * k) else hz * (1f - 0.08f * k)
            val env = sin(PI.toFloat() * k).let { it * it }
            (0.16f * env * (sin(2 * PI.toFloat() * f * t) + 0.3f * sin(4 * PI.toFloat() * f * t)) * Short.MAX_VALUE).toInt().toShort()
        }
    }
    private val inhale by lazy { tone(528f, 0.9f, true) }
    private val exhale by lazy { tone(396f, 1.1f, false) }
    private val hold by lazy { tone(660f, 0.25f, false) }

    fun play(kind: String) {
        val pcm = when (kind) { "in" -> inhale; "out" -> exhale; else -> hold }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(22_050).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size - 1)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) { t.release() }
            override fun onPeriodicNotification(t: AudioTrack) {}
        })
        track.play()
    }
}
