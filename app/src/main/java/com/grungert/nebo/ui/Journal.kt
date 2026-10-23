package com.grungert.nebo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.grungert.nebo.esm.Esm
import com.grungert.nebo.koan.Links
import com.grungert.nebo.koan.Thing
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.grungert.nebo.koan.MindHabits
import com.grungert.nebo.koan.SearchWords
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.app
import com.grungert.nebo.audio.NotePlayer
import com.grungert.nebo.data.Col
import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.Export
import com.grungert.nebo.data.KIND_LABEL
import com.grungert.nebo.data.Session
import com.grungert.nebo.data.TYPE_LABEL
import com.grungert.nebo.koan.Lexicon
import com.grungert.nebo.koan.MOODS
import com.grungert.nebo.koan.FourPointsReader
import com.grungert.nebo.koan.THEME_NAMES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dayFmt = SimpleDateFormat("EEEE, d. MMMM", Locale.forLanguageTag("sr-Latn-ME"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale.ROOT)
private val monthFmt = SimpleDateFormat("LLLL yyyy", Locale.forLanguageTag("sr-Latn-ME"))

/** The weekday names in our ijekavian: the system's Serbian locale says "ponedeljak" and "sreda". */
internal fun ijekavian(s: String) = s.replace("onedeljak", "onedjeljak").replace("Sreda", "Srijeda").replace("sreda", "srijeda")

/** "Danas", "Juče", or the weekday and date. */
internal fun dayLabel(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(c, today)) return "Danas"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(c, today)) return "Juče"
    return ijekavian(dayFmt.format(Date(t))).replaceFirstChar { it.uppercase() }
}

/** The view last used, so coming back from a thread or a note returns to the same one. */
private var lastFilter = 0

/** A day in the timeline: its name, the moods it held (most frequent first), and the colour of the first. */
private class DayHead(val label: String, val moods: List<String>, val first: Boolean)
/** The sessions of one day, folded into one line that opens on a touch. */
private class SessionsOfDay(val day: String, val sessions: List<Session>)
/** A note in the timeline, and whether the rail above it should be drawn. */
private class NoteRow(val entry: Entry)

/**
 * The journal: the sky above with the week's moods, and a sheet of paper over it with three views. "Sve" is a timeline,
 * notes first and each day's sessions folded into one line; "Uvidi" holds the notes saved as insights, one of them
 * coming back each day; "Niti" shows what keeps returning. The words in a note can be touched, to see what comes with them.
 */
