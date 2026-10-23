package com.grungert.nebo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.grungert.nebo.Screen
import com.grungert.nebo.app
import com.grungert.nebo.esm.Esm
import com.grungert.nebo.ml.ModelCatalog
import com.grungert.nebo.ml.ModelChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The colour of each group's little icons: reminders warm, sessions calm, voice and meaning lilac, your data blue. */
private val WARM = Color(0xFFE0A04E)
private val CALM = Color(0xFF5FBFA6)
private val LILAC = Color(0xFF9A8CE6)
private val BLUE = Color(0xFF6F9BE0)

/**
 * The settings as a calm list in groups: reminders, sessions, voice and meaning, your data, and Nebo itself. Each
 * setting is one row (an icon, its name, what it is set to now, a switch); its details unfold under it when it is on
 * and touched. Above the reminders, a word when the phone keeps Nebo from sending them, with the way to allow it.
 */
@Composable
fun SettingsScreen(host: com.grungert.nebo.SkyHost, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val s = app.settings
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askToNotify() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun refresh() { app.scope.launch { Esm.refresh(context) } }

    // what the phone allows, read again whenever the person comes back from the system settings
    val nm = context.getSystemService(android.app.NotificationManager::class.java)
    val am = context.getSystemService(android.app.AlarmManager::class.java)
    var notifying by remember { mutableStateOf(nm.areNotificationsEnabled()) }
    var exact by remember { mutableStateOf(am.canScheduleExactAlarms()) }
    LifecycleResumeEffect(Unit) { notifying = nm.areNotificationsEnabled(); exact = am.canScheduleExactAlarms(); onPauseOrDispose {} }

    var esm by remember { mutableStateOf(s.esmEnabled) }
    var start by remember { mutableIntStateOf(s.esmStartHour) }
    var end by remember { mutableIntStateOf(s.esmEndHour) }
    var perDay by remember { mutableIntStateOf(s.esmPerDay) }
    var silenced by remember { mutableStateOf(s.esmSilencedDay == Esm.today()) }
    var haptics by remember { mutableStateOf(s.haptics) }
    var ambient by remember { mutableStateOf(s.ambient) }
    var weekReminder by remember { mutableStateOf(s.weekReminder) }
    var fifty by remember { mutableStateOf(s.fiftyOn) }
    var fiftyN by remember { mutableIntStateOf(s.fiftyPerDay) }
    var fiftyFrom by remember { mutableIntStateOf(s.fiftyFrom) }
    var fiftyTo by remember { mutableIntStateOf(s.fiftyTo) }
    var daily by remember { mutableStateOf(s.dailyQuestion) }
    var dailyHour by remember { mutableIntStateOf(s.dailyHour) }
    var evening by remember { mutableStateOf(s.eveningReview) }
    var followUp by remember { mutableStateOf(s.followUp) }
    var eveningHour by remember { mutableIntStateOf(s.eveningHour) }
    var remind by remember { mutableStateOf(s.reminderOn) }
    var hour by remember { mutableIntStateOf(s.reminderHour) }
    var minute by remember { mutableIntStateOf(s.reminderMinute) }
    var days by remember { mutableStateOf(s.reminderDays) }
    var skip by remember { mutableStateOf(s.reminderSkipIfDone) }

    fun applyEsm() {
        s.esmEnabled = esm; s.esmStartHour = start; s.esmEndHour = end; s.esmPerDay = perDay
        app.scope.launch { app.db.deleteFuturePrompts(System.currentTimeMillis()); Esm.refresh(context) }
    }
    fun applyFifty() {
        s.fiftyOn = fifty; s.fiftyPerDay = fiftyN; s.fiftyFrom = fiftyFrom; s.fiftyTo = fiftyTo
        s.fiftyPlanDay = 0   // plan the day again with the new choices
        refresh()
    }
    fun applyReminder() { s.reminderOn = remind; s.reminderHour = hour; s.reminderMinute = minute; s.reminderDays = days; s.reminderSkipIfDone = skip; refresh() }
    val anyReminder = remind || fifty || esm || daily || evening || followUp || weekReminder

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).screenPadding().padding(top = 12.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Postavke", style = TitleStyle.copy(fontSize = 38.sp, lineHeight = 42.sp))
            Text("Sve ostaje na ovom telefonu.", style = BodyStyle.copy(fontSize = 15.sp, color = Ink.soft))
            Spacer(Modifier.height(4.dp))

            // how it all works, for anyone new or unsure
            val shape = RoundedCornerShape(24.dp)
            Row(
                Modifier.fillMaxWidth().tap("Kako radi Nebo", shape = shape) { go(Screen.Help) }.lift(shape).background(Ink.surface, shape).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Ink.accent), contentAlignment = Alignment.Center) { Icon(NeboIcon.Info, Ink.ink, 24.dp, filled = true) }
                Column(Modifier.weight(1f)) {
                    Text("Kako radi Nebo", style = BodyStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
                    Text("Vrste sesija, bilješke, niti, podsjetnici i privatnost", style = SmallStyle.copy(shadow = null))
                }
                Icon(NeboIcon.Forward, Ink.soft, 16.dp)
            }

            Group("Podsjetnici") {
                // the phone may block what is set here: say so where it matters, with the way to allow it
                if (anyReminder && !notifying) Notice("Telefon ne dozvoljava Nebu obavještenja, pa podsjetnici ne stižu.", "Dozvoli") {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
                else if (anyReminder && !exact) Notice("Podsjetnici mogu kasniti i do petnaestak minuta.", "Tačno vrijeme") {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:" + context.packageName)))
                }
                SettingRow(NeboIcon.Bell, WARM, "Podsjetnik za meditaciju",
                    if (remind) "%02d:%02d · %s".format(hour, minute, mapOf("all" to "svaki dan", "weekdays" to "radnim danima", "weekend" to "vikendom")[days] ?: "")
                    else "Jedno tiho obavještenje u vrijeme koje izabereš",
                    remind, { remind = it; if (it) askToNotify(); applyReminder() }) {
                    Label("Sat")
                    val hours = rememberLazyListState(initialFirstVisibleItemIndex = (hour - 5 - 2).coerceAtLeast(0))
                    LazyRow(state = hours, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((5..23).toList()) { h -> Chip("$h", hour == h, { hour = h; applyReminder() }) }
                    }
                    Label("Minute")
                    ChipRow { for (m in listOf(0, 15, 30, 45)) Chip("%02d".format(m), minute == m, { minute = m; applyReminder() }) }
                    Label("Dani")
                    ChipRow { for ((label, v) in listOf("svaki dan" to "all", "radni dani" to "weekdays", "vikend" to "weekend")) Chip(label, days == v, { days = v; applyReminder() }) }
                    SwitchRow("Ne ako sam taj dan već meditirao", skip, { skip = it; applyReminder() })
                }
                SettingRow(NeboIcon.Moon, WARM, "Večernji pregled", if (evening) "u $eveningHour h · šta je bilo dobro, šta teško" else "Minut-dva uveče: dobro, teško, sutra",
                    evening, { evening = it; s.eveningReview = it; if (it) askToNotify(); refresh() }) {
                    Label("Kada")
                    ChipRow { for (h in listOf(19, 20, 21, 22)) Chip("$h h", eveningHour == h, { eveningHour = h; s.eveningHour = h; refresh() }) }
                }
                SettingRow(NeboIcon.Sun, WARM, "Pitanje dana", if (daily) "u $dailyHour h · iz koana koji nosiš" else "Ujutru jedno pitanje za dan",
                    daily, { daily = it; s.dailyQuestion = it; if (it) askToNotify(); refresh() }) {
                    Label("Kada")
                    ChipRow { for (h in listOf(6, 7, 8, 9, 10)) Chip("$h h", dailyHour == h, { dailyHour = h; s.dailyHour = h; refresh() }) }
                }
                SettingRow(NeboIcon.Lotus, WARM, "50-50 tokom dana", if (fifty) "$fiftyN puta, $fiftyFrom–$fiftyTo h" else "Pola pažnje u tijelu, pola u svijetu",
                    fifty, { fifty = it; if (it) askToNotify(); applyFifty() }) {
                    Text("Tiho obavještenje u nasumično vrijeme; dodir otvara vježbu od jednog minuta.", style = SmallStyle.copy(shadow = null))
                    Label("Koliko puta dnevno")
                    ChipRow { for (n in listOf(3, 4, 5, 6, 8)) Chip("$n", fiftyN == n, { fiftyN = n; applyFifty() }) }
                    Label("Od – do")
                    ChipRow {
                        for (h in listOf(7, 8, 9, 10)) Chip("od $h", fiftyFrom == h, { fiftyFrom = h; applyFifty() })
                        for (h in listOf(18, 19, 20, 21, 22)) Chip("do $h", fiftyTo == h, { fiftyTo = h; applyFifty() })
                    }
                    GlassButton("Probaj sada", { go(Screen.Fifty) }, icon = NeboIcon.Play)
                }
                SettingRow(NeboIcon.Heart, WARM, "Zapažanja tokom dana", if (esm) "$perDay puta, $start–$end h" + (if (silenced) " · danas utišano" else "") else "Kako si? U nasumično vrijeme",
                    esm, { esm = it; if (it) askToNotify(); applyEsm() }) {
                    Text("Bez nizova i bodova. Propušten poziv je samo propušten.", style = SmallStyle.copy(shadow = null))
                    Label("Koliko puta dnevno")
                    ChipRow { for (n in 2..5) Chip("$n", perDay == n, { perDay = n; applyEsm() }) }
                    Label("Od – do")
                    ChipRow {
                        for (h in listOf(7, 8, 9, 10, 11)) Chip("od $h", start == h, { start = h; applyEsm() })
                        for (h in listOf(18, 19, 20, 21, 22)) Chip("do $h", end == h, { end = h; applyEsm() })
                    }
                    SwitchRow("Utišaj danas", silenced, { silenced = it; s.esmSilencedDay = if (it) Esm.today() else 0 })
                }
                SettingRow(NeboIcon.Similar, WARM, "Povratak sutradan", "Dan poslije teške bilješke: kako je sada?", followUp,
                    { followUp = it; s.followUp = it; if (it) askToNotify(); refresh() })
                SettingRow(NeboIcon.Week, WARM, "Sedmični pregled", "Nedjeljom u 19 h, ako već nije urađen", weekReminder,
                    { weekReminder = it; s.weekReminder = it; if (it) askToNotify(); refresh() }, last = true)
            }

            SkyGroup(host)

            Group("Sesije") {
                SettingRow(NeboIcon.Breath, CALM, "Vibracija na udah i izdah", "Blago, da dah možeš pratiti i zatvorenih očiju", haptics, { haptics = it; s.haptics = it })
                SettingRow(NeboIcon.Wave, CALM, "Zvuk prirode", "Vjetar, ptice danju, cvrčci noću, kiša", ambient, { ambient = it; s.ambient = it }, last = true)
            }

            VoicePanel()

            BackupPanel()

            Group("O Nebu") {
                var open by remember { mutableStateOf(false) }
                SettingRow(NeboIcon.Journal, BLUE, "Izvori i licence", "Koani, prevodi, modeli, fontovi i ikonice", null, null, onTap = { open = !open }, expanded = open, last = true) {
                    for (t in listOf(
                        "Koani: Mumon, Bezvratna kapija (1228), po engleskom prevodu Nyogena Senzakija i Paula Repsa (1934), javno vlasništvo. Prepričano na naš jezik. Poruke su pisane za ovu aplikaciju.",
                        "Tao Te Ching po prevodu Jamesa Leggea (1891), Džuang Ce po prevodu Herberta A. Gilesa (1889) i Dhammapada po prevodu F. Maxa Müllera (1881), sve javno vlasništvo, prevedeno na naš jezik. Zen priče su tradicionalne anegdote, prepričane za ovu aplikaciju.",
                        "Parakeet TDT 0.6B v3 (NVIDIA, CC-BY-4.0) kroz sherpa-onnx (Apache-2.0). Whisper (OpenAI, MIT) kroz whisper.cpp. Model za značenje: multilingual-e5-small (MIT).",
                        "Fontovi Manrope i Newsreader (SIL Open Font License), ikonice Phosphor (MIT).",
                    )) Text(t, style = SmallStyle.copy(shadow = null))
                }
            }
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
            Text("Nebo ${version ?: ""} · sve što snimiš i zapišeš ostaje na telefonu.", style = SmallStyle.copy(fontSize = 12.sp),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
        }
    }
}

