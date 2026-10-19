package com.grungert.nebo.sky

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Colours of the world at one moment of the day. Each colour is r, g, b in 0..1. */
class Palette(
    val skyTop: FloatArray, val skyMid: FloatArray, val skyHor: FloatArray,
    val sh: FloatArray, val md: FloatArray, val lt: FloatArray, val rim: FloatArray,
    val sun: FloatArray, val gTop: FloatArray, val gBot: FloatArray,
    val stars: Float,
)

private fun c(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

/** Night → dawn → morning → day → golden hour → sunset → dusk → night, keyed by hour. */
object DayCycle {
    private val NIGHT = Palette(c(0.02f, 0.04f, 0.12f), c(0.05f, 0.09f, 0.22f), c(0.12f, 0.17f, 0.33f),
        c(0.07f, 0.09f, 0.18f), c(0.16f, 0.20f, 0.34f), c(0.36f, 0.42f, 0.60f), c(0.70f, 0.78f, 0.95f),
        c(0.75f, 0.82f, 1.00f), c(0.10f, 0.18f, 0.20f), c(0.03f, 0.06f, 0.09f), 1f)
    private val DAWN = Palette(c(0.20f, 0.27f, 0.52f), c(0.62f, 0.55f, 0.70f), c(1.00f, 0.70f, 0.55f),
        c(0.42f, 0.40f, 0.60f), c(0.88f, 0.70f, 0.74f), c(1.00f, 0.88f, 0.78f), c(1.00f, 0.80f, 0.60f),
        c(1.00f, 0.78f, 0.55f), c(0.45f, 0.52f, 0.38f), c(0.15f, 0.22f, 0.22f), 0.15f)
    private val MORNING = Palette(c(0.16f, 0.43f, 0.80f), c(0.42f, 0.68f, 0.92f), c(0.84f, 0.92f, 0.95f),
        c(0.55f, 0.62f, 0.82f), c(0.87f, 0.90f, 0.96f), c(1.00f, 0.98f, 0.94f), c(1.00f, 0.94f, 0.84f),
        c(1.00f, 0.95f, 0.85f), c(0.48f, 0.70f, 0.34f), c(0.17f, 0.40f, 0.22f), 0f)
    private val DAY = Palette(c(0.13f, 0.42f, 0.82f), c(0.36f, 0.66f, 0.93f), c(0.80f, 0.92f, 0.97f),
        c(0.55f, 0.62f, 0.83f), c(0.86f, 0.90f, 0.97f), c(1.00f, 0.99f, 0.96f), c(1.00f, 0.94f, 0.82f),
        c(1.00f, 0.97f, 0.88f), c(0.47f, 0.70f, 0.33f), c(0.17f, 0.40f, 0.22f), 0f)
    private val GOLDEN = Palette(c(0.15f, 0.36f, 0.72f), c(0.50f, 0.66f, 0.86f), c(0.98f, 0.86f, 0.66f),
        c(0.52f, 0.54f, 0.74f), c(0.94f, 0.86f, 0.80f), c(1.00f, 0.93f, 0.78f), c(1.00f, 0.82f, 0.55f),
        c(1.00f, 0.85f, 0.60f), c(0.58f, 0.66f, 0.30f), c(0.20f, 0.34f, 0.18f), 0f)
    private val SUNSET = Palette(c(0.17f, 0.20f, 0.46f), c(0.62f, 0.47f, 0.66f), c(1.00f, 0.66f, 0.45f),
        c(0.45f, 0.38f, 0.60f), c(0.90f, 0.66f, 0.68f), c(1.00f, 0.87f, 0.70f), c(1.00f, 0.75f, 0.45f),
        c(1.00f, 0.68f, 0.38f), c(0.62f, 0.50f, 0.32f), c(0.20f, 0.18f, 0.24f), 0f)
    private val DUSK = Palette(c(0.07f, 0.10f, 0.28f), c(0.22f, 0.25f, 0.48f), c(0.55f, 0.45f, 0.60f),
        c(0.18f, 0.19f, 0.34f), c(0.38f, 0.36f, 0.55f), c(0.62f, 0.55f, 0.72f), c(0.90f, 0.65f, 0.60f),
        c(0.85f, 0.60f, 0.65f), c(0.22f, 0.26f, 0.30f), c(0.07f, 0.09f, 0.14f), 0.4f)

    private val KEYS = listOf(
        0f to NIGHT, 4.8f to NIGHT, 6.0f to DAWN, 8.0f to MORNING, 12f to DAY, 15.5f to DAY,
        17.5f to GOLDEN, 19.0f to SUNSET, 20.3f to DUSK, 21.5f to NIGHT, 24f to NIGHT,
    )

    fun paletteAt(hour: Float): Palette {
        var i = 0
        while (i < KEYS.size - 2 && hour >= KEYS[i + 1].first) i++
        val (h0, a) = KEYS[i]
        val (h1, b) = KEYS[i + 1]
        val t = smooth(0f, 1f, (hour - h0) / (h1 - h0))
        return Palette(
            mix(a.skyTop, b.skyTop, t), mix(a.skyMid, b.skyMid, t), mix(a.skyHor, b.skyHor, t),
            mix(a.sh, b.sh, t), mix(a.md, b.md, t), mix(a.lt, b.lt, t), mix(a.rim, b.rim, t),
            mix(a.sun, b.sun, t), mix(a.gTop, b.gTop, t), mix(a.gBot, b.gBot, t),
            a.stars + (b.stars - a.stars) * t,
        )
    }

    /** Position on the sky arc in uv space (x 0..1 left to right, y 0 at the horizon). */
    private fun arc(s: Float) = floatArrayOf(0.08f + 0.84f * s, -0.06f + sin(PI * s).toFloat())
    fun sunPos(h: Float) = arc((h - 5.8f) / 13.8f)
    fun sunAmount(h: Float) = smooth(5.5f, 6.6f, h) * (1f - smooth(19.3f, 20.2f, h))

    /**
     * The moon rises about 50 minutes later each day: with the sun when it is new, as the sun sets when it is full,
     * around midnight at last quarter. In scene hours, so it keeps that order on the scene's own day.
     */
    fun moonRise(phase: Float) = ((19.4f + (phase - 0.5f) * 24f) % 24f + 24f) % 24f
    /** How far along its arc the moon is, 0 rising .. 1 setting; above 1 it is below the horizon. */
    private fun moonArc(h: Float, phase: Float) = (((h - moonRise(phase)) % 24f) + 24f) % 24f / 11f
    fun moonPos(h: Float, phase: Float = 0.5f) = arc(moonArc(h, phase))
    /**
     * How strongly the moon shows: while it is above the horizon, paler by day, and not at all in the days around new
     * moon, when the little that is lit stands next to the sun.
     */
    fun moonAmount(h: Float, phase: Float = 0.5f): Float {
        val s = moonArc(h, phase)
        if (s >= 1f) return 0f
        val up = smooth(0f, 0.05f, s) * (1f - smooth(0.95f, 1f, s))
        return up * (1f - 0.8f * sunAmount(h)) * smooth(0.01f, 0.08f, Astro.moonLit(phase))
    }

    fun periodName(h: Float): String = when {
        h < 5.4f -> "Noć"
        h < 7.6f -> "Zora"
        h < 17.8f -> "Dan"
        h < 19.7f -> "Zalazak"
        h < 21.1f -> "Sumrak"
        else -> "Noć"
    }

    fun clockHour(): Float {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f + c.get(Calendar.SECOND) / 3600f
    }

    /** Direction towards the light for the cloud and tree shading. */
    fun lightDir(h: Float, phase: Float = 0.5f): FloatArray {
        fun dir(p: FloatArray) = floatArrayOf((p[0] - 0.5f) * 1.4f, max(-0.3f, p[1] - 0.3f) + 0.15f, 0.35f)
        val sun = dir(sunPos(h))
        val moon = dir(moonPos(h, phase))
        val a = sunAmount(h)
        val v = mix(moon, sun, if (a > 0.001f) clamp01(a * 1.5f) else 0f)
        val l = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-4f)
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }
}

fun clamp01(x: Float) = min(1f, max(0f, x))
fun smooth(a: Float, b: Float, x: Float): Float {
    val t = clamp01((x - a) / (b - a))
    return t * t * (3 - 2 * t)
}
fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(a.size) { a[it] + (b[it] - a[it]) * t }
