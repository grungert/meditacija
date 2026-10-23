package com.grungert.nebo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.app
import com.grungert.nebo.koan.FourPointsReader
import com.grungert.nebo.koan.Links
import com.grungert.nebo.koan.MindHabits
import com.grungert.nebo.koan.THEME_NAMES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/** A smooth line through every point (Catmull–Rom as cubic Béziers), so a curve still passes through the real values. */
internal fun smoothPath(pts: List<Offset>, path: Path = Path(), move: Boolean = true): Path {
    if (pts.isEmpty()) return path
    if (move) path.moveTo(pts[0].x, pts[0].y) else path.lineTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts[maxOf(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(i + 2, pts.size - 1)]
        path.cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
    }
    return path
}

/** A card that comes up into place a moment after the one above it. */
@Composable
private fun Arrive(order: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(70L * order); shown = true }
    AnimatedVisibility(shown, enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { it / 5 }) { content() }
}

@Composable
private fun CardHead(kicker: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(kicker, style = KickerStyle)
        Text(title, style = HeadStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, shadow = null))
    }
}

/** The span last looked at, so coming back to the overview shows the same one. */
private var lastRange: StatsRange? = null

/**
 * A calm overview, no streaks and no scores: what happened in a span of time (this week by default, or a month, three
 * months, a year, or any dates on the calendar), how it felt, what helps, how things come together, where the body
 * holds them, where the mind goes. Each card comes up in turn and its drawing grows in; legends switch parts on and off.
 */
@Composable
fun StatsScreen(go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var range by remember { mutableStateOf(lastRange ?: StatsRange.of(RangeKind.WEEK)) }
    LaunchedEffect(range) { lastRange = range }
    var picking by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<StatsData?>(null) }
    var web by remember { mutableStateOf<Links.Web?>(null) }
    var seen by remember { mutableStateOf<List<Pair<Set<com.grungert.nebo.koan.Thing>, Long>>>(emptyList()) }
    LaunchedEffect(range) {
        withContext(Dispatchers.IO) {
            val entries = app.db.entries()
            data = computeStats(app.db.sessions(), entries, app.db.prompts(0), range)
            // the span's notes, each read once for all the cards about links
            seen = Links.seen(entries.filter { it.createdAt >= range.from && it.createdAt < range.to })
            web = Links.webOf(seen.map { it.first })
        }
    }
    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.screenPadding()) {
                Text(ijekavian(SimpleDateFormat("EEEE, d. MMMM", Locale.forLanguageTag("sr-Latn-ME")).format(Date())).uppercase(), style = LabelStyle)
                Text("Pregled", style = TitleStyle.copy(fontSize = 38.sp, lineHeight = 42.sp))
                Text("Ne mjeri koliko si dobar, nego šta primjećuješ.", style = BodyStyle.copy(fontSize = 15.sp, color = Ink.soft))
            }
            RangePills(range, { range = StatsRange.of(it) }, { picking = true })
            val d = data ?: return@Column
            Column(Modifier.screenPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                var order = 0
                Arrive(order++) { PeriodCard(d) { go(Screen.CheckIn("week")) } }
                if (d.moodBuckets.any { it.isNotEmpty() }) Arrive(order++) { MoodSkyCard(d) }
                Arrive(order++) { HelpsCard(d) { go(Screen.Setup) } }
                web?.let { w ->
                    if (w.links.isNotEmpty()) {
                        Arrive(order++) { PatternCard(w) { constellationsMode = 1; go(Screen.Constellations(Screen.Stats)) } }
                        Arrive(order++) { FocusCard(w, seen) { t -> go(Screen.Focus(t.point, t.label, back = Screen.Stats)) } }
                        Arrive(order++) { MatrixCard(w) { t -> go(Screen.Focus(t.point, t.label, back = Screen.Stats)) } }
                    }
                }
                Arrive(order++) { BodyCard(d) }
                Arrive(order++) { MindCard(d) }
                if (d.dayPartSessions.any { it > 0 }) Arrive(order++) {
                    Panel {
                        CardHead("DOBA DANA", "Kad sjedneš")
                        DayParts(d.dayPartSessions)
                        val lead = d.dayPartSessions.indices.maxBy { d.dayPartSessions[it] }
                        if (d.dayPartSessions[lead] >= 2 && d.dayPartSessions.count { it == d.dayPartSessions[lead] } == 1)
                            Text("Najčešće ${Links.PART_WHEN[lead]}.", style = ReadStyle)
                    }
                }
                if (d.history.size > 1) Arrive(order++) {
                    Panel {
                        CardHead("KROZ VRIJEME", when (d.range.kind) {
                            RangeKind.WEEK -> "Minute po sedmici"; RangeKind.MONTH -> "Minute po 30 dana"; RangeKind.QUARTER -> "Minute po tri mjeseca"
                            RangeKind.YEAR -> "Minute po godini"; RangeKind.CUSTOM -> "Minute po periodu iste dužine"
                        })
                        Bars(d.history.map { it.toFloat() }, d.historyLabels, highlightLast = true)
                    }
                }
                if (d.themes.isNotEmpty()) Arrive(order++) { ThemesCard(d) }
                Arrive(order++) { Tiles(d) }
            }
        }
        if (picking) RangePickerDialog({ a, b -> picking = false; range = StatsRange.custom(a, b) }, { picking = false })
    }
}

