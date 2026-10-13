package com.grungert.nebo.sky

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** What the overlay should show this frame. */
class SceneFlags(
    val showCircle: Boolean,
    val showLandmarks: Boolean,
    val season: Season = Season.SUMMER,
    val weather: Weather = Weather.CLEAR,
    val foreground: Boolean = true,
)

/**
 * Everything painted over the sky shader with a 2D canvas: the meadow of individual grass blades,
 * silver-grass plumes, the things met along the walk, leaves, fireflies, thought clouds and
 * the breathing circle with its motes of light. A port of the browser prototype's overlay.
 */
class Scenery(private val u: Float) {
    private var W = 0f
    private var H = 0f
    private val rand = Mulberry(7)

    private class Blade(val x: Float, val off: Float, val frac: Float, val h: Float, val w: Float, val lean: Float,
                        val flex: Float, val ph: Float, val d: Float, val tone: Float)
    private class Plume(val x: Float, val frac: Float, val h: Float, val flex: Float, val ph: Float)
    private class Front(val x: Float, val h: Float, val w: Float, val lean: Float, val ph: Float)
    private class Leaf(var x: Float = -1f, var y: Float = 0f, var s: Float = 0f, var rot: Float = 0f, var vr: Float = 0f,
                       var bob: Float = 0f, var delay: Float = 0f, var hue: Float = 0f)
    private class Fly(val x: Float, val y: Float, val ph: Float, val sp: Float, val s: Float)
    private class Mote(var a: Float, val spd: Float, val off: Float, val loose: Float, val s: Float, val tw: Float)
    private class Ripple(val t0: Float, val strong: Boolean)
    private class Landmark(val type: String, val f: Float, val d: Float, val wx: Float, val seed: Float,
                           val items: List<FloatArray> = emptyList())
    private class ThoughtCloud(var x: Float, var y: Float, val born: Float, val size: Float, val seed: Int)

    private var blades = emptyList<Blade>()
    private var plumes = emptyList<Plume>()
    private var front = emptyList<Front>()
    // per depth band: where its tips and its roots lie on screen, for the root-to-tip colour ramp
    private val bandTop = FloatArray(6)
    private val bandBottom = FloatArray(6)
    private val stemX = FloatArray(13)
    private val stemY = FloatArray(13)
    private val strand = FloatArray(6)
    private val lensP = FloatArray(4)
    private val flyP = FloatArray(4)
    private val dropClip = Path()
    private val leaves = List(12) { i -> Leaf(delay = i * 1.8f + rand() * 2f) }
    private var season = Season.SUMMER
    private var weather = Weather.CLEAR
    private val drops = List(140) { floatArrayOf(rand(), rand(), 0.6f + rand() * 0.8f) }   // x, y (0..1), speed
    private val flakes = List(110) { floatArrayOf(rand(), rand(), 0.5f + rand(), rand() * 6.28f) } // x, y, size, phase
    private val flies = List(24) { Fly(rand(), rand(), rand() * 6.28f, 0.4f + rand() * 0.8f, 0.6f + rand() * 0.8f) }
    private val motes = List(44) { Mote(rand() * 6.28f, (0.05f + rand() * 0.14f) * if (rand() < 0.7f) 1 else -1,
        rand() * 2 - 1, rand(), 0.5f + rand() * 1.1f, rand() * 6.28f) }
    private val ripples = ArrayList<Ripple>()
    private var landmarks = ArrayList<Landmark>()
    private var lmDist = 0.25f
    private var lmIdx = 0
    private val lmRand = Mulberry(23)
    private val thoughts = ArrayList<ThoughtCloud>()
    var thoughtCount = 0; private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bladePaths = List(12) { Path() }
    private val tmp = Path()
    private val moteSprite = sprite(255, 252, 240)
    private val flySprite = sprite(214, 255, 150)
    private val spriteRect = RectF()

    // -------- world geometry shared with the shader --------
    private var walk = 0f
    private var windPhase = 0f
    private fun hillFn(xw: Float) = 0.115f + 0.006f * sin(xw * 1.6f + 0.8f) + 0.003f * sin(xw * 3.7f + 2.0f)
    private fun crestX(sx: Float) = sx / H + walk * 0.55f
    private fun hillY(sx: Float) = H * (1 - hillFn(crestX(sx)))
    private fun gustAt(xw: Float): Float {
        val g = 0.5f + 0.5f * sin(xw * 3.0f + windPhase + 0.6f * sin(xw * 1.3f - windPhase * 0.3f))
        return g * g
    }

    fun resize(w: Float, h: Float) {
        if (w == W && h == H) return
        W = w; H = h
        val r = Mulberry(11)
        val b = ArrayList<Blade>()
        var x = -12 * u
        while (x < W + 12 * u) {
            b += Blade(x, r() * 5 * u, 0f, (7 + r() * 13) * u, (1.6f + r()) * u, -0.08f + r() * 0.1f, 0.5f + r() * 0.5f, r() * 6.28f, 0f, r())
            x += (2.2f + r() * 2.2f) * u
        }
        val rows = 7
        for (k in 1..rows) {
            val d = k / rows.toFloat()
            val step = (5.5f - d * 2.2f) * u
            x = -30 * u
            while (x < W + 30 * u) {
                b += Blade(x, 0f, d * 0.92f + r() * 0.1f, (14 + 95 * d.pow(1.35f) * (0.6f + r() * 0.7f)) * u,
                    (2 + 5 * d) * u * (0.7f + r() * 0.6f), -0.12f + r() * 0.16f, 0.6f + r() * 0.6f, r() * 6.28f, d, r())
                x += step * (0.6f + r() * 0.8f)
            }
        }
        blades = b.sortedBy { it.frac }
        val heightScale = min(1f, H / (800 * u) + 0.4f)
        plumes = List(5) { Plume(W * (0.08f + 0.84f * r()), 0.35f + 0.55f * r(), (140 + 110 * r()) * u * heightScale, 0.9f + r() * 0.4f, r() * 6.28f) }
        front = List(9) { Front(W * r(), H * (0.09f + 0.10f * r()), (5 + 4 * r()) * u, -0.3f + 0.6f * r(), r() * 6.28f) }
        // the meadow is nearly level, so each depth band's tips and roots sit at about the same height across the screen
        bandTop.fill(H); bandBottom.fill(0f)
        val y0 = H * (1 - 0.115f)
        for (bl in blades) {
            val i = min(5, floor(bl.d * 6).toInt())
            val root = if (bl.d == 0f) y0 + bl.off else y0 + (H - y0) * bl.frac
            bandTop[i] = min(bandTop[i], root - bl.h * 0.8f)
            bandBottom[i] = max(bandBottom[i], root)
        }
    }

