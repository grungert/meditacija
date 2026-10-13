package com.grungert.nebo

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as SysSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.grungert.nebo.esm.Esm
import com.grungert.nebo.sky.BreathEngine
import com.grungert.nebo.sky.DayClock
import com.grungert.nebo.sky.Phase
import com.grungert.nebo.sky.SkyScene
import com.grungert.nebo.sky.patternById
import com.grungert.nebo.sky.rememberScenery
import com.grungert.nebo.ui.CheckInScreen
import com.grungert.nebo.ui.HomeScreen
import com.grungert.nebo.ui.JournalScreen
import com.grungert.nebo.ui.KoanScreen
import com.grungert.nebo.ui.SessionConfig
import com.grungert.nebo.ui.SessionScreen
import com.grungert.nebo.ui.SettingsScreen
import com.grungert.nebo.ui.SetupScreen
import com.grungert.nebo.ui.StatsScreen
import com.grungert.nebo.ui.lastConfig

sealed class Screen {
    data object Home : Screen()
    data object Setup : Screen()
    data class CheckIn(val kind: String, val config: SessionConfig? = null, val sessionId: Long? = null, val promptId: Long? = null) : Screen()
    data class Session(val config: SessionConfig) : Screen()
    data class Koan(val sessionId: Long?, val entryId: Long?) : Screen()
    data object Journal : Screen()
    data object Stats : Screen()
    data object Settings : Screen()
    data class Week(val entryId: Long?) : Screen()
    data class Similar(val entryId: Long) : Screen()
    data class KoanNotes(val koanId: String) : Screen()
    data class Thread(val ids: List<Long>, val title: String) : Screen()
}

/** The one sky shared by every screen, plus hooks the current screen can set. */
class SkyHost(val engine: BreathEngine, val day: DayClock) {
    var showCircle by mutableStateOf(false)
    var dark by mutableStateOf(false)
    var sheetOpen by mutableStateOf(false)
    var foreground by mutableStateOf(true)   // a sheet covers the screen, so the bottom bar steps aside
    var season by mutableStateOf(com.grungert.nebo.sky.Season.SUMMER)
    var weather by mutableStateOf(com.grungert.nebo.sky.Weather.CLEAR)

    fun applyWeatherSettings(seasonMode: String, weatherMode: String) {
        season = com.grungert.nebo.sky.Seasons.season(seasonMode)
        weather = com.grungert.nebo.sky.Seasons.weather(weatherMode, season)
    }
    val ambience = com.grungert.nebo.audio.Ambience()
    val breath = com.grungert.nebo.audio.BreathSound()

