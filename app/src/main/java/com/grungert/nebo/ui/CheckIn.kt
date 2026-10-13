package com.grungert.nebo.ui

import android.Manifest
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.geometry.CornerRadius
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.grungert.nebo.Screen
import com.grungert.nebo.SkyHost
import com.grungert.nebo.app
import com.grungert.nebo.audio.NotePlayer
import com.grungert.nebo.audio.Recorder
import com.grungert.nebo.data.Entry
import com.grungert.nebo.koan.MOODS
import com.grungert.nebo.koan.Pick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_SECONDS = 120

/**
 * A note in your own words: speak (up to two minutes) or write, pick moods if you like, save.
 * The recording becomes text right away, so it can be read and corrected before saving.
 */
@Composable
fun CheckInScreen(sc: Screen.CheckIn, host: SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val recorder = remember { Recorder() }
    val player = remember { NotePlayer() }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableFloatStateOf(0f) }
    val levels = remember { mutableStateListOf<Float>() }       // the live voice wave, newest last
    var audio by remember { mutableStateOf<File?>(null) }
    var audioSeconds by remember { mutableFloatStateOf(0f) }
    var text by remember { mutableStateOf("") }
    val moods = remember { mutableStateListOf<String>() }
    var askedMic by remember { mutableStateOf(false) }
    var silent by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) }      // what the speech model heard in the current recording
    var fieldFromVoice by remember { mutableStateOf(false) }     // the text field holds that transcript, maybe corrected
    var model by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { host.showCircle = false; host.engine.paced = false }

    val (title, prompt) = when (sc.kind) {
        "before" -> "Kako si sada?" to "Prije nego što počneš: šta je u tebi?"
        "after" -> "Šta primjećuješ?" to "Šta se pojavilo dok si sjedio? Riječ ili dvije su dovoljne."
        "esm" -> "Kako si sada?" to "Šta je u tebi upravo sada? Nema pogrešnog odgovora."
        "week" -> "Tvoja sedmica" to "Prije nego što ti pokažem obrasce: šta ti primjećuješ u ovoj sedmici?"
        else -> "Zapažanje" to "Šta primjećuješ u sebi upravo sada?"
    }

    fun startRecording() {
        val f = File(context.filesDir, "audio/${System.currentTimeMillis()}.wav")
        recording = true; seconds = 0f; levels.clear()
        app.transcriber.prepare()
        scope.launch {
            val dur = recorder.record(f, MAX_SECONDS) { lv ->
                scope.launch { levels.add(lv); if (levels.size > 56) levels.removeAt(0) }
            }
            recording = false
            silent = recorder.peak < Recorder.SILENCE
            if (dur > 0.6f && !silent) {
                audio?.delete(); audio = f; audioSeconds = dur
                if (fieldFromVoice) { text = ""; fieldFromVoice = false }
                heard = null
                model = app.transcriber.readyModel()
                if (model != null) {
                    transcribing = true
                    val t = app.transcriber.transcribeNow(f)
                    transcribing = false
                    if (audio == f && t != null) {
                        heard = t
                        if (text.isBlank() && t.isNotBlank()) { text = t; fieldFromVoice = true }
                    }
                }
            } else f.delete()
        }
        scope.launch { while (recorder.isRecording || recording) { delay(100); seconds += 0.1f } }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        askedMic = true
        if (ok) startRecording()
    }
    val micAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun next(entryId: Long?) {
        when (sc.kind) {
            "before" -> go(Screen.Session(sc.config!!))
            "after" -> go(Screen.Koan(sc.sessionId, entryId))
            "week" -> go(Screen.Week(entryId))
            else -> go(Screen.Koan(null, entryId))
        }
    }

    fun save() {
        player.stop()
        if (recording) recorder.stop()
        scope.launch {
            val typed = text.trim().ifBlank { null }
            // when the field holds the transcript, the corrected version becomes the transcript;
            // without one yet, the recording is transcribed in the background after saving
            val transcript = if (fieldFromVoice) typed ?: "" else heard
            val entry = Entry(createdAt = System.currentTimeMillis(), kind = sc.kind, sessionId = sc.sessionId, promptId = sc.promptId,
                audioPath = audio?.absolutePath, text = if (fieldFromVoice) null else typed, moods = moods.toList(),
                transcript = if (audio != null) transcript else null, transcriptModel = if (transcript != null) model else null)
            val id = withContext(Dispatchers.IO) {
                val id = app.db.insertEntry(entry)
                sc.promptId?.let { app.db.markPromptAnswered(it, id) }
                id
            }
            if (audio != null) app.transcriber.drain()
            app.scope.launch { app.recommender.tagNotes() }
            next(id)
        }
    }

    fun clock(s: Float) = "%d:%02d".format(s.toInt() / 60, s.toInt() % 60)

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScreenHeader(title, if (sc.kind == "note" || sc.kind == "esm" || sc.kind == "week") ({ player.stop(); recorder.stop(); go(Screen.Home) }) else null)
            Text(prompt, style = BodyStyle.copy(color = Ink.soft))

            // speaking: one big button, the live wave of the voice, the time
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Ink.surface).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val pulse by rememberInfiniteTransition(label = "rec").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                    if (recording) Box(Modifier.size((104 + 26 * pulse).dp).clip(CircleShape).background(Ink.seal.copy(alpha = 0.22f)))
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(if (recording) Ink.seal else Ink.paper)
                            .clickable(role = Role.Button, onClickLabel = if (recording) "Zaustavi snimanje" else "Snimi") {
                                when {
                                    recording -> recorder.stop()
                                    micAllowed -> { player.stop(); startRecording() }
                                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (recording) Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
                        else Icon(NeboIcon.Mic, Ink.inkOnPaper, 34.dp)
                    }
                }
                // the wave: one bar per tenth of a second, louder is taller
                Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                    val n = 56
                    val w = size.width / n
                    for (i in 0 until n) {
                        val lv = levels.getOrNull(levels.size - n + i) ?: 0f
                        val h = (size.height * (0.08f + 0.92f * kotlin.math.sqrt(lv.coerceIn(0f, 1f)))).coerceAtMost(size.height)
                        drawRoundRect(if (recording) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                            topLeft = Offset(i * w + w * 0.2f, (size.height - h) / 2), size = Size(w * 0.6f, h), cornerRadius = CornerRadius(w * 0.3f))
                    }
                }
                Text(
                    when {
                        recording -> clock(seconds) + " / " + clock(MAX_SECONDS.toFloat())
                        silent -> "Ne čujem ništa. Provjeri da li je mikrofon uključen, pa pokušaj ponovo."
                        transcribing -> "Pretvaram u tekst…"
                        audio != null && heard != null && heard!!.isBlank() -> "Snimljeno, ali nijesam razabrao riječi. Možeš napisati."
                        audio != null && heard != null -> "Evo šta sam čuo. Možeš ispraviti."
                        audio != null -> "Snimljeno · " + clock(audioSeconds)
                        askedMic && !micAllowed -> "Bez mikrofona možeš i da napišeš."
                        else -> "Dodirni i govori, do dva minuta"
                    }, style = SmallStyle.copy(shadow = null, textAlign = TextAlign.Center),
                )
                if (audio != null && !recording) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Preslušaj", { player.play(audio!!.absolutePath) {} }, icon = NeboIcon.Play)
                    if (app.models.readyWhisper() == null) GlassButton("Preuzmi model za govor", { player.stop(); go(Screen.Settings) })
                }
            }

            // writing: the words (or the transcript to correct)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (fieldFromVoice) "Tvoje riječi, možeš ispraviti" else "Ili napiši")
                Box {
                    if (text.isEmpty()) Text("Šta je u tebi…", style = BodyStyle.copy(color = Ink.faint, shadow = null))
                    BasicTextField(text, { text = it }, textStyle = BodyStyle.copy(fontSize = 17.sp, lineHeight = 25.sp, shadow = null),
                        cursorBrush = SolidColor(Ink.accent), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
                }
            }

            // moods: one scrolling row
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Kako je u tebi (po želji)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MOODS.map { it.first }) { m -> Chip(m, m in moods, { if (m in moods) moods.remove(m) else moods.add(m) }, dot = MOOD_COLORS[m]) }
                }
            }
        }
        // the actions stay within reach at the bottom
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().screenPadding().padding(bottom = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassButton("Preskoči", { player.stop(); recorder.stop(); audio?.delete(); next(null) }, Modifier.weight(1f))
            GlassButton("Sačuvaj", { save() }, Modifier.weight(2f), strong = true, enabled = !recording)
        }
    }
}

