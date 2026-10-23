package com.grungert.nebo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.app
import com.grungert.nebo.data.Entry
import com.grungert.nebo.koan.FourPointsReader
import com.grungert.nebo.koan.Links
import com.grungert.nebo.koan.Thing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val POINT_ORDER = listOf(FourPointsReader.THOUGHTS, FourPointsReader.EMOTIONS, FourPointsReader.BODY, FourPointsReader.URGES)

/** A small label drawn on a canvas, centred on [x], with its top at [y]. */
private fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, y: Float, style: TextStyle, maxWidth: Float = 220f * density) {
    val l = tm.measure(text, style.copy(textAlign = TextAlign.Center), constraints = Constraints(maxWidth = maxWidth.toInt()), maxLines = 2)
    drawText(l, topLeft = Offset(x - l.size.width / 2f, y))
}

/** A value that runs from 0 to 1 once, when the drawing first appears: lines grow, dots open, numbers fill. */
@Composable
fun rememberReveal(key: Any? = Unit, millis: Int = 900): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(millis, easing = FastOutSlowInEasing)) }
    return a.value
}

/**
 * The four points as a small web: every point joined to every other, the line as strong as the share of notes that
 * hold both, each dot as big as its point's share. Weak or unseen links stay as faint dashes, since all four always touch.
 */
@Composable
fun PointsWeb(share: Map<String, Float>, pairs: Map<Pair<String, String>, Float>, modifier: Modifier = Modifier, words: Map<String, String> = emptyMap()) {
    val k = rememberReveal(share)
    val tm = rememberTextMeasurer()
    val ink = Ink.text
    val soft = Ink.soft
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val labelled = words.isNotEmpty()
        val pos = mapOf(
            FourPointsReader.THOUGHTS to Offset(w / 2, h * (if (labelled) 0.17f else 0.12f)), FourPointsReader.EMOTIONS to Offset(w * (if (labelled) 0.84f else 0.88f), h * 0.5f),
            FourPointsReader.BODY to Offset(w / 2, h * (if (labelled) 0.83f else 0.88f)), FourPointsReader.URGES to Offset(w * (if (labelled) 0.16f else 0.12f), h * 0.5f),
        )
        val unit = min(w, h) / 100f
        val r = POINT_ORDER.associateWith { (3.2f + 6.5f * (share[it] ?: 0f)) * unit }
        val mid = Offset(w / 2, h / 2)
        for (i in POINT_ORDER.indices) for (j in i + 1 until POINT_ORDER.size) {
            val a = POINT_ORDER[i]; val b = POINT_ORDER[j]
            val v = pairs[a to b] ?: 0f
            val pa = pos.getValue(a); val pb = pos.getValue(b)
            val d = hypot(pb.x - pa.x, pb.y - pa.y); val ux = (pb.x - pa.x) / d; val uy = (pb.y - pa.y) / d
            val s = Offset(pa.x + ux * (r.getValue(a) + 3 * unit), pa.y + uy * (r.getValue(a) + 3 * unit))
            val e0 = Offset(pb.x - ux * (r.getValue(b) + 3 * unit), pb.y - uy * (r.getValue(b) + 3 * unit))
            val e = Offset(s.x + (e0.x - s.x) * k, s.y + (e0.y - s.y) * k)
            // the sides bow gently towards the middle; the two diagonals run straight through it
            val diag = (a == FourPointsReader.THOUGHTS && b == FourPointsReader.BODY) || (a == FourPointsReader.EMOTIONS && b == FourPointsReader.URGES)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(s.x, s.y)
                val c = Offset((s.x + e.x) / 2, (s.y + e.y) / 2)
                if (diag) lineTo(e.x, e.y) else quadraticTo(c.x + (mid.x - c.x) * 0.25f, c.y + (mid.y - c.y) * 0.25f, e.x, e.y)
            }
            if (v >= 0.08f) drawPath(path, ink.copy(alpha = 0.14f + 0.55f * v), style = Stroke((0.8f + 4.5f * v) * unit, cap = StrokeCap.Round))
            else drawPath(path, ink.copy(alpha = 0.12f), style = Stroke(0.8f * unit, cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f * unit, 4f * unit))))
        }
        for (p in POINT_ORDER) {
            val c = POINT_COLORS.getValue(p); val at = pos.getValue(p); val v = share[p] ?: 0f
            if (v > 0f) {
                drawCircle(c.copy(alpha = 0.3f), (r.getValue(p) + 3 * unit) * k, at)
                drawCircle(c, r.getValue(p) * k, at)
            } else drawCircle(ink.copy(alpha = 0.3f), 3f * unit, at, style = Stroke(1.2f * unit))
            if (labelled) {
                val name = POINT_NAMES.getValue(p).uppercase()
                val word = words[p]
                val below = p != FourPointsReader.THOUGHTS
                val y = if (below) at.y + r.getValue(p) + 4 * unit else at.y - r.getValue(p) - 4 * unit - (if (word != null) 30.sp.toPx() else 14.sp.toPx())
                label(tm, name, at.x, y, TextStyle(fontFamily = UiFont, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = soft))
                if (word != null) label(tm, word, at.x, y + 13.sp.toPx(), TextStyle(fontFamily = UiFont, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = ink), 110.dp.toPx())
            }
        }
    }
}

