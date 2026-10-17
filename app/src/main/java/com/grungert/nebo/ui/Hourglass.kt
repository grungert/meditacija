package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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

// a painted palette: warm wood with a cool shadow side, mossy stone, sand like starlight
private val WOOD = Color(0xFF7A4B2E)
private val WOOD_LIT = Color(0xFFC48B5C)
private val WOOD_SHADE = Color(0xFF3E2A2E)
private val WOOD_LINE = Color(0xFF2E1D18)
private val STONE_LIT = Color(0xFFA9AEC0)
private val STONE = Color(0xFF6E7389)
private val STONE_SHADE = Color(0xFF41455A)
private val MOSS_LIT = Color(0xFFA8C46A)
private val MOSS = Color(0xFF6E9A47)
private val MOSS_DEEP = Color(0xFF3F6634)
private val SAND_LIGHT = Color(0xFFFFF0C8)
private val SAND = Color(0xFFF6CF86)
private val SAND_DEEP = Color(0xFFD99A55)
private val FIREFLY = Color(0xFFE9F7A8)

private class Grain(val u: Float, val v: Float, val ph: Float, val sp: Float, val s: Float, val w: Float)

private fun smooth(a: Float, b: Float, x: Float): Float {
    val k = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

/**
 * The three-minute breathing space as an old wooden hourglass standing on a mossy stone in the meadow, ivy climbing
 * one of its posts, its sand like starlight. Over the three minutes the upper heap runs down through the neck into a
 * growing heap below. Each minute also shows in the light: in the first, motes drift above the sand in the upper bulb
 * (the wide view of what is here); in the second the neck glows and the falling thread brightens (the breath alone);
 * in the third the lower heap glows and fireflies rise from the moss and drift out across the meadow (the whole body).
 */
@Composable
fun HourglassView(elapsed: Float, modifier: Modifier = Modifier) {
    val grains = remember {
        val r = Random(7)
        List(70) { Grain(r.nextFloat() * 2 - 1, r.nextFloat(), r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 0.8f, 0.7f + r.nextFloat() * 0.9f, r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) { hourglass(elapsed, grains) }
}

/** A tapered, turned post: a bulge at each of three rings, darker on the shadow side, a few lines of grain. */
private fun DrawScope.post(x: Float, top: Float, bottom: Float, wPost: Float, back: Boolean) {
    val px = density
    val h = bottom - top
    fun half(f: Float): Float {
        var r = wPost / 2 * (0.86f + 0.14f * (1 - abs(f - 0.5f) * 2))
        for (ring in listOf(0.12f, 0.5f, 0.88f)) r += wPost * 0.28f * (1 - ((f - ring) / 0.045f).let { it * it }).coerceAtLeast(0f)
        return r
    }
    val p = Path()
    val n = 40
    for (i in 0..n) { val f = i / n.toFloat(); val y = top + h * f; if (i == 0) p.moveTo(x - half(f), y) else p.lineTo(x - half(f), y) }
    for (i in n downTo 0) { val f = i / n.toFloat(); p.lineTo(x + half(f), top + h * f) }
    p.close()
    val dim = if (back) 0.55f else 1f
    drawPath(p, Brush.horizontalGradient(listOf(lerp(WOOD_LIT, WOOD, 0.2f), WOOD, WOOD_SHADE).map { lerp(WOOD_SHADE, it, dim) }, x - wPost, x + wPost))
    if (!back) {
        // grain: a few long hand-drawn lines
        for (k in 0 until 3) {
            val gx = x - wPost * 0.2f + k * wPost * 0.18f
            drawLine(WOOD_LINE.copy(alpha = 0.35f), Offset(gx, top + h * (0.05f + 0.1f * k)), Offset(gx + px * (k - 1), top + h * (0.55f + 0.12f * k)), 0.8f * px)
        }
        drawLine(Color.White.copy(alpha = 0.22f), Offset(x - wPost * 0.28f, top + h * 0.04f), Offset(x - wPost * 0.28f, bottom - h * 0.04f), 1.2f * px, StrokeCap.Round)
        drawPath(p, WOOD_LINE.copy(alpha = 0.55f), style = Stroke(1.1f * px))
    }
}

/** A plate of the frame: a slightly bowed slab, lit on top, with a darker underside and a line of grain. */
private fun DrawScope.plate(cx: Float, y: Float, w: Float, h: Float) {
    val px = density
    val p = Path().apply {
        moveTo(cx - w / 2, y + h * 0.25f)
        quadraticTo(cx - w / 2, y, cx - w / 2 + h * 0.6f, y)
        lineTo(cx + w / 2 - h * 0.6f, y)
        quadraticTo(cx + w / 2, y, cx + w / 2, y + h * 0.25f)
        lineTo(cx + w / 2 - h * 0.15f, y + h)
        lineTo(cx - w / 2 + h * 0.15f, y + h)
        close()
    }
    drawPath(p, Brush.verticalGradient(listOf(WOOD_LIT, WOOD, WOOD_SHADE), y, y + h))
    drawLine(WOOD_LINE.copy(alpha = 0.3f), Offset(cx - w * 0.38f, y + h * 0.55f), Offset(cx + w * 0.30f, y + h * 0.5f), 0.8f * px)
    drawLine(Color.White.copy(alpha = 0.3f), Offset(cx - w * 0.44f, y + 1.4f * px), Offset(cx + w * 0.44f, y + 1.4f * px), 1.1f * px, StrokeCap.Round)
    drawPath(p, WOOD_LINE.copy(alpha = 0.6f), style = Stroke(1.1f * px))
}

/** A low, rounded stone with a cap of moss, a few flowers and tufts of grass at its feet. */
private fun DrawScope.mossyStone(cx: Float, top: Float, w: Float, h: Float, t: Float) {
    val px = density
    val stone = Path()
    val n = 36
    for (i in 0..n) {
        val a = PI.toFloat() + PI.toFloat() * i / n            // the upper half, left to right
        val wob = 1f + 0.05f * sin(a * 5f + 1.3f) + 0.03f * sin(a * 11f)
        val x = cx + cos(a) * w / 2 * wob
        val y = top + h * 0.55f + sin(a) * h * 0.55f * wob
        if (i == 0) stone.moveTo(x, y) else stone.lineTo(x, y)
    }
    // the underside narrows to a rough, rounded point, like a clod torn from a hillside: points with a little jitter,
    // joined through their midpoints so the outline stays soft
    val under = ArrayList<Offset>()
    val m = 14
    for (i in 0..m) {
        val f = i / m.toFloat()                           // 0 at the right edge, 1 at the left
        val x = cx + w * (0.47f - 0.94f * f) * (1f - 0.55f * sin(f * PI.toFloat()))
        val depth = 0.62f + 1.35f * sin(f * PI.toFloat()).coerceAtLeast(0f).pow(1.6f)
        val jitter = 0.10f * sin(i * 2.7f + 0.4f) + 0.06f * sin(i * 5.3f)
        under += Offset(x, top + h * (depth + jitter))
    }
    var prevPt = Offset(cx + w * 0.5f, top + h * 0.55f)
    for (pt in under) {
        stone.quadraticTo(prevPt.x, prevPt.y, (prevPt.x + pt.x) / 2, (prevPt.y + pt.y) / 2)
        prevPt = pt
    }
    stone.quadraticTo(prevPt.x, prevPt.y, cx - w * 0.5f, top + h * 0.55f)
    stone.close()
    // roots hanging from the underside, stirring in the air
    for ((i, pt) in under.withIndex()) {
        if (i == 0 || i == m || i % 2 == 1) continue
        val len = h * (0.45f + 0.45f * ((i * 37) % 5) / 4f)
        val sway = sin(t * 0.7f + i * 1.3f) * 4 * px
        val root = Path().apply { moveTo(pt.x, pt.y - 2 * px); cubicTo(pt.x - 4 * px, pt.y + len * 0.4f, pt.x + 6 * px + sway, pt.y + len * 0.7f, pt.x + sway * 1.5f, pt.y + len) }
        drawPath(root, Color(0xFF4A3A2E).copy(alpha = 0.8f), style = Stroke((1.9f - 0.2f * (i % 3)) * px, cap = StrokeCap.Round))
        if (i % 4 == 2) drawCircle(MOSS.copy(alpha = 0.9f), 2.4f * px, Offset(pt.x + sway * 1.5f, pt.y + len))
    }
    drawPath(stone, Brush.linearGradient(listOf(STONE_LIT, STONE, STONE_SHADE), Offset(cx - w * 0.4f, top), Offset(cx + w * 0.3f, top + h * 1.9f)))
    clipPath(stone) {
        // earthy strata: short curved strokes, lighter near the top, the deepest part in shadow
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF2E2A3A).copy(alpha = 0.35f)), top + h * 0.8f, top + h * 2.1f),
            Offset(cx - w / 2, top + h * 0.8f), Size(w, h * 1.4f))
        val sr = Random(9)
        repeat(9) {
            val sy = top + h * (0.8f + 1.0f * sr.nextFloat())
            val sx = cx + (sr.nextFloat() - 0.5f) * w * 0.6f
            val len = w * (0.08f + 0.12f * sr.nextFloat())
            val strata = Path().apply { moveTo(sx - len, sy); quadraticTo(sx, sy + 3 * px, sx + len, sy - 1 * px) }
            drawPath(strata, Color(0xFF3A3448).copy(alpha = 0.45f), style = Stroke(1.2f * px, cap = StrokeCap.Round))
        }
    }
    val r = Random(3)
    clipPath(stone) {
        // painted dabs and a crack
        repeat(18) {
            val dx = cx + (r.nextFloat() - 0.5f) * w; val dy = top + h * (0.3f + r.nextFloat() * 0.7f)
            drawOval((if (r.nextBoolean()) STONE_LIT else STONE_SHADE).copy(alpha = 0.18f), Offset(dx, dy), Size(w * (0.05f + 0.08f * r.nextFloat()), h * 0.06f))
        }
        val crack = Path().apply { moveTo(cx + w * 0.18f, top + h * 0.45f); quadraticTo(cx + w * 0.24f, top + h * 0.62f, cx + w * 0.2f, top + h * 0.8f) }
        drawPath(crack, STONE_SHADE.copy(alpha = 0.7f), style = Stroke(1.2f * px, cap = StrokeCap.Round))
        // moss over the top, its edge made of many small tufts
        fun edge(f: Float) = top + h * (0.30f + 0.10f * sin(f * 9f + 0.5f) + 0.06f * sin(f * 23f)) + h * 0.12f * abs(f - 0.5f) * 2
        val moss = Path()
        moss.moveTo(cx - w * 0.52f, edge(0f))
        for (i in 0..24) { val f = i / 24f; moss.lineTo(cx - w * 0.5f + w * f, edge(f)) }
        moss.lineTo(cx + w * 0.52f, top - h * 0.2f); moss.lineTo(cx - w * 0.52f, top - h * 0.2f); moss.close()
        drawPath(moss, Brush.verticalGradient(listOf(MOSS_LIT, MOSS, MOSS_DEEP), top, top + h * 0.5f))
        repeat(40) {
            val f = r.nextFloat()
            drawCircle((if (r.nextBoolean()) MOSS else MOSS_DEEP).copy(alpha = 0.9f), (2f + 3f * r.nextFloat()) * px, Offset(cx - w * 0.5f + w * f, edge(f) - 2 * px))
        }
        repeat(26) {
            drawCircle(MOSS_LIT.copy(alpha = 0.55f), (1.2f + 1.8f * r.nextFloat()) * px, Offset(cx + (r.nextFloat() - 0.5f) * w * 0.85f, top + h * (0.02f + 0.22f * r.nextFloat())))
        }
    }
    // a few small flowers in the moss, nodding a little
    val fr = Random(11)
    repeat(5) { i ->
        val fx = cx + (fr.nextFloat() - 0.5f) * w * 0.8f
        val fy = top + h * (0.06f + 0.12f * fr.nextFloat())
        val sway = sin(t * 0.9f + i) * 1.2f * px
        val petal = if (i % 2 == 0) Color(0xFFFFF7F0) else Color(0xFFF6C9DA)
        drawLine(MOSS_DEEP, Offset(fx, fy + 6 * px), Offset(fx + sway, fy), 1f * px)
        for (k in 0 until 5) {
            val a = k * 2 * PI.toFloat() / 5 + i
            drawCircle(petal, 1.7f * px, Offset(fx + sway + cos(a) * 2.2f * px, fy + sin(a) * 2.2f * px))
        }
        drawCircle(Color(0xFFFFD36B), 1.2f * px, Offset(fx + sway, fy))
    }
    // tufts of grass at the island's edges
    for ((side, base) in listOf(-1f to cx - w * 0.40f, 1f to cx + w * 0.38f)) for (k in 0 until 6) {
        val bx = base + side * k * 3 * px
        val ground = top + h * 0.30f
        val tall = h * (0.45f + 0.35f * ((k * 37) % 5) / 4f)
        val sway = sin(t * 1.1f + k + side) * 2.5f * px
        val blade = Path().apply { moveTo(bx, ground); quadraticTo(bx + side * 4 * px, ground - tall * 0.6f, bx + side * (6 + k) * px + sway, ground - tall) }
        drawPath(blade, (if (k % 2 == 0) MOSS else MOSS_DEEP), style = Stroke(1.6f * px, cap = StrokeCap.Round))
    }
}

