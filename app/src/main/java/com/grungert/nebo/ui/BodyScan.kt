package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import com.grungert.nebo.data.BodyMark
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A part of the body the scan rests on: where it sits on the figure (in figure units, 0..1 of its width and height),
 * what to notice there, the body point a touch there becomes in a note, and how to say "in it".
 */
class BodyRegion(
    val id: String, val name: String, val line: String,
    val centers: List<Offset>, val rx: Float, val ry: Float,
    val label: String, val where: String,
)

/** The scan's path, from the feet up to the crown, then the whole body at once. */
val BODY_REGIONS = listOf(
    BodyRegion("stopala", "Stopala", "Tabani, prsti, pete. Mjesto gdje dodiruju pod.",
        listOf(Offset(0.40f, 0.965f), Offset(0.60f, 0.965f)), 0.10f, 0.03f, "napetost u stopalima", "u stopalima"),
    BodyRegion("noge", "Noge", "Listovi, koljena, butine. Kolika je težina nogu?",
        listOf(Offset(0.395f, 0.75f), Offset(0.605f, 0.75f)), 0.09f, 0.18f, "napetost u nogama", "u nogama"),
    BodyRegion("karlica", "Karlica", "Kukovi i mjesto na kojem sjediš. Neka te podloga nosi.",
        listOf(Offset(0.5f, 0.53f)), 0.25f, 0.04f, "napetost u karlici", "u karlici"),
    BodyRegion("stomak", "Stomak", "Kako se stomak diže i spušta s dahom.",
        listOf(Offset(0.5f, 0.455f)), 0.17f, 0.045f, "stisnut stomak", "u stomaku"),
    BodyRegion("leđa", "Leđa", "Od krsta do lopatica. Gdje su leđa meka, a gdje stegnuta?",
        listOf(Offset(0.5f, 0.35f)), 0.22f, 0.12f, "napetost u leđima", "u leđima"),
    BodyRegion("grudi", "Grudi", "Dah koji širi grudi. Srce ispod njih.",
        listOf(Offset(0.5f, 0.29f)), 0.21f, 0.05f, "stezanje u grudima", "u grudima"),
    BodyRegion("ruke", "Ruke", "Šake, prsti, laktovi, nadlaktice. Šta osjećaju dlanovi?",
        listOf(Offset(0.15f, 0.42f), Offset(0.85f, 0.42f)), 0.08f, 0.17f, "napetost u rukama", "u rukama"),
    BodyRegion("ramena", "Ramena i vrat", "Ramena često nose ono što ne moraju. Mogu li da se spuste?",
        listOf(Offset(0.33f, 0.215f), Offset(0.67f, 0.215f)), 0.11f, 0.03f, "napeta ramena", "u ramenima"),
    BodyRegion("grlo", "Grlo", "Primijeti gutanje i dah koji prolazi kroz grlo.",
        listOf(Offset(0.5f, 0.165f)), 0.06f, 0.025f, "knedla u grlu", "u grlu"),
    BodyRegion("lice", "Lice", "Vilica, usne, obrazi, oči, čelo. Neka lice omekša.",
        listOf(Offset(0.5f, 0.095f)), 0.10f, 0.03f, "stegnuta vilica", "u vilici"),
    BodyRegion("glava", "Glava", "Tjeme, potiljak, prostor iza očiju.",
        listOf(Offset(0.5f, 0.04f)), 0.11f, 0.028f, "pritisak u glavi", "u glavi"),
    BodyRegion(WHOLE_BODY, "Cijelo tijelo", "Sve odjednom: jedno tijelo koje diše.",
        listOf(Offset(0.5f, 0.5f)), 0.5f, 0.5f, "", "u cijelom tijelu"),
)
const val WHOLE_BODY = "cijelo"

fun bodyRegion(id: String) = BODY_REGIONS.firstOrNull { it.id == id }

/** The body points of notes that name a place, mapped back to the region they sit in. */
private val LABEL_REGION = mapOf("lupanje srca" to "grudi") + BODY_REGIONS.filter { it.label.isNotEmpty() }.associate { it.label to it.id }

fun regionOfLabel(label: String): String? = LABEL_REGION[label]

/** The width of the figure for its height. */
const val FIGURE_ASPECT = 0.42f

/** The figure as large as fits in [area], centred. */
fun figureBox(area: Rect): Rect {
    val fh = min(area.height, area.width / FIGURE_ASPECT)
    val fw = fh * FIGURE_ASPECT
    val left = area.center.x - fw / 2
    val top = area.center.y - fh / 2
    return Rect(left, top, left + fw, top + fh)
}

/**
 * Which region the scan is on and how many seconds it has been there, for a session of [planned] seconds:
 * each part an equal share and the whole body one and a half. Open-ended (0), each part has 45 seconds
 * and the whole body stays.
 */
