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

/** The scan's path, from the feet up to the crown, then the whole body at once. Places are on a seated figure. */
val BODY_REGIONS = listOf(
    BodyRegion("stopala", "Stopala", "Tabani, prsti, pete. Gdje dodiruju jedno drugo i podlogu.",
        listOf(Offset(0.365f, 0.865f), Offset(0.635f, 0.84f)), 0.07f, 0.04f, "napetost u stopalima", "u stopalima"),
    BodyRegion("noge", "Noge", "Koljena, butine, listovi. Kolika je težina nogu?",
        listOf(Offset(0.22f, 0.75f), Offset(0.78f, 0.75f)), 0.13f, 0.06f, "napetost u nogama", "u nogama"),
    BodyRegion("karlica", "Karlica", "Kukovi i mjesto na kojem sjediš. Neka te jastuk nosi.",
        listOf(Offset(0.5f, 0.665f)), 0.18f, 0.035f, "napetost u karlici", "u karlici"),
    BodyRegion("stomak", "Stomak", "Kako se stomak diže i spušta s dahom.",
        listOf(Offset(0.5f, 0.555f)), 0.13f, 0.045f, "stisnut stomak", "u stomaku"),
    BodyRegion("leđa", "Leđa", "Od krsta do lopatica. Gdje su leđa meka, a gdje stegnuta?",
        listOf(Offset(0.5f, 0.45f)), 0.17f, 0.10f, "napetost u leđima", "u leđima"),
    BodyRegion("grudi", "Grudi", "Dah koji širi grudi. Srce ispod njih.",
        listOf(Offset(0.5f, 0.37f)), 0.16f, 0.045f, "stezanje u grudima", "u grudima"),
    BodyRegion("ruke", "Ruke", "Nadlaktice, laktovi, podlaktice, šake na koljenima. Šta osjećaju dlanovi?",
        listOf(Offset(0.235f, 0.54f), Offset(0.765f, 0.54f)), 0.06f, 0.13f, "napetost u rukama", "u rukama"),
    BodyRegion("ramena", "Ramena i vrat", "Ramena često nose ono što ne moraju. Mogu li da se spuste?",
        listOf(Offset(0.36f, 0.285f), Offset(0.64f, 0.285f)), 0.08f, 0.03f, "napeta ramena", "u ramenima"),
    BodyRegion("grlo", "Grlo", "Primijeti gutanje i dah koji prolazi kroz grlo.",
        listOf(Offset(0.5f, 0.25f)), 0.05f, 0.025f, "knedla u grlu", "u grlu"),
    BodyRegion("lice", "Lice", "Vilica, usne, obrazi, oči, čelo. Neka lice omekša.",
        listOf(Offset(0.5f, 0.17f)), 0.07f, 0.035f, "stegnuta vilica", "u vilici"),
    BodyRegion("glava", "Glava", "Tjeme, potiljak, prostor iza očiju.",
        listOf(Offset(0.5f, 0.095f)), 0.075f, 0.03f, "pritisak u glavi", "u glavi"),
    BodyRegion(WHOLE_BODY, "Cijelo tijelo", "Sve odjednom: jedno tijelo koje diše.",
        listOf(Offset(0.5f, 0.5f)), 0.5f, 0.5f, "", "u cijelom tijelu"),
)
const val WHOLE_BODY = "cijelo"

fun bodyRegion(id: String) = BODY_REGIONS.firstOrNull { it.id == id }

/** The body points of notes that name a place, mapped back to the region they sit in. */
private val LABEL_REGION = mapOf("lupanje srca" to "grudi") + BODY_REGIONS.filter { it.label.isNotEmpty() }.associate { it.label to it.id }

fun regionOfLabel(label: String): String? = LABEL_REGION[label]

/** The width of the seated figure, cushion included, for its height. */
const val FIGURE_ASPECT = 0.9f

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

/** A figure sitting cross-legged, hands resting on the knees, as one outline. */
fun figurePath(box: Rect): Path {
    val bw = box.width; val bh = box.height
    fun p(x: Float, y: Float) = Offset(box.left + x * bw, box.top + y * bh)
    fun oval(c: Offset, w: Float, h: Float) = Path().apply { addOval(Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)) }
    val parts = ArrayList<Path>()
    parts += oval(p(0.5f, 0.145f), 0.17f * bw, 0.165f * bh)                    // head
    parts += capsule(p(0.5f, 0.21f), p(0.5f, 0.275f), 0.064f * bw)             // neck
    parts += Path().apply {                                                     // torso, shoulders to hips
        val a = p(0.31f, 0.305f); moveTo(a.x, a.y)
        fun cubic(c1: Offset, c2: Offset, e: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
        // shoulders that slope down from the neck instead of standing square
        cubic(p(0.39f, 0.258f), p(0.61f, 0.258f), p(0.69f, 0.305f))
        cubic(p(0.725f, 0.37f), p(0.66f, 0.44f), p(0.64f, 0.52f))
        cubic(p(0.63f, 0.58f), p(0.70f, 0.62f), p(0.69f, 0.69f))
        lineTo(p(0.31f, 0.69f).x, p(0.31f, 0.69f).y)
        cubic(p(0.30f, 0.62f), p(0.37f, 0.58f), p(0.36f, 0.52f))
        cubic(p(0.34f, 0.44f), p(0.275f, 0.37f), a)
        close()
    }
    for (left in listOf(true, false)) {
        fun q(x: Float, y: Float) = p(if (left) x else 1f - x, y)
        parts += capsule(q(0.315f, 0.325f), q(0.245f, 0.47f), 0.044f * bw)    // upper arm
        parts += capsule(q(0.245f, 0.47f), q(0.215f, 0.64f), 0.038f * bw)     // forearm
        parts += oval(q(0.215f, 0.675f), 0.08f * bw, 0.05f * bh)              // hand on the knee
        parts += capsule(q(0.40f, 0.69f), q(0.16f, 0.76f), 0.078f * bw)       // thigh out to the knee
    }
    // the shins cross in front, each ending in a foot
    parts += capsule(p(0.17f, 0.79f), p(0.60f, 0.845f), 0.06f * bw)
    parts += capsule(p(0.83f, 0.79f), p(0.40f, 0.865f), 0.06f * bw)
    parts += oval(p(0.5f, 0.76f), 0.42f * bw, 0.17f * bh)                      // the lap, where the shins cross
    parts += oval(p(0.635f, 0.84f), 0.09f * bw, 0.045f * bh)
    parts += oval(p(0.365f, 0.865f), 0.09f * bw, 0.045f * bh)
    var body = parts[0]
    for (part in parts.drop(1)) body = Path().also { it.op(body, part, PathOperation.Union) }
    return body
}

