package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.grungert.nebo.sky.nextFrame
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import com.grungert.nebo.SkyHost
import com.grungert.nebo.sky.DayCycle
import com.grungert.nebo.sky.Seasons
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where the water meets the far shore, from the top of the screen; matches LAKE_Y in the sky shader (1 - 0.42). */
const val SHORE = 0.58f

/**
 * The still lake as a place to sit, for any session: the trees on the far shore and their twins in the water,
 * reeds at the edges, lily pads and a dragonfly. The water itself is painted by the sky shader.
 */
@Composable
fun LakeShore(host: SkyHost, modifier: Modifier = Modifier, visible: Float = 1f) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        // the lake keeps the sky's pace (see skyFps)
        while (true) nextFrame(last, com.grungert.nebo.sky.skyFps(host.engine.walkOn, 0f)).let { now -> t += ((now - last) / 1e9f).coerceIn(0f, 0.1f); last = now }
    }
    Canvas(modifier.fillMaxSize().alpha(visible)) {
        if (t < 0f) return@Canvas   // reading t redraws every frame
        treeline(treeColor(host), t)
        lakeDecor(t, calm = true, rainy = host.weather == com.grungert.nebo.sky.Weather.RAIN)
    }
}

/** The far bank's trees take the colour of the meadow at this hour and season, darker in the distance. */
fun treeColor(host: SkyHost): Color {
    val pal = Seasons.apply(DayCycle.paletteAt(host.day.hour), host.season, host.weather)
    return Color(pal.gBot[0] * 0.55f, pal.gBot[1] * 0.62f, pal.gBot[2] * 0.6f)
}

/** Rounded clumps of trees along the far shore, and their softer, upside-down twins in the water. */
fun DrawScope.treeline(color: Color, t: Float) {
    val w = size.width; val shore = size.height * SHORE; val px = density
    val clumps = List(16) { i ->
        val x = w * (i / 15f) + ((i * 37) % 11 - 5) * 3 * px
        val hgt = (10 + ((i * 53) % 9) * 3.5f + (if (i % 5 == 2) 16f else 0f) + (if (i % 7 == 4) -6f else 0f)) * px
        Triple(x, hgt, i)
    }
    fun paint(mirror: Boolean) {
        // every clump goes into one shape, so overlapping lobes merge instead of showing their edges
        val shape = Path()
        val light = Path()
        for ((x, hgt, i) in clumps) {
            if (x > w * 0.42f && x < w * 0.58f && i % 2 == 0) continue   // a gap in the middle, where the light comes in
            val sgn = if (mirror) 1f else -1f
            val sway = if (mirror) sin(t * 0.8f + i) * 1.5f * px else 0f
            val r = hgt * 0.55f
            for ((dx, dy, k) in listOf(Triple(0f, 0.55f, 1f), Triple(-0.7f, 0.35f, 0.8f), Triple(0.75f, 0.32f, 0.75f), Triple(-0.25f, 0.8f, 0.6f))) {
                val cx = x + r * dx + sway; val cy = shore + sgn * hgt * dy; val rr = r * k
                shape.addOval(Rect(cx - rr, cy - rr, cx + rr, cy + rr))
            }
            shape.addRect(Rect(x - r * 1.3f + sway, if (mirror) shore else shore - hgt * 0.3f,
                x + r * 1.3f + sway, if (mirror) shore + hgt * 0.3f else shore))
            // the side towards the light, a few lighter dabs
            if (!mirror) for ((dx, dy, k) in listOf(Triple(-0.35f, 0.74f, 0.5f), Triple(-0.95f, 0.47f, 0.38f), Triple(0.35f, 0.62f, 0.3f))) {
                val cx = x + r * dx; val cy = shore - hgt * dy; val rr = r * k
                light.addOval(Rect(cx - rr, cy - rr, cx + rr, cy + rr))
            }
        }
        drawPath(shape, color.copy(alpha = if (mirror) 0.30f else 1f))
        if (!mirror) drawPath(light, androidx.compose.ui.graphics.lerp(color, Color(0xFFE8F0D8), 0.25f).copy(alpha = 0.45f))
    }
    paint(false)
    clipRect(top = shore) { paint(true) }
}

/**
 * Lily pads, reeds at both edges and a dragonfly. [calm] slows the dragonfly for sitting: it moves on
 * every fourteen seconds instead of every six. In the rain ([rainy]) the dragonfly keeps to the reeds, out of sight.
 */
fun DrawScope.lakeDecor(t: Float, calm: Boolean = false, rainy: Boolean = false) {
    val w = size.width; val h = size.height
    val px = density

    // lily pads, gently rocking, one with a flower
    lilyPad(Offset(w * 0.20f, h * 0.905f), w * 0.11f, t, 0.3f, flower = true)
    lilyPad(Offset(w * 0.74f, h * 0.865f), w * 0.07f, t, 1.7f, flower = false)
    lilyPad(Offset(w * 0.86f, h * 0.95f), w * 0.10f, t, 2.9f, flower = false)
    lilyPad(Offset(w * 0.38f, h * 0.975f), w * 0.06f, t, 4.1f, flower = false)

    // reeds at both edges, swaying in the breeze
    reeds(0f, w * 0.20f, h, t, 1)
    reeds(w, w * 0.18f, h, t, -1)

    // a dragonfly that hovers near the reeds and darts to a new place now and then
    if (rainy) return
    val period = if (calm) 14f else 6f
    fun spot(n: Int) = Offset(w * (0.70f + 0.12f * sin(n * 2.3f)), h * (0.64f + 0.05f * cos(n * 1.7f)))
    val hop = (t / period).toInt()
    val from = spot(hop - 1); val to = spot(hop)
    val f = ((t % period) / 0.6f).coerceAtMost(1f)
    dragonfly(Offset(from.x + (to.x - from.x) * f + sin(t * 3f) * 2 * px, from.y + (to.y - from.y) * f + cos(t * 2.4f) * 2 * px), t)
}

