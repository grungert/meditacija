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
    data class CheckIn(val kind: String, val config: SessionConfig? = null, val sessionId: Long? = null, val promptId: Long? = null,
        /** For a note coming back the day after a hard one: that note. */
        val follows: Long? = null) : Screen()
    data class Session(val config: SessionConfig) : Screen()
    data class Koan(val sessionId: Long?, val entryId: Long?) : Screen()
    data object Journal : Screen()
    data object Stats : Screen()
    data object Settings : Screen()
    data class Week(val entryId: Long?) : Screen()
    // the screens that open from more than one place carry the one to go back to
    data class Similar(val entryId: Long, val back: Screen = Journal) : Screen()
    data class KoanNotes(val koanId: String) : Screen()
    data class Thread(val ids: List<Long>, val title: String, val back: Screen = Journal) : Screen()
    data object Fifty : Screen()
    data class Constellations(val back: Screen = Journal) : Screen()
    data object Intro : Screen()
    data object Help : Screen()
    /** One thing from the notes, and what tends to come with it. */
    data class Focus(val point: String, val label: String, val back: Screen = Journal) : Screen()
}

/** The screens read in daylight when the sun is up; sessions and check-ins keep the night look. */
private fun readsInDaylight(sc: Screen) = sc is Screen.Home || sc is Screen.Journal || sc is Screen.Stats || sc is Screen.Settings ||
    sc is Screen.Thread || sc is Screen.Similar || sc is Screen.Focus || sc is Screen.Setup

/** The one sky shared by every screen, plus hooks the current screen can set. */
class SkyHost(val engine: BreathEngine, val day: DayClock) {
    var showCircle by mutableStateOf(false)
    var dark by mutableStateOf(false)
    var sheetOpen by mutableStateOf(false)
    var foreground by mutableStateOf(true)
    var lake by mutableStateOf(false)   // a sheet covers the screen, so the bottom bar steps aside
    var season by mutableStateOf(com.grungert.nebo.sky.Season.SUMMER)
    var weather by mutableStateOf(com.grungert.nebo.sky.Weather.CLEAR)
    /** The weather is passing for now (a sit has ended): set again from the settings, it is back at once. */
    var clearing by mutableStateOf(false)
    private var rainLevel = 0f
    /** Whether the sky has drawn its first frame, for the opening loader to give way to it. */
    var skyDrawn by mutableStateOf(false)
    /** Whether the sun is up behind the screens, with some slack so dusk does not flicker between the two looks. */
    var sunUp by mutableStateOf(false)

    fun applyWeatherSettings(seasonMode: String, weatherMode: String) {
        season = com.grungert.nebo.sky.Seasons.season(seasonMode)
        weather = com.grungert.nebo.sky.Seasons.weather(weatherMode, season)
        clearing = false
    }
    val ambience = com.grungert.nebo.audio.Ambience()
    val breath = com.grungert.nebo.audio.BreathSound()

