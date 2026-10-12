package com.grungert.nebo.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.app
import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.TYPE_LABEL
import com.grungert.nebo.esm.Esm
import com.grungert.nebo.koan.Lexicon
import com.grungert.nebo.koan.MOODS
import com.grungert.nebo.koan.THEME_NAMES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private class WeekData(
    val reflection: Entry?,
    val range: String,
    val themes: List<Triple<String, Int, Int>>,      // theme, this week, last week
    val quotes: List<Pair<String, Entry>>,           // theme, the note that says it best
    val notesByPart: List<Int>,
    val moods: List<Pair<String, Int>>,
    val minutes: Int,
    val sessions: Int,
    val types: List<Pair<String, Int>>,
    val earlier: List<Pair<Entry, Float>>,
    val nextKoan: com.grungert.nebo.koan.KoanItem?,
)

private fun themesOf(e: Entry) = (e.themes.ifEmpty { Lexicon.themes(e.words) } + e.moods.mapNotNull { m -> MOODS.firstOrNull { it.first == m }?.second }).toSet()

private fun partOf(t: Long): Int {
    val h = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
    return when (h) { in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3 }
}

/**
 * The weekly look back. The person's own noticing comes first; only then the patterns: what kept
 * returning compared with last week, when in the day, their own sentences that say it best, and
 * earlier moments that felt the same. No scores and no diagnoses.
 */