/**
 * "Kad se javi…": the thing in the middle, and around it what comes with it. A ring around each fills to how often
 * (3 of 4), the line to it is as thick, and they float out from the middle when the screen opens. Touching one goes to it.
 */
@Composable
fun FocusOrbit(f: Links.Focus, onPick: (Thing) -> Unit, modifier: Modifier = Modifier) {
    val k = remember(f.thing) { Animatable(0f) }
    LaunchedEffect(f.thing) { k.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow)) }
    val tm = rememberTextMeasurer()
    val ink = Ink.text
    val sats = f.with.take(6)
    val cardBg = if (Ink.day) Color.White else Color(0xFF1A2850)
    Canvas(modifier.pointerInput(f.thing, sats) {
        detectTapGestures { pos ->
            val c = Offset(size.width / 2f, size.height / 2f)
            val hit = sats.withIndex().minByOrNull { (i, _) -> val p = satAt(i, sats.size, c, size.width.toFloat(), size.height.toFloat()); hypot(p.x - pos.x, p.y - pos.y) }
            if (hit != null) {
                val p = satAt(hit.index, sats.size, c, size.width.toFloat(), size.height.toFloat())
                if (hypot(p.x - pos.x, p.y - pos.y) < 40.dp.toPx()) onPick(hit.value.thing)
            }
        }
    }) {
        val c = Offset(size.width / 2, size.height / 2)
        val v = k.value
        val color = POINT_COLORS.getValue(f.thing.point)
        for ((i, s) in sats.withIndex()) {
            val p0 = satAt(i, sats.size, c, size.width, size.height)
            val p = Offset(c.x + (p0.x - c.x) * v, c.y + (p0.y - c.y) * v)
            val share = s.together.toFloat() / f.notes.coerceAtLeast(1)
            drawLine(POINT_COLORS.getValue(s.thing.point).copy(alpha = 0.85f), c, p, (1.5f + 7f * share).dp.toPx(), StrokeCap.Round)
        }
        val cr = 34.dp.toPx()
        drawCircle(color.copy(alpha = 0.25f), cr + 10.dp.toPx() * (0.6f + 0.4f * v), c)
        drawCircle(color, cr, c)
        label(tm, f.thing.label.trim('„', '“').let { if (f.thing.point == FourPointsReader.THOUGHTS) "„$it“" else it }, c.x, c.y - 15.sp.toPx(),
            TextStyle(fontFamily = UiFont, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Ink.ink), cr * 2.1f)
        label(tm, "${f.notes} ${plural(f.notes, "put", "puta", "puta")}", c.x, c.y + 3.sp.toPx(),
            TextStyle(fontFamily = UiFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ink.ink.copy(alpha = 0.7f)))
        for ((i, s) in sats.withIndex()) {
            val p0 = satAt(i, sats.size, c, size.width, size.height)
            val p = Offset(c.x + (p0.x - c.x) * v, c.y + (p0.y - c.y) * v)
            val pc = POINT_COLORS.getValue(s.thing.point)
            val share = s.together.toFloat() / f.notes.coerceAtLeast(1)
            val r = 22.dp.toPx()
            drawCircle(cardBg, r, p)
            drawCircle(pc.copy(alpha = 0.3f), r, p, style = Stroke(5.dp.toPx()))
            drawArc(pc, -90f, 360f * share * v, false, Offset(p.x - r, p.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            label(tm, "${s.together}/${f.notes}", p.x, p.y - 8.sp.toPx(), TextStyle(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (Ink.day) Ink.ink else Color.White))
            val above = p.y < c.y
            label(tm, s.thing.label, p.x, if (above) p.y - r - 34.sp.toPx() else p.y + r + 6.dp.toPx(),
                TextStyle(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink), 120.dp.toPx())
        }
    }
}

/** Where the i-th of n satellites sits: spread around the middle, the first top-left, on an ellipse that fills the canvas. */
private fun satAt(i: Int, n: Int, c: Offset, w: Float, h: Float): Offset {
    val start = if (n <= 2) -PI / 2 - PI / 4 else -PI / 2 - PI / 4
    val a = start + i * 2 * PI / max(n, 2)
    return Offset(c.x + (w * 0.33f) * cos(a).toFloat(), c.y + (h * 0.34f) * sin(a).toFloat())
}

/** A practice that meets a thing where it is: the body in a scan, a pull in a wave, thoughts as clouds, a heavy feeling in RAIN. */
internal fun practiceFor(t: Thing): Pair<String, String> = when (t.point) {
    FourPointsReader.BODY -> "bodyscan" to "Skeniranje tijela"
    FourPointsReader.URGES -> "urge" to "Talas"
    FourPointsReader.THOUGHTS -> "thoughts" to "Oblaci"
    else -> if (t.label in listOf("tužno", "ljuto", "zabrinuto")) "rain" to "RAIN" else "breath" to "Disanje"
}

/** One thing from the notes: what comes with it, when it comes, a practice for it, and the notes themselves. */
@Composable
fun FocusScreen(sc: Screen.Focus, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var thing by remember { mutableStateOf(Thing(sc.point, sc.label)) }
    var focus by remember { mutableStateOf<Links.Focus?>(null) }
    var notes by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var all by remember { mutableStateOf<List<Entry>?>(null) }
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { app.db.entries() } }
    LaunchedEffect(thing, all) {
        val a = all ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            focus = Links.focus(a, thing)
            notes = a.filter { thing in Links.things(it) }.sortedByDescending { it.createdAt }
        }
    }
    val scroll = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(scroll).screenPadding().padding(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader("Kad se javi…", { go(Screen.Journal) })
            val f = focus ?: return@Column
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(POINT_COLORS.getValue(thing.point)))
                    Text(POINT_NAMES.getValue(thing.point).uppercase(), style = KickerStyle)
                }
                Text(thing.label, style = HeadStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, shadow = null))
                if (f.with.isEmpty()) Text("Još ništa ne dolazi uz ovo dovoljno često. Veza se pokaže kad se ponovi bar ${Links.threshold(all?.size ?: 0)} puta.",
                    style = SmallStyle.copy(shadow = null))
                else FocusOrbit(f, { t -> thing = t; scope.launch { scroll.animateScrollTo(0) } }, Modifier.fillMaxWidth().height(300.dp))
                f.sentence()?.let { Text(it, style = ReadStyle) }
                f.with.firstOrNull { it.lift != null }?.let { w ->
                    Text("„${w.thing.label.trim('„', '“')}“ dolazi uz ovo %.1f puta češće nego inače.".format(w.lift).replace('.', ','),
                        style = SmallStyle.copy(shadow = null))
                }
                Text("Ide zajedno, u istim bilješkama; šta je bilo prvo, ne znamo.", style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
            }
            Panel {
                SectionLabel("Kad dolazi")
                DayParts(f.parts)
            }
            val (type, name) = practiceFor(thing)
            GlassButton("Probaj: $name", {
                val base = context.lastConfig()
                go(Screen.Session(base.copy(type = type, minutes = if (type == "bodyscan") 10 else if (type == "urge") 5 else base.minutes, checkInBefore = false, eyesClosed = false)))
            }, Modifier.fillMaxWidth(), strong = true, icon = SESSION_TYPES.firstOrNull { it.id == type }?.icon)
            if (notes.isNotEmpty()) {
                SectionLabel("Bilješke · ${notes.size}")
                for (e in notes.take(12)) NoteSnippet(e)
            }
        }
    }
}