@Composable
fun JournalScreen(host: com.grungert.nebo.SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val transcribed by app.transcriber.done.collectAsState()
    var rows by remember { mutableStateOf<List<Any>?>(null) }
    var filter by remember { mutableIntStateOf(lastFilter.coerceIn(0, 2)) }   // 0 all, 1 insights, 2 threads
    LaunchedEffect(filter) { lastFilter = filter }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var meaning by remember { mutableStateOf<List<Pair<Entry, Float>>?>(null) }   // found by sense, not by the words
    var reload by remember { mutableIntStateOf(0) }
    val player = remember { NotePlayer() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Entry?>(null) }
    var followed by remember { mutableStateOf<Map<Long, Entry>>(emptyMap()) }   // the hard notes that next-day notes answer
    var week by remember { mutableStateOf<List<Pair<Long, String?>>>(emptyList()) }   // this week's days and each one's first mood
    var starred by remember { mutableStateOf<List<Entry>?>(null) }
    var threadOf by remember { mutableStateOf<Map<Long, com.grungert.nebo.koan.InsightThread>>(emptyMap()) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(editing) { host.sheetOpen = editing != null }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { host.sheetOpen = false; player.stop() } }
    val open: (Thing) -> Unit = { t -> player.stop(); go(Screen.Focus(t.point, t.label)) }

    LaunchedEffect(transcribed, query, reload) {
        rows = withContext(Dispatchers.IO) {
            val q = query.trim().lowercase()
            val all = app.db.entries()
            val entries = all.filter { e -> q.isEmpty() || byWords(e, q) }
            // sessions under half a minute were only a start; they stay out of the timeline
            val sessions = if (q.isNotEmpty()) emptyList() else app.db.sessions().filter { it.actualSec >= 30 }
            followed = entries.mapNotNull { it.follows }.mapNotNull { app.db.entry(it) }.associateBy { it.id }
            starred = all.filter { it.starred }.sortedByDescending { it.createdAt }
            // the week strip: Monday to Sunday, each day with the colour of its most frequent mood
            val monday = weekStart()
            week = (0 until 7).map { i ->
                val from = monday + i * 86_400_000L
                from to all.filter { it.createdAt in from until from + 86_400_000L }.flatMap { it.moods }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            }
            val out = ArrayList<Any>()
            val byDay = (sessions.map { it.startedAt to (it as Any) } + entries.map { it.createdAt to (it as Any) }).sortedByDescending { it.first }
                .groupBy { dayLabel(it.first) }
            var first = true
            for ((label, items) in byDay) {
                val moods = items.mapNotNull { it.second as? Entry }.flatMap { it.moods }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
                out += DayHead(label, moods, first); first = false
                val ss = items.mapNotNull { it.second as? Session }
                var sessionsPlaced = false
                for ((t, item) in items) when (item) {
                    is Entry -> out += NoteRow(item)
                    is Session -> if (!sessionsPlaced) { out += SessionsOfDay(label, ss); sessionsPlaced = true }
                }
            }
            out
        }
    }
    // the threads, to say which one a saved insight belongs to
    LaunchedEffect(filter, reload) {
        if (filter != 1) return@LaunchedEffect
        val all = withContext(Dispatchers.IO) { app.db.entries() }
        threadOf = app.recommender.threads(all).flatMap { th -> th.entries.map { it.id to th } }.toMap()
    }

    // the same search by meaning, a moment after typing stops: notes close in sense that do not use the words
    LaunchedEffect(query, reload) {
        meaning = null
        val q = query.trim()
        if (q.length < 3 || !app.recommender.usesMeaning) { meaning = emptyList(); return@LaunchedEffect }
        delay(450)
        val all = withContext(Dispatchers.IO) { app.db.entries() }
        meaning = app.recommender.searchByMeaning(q, all, all.filter { byWords(it, q.lowercase()) }.map { it.id }.toSet())
    }

    fun toggle(e: Entry) {
        scope.launch { withContext(Dispatchers.IO) { app.db.updateEntry(e.copy(starred = !e.starred), Col.STARRED) }; reload++ }
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(Modifier.fillMaxSize()) {
        // the sky stays visible above the sheet, with just enough shade for the white title
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x4D14326E), 0.3f to Color(0x0D14326E))))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(monthFmt.format(Date()).uppercase(), style = LabelStyle.copy(color = Ink.onSky.copy(alpha = 0.9f), shadow = skyShadow, letterSpacing = 1.8.sp))
                    Text("Dnevnik", style = TitleStyle.copy(color = Ink.onSky, shadow = skyShadow, fontSize = 38.sp, lineHeight = 42.sp))
                }
                SkyAction(NeboIcon.Search, if (searching) "Zatvori pretragu" else "Traži") { searching = !searching; if (!searching) query = "" }
                SkyAction(NeboIcon.Stars, "Sazviježđa") { constellationsMode = 1; go(Screen.Constellations()) }
                SkyAction(NeboIcon.Share, "Izvezi") {
                    scope.launch { Export.share(context, app.db, app.library, app.recommender.threads(withContext(Dispatchers.IO) { app.db.entries() })) }
                }
            }
            WeekStrip(week, Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) { day ->
                // a touch on a day goes to it in the timeline
                filter = 0
                val i = rows?.indexOfFirst { it is DayHead && it.label == dayLabel(day) } ?: -1
                if (i >= 0) scope.launch { list.animateScrollToItem(i) }
            }
            AnimatedVisibility(shown, Modifier.weight(1f), enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) { it / 3 } + fadeIn()) {
                val sheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
                Column(Modifier.fillMaxSize().clip(sheetShape).background(if (Ink.day) Color(0xEBFAF7F0) else Color(0xE60E1834))) {
                    Box(Modifier.padding(top = 10.dp).size(40.dp, 5.dp).clip(CircleShape).background(Ink.text.copy(alpha = 0.18f)).align(Alignment.CenterHorizontally))
                    AnimatedVisibility(searching) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp).clip(RoundedCornerShape(18.dp)).background(Ink.surfaceHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(NeboIcon.Search, Ink.soft, 18.dp)
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text(if (app.recommender.usesMeaning) "Riječ ili kratka rečenica" else "Traži po riječima ili raspoloženju",
                                    style = BodyStyle.copy(color = Ink.faint, shadow = null))
                                BasicTextField(query, { query = it; filter = 0 }, singleLine = true, textStyle = BodyStyle.copy(shadow = null),
                                    cursorBrush = SolidColor(Ink.accentInk), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    PillTabs(listOf("Sve", "Uvidi", "Niti"), filter, { filter = it }, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                    when (filter) {
                        2 -> Box(Modifier.padding(horizontal = 16.dp)) { ThreadsList(go) }
                        1 -> InsightsList(starred, threadOf, player, open, { toggle(it) }, { player.stop(); editing = it }, go)
                        else -> {
                            val r = rows
                            val m = meaning
                            val bySense = query.trim().length >= 3 && app.recommender.usesMeaning
                            if (r != null && r.isEmpty() && (query.isBlank() || !bySense || m?.isEmpty() == true)) Text(
                                if (query.isNotBlank()) "Ništa ne odgovara pretrazi." else "Ovdje će se pojaviti tvoje sesije i zapažanja.",
                                style = BodyStyle.copy(color = Ink.soft, shadow = null), modifier = Modifier.padding(24.dp))
                            LazyColumn(state = list, contentPadding = PaddingValues(start = 10.dp, end = 18.dp, top = 6.dp, bottom = 140.dp)) {
                                items(r ?: emptyList(), key = { row ->
                                    when (row) { is DayHead -> "d" + row.label; is SessionsOfDay -> "s" + row.day; is NoteRow -> "n" + row.entry.id; else -> row.hashCode() }
                                }) { row ->
                                    Box(Modifier.animateItem()) {
                                        when (row) {
                                            is DayHead -> TimelineDay(row)
                                            is SessionsOfDay -> TimelineSessions(row)
                                            is NoteRow -> TimelineRow(timeFmt.format(Date(row.entry.createdAt)), 10.dp, row.entry.moods.firstOrNull()?.let { MOOD_COLORS[it] } ?: Ink.faint) {
                                                EntryCard(row.entry, player, onStar = { toggle(row.entry) }, onSimilar = { go(Screen.Similar(row.entry.id)) },
                                                    onEdit = { player.stop(); editing = row.entry }, followed = row.entry.follows?.let { followed[it] }, onThing = open)
                                            }
                                        }
                                    }
                                }
                                if (query.isNotBlank() && bySense) {
                                    if (m == null) item { Text("Tražim i po značenju…", style = SmallStyle.copy(shadow = null), modifier = Modifier.padding(start = 16.dp, top = 14.dp)) }
                                    else if (m.isNotEmpty()) {
                                        item {
                                            Column(Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)) {
                                                Text("Po značenju", style = HeadStyle.copy(fontSize = 18.sp, shadow = null))
                                                Text("Bilješke bliske po smislu, i bez tih riječi.", style = SmallStyle.copy(shadow = null))
                                            }
                                        }
                                        items(m, key = { "m" + it.first.id }) { (e, z) ->
                                            Box(Modifier.padding(start = 8.dp, bottom = 12.dp)) {
                                                EntryCard(e, player, onStar = { toggle(e) }, onSimilar = { go(Screen.Similar(e.id)) }, onEdit = { player.stop(); editing = e },
                                                    footer = dayLabel(e.createdAt) + " · " + when { z >= 2.3f -> "vrlo blisko"; z >= 1.9f -> "blisko"; else -> "slično" }, onThing = open)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        editing?.let { e ->
            EditEntry(e, onClose = { editing = null }, onSave = { changed, cols ->
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) { app.db.updateEntry(changed, *cols) }
                    reload++
                    app.recommender.tagNotes()
                    reload++
                }
            }, onDelete = {
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        app.db.deleteEntry(e.id)
                        e.audioPath?.let { java.io.File(it).delete() }
                    }
                    reload++
                }
            })
        }
    }
}

/** A round action that sits right on the sky: frosted white, a white icon. */
@Composable
private fun SkyAction(icon: NeboIcon, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).tap(label, shape = CircleShape, onClick = onClick).background(Color(0x33FFFFFF), CircleShape)
            .border(1.dp, Color(0x66FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, Color.White, 21.dp) }
}

private val WEEKDAYS = listOf("P", "U", "S", "Č", "P", "S", "N")

/** Monday to Sunday: a ring in the colour of each day's mood, today filled in. The rings come out one after the other. */
@Composable
private fun WeekStrip(days: List<Pair<Long, String?>>, modifier: Modifier = Modifier, onDay: (Long) -> Unit) {
    val today = Esm.midnight().timeInMillis
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, (day, mood) ->
            val k = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(day) { delay(60L * i); k.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            val isToday = day == today
            val future = day > today
            val ring = mood?.let { MOOD_COLORS[it] } ?: Color.White.copy(alpha = if (future) 0.25f else 0.4f)
            Column(
                Modifier.weight(1f).tap(dayLabel(day), shape = RoundedCornerShape(16.dp), enabled = !future) { onDay(day) }.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(WEEKDAYS[i], style = LabelStyle.copy(color = Color.White.copy(alpha = 0.88f), shadow = skyShadow))
                Box(
                    Modifier.size(40.dp).graphicsLayer { scaleX = k.value; scaleY = k.value }.clip(CircleShape)
                        .background(if (isToday) Color.White else if (future) Color.Transparent else Color(0x38FFFFFF))
                        .border(3.dp, ring, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_MONTH).toString(),
                        style = SmallStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (isToday) Ink.ink else Color.White, shadow = null))
                }
            }
        }
    }
}

