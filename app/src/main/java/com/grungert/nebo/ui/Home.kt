package com.grungert.nebo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.grungert.nebo.koan.Links
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusRequester
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
    /** The note written just before, to be joined to the session once it starts. */
    val beforeEntryId: Long? = null,
) {
    val paced get() = type == "breath" || type == "walk"
    /** The planned length in seconds: the breathing space is always three minutes. */
    val plannedSeconds get() = when (type) { "hourglass" -> HOURGLASS_SECONDS; "rain" -> 0; else -> minutes * 60 }
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
    SessionType("urge", "Talas", "Kad te nešto vuče: posmatraš poriv kako raste i opada, a ne ideš za njim.", NeboIcon.Wave),
    SessionType("rain", "RAIN", "Za tešku emociju: prepoznaj je, dozvoli joj, nađi je u tijelu, budi blag prema sebi.", NeboIcon.Heart),
)

/** Lengths offered for a kind of session; the breathing space has none to choose. */
private fun lengthsFor(type: String) = when (type) {
    "hourglass", "rain" -> emptyList()   // three minutes, and as long as the steps take
    "bodyscan" -> listOf(5, 10, 15, 20, 0)
    "urge" -> listOf(3, 5, 10)   // a wave has a beginning, a crest and an end
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
    // eyes closed is kept for the paced kinds only: elsewhere no breath would guide the dark
    val paced = s.sessionType == "breath" || s.sessionType == "walk"
    return SessionConfig(s.sessionType, minutes, s.pattern, s.intervalMinutes, s.checkInBefore, s.checkInAfter, s.eyesClosed && paced, s.ambient)
}

private fun describe(c: SessionConfig): String {
    val t = SESSION_TYPES.firstOrNull { it.id == c.type } ?: SESSION_TYPES[0]
    val length = c.plannedSeconds.let { if (it == 0) "slobodno" else "${it / 60} min" }
    return t.name + " · " + length + (if (c.paced) " · " + patternById(c.pattern).ratio else "")
}

/** The three ways in, each in the colour of its point: to calm down, to look inside, and for when it is hard. */
private class SessionGroup(val name: String, val color: Color, val types: List<String>)
private val GROUPS = listOf(
    SessionGroup("Smiri se", Color(0xFF8ED6C0), listOf("breath", "hourglass", "silence", "walk")),
    SessionGroup("Pogledaj unutra", Color(0xFFB9AEF2), listOf("thoughts", "points", "bodyscan")),
    SessionGroup("Kad je teško", Color(0xFFF2A88E), listOf("urge", "rain", "hourglass")),
)

/** What a card says under the name: the length it will likely have. */
private fun lengthOf(type: String, last: SessionConfig) = when {
    type == "hourglass" -> "pješčani sat"
    type == "rain" -> "koliko treba"
    type == last.type -> if (last.minutes == 0) "slobodno" else "${last.minutes} min"
    type == "urge" -> "5 min"
    type == "breath" || type == "points" -> "5 min"
    else -> "10 min"
}

/** The part of the day for "Za sada", in words for the label. */
private val PART_LABEL = listOf("UJUTRU", "USRED DANA", "UVEČE", "KASNO UVEČE")

/** The suggestion for now: a session, why it, and the label above it. */
class Now(val type: String, val why: String, val basis: String)

/** "Poslije Disanja", "Disanje": a kind of session in a sentence. */
private val SESSION_SUBJECT = mapOf(
    "breath" to "Disanje", "walk" to "Šetnja", "thoughts" to "Oblaci", "silence" to "Tišina", "points" to "4 tačke",
    "hourglass" to "Pješčani sat", "bodyscan" to "Skeniranje tijela", "urge" to "Talas", "rain" to "RAIN", "fifty" to "50-50",
)

/**
 * What to do now, in this order: something that keeps coming at this time of day in the notes, met with what has
 * been shown to help (from the notes before and after sessions) or else a practice that fits it; else what has most
 * often helped; else what suits the hour. The person's own evidence comes before any rule of thumb.
 */