/**
 * The sky behind every screen: the hour, the season and the weather, each as set, and for "kako dođe" what it is
 * today. A change shows at once on the sky behind this screen.
 */
@Composable
private fun SkyGroup(host: com.grungert.nebo.SkyHost) {
    val s = LocalContext.current.app.settings
    var time by remember { mutableStateOf(s.timeMode) }
    var season by remember { mutableStateOf(s.seasonMode) }
    var weather by remember { mutableStateOf(s.weatherMode) }
    var lake by remember { mutableStateOf(s.lakeScene) }
    var open by remember { mutableStateOf<String?>(null) }
    fun applySky() { host.applyWeatherSettings(season, weather); host.day.mode = time; host.lake = lake }
    val today = com.grungert.nebo.sky.Seasons.season(season)
    val timeLabel = when (time) { "clock" -> "po satu"; "cycle" -> "ciklus kroz dan"; else -> TIME_OPTIONS.firstOrNull { it.second == time }?.first ?: time }
    val seasonLabel = if (season == "auto") "po kalendaru · sada ${today.label}" else today.label
    val weatherLabel = if (weather == "auto") "kako dođe · danas ${com.grungert.nebo.sky.Seasons.weather("auto", today).label}"
        else com.grungert.nebo.sky.Seasons.weather(weather, today).label
    Group("Nebo") {
        SettingRow(NeboIcon.Sun, BLUE, "Doba dana", timeLabel, null, null, onTap = { open = if (open == "time") null else "time" }, expanded = open == "time") {
            ChipRow {
                Chip("po satu", time == "clock", { time = "clock"; s.timeMode = time; applySky() })
                Chip("ciklus", time == "cycle", { time = "cycle"; s.timeMode = time; applySky() })
                for ((label, h) in TIME_OPTIONS) Chip(label, time == h, { time = h; s.timeMode = h; applySky() })
            }
        }
        SettingRow(NeboIcon.Cloud, BLUE, "Vrijeme", weatherLabel, null, null, onTap = { open = if (open == "weather") null else "weather" }, expanded = open == "weather") {
            ChipRow {
                Chip("kako dođe", weather == "auto", { weather = "auto"; s.weatherMode = weather; applySky() })
                for (w in com.grungert.nebo.sky.Weather.entries) Chip(w.label, weather == w.name, { weather = w.name; s.weatherMode = w.name; applySky() })
            }
            Text("„Kako dođe“: svaki dan drugačije, najčešće vedro, ponekad kiša, magla ili snijeg zimi.", style = SmallStyle.copy(shadow = null))
        }
        SettingRow(NeboIcon.Lotus, BLUE, "Godišnje doba", seasonLabel, null, null, onTap = { open = if (open == "season") null else "season" }, expanded = open == "season") {
            ChipRow {
                Chip("po kalendaru", season == "auto", { season = "auto"; s.seasonMode = season; applySky() })
                for (sn in com.grungert.nebo.sky.Season.entries) Chip(sn.label, season == sn.name, { season = sn.name; s.seasonMode = sn.name; applySky() })
            }
        }
        SettingRow(NeboIcon.Wave, BLUE, "Mjesto", if (lake) "jezero" else "livada", null, null, onTap = { open = if (open == "place") null else "place" }, expanded = open == "place", last = true) {
            ChipRow {
                Chip("livada", !lake, { lake = false; s.lakeScene = false; applySky() })
                Chip("jezero", lake, { lake = true; s.lakeScene = true; applySky() })
            }
            Text("Šetnja uvijek ide livadom.", style = SmallStyle.copy(shadow = null))
        }
    }
}