/** The spans as pills that scroll sideways; the last one opens the calendar, and shows the dates once chosen. */
@Composable
private fun RangePills(range: StatsRange, onKind: (RangeKind) -> Unit, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        for (k in RangeKind.entries) {
            val on = range.kind == k
            val bg by animateColorAsState(if (on) Ink.accent else Ink.surface, label = "range")
            Row(
                Modifier.tap(k.pill, androidx.compose.ui.semantics.Role.Tab, shape = RoundedCornerShape(18.dp)) { if (k == RangeKind.CUSTOM) onPick() else onKind(k) }
                    .background(bg, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (k == RangeKind.CUSTOM) Icon(NeboIcon.Week, if (on) Ink.ink else Ink.text, 16.dp, filled = on)
                Text(if (k == RangeKind.CUSTOM && on) range.title else k.pill,
                    style = SmallStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (on) Ink.ink else Ink.text, shadow = null), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The calendar's day (UTC midnight) as the same day at local midnight. */
private fun localDay(utc: Long): Long {
    val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply { clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
}

/** Any dates, up to today, on a calendar in the app's colours and in our language, its weeks from Monday, whatever the phone speaks. */
@Composable
private fun RangePickerDialog(onPick: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    val base = androidx.compose.ui.platform.LocalConfiguration.current
    val ours = remember(base) { android.content.res.Configuration(base).apply { setLocale(Locale.forLanguageTag("sr-Latn-ME")) } }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides ours) { RangeCalendar(onPick, onDismiss) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RangeCalendar(onPick: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    val state = androidx.compose.material3.rememberDateRangePickerState(selectableDates = object : androidx.compose.material3.SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
    })
    val colors = androidx.compose.material3.DatePickerDefaults.colors(
        containerColor = Ink.sheet, titleContentColor = Ink.soft, headlineContentColor = Ink.text, weekdayContentColor = Ink.soft,
        subheadContentColor = Ink.soft, navigationContentColor = Ink.text, yearContentColor = Ink.text, currentYearContentColor = Ink.accentInk,
        selectedYearContainerColor = Ink.accent, selectedYearContentColor = Ink.ink, dayContentColor = Ink.text, disabledDayContentColor = Ink.faint,
        selectedDayContainerColor = Ink.accent, selectedDayContentColor = Ink.ink, todayContentColor = Ink.accentInk, todayDateBorderColor = Ink.accentInk,
        dayInSelectionRangeContainerColor = Ink.accent.copy(alpha = 0.35f), dayInSelectionRangeContentColor = Ink.text, dividerColor = Ink.hairline,
    )
    val start = state.selectedStartDateMillis; val end = state.selectedEndDateMillis
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton({ if (start != null) onPick(localDay(start), localDay(end ?: start)) }, enabled = start != null) {
                Text("Prikaži", style = BodyStyle.copy(fontWeight = FontWeight.Bold, color = if (start != null) Ink.accentInk else Ink.faint, shadow = null))
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text("Otkaži", style = BodyStyle.copy(color = Ink.soft, shadow = null)) } },
        colors = colors,
    ) {
        androidx.compose.material3.DateRangePicker(state, Modifier.weight(1f), colors = colors, showModeToggle = false,
            title = { Text("Izaberi period", style = KickerStyle, modifier = Modifier.padding(start = 24.dp, top = 20.dp)) },
            headline = {
                Text(if (start == null) "Od kog dana?" else if (end == null) "${SimpleDateFormat("d. M.", Locale.ROOT).format(Date(localDay(start)))} – do kog dana?"
                    else "${SimpleDateFormat("d. M.", Locale.ROOT).format(Date(localDay(start)))} – ${SimpleDateFormat("d. M. yyyy.", Locale.ROOT).format(Date(localDay(end)))}",
                    style = HeadStyle.copy(fontSize = 20.sp, shadow = null), modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
            })
    }
}

/** A figure with what it was in the span before: an arrow up or down and by how much, in quiet grey, never a score. */
@Composable
private fun Figure(n: Int, label: String, before: Int, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Ink.text.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$n", style = NumberStyle.copy(fontSize = 30.sp, lineHeight = 34.sp))
        Text(label, style = SmallStyle.copy(fontWeight = FontWeight.Bold, color = Ink.text, shadow = null), maxLines = 1)
        val diff = n - before
        Text(when { diff > 0 -> "↑ $diff"; diff < 0 -> "↓ ${-diff}"; else -> "kao prije" }, style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, shadow = null))
    }
}

/**
 * The span at a glance: which span, one sentence on how many days held a sit, three figures beside the span before,
 * the minutes as a wave over its days (or weeks, or months), and the way to the week's review.
 */
@Composable
private fun PeriodCard(d: StatsData, onReview: () -> Unit) {
    val r = d.range
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(r.title.uppercase() + (if (r.kind != RangeKind.CUSTOM) " · " + SimpleDateFormat("d. M.", Locale.ROOT).format(Date(r.from)) + " – " +
                SimpleDateFormat("d. M.", Locale.ROOT).format(Date(r.to - 1)) else ""), style = KickerStyle)
            Text(when {
                d.practicedDays == 0 && r.kind == RangeKind.WEEK && d.daysSoFar <= 1 -> "Sedmica tek počinje"
                d.practicedDays == 0 -> "U ovom periodu još nema sesija"
                else -> "Meditacija u ${d.practicedDays} od ${d.daysSoFar} ${plural(d.daysSoFar, "dana", "dana", "dana")}"
            }, style = HeadStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, shadow = null))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Figure(d.minutes, "minuta", d.prevMinutes, Modifier.weight(1f))
            Figure(d.sessions, plural(d.sessions, "sesija", "sesije", "sesija"), d.prevSessions, Modifier.weight(1f))
            Figure(d.notes, plural(d.notes, "zapis", "zapisa", "zapisa"), d.prevNotes, Modifier.weight(1f))
        }
        Text("Strelice: u odnosu na " + when (r.kind) {
            RangeKind.WEEK -> "prošlu sedmicu"; RangeKind.MONTH -> "prethodnih 30 dana"; RangeKind.QUARTER -> "prethodna tri mjeseca"
            RangeKind.YEAR -> "godinu prije"; RangeKind.CUSTOM -> "isto toliko dana prije"
        } + ".", style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
        Wave(d.wave, d.waveLabels)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val peak = d.wave.indices.filter { d.wave[it] > 0 }.maxByOrNull { d.wave[it] }
            // a day's letter says too little ("P" is Monday and Friday): the day by name when the stretches are days
            val name = peak?.let { if (r.days <= 31) ijekavian(SimpleDateFormat(if (r.days <= 7) "EEEE" else "d. M.", Locale.forLanguageTag("sr-Latn-ME"))
                .format(Date(r.from + it * 86_400_000L + 43_200_000L))) else d.waveLabels[it] }
            Text(if (peak == null) "" else "Najviše: ${name?.replaceFirstChar { it.uppercase() }} · ${d.wave[peak]} min",
                style = SmallStyle.copy(fontWeight = FontWeight.SemiBold, shadow = null), modifier = Modifier.weight(1f))
            Row(Modifier.tap("Sedmični pregled", shape = RoundedCornerShape(14.dp), onClick = onReview).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sedmični pregled", style = SmallStyle.copy(fontWeight = FontWeight.ExtraBold, color = Ink.accentInk, shadow = null))
                Icon(NeboIcon.Forward, Ink.accentInk, 13.dp)
            }
        }
    }
}

/**
 * The minutes of each stretch as a breath-like wave; stretches still ahead wait as dashed rings. With many stretches
 * only a few labels show, and only the highest and the last get a dot.
 */
