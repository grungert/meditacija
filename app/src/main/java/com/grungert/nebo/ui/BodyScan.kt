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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import com.grungert.nebo.data.BodyMark
import kotlin.math.PI
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

/** The scan's path, from the feet up to the crown, then the whole body at once. Places are on a standing figure. */
val BODY_REGIONS = listOf(
    BodyRegion("stopala", "Stopala", "Tabani, prsti, pete. Kako stopala dodiruju pod?",
        listOf(Offset(0.36f, 0.975f), Offset(0.64f, 0.975f)), 0.10f, 0.02f, "napetost u stopalima", "u stopalima"),
    BodyRegion("noge", "Noge", "Butine, koljena, listovi. Kolika je težina nogu?",
        listOf(Offset(0.37f, 0.76f), Offset(0.63f, 0.76f)), 0.09f, 0.15f, "napetost u nogama", "u nogama"),
    BodyRegion("karlica", "Karlica", "Kukovi i karlica. Neka se smjeste, ništa ne moraju da drže.",
        listOf(Offset(0.5f, 0.52f)), 0.19f, 0.03f, "napetost u karlici", "u karlici"),
    BodyRegion("stomak", "Stomak", "Kako se stomak diže i spušta s dahom.",
        listOf(Offset(0.5f, 0.44f)), 0.15f, 0.035f, "stisnut stomak", "u stomaku"),
    BodyRegion("leđa", "Leđa", "Od krsta do lopatica. Gdje su leđa meka, a gdje stegnuta?",
        listOf(Offset(0.5f, 0.36f)), 0.16f, 0.05f, "napetost u leđima", "u leđima"),
    BodyRegion("grudi", "Grudi", "Dah koji širi grudi. Srce ispod njih.",
        listOf(Offset(0.5f, 0.29f)), 0.16f, 0.035f, "stezanje u grudima", "u grudima"),
    BodyRegion("ruke", "Ruke", "Nadlaktice, laktovi, podlaktice, šake. Šta osjećaju dlanovi?",
        listOf(Offset(0.18f, 0.44f), Offset(0.82f, 0.44f)), 0.06f, 0.15f, "napetost u rukama", "u rukama"),
    BodyRegion("ramena", "Ramena i vrat", "Ramena često nose ono što ne moraju. Mogu li da se spuste?",
        listOf(Offset(0.33f, 0.225f), Offset(0.67f, 0.225f)), 0.10f, 0.02f, "napeta ramena", "u ramenima"),
    BodyRegion("grlo", "Grlo", "Primijeti gutanje i dah koji prolazi kroz grlo.",
        listOf(Offset(0.5f, 0.18f)), 0.05f, 0.015f, "knedla u grlu", "u grlu"),
    BodyRegion("lice", "Lice", "Vilica, usne, obrazi, oči, čelo. Neka lice omekša.",
        listOf(Offset(0.5f, 0.10f)), 0.09f, 0.03f, "stegnuta vilica", "u vilici"),
    BodyRegion("glava", "Glava", "Tjeme, potiljak, prostor iza očiju.",
        listOf(Offset(0.5f, 0.045f)), 0.10f, 0.02f, "pritisak u glavi", "u glavi"),
    BodyRegion(WHOLE_BODY, "Cijelo tijelo", "Sve odjednom: jedno tijelo koje diše.",
        listOf(Offset(0.5f, 0.5f)), 0.5f, 0.5f, "", "u cijelom tijelu"),
)
const val WHOLE_BODY = "cijelo"

fun bodyRegion(id: String) = BODY_REGIONS.firstOrNull { it.id == id }

/** The body points of notes that name a place, mapped back to the region they sit in. */
private val LABEL_REGION = mapOf("lupanje srca" to "grudi") + BODY_REGIONS.filter { it.label.isNotEmpty() }.associate { it.label to it.id }

fun regionOfLabel(label: String): String? = LABEL_REGION[label]

/**
 * The width of the standing figure for its height. Standing, the legs and feet are large and apart, and the arms hang
 * clear of the body, so every part is easy to touch.
 */
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
fun regionAt(pos: Offset, box: Rect, current: String?, lenient: Boolean = false): String? {
    val nx = (pos.x - box.left) / box.width
    val ny = (pos.y - box.top) / box.height
    fun d(r: BodyRegion) = r.centers.minOf { c -> ((nx - c.x) / r.rx).let { it * it } + ((ny - c.y) / r.ry).let { it * it } }
    BODY_REGIONS.firstOrNull { it.id == current && it.id != WHOLE_BODY }?.let { if (d(it) <= 1.8f) return it.id }
    val best = BODY_REGIONS.filter { it.id != WHOLE_BODY && (it.id != "leđa" || current == "leđa") }.minByOrNull { d(it) } ?: return null
    // without a scan to follow (RAIN), a touch anywhere on the figure belongs to the nearest part
    return best.id.takeIf { d(best) <= if (lenient) 9f else 2.4f }
}

