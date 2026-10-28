package com.grungert.nebo.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.grungert.nebo.sky.Storm
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Rain on the glass between you and the meadow, while you sit under a rainy sky. Fine drops land and stay a while; now
 * and then a bigger one gathers, hangs, and runs down in starts and stops, taking the small ones in its way and leaving
 * a wet line and a few beads behind it that dry away. In the spirit of Lucas Bebber's "RainEffect" (Codrops) and
 * Martijn Steinrucken's "Heartfelt", drawn by hand on a 2D canvas: a drop is a small lens of light and shade.
 * The glass follows the [Storm]: few drops in a drizzle, many and running in a downpour, and they catch the light of a
 * strike. [still]: only the fine drops, nothing runs.
 */
@Composable
fun RainOnGlass(night: Boolean, still: Boolean, modifier: Modifier = Modifier) {
    val dp = LocalDensity.current.density
    val glass = remember { WetGlass(dp) }
    val sprite = remember { dropSprite().asImageBitmap() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(still) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            if (size != IntSize.Zero) glass.step(dt, size.width.toFloat(), size.height.toFloat(), still, Storm.intensity(Storm.now()))
            frame = now
        }
    }
    Canvas(modifier.fillMaxSize().onSizeChanged { size = it }) {
        frame   // read, so every step draws anew
        val k = ((if (night) 0.6f else 1f) * (1f + 0.6f * Storm.flash(Storm.now()))).coerceAtMost(1f)
        // the wet lines first, under the drops: fading as they dry
        for (r in glass.runners) for (i in 1 until r.path.size) {
            val a = r.path[i - 1]; val b = r.path[i]
            val dry = 1f - (glass.time - b[2]) / 4f
            if (dry <= 0f) continue
            drawLine(Color.White.copy(alpha = 0.2f * dry * k), Offset(a[0], a[1]), Offset(b[0], b[1]), r.r * 0.7f * dry, StrokeCap.Round)
        }
        for (d in glass.beads) {
            val age = glass.time - d.born
            val left = d.life - age
            val shown = (age / 0.15f).coerceIn(0f, 1f) * (left / 3f).coerceIn(0f, 1f)
            val r = d.r * (0.5f + 0.5f * shown)
            if (r < 0.6f) continue
            drawImage(sprite, dstOffset = IntOffset((d.x - r).toInt(), (d.y - r).toInt()), dstSize = IntSize((2 * r).toInt(), (2 * r).toInt()), alpha = shown * k)
        }
        for (d in glass.runners) {
            // a running drop is heavier below: a little longer than wide, its weight at the bottom
            val w = d.r * 2; val h = d.r * (if (d.hold > 0f) 2.05f else 2.35f)
            drawImage(sprite, dstOffset = IntOffset((d.x - d.r).toInt(), (d.y + d.r - h).toInt()), dstSize = IntSize(w.toInt(), h.toInt()), alpha = k)
        }
    }
}

private class Bead(val x: Float, val y: Float, val r: Float, val born: Float, val life: Float)

private class Runner(var x: Float, var y: Float, var r: Float, var hold: Float, val seed: Float) {
    var speed = 0f               // px a second, now
    var stuck = 0f               // seconds left caught on the glass
    var trailAt = 0f             // where it last left a bead
    val path = ArrayList<FloatArray>()   // x, y, when: the wet line it leaves
}

/** The drops on the glass and how they move, in pixels and seconds. */
private class WetGlass(private val dp: Float) {
    val beads = ArrayList<Bead>()
    val runners = ArrayList<Runner>()
    var time = 0f
    private val rnd = Random(17)