    fun ripple(t: Float, strong: Boolean) { ripples += Ripple(t, strong) }

    fun resetWalkSpawns() { lmDist = min(lmDist, 0.25f) }

    private var now = 0f

    /** A thought noticed: a small cloud forms where the sky was touched and the wind carries it off. */
    fun addThought(x: Float, y: Float) {
        thoughts += ThoughtCloud(x, y, now, (26 + rand() * 18) * u, (rand() * 1000).toInt())
        thoughtCount++
    }

    fun resetThoughts() { thoughts.clear(); thoughtCount = 0 }

    fun stepWalk(dCrest: Float, walkOn: Boolean) {
        if (!walkOn || H == 0f) return
        lmDist -= dCrest
        if (lmDist <= 0f) {
            val type = LM_ORDER[lmIdx++ % LM_ORDER.size]
            val depth = LM_DEPTH.getValue(type)
            val f = 0.55f + 0.45f * depth
            val items = if (type == "flowers") List(60) {
                floatArrayOf((lmRand() - 0.5f) * 0.6f, 0.25f + lmRand() * 0.65f, floor(lmRand() * 3), 3.5f + lmRand() * 3.5f)
            } else emptyList()
            landmarks += Landmark(type, f, depth, walk * f + W / H + 0.35f, lmRand(), items)
            lmDist = 1.4f + lmRand() * 1.2f
        }
        landmarks.removeAll { (it.wx - walk * it.f) * H < -1.4f * H }
    }

    fun draw(c: Canvas, e: BreathEngine, pal: Palette, light: FloatArray, t: Float, dt: Float, flags: SceneFlags, walkAmt: Float) {
        walk = e.walk
        windPhase = e.windPhase
        now = t
        season = flags.season; weather = flags.weather
        drawLeaves(c, pal, dt, e.wind, e.reducedMotion)
        if (walkAmt > 0.02f && flags.showLandmarks) drawLandmarks(c, pal, light, false, walkAmt, e.wind)
        drawMeadow(c, pal, t, e.wind, e.reducedMotion)
        if (walkAmt > 0.02f && flags.showLandmarks) drawLandmarks(c, pal, light, true, walkAmt, e.wind)
        if (flags.foreground) drawForeground(c, pal, t, e.wind, e.reducedMotion)
        if (season != Season.WINTER && weather != Weather.RAIN) drawFireflies(c, pal, t)
        when (weather) {
            Weather.RAIN -> drawRain(c, dt, e.wind)
            Weather.SNOW -> drawSnow(c, t, dt, e.wind)
            Weather.FOG -> drawFog(c, pal, t)
            Weather.CLEAR -> {}
        }
        drawThoughts(c, pal, t, dt, e.wind)
        if (flags.showCircle) drawCircle(c, e, pal, t, dt)
    }

