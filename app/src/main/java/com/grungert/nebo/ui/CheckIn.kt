package com.grungert.nebo.ui

import android.Manifest
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

private const val MAX_SECONDS = 30

@Composable
fun CheckInScreen(sc: Screen.CheckIn, host: SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val recorder = remember { Recorder() }
    val player = remember { NotePlayer() }
    var recording by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableFloatStateOf(0f) }
    var audio by remember { mutableStateOf<File?>(null) }
    var text by remember { mutableStateOf("") }
    val moods = remember { mutableStateListOf<String>() }
    var askedMic by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { host.showCircle = false; host.engine.paced = false }

    val (title, prompt) = when (sc.kind) {
        "before" -> "Kako si sada?" to "Prije nego što počneš: šta je u tebi?"
        "after" -> "Šta primjećuješ?" to "Šta se pojavilo dok si sjedio? Riječ ili dvije su dovoljne."
        "esm" -> "Kako si sada?" to "Pritisni i reci šta je u tebi. Nema pogrešnog odgovora."
        else -> "Zapažanje" to "Šta primjećuješ u sebi upravo sada?"
    }

    fun startRecording() {
        val f = File(context.filesDir, "audio/${System.currentTimeMillis()}.wav")
        recording = true; seconds = 0f
        scope.launch {
            val dur = recorder.record(f, MAX_SECONDS) { level = it }
            recording = false
            if (dur > 0.6f) { audio?.delete(); audio = f } else f.delete()
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
            else -> go(Screen.Koan(null, entryId))
        }
    }

    fun save() {
        player.stop()
        if (recording) recorder.stop()
        scope.launch {
            val entry = Entry(createdAt = System.currentTimeMillis(), kind = sc.kind, sessionId = sc.sessionId, promptId = sc.promptId,
                audioPath = audio?.absolutePath, text = text.trim().ifBlank { null }, moods = moods.toList())
            val id = withContext(Dispatchers.IO) {
                val id = app.db.insertEntry(entry)
                sc.promptId?.let { app.db.markPromptAnswered(it, id) }
                id
            }
            if (audio != null) app.transcriber.drain()
            next(id)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (sc.kind == "note" || sc.kind == "esm") BackButton { player.stop(); recorder.stop(); go(Screen.Home) }
            }
            Text(title, style = TitleStyle)
            Text(prompt, style = BodyStyle.copy(color = Ink.soft), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))

            // the record button: tap to start, tap again to stop; the ring shows level and time left
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                Canvas(Modifier.size(150.dp)) {
                    val r = size.minDimension / 2
                    drawCircle(Color.White.copy(alpha = 0.10f + 0.25f * level), radius = r * (0.78f + 0.2f * level))
                    if (recording) drawArc(Color.White, -90f, 360f * (seconds / MAX_SECONDS).coerceAtMost(1f), false,
                        style = Stroke(width = 4.dp.toPx()), topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()))
                }
                Box(
                    Modifier.size(104.dp).clip(CircleShape).background(if (recording) Color(0xFFE8F0FF) else Ink.glassStrong)
                        .border(BorderStroke(1.5.dp, Color.White), CircleShape)
                        .clickable(role = Role.Button, onClickLabel = if (recording) "Zaustavi snimanje" else "Snimi") {
                            when {
                                recording -> recorder.stop()
                                micAllowed -> startRecording()
                                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (recording) "Stop" else if (audio != null) "Ponovo" else "Govori",
                        style = BodyStyle.copy(color = if (recording) Color(0xFF1F3B6E) else Color.White, fontSize = 17.sp))
                }
            }
            Text(
                when {
                    recording -> "Snimam… %d s".format(seconds.toInt())
                    audio != null -> "Snimljeno. Tekst stiže uskoro."
                    askedMic && !micAllowed -> "Bez mikrofona možeš i da napišeš."
                    else -> "Najviše $MAX_SECONDS sekundi"
                }, style = SmallStyle,
            )
            if (audio != null && !recording) GlassButton("Preslušaj", { player.play(audio!!.absolutePath) {} })
            if (audio != null && app.models.readyWhisper() == null) {
                Text("Whisper model još nije preuzet, pa snimak čeka. Preuzmi ga u Postavkama.", style = SmallStyle, textAlign = TextAlign.Center)
            }

            Panel {
                SectionLabel("Kako je u tebi (po želji)")
                ChipRow { for ((m, _) in MOODS) Chip(m, m in moods, { if (m in moods) moods.remove(m) else moods.add(m) }) }
                SectionLabel("Ili napiši")
                BasicTextField(
                    text, { text = it },
                    textStyle = BodyStyle,
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.glass).padding(12.dp).height(80.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton("Preskoči", {
                    player.stop(); recorder.stop(); audio?.delete()
                    next(null)
                })
                GlassButton("Sačuvaj", { save() }, strong = true)
            }
            Spacer(Modifier.height(24.dp))
        }
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
        val p = app.recommender.pick(entry?.words ?: "", entry?.moods ?: emptyList(), afterSession = sc.sessionId != null, exclude = seen)
        p.item?.let { seen += it.id }
        pick = p
        if (entry != null) withContext(Dispatchers.IO) { app.db.updateEntry(entry.copy(themes = p.themes)) }
    }

    fun keep() {
        val p = pick
        scope.launch {
            withContext(Dispatchers.IO) {
                val id = p?.item?.id
                if (id != null) {
                    sc.sessionId?.let { app.db.setSessionKoan(it, id) }
                    sc.entryId?.let { e -> app.db.entry(e)?.let { app.db.updateEntry(it.copy(koanId = id)) } }
                }
            }
            go(Screen.Home)
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
                        Text(if (item.isKoan) "KOAN" else "PORUKA", style = LabelStyle)
                        item.title?.let { Text(it, style = HeadStyle.copy(fontSize = 28.sp)) }
                        Panel {
                            Text(item.text, style = BodyStyle.copy(fontSize = 17.sp, lineHeight = 27.sp))
                            item.question?.let {
                                Text(it, style = BodyStyle.copy(fontStyle = FontStyle.Italic, color = Ink.accent, fontFamily = Serif, fontSize = 18.sp))
                            }
                        }
                        if (item.comment != null || item.verse != null) {
                            Text(if (showComment) "Sakrij komentar" else "Mumonov komentar i stih",
                                style = SmallStyle.copy(color = Ink.text), modifier = Modifier.clickable { showComment = !showComment }.padding(4.dp))
                            if (showComment) Panel {
                                item.comment?.let { Text(it, style = BodyStyle.copy(fontSize = 15.sp)) }
                                item.verse?.let { Text(it, style = BodyStyle.copy(fontFamily = Serif, fontStyle = FontStyle.Italic)) }
                            }
                        }
                        Text(p.reason, style = SmallStyle)
                        item.source?.let { Text(it, style = SmallStyle.copy(fontSize = 11.sp, color = Ink.faint)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton("Drugi", { pick = null; round++ })
                        GlassButton("Sačuvaj i završi", { keep() }, strong = true)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