/** Ivy climbing a post: a wandering stem and pointed leaves in two greens, stirring a little. */
private fun DrawScope.ivy(x: Float, bottom: Float, top: Float, t: Float) {
    val px = density
    val n = 30
    val pts = List(n + 1) { i ->
        val f = i / n.toFloat()
        Offset(x + sin(f * 11f) * 6 * px, bottom - (bottom - top) * f * 0.9f)
    }
    val stem = Path()
    pts.forEachIndexed { i, p -> if (i == 0) stem.moveTo(p.x, p.y) else stem.lineTo(p.x, p.y) }
    drawPath(stem, MOSS_DEEP, style = Stroke(1.4f * px, cap = StrokeCap.Round))
    for (i in 1..n step 2) {
        val p = pts[i]
        val side = if (i % 2 == 0) 1f else -1f
        val s = (11f - 5f * i / n) * px
        val sway = sin(t * 0.8f + i) * 6f
        rotate(side * 35f + sway, p) {
            val leaf = Path().apply {
                moveTo(p.x, p.y)
                quadraticTo(p.x + side * s * 1.1f, p.y - s * 0.9f, p.x + side * s * 1.6f, p.y)
                quadraticTo(p.x + side * s * 1.1f, p.y + s * 0.8f, p.x, p.y)
                close()
            }
            drawPath(leaf, if (i % 6 == 2) MOSS_LIT else MOSS)
            drawLine(MOSS_DEEP.copy(alpha = 0.7f), p, Offset(p.x + side * s * 1.3f, p.y), 0.7f * px)
        }
    }
}

