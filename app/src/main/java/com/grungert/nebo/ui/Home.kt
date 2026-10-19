package com.grungert.nebo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.SkyHost
import com.grungert.nebo.app
import com.grungert.nebo.esm.Esm
import com.grungert.nebo.sky.DayCycle
import com.grungert.nebo.sky.PATTERNS
import com.grungert.nebo.sky.patternById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SessionConfig(
    val type: String,          // breath | walk | thoughts | silence | points | hourglass | bodyscan
    val minutes: Int,          // 0 = open-ended
    val pattern: String,
    val intervalMinutes: Int,  // 0 none, -1 random
    val checkInBefore: Boolean,
    val checkInAfter: Boolean,
    val eyesClosed: Boolean = false,
    val ambient: Boolean = true,
) {
    val paced get() = type == "breath" || type == "walk"
    /** The planned length in seconds: the breathing space is always three minutes. */
    val plannedSeconds get() = if (type == "hourglass") HOURGLASS_SECONDS else minutes * 60
}

/** A kind of session; [short] is the label under its round button, where the full name would not fit. */
data class SessionType(val id: String, val name: String, val about: String, val icon: NeboIcon, val short: String = name)

val SESSION_TYPES = listOf(
    SessionType("breath", "Disanje", "Krug svjetla vodi udah i izdah.", NeboIcon.Breath),
    SessionType("walk", "Šetnja", "Livadom, korak po korak, uz dah.", NeboIcon.Steps),
    SessionType("thoughts", "Oblaci", "Primijetiš misao, dodirneš nebo, pustiš je.", NeboIcon.Cloud),
    SessionType("silence", "Tišina", "Samo nebo i zvono.", NeboIcon.Silence),
    SessionType("points", "4 tačke", "Misli, emocije, tijelo i nagoni, jedno po jedno, pa sve zajedno.", NeboIcon.Points),
    SessionType("hourglass", "Pješčani sat", "Tri minute: široko, pa samo dah, pa cijelo tijelo. Za usred dana.", NeboIcon.Hourglass, short = "3 minuta"),
    SessionType("bodyscan", "Skeniranje tijela", "Svjetlo putuje od stopala do tjemena. Dodirni gdje osjećaš napetost.", NeboIcon.Body, short = "Tijelo"),
)

/** Lengths offered for a kind of session; the breathing space has none to choose. */
private fun lengthsFor(type: String) = when (type) {
    "hourglass" -> emptyList()
    "bodyscan" -> listOf(5, 10, 15, 20, 0)
    else -> listOf(3, 5, 10, 15, 20, 0)
}

private fun greeting(h: Float) = when {
    h < 5 -> "Laku noć"
    h < 11 -> "Dobro jutro"
    h < 18 -> "Dobar dan"
    h < 22 -> "Dobro veče"
    else -> "Laku noć"
}