/** A group of settings: its name above, its rows on one card of glass. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), style = KickerStyle.copy(color = if (Ink.day) Ink.accentInk else Ink.soft), modifier = Modifier.padding(start = 6.dp, top = 12.dp))
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().lift(shape).clip(shape).background(Ink.surface).then(if (Ink.day) Modifier.border(1.dp, Color(0xCCFFFFFF), shape) else Modifier),
        content = content)
}

@Composable
private fun Label(text: String) = Text(text, style = SmallStyle.copy(fontWeight = FontWeight.Bold, shadow = null))

/** A word at the top of a group when the phone keeps something from working, with the way to fix it. */
@Composable
private fun Notice(text: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Ink.accent.copy(alpha = if (Ink.day) 0.45f else 0.16f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = SmallStyle.copy(color = Ink.text, fontWeight = FontWeight.SemiBold, shadow = null), modifier = Modifier.weight(1f))
        Text(action, style = SmallStyle.copy(fontWeight = FontWeight.ExtraBold, color = Ink.ink, shadow = null),
            modifier = Modifier.tap(action, shape = RoundedCornerShape(12.dp), onClick = onClick).background(Ink.accent, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

/**
 * One setting: an icon in its group's colour, the name, what it is set to now, and a switch. When it is on and has
 * details, touching the row unfolds them under it (and turning it on opens them once). [checked] null: no switch,
 * the row only opens and closes.
 */
@Composable
private fun SettingRow(icon: NeboIcon, tint: Color, title: String, summary: String, checked: Boolean?, onChange: ((Boolean) -> Unit)?, last: Boolean = false,
    onTap: (() -> Unit)? = null, expanded: Boolean? = null, details: (@Composable ColumnScope.() -> Unit)? = null) {
    var openOwn by remember { mutableStateOf(false) }
    val open = expanded ?: (openOwn && checked != false)
    val turn by animateFloatAsState(if (open) 90f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "caret")
    val bg by animateColorAsState(if (open) Ink.text.copy(alpha = 0.04f) else Color.Transparent, label = "row")
    Column(Modifier.fillMaxWidth().background(bg)) {
        Row(
            Modifier.fillMaxWidth().tap(title, role = if (checked != null && details == null) Role.Switch else Role.Button, haptic = false) {
                when {
                    onTap != null -> onTap()
                    details != null && checked == true -> openOwn = !openOwn
                    checked != null && onChange != null -> { onChange(!checked); if (!checked && details != null) openOwn = true }
                }
            }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = if (checked == false) 0.12f else if (Ink.day) 0.28f else 0.22f)),
                contentAlignment = Alignment.Center) { Icon(icon, if (checked == false) Ink.soft else if (Ink.day) Ink.ink else Color.White, 19.dp, filled = checked == true) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = BodyStyle.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Bold, shadow = null))
                Text(summary, style = SmallStyle.copy(fontSize = 12.5.sp, shadow = null), maxLines = 2)
            }
            if (details != null && (checked == true || checked == null)) Icon(NeboIcon.Forward, Ink.faint, 14.dp, Modifier.graphicsLayer { rotationZ = turn })
            if (checked != null && onChange != null) Switch(checked, { on -> onChange(on); if (on && details != null) openOwn = true; if (!on) openOwn = false },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Ink.accent, checkedThumbColor = Ink.ink, uncheckedTrackColor = if (Ink.day) Color(0x2216233F) else Color(0x33FFFFFF),
                    uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
                ))
        }
        if (details != null) AnimatedVisibility(open, enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(start = 62.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = details)
        }
        if (!last) Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(1.dp).background(Ink.hairline))
    }
}