private val GAP = 4.dp

/**
 * One row of the timeline: the time on the left, the rail with its dot, and the content. The rail stops a few pixels
 * short of the dot on both sides, so each dot sits in a little clear space.
 */
@Composable
private fun TimelineRow(time: String, dot: androidx.compose.ui.unit.Dp, color: Color, first: Boolean = false, center: androidx.compose.ui.unit.Dp = 20.dp,
    bottom: androidx.compose.ui.unit.Dp = 14.dp, content: @Composable () -> Unit) {
    val line = Ink.text.copy(alpha = 0.16f)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(time, style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = null, fontFeatureSettings = "tnum"), textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp).padding(top = 13.dp))
        Canvas(Modifier.width(26.dp).fillMaxHeight()) {
            val x = size.width / 2
            val cy = center.toPx()
            val r = dot.toPx() / 2
            val w = 2.dp.toPx()
            if (!first) drawLine(line, Offset(x, 0f), Offset(x, cy - r - GAP.toPx()), w, StrokeCap.Round)
            drawCircle(color, r, Offset(x, cy))
            drawLine(line, Offset(x, cy + r + GAP.toPx()), Offset(x, size.height), w, StrokeCap.Round)
        }
        Box(Modifier.weight(1f).padding(start = 4.dp, bottom = bottom)) { content() }
    }
}