fun weekStart(): Long = Calendar.getInstance().apply {
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun android.content.Context.lastConfig(): SessionConfig {
    val s = app.settings
    // a length chosen for another kind may not suit this one (a three-minute body scan rushes)
    val lengths = lengthsFor(s.sessionType)
    val minutes = if (lengths.isEmpty() || s.minutes in lengths) s.minutes else 10
    return SessionConfig(s.sessionType, minutes, s.pattern, s.intervalMinutes, s.checkInBefore, s.checkInAfter, s.eyesClosed, s.ambient)
}

private fun describe(c: SessionConfig): String {
    val t = SESSION_TYPES.firstOrNull { it.id == c.type } ?: SESSION_TYPES[0]
    val length = c.plannedSeconds.let { if (it == 0) "slobodno" else "${it / 60} min" }
    return t.name + " · " + length + (if (c.paced) " · " + patternById(c.pattern).ratio else "")
}

/**
 * Today: a greeting, the week's koan if one is carried, and one big way in. The orb starts the last
 * session; the shapes below start a kind of session straight away; the microphone takes a note.
 */
@Composable
fun HomeScreen(host: SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val s = context.app.settings
    LaunchedEffect(Unit) {
        host.showCircle = false; host.dark = false; host.lake = false
        host.engine.paced = false
        host.engine.walkOn = false
        host.onTap = null; host.onPhase = null; host.onFrame = null
        host.day.mode = s.timeMode
        host.applyWeatherSettings(s.seasonMode, s.weatherMode)
    }
    var weekMinutes by remember { mutableIntStateOf(0) }
    var weekSessions by remember { mutableIntStateOf(0) }
    var weekNotes by remember { mutableIntStateOf(0) }
    var reviewHeld by remember { mutableIntStateOf(0) }   // notes and sessions in the week a review would look back on
    var silenced by remember { mutableStateOf(s.esmSilencedDay == Esm.today()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ss = context.app.db.sessions(weekStart())
            weekMinutes = ss.sumOf { it.actualSec } / 60
            weekSessions = ss.size
            weekNotes = context.app.db.entries(weekStart()).size
            val from = Esm.reviewWeekStart().timeInMillis
            val to = (Esm.reviewWeekStart().apply { add(Calendar.WEEK_OF_YEAR, 1) }).timeInMillis
            reviewHeld = context.app.db.entries(from).count { it.createdAt < to } + context.app.db.sessions(from).count { it.startedAt < to }
        }
    }
    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val reviewDue = (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY || dow == Calendar.MONDAY) &&
        s.weekReviewed != Esm.weekKey(Esm.reviewWeekStart()) && reviewHeld > 0
    val last = context.lastConfig()
    val weekKoan = remember { context.app.library.byId(s.activeWeekKoan()) }

    fun startType(id: String) {
        s.sessionType = id
        go(Screen.Session(context.lastConfig()))
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // a soft dusk under the controls, so they read even over a bright cloud
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color(0x40081830), 0.22f to Color.Transparent, 0.42f to Color.Transparent, 1f to Color(0xB3081830),
    )))
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .screenPadding().padding(top = 20.dp, bottom = 110.dp),
    ) {
        val date = SimpleDateFormat("EEEE, d. MMMM", Locale.forLanguageTag("sr-Latn-ME")).format(Date())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date.uppercase(), style = LabelStyle, modifier = Modifier.weight(1f))
            if (s.esmEnabled) Text(if (silenced) "Pozivi utišani" else "Utišaj pozive",
                style = SmallStyle.copy(fontWeight = FontWeight.SemiBold, color = if (silenced) Ink.accent else Ink.text, shadow = null),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ink.surfaceHigh).clickable {
                    silenced = !silenced; s.esmSilencedDay = if (silenced) Esm.today() else 0
                }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(greeting(DayCycle.clockHour()), style = TitleStyle.copy(fontSize = 40.sp, lineHeight = 44.sp))
        if (weekMinutes > 0 || weekNotes > 0) Text(listOfNotNull(
            if (weekMinutes > 0) "$weekMinutes min ove sedmice" else null,
            if (weekNotes > 0) "$weekNotes ${plural(weekNotes, "zapis", "zapisa", "zapisa")}" else null,
        ).joinToString(" · "), style = SmallStyle.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 4.dp))

        if (weekKoan != null) {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink.paper.copy(alpha = 0.94f))
                    .clickable(role = Role.Button) { go(Screen.KoanNotes(weekKoan.id)) }.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("KOAN SEDMICE · ${s.weekKoanDay()}. DAN", style = LabelStyle.copy(color = Ink.seal, shadow = null))
                weekKoan.question?.let { Text(it, style = PaperText.copy(fontSize = 19.sp, lineHeight = 25.sp, fontStyle = FontStyle.Italic)) }
            }
        }

        Spacer(Modifier.height(if (weekKoan != null) 90.dp else 200.dp))
        AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 8 }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (reviewDue) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.surface)
                        .clickable(role = Role.Button) { go(Screen.CheckIn("week")) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Ink.accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(NeboIcon.Week, Ink.accent, 22.dp) }
                    Column(Modifier.weight(1f)) {
                        Text("Sedmični pregled", style = BodyStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
                        Text("Šta se vraćalo, i šta ti primjećuješ", style = SmallStyle.copy(shadow = null))
                    }
                }

                // the main way in: one tap starts the session set up last time
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Ink.surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Orb { go(Screen.Session(last)) }
                    Column(Modifier.weight(1f)) {
                        Text("Kreni", style = HeadStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, shadow = null))
                        Text(describe(last), style = SmallStyle.copy(shadow = null))
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Ink.surfaceHigh).clickable(role = Role.Button, onClickLabel = "Podesi sesiju") { go(Screen.Setup) },
                        contentAlignment = Alignment.Center) { Icon(NeboIcon.Settings, Ink.text, 20.dp) }
                }

                // or one tap for a kind of session, four to a row, and the microphone for a note
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in SESSION_TYPES.chunked(4)) Row(Modifier.fillMaxWidth()) {
                        for (t in row) IconButton(t.icon, t.short, { startType(t.id) }, Modifier.weight(1f), selected = t.id == last.type, circle = 50.dp)
                        if (row.size < 4) IconButton(NeboIcon.Mic, "Zapiši", { go(Screen.CheckIn("note")) }, Modifier.weight(1f), circle = 50.dp)
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** The big round start button: a slowly breathing sphere of light. */
@Composable
private fun Orb(onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "orb").animateFloat(0f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "breath")
    Box(Modifier.size(72.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Kreni", onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(72.dp)) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Ink.accent.copy(alpha = 0.55f), Color.Transparent), center, r), r)
            drawCircle(Brush.radialGradient(listOf(Color.White, Ink.paper, Ink.accent), Offset(center.x - r * 0.2f, center.y - r * 0.25f), r * 0.9f), r * (0.62f + 0.08f * pulse))
        }
        Icon(NeboIcon.Play, Ink.inkOnPaper, 22.dp)
    }
}

