package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.grungert.nebo.koan.THEME_NAMES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dayFmt = SimpleDateFormat("EEEE, d. MMMM", Locale.forLanguageTag("sr-Latn-ME"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale.ROOT)

@Composable
fun JournalScreen(go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val transcribed by app.transcriber.done.collectAsState()
    var rows by remember { mutableStateOf<List<Any>>(emptyList()) }
    var onlyInsights by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val player = remember { NotePlayer() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(transcribed, onlyInsights, reload) {
        rows = withContext(Dispatchers.IO) {
            val entries = app.db.entries().filter { !onlyInsights || it.starred }
            val sessions = if (onlyInsights) emptyList() else app.db.sessions()
            (sessions.map { it.startedAt to it } + entries.map { it.createdAt to it }).sortedByDescending { it.first }
                .flatMap { (t, item) -> listOf(dayFmt.format(Date(t)), item) }
                .fold(mutableListOf<Any>()) { acc, x -> if (x is String && acc.lastOrNull { it is String } == x) acc else acc.apply { add(x) } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().screenPadding().padding(top = 16.dp)) {
            ScreenHeader("Dnevnik", { player.stop(); go(Screen.Home) }) {
                GlassButton("Izvezi", { Export.share(context, app.db, app.library) })
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Sve", !onlyInsights, { onlyInsights = false })
                Chip("Uvidi", onlyInsights, { onlyInsights = true }, dot = Ink.accent)
            }
            Spacer(Modifier.height(8.dp))
            if (rows.isEmpty()) Text(if (onlyInsights) "Označi zvjezdicom bilješke koje su ti nešto otvorile; ovdje će se skupljati."
                else "Ovdje će se pojaviti tvoje sesije i zapažanja.", style = BodyStyle.copy(color = Ink.soft), modifier = Modifier.padding(top = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows) { row ->
                    when (row) {
                        is String -> Text(row.replaceFirstChar { it.uppercase() }, style = HeadStyle.copy(fontSize = 20.sp), modifier = Modifier.padding(top = 18.dp, bottom = 2.dp))
                        is Session -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = 6.dp)) {
                            val t = SESSION_TYPES.firstOrNull { it.id == row.type }
                            Icon(t?.icon ?: NeboIcon.Breath, Ink.soft, 20.dp)
                            Column(Modifier.weight(1f)) {
                                Text("${timeFmt.format(Date(row.startedAt))} · ${TYPE_LABEL[row.type] ?: row.type}, ${row.actualSec / 60} min" +
                                    (if (row.thoughts > 0) " · oblaka: ${row.thoughts}" else ""), style = SmallStyle.copy(fontSize = 14.sp))
                                app.library.byId(row.koanId)?.let { Text("koan: ${it.title ?: it.text.take(40)}", style = SmallStyle.copy(color = Ink.accent)) }
                            }
                        }
                        is Entry -> EntryCard(row, player, onStar = {
                            scope.launch { withContext(Dispatchers.IO) { app.db.updateEntry(row.copy(starred = !row.starred)) }; reload++ }
                        }, onSimilar = { go(Screen.Similar(row.id)) })
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
internal fun EntryCard(row: Entry, player: NotePlayer, onStar: () -> Unit, onSimilar: (() -> Unit)?, footer: String? = null) {
    Panel(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.moods.forEach { m -> Box(Modifier.size(9.dp).clip(CircleShape).background(MOOD_COLORS[m] ?: Ink.soft)) }
            Text("${timeFmt.format(Date(row.createdAt))} · ${KIND_LABEL[row.kind] ?: row.kind}" +
                (if (row.moods.isNotEmpty()) " · " + row.moods.joinToString(", ") else ""), style = SmallStyle, modifier = Modifier.weight(1f))
            Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClickLabel = if (row.starred) "Ukloni oznaku uvida" else "Označi kao uvid", onClick = onStar),
                contentAlignment = Alignment.Center) {
                Icon(if (row.starred) NeboIcon.StarFilled else NeboIcon.Star, if (row.starred) Ink.accent else Ink.faint, 20.dp)
            }
        }
        row.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = BodyStyle) }
        when {
            !row.transcript.isNullOrBlank() -> Text("„${row.transcript}“", style = HeadStyle.copy(fontSize = 21.sp, lineHeight = 28.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal))
            row.audioPath != null && row.transcript == null -> Text("Snimak čeka da postane tekst.", style = SmallStyle)
        }
        if (footer != null) Text(footer, style = SmallStyle.copy(color = Ink.accent))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (row.audioPath != null) GlassButton("Preslušaj", { player.play(row.audioPath) {} }, icon = NeboIcon.Play)
            if (onSimilar != null && row.words.isNotBlank()) GlassButton("Slične", onSimilar, icon = NeboIcon.Similar)
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
            ScreenHeader("Kad sam se ovako osjećao", { player.stop(); go(Screen.Journal) })
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
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader("Pregled", { go(Screen.Home) })
            val d = data ?: return@Column
            Panel {
                SectionLabel("Ova sedmica")
                Text("${d.weekMinutes} min · ${d.weekSessions} ${plural(d.weekSessions, "sesija", "sesije", "sesija")}", style = HeadStyle)
            }
            Panel {
                SectionLabel("Minute po sedmici (zadnjih 8)")
                Bars(d.weeks.map { it.toFloat() }, d.weekLabels)
            }
            Panel {
                SectionLabel("U koje doba dana")
                Bars(d.dayParts.map { it.toFloat() }, listOf("jutro", "dan", "veče", "noć"))
            }
            if (d.moodWeeks.any { it.isNotEmpty() }) Panel {
                SectionLabel("Raspoloženja kroz sedmice")
                MoodTrend(d.moodWeeks, d.weekLabels)
                val seen = d.moodWeeks.flatMap { it.keys }.distinct()
                ChipRow { for (m in seen) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(MOOD_COLORS[m] ?: Ink.soft))
                    Text(" $m", style = SmallStyle)
                } }
            }
            if (d.types.isNotEmpty()) Panel {
                SectionLabel("Vrste sesija")
                for ((t, n) in d.types) Text("${TYPE_LABEL[t] ?: t}: $n", style = BodyStyle)
            }
            Panel {
                SectionLabel("Teme koje se vraćaju")
                if (d.themes.isEmpty()) Text("Kad snimiš ili zapišeš nekoliko zapažanja, ovdje će se pokazati šta se ponavlja.", style = SmallStyle)
                for ((t, n) in d.themes) Text("${THEME_NAMES[t] ?: t} · $n", style = BodyStyle)
            }
            if (d.shifts.isNotEmpty()) Panel {
                SectionLabel("Prije → poslije sesije")
                for ((s, n) in d.shifts) Text("$s · $n×", style = BodyStyle)
            }
            Panel {
                SectionLabel("Zapažanja tokom dana")
                Text(if (d.promptsFired == 0) "Pozivi su isključeni ili još nijesu stigli." else
                    "Odgovoreno na ${d.promptsAnswered} od ${d.promptsFired} poziva. Propušten poziv je samo propušten.", style = BodyStyle)
                if (d.esmByPart.any { it > 0 }) Bars(d.esmByPart.map { it.toFloat() }, listOf("jutro", "dan", "veče", "noć"))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

class StatsData(
    val weekMinutes: Int, val weekSessions: Int, val weeks: List<Int>, val weekLabels: List<String>, val dayParts: List<Int>,
    val types: List<Pair<String, Int>>, val themes: List<Pair<String, Int>>, val shifts: List<Pair<String, Int>>,
    val promptsFired: Int, val promptsAnswered: Int, val esmByPart: List<Int>,
    val moodWeeks: List<Map<String, Int>>,
)

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
    val weeks = (7 downTo 0).map { k -> sessions.filter { it.startedAt in (thisWeek - k * week) until (thisWeek - (k - 1) * week) }.sumOf { it.actualSec } / 60 }
    val labels = (7 downTo 0).map { k -> SimpleDateFormat("d.M.", Locale.ROOT).format(Date(thisWeek - k * week)) }
    val parts = IntArray(4).also { a -> sessions.forEach { a[dayPart(it.startedAt)] += it.actualSec / 60 } }.toList()
    val types = sessions.groupingBy { it.type }.eachCount().toList().sortedByDescending { it.second }
    val themeCount = HashMap<String, Int>()
    for (e in entries) {
        val ts = (e.themes + Lexicon.themes(e.words) + e.moods.mapNotNull { m -> MOODS.firstOrNull { it.first == m }?.second }).toSet()
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
    return StatsData(
        weekMinutes = weeks.last(), weekSessions = sessions.count { it.startedAt >= thisWeek }, weeks = weeks, weekLabels = labels,
        dayParts = parts, types = types, themes = themeCount.toList().sortedByDescending { it.second }.take(8),
        shifts = shifts.toList().sortedByDescending { it.second }.take(6),
        promptsFired = fired.size, promptsAnswered = fired.count { it.answeredEntryId != null },
        esmByPart = IntArray(4).also { a -> esm.forEach { a[dayPart(it.createdAt)]++ } }.toList(),
        moodWeeks = (7 downTo 0).map { k ->
            entries.filter { it.createdAt in (thisWeek - k * week) until (thisWeek - (k - 1) * week) }.flatMap { it.moods }.groupingBy { it }.eachCount()
        },
    )
}

/** Simple bars with their values and labels; heights share one scale. */
@Composable
fun Bars(values: List<Float>, labels: List<String>) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v > 0) v.toInt().toString() else "", style = SmallStyle)
                Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                    val h = size.height * (v / max)
                    drawRoundRect(Color.White.copy(alpha = 0.14f), size = size, cornerRadius = CornerRadius(6f))
                    if (h > 0) drawRoundRect(Color(0xFFFFE7B8), topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = CornerRadius(6f))
                }
                Text(labels.getOrElse(i) { "" }, style = SmallStyle.copy(fontSize = 10.sp))
            }
        }
    }
}

/** One column per week: the moods noted that week, stacked in their colours. No scores, just the mix. */
@Composable
fun MoodTrend(weeks: List<Map<String, Int>>, labels: List<String>) {
    val max = weeks.maxOf { it.values.sum() }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        weeks.forEachIndexed { i, w ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    drawRoundRect(Color.White.copy(alpha = 0.10f), size = size, cornerRadius = CornerRadius(6f))
                    var y = size.height
                    for ((mood, n) in w.entries.sortedBy { it.key }) {
                        val h = size.height * n / max
                        drawRect(MOOD_COLORS[mood] ?: Color.White, topLeft = Offset(0f, y - h), size = Size(size.width, h))
                        y -= h
                    }
                }
                Text(labels.getOrElse(i) { "" }, style = SmallStyle.copy(fontSize = 10.sp))
            }
        }
    }
}