@Composable
private fun Wave(values: List<Int>, labels: List<String>) {
    val k = rememberReveal(values, 1100)
    val tm = rememberTextMeasurer()
    val ink = Ink.text
    val dot = if (Ink.day) Color.White else Color(0xFF16244A)
    val gold = Color(0xFFE0A04E)
    Canvas(Modifier.fillMaxWidth().height(124.dp)) {
        val n = values.size
        if (n == 0) return@Canvas
        val w = size.width
        val base = size.height - 26.dp.toPx()
        val top = 22.dp.toPx()
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val xs = (0 until n).map { 14.dp.toPx() + it * (w - 28.dp.toPx()) / (n - 1).coerceAtLeast(1) }
        fun y(v: Int) = base - (base - top) * (v / max)
        val done = values.withIndex().filter { it.value >= 0 }.map { Offset(xs[it.index], y(it.value)) }
        drawLine(ink.copy(alpha = 0.12f), Offset(0f, base), Offset(w, base), 1.dp.toPx())
        // many stretches (a month of days) read better as thin bars than as a curve that spikes between empty days
        if (n > 10) {
            val bw = ((w - 28.dp.toPx()) / n * 0.55f).coerceAtMost(10.dp.toPx())
            val peakAt = values.indices.filter { values[it] >= 0 }.maxByOrNull { values[it] }
            for ((i, v) in values.withIndex()) {
                if (v < 0) continue
                val hh = ((base - top) * (v / max) * k).coerceAtLeast(if (v > 0) 4.dp.toPx() else 2.dp.toPx())
                drawRoundRect(if (i == peakAt) gold else Ink.accent.copy(alpha = if (v > 0) 0.9f else 0.35f), Offset(xs[i] - bw / 2, base - hh), Size(bw, hh), CornerRadius(bw / 2))
            }
        } else if (done.size >= 2) clipRect(right = w * k) {
            val area = smoothPath(done).apply { lineTo(done.last().x, base); lineTo(done.first().x, base); close() }
            drawPath(area, Brush.verticalGradient(listOf(Ink.accent.copy(alpha = 0.85f), Ink.accent.copy(alpha = 0.04f)), startY = top, endY = base))
            drawPath(smoothPath(done), gold, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        val peak = values.indices.filter { values[it] >= 0 }.maxByOrNull { values[it] }
        val lastDone = values.indices.lastOrNull { values[it] >= 0 }
        val many = n > 10
        val every = if (many) ((n + 4) / 5) else 1
        for ((i, v) in values.withIndex()) {
            if (v < 0) { if (!many) drawCircle(ink.copy(alpha = 0.25f), 3.dp.toPx(), Offset(xs[i], base), style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))) }
            else if (!many && xs[i] <= w * k + 1) {
                val r = (if (i == peak) 5f else 3.5f).dp.toPx()
                drawCircle(if (i == peak) gold else dot, r, Offset(xs[i], y(v)))
                drawCircle(gold, r, Offset(xs[i], y(v)), style = Stroke(2.dp.toPx()))
            }
            if (i % every == 0 || i == n - 1 && (n - 1) % every >= every / 2) {
                val l = tm.measure(labels.getOrElse(i) { "" }, TextStyle(fontFamily = UiFont, fontSize = 11.sp, fontWeight = if (i == peak) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = ink.copy(alpha = if (i == peak) 1f else 0.6f)))
                drawText(l, topLeft = Offset((xs[i] - l.size.width / 2f).coerceIn(0f, w - l.size.width), base + 8.dp.toPx()))
            }
        }
        if (peak != null && values[peak] > 0) {
            val l = tm.measure("${values[peak]} min", TextStyle(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ink))
            drawText(l, topLeft = Offset((xs[peak] - l.size.width / 2f).coerceIn(0f, w - l.size.width), y(values[peak]) - l.size.height - 8.dp.toPx()), alpha = k)
        }
    }
}

/** Calm moods first, so the stream reads from light to heavy. */
private val MOOD_ORDER = listOf("mirno", "zahvalno", "radosno", "radoznalo", "umorno", "zbunjeno", "nemirno", "zabrinuto", "tužno", "ljuto")

/**
 * A legend whose entries switch their part of a chart on and off: a touch hides or shows one, a long press shows
 * only that one, and "Sve" brings them all back.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ToggleLegend(items: List<Pair<String, Color>>, hidden: Set<String>, onChange: (Set<String>) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((name, color) in items) {
            val off = name in hidden
            val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val a by animateFloatAsState(if (off) 0.4f else 1f, label = "legend")
            Row(
                Modifier.pressScale(src).clip(RoundedCornerShape(14.dp))
                    .combinedClickable(src, androidx.compose.foundation.LocalIndication.current, onClickLabel = if (off) "Prikaži" else "Sakrij",
                        onLongClickLabel = "Samo ovo", onLongClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onChange(items.map { it.first }.filter { it != name }.toSet())
                        }) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onChange(if (off) hidden - name else hidden + name)
                    }
                    .background(Ink.text.copy(alpha = if (off) 0.03f else 0.07f)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (off) Color.Transparent else color).border(1.5.dp, color.copy(alpha = a), CircleShape))
                Text(name, style = SmallStyle.copy(fontWeight = FontWeight.Bold, color = Ink.text.copy(alpha = a), shadow = null,
                    textDecoration = if (off) androidx.compose.ui.text.style.TextDecoration.LineThrough else null))
            }
        }
        if (hidden.isNotEmpty()) Text("Sve", style = SmallStyle.copy(fontWeight = FontWeight.ExtraBold, color = Ink.accentInk, shadow = null),
            modifier = Modifier.tap("Prikaži sve", shape = RoundedCornerShape(14.dp)) { onChange(emptySet()) }.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

/**
 * The moods of the span as one flowing stream, each mood a band as wide as the notes that held it in each stretch.
 * The legend switches moods on and off; the stream reshapes itself softly around what is left.
 */
