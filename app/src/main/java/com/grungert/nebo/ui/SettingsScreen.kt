package com.grungert.nebo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.ContextCompat
import com.grungert.nebo.Screen
import com.grungert.nebo.app
import com.grungert.nebo.esm.Esm
import com.grungert.nebo.ml.ModelCatalog
import com.grungert.nebo.ml.ModelChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val s = app.settings
    var esm by remember { mutableStateOf(s.esmEnabled) }
    var start by remember { mutableIntStateOf(s.esmStartHour) }
    var end by remember { mutableIntStateOf(s.esmEndHour) }
    var perDay by remember { mutableIntStateOf(s.esmPerDay) }
    var silenced by remember { mutableStateOf(s.esmSilencedDay == Esm.today()) }
    var whisper by remember { mutableStateOf(s.whisperModel) }
    var lang by remember { mutableStateOf(s.whisperLang) }
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
    var refresh by remember { mutableIntStateOf(0) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun applyEsm() {
        s.esmEnabled = esm; s.esmStartHour = start; s.esmEndHour = end; s.esmPerDay = perDay
        app.scope.launch {
            app.db.deleteFuturePrompts(System.currentTimeMillis())
            Esm.refresh(context)
        }
    }

    // refresh download progress once a second while this screen is open
    LaunchedEffect(Unit) { while (true) { delay(1000); refresh++ } }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(top = 8.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader("Postavke", null)

            // how it all works, for anyone new or unsure
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.surface)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { go(Screen.Help) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Ink.accent.copy(alpha = if (Ink.day) 0.45f else 0.2f)), contentAlignment = Alignment.Center) { Text("?", style = HeadStyle.copy(color = Ink.accentInk, shadow = null)) }
                Column(Modifier.weight(1f)) {
                    Text("Kako radi Nebo", style = BodyStyle.copy(fontWeight = FontWeight.Bold, shadow = null))
                    Text("Vrste sesija, bilješke, niti, podsjetnici i privatnost", style = SmallStyle.copy(shadow = null))
                }
            }

            ReminderPanel()

            BackupPanel()

            Panel {
                SectionLabel("50-50 tokom dana")
                fun applyFifty() {
                    s.fiftyOn = fifty; s.fiftyPerDay = fiftyN; s.fiftyFrom = fiftyFrom; s.fiftyTo = fiftyTo
                    s.fiftyPlanDay = 0   // plan the day again with the new choices
                    app.scope.launch { Esm.refresh(context) }
                }
                SwitchRow("Podsjeti me na 50-50", fifty, {
                    fifty = it
                    if (it && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    applyFifty()
                }, sub = "Pola pažnje u tijelu, pola u svijetu oko tebe. Tiho obavještenje u nasumično vrijeme; dodir otvara vježbu od jednog minuta.")
                if (fifty) {
                    Text("Koliko puta dnevno", style = SmallStyle)
                    ChipRow { for (n in listOf(3, 4, 5, 6, 8)) Chip("$n", fiftyN == n, { fiftyN = n; applyFifty() }) }
                    Text("Od", style = SmallStyle)
                    ChipRow { for (h in listOf(7, 8, 9, 10)) Chip("$h h", fiftyFrom == h, { fiftyFrom = h; applyFifty() }) }
                    Text("Do", style = SmallStyle)
                    ChipRow { for (h in listOf(18, 19, 20, 21, 22)) Chip("$h h", fiftyTo == h, { fiftyTo = h; applyFifty() }) }
                }
                GlassButton("Probaj sada", { go(Screen.Fifty) })
            }

            Panel {
                SectionLabel("Zapažanja tokom dana")
                SwitchRow("Pitaj me kako sam", esm, {
                    esm = it
                    if (it && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    applyEsm()
                }, sub = "U nasumično vrijeme, unutar prozora koji izabereš. Bez nizova i bodova.")
                if (esm) {
                    Text("Od", style = SmallStyle)
                    ChipRow { for (h in listOf(7, 8, 9, 10, 11)) Chip("$h h", start == h, { start = h; applyEsm() }) }
                    Text("Do", style = SmallStyle)
                    ChipRow { for (h in listOf(18, 19, 20, 21, 22)) Chip("$h h", end == h, { end = h; applyEsm() }) }
                    Text("Koliko puta dnevno", style = SmallStyle)
                    ChipRow { for (n in 2..5) Chip("$n", perDay == n, { perDay = n; applyEsm() }) }
                    SwitchRow("Utišaj danas", silenced, {
                        silenced = it
                        s.esmSilencedDay = if (it) Esm.today() else 0
                    })
                }
            }

            Panel {
                SectionLabel("Pretvaranje glasa u tekst")
                Text("Radi na telefonu, bez interneta. Model se preuzima jednom.", style = SmallStyle)
                for (choice in ModelCatalog.whisper) {
                    ModelRow(choice, refresh, selected = whisper == choice.id, onSelect = { whisper = choice.id; s.whisperModel = choice.id })
                }
                if (whisper == "parakeet") Text("Parakeet sam prepoznaje jezik: naš, engleski ili neki od 25 evropskih. Piše latinicom.", style = SmallStyle)
                else {
                    Text("Jezik prepoznavanja", style = SmallStyle)
                    ChipRow {
                        for ((code, label) in listOf("hr" to "hrvatski (latinica)", "bs" to "bosanski", "sr" to "srpski", "en" to "engleski")) {
                            Chip(label, lang == code, { lang = code; s.whisperLang = code })
                        }
                    }
                    Text("Crnogorski nema poseban model; hrvatski daje latinicu i ijekavicu najbliže našem govoru.", style = SmallStyle)
                }
            }

            Panel {
                SectionLabel("Izbor koana po značenju")
                ModelRow(ModelCatalog.meaning, refresh, selected = null, onSelect = null)
                Text("Bez ovog modela koan se bira po ključnim riječima i raspoloženju koje izabereš.", style = SmallStyle)
            }

            Panel {
                SectionLabel("Ostalo")
                SwitchRow("Blaga vibracija na udah i izdah", haptics, { haptics = it; s.haptics = it })
                SwitchRow("Zvuk prirode u sesijama", ambient, { ambient = it; s.ambient = it })
                SwitchRow("Podsjeti me na sedmični pregled", weekReminder, {
                    weekReminder = it; s.weekReminder = it
                    app.scope.launch { Esm.refresh(context) }
                }, sub = "Nedjeljom u 19 h, ako pregled već nije urađen.")
                SwitchRow("Pitanje dana ujutro", daily, {
                    daily = it; s.dailyQuestion = it
                    if (it && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    app.scope.launch { Esm.refresh(context) }
                }, sub = "Pitanje iz koana koji nosiš ove sedmice, ili iz biblioteke.")
                if (daily) ChipRow {
                    for (h in listOf(6, 7, 8, 9, 10)) Chip("$h h", dailyHour == h, { dailyHour = h; s.dailyHour = h; app.scope.launch { Esm.refresh(context) } })
                }
                SwitchRow("Povratak sutradan", followUp, {
                    followUp = it; s.followUp = it
                    app.scope.launch { Esm.refresh(context) }
                }, sub = "Dan poslije teške bilješke, jedno pitanje: kako je sada?")
                SwitchRow("Večernji pregled", evening, {
                    evening = it; s.eveningReview = it
                    if (it && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    app.scope.launch { Esm.refresh(context) }
                }, sub = "Uveče, minut-dva: šta je bilo dobro, šta teško, i šta sutra drugačije.")
                if (evening) ChipRow {
                    for (h in listOf(19, 20, 21, 22)) Chip("$h h", eveningHour == h, { eveningHour = h; s.eveningHour = h; app.scope.launch { Esm.refresh(context) } })
                }
            }

            Panel {
                SectionLabel("Izvori")
                Text("Koani: Mumon, Bezvratna kapija (1228), po engleskom prevodu Nyogena Senzakija i Paula Repsa (1934), " +
                    "javno vlasništvo. Prepričano na naš jezik. Poruke su pisane za ovu aplikaciju.", style = SmallStyle)
                Text("Tao Te Ching po prevodu Jamesa Leggea (1891), Džuang Ce po prevodu Herberta A. Gilesa (1889) i Dhammapada po prevodu " +
                    "F. Maxa Müllera (1881), sve javno vlasništvo, prevedeno na naš jezik. Zen priče su tradicionalne anegdote, prepričane za ovu aplikaciju.", style = SmallStyle)
                Text("Parakeet TDT 0.6B v3 (NVIDIA, CC-BY-4.0) kroz sherpa-onnx (Apache-2.0). Whisper (OpenAI, MIT) kroz whisper.cpp. " +
                    "Model za značenje: multilingual-e5-small (MIT).", style = SmallStyle)
                Text("Sve što snimiš i zapišeš ostaje na telefonu.", style = SmallStyle)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelRow(choice: ModelChoice, refresh: Int, selected: Boolean?, onSelect: (() -> Unit)?) {
    val app = LocalContext.current.app
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) { ready = app.models.isReady(choice); progress = app.models.progress(choice) }
        if (ready && choice.id == "e5") app.recommender.warmUp()
        if (ready && choice.id != "e5") app.transcriber.drain()
    }
    val mb = "%.0f MB".format(choice.bytes / 1e6)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selected != null && onSelect != null) Chip(if (selected) "✓" else " ", selected, onSelect)
        Column(Modifier.weight(1f)) {
            Text(choice.label, style = BodyStyle)
            Text("${choice.note} · $mb", style = SmallStyle)
            val p = progress
            when {
                ready -> Text("Preuzeto", style = SmallStyle.copy(color = Ink.accentInk))
                p != null -> Text("Preuzimam… %d%%".format((p * 100).toInt()), style = SmallStyle)
            }
        }
        when {
            ready -> GlassButton("Obriši", { app.models.delete(choice); ready = false })
            progress == null -> GlassButton("Preuzmi", { app.models.download(choice); progress = 0f })
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
    val df = java.text.SimpleDateFormat("d. M. yyyy. HH:mm", java.util.Locale.ROOT)

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

    Panel {
        SectionLabel("Rezervna kopija")
        Text("Bilješke, snimci i podešavanja postoje samo na ovom telefonu. Sačuvaj kopiju na Google Drive ili računar, " +
            "da ih ne izgubiš ako se telefon izgubi ili pokvari.", style = SmallStyle)
        Text(if (last > 0) "Zadnja kopija: " + df.format(java.util.Date(last)) else "Kopija još nije pravljena.",
            style = SmallStyle.copy(color = if (last > 0) Ink.accentInk else Ink.soft))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Sačuvaj kopiju", { save.launch(com.grungert.nebo.data.Backup.fileName()) }, strong = true)
            GlassButton("Vrati iz kopije", { open.launch(arrayOf("application/zip", "application/octet-stream")) })
        }
        SwitchRow("Automatski jednom sedmično", folder != null, { on ->
            if (on) pickFolder.launch(null) else { s.backupTree = null; folder = null }
        }, sub = if (folder != null) "U folder koji si izabrao; čuvaju se zadnje četiri kopije." else "Izaberi folder na telefonu, npr. Documents/Nebo.")
        status?.let { Text(it, style = SmallStyle.copy(color = Ink.text)) }
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
                })
                GlassButton("Odustani", { pending = null; pendingSummary = null })
            }
        }
    }
}

