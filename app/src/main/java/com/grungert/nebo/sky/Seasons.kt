package com.grungert.nebo.sky

import java.util.Calendar
import kotlin.random.Random

enum class Season(val label: String) { SPRING("proljeće"), SUMMER("ljeto"), AUTUMN("jesen"), WINTER("zima") }
enum class Weather(val label: String) { CLEAR("vedro"), RAIN("kiša"), FOG("magla"), SNOW("snijeg") }

/** Season and weather, from the calendar or as chosen, and what they do to the world's colours. */
object Seasons {
    fun fromCalendar(c: Calendar = Calendar.getInstance()): Season = when (c.get(Calendar.MONTH)) {
        Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Season.SPRING
        Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> Season.SUMMER
        Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> Season.AUTUMN
        else -> Season.WINTER
    }

    /** The day's weather when left to chance: the same all day, mostly clear. */
    fun weatherForToday(season: Season, c: Calendar = Calendar.getInstance()): Weather {
        val r = Random(c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)).nextFloat()
        return when {
            season == Season.WINTER && r < 0.30f -> Weather.SNOW
            season != Season.WINTER && r < 0.14f -> Weather.RAIN
            r > 0.88f -> Weather.FOG
            else -> Weather.CLEAR
        }
    }

    fun season(mode: String): Season = Season.entries.firstOrNull { it.name.equals(mode, true) } ?: fromCalendar()
    fun weather(mode: String, season: Season): Weather =
        Weather.entries.firstOrNull { it.name.equals(mode, true) } ?: weatherForToday(season)

    private fun tint(c: FloatArray, to: FloatArray, k: Float) = mix(c, to, k)

    /** Recolours the palette for the season and weather; the time of day stays in charge of light and dark. */
    fun apply(p: Palette, season: Season, weather: Weather): Palette {
        val night = p.stars
        val day = 1f - night
        var gTop = p.gTop; var gBot = p.gBot
        var skyTop = p.skyTop; var skyMid = p.skyMid; var skyHor = p.skyHor
        var sh = p.sh; var md = p.md; var lt = p.lt; var rim = p.rim
        when (season) {
            Season.SPRING -> { gTop = tint(gTop, floatArrayOf(0.56f, 0.80f, 0.38f), 0.45f * day); gBot = tint(gBot, floatArrayOf(0.20f, 0.46f, 0.24f), 0.35f * day) }
            Season.SUMMER -> {}
            Season.AUTUMN -> { gTop = tint(gTop, floatArrayOf(0.80f, 0.64f, 0.30f), 0.55f * day + 0.2f * night); gBot = tint(gBot, floatArrayOf(0.38f, 0.30f, 0.16f), 0.5f * day + 0.2f * night) }
            Season.WINTER -> {
                // snow on the meadow keeps a cool glow even at night
                gTop = tint(gTop, floatArrayOf(0.93f, 0.95f, 1.0f), 0.85f * day + 0.55f * night)
                gBot = tint(gBot, floatArrayOf(0.64f, 0.70f, 0.84f), 0.8f * day + 0.45f * night)
                skyHor = tint(skyHor, floatArrayOf(0.92f, 0.94f, 0.98f), 0.25f * day)
                lt = tint(lt, floatArrayOf(0.97f, 0.98f, 1.0f), 0.4f)
            }
        }
        when (weather) {
            Weather.CLEAR -> {}
            Weather.RAIN, Weather.SNOW -> {
                val grey = if (weather == Weather.SNOW) floatArrayOf(0.74f, 0.77f, 0.82f) else floatArrayOf(0.52f, 0.57f, 0.63f)
                val k = 0.6f * day + 0.3f * night
                skyTop = tint(skyTop, scale(grey, 0.8f), k); skyMid = tint(skyMid, grey, k); skyHor = tint(skyHor, scale(grey, 1.12f), k)
                sh = tint(sh, scale(grey, 0.72f), 0.7f); md = tint(md, grey, 0.7f); lt = tint(lt, scale(grey, 1.2f), 0.6f); rim = tint(rim, lt, 0.7f)
                gTop = scale(gTop, if (weather == Weather.RAIN) 0.82f else 1f); gBot = scale(gBot, if (weather == Weather.RAIN) 0.85f else 1f)
            }
            Weather.FOG -> {
                val fog = floatArrayOf(0.86f, 0.88f, 0.90f)
                val k = 0.6f * day + 0.2f * night
                skyTop = tint(skyTop, fog, k * 0.5f); skyMid = tint(skyMid, fog, k); skyHor = tint(skyHor, fog, k + 0.1f)
                sh = tint(sh, fog, 0.35f); md = tint(md, fog, 0.4f)
            }
        }
        return Palette(skyTop, skyMid, skyHor, sh, md, lt, rim, p.sun, gTop, gBot, p.stars)
    }

    /** How much of the sun and its rays get through the weather. */
    fun sunThrough(w: Weather) = when (w) { Weather.CLEAR -> 1f; Weather.FOG -> 0.55f; Weather.SNOW -> 0.35f; Weather.RAIN -> 0.2f }

    /** Between two palettes, [k] of the way from [a] to [b]. */
    fun blend(a: Palette, b: Palette, k: Float) = Palette(
        mix(a.skyTop, b.skyTop, k), mix(a.skyMid, b.skyMid, k), mix(a.skyHor, b.skyHor, k), mix(a.sh, b.sh, k), mix(a.md, b.md, k),
        mix(a.lt, b.lt, k), mix(a.rim, b.rim, k), mix(a.sun, b.sun, k), mix(a.gTop, b.gTop, k), mix(a.gBot, b.gBot, k),
        a.stars + (b.stars - a.stars) * k,
    )

    /** Extra cloud cover for the shader. */
    fun cover(w: Weather) = when (w) { Weather.CLEAR -> 0f; Weather.FOG -> 0.04f; Weather.SNOW -> 0.14f; Weather.RAIN -> 0.18f }
}