@Composable
fun WeekScreen(sc: Screen.Week, go: (Screen) -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<WeekData?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var kept by remember { mutableStateOf(false) }

    LaunchedEffect(sc.entryId) {
        var reflection = sc.entryId?.let { withContext(Dispatchers.IO) { app.db.entry(it) } }
        var waited = 0
        while (reflection != null && reflection.audioPath != null && reflection.transcript == null && app.models.readyWhisper() != null && waited < 90) {
            waiting = true; delay(1000); waited++
            reflection = withContext(Dispatchers.IO) { app.db.entry(reflection!!.id) }
        }
        waiting = false
        val start = weekStart()
        val week = 7 * 86_400_000L
        val (all, sessions) = withContext(Dispatchers.IO) { app.db.entries() to app.db.sessions(start) }
        val thisWeek = all.filter { it.createdAt >= start && it.id != reflection?.id }
        val lastWeek = all.filter { it.createdAt in (start - week) until start }
        val count = HashMap<String, Int>(); val prev = HashMap<String, Int>()
        thisWeek.forEach { e -> themesOf(e).forEach { count[it] = (count[it] ?: 0) + 1 } }
        lastWeek.forEach { e -> themesOf(e).forEach { prev[it] = (prev[it] ?: 0) + 1 } }
        val themes = count.entries.sortedByDescending { it.value }.take(5).map { Triple(it.key, it.value, prev[it.key] ?: 0) }
        val quotes = themes.take(3).mapNotNull { (t, _, _) ->
            thisWeek.filter { t in themesOf(it) && it.words.length in 12..400 }.maxByOrNull { it.words.length.coerceAtMost(160) }?.let { t to it }
        }.distinctBy { it.second.id }
        val parts = IntArray(4).also { a -> thisWeek.forEach { a[partOf(it.createdAt)]++ } }.toList()
        val moods = thisWeek.flatMap { it.moods }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
        val earlier = if (reflection != null && reflection.words.isNotBlank()) app.recommender.similar(reflection, all.filter { it.createdAt < start }, 3) else emptyList()
        // a koan for the coming week, chosen by what the week held
        val query = listOfNotNull(reflection?.words, themes.take(3).joinToString(", ") { THEME_NAMES[it.first] ?: it.first }).joinToString(". ")
        val nextKoan = app.recommender.pick(query, emptyList(), afterSession = true, koansOnly = true).item
        val fmt = SimpleDateFormat("d. MMMM", Locale.forLanguageTag("sr-Latn-ME"))
        data = WeekData(
            reflection, fmt.format(Date(start)) + " – " + fmt.format(Date(start + week - 1)), themes, quotes, parts, moods,
            sessions.sumOf { it.actualSec } / 60, sessions.size, sessions.groupingBy { it.type }.eachCount().toList(), earlier, nextKoan,
        )
        app.settings.weekReviewed = Esm.weekKey()
    }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader("Tvoja sedmica", { go(Screen.Home) })
            val d = data
            if (d == null) {
                Spacer(Modifier.height(60.dp))
                Text(if (waiting) "Slušam šta si rekao…" else "Skupljam sedmicu…", style = HeadStyle, modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }
            Text(d.range.uppercase(), style = LabelStyle)

            d.reflection?.takeIf { it.words.isNotBlank() }?.let { r ->
                PaperCard {
                    Text("Ti si primijetio", style = PaperSmall)
                    Text(r.words, style = PaperText)
                }
                GlassButton(if (kept || r.starred) "Sačuvano među uvidima" else "Sačuvaj kao uvid", {
                    kept = true
                    scope.launch { withContext(Dispatchers.IO) { app.db.updateEntry(r.copy(starred = true)) } }
                }, icon = if (kept || r.starred) NeboIcon.StarFilled else NeboIcon.Star, enabled = !(kept || r.starred))
            }

            Panel {
                SectionLabel("Šta se vraćalo")
                if (d.themes.isEmpty()) Text("Ove sedmice nema dovoljno zapisa da se vidi šta se ponavlja. I to je u redu.", style = BodyStyle)
                for ((t, n, before) in d.themes) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(THEME_NAMES[t] ?: t, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text("$n×", style = BodyStyle)
                    Text(when {
                        before == 0 -> "  novo"
                        n > before -> "  više nego prošle"
                        n < before -> "  manje nego prošle"
                        else -> "  kao prošle"
                    }, style = SmallStyle.copy(color = if (before == 0) Ink.accent else Ink.soft))
                }
            }

            if (d.quotes.isNotEmpty()) {
                Text("TVOJIM RIJEČIMA", style = LabelStyle)
                for ((t, e) in d.quotes) PaperCard {
                    Text((THEME_NAMES[t] ?: t).replaceFirstChar { it.uppercase() } + " · " +
                        SimpleDateFormat("EEEE", Locale.forLanguageTag("sr-Latn-ME")).format(Date(e.createdAt)), style = PaperSmall)
                    Text("„${e.words}“", style = PaperText.copy(fontStyle = FontStyle.Italic, fontSize = 20.sp))
                }
            }

            if (d.notesByPart.any { it > 0 }) Panel {
                SectionLabel("Kada si zapisivao")
                Bars(d.notesByPart.map { it.toFloat() }, listOf("jutro", "dan", "veče", "noć"))
                if (d.moods.isNotEmpty()) Text("Raspoloženja: " + d.moods.joinToString(", ") { "${it.first} ${it.second}×" }, style = SmallStyle)
            }

            Panel {
                SectionLabel("U tišini")
                Text(if (d.sessions == 0) "Ove sedmice nije bilo sesija." else "${d.minutes} min · ${d.sessions} ${plural(d.sessions, "sesija", "sesije", "sesija")}", style = HeadStyle.copy(fontSize = 24.sp))
                if (d.types.isNotEmpty()) Text(d.types.joinToString(" · ") { "${TYPE_LABEL[it.first] ?: it.first} ${it.second}" }, style = SmallStyle)
            }

            if (d.earlier.isNotEmpty()) {
                Text("RANIJE SI SE OVAKO OSJEĆAO", style = LabelStyle)
                for ((e, _) in d.earlier) Panel(padding = 16.dp) {
                    Text(SimpleDateFormat("d. MMMM yyyy.", Locale.forLanguageTag("sr-Latn-ME")).format(Date(e.createdAt)), style = SmallStyle)
                    Text(e.words, style = BodyStyle)
                }
            }

            d.nextKoan?.let { k ->
                Text("KOAN ZA SLJEDEĆU SEDMICU", style = LabelStyle, modifier = Modifier.padding(top = 8.dp))
                PaperCard {
                    Text(k.title ?: "", style = PaperText.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold))
                    Text(k.text, style = PaperText.copy(fontSize = 20.sp, lineHeight = 28.sp))
                    k.question?.let { Text(it, style = PaperText.copy(fontStyle = FontStyle.Italic, color = Ink.seal, fontSize = 20.sp)) }
                }
                var taken by remember { mutableStateOf(app.settings.activeWeekKoan() == k.id) }
                GlassButton(if (taken) "Nosiš ga ove sedmice" else "Nosi ga sedam dana", {
                    app.settings.carryKoan(k.id); taken = true
                }, Modifier.fillMaxWidth(), icon = NeboIcon.Week, enabled = !taken)
            }

            Text("Šta od ovoga želiš ponijeti u sljedeću sedmicu?", style = HeadStyle.copy(fontSize = 22.sp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            GlassButton("Nazad na nebo", { go(Screen.Home) }, Modifier.fillMaxWidth(), strong = true)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The koan being carried, and every note written alongside it, newest first. */
@Composable
fun KoanNotesScreen(sc: Screen.KoanNotes, go: (Screen) -> Unit) {
    val app = LocalContext.current.app
    val item = remember { app.library.byId(sc.koanId) }
    val player = remember { com.grungert.nebo.audio.NotePlayer() }
    var notes by remember { mutableStateOf<List<Entry>?>(null) }
    var active by remember { mutableStateOf(app.settings.activeWeekKoan() == sc.koanId) }
    LaunchedEffect(sc.koanId) { notes = withContext(Dispatchers.IO) { app.db.entriesForKoan(sc.koanId) } }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Koan sedmice", { player.stop(); go(Screen.Home) })
            if (item == null) { Text("Ovaj koan više nije u biblioteci.", style = BodyStyle); return@Column }
            PaperCard {
                Text(item.title ?: "", style = PaperText.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold))
                Text(item.text, style = PaperText)
                item.question?.let { Text(it, style = PaperText.copy(fontStyle = FontStyle.Italic, color = Ink.seal, fontSize = 21.sp)) }
            }
            if (active) {
                Text("Nosiš ga ${app.settings.weekKoanDay()}. dan od sedam.", style = SmallStyle.copy(color = Ink.accent))
                GlassButton("Pusti ga", { app.settings.carryKoan(null); active = false }, icon = NeboIcon.Cloud)
            }
            Text("BILJEŠKE UZ OVAJ KOAN", style = LabelStyle, modifier = Modifier.padding(top = 8.dp))
            val n = notes
            when {
                n == null -> Text("…", style = BodyStyle)
                n.isEmpty() -> Text("Još nema bilješki. Poslije sesije zapiši šta ti koan danas pokazuje; ovdje će se skupljati.", style = BodyStyle.copy(color = Ink.soft))
                else -> n.forEach { EntryCard(it, player, onStar = {}, onSimilar = null) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