@Composable
private fun MoodSkyCard(d: StatsData) {
    var hidden by remember { mutableStateOf(emptySet<String>()) }
    Panel {
        CardHead("NEBO RASPOLOŽENJA", "Kako ti je bilo")
        val k = rememberReveal(d.moodBuckets, 1400)
        val moods = MOOD_ORDER.filter { m -> d.moodBuckets.any { (it[m] ?: 0) > 0 } } + d.moodBuckets.flatMap { it.keys }.filter { it !in MOOD_ORDER }.distinct()
        // each mood fades in and out of the stream instead of jumping
        val weight = moods.associateWith { m -> animateFloatAsState(if (m in hidden) 0f else 1f, tween(450), label = "mood-$m").value }
        val edge = if (Ink.day) Color.White.copy(alpha = 0.7f) else Color(0x99101C3A)
        val single = d.moodBuckets.size == 1
        // with moods on only a few days a stream would pinch to nothing between them: then each stretch is a column of dots
        val sparse = d.moodBuckets.count { it.isNotEmpty() } < 4
        val tm = rememberTextMeasurer()
        val ink = Ink.text
        if (sparse) Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = d.moodBuckets.size
            val w = size.width
            val base = size.height - 22.dp.toPx()
            val xs = (0 until n).map { w * (it + 0.5f) / n }
            val step = 15.dp.toPx(); val r = 6.dp.toPx()
            for ((i, b) in d.moodBuckets.withIndex()) {
                var y = base - r
                for (m in moods) {
                    val wt = weight.getValue(m)
                    repeat(b[m] ?: 0) {
                        if (wt > 0.001f && y > r) {
                            drawCircle((MOOD_COLORS[m] ?: Color.Gray).copy(alpha = wt), r * (0.6f + 0.4f * wt) * k, Offset(xs[i], y))
                            y -= step * wt
                        }
                    }
                }
                if (b.isEmpty()) drawCircle(ink.copy(alpha = 0.15f), 2.dp.toPx(), Offset(xs[i], base - r))
                val l = tm.measure(d.moodLabels.getOrElse(i) { "" }, TextStyle(fontFamily = UiFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.6f)))
                if (n <= 12 || i % 2 == 0) drawText(l, topLeft = Offset((xs[i] - l.size.width / 2f).coerceIn(0f, w - l.size.width), base + 6.dp.toPx()))
            }
        } else Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width; val h = size.height
            val buckets = if (single) d.moodBuckets + d.moodBuckets else d.moodBuckets
            val totals = buckets.map { b -> moods.sumOf { ((b[it] ?: 0) * weight.getValue(it)).toDouble() }.toFloat() }
            val unit = h * 0.92f / (totals.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val xs = buckets.indices.map { 6.dp.toPx() + it * (w - 12.dp.toPx()) / (buckets.size - 1).coerceAtLeast(1) }
            val base = totals.map { h / 2 - it * unit / 2 }
            clipRect(right = w * k) {
                var below = List(xs.size) { 0f }
                for (m in moods) {
                    val wt = weight.getValue(m)
                    if (wt <= 0.001f) continue
                    val y0 = xs.indices.map { base[it] + below[it] }
                    val y1 = xs.indices.map { y0[it] + (buckets[it][m] ?: 0) * unit * wt }
                    if (y1.zip(y0).all { (a, b) -> a - b < 0.1f }) continue
                    val path = smoothPath(xs.indices.map { Offset(xs[it], y0[it]) })
                    smoothPath(xs.indices.reversed().map { Offset(xs[it], y1[it]) }, path, move = false)
                    path.close()
                    drawPath(path, (MOOD_COLORS[m] ?: Color.Gray).copy(alpha = wt))
                    drawPath(path, edge, style = Stroke(1.dp.toPx()))
                    below = xs.indices.map { below[it] + (buckets[it][m] ?: 0) * unit * wt }
                }
            }
        }
        if (!sparse) Row(Modifier.fillMaxWidth()) {
            Text(d.moodLabels.firstOrNull() ?: "", style = SmallStyle.copy(fontSize = 11.5.sp, shadow = null), modifier = Modifier.weight(1f))
            if (d.moodLabels.size > 1) Text(d.moodLabels.last(), style = SmallStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Ink.text, shadow = null))
        }
        ToggleLegend(moods.map { it to (MOOD_COLORS[it] ?: Color.Gray) }, hidden) { hidden = it }
        moodTrend(d.moodBuckets)?.let { Text(it, style = ReadStyle) }
    }
}

/** One sentence about how the moods moved: the first half of the span against the second, only when a mood clearly rose or fell. */
private fun moodTrend(buckets: List<Map<String, Int>>): String? {
    if (buckets.size < 4) return null
    val half = buckets.size / 2
    val early = buckets.take(half); val late = buckets.takeLast(half)
    val moods = buckets.flatMap { it.keys }.distinct()
    val change = moods.associateWith { m -> late.sumOf { it[m] ?: 0 } - early.sumOf { it[m] ?: 0 } }
    val up = change.entries.filter { it.value >= 2 }.maxByOrNull { it.value }
    val down = change.entries.filter { it.value <= -2 }.minByOrNull { it.value }
    return when {
        up != null && down != null -> "Više je „${up.key}“, manje „${down.key}“ nego na početku perioda."
        up != null -> "„${up.key.replaceFirstChar { it.uppercase() }}“ dolazi češće nego na početku perioda."
        down != null -> "„${down.key.replaceFirstChar { it.uppercase() }}“ dolazi rjeđe nego na početku perioda."
        else -> null
    }
}

/** "Poslije …": each kind of session after the word "after". */
private val AFTER_TYPE = mapOf(
    "breath" to "Disanja", "walk" to "šetnje", "thoughts" to "Oblaka", "silence" to "Tišine", "points" to "4 tačke",
    "hourglass" to "Pješčanog sata", "bodyscan" to "skeniranja tijela", "urge" to "Talasa", "rain" to "RAIN-a",
)
private val PART_WHEN = listOf("ujutru", "danju", "uveče", "noću")

/**
 * What helps: from notes before and after a session, how often things were lighter after; and once there are enough
 * such pairs, the moods going in and coming out as ribbons. With too few, a gentle way to start asking before and after.
 */
@Composable
private fun HelpsCard(d: StatsData, onSetup: () -> Unit) {
    val context = LocalContext.current
    val s = context.app.settings
    Panel {
        CardHead("ŠTA TI POMAŽE", "Iz bilješki prije i poslije sesije")
        val h = d.helps
        if (h != null && h.kinds.isNotEmpty()) {
            val top = h.kinds.first()
            Text("Poslije ${AFTER_TYPE[top.type] ?: top.type} si bio lakše ${top.lighter} od ${top.count} puta.", style = ReadStyle.copy(fontSize = 21.sp, lineHeight = 29.sp))
            for (kind in h.kinds.take(4)) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(com.grungert.nebo.data.TYPE_LABEL[kind.type] ?: kind.type, style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, shadow = null), modifier = Modifier.weight(1f))
                    Text("${kind.lighter} od ${kind.count}", style = SmallStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
                }
                Segments(kind.lighter, kind.count)
            }
            h.partFor?.let { (t, better, worse) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.text.copy(alpha = 0.06f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (better >= 2) NeboIcon.Moon else NeboIcon.Sun, Ink.text, 20.dp)
                    Text("${com.grungert.nebo.data.TYPE_LABEL[t] ?: t} ti više pomaže ${PART_WHEN[better]} nego ${PART_WHEN[worse]}.", style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = null))
                }
            }
        }
        if (d.flowPairs >= 5) {
            SectionLabel("Prije → poslije")
            Ribbons(listOf(d.flows.map { it.first }.distinct(), d.flows.map { it.second }.distinct()).let { names ->
                listOf(names[0].map { n -> "p:$n" to d.flows.filter { it.first == n }.sumOf { it.third } },
                    names[1].map { n -> "a:$n" to d.flows.filter { it.second == n }.sumOf { it.third } })
            }, d.flows.map { Triple("p:" + it.first, "a:" + it.second, it.third) }, { MOOD_COLORS[it.drop(2)] ?: Color.Gray }, listOf("PRIJE", "POSLIJE"),
                name = { it.drop(2) }, height = 200)
            Text("${d.flowPairs} ${plural(d.flowPairs, "sesija", "sesije", "sesija")} sa zapisom i prije i poslije.", style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
        } else {
            var asked by remember { mutableStateOf(s.checkInBefore && s.checkInAfter) }
            Text(if (h == null || h.kinds.isEmpty()) "Kad uz sesiju kažeš kako si prije i poslije, ovdje se vidi šta ti stvarno pomaže. Do sada: ${d.flowPairs} od potrebnih 5."
                else "Za tok raspoloženja prije i poslije treba još ${5 - d.flowPairs} ${plural(5 - d.flowPairs, "sesija", "sesije", "sesija")} sa oba zapisa.",
                style = SmallStyle.copy(shadow = null))
            if (!asked) GlassButton("Pitaj me prije i poslije sesije", { s.checkInBefore = true; s.checkInAfter = true; asked = true }, Modifier.fillMaxWidth(), icon = NeboIcon.Mic)
            else Text("Uključeno: prije i poslije sesije te pitam jednom riječju. Isključuješ u podešavanju sesije.", style = SmallStyle.copy(color = Ink.accentInk, shadow = null),
                modifier = Modifier.tap("Podesi sesiju", shape = RoundedCornerShape(10.dp), onClick = onSetup).padding(vertical = 4.dp))
        }
    }
}

