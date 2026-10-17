package com.grungert.nebo.ui

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

/** "Danas", "Juče", or the weekday and date. */
internal fun dayLabel(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(c, today)) return "Danas"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(c, today)) return "Juče"
    return dayFmt.format(Date(t)).replaceFirstChar { it.uppercase() }
}

/** The filter last used, so coming back from a thread or a note returns to the same view. */
private var lastFilter = 0

/** The journal: a quiet feed by day, searchable, filtered to insights, notes or sessions. */
@Composable
fun JournalScreen(host: com.grungert.nebo.SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val transcribed by app.transcriber.done.collectAsState()
    var rows by remember { mutableStateOf<List<Any>?>(null) }
    var filter by remember { mutableIntStateOf(lastFilter) }   // 0 all, 1 insights, 2 notes, 3 sessions, 4 threads
    LaunchedEffect(filter) { lastFilter = filter }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var meaning by remember { mutableStateOf<List<Pair<Entry, Float>>?>(null) }   // found by sense, not by the words
    var reload by remember { mutableIntStateOf(0) }
    val player = remember { NotePlayer() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Entry?>(null) }
    LaunchedEffect(editing) { host.sheetOpen = editing != null }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { host.sheetOpen = false } }

    LaunchedEffect(transcribed, filter, query, reload) {
        rows = withContext(Dispatchers.IO) {
            val q = query.trim().lowercase()
            val entries = if (filter == 3) emptyList() else app.db.entries().filter { e ->
                (filter != 1 || e.starred) && (q.isEmpty() || byWords(e, q))
            }
            val sessions = if (filter == 1 || filter == 2 || q.isNotEmpty()) emptyList() else app.db.sessions()
            val out = ArrayList<Any>()
            var lastLabel = ""
            for ((t, item) in (sessions.map { it.startedAt to it } + entries.map { it.createdAt to it }).sortedByDescending { it.first }) {
                val label = dayLabel(t)
                if (label != lastLabel) { out += label; lastLabel = label }
                out += item
            }
            out
        }
    }

    // the same search by meaning, a moment after typing stops: notes close in sense that do not use the words
    LaunchedEffect(query, filter, reload) {
        meaning = null
        val q = query.trim()
        if (q.length < 3 || filter == 3 || !app.recommender.usesMeaning) { meaning = emptyList(); return@LaunchedEffect }
        delay(450)
        val all = withContext(Dispatchers.IO) { app.db.entries() }.filter { filter != 1 || it.starred }
        meaning = app.recommender.searchByMeaning(q, all, all.filter { byWords(it, q.lowercase()) }.map { it.id }.toSet())
    }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().screenPadding().padding(top = 8.dp)) {
            ScreenHeader("Dnevnik", null) {
                RoundAction(NeboIcon.Stars, "Nebo zapisa") { go(Screen.Constellations) }
                RoundAction(NeboIcon.Search, if (searching) "Zatvori pretragu" else "Traži") { searching = !searching; if (!searching) query = "" }
                RoundAction(NeboIcon.Share, "Izvezi") {
                    scope.launch { Export.share(context, app.db, app.library, app.recommender.threads(withContext(Dispatchers.IO) { app.db.entries() })) }
                }
            }
            AnimatedVisibility(searching) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(Ink.surfaceHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(if (app.recommender.usesMeaning) "Riječ ili kratka rečenica" else "Traži po riječima ili raspoloženju",
                            style = BodyStyle.copy(color = Ink.faint, shadow = null))
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = BodyStyle.copy(shadow = null),
                            cursorBrush = SolidColor(Ink.accent), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("Sve", "Uvidi", "Bilješke", "Sesije", "Niti"), filter, { filter = it })
            if (filter == 4) { ThreadsList(go); return@Column }
            val r = rows
            val m = meaning
            val bySense = query.trim().length >= 3 && app.recommender.usesMeaning && filter != 3
            if (r != null && r.isEmpty() && (query.isBlank() || !bySense || m?.isEmpty() == true)) Text(when {
                query.isNotBlank() -> "Ništa ne odgovara pretrazi."
                filter == 1 -> "Označi zvjezdicom bilješke koje su ti nešto otvorile. Ovdje će se skupljati."
                else -> "Ovdje će se pojaviti tvoje sesije i zapažanja."
            }, style = BodyStyle.copy(color = Ink.soft), modifier = Modifier.padding(top = 24.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 130.dp)) {
                items(r ?: emptyList()) { row ->
                    when (row) {
                        is String -> Text(row, style = HeadStyle.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 18.dp, bottom = 2.dp))
                        is Session -> SessionRow(row, app.library.byId(row.koanId)?.title)
                        is Entry -> EntryCard(row, player, onStar = {
                            scope.launch { withContext(Dispatchers.IO) { app.db.updateEntry(row.copy(starred = !row.starred)) }; reload++ }
                        }, onSimilar = { go(Screen.Similar(row.id)) }, onEdit = { player.stop(); editing = row })
                    }
                }
                if (query.isNotBlank() && bySense) {
                    if (m == null) item { Text("Tražim i po značenju…", style = SmallStyle, modifier = Modifier.padding(top = 14.dp)) }
                    else if (m.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 18.dp, bottom = 2.dp)) {
                                Text("Po značenju", style = HeadStyle.copy(fontSize = 18.sp))
                                Text("Bilješke bliske po smislu, i bez tih riječi.", style = SmallStyle)
                            }
                        }
                        items(m) { (e, z) ->
                            EntryCard(e, player, onStar = {
                                scope.launch { withContext(Dispatchers.IO) { app.db.updateEntry(e.copy(starred = !e.starred)) }; reload++ }
                            }, onSimilar = { go(Screen.Similar(e.id)) }, onEdit = { player.stop(); editing = e },
                                footer = dayLabel(e.createdAt) + " · " + when { z >= 2.3f -> "vrlo blisko"; z >= 1.9f -> "blisko"; else -> "slično" })
                        }
                    }
                }
            }
        }
        editing?.let { e ->
            EditEntry(e, onClose = { editing = null }, onSave = { changed ->
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) { app.db.updateEntry(changed) }
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

/** A note found by the words of a search, in any of their forms, or by a mood. */
private fun byWords(e: Entry, q: String): Boolean {
    val stems = SearchWords.stems(q)
    return (if (stems.isEmpty()) e.words.lowercase().contains(q) else SearchWords.matches(e.words, stems)) || e.moods.any { it.contains(q) }
}

/**
 * Change a note's words and moods, or remove it together with its recording.
 * New words clear the themes, so the note is read again for what it is about.
 */
@Composable
private fun EditEntry(e: Entry, onClose: () -> Unit, onSave: (Entry) -> Unit, onDelete: () -> Unit) {
    var text by remember { mutableStateOf(e.words) }
    val moods = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(e.moods) } }
    val points = remember { FourPointsState(e.points) }
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(interactionSource = null, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Color(0xFF0C1630))
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
                BasicTextField(text, { text = it }, textStyle = BodyStyle.copy(fontSize = 17.sp, lineHeight = 25.sp, shadow = null),
                    cursorBrush = SolidColor(Ink.accent), modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp))
            }
            FourPointsCard(points, moods)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallAction(NeboIcon.Trash, "Obriši") { confirmDelete = true }
                Spacer(Modifier.weight(1f))
                GlassButton("Otkaži", onClose)
                GlassButton("Sačuvaj", {
                    val words = text.trim()
                    val changedWords = words != e.words.trim()
                    onSave(when {
                        !changedWords -> e.copy(moods = moods.toList(), themes = if (moods.toList() == e.moods) e.themes else emptyList())
                        // corrected words of a recording become its transcript, as on the check-in screen
                        e.audioPath != null -> e.copy(text = null, transcript = words, moods = moods.toList(), themes = emptyList())
                        else -> e.copy(text = words.ifBlank { null }, moods = moods.toList(), themes = emptyList())
                    }.copy(points = points.toPoints()))
                }, strong = true)
            }
        }
    }
}