/** A daily nudge to sit, at a chosen time and on chosen days; quiet on days that already had a session. */
@Composable
private fun ReminderPanel() {
    val context = LocalContext.current
    val app = context.app
    val s = app.settings
    var on by remember { mutableStateOf(s.reminderOn) }
    var hour by remember { mutableIntStateOf(s.reminderHour) }
    var minute by remember { mutableIntStateOf(s.reminderMinute) }
    var days by remember { mutableStateOf(s.reminderDays) }
    var skip by remember { mutableStateOf(s.reminderSkipIfDone) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun apply() {
        s.reminderOn = on; s.reminderHour = hour; s.reminderMinute = minute; s.reminderDays = days; s.reminderSkipIfDone = skip
        app.scope.launch { Esm.refresh(context) }
    }

    Panel {
        SectionLabel("Podsjetnik za meditaciju")
        SwitchRow("Podsjeti me da sjednem", on, {
            on = it
            if (it && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            apply()
        }, sub = if (on) "Svaki put u %02d:%02d.".format(hour, minute) else "Jedna tiha notifikacija u vrijeme koje izabereš.")
        if (on) {
            Text("Sat", style = SmallStyle)
            // opens scrolled to the chosen hour
            val hours = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (hour - 5 - 2).coerceAtLeast(0))
            androidx.compose.foundation.lazy.LazyRow(state = hours, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((5..23).toList()) { h -> Chip("$h", hour == h, { hour = h; apply() }) }
            }
            Text("Minute", style = SmallStyle)
            ChipRow { for (m in listOf(0, 15, 30, 45)) Chip(":%02d".format(m), minute == m, { minute = m; apply() }) }
            Text("Dani", style = SmallStyle)
            ChipRow {
                for ((label, v) in listOf("svaki dan" to "all", "radni dani" to "weekdays", "vikend" to "weekend")) Chip(label, days == v, { days = v; apply() })
            }
            SwitchRow("Ne podsjećaj ako sam već meditirao tog dana", skip, { skip = it; apply() })
            // without exact alarms Android may deliver the reminder up to about fifteen minutes late (see Esm.arm)
            val am = context.getSystemService(android.app.AlarmManager::class.java)
            var exact by remember { mutableStateOf(am.canScheduleExactAlarms()) }
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { exact = am.canScheduleExactAlarms(); onPauseOrDispose {} }
            if (!exact) {
                Text("Android trenutno smije poslati podsjetnik i do petnaestak minuta kasnije. Za tačno vrijeme dozvoli Nebu tačne alarme.", style = SmallStyle)
                GlassButton("Dozvoli tačno vrijeme", {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:" + context.packageName)))
                })
            } else Text("Podsjetnik stiže u minut tačno.", style = SmallStyle.copy(color = Ink.accentInk))
        }
    }
}
