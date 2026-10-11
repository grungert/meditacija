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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.SkyHost
import com.grungert.nebo.app
import com.grungert.nebo.sky.DayCycle
import com.grungert.nebo.sky.PATTERNS
import com.grungert.nebo.sky.patternById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class SessionConfig(
    val type: String,          // breath | walk | thoughts | silence
    val minutes: Int,          // 0 = open-ended
    val pattern: String,
    val intervalBell: Boolean,
    val checkInBefore: Boolean,
    val checkInAfter: Boolean,
) {
    val paced get() = type == "breath" || type == "walk"
}

data class SessionType(val id: String, val name: String, val about: String)

val SESSION_TYPES = listOf(
    SessionType("breath", "Disanje", "Krug svjetla vodi udah i izdah."),
    SessionType("walk", "Šetnja", "Livadom, korak po korak, uz dah."),
    SessionType("thoughts", "Oblaci misli", "Kad primijetiš misao, dodirni nebo i pusti je da otplovi."),
    SessionType("silence", "Tišina", "Samo nebo i zvono. Bez vođenja."),
)

private fun greeting(h: Float) = when {
    h < 5 -> "Laku noć"
    h < 11 -> "Dobro jutro"
    h < 18 -> "Dobar dan"
    h < 22 -> "Dobro veče"
    else -> "Laku noć"
}

@Composable
fun HomeScreen(host: SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        host.showCircle = false
        host.engine.paced = false
        host.engine.walkOn = false
        host.onTap = null; host.onPhase = null; host.onFrame = null
    }
    var weekMinutes by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        val weekStart = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        weekMinutes = withContext(Dispatchers.IO) { context.app.db.sessions(weekStart).sumOf { it.actualSec } / 60 }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().screenPadding().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Nebo", style = TitleStyle.copy(fontSize = 52.sp))
        Text(greeting(DayCycle.clockHour()), style = SmallStyle.copy(fontSize = 16.sp))
        Spacer(Modifier.weight(1f))
        GlassButton("Započni", { go(Screen.Setup) }, Modifier.fillMaxWidth(0.6f), strong = true)
        Spacer(Modifier.height(14.dp))
        GlassButton("Zapiši zapažanje", { go(Screen.CheckIn("note")) }, Modifier.fillMaxWidth(0.6f))
        Spacer(Modifier.height(28.dp))
        if (weekMinutes > 0) Text("Ove sedmice: $weekMinutes min u tišini", style = SmallStyle)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Dnevnik", { go(Screen.Journal) })
            GlassButton("Pregled", { go(Screen.Stats) })
            GlassButton("Postavke", { go(Screen.Settings) })
        }
    }
}

@Composable
fun SetupScreen(host: SkyHost, go: (Screen) -> Unit) {
    val s = LocalContext.current.app.settings
    var type by remember { mutableStateOf(s.sessionType) }
    var minutes by remember { mutableIntStateOf(s.minutes) }
    var pattern by remember { mutableStateOf(s.pattern) }
    var timeMode by remember { mutableStateOf(s.timeMode) }
    var intervalBell by remember { mutableStateOf(s.intervalBell) }
    var before by remember { mutableStateOf(s.checkInBefore) }
    var after by remember { mutableStateOf(s.checkInAfter) }

    LaunchedEffect(type, pattern) {
        // preview the choice on the sky behind the panel
        host.engine.walkOn = type == "walk"
        host.engine.paced = type == "breath" || type == "walk"
        if (host.engine.pattern.id != pattern) host.engine.setPatternAndRestart(patternById(pattern))
        host.showCircle = type == "breath" || type == "walk"
    }
    LaunchedEffect(timeMode) { host.day.mode = timeMode }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackButton { go(Screen.Home) }
                Text("Nova sesija", style = HeadStyle)
            }
            Panel {
                SectionLabel("Vrsta")
                for (t in SESSION_TYPES) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip(t.name, type == t.id, { type = t.id })
                        Text(t.about, style = SmallStyle, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    }
                }
            }
            Panel {
                SectionLabel("Trajanje")
                ChipRow {
                    for (m in listOf(3, 5, 10, 15, 20, 0)) Chip(if (m == 0) "slobodno" else "$m min", minutes == m, { minutes = m })
                }
                SwitchRow("Tiho zvono svakih 5 minuta", intervalBell, { intervalBell = it })
            }
            if (type == "breath" || type == "walk") Panel {
                SectionLabel("Ritam disanja")
                ChipRow { for (p in PATTERNS) Chip(p.ratio, pattern == p.id, { pattern = p.id }, sub = p.name) }
            }
            Panel {
                SectionLabel("Doba dana")
                ChipRow {
                    Chip("po satu", timeMode == "clock", { timeMode = "clock" })
                    Chip("ciklus dana", timeMode == "cycle", { timeMode = "cycle" })
                    for ((label, h) in listOf("zora" to "6.2", "dan" to "12.5", "zalazak" to "18.9", "sumrak" to "20.3", "noć" to "23.5")) {
                        Chip(label, timeMode == h, { timeMode = h })
                    }
                }
            }
            Panel {
                SectionLabel("Bilješka glasom")
                SwitchRow("Prije: kako si sada?", before, { before = it })
                SwitchRow("Poslije: šta primjećuješ?", after, { after = it }, sub = "Na osnovu toga dobijaš koan ili poruku.")
            }
            GlassButton("Počni", {
                s.sessionType = type; s.minutes = minutes; s.pattern = pattern; s.timeMode = timeMode
                s.intervalBell = intervalBell; s.checkInBefore = before; s.checkInAfter = after
                val cfg = SessionConfig(type, minutes, pattern, intervalBell, before, after)
                go(if (before) Screen.CheckIn("before", config = cfg) else Screen.Session(cfg))
            }, Modifier.fillMaxWidth(), strong = true)
            Spacer(Modifier.height(24.dp))
        }
    }
}
