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
import androidx.compose.ui.graphics.StrokeCap
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

private val WOOD = Color(0xFF8A5A3B)
private val WOOD_DARK = Color(0xFF4E3121)
private val WOOD_LIGHT = Color(0xFFC08A5E)

/**
 * The three-minute breathing space as an hourglass: a wooden frame, glass that catches the light, and sand made of
 * light. Over the three minutes the upper heap runs down through the neck and the lower one grows, like any hourglass.
 * The minutes show in the light as well: in the first, motes drift through the whole upper bulb (the wide view of what is
 * here); in the second the falling stream brightens and everything gathers in the neck (the breath alone); in the third
 * the lower heap glows and motes rise from it and spread across the screen (the whole body).
 */
@Composable
fun HourglassView(elapsed: Float, modifier: Modifier = Modifier) {
    val grains = remember {
        val r = Random(7)
        List(110) { Grain(r.nextFloat() * 2 - 1, r.nextFloat(), r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 0.8f, 0.7f + r.nextFloat() * 0.9f, r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) { hourglass(elapsed, grains) }
}

/** Sand is darker where it meets the glass and catches a few glints of light on its surface. */
private fun DrawScope.shadeHeap(heap: Path, cx: Float, hw: Float, t: Float, surface: Float, appear: Float) {
    drawPath(heap, Brush.horizontalGradient(listOf(Color(0xFF8A4E1E).copy(alpha = 0.28f * appear), Color.Transparent, Color.Transparent,
        Color(0xFF8A4E1E).copy(alpha = 0.28f * appear)), cx - hw, cx + hw))
    for (i in 0 until 7) {
        val x = cx + hw * (((i * 37) % 13) / 6.5f - 1f) * 0.7f
        val tw = (sin(t * (1.1f + i * 0.37f) + i * 2.1f) * 0.5f + 0.5f).pow(3)
        drawCircle(Color.White.copy(alpha = 0.85f * tw * appear), 1.3f * density, Offset(x, surface + (i % 3) * 3 * density))
    }
}

private fun DrawScope.hourglass(t: Float, grains: List<Grain>) {
    val w = size.width; val h = size.height; val px = density
    val cx = w / 2; val cy = h * 0.42f
    val gh = min(h * 0.44f, w * 1.1f)
    val gw = gh * 0.56f
    val half = gh / 2
    val y0 = cy - half; val y1 = cy + half
    val neck = gw * 0.04f
    fun halfWidth(y: Float): Float {
        val s = (abs(y - cy) / half).coerceIn(0f, 1f)
        return neck + (gw / 2 - neck) * sin(s * PI.toFloat() / 2).pow(0.55f)
    }
    val k = (t / HOURGLASS_SECONDS).coerceIn(0f, 1f)          // how much sand has run
    val gather = smooth(60f, 66f, t) * (1 - smooth(120f, 128f, t))
    val rise = smooth(120f, 130f, t)
    val spread = smooth(126f, 172f, t)
    val appear = smooth(0f, 3f, t)
    val plate = 12 * px

    // a soft shade behind, and the shadow the hourglass casts on what it stands on
    scale(0.95f, 1.15f, Offset(cx, cy)) {
        drawCircle(Brush.radialGradient(listOf(INK.copy(alpha = 0.52f), INK.copy(alpha = 0.22f), Color.Transparent), Offset(cx, cy), gw * 1.25f),
            gw * 1.25f, Offset(cx, cy))
    }
    drawOval(Color.Black.copy(alpha = 0.25f * appear), Offset(cx - gw * 0.75f, y1 + plate * 0.6f), Size(gw * 1.5f, plate * 1.6f))

    // in the last minute a warm light rises from the lower heap and fills everything
    if (rise > 0f) {
        val c = Offset(cx, y1 - half * 0.25f - half * 0.6f * spread)
        val r = gw * (0.7f + 1.5f * spread)
        drawCircle(Brush.radialGradient(listOf(SAND.copy(alpha = 0.40f * rise), SAND.copy(alpha = 0.14f * rise), Color.Transparent), c, r), r, c)
    }

    // the glass
    val glass = Path()
    val steps = 48
    for (i in 0..steps) { val y = y0 + gh * i / steps; val x = cx - halfWidth(y); if (i == 0) glass.moveTo(x, y) else glass.lineTo(x, y) }
    for (i in steps downTo 0) { val y = y0 + gh * i / steps; glass.lineTo(cx + halfWidth(y), y) }
    glass.close()
    drawPath(glass, Brush.horizontalGradient(listOf(GLASS.copy(alpha = 0.16f * appear), GLASS.copy(alpha = 0.05f * appear), GLASS.copy(alpha = 0.12f * appear)), cx - gw / 2, cx + gw / 2))

    // the sand: the upper heap sinks towards the neck with a small hollow where it runs out; the lower one grows to a peak
    val sandTop = SAND; val sandDeep = Color(0xFFE8A24E)
    if (k < 0.995f) {
        val level = cy - half * 0.86f * (1 - k)
        val dip = 7 * px * (1 - k)
        val heap = Path()
        val hw = halfWidth(level) * 0.97f
        heap.moveTo(cx - hw, level)
        for (i in 0..20) { val f = i / 20f; val x = cx - hw + 2 * hw * f; val u = (x - cx) / hw; heap.lineTo(x, level + dip * (1 - u * u)) }
        for (i in 0..24) { val y = level + (cy - level) * i / 24f; heap.lineTo(cx + halfWidth(y) * 0.97f, y) }
        for (i in 24 downTo 0) { val y = level + (cy - level) * i / 24f; heap.lineTo(cx - halfWidth(y) * 0.97f, y) }
        heap.close()
        drawPath(heap, Brush.verticalGradient(listOf(sandTop.copy(alpha = 0.95f * appear), sandDeep.copy(alpha = 0.9f * appear)), level, cy))
        shadeHeap(heap, cx, hw, t, level + dip * 0.5f, appear)
    }
    if (k > 0.005f) {
        val height = half * 0.62f * k.pow(0.8f)
        val base = y1 - 2 * px
        val top = base - height
        val heap = Path()
        heap.moveTo(cx - halfWidth(base) * 0.97f, base)
        for (i in 0..24) { val y = base - (base - top) * 0.55f * i / 24f; heap.lineTo(cx - halfWidth(y) * 0.97f, y) }
        val shoulder = base - (base - top) * 0.55f
        val sw = halfWidth(shoulder) * 0.97f
        heap.quadraticTo(cx - sw * 0.3f, top - height * 0.08f, cx, top)
        heap.quadraticTo(cx + sw * 0.3f, top - height * 0.08f, cx + sw, shoulder)
        for (i in 24 downTo 0) { val y = base - (base - top) * 0.55f * i / 24f; heap.lineTo(cx + halfWidth(y) * 0.97f, y) }
        heap.close()
        val glow = 1f + 0.4f * rise
        drawPath(heap, Brush.verticalGradient(listOf(sandTop.copy(alpha = (0.95f * appear).coerceAtMost(1f)), sandDeep.copy(alpha = 0.9f * appear)), top, base))
        shadeHeap(heap, cx, halfWidth(base) * 0.97f, t, top + height * 0.25f, appear)
        if (rise > 0f) drawPath(heap, SAND.copy(alpha = 0.25f * rise * glow), style = Stroke(6 * px))
    }
    // the falling stream: always there while sand runs, brightest in the second minute
    if (k < 0.995f) {
        val bottomTop = y1 - half * 0.62f * k.pow(0.8f) - 2 * px
        val sw = (1.4f + 1.4f * gather) * px
        drawLine(SAND.copy(alpha = 0.85f * appear), Offset(cx, cy - 2 * px), Offset(cx, bottomTop), sw, StrokeCap.Round)
        drawLine(SAND.copy(alpha = 0.18f * appear * (0.5f + gather)), Offset(cx, cy), Offset(cx, bottomTop), sw * 5, StrokeCap.Round)
    }
    // the neck glows while attention gathers there
    if (gather > 0.01f) {
        val r = (20 + 6 * sin(t * 0.7f)) * px
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * gather), SAND.copy(alpha = 0.40f * gather), Color.Transparent), Offset(cx, cy), r * 2f),
            r * 2f, Offset(cx, cy))
    }

    // the glass's edge and its highlights: a long curved gleam on the left of each bulb, a small one on the right
    drawPath(glass, Color.White.copy(alpha = 0.10f * appear), style = Stroke(8 * px))
    drawPath(glass, Color.White.copy(alpha = 0.55f * appear), style = Stroke(1.3f * px))
    for (top in listOf(true, false)) {
        val gleam = Path()
        for (i in 0..16) {
            val f = 0.22f + 0.6f * i / 16f
            val y = if (top) cy - half * f else cy + half * f
            val x = cx - halfWidth(y) * 0.78f
            if (i == 0) gleam.moveTo(x, y) else gleam.lineTo(x, y)
        }
        drawPath(gleam, Color.White.copy(alpha = 0.32f * appear), style = Stroke(3 * px, cap = StrokeCap.Round))
        val dy = if (top) cy - half * 0.62f else cy + half * 0.62f
        drawCircle(Color.White.copy(alpha = 0.30f * appear), 2.4f * px, Offset(cx + halfWidth(dy) * 0.66f, dy))
    }

    // the wooden frame: two plates and two turned posts
    val fw = gw * 1.18f
    for (posX in listOf(cx - fw / 2 + 6 * px, cx + fw / 2 - 6 * px)) {
        drawRoundRect(Brush.horizontalGradient(listOf(WOOD_LIGHT, WOOD, WOOD_DARK), posX - 4 * px, posX + 4 * px),
            Offset(posX - 4 * px, y0), Size(8 * px, gh), CornerRadius(4 * px), alpha = appear)
        for (by in listOf(y0 + gh * 0.25f, cy, y0 + gh * 0.75f)) drawOval(WOOD_LIGHT.copy(alpha = appear), Offset(posX - 6 * px, by - 4 * px), Size(12 * px, 8 * px))
    }
    for (py in listOf(y0 - plate, y1)) {
        drawRoundRect(Brush.verticalGradient(listOf(WOOD_LIGHT, WOOD, WOOD_DARK), py, py + plate), Offset(cx - fw / 2 - 6 * px, py), Size(fw + 12 * px, plate),
            CornerRadius(5 * px), alpha = appear)
        drawLine(Color.White.copy(alpha = 0.25f * appear), Offset(cx - fw / 2, py + 1.5f * px), Offset(cx + fw / 2, py + 1.5f * px), 1.2f * px)
    }

    // motes of light: drifting in the upper bulb, then along the stream, then rising out and across the screen
    for (g in grains) {
        val level = cy - half * 0.86f * (1 - k)
        val yt = y0 + (level - y0) * (0.12f + 0.80f * g.v) + gh * 0.012f * sin(t * g.sp * 0.25f + g.ph * 1.3f)
        val xt = cx + halfWidth(yt) * 0.8f * (g.u + 0.05f * sin(t * g.sp * 0.3f + g.ph)).coerceIn(-1f, 1f)
        val q = (t * 0.12f * g.sp + g.v) % 1f
        val ys = cy - gh * 0.08f + gh * 0.30f * q
        val xs = cx + neck * 1.4f * g.u * (1f - q)
        val lift = (t * 0.03f * g.sp + g.w) % 1f
        val yf = y1 - half * 0.3f - (y1 - h * 0.04f) * lift * (0.4f + 0.6f * spread)
        val xf = cx + (w * 0.48f * g.u) * (0.25f + 0.75f * spread) + w * 0.03f * sin(t * 0.5f * g.sp + g.ph)
        var x = xt + (xs - xt) * gather
        var y = yt + (ys - yt) * gather
        x += (xf - x) * rise
        y += (yf - y) * rise
        val fade = if (rise > 0f) (1f - lift).coerceIn(0f, 1f) * 1.2f else 1f
        val tw = 0.55f + 0.45f * sin(t * g.sp * 1.3f + g.ph)
        val a = (appear * tw * fade * (if (gather > 0.5f) 0.9f else 0.7f)).coerceIn(0f, 1f)
        val r = g.s * px * (if (gather > 0.5f) 1.0f else 1.3f) * (1f + 0.5f * rise)
        drawCircle(SAND.copy(alpha = 0.18f * a), r * 3.4f, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.9f * a), r, Offset(x, y))
    }
}