/**
 * Speech to text and the meaning model: each model a row to choose (a dot), with its size and state, a thin bar while
 * it downloads, and the way to fetch or remove it. The chosen model is used once it is on the phone; until then the
 * best one that is, and the row says so.
 */
@Composable
private fun VoicePanel() {
    val app = LocalContext.current.app
    val s = app.settings
    var whisper by remember { mutableStateOf(s.whisperModel) }
    var lang by remember { mutableStateOf(s.whisperLang) }
    var tick by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf(false) }
    // the state of the downloads, read every second only while one is running
    LaunchedEffect(downloading) { while (downloading) { delay(1000); tick++ } }
    var inUse by remember { mutableStateOf<ModelChoice?>(null) }
    LaunchedEffect(tick, whisper) { inUse = withContext(Dispatchers.IO) { app.models.readyWhisper() } }
    Group("Glas i značenje") {
        Text("Radi na telefonu, bez interneta. Model se preuzima jednom.", style = SmallStyle.copy(shadow = null), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp))
        for (choice in ModelCatalog.whisper) ModelRow(choice, tick, selected = whisper == choice.id, inUse = inUse?.id == choice.id,
            onSelect = { whisper = choice.id; s.whisperModel = choice.id }, onDownloading = { downloading = it },
            onDeleted = { if (whisper == choice.id) app.models.readyWhisper()?.let { whisper = it.id; s.whisperModel = it.id } })
        if (whisper != inUse?.id && inUse != null) Text("Dok se izabrani ne preuzme, koristi se ${inUse?.label}.", style = SmallStyle.copy(color = Ink.accentInk, shadow = null),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (whisper == "parakeet") Text("Parakeet sam prepoznaje jezik: naš, engleski ili neki od 25 evropskih. Piše latinicom.", style = SmallStyle.copy(shadow = null))
            else {
                Label("Jezik prepoznavanja")
                ChipRow { for ((code, label) in listOf("hr" to "hrvatski (latinica)", "bs" to "bosanski", "sr" to "srpski", "en" to "engleski")) Chip(label, lang == code, { lang = code; s.whisperLang = code }) }
                Text("Crnogorski nema poseban model; hrvatski daje latinicu i ijekavicu najbliže našem govoru.", style = SmallStyle.copy(shadow = null))
            }
        }
        Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Ink.hairline))
        ModelRow(ModelCatalog.meaning, tick, selected = null, inUse = false, onSelect = null, onDownloading = { downloading = it }, onDeleted = {})
        Text("Bira koan i nalazi slične bilješke po smislu. Bez njega, po riječima i raspoloženju.", style = SmallStyle.copy(shadow = null),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
    }
}