/** A small red seal, the way a painting or a page of a sutra is signed. */
@Composable
private fun Seal() {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(Ink.seal.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
        Icon(NeboIcon.Cloud, Ink.paper, 22.dp)
    }
}

@Composable
fun KoanScreen(sc: Screen.Koan, host: SkyHost, go: (Screen) -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf(true) }
    var skipWait by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    var showComment by remember { mutableStateOf(false) }
    var round by remember { mutableStateOf(0) }
    val seen = remember { mutableSetOf<String>() }
    // a koan carried through the week comes back after each session instead of a new one
    val weekKoan = remember { app.library.byId(app.settings.activeWeekKoan()) }
    var carrying by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { host.showCircle = false; host.engine.paced = false; host.engine.walkOn = false }

    LaunchedEffect(round, skipWait) {
        // wait (up to two minutes) for the spoken note to become text, unless asked not to
        var entry = sc.entryId?.let { withContext(Dispatchers.IO) { app.db.entry(it) } }
        if (sc.entryId == null && sc.sessionId != null) {
            // no note after the session: fall back to the note from before it, if any
            entry = withContext(Dispatchers.IO) { app.db.entries().firstOrNull { it.sessionId == sc.sessionId } }
        }
        val canTranscribe = app.models.readyWhisper() != null
        var waited = 0
        while (!skipWait && entry != null && entry.audioPath != null && entry.transcript == null && canTranscribe && waited < 120) {
            waiting = true
            delay(1000); waited++
            entry = withContext(Dispatchers.IO) { app.db.entry(entry.id) }
        }
        waiting = false
        val p = if (weekKoan != null && round == 0 && !com.grungert.nebo.koan.Lexicon.soundsLikeDistress(entry?.words ?: ""))
            Pick(weekKoan, "Koan ove sedmice, ${app.settings.weekKoanDay()}. dan. Šta ti danas pokazuje?")
        else app.recommender.pick(entry?.words ?: "", entry?.moods ?: emptyList(), afterSession = sc.sessionId != null, exclude = seen)
        p.item?.let { seen += it.id }
        pick = p
        if (entry != null) withContext(Dispatchers.IO) { app.db.updateEntry(entry.copy(themes = p.themes)) }
    }

    fun keep(next: Screen = Screen.Home) {
        val p = pick
        scope.launch {
            withContext(Dispatchers.IO) {
                val id = p?.item?.id
                if (id != null) {
                    sc.sessionId?.let { app.db.setSessionKoan(it, id) }
                    sc.entryId?.let { e -> app.db.entry(e)?.let { app.db.updateEntry(it.copy(koanId = id)) } }
                }
            }
            go(next)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val p = pick
            when {
                p == null && waiting -> {
                    Spacer(Modifier.height(120.dp))
                    Text("Slušam šta si rekao…", style = HeadStyle, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text("Pretvaram snimak u tekst, da izaberem koan po tome.", style = SmallStyle,
                        modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = TextAlign.Center)
                    GlassButton("Ne čekaj", { skipWait = true }, Modifier.align(Alignment.CenterHorizontally))
                }
                p == null -> {
                    Spacer(Modifier.height(120.dp))
                    Text("Biram…", style = HeadStyle, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                p.distress -> Panel {
                    Text("Zvuči kao da ti je sada jako teško.", style = HeadStyle)
                    Text("Nisi sam u tome. Ako si u opasnosti, pozovi 112. Javi se nekome kome vjeruješ, " +
                        "ljekaru ili terapeutu, i reci mu ovo što si sada rekao.", style = BodyStyle)
                    Text("Koan može da sačeka.", style = SmallStyle)
                    GlassButton("Nazad na nebo", { go(Screen.Home) }, strong = true)
                }
                else -> {
                    val item = p.item
                    Spacer(Modifier.height(24.dp))
                    if (item == null) Text(p.reason, style = BodyStyle) else {
                        Text(when {
                            item.id == weekKoan?.id && round == 0 -> "KOAN OVE SEDMICE"
                            item.isKoan -> "KOAN ZA TEBE"
                            item.collection != null -> item.collection.uppercase()
                            else -> "PORUKA ZA TEBE"
                        }, style = LabelStyle)
                        PaperCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.title ?: "", style = PaperText.copy(fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f))
                                Seal()
                            }
                            Text(item.text, style = PaperText)
                            item.question?.let {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.inkOnPaper.copy(alpha = 0.15f)))
                                Text(it, style = PaperText.copy(fontStyle = FontStyle.Italic, color = Ink.seal, fontSize = 21.sp))
                            }
                            if (item.comment != null || item.verse != null) {
                                Text(if (showComment) "Sakrij Mumonov komentar" else "Mumonov komentar i stih",
                                    style = PaperSmall.copy(color = Ink.seal), modifier = Modifier.clickable { showComment = !showComment }.padding(vertical = 4.dp))
                                if (showComment) {
                                    item.comment?.let { Text(it, style = PaperText.copy(fontSize = 18.sp, lineHeight = 26.sp)) }
                                    item.verse?.let { Text(it, style = PaperText.copy(fontSize = 19.sp, fontStyle = FontStyle.Italic)) }
                                }
                            }
                            item.source?.let { Text(it, style = PaperSmall.copy(fontSize = 11.sp)) }
                        }
                        Text(p.reason, style = SmallStyle)
                    }
                    val isWeek = item != null && item.id == weekKoan?.id && round == 0
                    if (item != null && item.isKoan && !isWeek) GlassButton(
                        if (carrying) "Nosiš ga ove sedmice" else "Nosi ga ove sedmice",
                        { carrying = true; app.settings.carryKoan(item.id) },
                        Modifier.fillMaxWidth(), icon = NeboIcon.Week, enabled = !carrying,
                        sub = if (carrying) null else "Vraćaće se poslije svake sesije, sedam dana",
                    )
                    // a word back: what landed is offered more often, "not now" rests for two weeks
                    if (item != null && !isWeek) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton("Pogodio je", {
                            if (liked != item.id) { liked = item.id; scope.launch(Dispatchers.IO) { app.db.addFeedback(item.id, 1) } }
                        }, Modifier.weight(1f), icon = if (liked == item.id) NeboIcon.Check else NeboIcon.Star, enabled = liked != item.id)
                        GlassButton("Ne sada", {
                            scope.launch(Dispatchers.IO) { app.db.addFeedback(item.id, -1) }
                            pick = null; round++
                        }, Modifier.weight(1f))
                    }
                    if (liked == item?.id && item != null) Text("Zapamtio sam. Ovakvi će dolaziti češće.", style = SmallStyle)
                    // "Ne sada" already brings another one, so a plain "Drugi" is only needed without an item
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isWeek) GlassButton("Bilješke uz koan", { keep(Screen.KoanNotes(item!!.id)) })
                        else if (item == null) GlassButton("Drugi", { pick = null; round++ })
                        GlassButton("Sačuvaj i završi", { keep() }, Modifier.weight(1f), strong = true)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
