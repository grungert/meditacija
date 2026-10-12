package com.grungert.nebo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
    val type: String,          // breath | walk | thoughts | silence
    val minutes: Int,          // 0 = open-ended
    val pattern: String,
    val intervalMinutes: Int,  // 0 none, -1 random
    val checkInBefore: Boolean,
    val checkInAfter: Boolean,
    val eyesClosed: Boolean = false,
    val ambient: Boolean = true,
) {
    val paced get() = type == "breath" || type == "walk"
}

data class SessionType(val id: String, val name: String, val about: String, val icon: NeboIcon)

val SESSION_TYPES = listOf(
    SessionType("breath", "Disanje", "Krug svjetla vodi udah i izdah.", NeboIcon.Breath),
    SessionType("walk", "Šetnja", "Livadom, korak po korak, uz dah.", NeboIcon.Steps),
    SessionType("thoughts", "Oblaci misli", "Primijetiš misao, dodirneš nebo, pustiš je.", NeboIcon.Cloud),
    SessionType("silence", "Tišina", "Samo nebo i zvono.", NeboIcon.Silence),
)

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
    return SessionConfig(s.sessionType, s.minutes, s.pattern, s.intervalMinutes, s.checkInBefore, s.checkInAfter, s.eyesClosed, s.ambient)
}