fun suggestNow(entries: List<com.grungert.nebo.data.Entry>, finding: com.grungert.nebo.koan.WhatHelps.Finding?, part: Int): Now {
    val helps = finding?.let { com.grungert.nebo.koan.WhatHelps.suggestion(it) }
    val helped = finding?.kinds?.firstOrNull { it.type == helps }
    // when one kind clearly helps more at this hour, that one
    val atThisHour = finding?.partFor?.takeIf { it.second == part }?.first
    val best = atThisHour ?: helps
    val proof = finding?.kinds?.firstOrNull { it.type == best }?.let { " ${SESSION_SUBJECT[it.type] ?: it.type} ti pomaže ${it.lighter} od ${it.count} puta." } ?: ""
    val recent = Links.seen(entries.filter { it.createdAt >= System.currentTimeMillis() - 60 * 86_400_000L })
    val web = Links.webOf(recent.map { it.first })
    val heavy = setOf("nemirno", "zabrinuto", "tužno", "ljuto", "umorno", "zbunjeno")
    val now = web.counts.filter { (t, n) -> n >= 3 && (t.point != com.grungert.nebo.koan.FourPointsReader.EMOTIONS || t.label in heavy) }.keys
        .map { Links.focusOf(recent, it) }.filter { it.part == part }.maxByOrNull { it.notes }
    if (now != null) {
        val type = best ?: practiceFor(now.thing).first
        return Now(type, "${Links.PART_WHEN[part].replaceFirstChar { it.uppercase() }} ti često dođe „${now.thing.label.trim('„', '“')}“." + proof,
            if (best != null) "iz tvojih bilješki i iskustva" else "iz tvojih bilješki")
    }
    if (best != null && helped != null) return Now(best, proof.trim().ifEmpty { "Najčešće ti pomaže." }, "iz tvog iskustva")
    return when (part) {
        0 -> Now("breath", "Nekoliko mirnih daha za početak dana.", "po dobu dana")
        1 -> Now("hourglass", "Tri minute da se vratiš sebi.", "po dobu dana")
        2 -> Now("bodyscan", "Da dan izađe iz tijela.", "po dobu dana")
        else -> Now("bodyscan", "Od stopala do tjemena, pa u san.", "po dobu dana")
    }
}

/**
 * Today: a greeting over the sky, the week in two chips, and below one suggestion for now that says why, then the
 * kinds of session in three groups. A card tells what it is on a touch and starts when held (a ring fills around it),
 * so nothing begins by accident. The microphone for a note waits in the bar below.
 */
