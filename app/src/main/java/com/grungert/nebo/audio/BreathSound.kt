package com.grungert.nebo.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The breath made audible, so the rhythm can be followed without looking: a soft rush of air that swells and
 * brightens through the in-breath, sinks and darkens through the out-breath, and falls silent while the breath
 * is held. Synthesised from filtered noise as it plays, like the meadow.
 */
class BreathSound {
    @Volatile var volume = 0.5f
    /** 0 while held or resting, 1 breathing in, 2 breathing out. */
    @Volatile var phase = 0
    /** How far into the current phase, 0..1. */
    @Volatile var progress = 0f
    @Volatile private var running = false
    @Volatile private var generation = 0
    @Volatile private var fadeRate = FADE_QUICK

    fun start() {
        if (running) return
        running = true
        fadeRate = FADE_QUICK
        val gen = ++generation
        Thread({ loop(gen) }, "nebo-breath").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop(slowly: Boolean = false) {
        if (!running) return
        fadeRate = if (slowly) FADE_SLOW else FADE_QUICK
        running = false
    }

    private fun loop(gen: Int) {
        val sr = 22_050
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, 2048 * 2))
            .build()
        track.play()
        val buf = ShortArray(512)
        val rnd = Random(11)
        var hi = 0f; var lo = 0f         // two low-pass filters; their difference is the band of air we hear
        var env = 0f; var fc = 600f       // smoothed loudness and brightness
        var fade = 0f
        val smooth = 1f - exp(-1f / (0.045f * sr))   // ~45 ms, hides the frame-rate steps of the targets
        val loCoef = 1f - exp(-2f * PI.toFloat() * 160f / sr)
        fun alive() = running && gen == generation
        while (alive() || fade > 0.001f) {
            val k = progress.coerceIn(0f, 1f)
            val (targetEnv, targetFc) = when (phase) {
                // the in-breath rises and brightens, easing off just before it turns
                1 -> sin(PI.toFloat() * (0.12f + 0.80f * k)).pow(0.9f) * 0.85f to 520f + 700f * k
                // the out-breath starts at once and sinks away, lower and darker
                2 -> ((1f - k).pow(1.3f) * (k / 0.06f).coerceAtMost(1f)) to 900f - 600f * k
                else -> 0f to 400f
            }
            for (i in buf.indices) {
                fade += ((if (alive()) volume else 0f) - fade) * fadeRate
                env += (targetEnv - env) * smooth
                fc += (targetFc - fc) * smooth
                val noise = rnd.nextFloat() * 2f - 1f
                hi += (noise - hi) * (1f - exp(-2f * PI.toFloat() * fc / sr))
                lo += (noise - lo) * loCoef
                val s = (hi - lo) * env * fade * 1.4f
                buf[i] = (s * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            }
            track.write(buf, 0, buf.size)
        }
        track.stop()
        track.release()
    }

    companion object {
        private const val FADE_QUICK = 0.0005f
        private const val FADE_SLOW = 0.00004f
    }
}