@Composable
private fun RoundAction(icon: NeboIcon, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Ink.surfaceHigh).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, Ink.text, 20.dp) }
}

@Composable
private fun SmallAction(icon: NeboIcon, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Ink.surfaceHigh).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, Ink.text, 15.dp)
        Text(label, style = SmallStyle.copy(fontWeight = FontWeight.SemiBold, color = Ink.text, shadow = null))
    }
}

@Composable
private fun SessionRow(row: Session, koanTitle: String?) {
    val t = SESSION_TYPES.firstOrNull { it.id == row.type }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.surface.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Ink.surfaceHigh), contentAlignment = Alignment.Center) { Icon(t?.icon ?: NeboIcon.Breath, Ink.accent, 18.dp) }
        Column(Modifier.weight(1f)) {
            Text("${TYPE_LABEL[row.type] ?: row.type} · " + (if (row.actualSec < 60) "${row.actualSec} s" else "${row.actualSec / 60} min") + (if (row.thoughts > 0) " · oblaka ${row.thoughts}" else ""),
                style = BodyStyle.copy(fontSize = 15.sp, shadow = null))
            if (koanTitle != null) Text(koanTitle, style = SmallStyle.copy(color = Ink.accent, shadow = null))
        }
        Text(timeFmt.format(Date(row.startedAt)), style = SmallStyle.copy(shadow = null))
    }
}

