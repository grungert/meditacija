package com.grungert.nebo.ui

import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import com.grungert.nebo.koan.FourPointsReader
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateListOf
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
import com.grungert.nebo.data.BodyMark
import com.grungert.nebo.data.Session
import com.grungert.nebo.koan.MindHabits
import com.grungert.nebo.sky.Phase
import com.grungert.nebo.sky.Scenery
import com.grungert.nebo.sky.patternById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import androidx.compose.foundation.layout.imePadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** Haptics that carry the breath when the eyes are closed: a swell to breathe in, an ebb to breathe out. */
private val SHADE = Color(0xFF14223F)

/** The stages of a four-points session: which point (null for all together), its name, and what to notice. */
private val POINT_STAGES = listOf(
    Triple(FourPointsReader.THOUGHTS, "Misli", "Primijeti misli koje dolaze. Ne prati ih, samo vidi da su tu."),
    Triple(FourPointsReader.EMOTIONS, "Emocije", "Šta osjećaš? Daj osjećaju ime, tiho, u sebi."),
    Triple(FourPointsReader.BODY, "Tijelo", "Gdje se to osjeća u tijelu? Stomak, grudi, ramena, grlo, lice."),
    Triple(FourPointsReader.URGES, "Nagoni", "Šta te vuče da uradiš? Primijeti poriv, i ne idi za njim."),
    Triple(null, "Sve zajedno", "Sve četiri tačke odjednom. Kako jedna pomjera drugu?"),
)

/** Five equal parts of a timed session; an open-ended one moves on every two minutes and then stays with all four. */
private fun pointsStage(elapsed: Float, planned: Int): Int =
    if (planned > 0) (elapsed / (planned / 5f)).toInt().coerceIn(0, 4) else (elapsed / 120f).toInt().coerceAtMost(4)

/**
 * White on a white cloud disappears, so a soft shade sits behind text drawn straight on the sky:
 * a wide oval that fades out, with no edge of its own.
 */
private fun Modifier.softShade() = drawBehind {
    val r = size.width * 0.30f
    scale(1f, 0.42f, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(SHADE.copy(alpha = 0.30f), SHADE.copy(alpha = 0.12f), Color.Transparent), center, r), r, center)
    }
}

/**
 * What a part of a guided session asks, on a solid night-blue card edged in its colour, so it reads the same over cloud
 * or clear sky: an optional small label, the title, the words, and a thin bar for how far into this part.
 */
@Composable
private fun StageCard(title: String, line: String, tone: Color, progress: Float?, label: String? = null) {
    Column(
        Modifier.widthIn(max = 340.dp).padding(horizontal = 24.dp).clip(RoundedCornerShape(26.dp))
            .background(Color(0xF20E1834)).border(1.dp, tone.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (label != null) Text(label, style = LabelStyle.copy(color = tone.copy(alpha = 0.85f), shadow = null))
        Text(title, style = TitleStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Light, color = tone, shadow = null), textAlign = TextAlign.Center)
        Text(line, style = BodyStyle.copy(color = Ink.text, lineHeight = 23.sp, shadow = null), textAlign = TextAlign.Center)
        if (progress != null) Box(Modifier.padding(top = 4.dp).width(110.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(tone))
        }
    }
}