/**
 * The right half of the figure's outline, from below the head down the neck, shoulder, the arm hanging clear of the body
 * and back up to the armpit, the side, the hip and the outer leg, the foot, and up the inner leg to the centre: cubic
 * curves (control, control, end) in figure units. The left half is the same, mirrored.
 */
private val HALF_START = Offset(0.5f, 0.12f)   // inside the head, where it is wide, so the neck joins it without a waist
private val HALF = listOf(
    floatArrayOf(0.552f, 0.12f, 0.557f, 0.15f, 0.558f, 0.175f),
    floatArrayOf(0.562f, 0.2f, 0.64f, 0.205f, 0.71f, 0.218f),
    floatArrayOf(0.79f, 0.232f, 0.815f, 0.27f, 0.82f, 0.315f),
    floatArrayOf(0.83f, 0.37f, 0.845f, 0.42f, 0.85f, 0.46f),
    floatArrayOf(0.86f, 0.5f, 0.872f, 0.54f, 0.878f, 0.57f),
    floatArrayOf(0.89f, 0.6f, 0.88f, 0.635f, 0.845f, 0.637f),
    floatArrayOf(0.812f, 0.637f, 0.8f, 0.605f, 0.8f, 0.575f),
    floatArrayOf(0.79f, 0.5f, 0.765f, 0.4f, 0.74f, 0.31f),
    floatArrayOf(0.735f, 0.35f, 0.705f, 0.4f, 0.7f, 0.44f),
    floatArrayOf(0.7f, 0.48f, 0.735f, 0.51f, 0.735f, 0.555f),
    floatArrayOf(0.735f, 0.63f, 0.7f, 0.7f, 0.69f, 0.765f),
    floatArrayOf(0.685f, 0.83f, 0.69f, 0.885f, 0.675f, 0.935f),
    floatArrayOf(0.68f, 0.96f, 0.745f, 0.975f, 0.735f, 0.992f),
    floatArrayOf(0.7f, 1.0f, 0.6f, 0.997f, 0.565f, 0.99f),
    floatArrayOf(0.555f, 0.965f, 0.565f, 0.94f, 0.565f, 0.915f),
    floatArrayOf(0.555f, 0.86f, 0.56f, 0.8f, 0.56f, 0.76f),
    floatArrayOf(0.555f, 0.68f, 0.545f, 0.6f, 0.522f, 0.565f),
    floatArrayOf(0.515f, 0.555f, 0.505f, 0.553f, 0.5f, 0.553f)
)
private val HEAD = Rect(0.5f - 0.12f, 0.082f - 0.068f, 0.5f + 0.12f, 0.082f + 0.068f)

/** A figure standing at ease, arms hanging a little away from the body, feet apart, as one outline. */
fun figurePath(box: Rect): Path {
    val bw = box.width; val bh = box.height
    fun x(u: Float) = box.left + u * bw
    fun y(v: Float) = box.top + v * bh
    val body = Path().apply {
        moveTo(x(HALF_START.x), y(HALF_START.y))
        for (s in HALF) cubicTo(x(s[0]), y(s[1]), x(s[2]), y(s[3]), x(s[4]), y(s[5]))
        // back up the left side: the same curves, mirrored and in reverse
        for (i in HALF.indices.reversed()) {
            val s = HALF[i]
            val (px, py) = if (i == 0) HALF_START.x to HALF_START.y else HALF[i - 1][4] to HALF[i - 1][5]
            cubicTo(x(1f - s[2]), y(s[3]), x(1f - s[0]), y(s[1]), x(1f - px), y(py))
        }
        close()
    }
    val head = Path().apply { addOval(Rect(x(HEAD.left), y(HEAD.top), x(HEAD.right), y(HEAD.bottom))) }
    return Path().also { it.op(body, head, PathOperation.Union) }
}

private val INK = Color(0xFF14223F)
private val LIGHT = Color(0xFFFFF1D6)
/** Tension marks: a warm coral, the colour of heat held in the body. */
val TENSION = Color(0xFFF2A88E)


/**
 * The figure of light on its cushion: a soft shade behind, the round cushion and its shadow, a pale glow around the
 * body, a fill that is brightest at the heart, and a thin bright outline drawn later. With [t] running, a few soft
 * lights drift slowly inside the body and the light along the spine breathes.
 */