/** One note: a stripe in its mood's colour, the words large, small actions underneath. */
@Composable
internal fun EntryCard(row: Entry, player: NotePlayer, onStar: () -> Unit, onSimilar: (() -> Unit)?, footer: String? = null, onEdit: (() -> Unit)? = null) {
    val stripe = row.moods.firstOrNull()?.let { MOOD_COLORS[it] } ?: Ink.hairline
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(24.dp)).background(Ink.surface)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(stripe))
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${timeFmt.format(Date(row.createdAt))} · ${KIND_LABEL[row.kind] ?: row.kind}" +
                    (if (row.moods.isNotEmpty()) " · " + row.moods.joinToString(", ") else ""), style = SmallStyle.copy(shadow = null), modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = if (row.starred) "Ukloni oznaku uvida" else "Označi kao uvid", onClick = onStar),
                    contentAlignment = Alignment.Center) {
                    Icon(if (row.starred) NeboIcon.StarFilled else NeboIcon.Star, if (row.starred) Ink.accent else Ink.faint, 19.dp)
                }
            }
            val words = row.words
            when {
                words.isNotBlank() -> Text(words, style = BodyStyle.copy(fontSize = 17.sp, lineHeight = 25.sp, shadow = null))
                row.audioPath != null && row.transcript == null -> Text("Snimak čeka da postane tekst.", style = SmallStyle.copy(shadow = null))
            }
            row.points.summary().takeIf { it.isNotBlank() }?.let { Text(it, style = SmallStyle.copy(shadow = null)) }
            if (footer != null) Text(footer, style = SmallStyle.copy(color = Ink.accent, shadow = null))
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
            ScreenHeader("Slično ranije", { player.stop(); go(Screen.Journal) })
            target?.let { t ->
                PaperCard {
                    Text(dayFmt.format(Date(t.createdAt)).replaceFirstChar { it.uppercase() } + " · " + timeFmt.format(Date(t.createdAt)), style = PaperSmall)
                    Text(t.words, style = PaperText)
                }
            }
            val r = result
            when {
                r == null -> Text("Tražim po značenju…", style = BodyStyle)
                r.isEmpty() -> Text("Još nema dovoljno bilješki za poređenje. Kako budeš zapisivao, ovdje će se pokazivati ranija slična stanja.", style = BodyStyle)
                else -> {
                    Text(if (app.recommender.usesMeaning) "Najbliže po značenju, od najsličnijeg:" else "Bilješke s istim temama ili raspoloženjem:", style = SmallStyle)
                    for ((e, score) in r) EntryCard(e, player, onStar = {}, onSimilar = null,
                        footer = dayFmt.format(Date(e.createdAt)).replaceFirstChar { it.uppercase() } + " · " + app.recommender.closeness(score))
                }
            }
        }
    }
}