private val INK = Color(0xFF14223F)
private val LIGHT = Color(0xFFFFF1D6)
/** Tension marks: a warm coral, the colour of heat held in the body. */
val TENSION = Color(0xFFF2A88E)

private val CUSHION = Color(0xFF3B2E52)

/**
 * The figure of light on its cushion: a soft shade behind, the round cushion and its shadow, a pale glow around the
 * body, a fill that is brightest at the heart, and a thin bright outline drawn later.
 */
private fun DrawScope.figureBase(box: Rect, path: Path, fill: Float, breath: Float = 0.5f) {
    val c = Offset(box.center.x, box.top + box.height * 0.5f)
    scale(1f, 1.05f, c) {
        drawCircle(Brush.radialGradient(listOf(INK.copy(alpha = 0.55f), INK.copy(alpha = 0.24f), Color.Transparent), c, box.width * 0.72f), box.width * 0.72f, c)
    }
    // the cushion: a low round zafu, its top catching a little light
    val cw = box.width * 0.66f; val ch = box.height * 0.15f
    val cc = Offset(box.center.x, box.top + box.height * 0.87f)
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(box.center.x - box.width * 0.48f, box.top + box.height * 0.89f), Size(box.width * 0.96f, box.height * 0.08f))
    // the cushion is behind the crossed legs: drawn only where the body is not
    clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) {
    drawOval(Brush.verticalGradient(listOf(CUSHION.copy(alpha = 0.95f), Color(0xFF241B36).copy(alpha = 0.95f)), cc.y - ch / 2, cc.y + ch / 2),
        Offset(cc.x - cw / 2, cc.y - ch / 2), Size(cw, ch))
    drawOval(Color.White.copy(alpha = 0.12f), Offset(cc.x - cw * 0.36f, cc.y - ch * 0.44f), Size(cw * 0.72f, ch * 0.32f))
    // a seam around its middle, like a stitched zafu
    drawOval(Color.White.copy(alpha = 0.07f), Offset(cc.x - cw * 0.5f, cc.y - ch * 0.08f), Size(cw, ch * 0.22f), style = Stroke(1.2f * density))
    }
    // an aura: the outline drawn wide and faint, several times
    for ((w, a) in listOf(30f to 0.035f, 18f to 0.05f, 9f to 0.08f)) drawPath(path, Color(0xFFDCE6FF).copy(alpha = a * (0.8f + 0.4f * breath)), style = Stroke(w * density))
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFFD6E2FF).copy(alpha = fill * 1.7f), Color(0xFF8FA6E8).copy(alpha = fill * 1.5f)), box.top, box.bottom))
    val heart = Offset(box.center.x, box.top + box.height * 0.40f)
    clipPath(path) {
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f + 0.08f * breath), Color.Transparent), heart, box.width * 0.30f), box.width * 0.30f, heart)
    }
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
        val breath = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 8f)   // a slow eight-second swell, not a pace to follow
        figureBase(box, path, if (whole) 0.26f + 0.06f * sin(t * 0.5f) else 0.17f, breath)

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
                // a soft band of light across the body at this height, and a brighter light on the part itself
                val fy = prev.centers.map { it.y }.average().toFloat(); val ty = region.centers.map { it.y }.average().toFloat()
                val by = box.top + (fy + (ty - fy) * move) * box.height
                val bh2 = max(region.ry * box.height * 2.2f, 26 * density)
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, LIGHT.copy(alpha = 0.32f), Color.Transparent), by - bh2, by + bh2),
                    Offset(box.left - 10, by - bh2), Size(box.width + 20, bh2 * 2))
                for (j in 0 until count) {
                    val from = prev.centers[j % prev.centers.size]
                    val to = region.centers[j % region.centers.size]
                    val c = Offset(box.left + (from.x + (to.x - from.x) * move) * box.width, box.top + (from.y + (to.y - from.y) * move) * box.height)
                    val pulse = 1f + 0.06f * sin(t * 0.9f + j)
                    drawCircle(Brush.radialGradient(listOf(LIGHT.copy(alpha = 0.95f), LIGHT.copy(alpha = 0.35f), Color.Transparent), c, r * pulse), r * pulse, c)
                }
            }
        }
        drawPath(path, INK.copy(alpha = 0.16f), style = Stroke(6f * density))
        drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke(1.4f * density))

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
        figureBase(box, path, 0.15f)
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
        drawPath(path, INK.copy(alpha = 0.16f), style = Stroke(5f * density))
        drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(1.2f * density))
    }
}