    /** Keeps the meadow's sound in step with the sky: wind and breath, birds by day, crickets by night. */
    fun tuneAmbience(pal: com.grungert.nebo.sky.Palette, sunAmount: Float) {
        if (!skyDrawn) skyDrawn = true
        // by the clock, not the light through the clouds: a rainy noon is still day
        val sun = com.grungert.nebo.sky.DayCycle.sunAmount(day.hour)
        if (!sunUp && sun > 0.55f) sunUp = true else if (sunUp && sun < 0.3f) sunUp = false
        val e = engine
        ambience.wind = e.wind
        ambience.swell = if (!e.paced) 0.25f else when (e.phase) {
            Phase.OUT -> kotlin.math.sin(Math.PI * e.progress).toFloat()
            Phase.IN -> 0.1f
            else -> 0f
        }
        // birds stay home in rain and winter; crickets sing in the warm half of the year
        // once the weather has passed, the meadow sounds as on a clear day: the rain dies away over a few seconds
        val w = if (clearing) com.grungert.nebo.sky.Weather.CLEAR else weather; val s = season
        ambience.day = sunAmount * (1f - pal.stars) * (if (w == com.grungert.nebo.sky.Weather.RAIN || w == com.grungert.nebo.sky.Weather.SNOW || s == com.grungert.nebo.sky.Season.WINTER) 0f else 1f)
        ambience.night = pal.stars * when (s) { com.grungert.nebo.sky.Season.WINTER -> 0f; com.grungert.nebo.sky.Season.AUTUMN -> 0.45f; else -> 1f } * (if (w == com.grungert.nebo.sky.Weather.RAIN) 0f else 1f)
        rainLevel += ((if (w == com.grungert.nebo.sky.Weather.RAIN) 1f else 0f) - rainLevel) * 0.012f
        ambience.rain = rainLevel
    }
    var onTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
    var onPhase: ((Phase) -> Unit)? = null
    var onFrame: ((Float) -> Unit)? = null
}

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_SHORTCUT = "shortcut" }

    private var screen by mutableStateOf<Screen>(Screen.Home)
    /** The opening loader, on a fresh start only: not when the system recreates the activity, nor with animations off. */
    private var loading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // the first time, a short look at what Nebo is and how to begin
        if (!app.settings.introSeen) screen = Screen.Intro
        // only a fresh launch acts on the notification or widget that opened it: when the system recreates the activity
        // (dark mode at sunset, a font change) or it comes back from Recents, the same intent arrives again, and must not
        // start a second session or reopen a check-in
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) handle(intent)
        loading = savedInstanceState == null && SysSettings.Global.getFloat(contentResolver, SysSettings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        // the system's opening screen hands over to the loader at once: its orb fades as the loader's starts to breathe
        splashScreen.setOnExitAnimationListener { view ->
            view.animate().alpha(0f).setDuration(if (loading) 180 else 300).withEndAction { view.remove() }.start()
        }
        setContent { App() }
    }

    // leaving the app: the widget shows what changed (a session, a note, the hour)
    override fun onStop() {
        super.onStop()
        com.grungert.nebo.widget.NeboWidget.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A tap on a random-moment notification opens the quick check-in. Once handled, the extras are dropped. */
    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(Esm.EXTRA_PROMPT, -1L) ?: -1L
        if (id > 0) screen = Screen.CheckIn("esm", promptId = id)
        if (intent?.getBooleanExtra(Esm.EXTRA_WEEK, false) == true) screen = Screen.CheckIn("week")
        if (intent?.getBooleanExtra(Esm.EXTRA_EVENING, false) == true) screen = Screen.CheckIn("evening")
        intent?.getLongExtra(Esm.EXTRA_FOLLOW, -1L)?.takeIf { it > 0 }?.let { screen = Screen.CheckIn("followup", follows = it) }
        // "Započni" on the daily reminder goes straight into a session set up like the last one
        if (intent?.getBooleanExtra(Esm.EXTRA_START, false) == true) screen = Screen.Session(lastConfig())
        if (intent?.getBooleanExtra(Esm.EXTRA_FIFTY, false) == true) {
            app.fiftyCall = intent.getLongExtra(Esm.EXTRA_FIFTY_CALL, 0L).takeIf { it > 0 }
            screen = Screen.Fifty
        }
        // "3 minuta" on the daily reminder: the breathing space, straight away
        if (intent?.getBooleanExtra(Esm.EXTRA_HOURGLASS, false) == true) screen = Screen.Session(lastConfig().copy(type = "hourglass", checkInBefore = false, eyesClosed = false))
        // a launcher shortcut or the quick-settings tile
        when (intent?.getStringExtra(EXTRA_SHORTCUT)) {
            "hourglass" -> screen = Screen.Session(lastConfig().copy(type = "hourglass", checkInBefore = false, eyesClosed = false))
            "urge" -> screen = Screen.Session(lastConfig().copy(type = "urge", minutes = 5, checkInBefore = false, eyesClosed = false))
            "breath" -> lastConfig().let { c -> screen = Screen.Session(c.copy(type = "breath", minutes = if (c.minutes in listOf(3, 5, 10, 15, 20, 0)) c.minutes else 5, checkInBefore = false)) }
            "note" -> screen = Screen.CheckIn("note")
        }
        // the suggested session on the home-screen widget, started straight away
        intent?.getStringExtra(com.grungert.nebo.widget.NeboWidget.EXTRA_START_TYPE)?.let { t ->
            app.settings.sessionType = t
            screen = Screen.Session(lastConfig().copy(checkInBefore = false))
        }
        // "Zapiši" on the home-screen widget
        if (intent?.getBooleanExtra(com.grungert.nebo.widget.NeboWidget.EXTRA_NOTE, false) == true) screen = Screen.CheckIn("note")
        intent?.let { setIntent(Intent(it).replaceExtras(null as android.os.Bundle?)) }
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
                is Screen.Similar -> sc.back
                is Screen.Thread -> sc.back
                is Screen.Constellations -> sc.back
                is Screen.Focus -> sc.back
                Screen.Fifty -> Screen.Home
                Screen.Help -> Screen.Settings
                Screen.Intro -> { app.settings.introSeen = true; Screen.Home }
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
                lake = host.lake,
                clearing = host.clearing,
            )
            // the light of the screens follows the sky, but only where they read in daylight
            val light = host.sunUp && readsInDaylight(screen)
            if (com.grungert.nebo.ui.Ink.day != light) com.grungert.nebo.ui.Ink.day = light
            AnimatedContent(screen, transitionSpec = {
                (fadeIn(androidx.compose.animation.core.tween(320)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(380), initialScale = 0.97f))
                    .togetherWith(fadeOut(androidx.compose.animation.core.tween(200)))
            }, label = "screen") { sc ->
                when (sc) {
                    Screen.Home -> HomeScreen(host, go)
                    Screen.Setup -> SetupScreen(host, go)
                    is Screen.CheckIn -> CheckInScreen(sc, host, go)
                    is Screen.Session -> SessionScreen(sc.config, host, scenery, go)
                    is Screen.Koan -> KoanScreen(sc, host, go)
                    Screen.Journal -> JournalScreen(host, go)
                    Screen.Stats -> StatsScreen(go)
                    Screen.Settings -> SettingsScreen(host, go)
                    is Screen.Week -> com.grungert.nebo.ui.WeekScreen(sc, go)
                    is Screen.Similar -> com.grungert.nebo.ui.SimilarScreen(sc, go)
                    is Screen.Thread -> com.grungert.nebo.ui.ThreadScreen(sc, go)
                    Screen.Fifty -> com.grungert.nebo.ui.FiftyScreen(host, go)
                    is Screen.Constellations -> com.grungert.nebo.ui.ConstellationsScreen(sc, host, go)
                    Screen.Intro -> com.grungert.nebo.ui.IntroScreen(go)
                    Screen.Help -> com.grungert.nebo.ui.HelpScreen(go)
                    is Screen.KoanNotes -> com.grungert.nebo.ui.KoanNotesScreen(sc, go)
                    is Screen.Focus -> com.grungert.nebo.ui.FocusScreen(sc, go)
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
                }, onMic = { screen = Screen.CheckIn("note") }, Modifier.navigationBarsPadding().padding(bottom = 12.dp))
            }
            if (loading) com.grungert.nebo.ui.Loader(ready = host.skyDrawn) { loading = false }
        }
    }
}