/** A calm overview: no streaks, no scores, just what happened and what keeps returning. */
@Composable
fun StatsScreen(go: (Screen) -> Unit) {
    val app = LocalContext.current.app
    var data by remember { mutableStateOf<StatsData?>(null) }
    LaunchedEffect(Unit) { data = withContext(Dispatchers.IO) { computeStats(app.db.sessions(), app.db.entries(), app.db.prompts(0)) } }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(top = 8.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader("Pregled", null) {
                GlassButton("Sedmica", { go(Screen.CheckIn("week")) }, icon = NeboIcon.Week)
            }
            val d = data ?: return@Column
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${d.weekMinutes}", "min ove sedmice", Modifier.weight(1f))
                StatTile("${d.weekSessions}", plural(d.weekSessions, "sesija", "sesije", "sesija"), Modifier.weight(1f))
                StatTile("${d.weekNotes}", plural(d.weekNotes, "zapis", "zapisa", "zapisa"), Modifier.weight(1f))
            }
            Panel {
                SectionLabel("Minute po sedmici")
                Bars(d.weeks.map { it.toFloat() }, d.weekLabels, highlightLast = true)
            }
            Panel {
                SectionLabel("Kada meditiraš")
                val parts = listOf("Jutro" to NeboIcon.Sun, "Dan" to NeboIcon.Sun, "Veče" to NeboIcon.Moon, "Noć" to NeboIcon.Moon)
                val max = d.dayParts.maxOrNull()?.coerceAtLeast(1) ?: 1
                parts.forEachIndexed { i, (label, icon) -> MeterRow(icon, label, d.dayParts[i], max, "min") }
            }
            if (d.moodWeeks.any { it.isNotEmpty() }) Panel {
                SectionLabel("Raspoloženja kroz sedmice")
                MoodTrend(d.moodWeeks, d.weekLabels)
                val seen = d.moodWeeks.flatMap { it.keys }.distinct()
                ChipRow { for (m in seen) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(MOOD_COLORS[m] ?: Ink.soft))
                    Text(" $m", style = SmallStyle.copy(shadow = null))
                } }
            }
            Panel {
                SectionLabel("Teme koje se vraćaju")
                if (d.themes.isEmpty()) Text("Kad snimiš ili zapišeš nekoliko zapažanja, ovdje će se pokazati šta se ponavlja.", style = SmallStyle.copy(shadow = null))
                else {
                    val top = d.themes.first().second.coerceAtLeast(1)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((t, n) in d.themes) {
                            // more frequent themes stand out a little more
                            val k = n.toFloat() / top
                            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Ink.accent.copy(alpha = 0.10f + 0.25f * k)).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(THEME_NAMES[t] ?: t, style = BodyStyle.copy(fontSize = (13 + 4 * k).sp, fontWeight = FontWeight.SemiBold, shadow = null))
                                Text("  $n", style = SmallStyle.copy(shadow = null))
                            }
                        }
                    }
                }
            }
            Panel {
                SectionLabel("Kako su povezani")
                if (d.links.isEmpty()) Text("Kad u bilješkama primijetiš i tijelo i nagone, ovdje će se pokazati šta najčešće ide uz koju emociju.",
                    style = SmallStyle.copy(shadow = null))
                else {
                    Text("Uz koju emociju najčešće ide koje stanje tijela i koji nagon:", style = SmallStyle.copy(shadow = null))
                    for (l in d.links) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        @Composable fun pill(text: String, color: Color) = Text(text, style = SmallStyle.copy(color = Ink.inkOnPaper, fontWeight = FontWeight.SemiBold, shadow = null),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(color).padding(horizontal = 10.dp, vertical = 5.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                pill(l.mood, MOOD_COLORS[l.mood] ?: POINT_COLORS.getValue(FourPointsReader.EMOTIONS))
                                l.body?.let { Text("→", style = SmallStyle.copy(shadow = null)); pill(it, POINT_COLORS.getValue(FourPointsReader.BODY)) }
                                l.urge?.let { Text("→", style = SmallStyle.copy(shadow = null)); pill(it, POINT_COLORS.getValue(FourPointsReader.URGES)) }
                            }
                        }
                        Text("${l.times}×", style = SmallStyle.copy(shadow = null))
                    }
                }
            }
            Panel {
                SectionLabel("Gdje nosiš napetost")
                if (d.tension.isEmpty()) Text("Kad uradiš skeniranje tijela, ili u bilješci označiš tačku tijelo, ovdje se vidi gdje najčešće nosiš napetost.",
                    style = SmallStyle.copy(shadow = null))
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    BodyMap(d.tension, Modifier.width(92.dp).height(220.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val ranked = d.tension.entries.sortedByDescending { it.value }
                        bodyRegion(ranked.first().key)?.let { Text("Najčešće je nosiš ${it.where}.", style = BodyStyle.copy(fontWeight = FontWeight.Bold, shadow = null)) }
                        val top = ranked.first().value.coerceAtLeast(1)
                        for ((id, n) in ranked.take(5)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(TENSION.copy(alpha = 0.35f + 0.65f * n / top)))
                            Text(bodyRegion(id)?.name ?: id, style = BodyStyle.copy(fontSize = 14.sp, shadow = null), modifier = Modifier.weight(1f))
                            Text("$n×", style = SmallStyle.copy(shadow = null))
                        }
                        Text(if (d.scans > 0) "Iz skeniranja tijela i iz bilješki s tačkom tijelo." else "Iz bilješki s tačkom tijelo. Skeniranje tijela dodaje još.",
                            style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
                    }
                }
            }
            Panel {
                SectionLabel("Navike uma")
                Text("Ovo nisu greške, nego navike koje ima svaki um. Kad ih primijetiš, manje te vode.", style = SmallStyle.copy(shadow = null))
                if (d.times.isEmpty() && d.habits.isEmpty() && d.wander.isEmpty())
                    Text("Kad zapisuješ misli, ovdje se pokaže šta se u njima ponavlja: „moram“, „šta ako“, „uvijek“ i „nikad“, i da li je um više u prošlosti ili u budućnosti.",
                        style = SmallStyle.copy(shadow = null))
                if (d.times.isNotEmpty()) {
                    val (top, _) = d.times.maxBy { it.value }.toPair()
                    Text("Posljednjih 30 dana um je najčešće bio ${top.label}: ${top.about}.", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold, shadow = null))
                    TimeBar(d.times)
                }
                for ((id, n) in d.habits) MindHabits.habit(id)?.let { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = BodyStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = null))
                            Text(h.about, style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
                        }
                        Text("$n×", style = SmallStyle.copy(shadow = null))
                    }
                }
                if (d.wander.isNotEmpty()) Text("U „Oblacima“ um je najčešće odlazio u: " + d.wander.take(3).joinToString(", ") { "${it.first} (${it.second})" } + ".",
                    style = SmallStyle.copy(shadow = null))
            }
            if (d.shifts.isNotEmpty()) Panel {
                SectionLabel("Prije → poslije sesije")
                for ((s, n) in d.shifts) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s, style = BodyStyle.copy(shadow = null), modifier = Modifier.weight(1f))
                    Text("$n×", style = SmallStyle.copy(shadow = null))
                }
            }
            if (d.types.isNotEmpty()) Panel {
                SectionLabel("Vrste sesija")
                val max = d.types.maxOf { it.second }
                for ((t, n) in d.types) MeterRow(SESSION_TYPES.firstOrNull { it.id == t }?.icon ?: NeboIcon.Breath, TYPE_LABEL[t] ?: t, n, max, "")
            }
            Panel {
                SectionLabel("Zapažanja tokom dana")
                if (d.promptsFired == 0) Text("Pozivi su isključeni ili još nijesu stigli.", style = SmallStyle.copy(shadow = null)) else {
                    Text("${d.promptsAnswered} od ${d.promptsFired}", style = NumberStyle.copy(fontSize = 30.sp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ink.surfaceHigh)) {
                        Box(Modifier.fillMaxWidth(d.promptsAnswered.toFloat() / d.promptsFired).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Ink.accent))
                    }
                    Text("poziva s odgovorom. Propušten poziv je samo propušten.", style = SmallStyle.copy(shadow = null))
                }
            }
        }
    }
}