fun scanStage(elapsed: Float, planned: Int): Triple<Int, Float, Float> {
    val n = BODY_REGIONS.size
    val unit = if (planned > 0) planned / (n - 1 + 1.5f) else 45f
    var t = elapsed
    for (i in 0 until n) {
        val len = if (i == n - 1) unit * 1.5f else unit
        if (t < len || i == n - 1) return Triple(i, t, len)
        t -= len
    }
    return Triple(n - 1, 0f, unit)
}

/**
 * The region a touch at [pos] (on a canvas where the figure stands in [box]) belongs to, or null when it missed the body.
 * The part the scan is on wins when the touch is near it; the back is only reachable while the scan is on it.
 */
fun regionAt(pos: Offset, box: Rect, current: String?): String? {
    val nx = (pos.x - box.left) / box.width
    val ny = (pos.y - box.top) / box.height
    fun d(r: BodyRegion) = r.centers.minOf { c -> ((nx - c.x) / r.rx).let { it * it } + ((ny - c.y) / r.ry).let { it * it } }
    BODY_REGIONS.firstOrNull { it.id == current && it.id != WHOLE_BODY }?.let { if (d(it) <= 1.8f) return it.id }
    val best = BODY_REGIONS.filter { it.id != WHOLE_BODY && (it.id != "leđa" || current == "leđa") }.minByOrNull { d(it) } ?: return null
    return best.id.takeIf { d(best) <= 2.4f }
}

/** A limb between two points: a capsule outline, so all the parts can be joined into one shape. */
private fun capsule(a: Offset, b: Offset, r: Float): Path {
    val ang = atan2(b.y - a.y, b.x - a.x)
    val path = Path()
    val steps = 12
    for (i in 0..steps) {
        val t = ang + PI.toFloat() / 2 - PI.toFloat() * i / steps
        val x = b.x + r * cos(t); val y = b.y + r * sin(t)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (i in 0..steps) {
        val t = ang - PI.toFloat() / 2 - PI.toFloat() * i / steps
        path.lineTo(a.x + r * cos(t), a.y + r * sin(t))
    }
    path.close()
    return path
}

/** A standing figure, arms a little away from the body, as one outline: head, neck, torso, arms, hands, legs, feet. */
fun figurePath(box: Rect): Path {
    val bw = box.width; val bh = box.height
    fun p(x: Float, y: Float) = Offset(box.left + x * bw, box.top + y * bh)
    fun oval(c: Offset, w: Float, h: Float) = Path().apply { addOval(Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)) }
    val parts = ArrayList<Path>()
    parts += oval(p(0.5f, 0.0745f), 0.23f * bw, 0.125f * bh)
    parts += capsule(p(0.5f, 0.11f), p(0.5f, 0.2f), 0.0575f * bw)
    parts += Path().apply {
        val a = p(0.24f, 0.205f); moveTo(a.x, a.y)
        fun cubic(c1: Offset, c2: Offset, e: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
        cubic(p(0.40f, 0.18f), p(0.60f, 0.18f), p(0.76f, 0.205f))
        cubic(p(0.80f, 0.27f), p(0.71f, 0.35f), p(0.695f, 0.42f))
        cubic(p(0.69f, 0.47f), p(0.75f, 0.50f), p(0.745f, 0.55f))
        lineTo(p(0.255f, 0.55f).x, p(0.255f, 0.55f).y)
        cubic(p(0.25f, 0.50f), p(0.31f, 0.47f), p(0.305f, 0.42f))
        cubic(p(0.29f, 0.35f), p(0.20f, 0.27f), a)
        close()
    }
    for (left in listOf(true, false)) {
        fun q(x: Float, y: Float) = p(if (left) x else 1f - x, y)
        parts += capsule(q(0.25f, 0.23f), q(0.165f, 0.39f), 0.05f * bw)       // upper arm
        parts += capsule(q(0.165f, 0.39f), q(0.13f, 0.535f), 0.039f * bw)     // forearm
        parts += oval(q(0.125f, 0.568f), 0.08f * bw, 0.056f * bh)            // hand
        parts += capsule(q(0.385f, 0.53f), q(0.395f, 0.75f), 0.0775f * bw)    // thigh
        parts += capsule(q(0.395f, 0.75f), q(0.405f, 0.94f), 0.054f * bw)     // shin
        parts += oval(q(0.40f, 0.965f), 0.15f * bw, 0.04f * bh)              // foot
    }
    var body = parts[0]
    for (part in parts.drop(1)) body = Path().also { it.op(body, part, PathOperation.Union) }
    return body
}

private val INK = Color(0xFF14223F)
private val LIGHT = Color(0xFFFFF1D6)
/** Tension marks: a warm coral, the colour of heat held in the body. */
val TENSION = Color(0xFFF2A88E)

/** The figure of light: a soft shade behind it, a faint glow around it, a pale fill and a thin bright outline. */
private fun DrawScope.figureBase(box: Rect, path: Path, fill: Float) {
    scale(0.78f, 1f, box.center) {
        drawCircle(Brush.radialGradient(listOf(INK.copy(alpha = 0.42f), INK.copy(alpha = 0.16f), Color.Transparent), box.center, box.height * 0.62f),
            box.height * 0.62f, box.center)
    }
    drawPath(path, Color.White.copy(alpha = 0.05f), style = Stroke(18 * density))
    drawPath(path, Color.White.copy(alpha = 0.08f), style = Stroke(8 * density))
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFFE6EEFF).copy(alpha = fill), Color(0xFFD8E4FF).copy(alpha = fill * 0.7f)), box.top, box.bottom))
}