@Composable
private fun TimelineDay(d: DayHead) {
    val color = d.moods.firstOrNull()?.let { MOOD_COLORS[it] } ?: Ink.faint
    TimelineRow("", 12.dp, color, first = d.first, center = 18.dp, bottom = 10.dp) {
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(d.label, style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, shadow = null))
            if (d.moods.isNotEmpty()) Text(d.moods.take(2).joinToString(", "), style = SmallStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
        }
    }
}

/** A day's sessions as one quiet line, "2 sesije · 15 min"; a touch opens them one by one. */
@Composable
private fun TimelineSessions(s: SessionsOfDay) {
    var open by remember { mutableStateOf(false) }
    val total = s.sessions.sumOf { it.actualSec }
    val first = s.sessions.first()
    TimelineRow(timeFmt.format(Date(first.startedAt)), 8.dp, Ink.soft, center = 19.dp, bottom = 10.dp) {
        Column(Modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)).clip(RoundedCornerShape(18.dp)).background(Ink.text.copy(alpha = 0.06f))) {
            Row(
                Modifier.fillMaxWidth().tap(if (open) "Sakrij sesije" else "Prikaži sesije", shape = RoundedCornerShape(18.dp)) { open = !open }.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(SESSION_TYPES.firstOrNull { it.id == first.type }?.icon ?: NeboIcon.Breath, Ink.text, 19.dp)
                Text(if (s.sessions.size == 1) (TYPE_LABEL[first.type] ?: first.type) else "${s.sessions.size} ${plural(s.sessions.size, "sesija", "sesije", "sesija")}",
                    style = BodyStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold, shadow = null), modifier = Modifier.weight(1f))
                Text(if (total < 60) "$total s" else "${total / 60} min", style = SmallStyle.copy(fontWeight = FontWeight.SemiBold, shadow = null))
                if (s.sessions.size > 1) {
                    val turn by animateFloatAsState(if (open) 90f else 0f, label = "caret")
                    Icon(NeboIcon.Forward, Ink.soft, 14.dp, Modifier.graphicsLayer { rotationZ = turn })
                }
            }
            if (open && s.sessions.size > 1) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp)) {
                for (row in s.sessions) SessionLine(row)
            }
        }
    }
}

