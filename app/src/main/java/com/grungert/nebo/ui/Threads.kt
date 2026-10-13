package com.grungert.nebo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.app
import com.grungert.nebo.audio.NotePlayer
import com.grungert.nebo.data.Entry
import com.grungert.nebo.koan.InsightThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "3 zapisa", "5 zapisa", "21 zapis" */
private fun notes(n: Int) = "$n " + plural(n, "zapis", "zapisa", "zapisa")

/** The threads of insight, newest activity first: each a card with its span, its moods over time and its latest words. */
@Composable
fun ThreadsList(go: (Screen) -> Unit) {
    val app = LocalContext.current.app
    var threads by remember { mutableStateOf<List<InsightThread>?>(null) }
    LaunchedEffect(Unit) { threads = app.recommender.threads(withContext(Dispatchers.IO) { app.db.entries() }) }
    val t = threads
    when {
        t == null -> Text("Tražim šta se vraća…", style = BodyStyle.copy(color = Ink.soft), modifier = Modifier.padding(top = 24.dp))
        t.isEmpty() -> Text("Nit se pojavi kad se ista stvar vrati u bilješkama različitih dana. Što više zapisuješ, to ih je više.",
            style = BodyStyle.copy(color = Ink.soft), modifier = Modifier.padding(top = 24.dp))
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 130.dp)) {
            item {
                Text("Ono što se vraća kroz tvoje bilješke, po značenju" + if (app.recommender.usesMeaning) "." else " i temama.",
                    style = SmallStyle.copy(shadow = null), modifier = Modifier.padding(bottom = 4.dp))
            }
            items(t) { th -> ThreadCard(th) { go(Screen.Thread(th.entries.map { it.id }, th.title)) } }
        }
    }
}

@Composable
private fun ThreadCard(th: InsightThread, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink.surface)
            .clickable(role = Role.Button, onClickLabel = "Otvori nit", onClick = onOpen).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(th.title, style = HeadStyle.copy(fontSize = 20.sp, shadow = null))
        Text("${notes(th.entries.size)} · ${dayLabel(th.entries.first().createdAt)} → ${dayLabel(th.entries.last().createdAt)}",
            style = SmallStyle.copy(shadow = null))
        MoodDots(th.entries)
        Text("„" + th.entries.last().words.take(160) + "“", style = BodyStyle.copy(fontSize = 15.sp, fontStyle = FontStyle.Italic, color = Ink.soft, shadow = null),
            maxLines = 2)
    }
}

/** One dot per note, in the colour of its first mood, oldest on the left: how the thread has felt over time. */
@Composable
private fun MoodDots(entries: List<Entry>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (e in entries) Box(Modifier.size(10.dp).clip(CircleShape).background(e.moods.firstOrNull()?.let { MOOD_COLORS[it] } ?: Ink.hairline))
    }
}

/** One thread, oldest first: where it began, where it is now, and every note in between. */
@Composable
fun ThreadScreen(sc: Screen.Thread, go: (Screen) -> Unit) {
    val app = LocalContext.current.app
    val player = remember { NotePlayer() }
    var entries by remember { mutableStateOf<List<Entry>?>(null) }
    LaunchedEffect(sc) { entries = withContext(Dispatchers.IO) { sc.ids.mapNotNull { app.db.entry(it) }.sortedBy { it.createdAt } } }
    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(sc.title, { player.stop(); go(Screen.Journal) })
            val list = entries ?: return@Column
            if (list.isEmpty()) { Text("Ove bilješke više ne postoje.", style = BodyStyle); return@Column }
            val first = list.first()
            val last = list.last()
            Text("${notes(list.size)} · ${dayLabel(first.createdAt)} → ${dayLabel(last.createdAt)}", style = SmallStyle)
            PaperCard {
                Text("PRVI PUT · " + dayLabel(first.createdAt).uppercase(), style = PaperSmall)
                Text(first.words, style = PaperText.copy(fontSize = 19.sp, lineHeight = 26.sp), maxLines = 5)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.inkOnPaper.copy(alpha = 0.15f)))
                Text("POSLJEDNJI PUT · " + dayLabel(last.createdAt).uppercase(), style = PaperSmall)
                Text(last.words, style = PaperText.copy(fontSize = 19.sp, lineHeight = 26.sp), maxLines = 5)
                if (first.moods.isNotEmpty() || last.moods.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.inkOnPaper.copy(alpha = 0.15f)))
                    Text("Raspoloženje: " + (first.moods.joinToString(", ").ifEmpty { "—" }) + "  →  " + (last.moods.joinToString(", ").ifEmpty { "—" }),
                        style = PaperSmall.copy(fontSize = 14.sp))
                }
            }
            MoodDots(list)
            SectionLabel("Cijela nit")
            for (e in list) EntryCard(e, player, onStar = {}, onSimilar = { player.stop(); go(Screen.Similar(e.id)) }, footer = dayLabel(e.createdAt))
            Box(Modifier.height(40.dp))
        }
    }
}