/** n of m as m small segments, the first n filled in calm green, filling in one after another. */
@Composable
private fun Segments(n: Int, m: Int) {
    val k = rememberReveal(n to m, 900)
    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(m) { i ->
            val on = i < n && k * m > i
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(if (on) Color(0xFF5FBFA6) else Ink.text.copy(alpha = 0.1f)))
        }
    }
}

/**
 * Columns of things with ribbons between them, each as thick as the notes that hold both ends.
 * [columns] hold (key, size); a key must be unique across columns. Two columns stand side by side, their names outside;
 * more than two run from the top down, one row per column, so the names have the whole width of a phone.
 */
@Composable
internal fun Ribbons(columns: List<List<Pair<String, Int>>>, flows: List<Triple<String, String, Int>>, colorOf: (String) -> Color, headers: List<String>,
    name: (String) -> String = { it }, height: Int = 250) {
    val k = rememberReveal(flows, 1200)
    val tm = rememberTextMeasurer()
    val ink = Ink.text
    val halo = if (Ink.day) Color.White else Color(0xFF101C3A)
    val vertical = columns.size > 2
    Canvas(Modifier.fillMaxWidth().height((if (vertical) 96 * columns.size else height).dp)) {
        val bar = 9.dp.toPx(); val gap = (if (vertical) 10 else 8).dp.toPx()
        val n = columns.size
        // a node is as long as the most that flows through it, in or out, or its own size when nothing does
        val through = HashMap<String, Int>()
        for (col in columns) for ((key, own) in col) {
            val out = flows.filter { it.first == key }.sumOf { it.third }; val inn = flows.filter { it.second == key }.sumOf { it.third }
            through[key] = maxOf(out, inn, if (out == 0 && inn == 0) own else 0)
        }
        val nameStyle = TextStyle(fontFamily = UiFont, fontSize = (if (vertical) 11.5 else 12.0).sp, fontWeight = FontWeight.Bold, color = ink,
            shadow = androidx.compose.ui.graphics.Shadow(halo, blurRadius = 8f))
        val headStyle = TextStyle(fontFamily = UiFont, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = ink.copy(alpha = 0.6f))
        val start = HashMap<String, Float>(); val outAt = HashMap<String, Float>(); val inAt = HashMap<String, Float>()
        if (vertical) {
            val w = size.width
            val headH = 16.dp.toPx(); val labelH = 42.dp.toPx()
            val step = (size.height - headH - labelH) / (n - 1).coerceAtLeast(1)
            val ys = (0 until n).map { headH + it * step }
            val unit = columns.minOf { col -> (w - gap * (col.size - 1)) / col.sumOf { through.getValue(it.first) }.coerceAtLeast(1) }
            for (col in columns) {
                val total = col.sumOf { through.getValue(it.first) } * unit + gap * (col.size - 1)
                var x = (w - total) / 2
                for ((key, _) in col) { start[key] = x; outAt[key] = x; inAt[key] = x; x += through.getValue(key) * unit + gap }
            }
            clipRect(bottom = size.height * k) {
                for ((a, b, c) in flows) {
                    val xa = outAt[a] ?: continue; val xb = inAt[b] ?: continue
                    outAt[a] = xa + c * unit; inAt[b] = xb + c * unit
                    val ca = columns.indexOfFirst { col -> col.any { it.first == a } }
                    val cb = columns.indexOfFirst { col -> col.any { it.first == b } }
                    val y0 = ys[ca] + bar; val y1 = ys[cb]; val ym = (y0 + y1) / 2; val ww = c * unit
                    val p = Path().apply {
                        moveTo(xa, y0); cubicTo(xa, ym, xb, ym, xb, y1); lineTo(xb + ww, y1); cubicTo(xb + ww, ym, xa + ww, ym, xa + ww, y0); close()
                    }
                    drawPath(p, colorOf(a).copy(alpha = 0.42f))
                }
            }
            for ((ci, col) in columns.withIndex()) {
                headers.getOrNull(ci)?.let { drawText(tm.measure(it, headStyle), topLeft = Offset(0f, ys[ci] - 14.dp.toPx())) }
                for ((key, _) in col) {
                    val x = start.getValue(key); val ww = through.getValue(key) * unit
                    drawRoundRect(colorOf(key), Offset(x, ys[ci]), Size(ww.coerceAtLeast(3f), bar), CornerRadius(4.dp.toPx()))
                    // the name under its node, as wide as the node allows (two lines at most), never past the edges
                    val room = (ww + gap - 2.dp.toPx()).coerceIn(44.dp.toPx(), 130.dp.toPx())
                    val l = tm.measure(name(key), nameStyle.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp, lineHeight = 13.sp),
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = room.toInt()), maxLines = 3)
                    val cx = (x + ww / 2 - l.size.width / 2f).coerceIn(0f, w - l.size.width)
                    drawText(l, topLeft = Offset(cx, ys[ci] + bar + 3.dp.toPx()))
                }
            }
            return@Canvas
        }
        val w = size.width
        val headH = 20.dp.toPx()
        val h = size.height - headH
        val left = 86.dp.toPx(); val right = 96.dp.toPx()
        val xs = (0 until n).map { left + it * (w - left - right - bar) / (n - 1).coerceAtLeast(1) }
        val unit = columns.minOf { col -> (h - gap * (col.size - 1)) / col.sumOf { through.getValue(it.first) }.coerceAtLeast(1) }
        for (col in columns) {
            val total = col.sumOf { through.getValue(it.first) } * unit + gap * (col.size - 1)
            var y = headH + (h - total) / 2
            for ((key, _) in col) { start[key] = y; outAt[key] = y; inAt[key] = y; y += through.getValue(key) * unit + gap }
        }
        for ((i, head) in headers.withIndex()) if (i < n) {
            val l = tm.measure(head, headStyle)
            drawText(l, topLeft = Offset(xs[i] + bar / 2 - l.size.width / 2f, 0f))
        }
        clipRect(right = w * k) {
            for ((a, b, c) in flows) {
                val ya = outAt[a] ?: continue; val yb = inAt[b] ?: continue
                outAt[a] = ya + c * unit; inAt[b] = yb + c * unit
                val ca = columns.indexOfFirst { col -> col.any { it.first == a } }
                val x0 = xs[ca] + bar; val x1 = xs[ca + 1]; val xm = (x0 + x1) / 2; val hh = c * unit
                val p = Path().apply {
                    moveTo(x0, ya); cubicTo(xm, ya, xm, yb, x1, yb); lineTo(x1, yb + hh); cubicTo(xm, yb + hh, xm, ya + hh, x0, ya + hh); close()
                }
                drawPath(p, colorOf(a).copy(alpha = 0.45f))
            }
        }
        for ((ci, col) in columns.withIndex()) for ((key, _) in col) {
            val y = start.getValue(key); val hh = through.getValue(key) * unit
            drawRoundRect(colorOf(key), Offset(xs[ci], y), Size(bar, hh.coerceAtLeast(3f)), CornerRadius(4.dp.toPx()))
            val l = tm.measure(name(key), nameStyle)
            val x = if (ci == 0) xs[ci] - 8.dp.toPx() - l.size.width else xs[ci] + bar + 7.dp.toPx()
            drawText(l, topLeft = Offset(x.coerceAtLeast(0f), y + hh / 2 - l.size.height / 2f))
        }
    }
}