@Composable
private fun SessionLine(row: Session) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(SESSION_TYPES.firstOrNull { it.id == row.type }?.icon ?: NeboIcon.Breath, Ink.soft, 16.dp)
        Column(Modifier.weight(1f)) {
            Text((TYPE_LABEL[row.type] ?: row.type) + (if (row.thoughts > 0) " · oblaka ${row.thoughts}" else ""), style = BodyStyle.copy(fontSize = 14.sp, shadow = null))
            if (row.urge != null) Text("poriv: " + row.urge.removePrefix("da ") + (row.urgeBefore?.let { " · $it" } ?: "") + (row.urgeAfter?.let { " → $it" } ?: ""),
                style = SmallStyle.copy(color = Ink.accentInk, shadow = null))
        }
        Text((if (row.actualSec < 60) "${row.actualSec} s" else "${row.actualSec / 60} min") + " · " + timeFmt.format(Date(row.startedAt)), style = SmallStyle.copy(shadow = null))
    }
}

/**
 * The saved insights: one older insight comes back each day on top ("Vraća ti se"), then all of them by month,
 * each with the thread it belongs to.
 */
@Composable
private fun InsightsList(starred: List<Entry>?, threadOf: Map<Long, com.grungert.nebo.koan.InsightThread>, player: NotePlayer, open: (Thing) -> Unit,
    onStar: (Entry) -> Unit, onEdit: (Entry) -> Unit, go: (Screen) -> Unit) {
    val s = starred ?: return
    if (s.isEmpty()) {
        Text("Označi bilješku koja ti je nešto otvorila, oznakom gore desno. Ovdje će se skupljati, a starije će ti se povremeno vraćati.",
            style = BodyStyle.copy(color = Ink.soft, shadow = null), modifier = Modifier.padding(24.dp))
        return
    }
    // one from at least two weeks ago, a different one each day
    val old = s.filter { it.createdAt < System.currentTimeMillis() - 14 * 86_400_000L }
    val back = old.getOrNull((Esm.today() % old.size.coerceAtLeast(1)).toInt())
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("${s.size} ${plural(s.size, "uvid", "uvida", "uvida")}", style = KickerStyle)
        }
        if (back != null) item(key = "back") {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(Ink.accent.copy(alpha = if (Ink.day) 0.42f else 0.12f)).border(1.dp, Ink.accent.copy(alpha = 0.35f), shape).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("VRAĆA TI SE · " + ago(back.createdAt).uppercase(), style = KickerStyle)
                Text(back.words, style = ReadStyle.copy(fontSize = 20.sp, lineHeight = 28.sp), maxLines = 6)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(back.createdAt), style = SmallStyle.copy(fontWeight = FontWeight.Bold, shadow = null), modifier = Modifier.weight(1f))
                    if (back.audioPath != null) Row(
                        Modifier.tap("Preslušaj", shape = RoundedCornerShape(16.dp)) { player.play(back.audioPath) {} }.background(if (Ink.day) Ink.ink else Ink.accent, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(NeboIcon.Play, if (Ink.day) Ink.accent else Ink.ink, 14.dp, filled = true)
                        Text("Preslušaj", style = SmallStyle.copy(fontWeight = FontWeight.Bold, color = if (Ink.day) Ink.accent else Ink.ink, shadow = null))
                    }
                }
            }
        }
        var lastMonth = ""
        for (e in s) {
            val month = monthFmt.format(Date(e.createdAt)).substringBefore(' ').uppercase()
            if (month != lastMonth) { lastMonth = month; item(key = "m$month${e.id}") { Text(month, style = LabelStyle.copy(shadow = null), modifier = Modifier.padding(top = 6.dp)) } }
            item(key = e.id) {
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EntryCard(e, player, onStar = { onStar(e) }, onSimilar = { go(Screen.Similar(e.id)) }, onEdit = { onEdit(e) }, onThing = open,
                        footer = dayLabel(e.createdAt))
                    threadOf[e.id]?.let { th ->
                        Text("nit · ${th.title}", style = SmallStyle.copy(fontWeight = FontWeight.Bold, color = Ink.text, shadow = null),
                            modifier = Modifier.tap("Otvori nit", shape = RoundedCornerShape(12.dp)) { go(Screen.Thread(th.entries.map { it.id }, th.title)) }
                                .background(Ink.text.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
        }
    }
}

/** "prije 3 sedmice", "prije mjesec dana". */
private fun ago(t: Long): String {
    val days = ((System.currentTimeMillis() - t) / 86_400_000L).toInt()
    return when {
        days < 21 -> "prije ${days / 7} ${plural(days / 7, "sedmicu", "sedmice", "sedmica")}"
        days < 45 -> "prije mjesec dana"
        days < 330 -> "prije ${days / 30} ${plural(days / 30, "mjesec", "mjeseca", "mjeseci")}"
        else -> "prije godinu dana"
    }
}

/** A note found by the words of a search, in any of their forms, or by a mood. */
private fun byWords(e: Entry, q: String): Boolean {
    val stems = SearchWords.stems(q)
    // a mood matches from its start: "mir" finds "mirno", never "nemirno"
    val m = q.trim().lowercase()
    return (if (stems.isEmpty()) e.words.lowercase().contains(q) else SearchWords.matches(e.words, stems)) || (m.length >= 3 && e.moods.any { it.startsWith(m) })
}

/**
 * Change a note's words and moods, or remove it together with its recording.
 * New words clear the themes, so the note is read again for what it is about.
 */
@Composable
private fun EditEntry(e: Entry, onClose: () -> Unit, onSave: (Entry, Array<Col>) -> Unit, onDelete: () -> Unit) {
    var text by remember { mutableStateOf(e.words) }
    val moods = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(e.moods) } }
    val points = remember { FourPointsState(e.points) }
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = null, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Ink.sheet)
                .clickable(interactionSource = null, indication = null) {}.navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (confirmDelete) {
                Text("Obrisati ovu bilješku?", style = HeadStyle.copy(shadow = null))
                Text(if (e.audioPath != null) "Briše se i tekst i snimak. Ovo se ne može vratiti." else "Ovo se ne može vratiti.", style = BodyStyle.copy(color = Ink.soft, shadow = null))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("Ne", { confirmDelete = false }, Modifier.weight(1f))
                    GlassButton("Obriši", onDelete, Modifier.weight(1f), strong = true)
                }
                return@Column
            }
            Text("Uredi bilješku", style = HeadStyle.copy(shadow = null))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.surface).padding(16.dp)) {
                if (text.isEmpty()) Text("Šta je bilo u tebi…", style = BodyStyle.copy(color = Ink.faint, shadow = null))
                BasicTextField(text, { text = it }, textStyle = ReadStyle.copy(fontSize = 18.sp, lineHeight = 26.sp, color = Ink.text),
                    cursorBrush = SolidColor(Ink.accentInk), modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp))
            }
            FourPointsCard(points, moods)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallAction(NeboIcon.Trash, "Obriši") { confirmDelete = true }
                Spacer(Modifier.weight(1f))
                GlassButton("Otkaži", onClose)
                GlassButton("Sačuvaj", {
                    val words = text.trim()
                    val changedWords = words != e.words.trim()
                    val base = arrayOf(Col.MOODS, Col.THEMES, Col.POINTS)
                    val m = moods.toList()
                    val (changed, cols) = when {
                        // themes are left alone unless the moods changed; then they are found again
                        !changedWords -> e.copy(moods = m, themes = emptyList()) to (if (m == e.moods) arrayOf(Col.MOODS, Col.POINTS) else base)
                        // corrected words of a recording become its transcript, as on the check-in screen
                        e.audioPath != null && e.transcript != null ->
                            e.copy(text = null, transcript = words, moods = m, themes = emptyList()) to base + Col.TEXT + Col.TRANSCRIPT
                        // a recording still waiting to be heard keeps waiting: the typed words go beside it
                        else -> e.copy(text = words.ifBlank { null }, moods = m, themes = emptyList()) to base + Col.TEXT
                    }
                    onSave(changed.copy(points = points.toPoints()), cols)
                }, strong = true)
            }
        }
    }
}