/** A note in a few lines: its day and time, then its words in the reading face. */
@Composable
internal fun NoteSnippet(e: Entry) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().lift(shape, 6.dp).clip(shape).background(Ink.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(dayLabel(e.createdAt) + (if (e.moods.isNotEmpty()) " · " + e.moods.joinToString(", ") else ""), style = SmallStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
        if (e.words.isNotBlank()) Text(e.words, style = ReadStyle.copy(fontSize = 17.sp, lineHeight = 24.sp), maxLines = 4)
    }
}

/** Morning, day, evening, night: a sun on its arc for each, as big as the times the thing came then. */
@Composable
fun DayParts(parts: List<Int>, modifier: Modifier = Modifier) {
    val k = rememberReveal(parts)
    val tm = rememberTextMeasurer()
    val ink = Ink.text
    Canvas(modifier.fillMaxWidth().height(130.dp)) {
        val w = size.width
        val base = size.height - 34.dp.toPx()
        val arcH = base - 18.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(0f, base); quadraticTo(w / 2, base - arcH * 2f, w, base) }
        drawPath(path, ink.copy(alpha = 0.18f), style = Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 10f))))
        drawLine(ink.copy(alpha = 0.12f), Offset(0f, base), Offset(w, base), 1.dp.toPx())
        val top = parts.maxOrNull()?.coerceAtLeast(1) ?: 1
        for (i in 0..3) {
            val t = 0.12f + i * 0.25f
            // a point on the arc: the same quadratic curve as above
            val x = w * t
            val y = (1 - t) * (1 - t) * base + 2 * (1 - t) * t * (base - arcH * 2f) + t * t * base
            val n = parts.getOrElse(i) { 0 }
            val r = (5f + 9f * sqrt(n.toFloat() / top)) * density * k
            if (n > 0) {
                drawCircle(Ink.accent.copy(alpha = 0.3f), r + 7.dp.toPx() * k, Offset(x, y))
                drawCircle(Color(0xFFFFC872), r, Offset(x, y))
            } else drawCircle(ink.copy(alpha = 0.25f), 4.dp.toPx(), Offset(x, y), style = Stroke(1.2.dp.toPx()))
            label(tm, "${Links.PART_WHEN[i]} · $n", x, base + 8.dp.toPx(), TextStyle(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink))
        }
    }
}

