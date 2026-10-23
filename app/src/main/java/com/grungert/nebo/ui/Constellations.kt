package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.SkyHost
import com.grungert.nebo.app
import com.grungert.nebo.data.Entry
import com.grungert.nebo.koan.FourPointsReader
import com.grungert.nebo.koan.InsightThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Where the constellations sit in the sky, as parts of its width and height: up to six threads, the most recent first. */
private val SLOTS = listOf(
    Offset(0.28f, 0.15f), Offset(0.72f, 0.27f), Offset(0.32f, 0.46f), Offset(0.74f, 0.60f), Offset(0.27f, 0.77f), Offset(0.68f, 0.90f),
)
private const val SLOT_RADIUS = 0.16f   // of the sky's width
private val PALE = Color(0xFFDDE6FF)

/** A note as a star: where it is, its colour, and the thread it belongs to (or -1). */
private class Star(val entry: Entry, val at: Offset, val color: Color, val thread: Int, val order: Float)

/** The colour of a note's point: a pull to act, then the body, then thoughts, then emotions; pale when it holds none. */
private fun starColor(e: Entry): Color {
    val read = if (e.points.isEmpty && e.moods.isEmpty()) FourPointsReader.read(e.words) else null
    return when {
        e.points.urges.isNotEmpty() || read?.urges?.isNotEmpty() == true -> POINT_COLORS.getValue(FourPointsReader.URGES)
        e.points.body.isNotEmpty() || read?.body?.isNotEmpty() == true -> POINT_COLORS.getValue(FourPointsReader.BODY)
        e.points.thoughts.isNotBlank() || read?.thoughts?.isNotBlank() == true -> POINT_COLORS.getValue(FourPointsReader.THOUGHTS)
        e.moods.isNotEmpty() || read?.moods?.isNotEmpty() == true -> POINT_COLORS.getValue(FourPointsReader.EMOTIONS)
        else -> PALE
    }
}

/** A thread's stars, oldest first, in a small wandering figure around its place in the sky. */
private fun figureOf(notes: List<Entry>, center: Offset, radius: Float, aspect: Float): List<Offset> {
    val rnd = Random(notes.first().id * 31 + notes.size)
    val pts = ArrayList<Offset>()
    var p = center
    var ang = rnd.nextFloat() * 2 * PI.toFloat()
    for (i in notes.indices) {
        if (i > 0) {
            ang += (rnd.nextFloat() - 0.5f) * 2.4f
            val step = radius * (0.42f + 0.25f * rnd.nextFloat())
            p = Offset(p.x + cos(ang) * step, p.y + sin(ang) * step * aspect)
        }
        val dx = p.x - center.x; val dy = (p.y - center.y) / aspect
        val d = hypot(dx, dy)
        if (d > radius) { p = Offset(center.x + dx / d * radius, center.y + dy / d * radius * aspect); ang += PI.toFloat() * 0.75f }
        pts += p
    }
    // centred on its place, so the figure does not drift into a neighbour's
    val mx = pts.map { it.x }.average().toFloat(); val my = pts.map { it.y }.average().toFloat()
    return pts.map { Offset(it.x - mx + center.x, it.y - my + center.y) }
}

/** Which sky opens: the threads as constellations (0), or the things that come together (1). */
var constellationsMode = 0

/**
 * The journal as a night sky: every note a star in the colour of its point, every thread a constellation joining its
 * notes from the first to the last. Touching a constellation opens its thread; touching a lone star shows the note.
 * The sky above turns to night while this is open.
 */