    fun step(dt: Float, w: Float, h: Float, still: Boolean, heavy: Float) {
        time += dt
        // fine drops land all the time, as many as the rain is hard, more at first so the glass is not bare
        val landing = if (beads.size < 50 * heavy) 40f else 3f + 26f * heavy
        var n = landing * dt
        while (n > 0f && beads.size < 60 + 150 * heavy) {
            if (n < 1f && rnd.nextFloat() > n) break
            val s = rnd.nextFloat()
            beads += Bead(rnd.nextFloat() * w, rnd.nextFloat() * h, (1.4f + 3.8f * s * s) * dp, time, 9f + rnd.nextFloat() * 16f)
            n -= 1f
        }
        beads.removeAll { time - it.born > it.life }

        // now and then a big one gathers
        if (!still && runners.size < 4 + 10 * heavy && rnd.nextFloat() < (0.15f + 1.1f * heavy) * dt)
            runners += Runner(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.8f, (6f + rnd.nextFloat() * 6f) * dp, 0.5f + rnd.nextFloat() * 2.5f, rnd.nextFloat() * 6.28f)

        val it = runners.iterator()
        while (it.hasNext()) {
            val d = it.next()
            if (d.y - 2.5f * d.r > h) {
                // gone below the glass; its wet line dries away after it
                if (d.path.all { p -> time - p[2] > 4f }) it.remove()
                continue
            }
            if (d.hold > 0f) {
                // it hangs, growing a little, until it is heavy enough to go
                d.hold -= dt
                d.r += 0.25f * dp * dt
                d.trailAt = d.y
                continue
            }
            // starts and stops: caught on the glass now and then, then away again, the heavier the quicker
            if (d.stuck > 0f) { d.stuck -= dt; d.speed *= 0.8f }
            else {
                val target = (40f + 18f * d.r / dp) * dp
                d.speed += (target - d.speed) * (1f - Math.exp(-4.0 * dt).toFloat())
                if (rnd.nextFloat() < 1.6f * dt) d.stuck = 0.08f + rnd.nextFloat() * 0.5f * (8f * dp / d.r)
            }
            d.y += d.speed * dt
            d.x += sin(d.y / (23f * dp) + d.seed) * 6f * dp * dt * (d.speed / (80f * dp))
            if (d.path.isEmpty() || d.y - d.path.last()[1] > 5f * dp) {
                d.path += floatArrayOf(d.x, d.y, time)
                if (d.path.size > 60) d.path.removeAt(0)
            }
            // the small drops in its way run into it
            val bi = beads.iterator()
            while (bi.hasNext()) {
                val b = bi.next()
                if (hypot(b.x - d.x, b.y - d.y) < d.r + b.r * 0.6f) { bi.remove(); d.r = sqrt(d.r * d.r + b.r * b.r * 0.6f).coerceAtMost(14f * dp) }
            }
            // it leaves beads behind as it goes, and grows lighter by them
            if (d.y - d.trailAt > (7f + 9f * rnd.nextFloat()) * dp) {
                d.trailAt = d.y
                val tr = d.r * (0.22f + 0.2f * rnd.nextFloat())
                beads += Bead(d.x + (rnd.nextFloat() - 0.5f) * d.r * 0.5f, d.y - d.r * 1.1f, tr, time, 2.5f + 3f * rnd.nextFloat())
                d.r = sqrt(d.r * d.r - tr * tr * 0.5f)
            }
            if (d.r < 3.5f * dp) { beads += Bead(d.x, d.y, d.r, time, 6f); it.remove() }   // too light to go on: it stays
        }
    }
}

/** One drop on the glass, drawn once and stretched for all: a body of faint light, a darker rim, light gathered at the bottom, a glint at the top. */
internal fun dropSprite(): Bitmap {
    val s = 96f; val c = s / 2; val r = s / 2 - 1
    val b = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun argb(a: Int, rr: Int, g: Int, bb: Int) = android.graphics.Color.argb(a, rr, g, bb)
    // the body: faint light inside, a dark rim where the glass bends the light away
    p.shader = RadialGradient(c, c + r * 0.12f, r, intArrayOf(argb(64, 255, 255, 255), argb(30, 235, 242, 255), argb(110, 22, 34, 58), argb(150, 18, 28, 50), argb(0, 18, 28, 50)),
        floatArrayOf(0f, 0.55f, 0.82f, 0.93f, 1f), Shader.TileMode.CLAMP)
    cv.drawCircle(c, c, r, p)
    // the top in shade: a drop shows the world upside down, the dark ground above, the bright sky below
    p.shader = RadialGradient(c, c - r * 0.32f, r * 0.62f, intArrayOf(argb(80, 18, 28, 50), argb(0, 18, 28, 50)), null, Shader.TileMode.CLAMP)
    cv.drawCircle(c, c - r * 0.32f, r * 0.62f, p)
    p.shader = RadialGradient(c, c + r * 0.5f, r * 0.48f, intArrayOf(argb(200, 255, 255, 255), argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP)
    cv.drawCircle(c, c + r * 0.5f, r * 0.48f, p)
    p.shader = RadialGradient(c - r * 0.3f, c - r * 0.42f, r * 0.24f, intArrayOf(argb(255, 255, 255, 255), argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP)
    cv.drawOval(RectF(c - r * 0.56f, c - r * 0.6f, c - r * 0.04f, c - r * 0.24f), p)
    return b
}