/** The mind's time as one bar in three parts: the past, here and now, the future. */
@Composable
private fun TimeBar(times: Map<MindHabits.Time, Int>) {
    val total = times.values.sum().coerceAtLeast(1)
    val colors = mapOf(MindHabits.Time.PAST to POINT_COLORS.getValue(FourPointsReader.THOUGHTS), MindHabits.Time.NOW to POINT_COLORS.getValue(FourPointsReader.BODY),
        MindHabits.Time.FUTURE to POINT_COLORS.getValue(FourPointsReader.EMOTIONS))
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Ink.surfaceHigh)) {
        for (t in MindHabits.Time.entries) {
            val n = times[t] ?: 0
            if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(colors.getValue(t)))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (t in MindHabits.Time.entries) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.getValue(t)))
            Text("${t.label} ${(times[t] ?: 0) * 100 / total}%", style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
        }
    }
}

/** An icon, a label, a thin bar for the value and the value itself. */
@Composable
private fun MeterRow(icon: NeboIcon, label: String, value: Int, max: Int, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, Ink.soft, 16.dp)
        Text(label, style = BodyStyle.copy(fontSize = 14.sp, shadow = null), modifier = Modifier.width(78.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ink.surfaceHigh)) {
            if (value > 0) Box(Modifier.fillMaxWidth(value.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Ink.accent))
        }
        Text(if (unit.isEmpty()) "$value" else "$value $unit", style = SmallStyle.copy(shadow = null), modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
    }
}