private fun DrawScope.hourglass(t: Float, grains: List<Grain>) {
    val w = size.width; val h = size.height; val px = density
    val cx = w / 2
    val gh = min(h * 0.34f, w * 0.95f)
    val gw = gh * 0.56f
    val half = gh / 2
    val cy = h * 0.33f
    val y0 = cy - half; val y1 = cy + half
    val neck = gw * 0.045f
    fun halfWidth(y: Float): Float {
        val s = (abs(y - cy) / half).coerceIn(0f, 1f)
        return neck + (gw / 2 - neck) * sin(s * PI.toFloat() / 2).pow(0.55f)
    }
    val k = (t / HOURGLASS_SECONDS).coerceIn(0f, 1f)          // how much sand has run
    val gather = smooth(60f, 66f, t) * (1 - smooth(120f, 128f, t))
    val rise = smooth(120f, 130f, t)
    val spread = smooth(126f, 172f, t)
    val appear = smooth(0f, 2.5f, t)
    val plateH = 14 * px
    val frameW = gw * 1.22f
    val stoneTop = y1 + plateH * 0.9f

    // a warm light around the whole, brighter as the last minute fills everything
    val glowC = Offset(cx, cy + half * 0.4f)
    val glowR = gw * (1.3f + 1.3f * spread)
    drawCircle(Brush.radialGradient(listOf(SAND.copy(alpha = (0.16f + 0.26f * rise) * appear), Color.Transparent), glowC, glowR), glowR, glowC)

    mossyStone(cx, stoneTop, frameW * 1.5f, gh * 0.27f, t)
    // the hourglass's shadow on the moss
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(cx - frameW * 0.55f, stoneTop - 1 * px), Size(frameW * 1.1f, plateH * 0.9f))


    // the glass, tinted a little by the sky
    val glass = Path()
    val steps = 48
    for (i in 0..steps) { val y = y0 + gh * i / steps; val x = cx - halfWidth(y); if (i == 0) glass.moveTo(x, y) else glass.lineTo(x, y) }
    for (i in steps downTo 0) { val y = y0 + gh * i / steps; glass.lineTo(cx + halfWidth(y), y) }
    glass.close()
    drawPath(glass, Brush.verticalGradient(listOf(Color(0xFFE6F2FF).copy(alpha = 0.30f), Color(0xFFDDEBFF).copy(alpha = 0.10f), Color(0xFFE6F2FF).copy(alpha = 0.22f)), y0, y1))

    // the sand: starlight-coloured, grainy, glowing softly
    val specks = Random(5)
    fun sandFill(heap: Path, top: Float, bottom: Float, glow: Float) {
        drawPath(heap, SAND.copy(alpha = (0.35f * glow).coerceAtMost(1f)), style = Stroke(7 * px))
        drawPath(heap, Brush.verticalGradient(listOf(SAND_LIGHT, SAND, SAND_DEEP), top, bottom))
        clipPath(heap) {
            repeat(90) {
                val x = cx + (specks.nextFloat() - 0.5f) * gw
                val y = top + (bottom - top) * specks.nextFloat()
                drawCircle((if (specks.nextFloat() < 0.5f) SAND_DEEP else Color.White).copy(alpha = 0.35f), 0.9f * px, Offset(x, y))
            }
            drawRect(Brush.horizontalGradient(listOf(SAND_DEEP.copy(alpha = 0.45f), Color.Transparent, Color.Transparent, SAND_DEEP.copy(alpha = 0.45f)),
                cx - gw / 2, cx + gw / 2), Offset(cx - gw / 2, top), Size(gw, bottom - top))
        }
    }
    if (k < 0.995f) {
        val level = cy - half * 0.84f * (1 - k)
        val dip = 7 * px * (1 - k)
        val heap = Path()
        val hw = halfWidth(level) * 0.96f
        heap.moveTo(cx - hw, level)
        for (i in 0..20) { val f = i / 20f; val x = cx - hw + 2 * hw * f; val u = (x - cx) / hw; heap.lineTo(x, level + dip * (1 - u * u)) }
        for (i in 0..24) { val y = level + (cy - level) * i / 24f; heap.lineTo(cx + halfWidth(y) * 0.96f, y) }
        for (i in 24 downTo 0) { val y = level + (cy - level) * i / 24f; heap.lineTo(cx - halfWidth(y) * 0.96f, y) }
        heap.close()
        sandFill(heap, level, cy, 0.6f)
    }
    if (k > 0.005f) {
        val height = half * 0.62f * k.pow(0.8f)
        val base = y1 - 2 * px
        val top = base - height
        val heap = Path()
        heap.moveTo(cx - halfWidth(base) * 0.96f, base)
        for (i in 0..24) { val y = base - (base - top) * 0.55f * i / 24f; heap.lineTo(cx - halfWidth(y) * 0.96f, y) }
        val shoulder = base - (base - top) * 0.55f
        val sw = halfWidth(shoulder) * 0.96f
        heap.quadraticTo(cx - sw * 0.3f, top - height * 0.08f, cx, top)
        heap.quadraticTo(cx + sw * 0.3f, top - height * 0.08f, cx + sw, shoulder)
        for (i in 24 downTo 0) { val y = base - (base - top) * 0.55f * i / 24f; heap.lineTo(cx + halfWidth(y) * 0.96f, y) }
        heap.close()
        sandFill(heap, top, base, 0.6f + 1.2f * rise)
    }
    // the falling thread of sand, brightest in the second minute
    if (k < 0.995f) {
        val bottomTop = y1 - half * 0.62f * k.pow(0.8f) - 2 * px
        val sw = (1.3f + 1.2f * gather) * px
        drawLine(SAND.copy(alpha = 0.22f * (0.6f + gather)), Offset(cx, cy), Offset(cx, bottomTop), sw * 6, StrokeCap.Round)
        drawLine(SAND_LIGHT, Offset(cx, cy - 2 * px), Offset(cx, bottomTop), sw, StrokeCap.Round)
    }
    if (gather > 0.01f) {
        val r = (22 + 5 * sin(t * 0.7f)) * px
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * gather), SAND.copy(alpha = 0.35f * gather), Color.Transparent), Offset(cx, cy), r * 2f),
            r * 2f, Offset(cx, cy))
    }

    // painted gleams on the glass: a long soft one on the left of each bulb, a short bright one beside it, a dot on the right
    for (topBulb in listOf(true, false)) {
        fun gleam(inset: Float, from: Float, to: Float, width: Float, alpha: Float) {
            val g = Path()
            for (i in 0..16) {
                val f = from + (to - from) * i / 16f
                val y = if (topBulb) cy - half * f else cy + half * f
                val x = cx - halfWidth(y) * inset
                if (i == 0) g.moveTo(x, y) else g.lineTo(x, y)
            }
            drawPath(g, Color.White.copy(alpha = alpha), style = Stroke(width * px, cap = StrokeCap.Round))
        }
        gleam(0.80f, 0.25f, 0.85f, 6f, 0.10f)
        gleam(0.80f, 0.32f, 0.70f, 2.2f, 0.45f)
        gleam(0.62f, 0.55f, 0.66f, 1.6f, 0.35f)
        val dy = if (topBulb) cy - half * 0.6f else cy + half * 0.6f
        drawCircle(Color.White.copy(alpha = 0.4f), 2.2f * px, Offset(cx + halfWidth(dy) * 0.62f, dy))
    }
    drawPath(glass, Color(0xFF5B6E8C).copy(alpha = 0.45f), style = Stroke(1.2f * px))

    // the frame: two plates, two turned posts in front, ivy up the right one
    val postX = listOf(cx - frameW / 2 + 7 * px, cx + frameW / 2 - 7 * px)
    for (x in postX) post(x, y0, y1, 10 * px, back = false)
    plate(cx, y0 - plateH, frameW + 14 * px, plateH)
    plate(cx, y1, frameW + 14 * px, plateH)
    ivy(postX[1], y1 + plateH, y0, t)
    ivy(postX[0], y1 + plateH, y1 - gh * 0.45f, t + 2f)

    // motes: drifting in the upper bulb, gathering along the thread, then fireflies rising from the moss
    for (g in grains) {
        val level = cy - half * 0.84f * (1 - k)
        val yt = y0 + (level - y0) * (0.15f + 0.75f * g.v) + gh * 0.012f * sin(t * g.sp * 0.25f + g.ph * 1.3f)
        val xt = cx + halfWidth(yt) * 0.78f * (g.u + 0.05f * sin(t * g.sp * 0.3f + g.ph)).coerceIn(-1f, 1f)
        val q = (t * 0.12f * g.sp + g.v) % 1f
        val ys = cy - gh * 0.08f + gh * 0.30f * q
        val xs = cx + neck * 1.2f * g.u * (1f - q)
        val lift = (t * 0.035f * g.sp + g.w) % 1f
        val yf = stoneTop - (stoneTop - h * 0.05f) * lift * (0.35f + 0.65f * spread)
        val xf = cx + w * 0.46f * g.u * (0.3f + 0.7f * spread) + w * 0.04f * sin(t * 0.6f * g.sp + g.ph)
        var x = xt + (xs - xt) * gather
        var y = yt + (ys - yt) * gather
        x += (xf - x) * rise
        y += (yf - y) * rise
        val blink = 0.5f + 0.5f * sin(t * g.sp * 1.6f + g.ph)
        val fade = if (rise > 0.5f) (1f - lift).coerceIn(0f, 1f) * 1.3f else 1f
        val a = (appear * fade * (0.35f + 0.65f * blink)).coerceIn(0f, 1f)
        val color = lerp(SAND_LIGHT, FIREFLY, rise)
        val r = g.s * px * (1f + 0.6f * rise)
        drawCircle(color.copy(alpha = 0.22f * a), r * 4f, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.9f * a), r, Offset(x, y))
    }
}
