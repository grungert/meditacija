package com.grungert.nebo.ui

import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
/** A round glass control for the session's top bar. */
@Composable
private fun RoundControl(icon: NeboIcon, label: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Ink.surfaceHigh).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, Ink.text, 20.dp) }
}

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
    var paused by remember { mutableStateOf(false) }
    var ambienceOpen by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(cfg.ambient) }
    val planned = cfg.minutes * 60

    fun pause(on: Boolean) {
        if (ended) return
        paused = on
        host.engine.running = !on
        if (on) { word = ""; showUi = true; host.dark = false }
        else {
            // start again from a fresh inhale, and let the dark return for closed eyes
            host.engine.setPatternAndRestart(patternById(cfg.pattern))
            word = if (cfg.paced) host.engine.phase.label else ""
            lastTouch = 0f; host.dark = cfg.eyesClosed
        }
    }

    fun finish() {
        if (ended) return
        ended = true
        paused = false; host.engine.running = true
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
            if (!ended && !paused) {
                elapsed += dt
                lastTouch += dt
                if (ambienceOpen) lastTouch = 0f
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
            if (cfg.eyesClosed && !ended && !paused) { peek = true; host.dark = false }
            if (cfg.type == "thoughts" && !ended && !paused) { scenery.addThought(pos.x, pos.y); thoughts = scenery.thoughtCount }
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
                Lifecycle.Event.ON_START -> if (soundOn && !ended) host.ambience.start()
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
            e.paced = false; e.walkOn = false; e.running = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        // time along the edge of the screen: a thin line that goes once around, clockwise from the top
        if (planned > 0) {
            val glow by animateFloatAsState(when { host.dark && !peek -> 0f; showUi || paused -> 0.75f; else -> 0.3f }, tween(600), label = "edge")
            val density = LocalDensity.current
            Box(Modifier.fillMaxSize().drawWithCache {
                val i = with(density) { 3.dp.toPx() }
                val r = with(density) { 30.dp.toPx() }
                val w = size.width; val h = size.height
                val edge = Path().apply {
                    moveTo(w / 2, i)
                    lineTo(w - i - r, i); arcTo(Rect(w - i - 2 * r, i, w - i, i + 2 * r), -90f, 90f, false)
                    lineTo(w - i, h - i - r); arcTo(Rect(w - i - 2 * r, h - i - 2 * r, w - i, h - i), 0f, 90f, false)
                    lineTo(i + r, h - i); arcTo(Rect(i, h - i - 2 * r, i + 2 * r, h - i), 90f, 90f, false)
                    lineTo(i, i + r); arcTo(Rect(i, i, i + 2 * r, i + 2 * r), 180f, 90f, false)
                    close()
                }
                val measure = PathMeasure().apply { setPath(edge, false) }
                val done = Path()
                val stroke = Stroke(with(density) { 2.dp.toPx() }, cap = StrokeCap.Round)
                onDrawBehind {
                    if (glow <= 0.01f) return@onDrawBehind
                    drawPath(edge, Color.White.copy(alpha = 0.08f * glow), style = stroke)
                    done.reset()
                    measure.getSegment(0f, measure.length * (elapsed / planned).coerceIn(0f, 1f), done, true)
                    drawPath(done, Ink.accent.copy(alpha = glow), style = stroke)
                }
            })
        }

        // the phase word sits under the drop (centred 36% from the top, at most 0.52 of its base size across),
        // with one dot per second of the phase instead of a number
        if (cfg.paced && !ended && !paused) BoxWithConstraints(Modifier.fillMaxSize()) {
            val e = host.engine
            val fade = minOf(1f, e.phaseT / 0.45f, (e.phaseDuration - e.phaseT) / 0.45f).coerceAtLeast(0.25f)
            val base = minOf(maxWidth * 0.5f, maxHeight * 0.32f)
            // white on a white cloud disappears, so a soft shade sits behind the word and the dots:
            // a wide oval that fades out, with no edge of its own
            val shade = Color(0xFF14223F)
            Column(Modifier.fillMaxWidth().offset(y = maxHeight * 0.36f + base * 0.52f + 22.dp)
                .drawBehind {
                    val r = size.width * 0.30f
                    scale(1f, 0.42f, pivot = center) {
                        drawCircle(Brush.radialGradient(listOf(shade.copy(alpha = 0.30f), shade.copy(alpha = 0.12f), Color.Transparent), center, r), r, center)
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (elapsed < 0f) return@Column // reading elapsed refreshes the dots every frame
                Text(word, style = TitleStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 1.5.sp,
                    shadow = Shadow(shade.copy(alpha = 0.55f), Offset(0f, 1f), 12f)), modifier = Modifier.alpha(fade))
                val n = ceil(e.phaseDuration - 0.01f).toInt().coerceIn(1, 12)
                val done = e.phaseT.toInt()
                // each dot has a dark ring under it; the ones still to come are dark with a light rim, so both read on cloud and sky
                Canvas(Modifier.size(width = (14 * n).dp, height = 12.dp)) {
                    val step = size.width / n
                    val r = 3.4.dp.toPx()
                    for (i in 0 until n) {
                        val c = Offset(step * (i + 0.5f), size.height / 2)
                        drawCircle(shade.copy(alpha = 0.30f), r + 1.6.dp.toPx(), c)
                        if (i <= done) drawCircle(Color.White, r, c)
                        else {
                            drawCircle(shade.copy(alpha = 0.40f), r, c)
                            drawCircle(Color.White.copy(alpha = 0.70f), r, c, style = Stroke(1.dp.toPx()))
                        }
                    }
                }
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

        AnimatedVisibility(showUi || ended || paused || ambienceOpen, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().screenPadding().padding(vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        val shown = if (planned > 0) (planned - elapsed).coerceAtLeast(0f) else elapsed
                        Text("%d:%02d".format(shown.toInt() / 60, shown.toInt() % 60),
                            style = NumberStyle.copy(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp))
                        Text(when {
                            cfg.type == "thoughts" -> "oblaka: $thoughts"
                            planned > 0 -> "preostalo od ${cfg.minutes} min"
                            else -> "bez ograničenja"
                        }, style = SmallStyle)
                    }
                    if (!ended) {
                        RoundControl(NeboIcon.Settings, "Ambijent") { ambienceOpen = true; host.dark = false }
                        RoundControl(if (paused) NeboIcon.Play else NeboIcon.Pause, if (paused) "Nastavi" else "Pauza") { pause(!paused) }
                        RoundControl(NeboIcon.Stop, "Završi") { finish() }
                    }
                }
            }
        }

        if (paused) Column(Modifier.align(Alignment.Center).offset(y = 96.dp).screenPadding(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Pauzirano", style = TitleStyle.copy(fontWeight = FontWeight.Light))
            Text("Vrijeme stoji. Kad budeš spreman, dah kreće ispočetka.", style = SmallStyle, textAlign = TextAlign.Center)
            GlassButton("Nastavi", { pause(false) }, strong = true, icon = NeboIcon.Play)
        }

        if (ambienceOpen && !ended) AmbienceSheet(host, soundOn, { on ->
            soundOn = on; app.settings.ambient = on
            if (on) host.ambience.start() else host.ambience.stop()
        }, onClose = { ambienceOpen = false; lastTouch = 0f; host.dark = cfg.eyesClosed && !paused })

        if (ended) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
            Text("Sesija je završena.", style = HeadStyle)
        }
    }
}