/**
 * The pattern: what comes together in the notes of the last two months, through all four points: thoughts, emotions,
 * the body, urges. Only links that came back are drawn; things in the same note have no order, so this is a picture of
 * what goes together, not of what causes what.
 */
@Composable
private fun PatternCard(w: Links.Web, onAll: () -> Unit) {
    val order = listOf(FourPointsReader.THOUGHTS, FourPointsReader.EMOTIONS, FourPointsReader.BODY, FourPointsReader.URGES)
    var hidden by remember(w) { mutableStateOf(emptySet<String>()) }   // points switched off in the legend, by name
    // up to four things per point, among those that have a link, the most frequent first
    val linked = w.links.flatMap { listOf(it.a, it.b) }.toSet()
    val present = order.filter { p -> linked.any { it.point == p } }
    if (present.size < 2) return
    val cols = order.filter { POINT_NAMES.getValue(it) !in hidden }
        .map { p -> linked.filter { it.point == p }.sortedByDescending { w.counts[it] ?: 0 }.take(4) }.filter { it.isNotEmpty() }
    val idx = cols.flatMapIndexed { i, c -> c.map { it to i } }.toMap()
    // a ribbon for every link, from the upper row to the lower; those that skip a row pass behind it, since all four touch
    val flows = w.links.mapNotNull { l ->
        val ia = idx[l.a]; val ib = idx[l.b]
        when {
            ia == null || ib == null || ia == ib -> null
            ia < ib -> Triple(l.a.point + ":" + l.a.label, l.b.point + ":" + l.b.label, l.together)
            else -> Triple(l.b.point + ":" + l.b.label, l.a.point + ":" + l.a.label, l.together)
        }
    }.sortedBy { f -> idx.entries.first { (it.key.point + ":" + it.key.label) == f.second }.value }
    Panel {
        CardHead("TVOJ OBRAZAC", "Šta ide zajedno")
        ToggleLegend(present.map { POINT_NAMES.getValue(it) to POINT_COLORS.getValue(it) }, hidden) { hidden = it }
        if (flows.isEmpty() || cols.size < 2) Text("Uključi bar dvije tačke koje se dodiruju.", style = SmallStyle.copy(shadow = null))
        else Ribbons(cols.map { c -> c.map { (it.point + ":" + it.label) to (w.counts[it] ?: 0) } }, flows,
            { POINT_COLORS[it.substringBefore(':')] ?: Color.Gray },
            cols.map { POINT_NAMES.getValue(it.first().point).uppercase() }, name = { it.substringAfter(':').removePrefix("da ") })
        w.links.firstOrNull()?.let { l ->
            Text("Najčešće zajedno: „${l.a.label.trim('„', '“')}“ i „${l.b.label.trim('„', '“')}“, ${l.together} ${plural(l.together, "put", "puta", "puta")}.", style = ReadStyle)
        }
        Text("U istim bilješkama; šta je bilo prvo, ne znamo. Sve utiče na sve, a ovdje je ono što se ponavlja.", style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
        GlassButton("Vidi sve veze", onAll, Modifier.fillMaxWidth(), icon = NeboIcon.Stars)
    }
}

/**
 * "Kad se javi…" for the things that come back most: a chip for each, and around the chosen one what comes with it.
 * Touching a thing around it makes that one the middle; the button opens it whole.
 */
@Composable
private fun FocusCard(w: Links.Web, seen: List<Pair<Set<com.grungert.nebo.koan.Thing>, Long>>, onOpen: (com.grungert.nebo.koan.Thing) -> Unit) {
    val top = remember(w) { w.stars(5) }
    // several can be on at once, like boxes to tick; one always stays, and at most four
    var chosen by remember(w) { mutableStateOf(top.take(1)) }
    if (chosen.isEmpty()) return
    fun toggle(t: com.grungert.nebo.koan.Thing) {
        chosen = when {
            t in chosen && chosen.size > 1 -> chosen - t
            t in chosen -> chosen
            else -> (chosen + t).takeLast(4)
        }
    }
    val foci = remember(chosen, seen) { chosen.map { Links.focusOf(seen, it) } }
    Panel {
        CardHead("KAD SE JAVI…", "Šta dolazi s tim")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in (top + chosen.filter { it !in top })) {
                val on = c in chosen
                val pc = POINT_COLORS.getValue(c.point)
                val bg by animateColorAsState(if (on) pc else pc.copy(alpha = if (Ink.day) 0.22f else 0.14f), label = "focus-chip")
                // several can be on at once: a chosen one in its full colour, the others pale
                Text(c.label, style = SmallStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on || Ink.day) Ink.ink else Color.White, shadow = null),
                    modifier = Modifier.tap(c.label, androidx.compose.ui.semantics.Role.Checkbox, shape = RoundedCornerShape(14.dp)) { toggle(c) }
                        .background(bg, RoundedCornerShape(14.dp)).padding(horizontal = 11.dp, vertical = 6.dp))
            }
        }
        if (foci.all { it.with.isEmpty() }) Text("Uz ovo još ništa ne dolazi dovoljno često.", style = SmallStyle.copy(shadow = null))
        else FocusMany(foci, { toggle(it) }, Modifier.fillMaxWidth().height(if (foci.size > 1) 320.dp else 280.dp))
        for (f in foci) f.sentence()?.let { Text(it, style = ReadStyle.copy(fontSize = if (foci.size > 1) 17.sp else 19.sp, lineHeight = if (foci.size > 1) 24.sp else 27.sp)) }
        if (foci.size > 1) Text("Dodirni pojam oko njih da ga dodaš; dodirni onaj u sredini da ga skloniš.", style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
        GlassButton("Otvori „${chosen.last().label.trim('„', '“')}“", { onOpen(chosen.last()) }, Modifier.fillMaxWidth(), icon = NeboIcon.Forward)
    }
}