class StatsData(
    val weekMinutes: Int, val weekSessions: Int, val weekNotes: Int, val weeks: List<Int>, val weekLabels: List<String>, val dayParts: List<Int>,
    val types: List<Pair<String, Int>>, val themes: List<Pair<String, Int>>, val shifts: List<Pair<String, Int>>,
    val promptsFired: Int, val promptsAnswered: Int, val esmByPart: List<Int>,
    val moodWeeks: List<Map<String, Int>>,
    val links: List<PointLink> = emptyList(),
    /** How often each part of the body was named as tense: in scans (once a scan) and in notes' body point. */
    val tension: Map<String, Int> = emptyMap(),
    val scans: Int = 0,
    /** The habits of mind in the last 30 days' notes, the mind's time, and where it went during clouds sessions. */
    val habits: List<Pair<String, Int>> = emptyList(),
    val times: Map<MindHabits.Time, Int> = emptyMap(),
    val wander: List<Pair<String, Int>> = emptyList(),
)

/** Where tension was named: each scan counts a part once, each note once for each part its body point names. */
fun tensionCounts(sessions: List<Session>, entries: List<Entry>): Map<String, Int> {
    val c = HashMap<String, Int>()
    for (s in sessions) if (s.type == "bodyscan") s.marks.map { it.region }.distinct().forEach { c[it] = (c[it] ?: 0) + 1 }
    for (e in entries) e.points.body.mapNotNull { regionOfLabel(it) }.distinct().forEach { c[it] = (c[it] ?: 0) + 1 }
    return c
}

/** An emotion and what most often comes with it in the body and as an urge, across the notes that name them. */
class PointLink(val mood: String, val body: String?, val urge: String?, val times: Int)

/** For each emotion seen at least twice: the body sensation and the urge that most often came with it. */
fun pointLinks(entries: List<Entry>): List<PointLink> {
    val withPoints = entries.filter { it.moods.isNotEmpty() && (it.points.body.isNotEmpty() || it.points.urges.isNotEmpty()) }
    return withPoints.flatMap { e -> e.moods.map { it to e } }.groupBy({ it.first }, { it.second })
        .filter { it.value.size >= 2 }
        .map { (mood, es) ->
            fun top(pick: (Entry) -> List<String>) = es.flatMap(pick).groupingBy { it }.eachCount().maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.key
            PointLink(mood, top { it.points.body }, top { it.points.urges }, es.size)
        }
        .filter { it.body != null || it.urge != null }
        .sortedByDescending { it.times }.take(5)
}

private fun dayPart(t: Long): Int {
    val h = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
    return when (h) { in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3 }
}