    /** Keeps the meadow's sound in step with the sky: wind and breath, birds by day, crickets by night. */
    fun tuneAmbience(pal: com.grungert.nebo.sky.Palette, sunAmount: Float) {
        val e = engine
        ambience.wind = e.wind
        ambience.swell = if (!e.paced) 0.25f else when (e.phase) {
            Phase.OUT -> kotlin.math.sin(Math.PI * e.progress).toFloat()
            Phase.IN -> 0.1f
            else -> 0f
        }
        // birds stay home in rain and winter; crickets sing in the warm half of the year
        val w = weather; val s = season
        ambience.day = sunAmount * (1f - pal.stars) * (if (w == com.grungert.nebo.sky.Weather.RAIN || w == com.grungert.nebo.sky.Weather.SNOW || s == com.grungert.nebo.sky.Season.WINTER) 0f else 1f)
        ambience.night = pal.stars * when (s) { com.grungert.nebo.sky.Season.WINTER -> 0f; com.grungert.nebo.sky.Season.AUTUMN -> 0.45f; else -> 1f } * (if (w == com.grungert.nebo.sky.Weather.RAIN) 0f else 1f)
        ambience.rain = if (w == com.grungert.nebo.sky.Weather.RAIN) 1f else 0f
    }
    var onTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
    var onPhase: ((Phase) -> Unit)? = null
    var onFrame: ((Float) -> Unit)? = null
}

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf<Screen>(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A tap on a random-moment notification opens the quick check-in. */
    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(Esm.EXTRA_PROMPT, -1L) ?: -1L
        if (id > 0) screen = Screen.CheckIn("esm", promptId = id)
        if (intent?.getBooleanExtra(Esm.EXTRA_WEEK, false) == true) screen = Screen.CheckIn("week")
        // "Započni" on the daily reminder goes straight into a session set up like the last one
        if (intent?.getBooleanExtra(Esm.EXTRA_START, false) == true) screen = Screen.Session(lastConfig())
        // "Zapiši" on the home-screen widget
        if (intent?.getBooleanExtra(com.grungert.nebo.widget.NeboWidget.EXTRA_NOTE, false) == true) screen = Screen.CheckIn("note")
    }

    @Composable
    private fun App() {
        val s = app.settings
        val host = remember {
            val reduced = SysSettings.Global.getFloat(contentResolver, SysSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            SkyHost(BreathEngine(patternById(s.pattern)).apply { paced = false; reducedMotion = reduced }, DayClock(s.timeMode))
                .also { it.applyWeatherSettings(s.seasonMode, s.weatherMode); it.foreground = s.meadowFront }
        }
        val scenery = rememberScenery()
        val go: (Screen) -> Unit = { screen = it }

        BackHandler(enabled = screen != Screen.Home) {
            screen = when (val sc = screen) {
                is Screen.Journal, is Screen.Stats, is Screen.Settings, is Screen.Setup, is Screen.Week -> Screen.Home
                is Screen.Similar -> Screen.Journal
                is Screen.Thread -> Screen.Journal
                is Screen.KoanNotes -> Screen.Home
                is Screen.CheckIn -> if (sc.kind == "before") Screen.Setup else Screen.Home
                else -> Screen.Home
            }
        }

        Box(Modifier.fillMaxSize()) {
            SkyScene(
                engine = host.engine, day = host.day, showCircle = host.showCircle, scenery = scenery,
                onTap = { host.onTap?.invoke(it) },
                onPhase = { host.onPhase?.invoke(it) },
                onFrame = { host.onFrame?.invoke(it) },
                onLight = { pal, sun -> host.tuneAmbience(pal, sun) },
                dark = host.dark,
                season = host.season,
                weather = host.weather,
                foreground = host.foreground,
            )
            AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { sc ->
                when (sc) {
                    Screen.Home -> HomeScreen(host, go)
                    Screen.Setup -> SetupScreen(host, go)
                    is Screen.CheckIn -> CheckInScreen(sc, host, go)
                    is Screen.Session -> SessionScreen(sc.config, host, scenery, go)
                    is Screen.Koan -> KoanScreen(sc, host, go)
                    Screen.Journal -> JournalScreen(host, go)
                    Screen.Stats -> StatsScreen(go)
                    Screen.Settings -> SettingsScreen(go)
                    is Screen.Week -> com.grungert.nebo.ui.WeekScreen(sc, go)
                    is Screen.Similar -> com.grungert.nebo.ui.SimilarScreen(sc, go)
                    is Screen.Thread -> com.grungert.nebo.ui.ThreadScreen(sc, go)
                    is Screen.KoanNotes -> com.grungert.nebo.ui.KoanNotesScreen(sc, go)
                }
            }
            // the four main places share a bottom bar; sessions and notes take the whole screen
            val tab = when (screen) {
                Screen.Home -> com.grungert.nebo.ui.Tab.TODAY
                Screen.Journal -> com.grungert.nebo.ui.Tab.JOURNAL
                Screen.Stats -> com.grungert.nebo.ui.Tab.OVERVIEW
                Screen.Settings -> com.grungert.nebo.ui.Tab.SETTINGS
                else -> null
            }
            androidx.compose.animation.AnimatedVisibility(tab != null && !host.sheetOpen, Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it }) {
                com.grungert.nebo.ui.BottomNav(tab ?: com.grungert.nebo.ui.Tab.TODAY, { t ->
                    screen = when (t) {
                        com.grungert.nebo.ui.Tab.TODAY -> Screen.Home
                        com.grungert.nebo.ui.Tab.JOURNAL -> Screen.Journal
                        com.grungert.nebo.ui.Tab.OVERVIEW -> Screen.Stats
                        com.grungert.nebo.ui.Tab.SETTINGS -> Screen.Settings
                    }
                }, Modifier.navigationBarsPadding().padding(bottom = 10.dp))
            }
        }
    }
}
