package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** The three minutes of the breathing space, one each: wide, narrow, wide again. */
const val HOURGLASS_SECONDS = 180

/** The stage (0, 1 or 2), with its name, its title and what to do. */
val HOURGLASS_STAGES = listOf(
    Triple("Široko", "Šta je sada tu?", "Primijeti šta je u tebi: misli, osjećaje, tijelo. Ne mijenjaj ništa, samo vidi."),
    Triple("Usko", "Samo dah", "Skupi pažnju u stomak. Prati jedan udah, pa jedan izdah. Kad odlutaš, vrati se dahu."),
    Triple("Opet široko", "Cijelo tijelo", "Raširi pažnju na cijelo tijelo: držanje, lice, ruke, stopala. Kao da cijelo tijelo diše."),
)

fun hourglassStage(elapsed: Float) = (elapsed / 60f).toInt().coerceIn(0, 2)

private val SAND = Color(0xFFFFD9A0)
private val GLASS = Color(0xFFE8F0FF)
private val INK = Color(0xFF14223F)

private class Grain(val u: Float, val v: Float, val ph: Float, val sp: Float, val s: Float, val w: Float)

private fun smooth(a: Float, b: Float, x: Float): Float {
    val k = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

/**
 * The three-minute breathing space as an hourglass of light. In the first minute the grains drift through the whole
 * upper bulb, the wide view of what is here; in the second they gather and fall as one thin stream through the neck,
 * the breath alone; in the third they spill into the lower bulb and on across the whole screen, the whole body.
 */
@Composable
fun HourglassView(elapsed: Float, modifier: Modifier = Modifier) {
    val grains = remember {
        val r = Random(7)
        List(150) { Grain(r.nextFloat() * 2 - 1, r.nextFloat(), r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 0.8f, 0.7f + r.nextFloat() * 0.9f, r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) { hourglass(elapsed, grains) }
}

private fun DrawScope.hourglass(t: Float, grains: List<Grain>) {
    val w = size.width; val h = size.height; val px = density
    val cx = w / 2; val cy = h * 0.42f
    val gh = min(h * 0.46f, w * 1.15f)
    val gw = gh * 0.62f
    val half = gh / 2
    val y0 = cy - half; val y1 = cy + half
    val neck = gw * 0.035f
    // the glass: each bulb widens fast from the neck and rounds off towards its lid
    fun halfWidth(y: Float): Float {
        val s = (abs(y - cy) / half).coerceIn(0f, 1f)
        return neck + (gw / 2 - neck) * sin(s * PI.toFloat() / 2).pow(0.55f)
    }
    val gather = smooth(60f, 67f, t)     // into the neck
    val spill = smooth(120f, 129f, t)    // out into the lower bulb
    val spread = smooth(128f, 172f, t)   // and on, across everything
    val glassAlpha = 1f - 0.75f * smooth(122f, 140f, t)
    val appear = smooth(0f, 4f, t)

    // a soft shade behind the glass keeps the light legible over a bright sky
    scale(0.9f, 1.15f, Offset(cx, cy)) {
        drawCircle(Brush.radialGradient(listOf(INK.copy(alpha = 0.50f), INK.copy(alpha = 0.20f), Color.Transparent), Offset(cx, cy), gw * 1.15f),
            gw * 1.15f, Offset(cx, cy))
    }

    // in the last minute a warm light rises from the lower bulb and fills the screen
    if (spill > 0f) {
        val c = Offset(cx, cy + half * 0.55f * (1 - spread))
        val r = gw * (0.6f + 1.4f * spread)
        drawCircle(Brush.radialGradient(listOf(SAND.copy(alpha = 0.45f * spill), SAND.copy(alpha = 0.16f * spill), Color.Transparent), c, r), r, c)
    }

    // the glass outline and its lids
    if (glassAlpha > 0.01f) {
        val outline = Path()
        val steps = 48
        for (i in 0..steps) { val y = y0 + gh * i / steps; val x = cx - halfWidth(y); if (i == 0) outline.moveTo(x, y) else outline.lineTo(x, y) }
        for (i in steps downTo 0) { val y = y0 + gh * i / steps; outline.lineTo(cx + halfWidth(y), y) }
        outline.close()
        drawPath(outline, GLASS.copy(alpha = 0.05f * glassAlpha * appear))
        drawPath(outline, Color.White.copy(alpha = 0.06f * glassAlpha * appear), style = Stroke(9 * px))
        drawPath(outline, Color.White.copy(alpha = 0.42f * glassAlpha * appear), style = Stroke(1.4f * px))
        for (y in listOf(y0, y1)) {
            drawRoundRect(Ink.paper.copy(alpha = 0.55f * glassAlpha * appear), Offset(cx - gw * 0.56f, y - 3 * px), Size(gw * 1.12f, 6 * px), CornerRadius(3 * px))
        }
    }

    // the neck: a point of light while attention is gathered there
    val focus = gather * (1 - spill)
    if (focus > 0.01f) {
        val r = (16 + 6 * sin(t * 0.7f)) * px
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * focus), SAND.copy(alpha = 0.45f * focus), Color.Transparent), Offset(cx, cy), r * 2.2f),
            r * 2.2f, Offset(cx, cy))
    }

    for (g in grains) {
        // first minute: drifting through the upper bulb
        val yt = y0 + (cy - y0) * (0.14f + 0.78f * g.v) + gh * 0.015f * sin(t * g.sp * 0.25f + g.ph * 1.3f)
        val xt = cx + halfWidth(yt) * 0.84f * (g.u + 0.05f * sin(t * g.sp * 0.3f + g.ph)).coerceIn(-1f, 1f)
        // second minute: a thin stream falling through the neck
        val q = (t * 0.11f * g.sp + g.v) % 1f
        val ys = cy - gh * 0.10f + gh * 0.21f * q
        val xs = cx + neck * 0.9f * g.u * (1f - (abs(ys - cy) / (gh * 0.11f)).coerceAtMost(1f) * 0.4f)
        // third minute: settled in the lower bulb, then carried out across the screen
        val yb = cy + (y1 - cy) * (0.18f + 0.76f * g.w) + gh * 0.012f * sin(t * g.sp * 0.2f + g.ph)
        val xb = cx + halfWidth(yb) * 0.86f * g.u
        val xf = cx + (xb - cx) * (1f + 1.5f * spread) + w * 0.05f * sin(t * 0.08f * g.sp + g.ph) * spread
        val yf = yb + (h * (0.06f + 0.86f * g.v) - yb) * 0.85f * spread

        var x = xt + (xs - xt) * gather
        var y = yt + (ys - yt) * gather
        x += (xf - x) * spill
        y += (yf - y) * spill
        val twinkle = 0.55f + 0.45f * sin(t * g.sp * 1.3f + g.ph)
        val a = appear * twinkle * (if (gather > 0.5f && spill < 0.5f) 0.95f else 0.75f) * (1f - 0.15f * spread)
        val r = g.s * px * (if (gather > 0.5f && spill < 0.5f) 1.1f else 1.4f)
        drawCircle(SAND.copy(alpha = 0.16f * a), r * 3.2f, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.9f * a), r, Offset(x, y))
    }
}