    // -------- meadow --------
    private fun drawMeadow(c: Canvas, pal: Palette, t: Float, wind: Float, reduced: Boolean) {
        val amp = (0.18f + 0.42f * wind) * if (reduced) 0.4f else 1f
        val night = pal.stars
        val buckets = 6
        bladePaths.forEach { it.rewind() }
        val margin = 30 * u
        val span = W + 2 * margin
        val winter = season == Season.WINTER
        val blossoms = if (season == Season.SPRING) ArrayList<FloatArray>() else null
        for ((index, b) in blades.withIndex()) {
            if (winter && index % 9 < 5) continue // most grass lies under the snow
            val bx = wrapX(b.x, 0.55f + 0.45f * b.frac, margin, span)
            val y0 = hillY(bx)
            val by = if (b.d == 0f) y0 + b.off else y0 + (H - y0) * b.frac
            val g = gustAt(crestX(bx))
            val ang = b.lean - amp * g * b.flex * 1.1f + sin(t * 2.3f + b.ph) * 0.05f * b.flex
            val bh = if (winter) b.h * 0.5f else b.h
            val tx = bx + sin(ang) * bh
            val ty = by - cos(ang) * bh
            // the blade stays fairly upright low down and bends towards its tip
            val cx = bx + sin(ang * 0.25f) * bh * 0.55f
            val cy = by - cos(ang * 0.25f) * bh * 0.55f
            if (blossoms != null && index % 7 == 0 && b.d >= 0.28f) blossoms += floatArrayOf(tx, ty, b.d, (index / 7 % 3).toFloat())
            // where a gust bends the grass, its tips turn to the light: the wind shows as waves of brightness
            val shine = g > 0.55f && b.tone > 0.3f
            val p = bladePaths[min(buckets - 1, floor(b.d * buckets).toInt()) * 2 + if (shine) 1 else 0]
            // the nearest blades run on past the bottom edge, so no flat blade base shows against the ground
            val foot = if (b.d > 0.6f) max(by, H + 2 * u) else by
            p.moveTo(bx - b.w / 2, foot)
            p.lineTo(bx - b.w / 2, by)
            p.quadTo(cx - b.w * 0.25f, cy, tx, ty)
            p.quadTo(cx + b.w * 0.25f, cy, bx + b.w / 2, by)
            p.lineTo(bx + b.w / 2, foot)
            p.close()
        }
        paint.style = Paint.Style.FILL
        val white = floatArrayOf(1f, 1f, 1f)
        val glint = mix(mix(pal.sun, white, 0.6f), pal.gTop, night * 0.7f)
        for (i in 0 until buckets) {
            val d = i / (buckets - 1f)
            val base = mix(pal.gTop, pal.gBot, min(1f, d * 1.15f))
            val root = scale(base, 0.50f)
            val tip = mix(scale(base, 1.22f), pal.sun, 0.12f * (1 - night))
            val top = bandTop[i]; val bottom = max(bandBottom[i], top + 1f)
            paint.shader = LinearGradient(0f, top, 0f, bottom, intArrayOf(argb(tip, 1f), argb(base, 1f), argb(root, 1f)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(bladePaths[i * 2], paint)
            paint.shader = LinearGradient(0f, top, 0f, bottom, intArrayOf(argb(mix(tip, glint, 0.55f), 1f), argb(scale(base, 1.1f), 1f), argb(root, 1f)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(bladePaths[i * 2 + 1], paint)
        }
        paint.shader = null
        if (blossoms != null) for (bl in blossoms) {
            paint.color = argb(mix(SPRING_COLS[bl[3].toInt()], pal.gBot, 0.6f * night), 0.95f)
            c.drawCircle(bl[0], bl[1], (1.8f + 2.6f * bl[2]) * u, paint)
        }
        if (winter) return
        drawPlumes(c, pal, t, amp, margin, span)
    }

    /**
     * Silver grass: a slender stem that bends more towards the top, and a plume of fine warm strands
     * that hang from it on the lee side and tremble in the wind.
     */
    private fun drawPlumes(c: Canvas, pal: Palette, t: Float, amp: Float, margin: Float, span: Float) {
        val night = pal.stars
        for (s in plumes) {
            val bend = plumeStem(s, t, amp, margin, span)
            tmp.rewind(); tmp.moveTo(stemX[0], stemY[0])
            for (i in 1..12) tmp.lineTo(stemX[i], stemY[i])
            stroke.color = argb(plumeStemCol(pal), 0.95f); stroke.strokeWidth = 1.5f * u
            c.drawPath(tmp, stroke)
            tmp.rewind()
            for (j in 0 until 36) {
                plumeStrand(s, bend, j, t)
                tmp.moveTo(strand[0], strand[1]); tmp.quadTo(strand[2], strand[3], strand[4], strand[5])
            }
            stroke.color = argb(plumeCol(pal), 0.62f * (1 - 0.5f * night)); stroke.strokeWidth = 1.1f * u
            c.drawPath(tmp, stroke)
        }
    }

    private fun plumeStemCol(pal: Palette) = scale(mix(pal.gTop, pal.gBot, 0.5f), 0.8f)
    private fun plumeCol(pal: Palette) = mix(mix(pal.sun, floatArrayOf(1f, 0.95f, 0.82f), 0.55f), pal.gBot, pal.stars * 0.6f)

    /**
     * Silver grass: a slender stem that bends more towards the top (into [stemX], [stemY]);
     * returns how far it bends.
     */
    private fun plumeStem(s: Plume, t: Float, amp: Float, margin: Float, span: Float): Float {
        val sx = wrapX(s.x, 0.55f + 0.45f * s.frac, margin, span)
        val sy0 = hillY(sx)
        val sy = sy0 + (H - sy0) * s.frac
        val g = gustAt(crestX(sx))
        val bend = -0.08f - amp * 0.75f * g * s.flex + sin(t * 1.7f + s.ph) * 0.05f
        for (i in 0..12) {
            val q = i / 12f
            val a = bend * q * q
            stemX[i] = sx + sin(a) * s.h * q
            stemY[i] = sy - cos(a * 0.6f) * s.h * q
        }
        return bend
    }

    /**
     * One of the fine strands of the plume, swept downwind from the top third of the stem,
     * longest in the plume's middle and sagging a little: start, control and end point into [strand].
     */
    private fun plumeStrand(s: Plume, bend: Float, j: Int, t: Float) {
        val size = (s.h / (200 * u)).coerceIn(0.7f, 1.3f)
        val q = 0.66f + 0.34f * j / 35f
        val f = q * 12
        val i = min(11, floor(f).toInt())
        val k = f - i
        val px = stemX[i] + (stemX[i + 1] - stemX[i]) * k
        val py = stemY[i] + (stemY[i + 1] - stemY[i]) * k
        val spread = ((j * 37) % 11) / 10f - 0.5f
        val a = bend * q * 1.6f - 0.25f - 0.35f * (1 - q) + spread * 0.35f + sin(t * 3.1f + j * 0.7f + s.ph) * 0.06f
        val len = (10 + 22 * sin(PI.toFloat() * (q - 0.66f) / 0.36f)) * u * size
        val ex = px + sin(a) * len
        val ey = py - cos(a) * len
        strand[0] = px; strand[1] = py
        strand[2] = (px + ex) / 2; strand[3] = (py + ey) / 2 + len * 0.18f
        strand[4] = ex; strand[5] = ey + len * 0.12f
    }

    /** A few large, dark blades right in front of the viewer frame the meadow and give it depth. */
    private fun drawForeground(c: Canvas, pal: Palette, t: Float, wind: Float, reduced: Boolean) {
        if (season == Season.WINTER) return
        val amp = (0.18f + 0.42f * wind) * if (reduced) 0.4f else 1f
        tmp.rewind()
        for (f in front) {
            val g = gustAt(crestX(f.x))
            val ang = f.lean - amp * 0.7f * g + sin(t * 1.3f + f.ph) * 0.05f
            val by = H + 20 * u
            val tx = f.x + sin(ang) * f.h
            val ty = by - cos(ang) * f.h
            val cx = f.x + sin(ang * 0.25f) * f.h * 0.55f
            val cy = by - cos(ang * 0.25f) * f.h * 0.55f
            tmp.moveTo(f.x - f.w / 2, by)
            tmp.quadTo(cx - f.w * 0.25f, cy, tx, ty)
            tmp.quadTo(cx + f.w * 0.25f, cy, f.x + f.w / 2, by)
            tmp.close()
        }
        val dark = scale(pal.gBot, 0.42f)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, H * 0.72f, 0f, H, intArrayOf(argb(scale(pal.gBot, 0.62f), 1f), argb(dark, 1f)), null, Shader.TileMode.CLAMP)
        c.drawPath(tmp, paint)
        // and the very bottom of the picture settles into shade
        paint.shader = LinearGradient(0f, H * 0.8f, 0f, H, intArrayOf(argb(dark, 0f), argb(dark, 0.35f)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, H * 0.8f, W, H, paint)
        paint.shader = null
    }

    private fun wrapX(x0: Float, f: Float, margin: Float, span: Float): Float {
        var x = (x0 + margin - walk * f * H) % span
        if (x < 0) x += span
        return x - margin
    }

    // -------- leaves and fireflies --------
    private fun drawLeaves(c: Canvas, pal: Palette, dt: Float, wind: Float, reduced: Boolean) {
        val amt = (1 - pal.stars) * if (reduced) 0f else 1f
        if (amt < 0.02f || season == Season.WINTER || weather == Weather.RAIN) return
        val active = when (season) { Season.AUTUMN -> 12; Season.SPRING -> 9; else -> 5 }
        for ((li, l) in leaves.withIndex()) {
            if (li >= active) continue
            if (l.delay > 0) {
                l.delay -= dt
                if (l.delay <= 0) {
                    l.x = W + (20 + rand() * 120) * u; l.y = H * (0.18f + rand() * 0.55f)
                    l.s = (5 + rand() * 6) * u; l.rot = rand() * 6.28f; l.vr = (rand() - 0.5f) * 5; l.bob = rand() * 6.28f; l.hue = rand()
                }
                continue
            }
            if (l.x < -40 * u) { l.delay = 1 + rand() * 5; continue }
            l.x -= (55 + 120 * wind) * u * dt
            l.bob += dt * 1.6f
            l.y += sin(l.bob) * 18 * u * dt
            l.rot += l.vr * dt * (0.4f + 0.6f * wind)
            paintLeaf(c, l, leafColor(l, pal), l.x, l.y, 1f, 0f, 0.9f * amt)
        }
    }

    private fun leafColor(l: Leaf, pal: Palette) = mix(when (season) {
        Season.SPRING -> mix(floatArrayOf(1f, 0.86f, 0.90f), floatArrayOf(1f, 0.97f, 0.97f), l.hue)        // blossom petals
        Season.AUTUMN -> mix(floatArrayOf(0.92f, 0.42f, 0.16f), floatArrayOf(0.98f, 0.78f, 0.25f), l.hue)  // red to gold
        else -> mix(floatArrayOf(0.55f, 0.75f, 0.35f), floatArrayOf(0.95f, 0.78f, 0.35f), l.hue * 0.7f)
    }, pal.sun, 0.2f)

    private fun paintLeaf(c: Canvas, l: Leaf, col: FloatArray, x: Float, y: Float, k: Float, turn: Float, alpha: Float) {
        c.save()
        c.translate(x, y); c.rotate(Math.toDegrees((l.rot + turn).toDouble()).toFloat())
        c.scale(k, k * (0.35f + 0.65f * abs(sin(l.rot * 0.7f + l.bob))))
        paint.color = argb(col, alpha)
        c.drawOval(-l.s, -l.s * 0.42f, l.s, l.s * 0.42f, paint)
        stroke.color = argb(scale(col, 0.7f), alpha); stroke.strokeWidth = 0.8f * u / k
        c.drawLine(-l.s, 0f, l.s * 1.25f, 0f, stroke)
        c.restore()
    }

    /** Where a firefly is (x, y), how bright it blinks and its size, into [flyP]. */
    private fun flyAt(f: Fly, t: Float, fspan: Float) {
        var x = (f.x * W + sin(t * f.sp * 0.5f + f.ph) * 40 * u + t * 6 * u * f.sp - walk * 0.8f * H) % fspan
        if (x < 0) x += fspan
        x -= 30 * u
        // about a third of them rise higher, up around the drop, where it can catch them too
        val high = f.ph < 2.1f
        val yTop = H * if (high) 0.30f else 0.58f
        val depth = (H * if (high) 0.62f else 1f) - yTop
        flyP[0] = x
        flyP[1] = yTop + f.y * depth * 0.9f + cos(t * f.sp * 0.7f + f.ph * 1.3f) * 22 * u
        flyP[2] = max(0f, sin(t * 1.2f * f.sp + f.ph * 5)).pow(2)
        flyP[3] = (10 + 10 * f.s) * u
    }

    private fun drawFireflies(c: Canvas, pal: Palette, t: Float) {
        val amt = pal.stars
        if (amt < 0.05f) return
        paint.blendMode = BlendMode.PLUS
        val fspan = W + 60 * u
        for (f in flies) {
            flyAt(f, t, fspan)
            val x = flyP[0]; val y = flyP[1]; val blink = flyP[2]; val s = flyP[3]
            paint.alpha = (255 * amt * (0.15f + 0.85f * blink)).toInt().coerceIn(0, 255)
            spriteRect.set(x - s / 2, y - s / 2, x + s / 2, y + s / 2)
            c.drawBitmap(flySprite, null, spriteRect, paint)
        }
        paint.blendMode = null
        paint.alpha = 255
    }

    // -------- weather --------
    private fun drawRain(c: Canvas, dt: Float, wind: Float) {
        stroke.color = android.graphics.Color.argb(110, 210, 222, 240)
        stroke.strokeWidth = 1.1f * u
        val slant = -0.18f - 0.12f * wind
        for (d in drops) {
            d[1] += dt * 1.4f * d[2]
            d[0] += dt * slant * 1.4f * d[2] * H / W
            if (d[1] > 1.05f) { d[1] = -0.05f; d[0] = rand() * 1.2f }
            if (d[0] < -0.05f) d[0] += 1.1f
            val x = d[0] * W; val y = d[1] * H; val len = (14 + 10 * d[2]) * u
            c.drawLine(x, y, x - slant * len, y - len, stroke)
        }
    }

    private fun drawSnow(c: Canvas, t: Float, dt: Float, wind: Float) {
        paint.color = android.graphics.Color.argb(225, 255, 255, 255)
        for (f in flakes) {
            f[1] += dt * 0.06f * (0.6f + f[2] * 0.6f)
            f[0] += dt * (sin(t * 0.8f + f[3]) * 0.02f - 0.015f * wind)
            if (f[1] > 1.03f) { f[1] = -0.03f; f[0] = rand() }
            if (f[0] < -0.03f) f[0] += 1.06f
            c.drawCircle(f[0] * W, f[1] * H, (1.2f + 2.2f * f[2]) * u, paint)
        }
    }

    /** Slow banks of mist drifting low over the meadow. */
    private fun drawFog(c: Canvas, pal: Palette, t: Float) {
        val fogCol = mix(floatArrayOf(0.9f, 0.92f, 0.94f), pal.skyHor, 0.3f)
        val dim = 1f - 0.6f * pal.stars
        // a thin veil over everything, thickening towards the ground
        paint.shader = android.graphics.LinearGradient(0f, H * 0.3f, 0f, H, intArrayOf(argb(fogCol, 0f), argb(fogCol, 0.32f * dim)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, H * 0.3f, W, H, paint)
        // and drifting banks along the hills
        for (k in 0 until 4) {
            val y = H * (0.58f + 0.09f * k) + sin(t * 0.07f + k * 2f) * 18 * u
            val h = H * (0.14f + 0.04f * k)
            paint.shader = android.graphics.LinearGradient(0f, y - h / 2, 0f, y + h / 2,
                intArrayOf(argb(fogCol, 0f), argb(fogCol, (0.55f - 0.08f * k) * dim), argb(fogCol, 0f)), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, y - h / 2, W, y + h / 2, paint)
        }
        paint.shader = null
    }

    // -------- thought clouds --------
    private fun drawThoughts(c: Canvas, pal: Palette, t: Float, dt: Float, wind: Float) {
        if (thoughts.isEmpty()) return
        val it = thoughts.iterator()
        while (it.hasNext()) {
            val th = it.next()
            val age = t - th.born
            if (age > 14f) { it.remove(); continue }
            th.x -= (14 + 30 * wind) * u * dt
            th.y -= 9 * u * dt
            val grow = 0.6f + 0.4f * smooth(0f, 1.2f, age)
            val alpha = smooth(0f, 0.8f, age) * (1 - smooth(8f, 14f, age))
            val s = th.size * grow * (1 + age * 0.03f)
            val r = Mulberry(th.seed)
            val puffs = List(6) { floatArrayOf((r() - 0.5f) * 1.6f, -r() * 0.6f, 0.35f + r() * 0.35f) }
            // shadowed base, then lit tops shifted up towards the light
            paint.color = argb(pal.md, alpha * 0.95f)
            c.drawRoundRect(th.x - s * 0.9f, th.y - s * 0.15f, th.x + s * 0.9f, th.y + s * 0.25f, s * 0.2f, s * 0.2f, paint)
            for (p in puffs) c.drawCircle(th.x + p[0] * s * 0.6f, th.y + p[1] * s * 0.6f, p[2] * s, paint)
            paint.color = argb(pal.lt, alpha)
            for (p in puffs) c.drawCircle(th.x + p[0] * s * 0.6f - p[2] * s * 0.12f, th.y + p[1] * s * 0.6f - p[2] * s * 0.18f, p[2] * s * 0.78f, paint)
        }
    }

    // -------- the breathing drop --------
    // The drop itself is painted in the sky shader, where it can bend the real sky behind it.
    // Here only its crisp edge (the shader runs below screen resolution) and the rings it sends out on each turn of the breath.
    private fun drawCircle(c: Canvas, e: BreathEngine, pal: Palette, t: Float, dt: Float) {
        val cx = W / 2
        val cy = H * 0.36f
        val base = min(W * 0.5f, H * 0.32f)
        val r = base * (0.30f + 0.22f * e.breath)
        val white = floatArrayOf(1f, 1f, 1f)
        drawInDrop(c, e, pal, t, cx, cy, r)
        stroke.color = argb(white, 0.30f * (1 - 0.5f * pal.stars)); stroke.strokeWidth = 1.2f * u
        c.drawCircle(cx, cy, r, stroke)

        val it = ripples.iterator()
        while (it.hasNext()) {
            val rp = it.next()
            val k = (t - rp.t0) / 2.6f
            if (k >= 1) { it.remove(); continue }
            val eK = 1 - (1 - k).pow(3)
            stroke.color = argb(white, (if (rp.strong) 0.30f else 0.18f) * (1 - k))
            stroke.strokeWidth = (1.6f - 1.2f * k) * u
            c.drawCircle(cx, cy, r * (1 + 0.6f * eK), stroke)
        }
    }

    /** How much the drop shrinks what it sees, at a distance d (0 centre, 1 rim) from its middle. Same lens as the sky shader. */
    private fun lensK(d: Float) = 1.4f + 1.6f * (1 - kotlin.math.sqrt(max(0f, 1 - d * d)))

    /**
     * Where a point of the scene shows inside the drop, into [lensP]: x, y, how much smaller, and how clearly
     * (fading towards the rim, where the drop mostly reflects the light). False when the drop does not see that point.
     * The sky shader samples the scene at centre - q * lensK(|q|/r) + 0.9 r down for a point q of the drop;
     * this solves that backwards.
     */
    private fun lens(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val vx = cx - x
        val vy = cy + 0.9f * r - y
        val l = hypot(vx, vy)
        if (l >= 2.97f * r) return false
        var lo = 0f; var hi = r
        repeat(16) {
            val m = (lo + hi) / 2
            if (m * lensK(m / r) < l) lo = m else hi = m
        }
        val m = (lo + hi) / 2
        val f = if (l > 1e-3f) m / l else 0f
        lensP[0] = cx + vx * f
        lensP[1] = cy + vy * f
        lensP[2] = 1f / lensK(m / r)
        lensP[3] = kotlin.math.sqrt(max(0f, 1 - (m / r) * (m / r)))
        return true
    }

    /**
     * The drop sees the things that move through the meadow as well as the sky:
     * leaves and petals, fireflies, silver grass, rain and snow, upside down and small, through the same lens.
     */
    private fun drawInDrop(c: Canvas, e: BreathEngine, pal: Palette, t: Float, cx: Float, cy: Float, r: Float) {
        c.save()
        dropClip.rewind(); dropClip.addCircle(cx, cy, r, Path.Direction.CW)
        c.clipPath(dropClip)
        val clear = 0.85f
        // leaves (already moved this frame)
        val amtL = (1 - pal.stars) * if (e.reducedMotion) 0f else 1f
        if (amtL > 0.02f && season != Season.WINTER && weather != Weather.RAIN) {
            val active = when (season) { Season.AUTUMN -> 12; Season.SPRING -> 9; else -> 5 }
            for ((li, l) in leaves.withIndex()) {
                if (li >= active || l.delay > 0 || l.x < -40 * u) continue
                if (!lens(l.x, l.y, cx, cy, r)) continue
                // a little larger than the lens alone would make them, so they read in the drop
                paintLeaf(c, l, leafColor(l, pal), lensP[0], lensP[1], max(0.5f, lensP[2] * 1.6f), PI.toFloat(), 0.95f * amtL * (0.4f + 0.6f * lensP[3]))
            }
        }
        // fireflies
        if (pal.stars > 0.05f) {
            paint.blendMode = BlendMode.PLUS
            val fspan = W + 60 * u
            for (f in flies) {
                flyAt(f, t, fspan)
                if (!lens(flyP[0], flyP[1], cx, cy, r)) continue
                val s = flyP[3] * max(0.45f, lensP[2])
                paint.alpha = (255 * pal.stars * (0.15f + 0.85f * flyP[2]) * clear * (0.4f + 0.6f * lensP[3])).toInt().coerceIn(0, 255)
                spriteRect.set(lensP[0] - s / 2, lensP[1] - s / 2, lensP[0] + s / 2, lensP[1] + s / 2)
                c.drawBitmap(flySprite, null, spriteRect, paint)
            }
            paint.blendMode = null
            paint.alpha = 255
        }
        // silver grass: the parts of stems and plumes the drop sees
        if (season != Season.WINTER) {
            val amp = (0.18f + 0.42f * e.wind) * if (e.reducedMotion) 0.4f else 1f
            val margin = 30 * u
            val span = W + 2 * margin
            for (s in plumes) {
                val bend = plumeStem(s, t, amp, margin, span)
                tmp.rewind()
                var prev = false
                for (i in 0..12) {
                    if (lens(stemX[i], stemY[i], cx, cy, r)) {
                        if (prev) tmp.lineTo(lensP[0], lensP[1]) else tmp.moveTo(lensP[0], lensP[1])
                        prev = true
                    } else prev = false
                }
                stroke.color = argb(plumeStemCol(pal), 0.7f * clear); stroke.strokeWidth = 1.0f * u
                c.drawPath(tmp, stroke)
                tmp.rewind()
                for (j in 0 until 36) {
                    plumeStrand(s, bend, j, t)
                    if (!lens(strand[0], strand[1], cx, cy, r)) continue
                    val sx = lensP[0]; val sy = lensP[1]
                    if (!lens(strand[2], strand[3], cx, cy, r)) continue
                    val qx = lensP[0]; val qy = lensP[1]
                    if (!lens(strand[4], strand[5], cx, cy, r)) continue
                    tmp.moveTo(sx, sy); tmp.quadTo(qx, qy, lensP[0], lensP[1])
                }
                stroke.color = argb(plumeCol(pal), 0.55f * clear * (1 - 0.5f * pal.stars)); stroke.strokeWidth = 0.8f * u
                c.drawPath(tmp, stroke)
            }
        }
        // rain and snow (already moved this frame)
        if (weather == Weather.RAIN) {
            stroke.color = android.graphics.Color.argb(90, 220, 230, 245); stroke.strokeWidth = 0.9f * u
            val slant = -0.18f - 0.12f * e.wind
            for (d in drops) {
                val x = d[0] * W; val y = d[1] * H; val len = (14 + 10 * d[2]) * u
                if (!lens(x, y, cx, cy, r)) continue
                val ax = lensP[0]; val ay = lensP[1]
                if (!lens(x - slant * len, y - len, cx, cy, r)) continue
                c.drawLine(ax, ay, lensP[0], lensP[1], stroke)
            }
        } else if (weather == Weather.SNOW) {
            paint.color = android.graphics.Color.argb(200, 255, 255, 255)
            for (f in flakes) {
                if (!lens(f[0] * W, f[1] * H, cx, cy, r)) continue
                c.drawCircle(lensP[0], lensP[1], (1.2f + 2.2f * f[2]) * u * max(0.5f, lensP[2]), paint)
            }
        }
        c.restore()
    }

    // -------- things met along the walk --------
    private fun drawLandmarks(c: Canvas, pal: Palette, light: FloatArray, front: Boolean, walkAmt: Float, wind: Float) {
        val layer = if (walkAmt < 0.99f) c.saveLayerAlpha(null, (255 * walkAmt).toInt()) else -1
        for (l in landmarks) {
            if ((l.type == "flowers") != front) continue
            val x = (l.wx - walk * l.f) * H
            val reach = if (l.type == "poles") 1.1f * H else 0.4f * H
            if (x < -reach || x > W + 0.4f * H) continue
            val y0 = hillY(x)
            val y = if (l.d == 0f) y0 + 3 * u else y0 + (H - y0) * l.d
            when (l.type) {
                "tree" -> drawTree(c, x, y, H * (0.21f + 0.06f * l.seed), pal, gustAt(crestX(x)), light, l.seed)
                "poles" -> drawPoles(c, x, pal)
                "shrine" -> drawShrine(c, x, y, H * 0.05f, pal)
                "stone" -> drawStone(c, x, y, H * 0.045f, pal, light)
                else -> drawFlowers(c, l, x, pal, wind)
            }
        }
        if (layer >= 0) c.restoreToCount(layer)
    }

    /**
     * A lone tree on the crest, painted like the clouds: clumps of foliage in three tones, shaded towards the light,
     * with a soft shadow at its foot. Spring adds blossom, autumn turns it red and gold, winter leaves bare snowy branches.
     */
    private fun drawTree(c: Canvas, x: Float, y: Float, sc: Float, pal: Palette, g: Float, light: FloatArray, seed: Float) {
        val night = pal.stars
        fun fog(col: FloatArray) = mix(col, pal.skyHor, 0.18f * (1 - night))
        val r = Mulberry((seed * 10_000).toInt() + 5)
        val trunk = fog(mix(floatArrayOf(0.30f, 0.22f, 0.19f), pal.gBot, 0.40f + 0.35f * night))
        val sway = (g - 0.3f) * sc * 0.015f

        // shadow on the ground
        paint.color = argb(scale(pal.gBot, 0.6f), 0.28f)
        c.drawOval(x - sc * 0.32f, y - sc * 0.025f, x + sc * 0.32f, y + sc * 0.03f, paint)

        // trunk, slightly leaning, and the main limbs
        paint.color = argb(trunk, 1f)
        tmp.rewind()
        tmp.moveTo(x - sc * 0.045f, y)
        tmp.quadTo(x - sc * 0.02f, y - sc * 0.28f, x - sc * 0.014f + sway, y - sc * 0.55f)
        tmp.lineTo(x + sc * 0.014f + sway, y - sc * 0.55f)
        tmp.quadTo(x + sc * 0.024f, y - sc * 0.28f, x + sc * 0.05f, y)
        tmp.close()
        c.drawPath(tmp, paint)
        stroke.color = argb(trunk, 1f)
        val limbs = listOf(floatArrayOf(-0.20f, -0.66f, 0.016f), floatArrayOf(0.21f, -0.69f, 0.015f), floatArrayOf(-0.05f, -0.86f, 0.013f),
            floatArrayOf(0.10f, -0.84f, 0.012f), floatArrayOf(-0.30f, -0.58f, 0.010f), floatArrayOf(0.31f, -0.60f, 0.010f))
        for (l in limbs) {
            stroke.strokeWidth = sc * l[2]
            tmp.rewind(); tmp.moveTo(x + sway * 0.5f, y - sc * 0.48f)
            tmp.quadTo(x + l[0] * sc * 0.4f, y + l[1] * sc * 0.8f, x + l[0] * sc + sway * -l[1], y + l[1] * sc)
            c.drawPath(tmp, stroke)
        }

        if (season == Season.WINTER) {
            // bare twigs, and snow along the tops of the limbs
            stroke.strokeWidth = sc * 0.005f
            for (l in limbs) for (k in 0 until 3) {
                val bx = x + l[0] * sc + sway * -l[1]
                val by = y + l[1] * sc
                val a = -1.6f + (r() - 0.5f) * 1.8f
                c.drawLine(bx, by, bx + cos(a) * sc * 0.09f, by + sin(a) * sc * 0.09f, stroke)
            }
            stroke.color = android.graphics.Color.argb(230, 245, 248, 255)
            stroke.strokeWidth = sc * 0.008f
            for (l in limbs) {
                tmp.rewind(); tmp.moveTo(x + l[0] * sc * 0.45f, y + l[1] * sc * 0.82f - sc * 0.006f)
                tmp.quadTo(x + l[0] * sc * 0.75f, y + l[1] * sc * 0.93f - sc * 0.008f, x + l[0] * sc + sway * -l[1], y + l[1] * sc - sc * 0.008f)
                c.drawPath(tmp, stroke)
            }
            return
        }

        // foliage: clumps on a rounded crown, each with a scalloped upper rim
        val clumps = ArrayList<FloatArray>()
        for (i in 0 until 11) {
            val a = (i / 11f) * 6.283f + r() * 0.4f
            val rr = 0.22f + r() * 0.12f
            clumps += floatArrayOf(cos(a) * rr * 1.25f, -0.76f + sin(a) * rr * 0.85f, 0.10f + r() * 0.07f)
        }
        clumps += floatArrayOf(0f, -0.74f, 0.2f); clumps += floatArrayOf(-0.1f, -0.92f, 0.13f); clumps += floatArrayOf(0.12f, -0.93f, 0.12f)
        val ln = hypot(light[0], light[1]).coerceAtLeast(1e-4f)
        val ox = light[0] / ln
        val oy = -light[1] / ln
        val tones = when (season) {
            Season.AUTUMN -> listOf(floatArrayOf(0.52f, 0.22f, 0.12f), floatArrayOf(0.84f, 0.44f, 0.16f), floatArrayOf(0.99f, 0.74f, 0.32f))
            else -> listOf(scale(mix(pal.gBot, pal.gTop, 0.15f), 0.85f), mix(pal.gBot, pal.gTop, 0.55f), scale(mix(pal.gTop, pal.sun, 0.22f * (1 - night)), 1.05f))
        }
        fun tone(cl: FloatArray) = fog(if (season == Season.AUTUMN) mix(cl, pal.gBot, 0.55f * night) else cl)
        fun crown(rs: Float, off: Float, upperOnly: Boolean): Path {
            val p = Path()
            for (b in clumps) {
                if (upperOnly && b[1] > -0.66f) continue
                val bx = x + b[0] * sc + sway * -b[1] + ox * b[2] * sc * off
                val by = y + b[1] * sc + oy * b[2] * sc * off
                p.addCircle(bx, by, b[2] * sc * rs, Path.Direction.CW)
                for (k in 0 until 3) {
                    val a = -2.4f + k * 0.8f
                    p.addCircle(bx + cos(a) * b[2] * sc * 0.75f * rs, by + sin(a) * b[2] * sc * 0.75f * rs, b[2] * sc * 0.45f * rs, Path.Direction.CW)
                }
            }
            return p
        }
        val silhouette = crown(1f, 0f, false)
        paint.color = argb(tone(tones[0]), 1f); c.drawPath(silhouette, paint)
        c.save()
        c.clipPath(silhouette)
        paint.color = argb(tone(tones[1]), 1f); c.drawPath(crown(0.9f, 0.4f, false), paint)
        paint.color = argb(tone(tones[2]), 1f); c.drawPath(crown(0.58f, 0.66f, true), paint)
        if (season == Season.SPRING) {
            // blossom scattered through the crown
            for (k in 0 until 46) {
                val b = clumps[k % clumps.size]
                val bx = x + (b[0] + (r() - 0.5f) * b[2] * 2f) * sc
                val by = y + (b[1] + (r() - 0.5f) * b[2] * 2f) * sc
                paint.color = argb(mix(SPRING_COLS[k % 3], pal.gBot, 0.5f * night), 0.95f)
                c.drawCircle(bx, by, sc * (0.010f + 0.008f * r()), paint)
            }
        }
        c.restore()
    }

    private fun drawPoles(c: Canvas, x: Float, pal: Palette) {
        val col = mix(mix(floatArrayOf(0.25f, 0.20f, 0.18f), pal.gBot, 0.5f), pal.skyHor, 0.25f * (1 - pal.stars))
        val n = 4
        val gap = H * 0.32f
        val h = H * 0.16f
        val tops = ArrayList<FloatArray>()
        paint.color = argb(col, 1f)
        for (k in 0 until n) {
            val px = x + k * gap
            val py = hillY(px) + 2 * u
            val hh = h * (1 - 0.04f * k)
            c.drawRect(px - 1.6f * u, py - hh, px + 1.6f * u, py, paint)
            c.drawRect(px - H * 0.024f, py - hh + H * 0.012f, px + H * 0.024f, py - hh + H * 0.012f + 2.2f * u, paint)
            tops += floatArrayOf(px, py - hh + H * 0.012f)
        }
        stroke.color = argb(col, 0.9f); stroke.strokeWidth = 0.9f * u
        tmp.rewind()
        for (o in floatArrayOf(-0.02f * H, 0.02f * H)) {
            for (k in 0 until n - 1) {
                val a = tops[k]; val b = tops[k + 1]
                tmp.moveTo(a[0] + o, a[1])
                tmp.quadTo((a[0] + b[0]) / 2 + o, (a[1] + b[1]) / 2 + H * 0.03f, b[0] + o, b[1])
            }
        }
        c.drawPath(tmp, stroke)
    }

    private fun drawShrine(c: Canvas, x: Float, y: Float, sc: Float, pal: Palette) {
        val night = pal.stars
        fun tone(col: FloatArray) = mix(col, pal.gBot, 0.2f + 0.45f * night)
        val stone = tone(floatArrayOf(0.60f, 0.60f, 0.58f))
        val wood = tone(floatArrayOf(0.56f, 0.28f, 0.20f))
        val roof = tone(floatArrayOf(0.26f, 0.24f, 0.27f))
        paint.color = argb(stone, 1f); c.drawRect(x - sc * 0.55f, y - sc * 0.28f, x + sc * 0.55f, y, paint)
        paint.color = argb(wood, 1f); c.drawRect(x - sc * 0.38f, y - sc * 0.95f, x + sc * 0.38f, y - sc * 0.28f, paint)
        paint.color = argb(scale(wood, 0.45f), 1f); c.drawRect(x - sc * 0.17f, y - sc * 0.80f, x + sc * 0.17f, y - sc * 0.28f, paint)
        paint.color = argb(roof, 1f)
        tmp.rewind()
        tmp.moveTo(x - sc * 0.72f, y - sc * 0.88f)
        tmp.quadTo(x - sc * 0.3f, y - sc * 1.02f, x, y - sc * 1.38f)
        tmp.quadTo(x + sc * 0.3f, y - sc * 1.02f, x + sc * 0.72f, y - sc * 0.88f)
        tmp.close()
        c.drawPath(tmp, paint)
    }

    private fun drawStone(c: Canvas, x: Float, y: Float, sc: Float, pal: Palette, light: FloatArray) {
        val night = pal.stars
        val base = mix(floatArrayOf(0.52f, 0.53f, 0.55f), pal.gBot, 0.3f + 0.4f * night)
        val shape = Path().apply {
            moveTo(x - sc * 0.48f, y)
            quadTo(x - sc * 0.55f, y - sc * 0.6f, x - sc * 0.26f, y - sc * 1.0f)
            quadTo(x - sc * 0.02f, y - sc * 1.16f, x + sc * 0.22f, y - sc * 0.98f)
            quadTo(x + sc * 0.5f, y - sc * 0.56f, x + sc * 0.44f, y)
            close()
        }
        paint.color = argb(base, 1f); c.drawPath(shape, paint)
        c.save(); c.clipPath(shape)
        val side = if (light[0] < 0) -1 else 1
        paint.color = argb(mix(scale(base, 1.22f), pal.sun, 0.2f * (1 - night)), 1f)
        c.save(); c.rotate(Math.toDegrees(side * 0.25).toFloat(), x + side * sc * 0.28f, y - sc * 0.72f)
        c.drawOval(x + side * sc * 0.28f - sc * 0.42f, y - sc * 0.72f - sc * 0.6f, x + side * sc * 0.28f + sc * 0.42f, y - sc * 0.72f + sc * 0.6f, paint)
        c.restore()
        paint.color = argb(mix(pal.gTop, pal.gBot, 0.35f), 1f)
        c.drawOval(x - sc * 0.08f - sc * 0.42f, y - sc * 1.1f - sc * 0.2f, x - sc * 0.08f + sc * 0.42f, y - sc * 1.1f + sc * 0.2f, paint)
        c.restore()
    }

    private fun drawFlowers(c: Canvas, l: Landmark, x0: Float, pal: Palette, wind: Float) {
        val amp = (0.18f + 0.42f * wind) * 4 * u
        for (ci in 0 until 3) {
            paint.color = argb(mix(FLOWER_COLS[ci], pal.gBot, 0.7f * pal.stars), 0.95f)
            for (it in l.items) {
                if (it[2].toInt() != ci) continue
                val x = x0 + it[0] * H
                val y0 = hillY(x)
                val s = it[3] * (0.5f + it[1]) * u
                val fx = x - gustAt(crestX(x)) * amp * it[1]
                val fy = y0 + (H - y0) * it[1] - s * 2.5f
                c.drawCircle(fx, fy, s, paint)
            }
        }
    }

    // -------- helpers --------
    private fun sprite(r: Int, g: Int, b: Int): Bitmap {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(32f, 32f, 32f,
            intArrayOf(android.graphics.Color.argb(255, r, g, b), android.graphics.Color.argb(140, r, g, b), android.graphics.Color.argb(0, r, g, b)),
            floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP)
        Canvas(bmp).drawRect(0f, 0f, 64f, 64f, p)
        return bmp
    }

    companion object {
        private val LM_ORDER = listOf("tree", "poles", "flowers", "shrine", "tree", "stone", "flowers", "poles")
        private val LM_DEPTH = mapOf("tree" to 0f, "poles" to 0f, "stone" to 0.08f, "shrine" to 0.12f, "flowers" to 0.55f)
        private val SPRING_COLS = listOf(floatArrayOf(1f, 1f, 0.97f), floatArrayOf(1f, 0.84f, 0.90f), floatArrayOf(1f, 0.93f, 0.55f))
        private val FLOWER_COLS = listOf(floatArrayOf(1f, 1f, 0.96f), floatArrayOf(1f, 0.88f, 0.45f), floatArrayOf(1f, 0.78f, 0.85f))
    }
}

/** Small deterministic random generator (mulberry32), the same one the prototype uses. */
class Mulberry(seed: Int) {
    private var a = seed
    operator fun invoke(): Float {
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toFloat() / 4294967296f
    }
}

fun argb(c: FloatArray, a: Float): Int = android.graphics.Color.argb(
    (a.coerceIn(0f, 1f) * 255).toInt(), (c[0].coerceIn(0f, 1f) * 255).toInt(),
    (c[1].coerceIn(0f, 1f) * 255).toInt(), (c[2].coerceIn(0f, 1f) * 255).toInt(),
)
fun scale(c: FloatArray, k: Float) = FloatArray(c.size) { c[it] * k }