/**
 * Setting up a session: the kind and the length up front, everything else folded under "Više opcija".
 * The sky behind previews the choice.
 */
@Composable
fun SetupScreen(host: SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val s = context.app.settings
    var type by remember { mutableStateOf(s.sessionType) }
    var minutes by remember { mutableIntStateOf(s.minutes) }
    var pattern by remember { mutableStateOf(s.pattern) }
    var timeMode by remember { mutableStateOf(s.timeMode) }
    var interval by remember { mutableIntStateOf(s.intervalMinutes) }
    var before by remember { mutableStateOf(s.checkInBefore) }
    var after by remember { mutableStateOf(s.checkInAfter) }
    var eyes by remember { mutableStateOf(s.eyesClosed) }
    var ambient by remember { mutableStateOf(s.ambient) }
    var volume by remember { mutableStateOf(s.ambientVolume) }
    var breathSnd by remember { mutableStateOf(s.breathSound) }
    var seasonMode by remember { mutableStateOf(s.seasonMode) }
    var weatherMode by remember { mutableStateOf(s.weatherMode) }
    var lakeMode by remember { mutableStateOf(s.lakeScene) }
    var more by remember { mutableStateOf(false) }
    val paced = type == "breath" || type == "walk"

    LaunchedEffect(type, pattern) {
        host.engine.walkOn = type == "walk"
        host.engine.paced = paced
        if (host.engine.pattern.id != pattern) host.engine.setPatternAndRestart(patternById(pattern))
        host.showCircle = paced
    }
    LaunchedEffect(timeMode) { host.day.mode = timeMode }
    LaunchedEffect(seasonMode, weatherMode) { host.applyWeatherSettings(seasonMode, weatherMode) }
    // the sky behind shows the place: the walk always goes through the meadow
    LaunchedEffect(lakeMode, type) { host.lake = lakeMode && type != "walk" }
    LaunchedEffect(type) { val ls = lengthsFor(type); if (ls.isNotEmpty() && minutes !in ls) minutes = if (10 in ls) 10 else ls.first() }

    fun start() {
        s.sessionType = type; s.minutes = minutes; s.pattern = pattern; s.timeMode = timeMode
        s.intervalMinutes = interval; s.checkInBefore = before; s.checkInAfter = after
        s.eyesClosed = eyes; s.ambient = ambient; s.ambientVolume = volume; s.breathSound = breathSnd
        s.seasonMode = seasonMode; s.weatherMode = weatherMode; s.lakeScene = lakeMode
        val cfg = SessionConfig(type, minutes, pattern, interval, before, after, eyes && paced, ambient)
        go(if (before) Screen.CheckIn("before", config = cfg) else Screen.Session(cfg))
    }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader("Nova sesija", { go(Screen.Home) })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in SESSION_TYPES.chunked(4)) Row(Modifier.fillMaxWidth()) {
                    for (t in row) IconButton(t.icon, t.short, { type = t.id }, Modifier.weight(1f), selected = type == t.id, circle = 52.dp)
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            SESSION_TYPES.firstOrNull { it.id == type }?.let { t ->
                Text(t.name, style = BodyStyle.copy(fontWeight = FontWeight.Bold))
                Text(t.about, style = SmallStyle)
            }
            Panel {
                SectionLabel("Trajanje")
                val lengths = lengthsFor(type)
                if (lengths.isEmpty()) Text("Uvijek tri minute: po jedna za svaki dio.", style = SmallStyle.copy(shadow = null))
                else ChipRow { for (m in lengths) Chip(if (m == 0) "slobodno" else "$m min", minutes == m, { minutes = m }) }
                if (paced) {
                    SectionLabel("Ritam disanja")
                    ChipRow { for (p in PATTERNS) Chip(p.ratio, pattern == p.id, { pattern = p.id }, sub = p.name) }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.surface).clickable { more = !more }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (more) "Manje opcija" else "Više opcija", style = BodyStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
                    if (!more) Text("Zvono, zvuk, mjesto i nebo, zatvorene oči, bilješke", style = SmallStyle.copy(shadow = null))
                }
                Text(if (more) "−" else "+", style = HeadStyle.copy(shadow = null))
            }
            AnimatedVisibility(more, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Panel {
                        SectionLabel("Zvono tokom sesije")
                        ChipRow {
                            for ((label, v) in listOf("bez" to 0, "1 min" to 1, "2" to 2, "3" to 3, "5" to 5, "10" to 10, "15" to 15, "nasumično" to -1)) {
                                Chip(label, interval == v, { interval = v })
                            }
                        }
                        if (interval == -1) Text("Zvono svjesnosti: oglasi se kad ga ne očekuješ, otprilike svakih 2–6 minuta, i vrati te ovdje.", style = SmallStyle.copy(shadow = null))
                        if (paced) SwitchRow("Zatvorene oči", eyes, { eyes = it }, sub = "Ekran se zatamni; dah vode vibracija i tihi tonovi.")
                    }
                    Panel {
                        SectionLabel("Zvuk")
                        SwitchRow("Zvuk prirode", ambient, { ambient = it }, sub = "Vjetar, ptice danju, cvrčci noću, kiša.")
                        if (paced) SwitchRow("Zvuk daha", breathSnd, { breathSnd = it }, sub = "Tihi šum vazduha: raste na udahu, spušta se na izdahu.")
                        if (ambient) {
                            val levels = listOf(0.35f, 0.6f, 0.9f)
                            Segmented(listOf("tiho", "srednje", "jače"), levels.indexOfFirst { kotlin.math.abs(volume - it) < 0.05f }, { volume = levels[it] })
                        }
                    }
                    Panel {
                        SectionLabel("Nebo")
                        Text("Mjesto", style = SmallStyle.copy(shadow = null))
                        if (type == "walk") Text("Šetnja uvijek ide livadom.", style = SmallStyle.copy(shadow = null))
                        else ChipRow {
                            Chip("livada", !lakeMode, { lakeMode = false })
                            Chip("jezero", lakeMode, { lakeMode = true })
                        }
                        Text("Doba dana", style = SmallStyle.copy(shadow = null))
                        ChipRow {
                            Chip("po satu", timeMode == "clock", { timeMode = "clock" })
                            Chip("ciklus", timeMode == "cycle", { timeMode = "cycle" })
                            for ((label, h) in TIME_OPTIONS) {
                                Chip(label, timeMode == h, { timeMode = h })
                            }
                        }
                        Text("Godišnje doba", style = SmallStyle.copy(shadow = null))
                        ChipRow {
                            Chip("po kalendaru", seasonMode == "auto", { seasonMode = "auto" })
                            for (sn in com.grungert.nebo.sky.Season.entries) Chip(sn.label, seasonMode == sn.name, { seasonMode = sn.name })
                        }
                        Text("Vrijeme", style = SmallStyle.copy(shadow = null))
                        ChipRow {
                            Chip("kako dođe", weatherMode == "auto", { weatherMode = "auto" })
                            for (w in com.grungert.nebo.sky.Weather.entries) Chip(w.label, weatherMode == w.name, { weatherMode = w.name })
                        }
                    }
                    Panel {
                        SectionLabel("Bilješka glasom")
                        SwitchRow("Prije: kako si sada?", before, { before = it })
                        SwitchRow("Poslije: šta primjećuješ?", after, { after = it }, sub = "Na osnovu toga dobijaš koan ili poruku.")
                    }
                }
            }
        }
        // the start button stays within reach at the bottom
        GlassButton("Počni", { start() }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().screenPadding().padding(bottom = 16.dp).fillMaxWidth(),
            strong = true, sub = describe(SessionConfig(type, minutes, pattern, interval, before, after, eyes, ambient)))
    }
}
