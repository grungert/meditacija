package com.grungert.nebo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grungert.nebo.SkyHost
import com.grungert.nebo.app
import com.grungert.nebo.sky.Season
import com.grungert.nebo.sky.Weather

/** Fixed times of day a person can hold the sky at, besides following the clock or cycling through the day. */
val TIME_OPTIONS = listOf("zora" to "6.2", "dan" to "12.5", "zalazak" to "18.9", "sumrak" to "20.3", "noć" to "23.5")

/**
 * Everything about the surroundings, changed live while sitting: the sound of the meadow and how loud it is,
 * the time of day, the season and the weather. Choices are kept for the next session too.
 */
@Composable
fun AmbienceSheet(
    host: SkyHost, soundOn: Boolean, onSound: (Boolean) -> Unit, breathOn: Boolean? = null, onBreath: (Boolean) -> Unit = {},
    lakeAllowed: Boolean = true, onClose: () -> Unit,
) {
    val s = LocalContext.current.app.settings
    var volume by remember { mutableStateOf(s.ambientVolume) }
    var timeMode by remember { mutableStateOf(s.timeMode) }
    var seasonMode by remember { mutableStateOf(s.seasonMode) }
    var weatherMode by remember { mutableStateOf(s.weatherMode) }
    var front by remember { mutableStateOf(s.meadowFront) }
    var lake by remember { mutableStateOf(s.lakeScene && lakeAllowed) }

    fun time(v: String) { timeMode = v; s.timeMode = v; host.day.mode = v }
    fun season(v: String) { seasonMode = v; s.seasonMode = v; host.applyWeatherSettings(v, weatherMode) }
    fun weather(v: String) { weatherMode = v; s.weatherMode = v; host.applyWeatherSettings(seasonMode, v) }

    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(interactionSource = null, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 620.dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Color(0xF20C1630))
                .clickable(interactionSource = null, indication = null) {}
                .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ambijent", style = HeadStyle.copy(shadow = null), modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Zatvori", onClick = onClose).padding(10.dp)) {
                    Icon(NeboIcon.Close, Ink.text, 18.dp)
                }
            }

            SectionLabel("Zvuk")
            SwitchRow("Zvuk prirode", soundOn, onSound, sub = "Vjetar, ptice danju, cvrčci noću, kiša.")
            if (soundOn) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("tiho", style = SmallStyle.copy(shadow = null))
                Slider(
                    volume, { volume = it; host.ambience.volume = it; host.breath.volume = it * 0.8f }, Modifier.weight(1f), valueRange = 0.1f..1f,
                    onValueChangeFinished = { s.ambientVolume = volume },
                    colors = SliderDefaults.colors(thumbColor = Ink.paper, activeTrackColor = Ink.accent, inactiveTrackColor = Ink.surfaceHigh),
                )
                Text("jače", style = SmallStyle.copy(shadow = null))
            }

            if (breathOn != null) SwitchRow("Zvuk daha", breathOn, onBreath, sub = "Tihi šum vazduha: raste na udahu, spušta se na izdahu.")

            SectionLabel("Mjesto")
            if (lakeAllowed) ChipRow {
                Chip("livada", !lake, { lake = false; s.lakeScene = false; host.lake = false })
                Chip("jezero", lake, { lake = true; s.lakeScene = true; host.lake = true })
            } else Text("Šetnja uvijek ide livadom.", style = SmallStyle.copy(shadow = null))
            if (!lake) SwitchRow("Prednji plan livade", front, { front = it; s.meadowFront = it; host.foreground = it },
                sub = "Nekoliko krupnih tamnih vlati uz donju ivicu, za dubinu.")

            SectionLabel("Doba dana")
            ChipRow {
                Chip("po satu", timeMode == "clock", { time("clock") })
                Chip("ciklus", timeMode == "cycle", { time("cycle") })
                for ((label, h) in TIME_OPTIONS) Chip(label, timeMode == h, { time(h) })
            }

            SectionLabel("Vrijeme")
            ChipRow {
                Chip("kako dođe", weatherMode == "auto", { weather("auto") })
                for (w in Weather.entries) Chip(w.label, weatherMode == w.name, { weather(w.name) })
            }

            SectionLabel("Godišnje doba")
            ChipRow {
                Chip("po kalendaru", seasonMode == "auto", { season("auto") })
                for (sn in Season.entries) Chip(sn.label, seasonMode == sn.name, { season(sn.name) })
            }
        }
    }
}