/**
 * The body scan: a figure of light, with a warm light resting on one part after another, from the feet up.
 * [marks] are the places touched (with the second each was touched, for a small ripple). [onBox] reports where
 * the figure stands, so a touch can be matched to a part.
 */
@Composable
fun BodyScanView(elapsed: Float, planned: Int, area: Rect?, marks: List<Pair<BodyMark, Float>>, modifier: Modifier = Modifier, onBox: (Rect) -> Unit) {
    val cache = remember { HashMap<Rect, Path>() }
    Canvas(modifier.fillMaxSize()) {
        val a = area ?: Rect(0f, size.height * 0.14f, size.width, size.height * 0.70f)
        val box = figureBox(a)
        onBox(box)
        val path = cache.getOrPut(box) { cache.clear(); figurePath(box) }
        val (stage, inStage, _) = scanStage(elapsed, planned)
        val region = BODY_REGIONS[stage]
        val whole = region.id == WHOLE_BODY
        val t = elapsed
        figureBase(box, path, if (whole) 0.26f + 0.06f * sin(t * 0.5f) else 0.18f)

        clipPath(path) {
            if (whole) {
                // a slow wave of light rising through the whole body, again and again
                val k = (t * 0.07f) % 1.3f
                val y = box.bottom - box.height * k
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, LIGHT.copy(alpha = 0.45f), Color.Transparent), y - box.height * 0.18f, y + box.height * 0.18f),
                    Offset(box.left - 20, y - box.height * 0.18f), Size(box.width + 40, box.height * 0.36f))
            } else {
                // the light travels from the last part to this one over the first seconds
                val prev = BODY_REGIONS[(stage - 1).coerceAtLeast(0)]
                val move = (inStage / 3.5f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                val count = max(prev.centers.size, region.centers.size)
                val r = max(region.rx * box.width, region.ry * box.height) * 1.3f + 16 * density
                for (j in 0 until count) {
                    val from = prev.centers[j % prev.centers.size]
                    val to = region.centers[j % region.centers.size]
                    val c = Offset(box.left + (from.x + (to.x - from.x) * move) * box.width, box.top + (from.y + (to.y - from.y) * move) * box.height)
                    val pulse = 1f + 0.06f * sin(t * 0.9f + j)
                    drawCircle(Brush.radialGradient(listOf(LIGHT.copy(alpha = 0.95f), LIGHT.copy(alpha = 0.35f), Color.Transparent), c, r * pulse), r * pulse, c)
                }
            }
        }
        drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(1.4f * density))

        // the places touched: a warm glow, and a ring that opens once when the touch is new
        for ((m, born) in marks) {
            val c = Offset(box.left + m.x * box.width, box.top + m.y * box.height)
            val age = t - born
            drawCircle(Brush.radialGradient(listOf(TENSION.copy(alpha = 0.75f), TENSION.copy(alpha = 0f)), c, 20 * density), 20 * density, c)
            drawCircle(Color.White.copy(alpha = 0.9f), 2.6f * density, c)
            if (age in 0f..1.6f) {
                val k = age / 1.6f
                drawCircle(TENSION.copy(alpha = 0.8f * (1 - k)), (8 + 26 * k) * density, c, style = Stroke(1.6f * density))
            }
        }
    }
}

/**
 * Where tension is carried, over many scans and notes: the figure with a warm glow on each part,
 * brighter and wider the more often it was named.
 */
@Composable
fun BodyMap(counts: Map<String, Int>, modifier: Modifier = Modifier) {
    val cache = remember { HashMap<Rect, Path>() }
    Canvas(modifier) {
        val box = figureBox(Rect(0f, 0f, size.width, size.height))
        val path = cache.getOrPut(box) { cache.clear(); figurePath(box) }
        figureBase(box, path, 0.16f)
        val top = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        clipPath(path) {
            for ((id, n) in counts) {
                val reg = bodyRegion(id) ?: continue
                val k = n / top.toFloat()
                val r = (max(reg.rx * box.width, reg.ry * box.height) * 1.1f + 10 * density) * (0.7f + 0.5f * k)
                for (c0 in reg.centers) {
                    val c = Offset(box.left + c0.x * box.width, box.top + c0.y * box.height)
                    drawCircle(Brush.radialGradient(listOf(TENSION.copy(alpha = 0.35f + 0.6f * k), TENSION.copy(alpha = 0f)), c, r), r, c)
                }
            }
        }
        drawPath(path, Color.White.copy(alpha = 0.5f), style = Stroke(1.2f * density))
    }
}