@Composable
fun HomeScreen(host: SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val s = context.app.settings
    var info by remember { mutableStateOf<String?>(null) }   // the kind of session whose card is open
    var holdLearned by remember { mutableStateOf(s.holdLearned) }
    LaunchedEffect(info) { host.sheetOpen = info != null }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { host.sheetOpen = false } }
    val homeView = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(Unit) {
        // home takes the sky over: the sit that is fading out behind it then leaves the sky as set here (the lake stays)
        host.owner = homeView
        context.app.sitting = false; homeView.keepScreenOn = false
        host.ambience.stop(); host.breath.stop(); host.engine.running = true
        host.showCircle = false; host.dark = false; host.lake = s.lakeScene
        host.engine.paced = false
        host.engine.walkOn = false
        host.onTap = null; host.onPhase = null; host.onFrame = null
        host.day.mode = s.timeMode
        host.applyWeatherSettings(s.seasonMode, s.weatherMode)
    }
    var weekMinutes by remember { mutableIntStateOf(0) }
    var weekNotes by remember { mutableIntStateOf(0) }
    var reviewHeld by remember { mutableIntStateOf(0) }   // notes and sessions in the week a review would look back on
    var eveningDue by remember { mutableStateOf(false) }
    var followUp by remember { mutableStateOf<com.grungert.nebo.data.Entry?>(null) }   // yesterday's hard note, to come back to
    var silenced by remember { mutableStateOf(s.esmSilencedDay == Esm.today()) }
    val part = Links.part(System.currentTimeMillis())
    var now by remember { mutableStateOf<Now?>(null) }
    var group by remember { mutableIntStateOf(if (part >= 2) 1 else 0) }
    var groupChosen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ss = context.app.db.sessions(weekStart())
            weekMinutes = ss.sumOf { it.actualSec } / 60
            weekNotes = context.app.db.entries(weekStart()).size
            val from = Esm.reviewWeekStart().timeInMillis
            val to = (Esm.reviewWeekStart().apply { add(Calendar.WEEK_OF_YEAR, 1) }).timeInMillis
            reviewHeld = context.app.db.entries(from).count { it.createdAt < to } + context.app.db.sessions(from).count { it.startedAt < to }
            // the evening look back: from an hour before its time (or eight in the evening), when the day held something
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val start = if (s.eveningReview) s.eveningHour - 1 else 20
            followUp = Esm.followUpNote(context)
            val all = context.app.db.entries()
            now = suggestNow(all, com.grungert.nebo.koan.WhatHelps.find(context.app.db.sessions(), all), part)

            val today = Esm.midnight().timeInMillis
            eveningDue = hour >= start && !Esm.eveningDone(context) &&
                (s.eveningReview || context.app.db.sessions(today).isNotEmpty() || context.app.db.entries(today).isNotEmpty())
        }
    }
    LaunchedEffect(now) {
        val n = now ?: return@LaunchedEffect
        if (!groupChosen) GROUPS.indexOfFirst { n.type in it.types }.takeIf { it >= 0 }?.let { group = it }
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

    // a soft shade under the greeting, so white type reads even over a bright cloud; by night a dusk under the controls too
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color(0x40081830), 0.22f to Color.Transparent, 0.5f to Color.Transparent,
        1f to (if (Ink.day) Color(0x33F4F1EA) else Color(0xB3081830)),
    )))
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(top = 20.dp, bottom = 110.dp),
    ) {
        val date = ijekavian(SimpleDateFormat("EEEE, d. MMMM", Locale.forLanguageTag("sr-Latn-ME")).format(Date()))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(date.uppercase(), style = LabelStyle.copy(color = Ink.onSky.copy(alpha = 0.92f), shadow = skyShadow, letterSpacing = 1.8.sp), modifier = Modifier.weight(1f))
            if (s.esmEnabled) Text(if (silenced) "Pozivi utišani" else "Utišaj pozive",
                style = SmallStyle.copy(fontWeight = FontWeight.Bold, color = if (silenced) Ink.ink else Color.White, shadow = null),
                modifier = Modifier.tap(shape = RoundedCornerShape(14.dp)) { silenced = !silenced; s.esmSilencedDay = if (silenced) Esm.today() else 0 }
                    .background(if (silenced) Ink.accent else Color(0x33FFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(greeting(DayCycle.clockHour()), style = TitleStyle.copy(fontSize = 42.sp, lineHeight = 46.sp, color = Ink.onSky, shadow = skyShadow), modifier = Modifier.padding(horizontal = 4.dp))
        var selfNote by remember { mutableStateOf(s.selfNote) }
        var writingNote by remember { mutableStateOf(false) }
        Row(Modifier.padding(top = 10.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (weekMinutes > 0) SkyChip("$weekMinutes min ove sedmice")
            if (weekNotes > 0) SkyChip("$weekNotes ${plural(weekNotes, "zapis", "zapisa", "zapisa")}")
            if (selfNote.isEmpty() && !writingNote) SkyChip("+ Podsjetnik", Modifier.tap("Napiši sebi podsjetnik", shape = RoundedCornerShape(16.dp)) { writingNote = true })
        }
        SelfNote(selfNote, writingNote, onWrite = { writingNote = true }) { written ->
            selfNote = written.trim(); s.selfNote = selfNote; writingNote = false
        }

        weekKoan?.let { weekKoan ->
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().tap(shape = RoundedCornerShape(24.dp)) { go(Screen.KoanNotes(weekKoan.id)) }.background(Ink.paper.copy(alpha = 0.94f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("KOAN SEDMICE · ${s.weekKoanDay()}. DAN", style = LabelStyle.copy(color = Ink.seal, shadow = null))
                weekKoan.question?.let { Text(it, style = PaperText.copy(fontSize = 19.sp, lineHeight = 25.sp, fontStyle = FontStyle.Italic)) }
            }
        }

        Spacer(Modifier.height(if (weekKoan != null) 70.dp else 150.dp))
        AnimatedVisibility(shown, enter = fadeIn(tween(500)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { it / 6 }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                followUp?.let { note ->
                    NudgeCard(NeboIcon.Heart, "Kako je danas?", com.grungert.nebo.koan.FollowUp.about(note)?.let { "Juče ti je bilo teško: $it" } ?: "Juče ti je bilo teško") {
                        go(Screen.CheckIn("followup", follows = note.id))
                    }
                }
                if (eveningDue) NudgeCard(NeboIcon.Moon, "Večernji pregled", "Šta je danas bilo dobro, a šta teško") { go(Screen.CheckIn("evening")) }
                if (reviewDue) NudgeCard(NeboIcon.Week, "Sedmični pregled", "Šta se vraćalo, i šta ti primjećuješ") { go(Screen.CheckIn("week")) }

                // the main way in: one suggestion for now, and why
                val n = now
                val type = SESSION_TYPES.firstOrNull { it.id == (n?.type ?: last.type) } ?: SESSION_TYPES[0]
                val shape = RoundedCornerShape(30.dp)
                Row(
                    Modifier.fillMaxWidth().tap("Kreni: ${type.name}", shape = shape) { startType(type.id) }.lift(shape, 16.dp)
                        .background(Ink.surface, shape).then(if (Ink.day) Modifier.border(1.dp, Color(0xCCFFFFFF), shape) else Modifier)
                        .padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Orb()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("ZA SADA · " + PART_LABEL[part], style = KickerStyle)
                        Text(type.name, style = HeadStyle.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold, shadow = null))
                        Text(n?.why ?: describe(last), style = SmallStyle.copy(fontSize = 13.5.sp, shadow = null), maxLines = 2)
                        // on what the suggestion rests: the notes, what has helped, or only the hour
                        n?.let {
                            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(if (it.basis == "po dobu dana") NeboIcon.Sun else NeboIcon.Stars, Ink.accentInk, 13.dp, filled = true)
                                Text(it.basis, style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink.accentInk, shadow = null))
                            }
                        }
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (Ink.day) Ink.ink else Ink.accent), contentAlignment = Alignment.Center) {
                        Icon(NeboIcon.Play, if (Ink.day) Ink.accent else Ink.ink, 18.dp, filled = true)
                    }
                }

                // the kinds of session, in three groups; the gear sets up a session in detail
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((i, g) in GROUPS.withIndex()) {
                        val on = i == group
                        val bg by animateColorAsState(if (on) Ink.accent else Ink.surface, label = "group")
                        Text(g.name, style = SmallStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (on) Ink.ink else Ink.text, shadow = null),
                            maxLines = 1, softWrap = false,
                            modifier = Modifier.tap(role = Role.Tab, shape = RoundedCornerShape(18.dp)) { group = i; groupChosen = true }.background(bg, RoundedCornerShape(18.dp))
                                .padding(horizontal = 13.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(38.dp).tap("Podesi sesiju", shape = CircleShape) { go(Screen.Setup) }.background(Ink.surface, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(NeboIcon.Settings, Ink.text, 18.dp)
                    }
                }
                if (!holdLearned) Text("Dodirni karticu za opis · drži je za početak", style = SmallStyle.copy(color = Ink.accentInk, fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                fun learned() { if (!holdLearned) { holdLearned = true; s.holdLearned = true } }
                AnimatedContent(group, transitionSpec = {
                    (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { if (targetState > initialState) it / 6 else -it / 6 })
                        .togetherWith(fadeOut(tween(160)))
                }, label = "group") { gi ->
                    val g = GROUPS[gi]
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (row in g.types.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (id in row) {
                                val t = SESSION_TYPES.first { it.id == id }
                                HoldCard(t.icon, t.short.let { if (it == t.name) t.name else it }, lengthOf(id, last), g.color, { info = id }, { learned(); startType(id) }, Modifier.weight(1f),
                                    forYou = id == now?.type)
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // the card of the kind of session tapped: what it is and how it works, with the way in
    info?.let { id ->
        val t = SESSION_TYPES.firstOrNull { it.id == id }
        SessionInfoSheet(id, t?.name ?: "Zapiši", t?.icon ?: NeboIcon.Mic, if (t == null) "Zapiši" else "Kreni",
            onStart = { info = null; if (t == null) go(Screen.CheckIn("note")) else startType(id) },
            onSetup = if (t == null) null else ({ info = null; s.sessionType = id; go(Screen.Setup) }),
            onClose = { info = null })
    }
}

/** A small chip of frosted glass right on the sky. */
@Composable
private fun SkyChip(text: String, modifier: Modifier = Modifier) {
    Text(text, style = SmallStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, shadow = null),
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF)).border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp))
}

/**
 * The person's own reminder to themself, a few words kept in sight under the greeting ("50-50"): written in the hand of
 * the koans, with a thin line of sand before it. A tap writes it anew; written empty, it is gone.
 */
@Composable
private fun SelfNote(note: String, writing: Boolean, onWrite: () -> Unit, onDone: (String) -> Unit) {
    val sand = Color(0xFFFFD9A0)
    AnimatedVisibility(writing || note.isNotEmpty(), enter = fadeIn(tween(400)) + expandVertically(clip = false), exit = fadeOut(tween(250)) + shrinkVertically(clip = false)) {
        if (writing) {
            var draft by remember { mutableStateOf(note) }
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x40081830))
                    .border(1.dp, sand.copy(alpha = 0.7f), RoundedCornerShape(18.dp)).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    if (draft.isEmpty()) Text("npr. 50-50", style = ReadStyle.copy(fontSize = 20.sp, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.55f), shadow = null))
                    androidx.compose.foundation.text.BasicTextField(draft, { draft = it.take(60) }, singleLine = true,
                        textStyle = ReadStyle.copy(fontSize = 20.sp, fontStyle = FontStyle.Italic, color = Color.White, shadow = null),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(sand),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone(draft) }))
                }
                Box(Modifier.size(40.dp).tap("Sačuvaj podsjetnik", shape = CircleShape) { onDone(draft) }, contentAlignment = Alignment.Center) {
                    Icon(NeboIcon.Check, sand, 20.dp)
                }
            }
        } else Row(   // no press layer here: it would cut the words' soft shadow into a box
            Modifier.padding(top = 14.dp).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClickLabel = "Promijeni podsjetnik", onClick = onWrite)
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(3.dp).height(30.dp).background(sand, RoundedCornerShape(2.dp)))
            Text(note, style = ReadStyle.copy(fontSize = 24.sp, lineHeight = 30.sp, fontStyle = FontStyle.Italic, color = Ink.onSky, shadow = skyShadow))
        }
    }
}