@Composable
fun ConstellationsScreen(host: SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var entries by remember { mutableStateOf<List<Entry>?>(null) }
    var threads by remember { mutableStateOf<List<InsightThread>?>(null) }
    var peek by remember { mutableStateOf<Entry?>(null) }
    var t by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableIntStateOf(constellationsMode) }
    LaunchedEffect(mode) { constellationsMode = mode }
    var web by remember { mutableStateOf<com.grungert.nebo.koan.Links.Web?>(null) }

    DisposableEffect(Unit) {
        host.day.mode = "23.5"; host.lake = false
        onDispose { host.day.mode = app.settings.timeMode }
    }
    LaunchedEffect(Unit) {
        val all = withContext(Dispatchers.IO) { app.db.entries() }.filter { it.words.isNotBlank() }
        entries = all
        web = withContext(Dispatchers.Default) { com.grungert.nebo.koan.Links.web(all) }
        threads = app.recommender.threads(all).take(SLOTS.size)
    }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        // stars twinkle slowly: the sky's pace (see skyFps)
        while (true) com.grungert.nebo.sky.nextFrame(last, 30).let { now -> t += ((now - last) / 1e9f).coerceIn(0f, 0.1f); last = now }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66040A1C), Color(0x33040A1C), Color(0xCC040A1C)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.screenPadding().padding(top = 8.dp)) {
                ScreenHeader("Sazviježđa", { go(Screen.Journal) })
                PillTabs(listOf("Niti", "Veze"), mode, { mode = it }, Modifier.padding(bottom = 8.dp))
                Text(if (mode == 0) "Svaki zapis je zvijezda u boji svoje tačke. Niti su sazviježđa: dodirni jedno da ga otvoriš."
                    else "Svaka zvijezda je nešto što se vraća u bilješkama. Linija spaja ono što dolazi zajedno. Dodirni zvijezdu.",
                    style = SmallStyle, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (mode == 1) Box(Modifier.weight(1f).fillMaxWidth()) {
                val w = web
                when {
                    w == null -> Text("Tražim veze…", style = SmallStyle, modifier = Modifier.align(Alignment.TopCenter))
                    w.links.isEmpty() -> Text("Veze se pokažu kad se nešto u bilješkama vrati bar ${w.threshold} puta zajedno. Zapisuj, i nebo će se popuniti.",
                        style = BodyStyle, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).screenPadding())
                    else -> ThingsSky(w, { th -> go(Screen.Focus(th.point, th.label)) }, Modifier.fillMaxSize())
                }
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val wPx = with(density) { maxWidth.toPx() }
                val hPx = with(density) { maxHeight.toPx() }
                val all = entries
                val th = threads ?: emptyList()
                // placing and colouring every note reads each one's words: done off the main thread
                val stars by produceState(emptyList<Star>(), all, th, wPx, hPx) {
                    value = if (all == null || wPx <= 0f) emptyList() else withContext(Dispatchers.Default) { layoutSky(all, th, wPx, hPx) }
                }
                val total = stars.size.coerceAtLeast(1)
                Canvas(Modifier.fillMaxSize().pointerInput(stars) {
                    detectTapGestures { pos ->
                        val near = stars.minByOrNull { hypot(it.at.x - pos.x, it.at.y - pos.y) }
                        if (near == null || hypot(near.at.x - pos.x, near.at.y - pos.y) > 34.dp.toPx()) { peek = null; return@detectTapGestures }
                        if (near.thread >= 0) th.getOrNull(near.thread)?.let { go(Screen.Thread(it.entries.map { e -> e.id }, it.title)) }
                        else peek = near.entry
                    }
                }) {
                    // the threads: lines from each note to the next, drawn once their stars are out
                    val lines = ((t - 1.6f) / 1.4f).coerceIn(0f, 1f)
                    for ((i, _) in th.withIndex()) {
                        val own = stars.filter { it.thread == i }
                        for (k in 0 until own.size - 1) {
                            val a = own[k].at; val b = own[k + 1].at
                            val reach = (lines * own.size - k).coerceIn(0f, 1f)
                            if (reach <= 0f) continue
                            val e = Offset(a.x + (b.x - a.x) * reach, a.y + (b.y - a.y) * reach)
                            drawLine(Color.White.copy(alpha = 0.07f), a, e, 5.dp.toPx(), StrokeCap.Round)
                            drawLine(Color.White.copy(alpha = 0.42f), a, e, 1.dp.toPx(), StrokeCap.Round)
                        }
                    }
                    for (s in stars) {
                        val shown = ((t - 0.2f - 1.4f * s.order / total) / 0.6f).coerceIn(0f, 1f)
                        if (shown <= 0f) continue
                        val tw = 0.75f + 0.25f * sin(t * (0.8f + (s.entry.id % 7) * 0.13f) + s.entry.id)
                        val big = s.thread >= 0
                        val core = (if (s.entry.starred) 3.2f else if (big) 2.6f else 1.7f).dp.toPx()
                        val glow = core * (if (big) 4.2f else 3.4f)
                        drawCircle(Brush.radialGradient(listOf(s.color.copy(alpha = 0.8f * shown * tw), s.color.copy(alpha = 0f)), s.at, glow), glow, s.at)
                        drawCircle(androidx.compose.ui.graphics.lerp(Color.White, s.color, 0.45f).copy(alpha = shown * (0.7f + 0.3f * tw)), core, s.at)
                        if (s.entry.starred) {
                            // an insight shines with four small rays
                            val ray = core * 3.2f
                            for (ang in listOf(0f, PI.toFloat() / 2)) {
                                val d = Offset(cos(ang) * ray, sin(ang) * ray)
                                drawLine(Color.White.copy(alpha = 0.5f * shown * tw), s.at - d, s.at + d, 1.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    }
                }
                // each constellation's name, under its lowest star
                for ((i, thread) in th.withIndex()) {
                    val own = stars.filter { it.thread == i }
                    if (own.isEmpty()) continue
                    val x = own.map { it.at.x }.average().toFloat()
                    val y = own.maxOf { it.at.y }
                    val alpha = ((t - 2.2f) / 0.8f).coerceIn(0f, 1f)
                    Text("${thread.title.uppercase()} · ${thread.entries.size}", style = LabelStyle.copy(color = Ink.soft.copy(alpha = 0.9f * alpha), fontSize = 10.sp),
                        textAlign = TextAlign.Center, maxLines = 2,
                        modifier = Modifier.offset(x = with(density) { x.toDp() } - 75.dp, y = with(density) { y.toDp() } + 12.dp).width(150.dp)
                            .clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { go(Screen.Thread(thread.entries.map { it.id }, thread.title)) }
                            .padding(vertical = 4.dp))
                }
                if (all != null && all.isEmpty()) Text("Nebo je još prazno. Svaka bilješka ovdje postaje zvijezda.", style = BodyStyle,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).screenPadding())
                if (threads == null && all != null && all.isNotEmpty()) Text("Tražim sazviježđa…", style = SmallStyle, modifier = Modifier.align(Alignment.TopCenter))
            }
            // the note of a lone star, or the key to the colours
            val p = peek
            Column(Modifier.screenPadding().padding(bottom = 14.dp, top = 6.dp)) {
                if (p != null) Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xF20E1834)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(starColor(p)))
                        Text("  " + dayLabel(p.createdAt), style = SmallStyle.copy(shadow = null), modifier = Modifier.weight(1f))
                        Text("Zatvori", style = SmallStyle.copy(color = Ink.accent, shadow = null),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { peek = null }.padding(4.dp))
                    }
                    Text(p.words, style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, shadow = null), maxLines = 4)
                    GlassButton("Slične bilješke", { go(Screen.Similar(p.id)) }, icon = NeboIcon.Similar)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    for (k in listOf(FourPointsReader.THOUGHTS, FourPointsReader.EMOTIONS, FourPointsReader.BODY, FourPointsReader.URGES)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(POINT_COLORS.getValue(k)))
                            Text(POINT_NAMES.getValue(k), style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Places every note: the threads' notes as small figures in their own parts of the sky, every other note scattered
 * where its id puts it, kept out of the constellations' places.
 */
private fun layoutSky(all: List<Entry>, threads: List<InsightThread>, w: Float, h: Float): List<Star> {
    val stars = ArrayList<Star>()
    val inThread = HashMap<Long, Int>()
    val order = all.sortedBy { it.createdAt }.withIndex().associate { (i, e) -> e.id to i.toFloat() }
    val radius = SLOT_RADIUS * w
    val centers = SLOTS.map { Offset(it.x * w, it.y * h) }
    for ((i, th) in threads.withIndex()) {
        // a figure holds a thread's latest fourteen notes; the older ones belong to it too, so they are not scattered
        // as lone stars that would open on their own
        th.entries.forEach { inThread[it.id] = i }
        val notes = th.entries.sortedBy { it.createdAt }.takeLast(14)
        val pts = figureOf(notes, centers[i], radius, 0.9f)
        for ((k, e) in notes.withIndex()) {
            inThread[e.id] = i
            stars += Star(e, pts[k], starColor(e), i, order[e.id] ?: 0f)
        }
    }
    for (e in all) {
        if (e.id in inThread) continue
        val r = Random(e.id * 7919 + 17)
        var p = Offset((0.05f + 0.90f * r.nextFloat()) * w, (0.03f + 0.94f * r.nextFloat()) * h)
        // lone stars keep clear of the constellations
        for ((i, c) in centers.withIndex()) {
            if (i >= threads.size) break
            val d = hypot(p.x - c.x, p.y - c.y)
            if (d < radius * 1.15f) {
                val k = radius * 1.15f / d.coerceAtLeast(1f)
                p = Offset((c.x + (p.x - c.x) * k).coerceIn(0.03f * w, 0.97f * w), (c.y + (p.y - c.y) * k).coerceIn(0.02f * h, 0.98f * h))
            }
        }
        stars += Star(e, p, starColor(e), -1, order[e.id] ?: 0f)
    }
    return stars
}