/**
 * The whole picture, for the curious: each mood against the body, the pulls and the habits of mind, a dot as big as
 * the notes that held both. Links that came back are full; those seen once are pale. A row's mood opens "Kad se javi…".
 */
@Composable
private fun MatrixCard(w: Links.Web, onOpen: (com.grungert.nebo.koan.Thing) -> Unit) {
    val rows = remember(w) {
        w.counts.filter { it.key.point == FourPointsReader.EMOTIONS && it.value >= 2 }.entries.sortedByDescending { it.value }.map { it.key }
            .filter { r -> w.counts.keys.any { it.point != FourPointsReader.EMOTIONS && w.together(r, it) > 0 } }.take(6)
    }
    var hidden by remember(w) { mutableStateOf(emptySet<String>()) }
    val groups = remember(w) { listOf(FourPointsReader.BODY, FourPointsReader.URGES, FourPointsReader.THOUGHTS).filter { p -> w.counts.keys.any { it.point == p && (w.counts[it] ?: 0) >= 2 } } }
    val cols = remember(w, hidden) {
        groups.filter { POINT_NAMES.getValue(it) !in hidden }.flatMap { p ->
            w.counts.filter { it.key.point == p && it.value >= 2 }.entries.sortedByDescending { it.value }.map { it.key }
                .filter { c -> rows.any { w.together(it, c) > 0 } }.take(4)
        }.take(9)
    }
    // a mood stays only if it touches something shown
    val shownRows = rows.filter { r -> cols.any { w.together(r, it) > 0 } }
    if (rows.isEmpty() || groups.isEmpty()) return
    val k = rememberReveal(w)
    val tm = rememberTextMeasurer()
    val ink = Ink.text
    Panel {
        CardHead("MATRICA", "Sve sa svim")
        ToggleLegend(groups.map { POINT_NAMES.getValue(it) to POINT_COLORS.getValue(it) }, hidden) { hidden = it }
        if (shownRows.isEmpty() || cols.isEmpty()) Text("Uključi bar jednu tačku.", style = SmallStyle.copy(shadow = null))
        else androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val head = 92.dp; val rowH = 34.dp; val labelW = 84.dp
            val cell = (maxWidth - labelW) / cols.size
            val rows = shownRows
            Canvas(Modifier.fillMaxWidth().height(head + rowH * rows.size).pointerInput(rows) {
                detectTapGestures { pos ->
                    val i = ((pos.y - head.toPx()) / rowH.toPx()).toInt()
                    if (pos.y > head.toPx() && i in rows.indices) onOpen(rows[i])
                }
            }) {
                val top = cols.maxOf { c -> rows.maxOf { r -> w.together(r, c) } }.coerceAtLeast(1)
                for ((j, c) in cols.withIndex()) {
                    val x = labelW.toPx() + cell.toPx() * (j + 0.5f)
                    val l = tm.measure(c.label.removePrefix("da "), TextStyle(fontFamily = UiFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ink), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, constraints = androidx.compose.ui.unit.Constraints(maxWidth = 80.dp.toPx().toInt()))
                    rotate(-55f, Offset(x, head.toPx() - 14.dp.toPx())) { drawText(l, topLeft = Offset(x, head.toPx() - 14.dp.toPx() - l.size.height / 2f)) }
                    drawRoundRect(POINT_COLORS.getValue(c.point), Offset(x - 9.dp.toPx(), head.toPx() - 8.dp.toPx()), Size(18.dp.toPx(), 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
                for ((i, r) in rows.withIndex()) {
                    val y = head.toPx() + rowH.toPx() * (i + 0.5f)
                    drawCircle(MOOD_COLORS[r.label] ?: POINT_COLORS.getValue(r.point), 5.dp.toPx(), Offset(6.dp.toPx(), y))
                    val l = tm.measure(r.label, TextStyle(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink), maxLines = 1)
                    drawText(l, topLeft = Offset(16.dp.toPx(), y - l.size.height / 2f))
                    for ((j, c) in cols.withIndex()) {
                        val x = labelW.toPx() + cell.toPx() * (j + 0.5f)
                        val v = w.together(r, c)
                        if (v == 0) { drawCircle(ink.copy(alpha = 0.16f), 1.5.dp.toPx(), Offset(x, y)); continue }
                        val linked = v >= w.threshold
                        val rr = (2.5f + 9f * sqrt(v.toFloat() / top)).dp.toPx() * k
                        drawCircle(POINT_COLORS.getValue(c.point).copy(alpha = if (linked) 1f else 0.4f), rr, Offset(x, y))
                        if (linked && rr > 8.dp.toPx()) {
                            val n = tm.measure("$v", TextStyle(fontFamily = UiFont, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Ink.ink))
                            drawText(n, topLeft = Offset(x - n.size.width / 2f, y - n.size.height / 2f), alpha = k)
                        }
                    }
                }
            }
        }
        Text("Pune tačke su se ponovile bar ${w.threshold} puta; blijede tek jednom. Dodirni raspoloženje da vidiš šta dolazi s njim.",
            style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
    }
}

@Composable
private fun BodyCard(d: StatsData) {
    Panel {
        CardHead("TIJELO", "Gdje nosiš napetost")
        if (d.tension.isEmpty()) Text("Kad uradiš skeniranje tijela, ili u bilješci označiš tačku tijelo, ovdje se vidi gdje najčešće nosiš napetost.",
            style = SmallStyle.copy(shadow = null))
        else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BodyMap(d.tension, Modifier.width(104.dp).height(220.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val ranked = d.tension.entries.sortedByDescending { it.value }
                    val top = ranked.first().value.coerceAtLeast(1)
                    for ((id, n) in ranked.take(5)) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(bodyRegion(id)?.name ?: id, style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, shadow = null), modifier = Modifier.weight(1f))
                            Text("$n×", style = SmallStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
                        }
                        val k = rememberReveal(id)
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ink.text.copy(alpha = 0.1f))) {
                            Box(Modifier.fillMaxWidth(n.toFloat() / top * k).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(0xFFEC8B74)))
                        }
                    }
                }
            }
            val ranked = d.tension.entries.sortedByDescending { it.value }
            // "most often" only when one place leads, and has been named more than once
            if (ranked.first().value >= 2 && ranked.getOrNull(1)?.value != ranked.first().value)
                bodyRegion(ranked.first().key)?.let { Text("Najčešće je nosiš ${it.where}.", style = ReadStyle) }
            Text(if (d.scans > 0) "Iz skeniranja tijela i iz bilješki s tačkom tijelo." else "Iz bilješki s tačkom tijelo. Skeniranje tijela dodaje još.",
                style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
        }
    }
}