/** A gentle card for something waiting today: the day after a hard one, the evening look back, the week's review. */
@Composable
private fun NudgeCard(icon: NeboIcon, title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().tap(title, shape = shape, onClick = onClick).lift(shape).background(Ink.surface, shape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Ink.accent.copy(alpha = if (Ink.day) 0.5f else 0.2f)), contentAlignment = Alignment.Center) { Icon(icon, Ink.accentInk, 22.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, style = BodyStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
            Text(sub, style = SmallStyle.copy(shadow = null), maxLines = 1)
        }
    }
}

/**
 * A kind of session as a card: its icon in the group's colour, its name and length. A touch opens what it is; holding
 * starts it, a ring filling around the icon, so nothing begins by accident.
 */
@Composable
private fun HoldCard(icon: NeboIcon, name: String, length: String, tint: Color, onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier, holdMillis: Int = 700,
    forYou: Boolean = false) {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val press = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.height(78.dp).graphicsLayer { scaleX = press.value; scaleY = press.value }.lift(shape, 8.dp).clip(shape).background(Ink.surface)
            .then(if (forYou) Modifier.border(2.dp, Ink.accent, shape) else if (Ink.day) Modifier.border(1.dp, Color(0xCCFFFFFF), shape) else Modifier)
            .semantics {
                role = Role.Button
                onClick(label = "Opis") { tap(); true }
                customActions = listOf(CustomAccessibilityAction("Pokreni odmah") { hold(); true })
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val start = System.currentTimeMillis()
                    var done = false
                    scope.launch { press.animateTo(0.96f, spring(stiffness = Spring.StiffnessMedium)) }
                    val fill = scope.launch {
                        delay(110)   // a quick tap should not flash the ring
                        progress.animateTo(1f, tween(holdMillis - 110, easing = LinearEasing))
                        done = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        hold()
                    }
                    val released = tryAwaitRelease()
                    scope.launch { press.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
                    if (!done) {
                        fill.cancel()
                        if (released && System.currentTimeMillis() - start < 300) tap()
                        scope.launch { progress.animateTo(0f, tween(220)) }
                    }
                })
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(tint.copy(alpha = if (Ink.day) 0.4f else 0.25f)), contentAlignment = Alignment.Center) {
                Icon(icon, Ink.text, 24.dp)
            }
            Canvas(Modifier.size(54.dp)) {
                val p = progress.value
                if (p <= 0f) return@Canvas
                val w = 3.dp.toPx()
                val r = androidx.compose.ui.geometry.CornerRadius(19.dp.toPx())
                val ring = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(w / 2, w / 2, size.width - w / 2, size.height - w / 2, r))
                }
                val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(ring, false) }
                val part = androidx.compose.ui.graphics.Path()
                measure.getSegment(0f, measure.length * p, part, true)
                val c = if (Ink.day) Color(0xFFE0A04E) else Ink.accent
                drawPath(ring, c.copy(alpha = 0.2f), style = Stroke(w))
                drawPath(part, c, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, style = BodyStyle.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Bold, shadow = null), maxLines = 1, softWrap = false)
            if (forYou) Text("za tebe", style = SmallStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Ink.ink, shadow = null),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.accent).padding(horizontal = 7.dp, vertical = 1.dp))
            else Text(length, style = SmallStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, shadow = null), maxLines = 1, softWrap = false)
        }
    }
}

/** The light of "Za sada": a slowly breathing sphere of warm light with a lotus in it. */
@Composable
private fun Orb() {
    val pulse = com.grungert.nebo.sky.slowPulse(4000)
    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(68.dp)) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Ink.accent.copy(alpha = 0.6f), Color.Transparent), center, r), r)
            drawCircle(Brush.radialGradient(listOf(Color.White, Ink.paper, Ink.accent), Offset(center.x - r * 0.2f, center.y - r * 0.25f), r * 0.9f), r * (0.74f + 0.06f * pulse()))
        }
        Icon(NeboIcon.Lotus, Ink.ink.copy(alpha = 0.85f), 28.dp)
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
                if (lengths.isEmpty()) Text(if (type == "rain") "Koliko treba: četiri koraka, svojim tempom." else "Uvijek tri minute: po jedna za svaki dio.", style = SmallStyle.copy(shadow = null))
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
