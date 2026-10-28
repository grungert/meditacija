package com.grungert.nebo.sky

import kotlin.math.floor
import kotlin.math.pow

/**
 * The rain's mood, the same for whoever asks at the same moment: the sky, the drops on the glass and the sound all
 * follow it. It wanders on its own between a drizzle and a downpour, over a minute or two, with shorter swells on top;
 * when it pours, now and then lightning strikes somewhere beyond the hills, and its thunder comes a few seconds later,
 * the further the later and the softer. Time is in seconds of any steady clock ([now]).
 */
object Storm {
    /** Thunder and lightning may come with the heaviest rain (a setting: some would rather sit without it). */
    @Volatile var thunder = true

    fun now(): Double = android.os.SystemClock.elapsedRealtime() / 1000.0

    /** How hard it rains, 0.12 (a drizzle) to 1 (a downpour). */
    fun intensity(t: Double): Float {
        val slow = noise(t, 80.0, 1)
        val swell = noise(t, 13.0, 2)
        return (0.12f + 0.88f * (0.68f * slow + 0.32f * swell).pow(1.5f)).coerceIn(0.12f, 1f)
    }

    /** One strike: when, how far (0 near, 1 far), where across the sky (0..1), and when its thunder arrives. */
    class Strike(val at: Double, val far: Float, val x: Float, val seed: Int) {
        val heard: Double get() = at + 1.2 + 5.0 * far   // sound is slow: about three seconds a kilometre
        val strength: Float get() = 1f - 0.65f * far
    }

    private const val SLOT = 28.0

    /** The strike in the slot of time around [t], if there is one: only in heavy rain, and never two close together. */
    fun strikeIn(slot: Long): Strike? {
        if (!thunder) return null
        val at = slot * SLOT + 3.0 + hash(slot, 4) * (SLOT - 6.0)
        val heavy = ((intensity(at) - 0.55f) / 0.3f).coerceIn(0f, 1f)
        if (hash(slot, 3) >= 0.75f * heavy) return null
        return Strike(at, hash(slot, 5), 0.1f + 0.8f * hash(slot, 6), (slot % 100_000).toInt())
    }

    fun slotOf(t: Double): Long = floor(t / SLOT).toLong()

    /** The last strike at or before [t] (within a slot or so), for the flash and the thunder that follows it. */
    fun lastStrike(t: Double): Strike? {
        val s = slotOf(t)
        return strikeIn(s)?.takeIf { it.at <= t } ?: strikeIn(s - 1)
    }

    /** The light of a strike, 0..1, a moment after it: a flash, a flicker, a fainter one, then dark again. */
    fun flash(t: Double): Float {
        val s = lastStrike(t) ?: return 0f
        val d = (t - s.at).toFloat()
        if (d < 0f || d > 1.6f) return 0f
        fun pulse(at: Float, width: Float, h: Float) = if (d < at) 0f else h * kotlin.math.exp(-(d - at) / width)
        return s.strength * (pulse(0f, 0.16f, 1f) + pulse(0.22f, 0.12f, 0.75f) + pulse(0.55f, 0.25f, 0.4f)).coerceAtMost(1f)
    }

    /**
     * A gust of wind, 0..1: now and then (more often in heavy rain) the wind rises over a second or two, holds, and dies
     * away; in between it is quieter. Grass, leaves and the sound of the wind all follow it.
     */
    fun gust(t: Double): Float {
        val s = floor(t / GUST_SLOT).toLong()
        return maxOf(gustIn(s, t), gustIn(s - 1, t))
    }

    private const val GUST_SLOT = 22.0

    private fun gustIn(slot: Long, t: Double): Float {
        val at = slot * GUST_SLOT + hash(slot, 7) * GUST_SLOT * 0.6
        val chance = 0.45f + 0.4f * intensity(at)
        if (hash(slot, 8) >= chance) return 0f
        val d = (t - at).toFloat()
        val len = 4f + 6f * hash(slot, 9)
        if (d < 0f || d > len + 3.5f) return 0f
        val rise = (d / 1.6f).coerceAtMost(1f)
        val fall = (1f - (d - len) / 3.5f).coerceIn(0f, 1f)
        val e = minOf(rise, fall)
        return (0.45f + 0.55f * hash(slot, 10)) * e * e * (3 - 2 * e)
    }

    // smooth wandering noise: a random height at every step of [period], eased from one to the next
    private fun noise(t: Double, period: Double, salt: Int): Float {
        val x = t / period
        val i = floor(x).toLong()
        val f = (x - i).toFloat()
        val e = f * f * (3 - 2 * f)
        return hash(i, salt) * (1 - e) + hash(i + 1, salt) * e
    }

    private fun hash(i: Long, salt: Int): Float {
        var h = i * 0x9E3779B97F4A7C15uL.toLong() + salt * 0x632BE59BD9B4E019L
        h = (h xor (h ushr 31)) * 0x94D049BB133111EBuL.toLong()
        h = h xor (h ushr 29)
        return ((h ushr 40).toFloat() / (1L shl 24).toFloat())
    }
}