/** A line for the end of a body scan: where tension was touched today, and where it most often is over the last scans. */
private fun scanNote(today: List<String>, sessions: List<Session>): String {
    val names = today.mapNotNull { bodyRegion(it)?.name?.lowercase() }
    val now = if (names.isEmpty()) "Danas bez oznaka napetosti." else "Napetost danas: " + names.joinToString(", ") + "."
    val scans = sessions.filter { it.type == "bodyscan" && it.marks.isNotEmpty() }
    if (scans.size < 3) return now
    val top = scans.flatMap { s -> s.marks.map { it.region }.distinct() }.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return now
    val where = bodyRegion(top.key)?.where ?: return now
    return now + " Najčešće je nosiš $where (${top.value} od ${scans.size} skeniranja)."
}

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
    var endReady by remember { mutableStateOf(false) }   // the ending can be touched away
    var left by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(cfg.ambient) }
    var breathOn by remember { mutableStateOf(cfg.paced && app.settings.breathSound) }
    val planned = cfg.plannedSeconds
    // body scan: the places touched, with the second of each touch, and where the figure stands on screen
    val marks = remember { mutableStateListOf<Pair<BodyMark, Float>>() }
    val figure = remember { arrayOfNulls<Rect>(1) }
    // clouds: where the mind went, asked at the end
    val wander = remember { mutableStateListOf<String>() }
    var endNote by remember { mutableStateOf<String?>(null) }
    val asksWander = cfg.type == "thoughts"
    // the wave: what pulls and how strongly, asked before the time starts and again at the end
    val asksWave = cfg.type == "urge"
    var waveUrge by remember { mutableStateOf<String?>(null) }
    var waveBefore by remember { mutableIntStateOf(6) }
    var waveAfter by remember { mutableIntStateOf(6) }
    var waveStarted by remember { mutableStateOf(!asksWave) }
    var waveRated by remember { mutableStateOf(false) }
    // RAIN: the step, the feelings named, the kind words; the places touched are the body-scan marks
    val isRain = cfg.type == "rain"
    var rainStep by remember { mutableIntStateOf(0) }
    val rainMoods = remember { mutableStateListOf<String>() }
    var rainWords by remember { mutableStateOf("") }
    var rainEntry by remember { mutableStateOf<kotlinx.coroutines.Deferred<Long>?>(null) }

    fun pause(on: Boolean) {
        if (ended) return
        paused = on
        host.engine.running = !on
        if (on) { word = ""; showUi = true; host.dark = false; host.breath.phase = 0 }
        else {
            // start again from a fresh inhale, and let the dark return for closed eyes
            host.engine.setPatternAndRestart(patternById(cfg.pattern))
            word = if (cfg.paced) host.engine.phase.label else ""
            lastTouch = 0f; host.dark = cfg.eyesClosed
        }
    }

    fun proceed() {
        if (left) return
        left = true
        val id = sessionId
        if (id != null && wander.isNotEmpty()) { val w = wander.toList(); app.scope.launch(Dispatchers.IO) { app.db.setSessionWander(id, w) } }
        if (id != null && asksWave && waveStarted && waveRated) { val a = waveAfter; app.scope.launch(Dispatchers.IO) { app.db.setSessionUrgeAfter(id, a) } }
        // a wave that never began has nothing to look back on
        if (asksWave && !waveStarted) { go(Screen.Home); return }
        // RAIN has already been its own note: straight on to what it might want to read
        val note = rainEntry
        if (note != null) { app.scope.launch(Dispatchers.Main) { go(Screen.Koan(id, note.await())) }; return }
        go(if (cfg.checkInAfter) Screen.CheckIn("after", config = cfg, sessionId = id) else Screen.Koan(id, null))
    }

    /**
     * The ending is unhurried: the bell rings out in full (it plays on the app's scope, so leaving the screen
     * never cuts it), the light returns, the meadow's sound fades over a few seconds, and only when the bell has
     * died away does the note come. A touch moves on sooner.
     */
    fun finish() {
        if (ended) return
        ended = true
        paused = false; host.engine.running = true
        host.showCircle = false; host.dark = false
        word = ""
        host.engine.paced = false
        host.ambience.stop(slowly = true)
        host.breath.stop(slowly = true)
        app.scope.launch { Bell.ring() }
        val id = sessionId
        val touched = marks.map { it.first }
        // written on the app's scope, so leaving at once does not lose it
        val sec = elapsed.toInt(); val n = scenery.thoughtCount
        val written = if (id != null) app.scope.launch(Dispatchers.IO) { app.db.finishSession(id, System.currentTimeMillis(), sec, n, touched) } else null
        // RAIN keeps what was found as a note: the feelings named, the body where they sat, the words of kindness
        if (isRain && (rainMoods.isNotEmpty() || touched.isNotEmpty() || rainWords.isNotBlank())) {
            val body = touched.mapNotNull { bodyRegion(it.region)?.label?.ifBlank { null } }.distinct()
            val entry = com.grungert.nebo.data.Entry(createdAt = System.currentTimeMillis(), kind = "rain", sessionId = id,
                text = rainWords.trim().ifBlank { null }, moods = rainMoods.toList(), points = com.grungert.nebo.data.FourPoints(body = body))
            rainEntry = app.scope.async(Dispatchers.IO) { app.db.insertEntry(entry).also { app.scope.launch { app.recommender.tagNotes() } } }
        }
        scope.launch {
            written?.join()   // the note about this scan reads it back among the others
            if (cfg.type == "bodyscan") endNote = withContext(Dispatchers.IO) { scanNote(touched.map { it.region }.distinct(), app.db.sessions()) }
            delay(1500)
            endReady = true
            // after the clouds and the wave the ending waits for an answer; otherwise it moves on by itself
            if (!asksWander && !(asksWave && waveStarted)) { delay(7000); proceed() }
        }
    }

    DisposableEffect(Unit) { app.sitting = true; onDispose { app.sitting = false } }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        // by the lake, if that is where the person sits; the walk always goes through the meadow
        // the hourglass stands on a veranda by the water, so it always has the lake behind it
        // and the wave is a sea of its own over the water
        host.lake = (app.settings.lakeScene || cfg.type == "hourglass" || cfg.type == "urge") && cfg.type != "walk"
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
        host.breath.volume = app.settings.ambientVolume * 0.8f
        if (breathOn) host.breath.start()
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
        var stageAt = -1
        var lastGust = 1f; var lastChime = -100f
        host.onFrame = { dt ->
            if (!ended && !paused && waveStarted) {
                elapsed += dt
                lastTouch += dt
                host.breath.phase = when (e.phase) { Phase.IN -> 1; Phase.OUT -> 2; else -> 0 }
                host.breath.progress = if (e.phaseDuration > 0f) e.phaseT / e.phaseDuration else 0f
                if (ambienceOpen) lastTouch = 0f
                if (lastTouch > 5f) { showUi = false; if (peek) { peek = false; host.dark = cfg.eyesClosed } }
                // a soft bell as the four points or the parts of the hourglass turn from one to the next;
                // the body scan moves on with a light tick, and rings once for the whole body
                val st = when (cfg.type) {
                    "points" -> pointsStage(elapsed, planned)
                    "hourglass" -> hourglassStage(elapsed)
                    "bodyscan" -> scanStage(elapsed, planned).first
                    "urge" -> waveStage(elapsed, planned)
                    "rain" -> rainStep
                    else -> -1
                }
                if (cfg.type == "hourglass" && soundOn) {
                    val g = windGust(elapsed)
                    if (g > 0.93f && lastGust <= 0.93f && elapsed - lastChime > 10f) { lastChime = elapsed; app.scope.launch { Bell.chime() } }
                    lastGust = g
                }
                if (st != stageAt) {
                    if (stageAt >= 0) when (cfg.type) {
                        "bodyscan" -> if (st == BODY_REGIONS.lastIndex) scope.launch { Bell.ring(soft = true) }
                            else if (app.settings.haptics) runCatching { vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)) }
                        else -> scope.launch { Bell.ring(soft = true) }
                    }
                    stageAt = st
                }
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
            // a touch on the body marks tension there (in RAIN, where the feeling sits)
            val box = figure[0]
            if ((cfg.type == "bodyscan" || (isRain && rainStep == 2)) && !ended && !paused && box != null) {
                val current = if (isRain) null else BODY_REGIONS[scanStage(elapsed, planned).first].id
                regionAt(pos, box, current, lenient = isRain)?.let { r ->
                    marks += BodyMark(r, ((pos.x - box.left) / box.width).coerceIn(0f, 1f), ((pos.y - box.top) / box.height).coerceIn(0f, 1f)) to elapsed
                    if (app.settings.haptics) runCatching { vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)) }
                }
            }
        }
        word = if (cfg.paced) e.phase.label else ""
        scope.launch {
            // the session is written first and the bell rings after, so even a session ended in its first second has a row
            sessionId = withContext(Dispatchers.IO) {
                app.db.insertSession(Session(startedAt = System.currentTimeMillis(), type = cfg.type, plannedSec = planned,
                    pattern = cfg.pattern, walk = cfg.type == "walk")).also { id -> cfg.beforeEntryId?.let { app.db.linkEntryToSession(it, id) } }
            }
            if (!asksWave) Bell.ring()   // the wave rings when it starts, after its question
        }
        // the meadow falls silent while the app is in the background
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_STOP -> { host.ambience.stop(); host.breath.stop() }
                Lifecycle.Event.ON_START -> {
                    if (soundOn && !ended) host.ambience.start()
                    if (breathOn && !ended) host.breath.start()
                }
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            // left before the end (Back, a notification): the time actually sat still counts, and so do the places touched;
            // after the clouds, the chips picked before leaving are kept
            val id = sessionId
            // a wave left at its first question never began: no row for it
            if (id != null && asksWave && !waveStarted) { app.scope.launch(Dispatchers.IO) { app.db.deleteSession(id) } }
            else if (id != null && !ended) {
                val sec = elapsed.toInt(); val n = scenery.thoughtCount; val touched = marks.map { it.first }
                app.scope.launch(Dispatchers.IO) { app.db.finishSession(id, System.currentTimeMillis(), sec, n, touched) }
            }
            if (id != null && !left && wander.isNotEmpty()) { val w = wander.toList(); app.scope.launch(Dispatchers.IO) { app.db.setSessionWander(id, w) } }
            view.keepScreenOn = false
            host.ambience.stop()
            host.breath.stop()
            host.onPhase = null; host.onFrame = null; host.onTap = null
            host.showCircle = false; host.dark = false; host.lake = false
            e.paced = false; e.walkOn = false; e.running = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        // the far shore, reeds and lily pads, when sitting by the lake
        val lakeShown by animateFloatAsState(if (host.lake) 1f else 0f, tween(900), label = "lake")
        if (lakeShown > 0.01f) LakeShore(host, visible = lakeShown)
        // under a rainy sky, rain on the glass too
        if (host.weather == com.grungert.nebo.sky.Weather.RAIN) RainOnGlass(night = !Ink.day, still = host.engine.reducedMotion)
        // in the mist, the glass mists over too, and a finger wipes it
        if (host.weather == com.grungert.nebo.sky.Weather.FOG) FoggedGlass(
            night = !Ink.day,
            breathOut = { val b = host.engine; if (b.paced && b.phase == com.grungert.nebo.sky.Phase.OUT) kotlin.math.sin(Math.PI * b.progress).toFloat() else 0f },
            onTap = { host.onTap?.invoke(it) },
            hint = !app.settings.fogWiped, onWiped = { app.settings.fogWiped = true },
        )

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
            Column(Modifier.fillMaxWidth().offset(y = maxHeight * 0.36f + base * 0.52f + 22.dp).softShade(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (elapsed < 0f) return@Column // reading elapsed refreshes the dots every frame
                Text(word, style = TitleStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 1.5.sp,
                    shadow = Shadow(SHADE.copy(alpha = 0.55f), Offset(0f, 1f), 12f)), modifier = Modifier.alpha(fade))
                val n = ceil(e.phaseDuration - 0.01f).toInt().coerceIn(1, 12)
                val done = e.phaseT.toInt()
                // each dot has a dark ring under it; the ones still to come are dark with a light rim, so both read on cloud and sky
                Canvas(Modifier.size(width = (14 * n).dp, height = 12.dp)) {
                    val step = size.width / n
                    val r = 3.4.dp.toPx()
                    for (i in 0 until n) {
                        val c = Offset(step * (i + 0.5f), size.height / 2)
                        drawCircle(SHADE.copy(alpha = 0.30f), r + 1.6.dp.toPx(), c)
                        if (i <= done) drawCircle(Color.White, r, c)
                        else {
                            drawCircle(SHADE.copy(alpha = 0.40f), r, c)
                            drawCircle(Color.White.copy(alpha = 0.70f), r, c, style = Stroke(1.dp.toPx()))
                        }
                    }
                }
            }
        }

        // four points: thoughts, emotions, body and urges in turn, then all of them together
        if (cfg.type == "points" && !ended) {
            val stage = pointsStage(elapsed, planned)
            AnimatedContent(stage, transitionSpec = { fadeIn(tween(1500)) togetherWith fadeOut(tween(1500)) }, label = "points",
                modifier = Modifier.align(Alignment.Center)) { st ->
                val (key, title, line) = POINT_STAGES[st]
                // the constellation floats free above; the words sit on a solid night-blue card, narrow and edged in the
                // point's colour, so they read the same over cloud or clear sky without the sky showing through
                val tone = key?.let { POINT_COLORS.getValue(it) } ?: Ink.accent
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val order = listOf(FourPointsReader.THOUGHTS, FourPointsReader.EMOTIONS, FourPointsReader.BODY, FourPointsReader.URGES)
                    PointsConstellation(order.withIndex().associate { (i, k) -> k to (i <= st) }, Modifier.size(width = 170.dp, height = 118.dp),
                        highlight = key, labels = false)
                    // how far into this point, until the next one
                    val part = planned / 5f
                    StageCard(title, line, tone, if (planned > 0 && st < 4) ((elapsed - st * part) / part).coerceIn(0f, 1f) else null)
                }
            }
        }

        // the three-minute breathing space: an hourglass of light, and what each minute asks
        if (cfg.type == "hourglass" && !ended) {
            val hour = host.day.hour
            // following the calendar, the tree turns slowly near a change of season; a chosen season stays as it is
            val turning = remember { if (app.settings.seasonMode == "auto") seasonBlend() else Triple(host.season, host.season, 0f) }
            HourglassView(elapsed, HourglassLight(
                com.grungert.nebo.sky.Seasons.apply(com.grungert.nebo.sky.DayCycle.paletteAt(hour), host.season, host.weather),
                com.grungert.nebo.sky.DayCycle.sunAmount(hour) * com.grungert.nebo.sky.Seasons.sunThrough(host.weather),
                if (app.settings.seasonMode == "auto") turning.first else host.season, turning.second, turning.third,
                host.weather, host.engine.reducedMotion))
            val st = hourglassStage(elapsed)
            AnimatedContent(st, transitionSpec = { fadeIn(tween(1500)) togetherWith fadeOut(tween(1500)) }, label = "hourglass",
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp)) { s ->
                val (name, title, line) = HOURGLASS_STAGES[s]
                StageCard(title, line, Ink.accent, ((elapsed - s * 60f) / 60f).coerceIn(0f, 1f), label = "${name.uppercase()} · ${s + 1} OD 3")
            }
        }

        // the body scan: a figure of light, the warm light resting on one part after another, touches marking tension
        if (cfg.type == "bodyscan" && !ended) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                // the standing figure, as tall as the space between the time and the card allows
                val area = with(density) { Rect(maxWidth.toPx() * 0.06f, maxHeight.toPx() * 0.12f, maxWidth.toPx() * 0.94f, maxHeight.toPx() * 0.70f) }
                BodyScanView(elapsed, planned, area, marks.toList()) { figure[0] = it }
            }
            val (st, inStage, len) = scanStage(elapsed, planned)
            AnimatedContent(st, transitionSpec = { fadeIn(tween(1200)) togetherWith fadeOut(tween(1200)) }, label = "scan",
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp)) { s ->
                val region = BODY_REGIONS[s]
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StageCard(region.name, region.line, Ink.accent,
                        if (planned > 0 || s < BODY_REGIONS.lastIndex) (inStage / len).coerceIn(0f, 1f) else null,
                        label = if (s < BODY_REGIONS.lastIndex) "${s + 1} OD ${BODY_REGIONS.size - 1}" else "NA KRAJU")
                    if (elapsed < 25f) Text("Ako negdje osjetiš napetost, dodirni to mjesto na tijelu.", style = SmallStyle,
                        textAlign = TextAlign.Center, modifier = Modifier.screenPadding())
                }
            }
        }

        // the wave: first what pulls and how strongly, then the sea rising and falling, and what each part asks
        if (cfg.type == "urge" && !ended) {
            if (!waveStarted) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WaveIntro(waveUrge, waveBefore, { waveUrge = it }, { waveBefore = it }) {
                    waveStarted = true; waveAfter = waveBefore; lastTouch = 0f
                    val id = sessionId; val u = waveUrge!!; val b = waveBefore
                    if (id != null) app.scope.launch(Dispatchers.IO) { app.db.setSessionUrge(id, u, b) }
                    app.scope.launch { Bell.ring() }
                }
            } else {
                WaveView(elapsed, planned)
                val st = waveStage(elapsed, planned)
                AnimatedContent(st, transitionSpec = { fadeIn(tween(1500)) togetherWith fadeOut(tween(1500)) }, label = "wave",
                    modifier = Modifier.align(Alignment.Center)) { s ->
                    val (name, title, line) = WAVE_STAGES[s]
                    val part = planned / 5f
                    StageCard(title, line, Color(0xFF8FD3E8), ((elapsed - s * part) / part).coerceIn(0f, 1f), label = "${name.uppercase()} · ${s + 1} OD 5")
                }
            }
        }

        // RAIN: a card for each step, moving on when the person is ready; the figure appears to touch where it sits
        if (isRain && !ended) {
            if (rainStep == 2) BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val area = with(density) { Rect(maxWidth.toPx() * 0.12f, maxHeight.toPx() * 0.11f, maxWidth.toPx() * 0.88f, maxHeight.toPx() * 0.60f) }
                BodyTouchView(elapsed, area, marks.toList()) { figure[0] = it }
            }
            AnimatedContent(rainStep, transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(900)) }, label = "rain",
                modifier = Modifier.align(if (rainStep == 2) Alignment.BottomCenter else Alignment.Center).navigationBarsPadding().imePadding()
                    .padding(bottom = if (rainStep == 2) 24.dp else 0.dp)) { s ->
                RainCard(s, rainMoods, { m -> if (m in rainMoods) rainMoods.remove(m) else rainMoods.add(m) }, marks.map { it.first.region }.distinct().size,
                    rainWords, { rainWords = it }) { lastTouch = 0f; if (s < 3) rainStep = s + 1 else finish() }
            }
        }

        if (cfg.type == "thoughts") {
            AnimatedVisibility(elapsed < 9f && !ended, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
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
        if (weekQuestion != null) AnimatedVisibility(elapsed in 1.5f..12f && cfg.type !in listOf("thoughts", "bodyscan", "hourglass", "urge", "rain") && !ended, enter = fadeIn(), exit = fadeOut(),
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
                            cfg.type == "urge" && waveUrge != null -> "talas: " + waveUrge!!.removePrefix("da ")
                            cfg.type == "bodyscan" && marks.isNotEmpty() ->
                                marks.map { it.first.region }.distinct().size.let { n -> "napetost: $n " + plural(n, "mjesto", "mjesta", "mjesta") }
                            planned > 0 -> "preostalo od ${planned / 60} min"
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

        if (ambienceOpen && !ended) AmbienceSheet(host, soundOn, lakeAllowed = cfg.type != "walk", onSound = { on ->
            soundOn = on; app.settings.ambient = on
            if (on) host.ambience.start() else host.ambience.stop()
        }, breathOn = if (cfg.paced) breathOn else null, onBreath = { on ->
            breathOn = on; app.settings.breathSound = on
            if (on) host.breath.start() else host.breath.stop()
        }, onClose = { ambienceOpen = false; lastTouch = 0f; host.dark = cfg.eyesClosed && !paused })

        if (ended) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { if (endReady && !asksWander && !(asksWave && waveStarted)) proceed() },
            contentAlignment = Alignment.Center) {
            val shown by animateFloatAsState(if (endReady) 1f else 0f, tween(1800), label = "end")
            Column(Modifier.alpha(shown).fillMaxWidth().softShade().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sesija je završena.", style = TitleStyle.copy(fontWeight = FontWeight.Light, shadow = Shadow(SHADE.copy(alpha = 0.55f), Offset(0f, 1f), 12f)))
                val min = (elapsed / 60f).let { if (it < 1f) null else kotlin.math.ceil(it).toInt() }
                Text(if (min == null) "Neka zvono utihne." else "$min min tišine. Neka zvono utihne.", style = SmallStyle, textAlign = TextAlign.Center)
                endNote?.let { Text(it, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold, shadow = Shadow(SHADE.copy(alpha = 0.7f), Offset(0f, 1f), 10f)), textAlign = TextAlign.Center, modifier = Modifier.screenPadding()) }
                if (asksWander) {
                    // one tap: where did the mind go most often? (any number, or none)
                    Column(Modifier.widthIn(max = 360.dp).padding(horizontal = 20.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xF20E1834))
                        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Kuda je um najčešće odlazio?", style = HeadStyle.copy(fontSize = 19.sp, shadow = null))
                        ChipRow { for (wd in MindHabits.WANDER) Chip(wd, wd in wander, { if (wd in wander) wander.remove(wd) else wander.add(wd) }) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton("Preskoči", { wander.clear(); proceed() }, Modifier.weight(1f))
                            GlassButton("Dalje", { proceed() }, Modifier.weight(1f), strong = true, enabled = endReady)
                        }
                    }
                } else if (asksWave && waveStarted) WaveRating(waveBefore, waveAfter, { waveAfter = it; waveRated = true }, { waveRated = true; proceed() }, endReady)
                else Text("Dodirni za nastavak", style = SmallStyle)
            }
        }
    }
}
