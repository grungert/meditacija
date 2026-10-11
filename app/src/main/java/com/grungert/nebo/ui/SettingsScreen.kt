package com.grungert.nebo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackButton { go(Screen.Home) }
                Text("Postavke", style = HeadStyle)
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
                SectionLabel("Pretvaranje glasa u tekst (Whisper)")
                Text("Radi na telefonu, bez interneta. Model se preuzima jednom.", style = SmallStyle)
                for (choice in ModelCatalog.whisper) {
                    ModelRow(choice, refresh, selected = whisper == choice.id, onSelect = { whisper = choice.id; s.whisperModel = choice.id })
                }
                Text("Jezik prepoznavanja", style = SmallStyle)
                ChipRow {
                    for ((code, label) in listOf("hr" to "hrvatski (latinica)", "bs" to "bosanski", "sr" to "srpski")) {
                        Chip(label, lang == code, { lang = code; s.whisperLang = code })
                    }
                }
                Text("Crnogorski nema poseban model; hrvatski daje latinicu i ijekavicu najbliže našem govoru.", style = SmallStyle)
            }

            Panel {
                SectionLabel("Izbor koana po značenju")
                ModelRow(ModelCatalog.meaning, refresh, selected = null, onSelect = null)
                Text("Bez ovog modela koan se bira po ključnim riječima i raspoloženju koje izabereš.", style = SmallStyle)
            }

            Panel {
                SectionLabel("Ostalo")
                SwitchRow("Blaga vibracija na udah i izdah", haptics, { haptics = it; s.haptics = it })
            }

            Panel {
                SectionLabel("Izvori")
                Text("Koani: Mumon, Bezvratna kapija (1228), po engleskom prevodu Nyogena Senzakija i Paula Repsa (1934), " +
                    "javno vlasništvo. Prepričano na naš jezik. Poruke su pisane za ovu aplikaciju.", style = SmallStyle)
                Text("Whisper (OpenAI, MIT) kroz whisper.cpp. Model za značenje: multilingual-e5-small (MIT).", style = SmallStyle)
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
                ready -> Text("Preuzeto", style = SmallStyle.copy(color = Ink.accent))
                p != null -> Text("Preuzimam… %d%%".format((p * 100).toInt()), style = SmallStyle)
            }
        }
        when {
            ready -> GlassButton("Obriši", { app.models.delete(choice); ready = false })
            progress == null -> GlassButton("Preuzmi", { app.models.download(choice); progress = 0f })
        }
    }
}