@Composable
private fun ModelRow(choice: ModelChoice, tick: Int, selected: Boolean?, inUse: Boolean, onSelect: (() -> Unit)?, onDownloading: (Boolean) -> Unit, onDeleted: () -> Unit) {
    val app = LocalContext.current.app
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(tick) {
        val (r, p) = withContext(Dispatchers.IO) { app.models.isReady(choice) to app.models.progress(choice) }
        // the model is put to work once, when it arrives, never again every second
        if (r && ready == false) { if (choice.id == "e5") app.recommender.warmUp() else app.transcriber.drain() }
        ready = r; progress = if (r) null else p
        if (p != null && !r) onDownloading(true)
    }
    val mb = "%.0f MB".format(choice.bytes / 1e6)
    Column(Modifier.fillMaxWidth().then(if (onSelect != null) Modifier.tap(choice.label, Role.RadioButton, haptic = false, onClick = onSelect) else Modifier)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected != null) Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) Ink.accentInk else Ink.faint, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(Ink.accentInk))
            } else Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(LILAC.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(NeboIcon.Stars, if (Ink.day) Ink.ink else Color.White, 19.dp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(choice.label, style = BodyStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, shadow = null))
                    if (inUse) Text("koristi se", style = SmallStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Ink.ink, shadow = null),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.accent).padding(horizontal = 6.dp, vertical = 1.dp))
                }
                Text(choice.note + " · " + mb + (if (ready == true) " · na telefonu" else ""), style = SmallStyle.copy(fontSize = 12.5.sp, shadow = null))
            }
            val p = progress
            when {
                ready == true -> Box(Modifier.size(36.dp).tap("Obriši ${choice.label}", shape = CircleShape) { app.models.delete(choice); ready = false; onDeleted() },
                    contentAlignment = Alignment.Center) { Icon(NeboIcon.Trash, Ink.soft, 18.dp) }
                p != null -> Text("%d%%".format((p * 100).toInt()), style = SmallStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
                ready == false -> Text("Preuzmi", style = SmallStyle.copy(fontWeight = FontWeight.ExtraBold, color = Ink.ink, shadow = null),
                    modifier = Modifier.tap("Preuzmi ${choice.label}", shape = RoundedCornerShape(12.dp)) { app.models.download(choice); progress = 0f; onDownloading(true) }
                        .background(Ink.accent, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        progress?.let { p ->
            Box(Modifier.padding(start = 34.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ink.text.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Ink.accent))
            }
        }
    }
}

/** Saving everything to one file, restoring it, and a weekly automatic copy into a chosen folder. */
@Composable
private fun BackupPanel() {
    val context = LocalContext.current
    val app = context.app
    val s = app.settings
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingSummary by remember { mutableStateOf<com.grungert.nebo.data.Backup.Summary?>(null) }
    var folder by remember { mutableStateOf(s.backupTree) }
    var last by remember { mutableStateOf(s.lastBackup) }
    val df = java.text.SimpleDateFormat("d. M. yyyy. 'u' HH:mm", java.util.Locale.ROOT)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Pravim kopiju…"
        app.scope.launch {
            status = runCatching {
                val sum = context.contentResolver.openOutputStream(uri)!!.use { com.grungert.nebo.data.Backup.write(context, it) }
                s.lastBackup = System.currentTimeMillis(); last = s.lastBackup
                "Sačuvano: ${sum.entries} zapisa, ${sum.sessions} sesija, ${sum.recordings} snimaka."
            }.getOrElse { "Kopija nije uspjela: ${it.message}" }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.scope.launch {
            val sum = runCatching { context.contentResolver.openInputStream(uri)!!.use { com.grungert.nebo.data.Backup.inspect(it) } }.getOrNull()
            if (sum == null) status = "Ovo nije Nebo kopija." else { pending = uri; pendingSummary = sum }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        s.backupTree = uri.toString(); folder = s.backupTree
        status = "Pravim prvu kopiju u izabrani folder…"
        app.scope.launch {
            status = if (com.grungert.nebo.data.Backup.autoIfDue(context, force = true)) "Kopija je u folderu. Sljedeća za sedam dana." else "U taj folder ne mogu da pišem; izaberi drugi."
            last = s.lastBackup
        }
    }
    // an old copy, or none, is worth a word: notes exist only on this phone
    val stale = last == 0L || System.currentTimeMillis() - last > 30 * 86_400_000L

    Group("Tvoji podaci") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(BLUE.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Icon(NeboIcon.Share, if (Ink.day) Ink.ink else Color.White, 19.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Rezervna kopija", style = BodyStyle.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Bold, shadow = null))
                    Text(if (last > 0) "Zadnja: " + df.format(java.util.Date(last)) else "Još nije pravljena",
                        style = SmallStyle.copy(fontSize = 12.5.sp, color = if (stale) Ink.accentInk else Ink.soft, fontWeight = if (stale) FontWeight.Bold else FontWeight.Medium, shadow = null))
                }
            }
            Text("Bilješke, snimci i podešavanja postoje samo na ovom telefonu. Sačuvaj kopiju na Google Drive ili računar, da ih ne izgubiš.", style = SmallStyle.copy(shadow = null))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Sačuvaj kopiju", { save.launch(com.grungert.nebo.data.Backup.fileName()) }, Modifier.weight(1f), strong = true)
                GlassButton("Vrati", { open.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.weight(1f))
            }
            status?.let { Text(it, style = SmallStyle.copy(color = Ink.text, fontWeight = FontWeight.SemiBold, shadow = null)) }
            val p = pending; val ps = pendingSummary
            if (p != null && ps != null) PaperCard {
                Text("Kopija od " + df.format(java.util.Date(ps.created)), style = PaperText.copy(fontSize = 20.sp))
                Text("${ps.entries} zapisa, ${ps.sessions} sesija, ${ps.recordings} snimaka.", style = PaperSmall)
                Text("Vraćanje zamjenjuje sve sadašnje zapise na ovom telefonu. Aplikacija će se zatim ponovo pokrenuti.", style = PaperSmall.copy(color = Ink.seal))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Vrati", {
                        status = "Vraćam…"
                        app.scope.launch {
                            runCatching { context.contentResolver.openInputStream(p)!!.use { com.grungert.nebo.data.Backup.restore(context, it) } }
                                .onFailure { status = "Vraćanje nije uspjelo: ${it.message}" }
                        }
                    }, strong = true)
                    GlassButton("Odustani", { pending = null; pendingSummary = null })
                }
            }
        }
        Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(1.dp).background(Ink.hairline))
        SettingRow(NeboIcon.Week, BLUE, "Automatski jednom sedmično",
            if (folder != null) "U izabrani folder; čuvaju se zadnje četiri" else "Izaberi folder, npr. Documents/Nebo", folder != null,
            { on -> if (on) pickFolder.launch(null) else { s.backupTree = null; folder = null } }, last = true)
    }
}