@Composable
private fun SmallAction(icon: NeboIcon, label: String, onClick: () -> Unit) {
    Row(
        Modifier.tap(label, shape = RoundedCornerShape(12.dp), onClick = onClick).background(Ink.text.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, Ink.text, 15.dp)
        Text(label, style = SmallStyle.copy(fontWeight = FontWeight.SemiBold, color = Ink.text, shadow = null))
    }
}

/** The bookmark that saves a note as an insight: soft green when saved, and it pops a little when touched. */
@Composable
private fun InsightMark(on: Boolean, onClick: () -> Unit) {
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()
    val bg by animateColorAsState(if (on) Ink.insightBg else Color.Transparent, label = "mark")
    Box(
        Modifier.size(34.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .tap(if (on) "Ukloni iz uvida" else "Sačuvaj kao uvid", shape = CircleShape) {
                onClick()
                scope.launch { pop.snapTo(0.7f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) }
            }.background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(NeboIcon.Bookmark, if (on) Ink.insight else Ink.faint, 17.dp, filled = on) }
}

/** The things of a note as small chips in their points' colours; each opens what tends to come with it. */
@Composable
private fun ThingChips(e: Entry, onThing: (Thing) -> Unit) {
    val things = remember(e) { Links.things(e).filter { it.point != FourPointsReader.EMOTIONS } }
    if (things.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in things.take(6)) {
            val c = POINT_COLORS.getValue(t.point)
            Text(t.label, style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (Ink.day) Ink.ink.copy(alpha = 0.85f) else lerp(c, Color.White, 0.45f), shadow = null),
                modifier = Modifier.tap(t.label, shape = RoundedCornerShape(12.dp)) { onThing(t) }.background(c.copy(alpha = if (Ink.day) 0.35f else 0.22f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

/** One note on its card: when and what kind, the bookmark, the words in the reading face, its things to touch, small actions. */
@Composable
internal fun EntryCard(row: Entry, player: NotePlayer, onStar: () -> Unit, onSimilar: (() -> Unit)?, footer: String? = null, onEdit: (() -> Unit)? = null,
    followed: Entry? = null, onThing: ((Thing) -> Unit)? = null) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().lift(shape, 6.dp).clip(shape).background(Ink.card).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((KIND_LABEL[row.kind] ?: row.kind) + (if (row.moods.isNotEmpty()) " · " + row.moods.joinToString(", ") else ""),
                style = SmallStyle.copy(fontWeight = FontWeight.Bold, shadow = null), modifier = Modifier.weight(1f))
            InsightMark(row.starred, onStar)
        }
        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // a note from the day after a hard one carries that one with it: yesterday, then today
            followed?.let { y ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.text.copy(alpha = 0.05f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Juče" + (if (y.moods.isNotEmpty()) " · " + y.moods.joinToString(", ") else ""), style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
                    if (y.words.isNotBlank()) Text("„" + y.words.take(140) + (if (y.words.length > 140) "…" else "") + "“", style = SmallStyle.copy(color = Ink.text, shadow = null))
                }
            }
            val words = row.words
            when {
                words.isNotBlank() -> Text(words, style = ReadStyle.copy(fontStyle = if (row.kind == "rain") FontStyle.Italic else FontStyle.Normal))
                row.audioPath != null && row.transcript == null -> Text("Snimak čeka da postane tekst.", style = SmallStyle.copy(shadow = null))
            }
            if (onThing != null) ThingChips(row, onThing)
            if (footer != null) Text(footer, style = SmallStyle.copy(color = Ink.accentInk, fontWeight = FontWeight.SemiBold, shadow = null))
            if (row.audioPath != null || (onSimilar != null && words.isNotBlank()) || onEdit != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.audioPath != null) SmallAction(NeboIcon.Play, "Preslušaj") { player.play(row.audioPath) {} }
                if (onSimilar != null && words.isNotBlank()) SmallAction(NeboIcon.Similar, "Slične") { onSimilar() }
                if (onEdit != null) SmallAction(NeboIcon.Pen, "Uredi") { onEdit() }
            }
        }
    }
}