private fun DrawScope.figureBase(box: Rect, path: Path, fill: Float, breath: Float = 0.5f, t: Float = 0f) {
    // a soft shade behind, as tall as the figure
    val c = box.center
    scale(1f, box.height / box.width * 0.62f, c) {
        drawCircle(Brush.radialGradient(listOf(INK.copy(alpha = 0.55f), INK.copy(alpha = 0.24f), Color.Transparent), c, box.width * 0.85f), box.width * 0.85f, c)
    }
    // the ground: a shadow under the feet and a little light where they stand
    val g = Offset(box.center.x, box.top + box.height * 0.995f)
    scale(1f, 0.16f, g) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), g, box.width * 0.55f), box.width * 0.55f, g)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFDCE6FF).copy(alpha = 0.10f + 0.05f * breath), Color.Transparent), g, box.width * 0.38f), box.width * 0.38f, g)
    }
    // an aura: the outline drawn wide and faint, several times
    for ((w, a) in listOf(30f to 0.035f, 18f to 0.05f, 9f to 0.08f)) drawPath(path, Color(0xFFDCE6FF).copy(alpha = a * (0.8f + 0.4f * breath)), style = Stroke(w * density))
    // a body of night blue under the light, so clouds behind it do not show through and seem to stand in front
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFF243466).copy(alpha = 0.96f), Color(0xFF1A2550).copy(alpha = 0.97f)), box.top, box.bottom))
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFFD6E2FF).copy(alpha = fill * 1.7f), Color(0xFF8FA6E8).copy(alpha = fill * 1.5f)), box.top, box.bottom))
    val heart = Offset(box.center.x, box.top + box.height * 0.29f)
    clipPath(path) {
        // volume: light falls from above and a little to the left, the lap and legs sit in a soft shade
        val key = Offset(box.left + box.width * 0.42f, box.top + box.height * 0.16f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), key, box.width * 0.75f), box.width * 0.75f, key)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, INK.copy(alpha = 0.18f)), box.top + box.height * 0.58f, box.bottom),
            Offset(box.left, box.top + box.height * 0.58f), Size(box.width, box.height * 0.42f))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f + 0.08f * breath), Color.Transparent), heart, box.width * 0.30f), box.width * 0.30f, heart)
        // a few soft lights drifting inside, slower than the breath
        for (i in 0 until 4) {
            val a = t * (0.05f + 0.013f * i) + i * 1.9f
            val c = Offset(box.left + box.width * (0.5f + 0.12f * sin(a) * (if (i % 2 == 0) 1f else -1f)),
                box.top + box.height * (0.26f + 0.10f * i + 0.04f * cos(a * 1.3f)))
            val r = box.width * (0.16f + 0.04f * sin(a * 0.7f + i))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFE8EEFF).copy(alpha = 0.10f), Color.Transparent), c, r), r, c)
        }
        // the spine: a faint line of light from the seat to the crown
        val sx = box.center.x
        val top = box.top + box.height * 0.04f; val seat = box.top + box.height * 0.55f
        // a wide faint glow with a fine bright thread inside it, so it reads as light and not as a seam
        for ((w, a) in listOf(9f to 0.05f, 1.2f to 0.16f)) drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = a + 0.05f * breath),
            Color.White.copy(alpha = a * 1.4f + 0.05f * breath), Color.Transparent), top, seat), Offset(sx, top), Offset(sx, seat), w * density, StrokeCap.Round)
    }
}

/** The outline: a soft dark edge outside, and a bright rim, brightest where the light falls (head and shoulders). */
private fun DrawScope.figureRim(box: Rect, path: Path) {
    drawPath(path, INK.copy(alpha = 0.16f), style = Stroke(6f * density))
    drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.92f), Color(0xFFCFDBFF).copy(alpha = 0.62f), Color(0xFFAFC0F0).copy(alpha = 0.42f)),
        box.top, box.bottom), style = Stroke(1.5f * density))
}

/** A touch of tension: a warm glow that breathes, a bright core, and a ring that opens once when the touch is new. */
private fun DrawScope.tensionMark(c: Offset, age: Float, breath: Float) {
    val r = (24f + 5f * breath) * density
    drawCircle(Brush.radialGradient(listOf(TENSION.copy(alpha = 0.85f), TENSION.copy(alpha = 0.40f + 0.15f * breath), TENSION.copy(alpha = 0f)), c, r), r, c)
    drawCircle(Brush.radialGradient(listOf(Color.White, TENSION.copy(alpha = 0.9f), TENSION.copy(alpha = 0f)), c, 7f * density), 7f * density, c)
    if (age in 0f..1.6f) {
        val k = age / 1.6f
        drawCircle(TENSION.copy(alpha = 0.8f * (1 - k)), (8 + 26 * k) * density, c, style = Stroke(1.6f * density))
    }
}

