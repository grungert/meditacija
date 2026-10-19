package com.grungert.nebo.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.grungert.nebo.data.Settings
import com.grungert.nebo.sky.BreathEngine
import com.grungert.nebo.sky.DayCycle
import com.grungert.nebo.sky.SceneFlags
import com.grungert.nebo.sky.Scenery
import com.grungert.nebo.sky.Seasons
import com.grungert.nebo.sky.argb
import com.grungert.nebo.sky.mix
import com.grungert.nebo.sky.scale
import kotlin.math.sin
import kotlin.random.Random

/**
 * A still of the app's sky for the widget, as it is right now: the colours of this hour, season and weather,
 * the sun or the moon, a couple of painted clouds, the distant ridges, and the meadow with its silver grass,
 * drawn by the same code as in the app. The sky shader cannot run in a widget, so its parts are painted simply here.
 */
object WidgetArt {
    fun render(settings: Settings, wPx: Int, hPx: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = wPx.toFloat(); val h = hPx.toFloat()
        val u = density
        val hour = settings.timeMode.toFloatOrNull() ?: com.grungert.nebo.sky.Astro.sceneHourNow()
        val phase = com.grungert.nebo.sky.Astro.moonPhase()
        val season = Seasons.season(settings.seasonMode)
        val weather = Seasons.weather(settings.weatherMode, season)
        val pal = Seasons.apply(DayCycle.paletteAt(hour), season, weather)
        val through = Seasons.sunThrough(weather)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // rounded like the other widgets on the home screen
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w, h), 28 * u, 28 * u, Path.Direction.CW) }
        c.clipPath(clip)

        val horizon = h * (1 - 0.115f)
        // the sky: horizon colour low, deepening upwards
        paint.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(argb(pal.skyTop, 1f), argb(pal.skyMid, 1f), argb(pal.skyHor, 1f)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // stars
        if (pal.stars > 0.05f) {
            val r = Random(5)
            repeat(70) {
                paint.color = android.graphics.Color.argb((255 * pal.stars * (0.3f + 0.7f * r.nextFloat())).toInt(), 225, 232, 255)
                c.drawCircle(r.nextFloat() * w, r.nextFloat() * horizon * 0.85f, (0.6f + r.nextFloat() * 1.1f) * u, paint)
            }
        }

        // the sun or the moon on its arc
        fun disc(pos: FloatArray, amount: Float, col: FloatArray, radius: Float, glow: Float) {
            if (amount < 0.02f) return
            val x = w * pos[0]
            // kept low in the picture, below the words of the widget
            val y = horizon - 0.12f * h - pos[1].coerceIn(0f, 1f) * h * 0.22f
            paint.shader = RadialGradient(x, y, radius * glow, intArrayOf(argb(col, 0.45f * amount), argb(col, 0f)), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, radius * glow, paint)
            paint.shader = null
            paint.color = argb(mix(col, floatArrayOf(1f, 1f, 1f), 0.6f), amount)
            c.drawCircle(x, y, radius, paint)
        }
        disc(DayCycle.sunPos(hour), DayCycle.sunAmount(hour) * through, pal.sun, 13 * u, 6f)
        disc(DayCycle.moonPos(hour, phase), DayCycle.moonAmount(hour, phase) * through, floatArrayOf(0.98f, 0.96f, 0.88f), 10 * u, 4f)

        // two painted cumulus: lobes in the shadow tone, lit lobes over them towards the top
        fun cloud(cx: Float, cy: Float, s: Float, seed: Int) {
            val r = Random(seed)
            val lobes = List(7) { floatArrayOf((r.nextFloat() - 0.5f) * 2.4f * s, -r.nextFloat() * 0.5f * s, (0.45f + 0.4f * r.nextFloat()) * s) }
            paint.color = argb(mix(pal.md, pal.skyHor, 0.25f), 0.95f)
            for (l in lobes) c.drawCircle(cx + l[0], cy + l[1], l[2], paint)
            c.drawRect(cx - 1.6f * s, cy, cx + 1.6f * s, cy + 0.35f * s, paint)
            paint.color = argb(pal.lt, 0.95f)
            for (l in lobes) c.drawCircle(cx + l[0] - l[2] * 0.12f, cy + l[1] - l[2] * 0.2f, l[2] * 0.78f, paint)
        }
        val cover = if (weather == com.grungert.nebo.sky.Weather.CLEAR) 1f else 1.4f
        cloud(w * 0.78f, horizon * 0.62f, 26 * u * cover, 3)
        cloud(w * 0.22f, horizon * 0.80f, 18 * u * cover, 8)

        // distant ridges, hazed into the sky
        fun ridge(base: Float, amp: Float, freq: Float, col: FloatArray, seed: Float) {
            val p = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w + 4) {
                    val k = x / h
                    lineTo(x, h * (1 - base - amp * (0.55f * (0.5f + 0.5f * sin(k * freq + seed)) + 0.45f * (0.5f + 0.5f * sin(k * freq * 2.7f + seed * 1.7f)))))
                    x += 4 * u
                }
                lineTo(w, h); close()
            }
            paint.color = argb(col, 1f)
            c.drawPath(p, paint)
        }
        val haze = mix(pal.skyHor, pal.skyMid, 0.2f)
        ridge(0.16f, 0.09f, 2.6f, mix(haze, mix(pal.skyMid, pal.gBot, 0.4f), 0.42f), 4f)
        ridge(0.135f, 0.06f, 3.4f, mix(haze, mix(pal.gBot, pal.gTop, 0.45f), 0.62f), 9f)

        // the ground of the meadow; the blades are drawn over it by the app's own scenery
        paint.shader = LinearGradient(0f, horizon, 0f, h, intArrayOf(argb(pal.gTop, 1f), argb(scale(pal.gBot, 0.55f), 1f)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, w, h, paint)
        paint.shader = null
        // the meadow is made for a whole phone screen; in a widget a fraction of the size keeps it in proportion
        Scenery(u * 0.42f).apply { resize(w, h) }.draw(c, BreathEngine(), pal, DayCycle.lightDir(hour), 3f, 0f,
            SceneFlags(showCircle = false, showLandmarks = false, season = season, weather = weather, foreground = false), 0f)

        // a soft shade at the top keeps the white words readable in any light
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.6f, intArrayOf(android.graphics.Color.argb(80, 20, 34, 63), 0), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * 0.6f, paint)
        paint.shader = null
        return bmp
    }

}