@Composable
fun HomeScreen(host: SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val s = context.app.settings
    LaunchedEffect(Unit) {
        host.showCircle = false; host.dark = false
        host.engine.paced = false
        host.engine.walkOn = false
        host.onTap = null; host.onPhase = null; host.onFrame = null
        host.day.mode = s.timeMode
        host.applyWeatherSettings(s.seasonMode, s.weatherMode)
    }
    var weekMinutes by remember { mutableIntStateOf(0) }
    var weekSessions by remember { mutableIntStateOf(0) }
    var weekNotes by remember { mutableIntStateOf(0) }
    var silenced by remember { mutableStateOf(s.esmSilencedDay == Esm.today()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ss = context.app.db.sessions(weekStart())
            weekMinutes = ss.sumOf { it.actualSec } / 60
            weekSessions = ss.size
            weekNotes = context.app.db.entries(weekStart()).size
        }
    }
    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val reviewDue = (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY || dow == Calendar.MONDAY) &&
        s.weekReviewed != Esm.weekKey() && (weekNotes + weekSessions) > 0
    val last = context.lastConfig()
    val lastType = SESSION_TYPES.firstOrNull { it.id == last.type } ?: SESSION_TYPES[0]
    val lastSub = lastType.name + " · " + (if (last.minutes == 0) "slobodno" else "${last.minutes} min") +
        (if (last.paced) " · " + patternById(last.pattern).ratio else "")

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // a soft dusk under the menu, so the controls read even over a bright cloud
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color(0x33081830), 0.25f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color(0xA6081830),
    )))
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).screenPadding().padding(vertical = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        val date = SimpleDateFormat("EEEE, d. MMMM", Locale.forLanguageTag("sr-Latn-ME")).format(Date())
        Text(date.uppercase(), style = LabelStyle)
        Spacer(Modifier.height(6.dp))
        Text(greeting(DayCycle.clockHour()), style = TitleStyle.copy(fontSize = 46.sp, lineHeight = 50.sp))
        if (weekMinutes > 0 || weekNotes > 0) {
            Spacer(Modifier.height(4.dp))
            Text(listOfNotNull(
                if (weekMinutes > 0) "$weekMinutes min u tišini ove sedmice" else null,
                if (weekNotes > 0) "$weekNotes ${plural(weekNotes, "zapis", "zapisa", "zapisa")}" else null,
            ).joinToString(" · "), style = SmallStyle.copy(fontSize = 15.sp))
        }

        val weekKoan = remember { context.app.library.byId(s.activeWeekKoan()) }
        if (weekKoan != null) {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp, 22.dp, 6.dp, 22.dp))
                    .background(Brush.verticalGradient(listOf(Ink.paper.copy(alpha = 0.92f), Ink.paperDeep.copy(alpha = 0.92f))))
                    .clickable(role = Role.Button) { go(Screen.KoanNotes(weekKoan.id)) }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("KOAN SEDMICE · ${s.weekKoanDay()}. DAN", style = LabelStyle.copy(color = Ink.seal, shadow = null))
                Text(weekKoan.title ?: "", style = PaperText.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
                weekKoan.question?.let { Text(it, style = PaperText.copy(fontSize = 18.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) }
            }
        }
        Spacer(Modifier.height(if (weekKoan != null) 120.dp else 260.dp))
        AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 6 }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (reviewDue) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0x66FFE3B0), Color(0x33FFFFFF))))
                        .border(BorderStroke(1.dp, Color(0x80FFE3B0)), RoundedCornerShape(22.dp))
                        .clickable(role = Role.Button) { go(Screen.CheckIn("week")) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(NeboIcon.Week, Ink.text, 26.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Sedmični pregled", style = BodyStyle.copy(fontWeight = FontWeight.Bold))
                        Text("Šta se vraćalo ove sedmice, i šta ti primjećuješ.", style = SmallStyle)
                    }
                }
                GlassButton("Započni", { go(Screen.Session(last)) }, Modifier.fillMaxWidth(), strong = true, sub = lastSub)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("Nova sesija", { go(Screen.Setup) }, Modifier.weight(1f), icon = NeboIcon.Settings)
                    GlassButton("Zapiši", { go(Screen.CheckIn("note")) }, Modifier.weight(1f), icon = NeboIcon.Mic)
                }
                if (s.esmEnabled) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Chip(if (silenced) "Pozivi utišani danas" else "Utišaj pozive danas", silenced, {
                        silenced = !silenced
                        s.esmSilencedDay = if (silenced) Esm.today() else 0
                    })
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(NeboIcon.Journal, "Dnevnik", { go(Screen.Journal) })
                    IconButton(NeboIcon.Stats, "Pregled", { go(Screen.Stats) })
                    IconButton(NeboIcon.Settings, "Postavke", { go(Screen.Settings) })
                }
            }
        }
    }
}

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
    var seasonMode by remember { mutableStateOf(s.seasonMode) }
    var weatherMode by remember { mutableStateOf(s.weatherMode) }
    val paced = type == "breath" || type == "walk"

    LaunchedEffect(type, pattern) {
        // preview the choice on the sky behind the panel
        host.engine.walkOn = type == "walk"
        host.engine.paced = paced
        if (host.engine.pattern.id != pattern) host.engine.setPatternAndRestart(patternById(pattern))
        host.showCircle = paced
    }
    LaunchedEffect(timeMode) { host.day.mode = timeMode }
    LaunchedEffect(seasonMode, weatherMode) { host.applyWeatherSettings(seasonMode, weatherMode) }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader("Nova sesija", { go(Screen.Home) })
            // session types as a 2 x 2 grid of cards
            for (rowTypes in SESSION_TYPES.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (t in rowTypes) TypeCard(t, type == t.id, { type = t.id }, Modifier.weight(1f))
                }
            }
            Panel {
                SectionLabel("Trajanje")
                ChipRow { for (m in listOf(3, 5, 10, 15, 20, 0)) Chip(if (m == 0) "slobodno" else "$m min", minutes == m, { minutes = m }) }
                Text("Tiho zvono tokom sesije", style = SmallStyle)
                ChipRow {
                    for ((label, v) in listOf("bez" to 0, "1 min" to 1, "2 min" to 2, "3 min" to 3, "5 min" to 5, "10 min" to 10, "15 min" to 15, "nasumično" to -1)) {
                        Chip(label, interval == v, { interval = v })
                    }
                }
                if (interval == -1) Text("Zvono svjesnosti: oglasi se kad ga ne očekuješ, otprilike svakih 2–6 minuta, i vrati te ovdje.", style = SmallStyle)
            }
            if (paced) Panel {
                SectionLabel("Ritam disanja")
                ChipRow { for (p in PATTERNS) Chip(p.ratio, pattern == p.id, { pattern = p.id }, sub = p.name) }
                SwitchRow("Zatvorene oči", eyes, { eyes = it },
                    sub = "Ekran se zatamni; dah vode blaga vibracija i tihi tonovi. Dodir vraća svjetlo.")
            }
            Panel {
                SectionLabel("Zvuk i svjetlo")
                SwitchRow("Zvuk prirode", ambient, { ambient = it }, sub = "Vjetar kroz travu, ptice danju, cvrčci noću.")
                if (ambient) ChipRow {
                    for ((label, v) in listOf("tiho" to 0.35f, "srednje" to 0.6f, "jače" to 0.9f)) Chip(label, kotlin.math.abs(volume - v) < 0.05f, { volume = v })
                }
                Text("Doba dana", style = SmallStyle)
                ChipRow {
                    Chip("po satu", timeMode == "clock", { timeMode = "clock" })
                    Chip("ciklus dana", timeMode == "cycle", { timeMode = "cycle" })
                    for ((label, h) in listOf("zora" to "6.2", "dan" to "12.5", "zalazak" to "18.9", "sumrak" to "20.3", "noć" to "23.5")) {
                        Chip(label, timeMode == h, { timeMode = h })
                    }
                }
                Text("Godišnje doba", style = SmallStyle)
                ChipRow {
                    Chip("po kalendaru", seasonMode == "auto", { seasonMode = "auto" })
                    for (sn in com.grungert.nebo.sky.Season.entries) Chip(sn.label, seasonMode == sn.name, { seasonMode = sn.name })
                }
                Text("Vrijeme", style = SmallStyle)
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
            GlassButton("Počni", {
                s.sessionType = type; s.minutes = minutes; s.pattern = pattern; s.timeMode = timeMode
                s.intervalMinutes = interval; s.checkInBefore = before; s.checkInAfter = after
                s.eyesClosed = eyes; s.ambient = ambient; s.ambientVolume = volume
                s.seasonMode = seasonMode; s.weatherMode = weatherMode
                val cfg = SessionConfig(type, minutes, pattern, interval, before, after, eyes && paced, ambient)
                go(if (before) Screen.CheckIn("before", config = cfg) else Screen.Session(cfg))
            }, Modifier.fillMaxWidth(), strong = true)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TypeCard(t: SessionType, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.clip(shape)
            .background(if (selected) Brush.verticalGradient(listOf(Ink.paper, Ink.paperDeep)) else Brush.verticalGradient(listOf(Color(0x5A1B305E), Color(0x8C0C1A38))))
            .border(BorderStroke(1.dp, if (selected) Ink.paper else Color(0x59FFFFFF)), shape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val ink = if (selected) Ink.inkOnPaper else Ink.text
        Icon(t.icon, ink, 28.dp)
        Text(t.name, style = HeadStyle.copy(fontSize = 22.sp, color = ink, shadow = if (selected) null else HeadStyle.shadow))
        Text(t.about, style = SmallStyle.copy(color = ink.copy(alpha = 0.8f), shadow = if (selected) null else SmallStyle.shadow),
            modifier = Modifier.height(38.dp))
    }
}

