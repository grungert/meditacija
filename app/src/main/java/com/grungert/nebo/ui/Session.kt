package com.grungert.nebo.ui

import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grungert.nebo.Screen
import com.grungert.nebo.SkyHost
import com.grungert.nebo.app
import com.grungert.nebo.audio.Bell
import com.grungert.nebo.audio.Cue
import com.grungert.nebo.data.Session
import com.grungert.nebo.sky.Phase
import com.grungert.nebo.sky.Scenery
import com.grungert.nebo.sky.patternById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** Haptics that carry the breath when the eyes are closed: a swell to breathe in, an ebb to breathe out. */
private fun breathHaptic(v: Vibrator, p: Phase, eyesClosed: Boolean) {
    if (!eyesClosed) {
        v.vibrate(VibrationEffect.createOneShot(if (p == Phase.IN) 22 else 12, if (p == Phase.IN) 90 else 50))
        return
    }
    if (!v.hasAmplitudeControl()) {
        when (p) {
            Phase.IN -> v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 120, 40), -1))
            Phase.OUT -> v.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        return
    }
    when (p) {
        Phase.IN -> v.vibrate(VibrationEffect.createWaveform(LongArray(12) { if (it == 0) 0 else 90 }, IntArray(12) { minOf(150, it * 13) }, -1))
        Phase.OUT -> v.vibrate(VibrationEffect.createWaveform(LongArray(12) { if (it == 0) 0 else 90 }, IntArray(12) { maxOf(0, 110 - it * 10) }, -1))
        else -> v.vibrate(VibrationEffect.createOneShot(18, 60))
    }
}