/** "When did I feel like this before?": earlier notes closest in meaning to one note. */
@Composable
fun SimilarScreen(sc: Screen.Similar, go: (Screen) -> Unit) {
    val app = LocalContext.current.app
    val player = remember { NotePlayer() }
    var target by remember { mutableStateOf<Entry?>(null) }
    var result by remember { mutableStateOf<List<Pair<Entry, Float>>?>(null) }
    LaunchedEffect(sc.entryId) {
        val (t, all) = withContext(Dispatchers.IO) { app.db.entry(sc.entryId) to app.db.entries() }
        target = t
        result = if (t == null) emptyList() else app.recommender.similar(t, all, 5)
    }
    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Slično ranije", { player.stop(); go(sc.back) })
            target?.let { t ->
                PaperCard {
                    Text(ijekavian(dayFmt.format(Date(t.createdAt))).replaceFirstChar { it.uppercase() } + " · " + timeFmt.format(Date(t.createdAt)), style = PaperSmall)
                    Text(t.words, style = PaperText)
                }
            }
            val r = result
            when {
                r == null -> Text("Tražim po značenju…", style = BodyStyle)
                r.isEmpty() -> Text("Nijedna ranija bilješka nije baš bliska ovoj. Kako budeš zapisivao, ovdje će se pokazivati ranija slična stanja.", style = BodyStyle)
                else -> {
                    Text(if (app.recommender.usesMeaning) "Najbliže po značenju, od najsličnijeg:" else "Bilješke s istim temama ili raspoloženjem:", style = SmallStyle)
                    for ((e, score) in r) EntryCard(e, player, onStar = {}, onSimilar = null,
                        footer = ijekavian(dayFmt.format(Date(e.createdAt))).replaceFirstChar { it.uppercase() } + " · " + app.recommender.closeness(score))
                }
            }
        }
    }
}