/** Where the mind was: past, here, future as three suns as big as their share; then the habits that came back. */
@Composable
private fun MindCard(d: StatsData) {
    Panel {
        CardHead("UM", "Gdje je bio um")
        if (d.times.isEmpty() && d.habits.isEmpty() && d.wander.isEmpty()) {
            Text("Kad zapisuješ misli, ovdje se pokaže šta se u njima ponavlja: „moram“, „šta ako“, „uvijek“ i „nikad“, i da li je um više u prošlosti ili u budućnosti.",
                style = SmallStyle.copy(shadow = null))
            return@Panel
        }
        if (d.times.isNotEmpty()) {
            val pct = percents(MindHabits.Time.entries.map { d.times[it] ?: 0 })
            val colors = listOf(POINT_COLORS.getValue(FourPointsReader.THOUGHTS), POINT_COLORS.getValue(FourPointsReader.BODY), Ink.accent)
            val k = rememberReveal(d.times)
            val tm = rememberTextMeasurer()
            val ink = Ink.text
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width
                val maxP = pct.maxOrNull()?.coerceAtLeast(1) ?: 1
                for ((i, t) in MindHabits.Time.entries.withIndex()) {
                    val x = w * (0.17f + 0.33f * i)
                    val r = 46.dp.toPx() * sqrt(pct[i].toFloat() / maxP) * k
                    if (pct[i] > 0) {
                        drawCircle(colors[i].copy(alpha = 0.28f), r + 6.dp.toPx(), Offset(x, 58.dp.toPx()))
                        drawCircle(colors[i], r, Offset(x, 58.dp.toPx()))
                    }
                    val l = tm.measure("${pct[i]}%", TextStyle(fontFamily = UiFont, fontSize = if (pct[i] >= 30) 15.sp else 13.sp, fontWeight = FontWeight.ExtraBold, color = Ink.ink))
                    if (pct[i] > 0) drawText(l, topLeft = Offset(x - l.size.width / 2f, 58.dp.toPx() - l.size.height / 2f), alpha = k)
                    val n = tm.measure(t.label, TextStyle(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink))
                    drawText(n, topLeft = Offset(x - n.size.width / 2f, 118.dp.toPx()))
                }
            }
            val ranked = d.times.entries.sortedByDescending { it.value }
            // a sentence only when one time clearly leads, over at least three notes
            if (d.times.values.sum() >= 3 && ranked.getOrNull(1)?.value != ranked[0].value)
                Text("Najčešće ${ranked[0].key.label}: ${ranked[0].key.about}.", style = ReadStyle)
        }
        if (d.habits.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, n) in d.habits) MindHabits.habit(id)?.let { h ->
                Text("${h.name} · $n×", style = SmallStyle.copy(fontWeight = FontWeight.Bold, color = Ink.text, shadow = null),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(POINT_COLORS.getValue(FourPointsReader.THOUGHTS).copy(alpha = if (Ink.day) 0.3f else 0.2f))
                        .padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        if (d.wander.isNotEmpty()) Text("U „Oblacima“ um je najčešće odlazio u: " + d.wander.take(3).joinToString(", ") { "${it.first} (${it.second})" } + ".",
            style = SmallStyle.copy(shadow = null))
        Text("Ovo nisu greške, nego navike svakog uma. Kad ih primijetiš, manje te vode.", style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
    }
}

@Composable
private fun ThemesCard(d: StatsData) {
    Panel {
        CardHead("TEME", "Šta se vraća")
        val top = d.themes.first().second.coerceAtLeast(1)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((t, n) in d.themes) {
                // more frequent themes stand out a little more
                val k = n.toFloat() / top
                Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Ink.accent.copy(alpha = 0.18f + 0.4f * k)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(THEME_NAMES[t] ?: t, style = BodyStyle.copy(fontSize = (13 + 4 * k).sp, fontWeight = FontWeight.SemiBold, color = if (Ink.day) Ink.ink else Color.White, shadow = null))
                    Text("  $n", style = SmallStyle.copy(shadow = null))
                }
            }
        }
    }
}

/** Two small tiles: the waves ridden, and the calls answered. */
@Composable
private fun Tiles(d: StatsData) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d.waves.isNotEmpty()) {
            val all = d.waves.sumOf { it.count }
            val before = d.waves.sumOf { (it.before * it.count).toDouble() } / all
            val after = d.waves.sumOf { (it.after * it.count).toDouble() } / all
            Tile("TALAS", NeboIcon.Wave, "%.0f → %.0f".format(before, after), "jačina poriva prije i poslije, $all ${plural(all, "talas", "talasa", "talasa")}", Modifier.weight(1f))
        }
        Tile("POZIVI", NeboIcon.Bell, if (d.promptsFired == 0) "—" else "${d.promptsAnswered} od ${d.promptsFired}",
            if (d.promptsFired == 0) "pozivi su isključeni ili još nijesu stigli" else "poziva s odgovorom; propušten je samo propušten", Modifier.weight(1f))
    }
}

@Composable
private fun Tile(kicker: String, icon: NeboIcon, big: String, small: String, modifier: Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Column(modifier.lift(shape).clip(shape).background(Ink.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, Ink.accentInk, 16.dp)
            Text(kicker, style = KickerStyle.copy(fontSize = 11.sp))
        }
        Text(big, style = NumberStyle.copy(fontSize = 28.sp))
        Text(small, style = SmallStyle.copy(fontSize = 12.5.sp, shadow = null))
    }
}