@Composable
fun SessionScreen(cfg: SessionConfig, host: SkyHost, scenery: Scenery, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val vibrator = remember { context.getSystemService(VibratorManager::class.java).defaultVibrator }

    var elapsed by remember { mutableFloatStateOf(0f) }
    var sessionId by remember { mutableStateOf<Long?>(null) }
    var ended by remember { mutableStateOf(false) }
    var showUi by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableFloatStateOf(0f) }
    var thoughts by remember { mutableIntStateOf(0) }
    var word by remember { mutableStateOf("") }
    var peek by remember { mutableStateOf(false) }  // eyes-closed mode: light briefly returns after a touch
    val planned = cfg.minutes * 60

    fun finish() {
        if (ended) return
        ended = true
        host.showCircle = false; host.dark = false
        word = ""
        host.engine.paced = false
        scope.launch {
            launch { Bell.ring() }
            val id = sessionId
            if (id != null) withContext(Dispatchers.IO) {
                app.db.finishSession(id, System.currentTimeMillis(), elapsed.toInt(), scenery.thoughtCount)
            }
            delay(2500)
            host.ambience.stop()
            go(if (cfg.checkInAfter) Screen.CheckIn("after", config = cfg, sessionId = id) else Screen.Koan(id, null))
        }
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val e = host.engine
        e.walkOn = cfg.type == "walk"
        e.paced = cfg.paced
        e.running = true
        e.setPatternAndRestart(patternById(cfg.pattern))
        host.showCircle = cfg.paced
        host.dark = cfg.eyesClosed
        scenery.resetThoughts()
        if (cfg.type == "walk") scenery.resetWalkSpawns()
        host.ambience.volume = app.settings.ambientVolume
        if (cfg.ambient) host.ambience.start()
        host.onPhase = { p ->
            if (cfg.paced && !ended) {
                word = p.label
                if (app.settings.haptics || cfg.eyesClosed) runCatching { breathHaptic(vibrator, p, cfg.eyesClosed) }
                if (cfg.eyesClosed) Cue.play(when (p) { Phase.IN -> "in"; Phase.OUT -> "out"; else -> "hold" })
            }
        }
        // soft bells at the chosen interval, or at unexpected moments for a mindfulness bell
        fun gap(): Float = if (cfg.intervalMinutes < 0) 120f + kotlin.random.Random.nextFloat() * 240f else cfg.intervalMinutes * 60f
        var nextBell = if (cfg.intervalMinutes != 0) gap() else Float.MAX_VALUE
        host.onFrame = { dt ->
            if (!ended) {
                elapsed += dt
                lastTouch += dt
                if (lastTouch > 5f) { showUi = false; if (peek) { peek = false; host.dark = cfg.eyesClosed } }
                if (elapsed >= nextBell && (planned == 0 || elapsed < planned - 20)) {
                    nextBell += gap()
                    scope.launch { Bell.ring(soft = true) }
                }
                if (planned > 0 && elapsed >= planned) finish()
            }
        }
        host.onTap = { pos ->
            lastTouch = 0f; showUi = true
            if (cfg.eyesClosed && !ended) { peek = true; host.dark = false }
            if (cfg.type == "thoughts" && !ended) { scenery.addThought(pos.x, pos.y); thoughts = scenery.thoughtCount }
        }
        word = if (cfg.paced) e.phase.label else ""
        scope.launch {
            Bell.ring()
            sessionId = withContext(Dispatchers.IO) {
                app.db.insertSession(Session(startedAt = System.currentTimeMillis(), type = cfg.type, plannedSec = planned,
                    pattern = cfg.pattern, walk = cfg.type == "walk"))
            }
        }
        // the meadow falls silent while the app is in the background
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_STOP -> host.ambience.stop()
                Lifecycle.Event.ON_START -> if (cfg.ambient && !ended) host.ambience.start()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            view.keepScreenOn = false
            host.ambience.stop()
            host.onPhase = null; host.onFrame = null; host.onTap = null
            host.showCircle = false; host.dark = false
            e.paced = false; e.walkOn = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        // the phase word sits inside the breathing circle (the circle is centred 36% from the top)
        if (cfg.paced && !ended) BoxWithConstraints(Modifier.fillMaxSize()) {
            val e = host.engine
            val remaining = ceil(e.phaseDuration - e.phaseT).toInt()
            val fade = minOf(1f, e.phaseT / 0.45f, (e.phaseDuration - e.phaseT) / 0.45f).coerceAtLeast(0.25f)
            Column(Modifier.fillMaxWidth().offset(y = maxHeight * 0.36f - 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (elapsed < 0f) return@Column // reading elapsed refreshes the countdown every frame
                Text(word, style = TitleStyle.copy(fontSize = 38.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light, letterSpacing = 1.sp), modifier = Modifier.alpha(fade))
                Text("$remaining", style = SmallStyle)
            }
        }

        if (cfg.type == "thoughts") {
            AnimatedVisibility(elapsed < 9f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                Text("Kad primijetiš da je misao odlutala,\ndodirni nebo i pusti je.", style = HeadStyle.copy(fontSize = 24.sp),
                    textAlign = TextAlign.Center, modifier = Modifier.screenPadding())
            }
        }

        // eyes closed: the sky itself fades to black (see SkyScene); a touch brings the light back for a few seconds
        AnimatedVisibility(host.dark && elapsed in 1.5f..12f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Text("Zatvori oči.\nDah će te voditi.", style = HeadStyle.copy(color = Color(0x99FFFFFF), fontSize = 24.sp), textAlign = TextAlign.Center)
        }

        // drawn above the eyes-closed darkness, so the question is there for the first breaths
        val weekQuestion = remember { app.library.byId(app.settings.activeWeekKoan())?.question }
        if (weekQuestion != null) AnimatedVisibility(elapsed in 1.5f..12f && cfg.type != "thoughts", enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.screenPadding()) {
                Text("PITANJE SEDMICE", style = LabelStyle)
                Text(weekQuestion, style = HeadStyle.copy(fontSize = 24.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), textAlign = TextAlign.Center)
            }
        }

        AnimatedVisibility(showUi || ended, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().screenPadding().padding(vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val shown = if (planned > 0) (planned - elapsed).coerceAtLeast(0f) else elapsed
                    Text("%d:%02d".format(shown.toInt() / 60, shown.toInt() % 60), style = HeadStyle.copy(fontSize = 24.sp))
                    if (cfg.type == "thoughts") Text("· oblaka: $thoughts", style = SmallStyle)
                    Box(Modifier.weight(1f))
                    GlassButton(if (ended) "Završeno" else "Završi", { finish() }, enabled = !ended)
                }
            }
        }

        if (ended) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
            Text("Sesija je završena.", style = HeadStyle)
        }
    }
}