/** A thing placed in the sky of things. */
class StarPlace(val thing: Thing, val at: Offset, val count: Int)

/**
 * Lays the things out like a small constellation map: linked things pull together, all things push apart a little, and
 * everything is kept off the edges. Deterministic, so the same notes give the same sky.
 */
fun layoutStars(web: Links.Web, w: Float, h: Float, max: Int = 10): List<StarPlace> {
    val things = web.stars(max)
    if (things.isEmpty()) return emptyList()
    val n = things.size
    val idx = things.withIndex().associate { it.value to it.index }
    val px = FloatArray(n) { i -> (0.5f + 0.32f * cos(2 * PI * i / n + 0.4).toFloat()) * w }
    val py = FloatArray(n) { i -> (0.5f + 0.32f * sin(2 * PI * i / n + 0.4).toFloat()) * h }
    val links = web.links.mapNotNull { l -> val a = idx[l.a]; val b = idx[l.b]; if (a != null && b != null) Triple(a, b, l.together) else null }
    val ideal = min(w, h) * 0.32f
    repeat(400) { step ->
        val fx = FloatArray(n); val fy = FloatArray(n)
        for (i in 0 until n) for (j in 0 until n) if (i != j) {
            val dx = px[i] - px[j]; val dy = py[i] - py[j]
            val d2 = (dx * dx + dy * dy).coerceAtLeast(1f)
            val f = ideal * ideal / d2
            fx[i] += dx * f * 0.6f; fy[i] += dy * f * 0.6f
        }
        for ((a, b, c) in links) {
            val dx = px[b] - px[a]; val dy = py[b] - py[a]
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val f = (d - ideal * 0.7f) / d * (0.08f + 0.03f * c)
            fx[a] += dx * f; fy[a] += dy * f; fx[b] -= dx * f; fy[b] -= dy * f
        }
        val cool = 1f - step / 400f
        for (i in 0 until n) {
            fx[i] += (w / 2 - px[i]) * 0.02f; fy[i] += (h / 2 - py[i]) * 0.02f
            px[i] += (fx[i] * 0.1f).coerceIn(-12f, 12f) * cool
            py[i] += (fy[i] * 0.1f).coerceIn(-12f, 12f) * cool
        }
    }
    // fit into the box, keeping room for the names under the stars
    val minX = px.min(); val maxX = px.max(); val minY = py.min(); val maxY = py.max()
    val padX = w * 0.16f; val padTop = h * 0.08f; val padBottom = h * 0.14f
    fun fx(x: Float) = if (maxX - minX < 1f) w / 2 else padX + (x - minX) / (maxX - minX) * (w - 2 * padX)
    fun fy(y: Float) = if (maxY - minY < 1f) h / 2 else padTop + (y - minY) / (maxY - minY) * (h - padTop - padBottom)
    return things.mapIndexed { i, t -> StarPlace(t, Offset(fx(px[i]), fy(py[i])), web.counts[t] ?: 0) }
}

