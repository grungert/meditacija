package com.grungert.nebo.sky

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The real sky over the person: when the sun rises and sets today, and how full the moon is. Without asking for the
 * location, the place is Podgorica; anywhere in the region the times are within a few minutes.
 */
object Astro {
    const val LAT = 42.44
    const val LON = 19.26

    /** The scene's own hours at which the sun touches the horizon (see [DayCycle.sunPos]), and how night falls around them. */
    private const val SCENE_RISE = 5.8f
    private const val SCENE_SET = 19.6f
    private const val SCENE_NIGHT_ENDS = 4.8f   // the last of the night before dawn
    private const val SCENE_NIGHT_BEGINS = 21.5f
    /** Real twilight around sunrise and sunset, in hours: the sky is fully dark about this long after sunset. */
    private const val DAWN_REAL = 1.2f
    private const val DUSK_REAL = 1.6f

    /**
     * Sunrise and sunset on that day, in local clock hours (NOAA's approximate equations, with the sun's disc and
     * refraction at the horizon), for the time zone of [day] (so with summer time when it applies).
     */
    fun sunTimes(day: Calendar, lat: Double = LAT, lon: Double = LON): Pair<Float, Float> {
        val n = day.get(Calendar.DAY_OF_YEAR)
        val g = 2 * PI / 365 * (n - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val phi = Math.toRadians(lat)
        val ha = Math.toDegrees(acos((cos(Math.toRadians(90.833)) / (cos(phi) * cos(decl)) - tan(phi) * tan(decl)).coerceIn(-1.0, 1.0)))
        val noon = (day.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0) }
        val offset = day.timeZone.getOffset(noon.timeInMillis) / 60_000.0
        val rise = (720 - 4 * (lon + ha) - eq + offset) / 60
        val set = (720 - 4 * (lon - ha) - eq + offset) / 60
        return rise.toFloat() to set.toFloat()
    }

    /**
     * The scene hour for a real clock hour: the scene's palettes are painted for one day with the sun up from 5.8
     * to 19.6, so the real day is stretched or squeezed onto it. Daylight maps onto daylight, twilight keeps its real
     * length, and the night between takes what is left. A short June night and a long December one both pass through
     * dusk, night and dawn, at the times they really happen.
     */
    fun sceneHour(clock: Float, rise: Float, set: Float): Float {
        fun lerp(x: Float, x0: Float, x1: Float, y0: Float, y1: Float) = y0 + (y1 - y0) * (x - x0) / (x1 - x0)
        val dawn = rise - DAWN_REAL
        val dark = set + DUSK_REAL
        val h = if (clock < dawn) clock + 24f else clock   // after midnight is still the same night
        val scene = when {
            h < rise -> lerp(h, dawn, rise, SCENE_NIGHT_ENDS, SCENE_RISE)
            h < set -> lerp(h, rise, set, SCENE_RISE, SCENE_SET)
            h < dark -> lerp(h, set, dark, SCENE_SET, SCENE_NIGHT_BEGINS)
            else -> lerp(h, dark, dawn + 24f, SCENE_NIGHT_BEGINS, SCENE_NIGHT_ENDS + 24f)
        }
        return ((scene % 24f) + 24f) % 24f
    }

    /** The scene hour of the real sky right now. */
    fun sceneHourNow(now: Calendar = Calendar.getInstance()): Float {
        val (rise, set) = sunTimes(now)
        val clock = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f + now.get(Calendar.SECOND) / 3600f
        return sceneHour(clock, rise, set)
    }

    private const val SYNODIC = 29.530588853
    /** A new moon to count from: 6 January 2000, 18:14 UTC. */
    private const val NEW_MOON_MS = 947_182_440_000L

    /** Where the moon is in its month: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter. Good to about half a day. */
    fun moonPhase(atMs: Long = System.currentTimeMillis()): Float {
        val days = (atMs - NEW_MOON_MS) / 86_400_000.0
        return (((days / SYNODIC) % 1.0 + 1.0) % 1.0).toFloat()
    }

    /** How much of the moon's face is lit, 0..1. */
    fun moonLit(phase: Float): Float = ((1 - cos(2 * PI * phase)) / 2).toFloat()

}