private fun DrawScope.lilyPad(c: Offset, r: Float, t: Float, ph: Float, flower: Boolean) {
    val ry = r * 0.36f
    val o = Offset(c.x, c.y + sin(t * 0.9f + ph) * 1.2f * density)
    // a soft shadow on the water, then the pad with its notch
    drawOval(Color(0x33000000), Offset(o.x - r * 1.04f, o.y - ry * 0.8f), Size(r * 2.08f, ry * 2.1f))
    val pad = Path().apply {
        moveTo(o.x, o.y)
        var a = 0.25f + 0.1f * sin(ph)
        lineTo(o.x + cos(a) * r, o.y + sin(a) * ry)
        while (a < 2 * PI.toFloat() - 0.05f) { a += 0.12f; lineTo(o.x + cos(a) * r, o.y + sin(a) * ry) }
        close()
    }
    drawPath(pad, Brush.radialGradient(listOf(Color(0xFF6E9E58), Color(0xFF3F6B3A)), Offset(o.x - r * 0.2f, o.y - ry * 0.3f), r))
    drawPath(pad, Color(0xFF8FBF74).copy(alpha = 0.5f), style = Stroke(1.2f * density))
    for (i in 0 until 6) {
        val a = 0.6f + i * 0.9f
        drawLine(Color(0xFF2F5530).copy(alpha = 0.4f), o, Offset(o.x + cos(a) * r * 0.85f, o.y + sin(a) * ry * 0.85f), 0.8f * density)
    }
    if (flower) {
        val fc = Offset(o.x + r * 0.15f, o.y - ry * 0.35f)
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4 + 0.2f
            drawLine(Color(0xFFF7C6D8), fc, Offset(fc.x + cos(a) * r * 0.32f, fc.y + sin(a) * r * 0.16f - r * 0.06f), r * 0.13f, StrokeCap.Round)
        }
        for (i in 0 until 5) {
            val a = i * 2 * PI.toFloat() / 5
            drawLine(Color(0xFFFFE4EE), fc, Offset(fc.x + cos(a) * r * 0.18f, fc.y + sin(a) * r * 0.09f - r * 0.12f), r * 0.10f, StrokeCap.Round)
        }
        drawCircle(Color(0xFFFFD36B), r * 0.06f, Offset(fc.x, fc.y - r * 0.1f))
    }
}

private fun DrawScope.reeds(edge: Float, spread: Float, h: Float, t: Float, dir: Int) {
    for (i in 0 until 9) {
        val k = i / 8f
        val x = edge + dir * spread * (0.05f + 0.95f * ((i * 37) % 9) / 8f)
        val tall = h * (0.16f + 0.16f * ((i * 53) % 7) / 6f)
        val sway = (sin(t * 0.8f + i * 0.9f) * 0.6f + sin(t * 1.9f + i) * 0.25f) * 10 * density * (0.6f + 0.4f * k)
        val base = Offset(x, h + 8 * density)
        val tip = Offset(x + sway - dir * 6 * density, h - tall)
        val stem = Path().apply { moveTo(base.x, base.y); quadraticTo(x - dir * 2 * density, h - tall * 0.5f, tip.x, tip.y) }
        drawPath(stem, Color(0xFF243A2A).copy(alpha = 0.92f), style = Stroke((2.2f - 0.8f * k) * density, cap = StrokeCap.Round))
        if (i % 3 == 0) {
            // a cattail head a little below the tip
            val head = Offset(base.x + (tip.x - base.x) * 0.82f, base.y + (tip.y - base.y) * 0.82f)
            rotate(sway * 0.6f, head) {
                drawRoundRect(Color(0xFF5A3A24), Offset(head.x - 2.6f * density, head.y - 11 * density), Size(5.2f * density, 20 * density),
                    CornerRadius(2.6f * density))
            }
        } else {
            // a long leaf bending outwards
            val leaf = Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(x + dir * 14 * density + sway, h - tall * 0.55f, x + dir * 26 * density + sway * 1.3f, h - tall * 0.75f)
            }
            drawPath(leaf, Color(0xFF2E4A32).copy(alpha = 0.85f), style = Stroke(1.6f * density, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.dragonfly(c: Offset, t: Float) {
    val px = density
    val wing = Color(0xFFE8F4FF).copy(alpha = 0.55f * (0.6f + 0.4f * sin(t * 40f)))
    drawOval(wing, Offset(c.x - 16 * px, c.y - 5.5f * px), Size(14 * px, 4.5f * px))
    drawOval(wing, Offset(c.x + 2 * px, c.y - 5.5f * px), Size(14 * px, 4.5f * px))
    drawOval(wing, Offset(c.x - 14 * px, c.y + 0.5f * px), Size(12 * px, 4 * px))
    drawOval(wing, Offset(c.x + 2 * px, c.y + 0.5f * px), Size(12 * px, 4 * px))
    drawLine(Color(0xFF2A6F8F), Offset(c.x, c.y - 4 * px), Offset(c.x, c.y + 14 * px), 2.2f * px, StrokeCap.Round)
    drawCircle(Color(0xFF3B8DB0), 2.6f * px, Offset(c.x, c.y - 5 * px))
}