/**
 * The sky of things: the words that come back in the notes as stars in their points' colours, as bright as they are
 * frequent, joined where they come together. The links draw in once the stars are out; stars twinkle; touching one opens it.
 */
@Composable
fun ThingsSky(web: Links.Web, onPick: (Thing) -> Unit, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) com.grungert.nebo.sky.nextFrame(last, 30).let { now -> t += ((now - last) / 1e9f).coerceIn(0f, 0.1f); last = now }
    }
    BoxWithConstraints(modifier) {
        val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
        val stars = remember(web, wPx, hPx) { layoutStars(web, wPx, hPx) }
        val at = stars.associateBy { it.thing }
        val top = stars.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        Canvas(Modifier.fillMaxSize().pointerInput(stars) {
            detectTapGestures { pos ->
                val near = stars.minByOrNull { hypot(it.at.x - pos.x, it.at.y - pos.y) } ?: return@detectTapGestures
                if (hypot(near.at.x - pos.x, near.at.y - pos.y) < 40.dp.toPx()) onPick(near.thing)
            }
        }) {
            val lines = ((t - 0.9f) / 1.2f).coerceIn(0f, 1f)
            val strongest = web.links.maxOfOrNull { it.together }?.coerceAtLeast(1) ?: 1
            for (l in web.links) {
                val a = at[l.a]?.at ?: continue; val b = at[l.b]?.at ?: continue
                val e = Offset(a.x + (b.x - a.x) * lines, a.y + (b.y - a.y) * lines)
                val s = l.together.toFloat() / strongest
                drawLine(Color.White.copy(alpha = 0.06f + 0.06f * s), a, e, (4f + 6f * s).dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.25f + 0.4f * s), a, e, (0.8f + 1.6f * s).dp.toPx(), StrokeCap.Round)
            }
            for ((i, s) in stars.withIndex()) {
                val shown = ((t - 0.1f - 0.08f * i) / 0.5f).coerceIn(0f, 1f)
                if (shown <= 0f) continue
                val c = POINT_COLORS.getValue(s.thing.point)
                val tw = 0.8f + 0.2f * sin(t * (0.9f + i * 0.17f) + i)
                val core = (3.5f + 6f * sqrt(s.count.toFloat() / top)).dp.toPx() * shown
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f * tw), c.copy(alpha = 0f)), s.at, core * 3.2f), core * 3.2f, s.at)
                drawCircle(c, core, s.at)
                drawCircle(Color.White.copy(alpha = 0.6f * tw), core * 0.35f, s.at)
                label(tm, s.thing.label, s.at.x, s.at.y + core + 8.dp.toPx(),
                    TextStyle(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.92f * shown)), 130.dp.toPx())
            }
        }
    }
}

/** The thread card's corner: the web of its own notes, without words. */
@Composable
fun ThreadWeb(entries: List<Entry>, modifier: Modifier = Modifier) {
    val web = remember(entries) { Links.web(entries) }
    PointsWeb(web.pointShare, web.pointPairs, modifier.aspectRatio(1.25f))
}