fun computeStats(sessions: List<Session>, entries: List<Entry>, prompts: List<com.grungert.nebo.data.Prompt>): StatsData {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val week = 7 * 86_400_000L
    val thisWeek = cal.timeInMillis
    val weeks = (7 downTo 0).map { k -> (sessions.filter { it.startedAt in (thisWeek - k * week) until (thisWeek - (k - 1) * week) }.sumOf { it.actualSec } + 59) / 60 }
    val labels = (7 downTo 0).map { k -> SimpleDateFormat("d.M.", Locale.ROOT).format(Date(thisWeek - k * week)) }
    // minutes rounded up, so a short sit still shows
    val parts = IntArray(4).also { a -> sessions.forEach { a[dayPart(it.startedAt)] += it.actualSec } }.map { (it + 59) / 60 }
    val types = sessions.groupingBy { it.type }.eachCount().toList().sortedByDescending { it.second }
    val themeCount = HashMap<String, Int>()
    for (e in entries) {
        val ts = (e.themes.ifEmpty { Lexicon.themes(e.words) } + e.moods.mapNotNull { m -> MOODS.firstOrNull { it.first == m }?.second }).toSet()
        ts.forEach { themeCount[it] = (themeCount[it] ?: 0) + 1 }
    }
    val shifts = HashMap<String, Int>()
    for (s in sessions) {
        val b = entries.firstOrNull { it.sessionId == s.id && it.kind == "before" }?.moods?.firstOrNull() ?: continue
        val a = entries.firstOrNull { it.sessionId == s.id && it.kind == "after" }?.moods?.firstOrNull() ?: continue
        val k = "$b → $a"; shifts[k] = (shifts[k] ?: 0) + 1
    }
    val fired = prompts.filter { it.firedAt != null }
    val esm = entries.filter { it.kind == "esm" }
    val month = entries.filter { it.createdAt >= System.currentTimeMillis() - 30 * 86_400_000L }
    val mind = month.map { it.words + "\n" + it.points.thoughts }
    return StatsData(
        weekMinutes = weeks.last(), weekSessions = sessions.count { it.startedAt >= thisWeek }, weekNotes = entries.count { it.createdAt >= thisWeek }, weeks = weeks, weekLabels = labels,
        dayParts = parts, types = types, themes = themeCount.toList().sortedByDescending { it.second }.take(8),
        shifts = shifts.toList().sortedByDescending { it.second }.take(6),
        promptsFired = fired.size, promptsAnswered = fired.count { it.answeredEntryId != null }, links = pointLinks(entries),
        esmByPart = IntArray(4).also { a -> esm.forEach { a[dayPart(it.createdAt)]++ } }.toList(),
        moodWeeks = (7 downTo 0).map { k ->
            entries.filter { it.createdAt in (thisWeek - k * week) until (thisWeek - (k - 1) * week) }.flatMap { it.moods }.groupingBy { it }.eachCount()
        },
        tension = tensionCounts(sessions, entries),
        scans = sessions.count { it.type == "bodyscan" },
        habits = MindHabits.HABITS.map { h -> h.id to mind.count { h.id in MindHabits.habits(it) } }.filter { it.second > 0 }.sortedByDescending { it.second },
        times = mind.mapNotNull { MindHabits.time(it) }.groupingBy { it }.eachCount(),
        wander = sessions.flatMap { it.wander }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second },
    )
}

/** Rounded bars on one scale, the value above each; the latest one in warm light. */
@Composable
fun Bars(values: List<Float>, labels: List<String>, highlightLast: Boolean = false) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val hot = highlightLast && i == values.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v > 0) v.toInt().toString() else "", style = SmallStyle.copy(fontSize = 11.sp, color = if (hot) Ink.accent else Ink.soft, shadow = null))
                Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                    val h = (size.height * (v / max)).coerceAtLeast(if (v > 0) 6f else 3f)
                    drawRoundRect(if (hot) Ink.accent else Color.White.copy(alpha = if (v > 0) 0.42f else 0.12f),
                        topLeft = Offset(size.width * 0.15f, size.height - h), size = Size(size.width * 0.7f, h), cornerRadius = CornerRadius(size.width * 0.35f))
                }
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, style = SmallStyle.copy(fontSize = 10.sp, shadow = null), maxLines = 1)
            }
        }
    }
}

/** One column per week: the moods noted that week, stacked in their colours. No scores, just the mix. */
@Composable
fun MoodTrend(weeks: List<Map<String, Int>>, labels: List<String>) {
    val max = weeks.maxOf { it.values.sum() }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        weeks.forEachIndexed { i, w ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                    val bw = size.width * 0.7f
                    val x = size.width * 0.15f
                    drawRoundRect(Color.White.copy(alpha = 0.08f), topLeft = Offset(x, 0f), size = Size(bw, size.height), cornerRadius = CornerRadius(bw / 2))
                    var y = size.height
                    for ((mood, n) in w.entries.sortedBy { it.key }) {
                        val h = size.height * n / max
                        drawRect(MOOD_COLORS[mood] ?: Color.White, topLeft = Offset(x, y - h), size = Size(bw, h))
                        y -= h
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, style = SmallStyle.copy(fontSize = 10.sp, shadow = null), maxLines = 1)
            }
        }
    }
}