/** Whole percentages that add up to 100: each part rounded down, the points left over going to the largest remainders. */
fun percents(counts: List<Int>): List<Int> {
    val total = counts.sum()
    if (total == 0) return counts.map { 0 }
    val exact = counts.map { it * 100.0 / total }
    val out = exact.map { it.toInt() }.toMutableList()
    exact.indices.sortedByDescending { exact[it] - out[it] }.take(100 - out.sum()).forEach { out[it]++ }
    return out
}

/** Where tension was named: each scan counts a part once, each note once for each part its body point names. */
fun tensionCounts(sessions: List<Session>, entries: List<Entry>): Map<String, Int> {
    val c = HashMap<String, Int>()
    for (s in sessions) if (s.type == "bodyscan") s.marks.map { it.region }.distinct().forEach { c[it] = (c[it] ?: 0) + 1 }
    for (e in entries) e.points.body.mapNotNull { regionOfLabel(it) }.distinct().forEach { c[it] = (c[it] ?: 0) + 1 }
    return c
}

/** An emotion and what most often comes with it in the body and as an urge, across the notes that name them. */
class PointLink(val mood: String, val body: String?, val urge: String?, val times: Int)

/**
 * For each emotion: the body sensation and the urge that most often came with it, and how many notes held that whole
 * chain together (never just how many notes had the emotion). A body and an urge that each came twice but never in the
 * same note are not shown as one chain: the stronger of the two stands alone.
 */
fun pointLinks(entries: List<Entry>): List<PointLink> {
    val withPoints = entries.filter { it.moods.isNotEmpty() && (it.points.body.isNotEmpty() || it.points.urges.isNotEmpty()) }
    return withPoints.flatMap { e -> e.moods.map { it to e } }.groupBy({ it.first }, { it.second })
        .filter { it.value.size >= 2 }
        .mapNotNull { (mood, es) ->
            fun top(pick: (Entry) -> List<String>) = es.flatMap(pick).groupingBy { it }.eachCount().maxByOrNull { it.value }?.takeIf { it.value >= 2 }
            val b = top { it.points.body }; val u = top { it.points.urges }
            fun together(body: String?, urge: String?) = es.count { e -> (body == null || body in e.points.body) && (urge == null || urge in e.points.urges) }
            var body = b?.key; var urge = u?.key
            if (body != null && urge != null && together(body, urge) < 2) { if (b!!.value >= u!!.value) urge = null else body = null }
            if (body == null && urge == null) null else PointLink(mood, body, urge, together(body, urge))
        }
        .sortedByDescending { it.times }.take(5)
}

/** Rounded bars on one scale, the value above each; the latest one in warm light. */
@Composable
fun Bars(values: List<Float>, labels: List<String>, highlightLast: Boolean = false) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val hot = highlightLast && i == values.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v > 0) v.toInt().toString() else "", style = SmallStyle.copy(fontSize = 11.sp, fontWeight = if (hot) FontWeight.ExtraBold else FontWeight.Medium, color = if (hot) Ink.text else Ink.soft, shadow = null))
                val k = rememberReveal(values, 900 + 60 * i)
                val rest = Ink.text
                Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                    val h = (size.height * (v / max) * k).coerceAtLeast(if (v > 0) 6f else 3f)
                    drawRoundRect(if (hot) Ink.accent else rest.copy(alpha = if (v > 0) 0.16f else 0.08f),
                        topLeft = Offset(size.width * 0.15f, size.height - h), size = Size(size.width * 0.7f, h), cornerRadius = CornerRadius(size.width * 0.35f))
                }
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, style = SmallStyle.copy(fontSize = 10.sp, shadow = null), maxLines = 1)
            }
        }
    }
}