/** The figure rises and falls a little with a slow breath, from its seat. */
private inline fun DrawScope.breathing(box: Rect, breath: Float, block: DrawScope.() -> Unit) =
    scale(1f + 0.008f * breath, 1f + 0.006f * breath, Offset(box.center.x, box.top + box.height * 0.99f), block)

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
        breathing(box, breath) {
        figureBase(box, path, if (whole) 0.26f + 0.06f * sin(t * 0.5f) else 0.17f, breath, t)

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
                // a soft band of light across the body at this height (an oval, wider than tall, with no hard edges)
                val fy = prev.centers.map { it.y }.average().toFloat(); val ty = region.centers.map { it.y }.average().toFloat()
                val by = box.top + (fy + (ty - fy) * move) * box.height
                val bh2 = max(region.ry * box.height * 2.2f, 26 * density)
                val bandC = Offset(box.center.x, by)
                scale(box.width * 0.62f / bh2, 1f, bandC) {
                    drawCircle(Brush.radialGradient(listOf(LIGHT.copy(alpha = 0.30f), LIGHT.copy(alpha = 0.10f), Color.Transparent), bandC, bh2), bh2, bandC)
                }
                // on the spine, a bright point at the same height
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), LIGHT.copy(alpha = 0.3f), Color.Transparent), bandC, 9 * density), 9 * density, bandC)
                for (j in 0 until count) {
                    val from = prev.centers[j % prev.centers.size]
                    val to = region.centers[j % region.centers.size]
                    val c = Offset(box.left + (from.x + (to.x - from.x) * move) * box.width, box.top + (from.y + (to.y - from.y) * move) * box.height)
                    val pulse = 1f + 0.05f * sin(t * 0.9f + j) + 0.05f * breath
                    // the light on the part: a wide warm haze, then a brighter heart
                    drawCircle(Brush.radialGradient(listOf(LIGHT.copy(alpha = 0.45f), LIGHT.copy(alpha = 0.12f), Color.Transparent), c, r * 1.5f * pulse), r * 1.5f * pulse, c)
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), LIGHT.copy(alpha = 0.55f), Color.Transparent), c, r * 0.7f * pulse), r * 0.7f * pulse, c)
                    // motes of light rising slowly from it
                    for (k in 0 until 6) {
                        val ph = (t * 0.18f + k / 6f + j * 0.37f) % 1f
                        val px = c.x + r * 0.55f * sin(k * 2.1f + t * 0.3f) * (1f - ph * 0.5f)
                        val py = c.y + r * 0.4f - ph * r * 1.6f
                        drawCircle(Color.White.copy(alpha = 0.55f * sin(PI.toFloat() * ph)), (1.3f + 0.45f * (k % 3)) * density, Offset(px, py))
                    }
                    // as the light arrives on a new part, a ring opens from it once
                    if (inStage in 0.6f..2.6f) {
                        val k = (inStage - 0.6f) / 2f
                        drawCircle(LIGHT.copy(alpha = 0.55f * (1 - k)), r * (0.4f + 1.1f * k), c, style = Stroke(1.4f * density))
                    }
                }
            }
        }
        figureRim(box, path)

        // the places touched: a warm glow that breathes, and a ring that opens once when the touch is new
        for ((m, born) in marks) tensionMark(Offset(box.left + m.x * box.width, box.top + m.y * box.height), t - born, breath)
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
        figureRim(box, path)
    }
}

/**
 * The figure at rest, for marking where a feeling sits (RAIN's "istraži"): no travelling light, only a slow breath
 * of glow, and the places touched. [onBox] reports where the figure stands, as in [BodyScanView].
 */
@Composable
fun BodyTouchView(elapsed: Float, area: Rect, marks: List<Pair<BodyMark, Float>>, modifier: Modifier = Modifier, onBox: (Rect) -> Unit) {
    val cache = remember { HashMap<Rect, Path>() }
    Canvas(modifier.fillMaxSize()) {
        val box = figureBox(area)
        onBox(box)
        val path = cache.getOrPut(box) { cache.clear(); figurePath(box) }
        val breath = 0.5f + 0.5f * sin(elapsed * 2f * PI.toFloat() / 8f)
        breathing(box, breath) {
            figureBase(box, path, 0.2f, breath, elapsed)
            figureRim(box, path)
            for ((m, born) in marks) tensionMark(Offset(box.left + m.x * box.width, box.top + m.y * box.height), elapsed - born, breath)
        }
    }
}
