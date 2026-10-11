package com.grungert.nebo.ui

import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.SkyHost
import com.grungert.nebo.app
import com.grungert.nebo.audio.Bell
import com.grungert.nebo.data.Session
import com.grungert.nebo.sky.Phase
import com.grungert.nebo.sky.Scenery
import com.grungert.nebo.sky.patternById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

@Composable
fun SessionScreen(cfg: SessionConfig, host: SkyHost, scenery: Scenery, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val vibrator = remember { context.getSystemService(VibratorManager::class.java).defaultVibrator }

    var elapsed by remember { mutableFloatStateOf(0f) }
    var sessionId by remember { mutableStateOf<Long?>(null) }
    var ended by remember { mutableStateOf(false) }
    var showUi by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableFloatStateOf(0f) }
    var thoughts by remember { mutableIntStateOf(0) }
    var word by remember { mutableStateOf("") }
    val planned = cfg.minutes * 60

    fun finish() {
        if (ended) return
        ended = true
        host.showCircle = false
        word = ""
        host.engine.paced = false
        scope.launch {
            launch { Bell.ring() }
            val id = sessionId
            if (id != null) withContext(Dispatchers.IO) {
                app.db.finishSession(id, System.currentTimeMillis(), elapsed.toInt(), scenery.thoughtCount)
            }
            delay(2500)
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
        scenery.resetThoughts()
        if (cfg.type == "walk") scenery.resetWalkSpawns()
        host.onPhase = { p ->
            if (cfg.paced) {
                word = p.label
                if (app.settings.haptics) runCatching {
                    vibrator.vibrate(VibrationEffect.createOneShot(if (p == Phase.IN) 22 else 12, if (p == Phase.IN) 90 else 50))
                }
            }
        }
        var nextBell = 300f
        host.onFrame = { dt ->
            if (!ended) {
                elapsed += dt
                lastTouch += dt
                if (lastTouch > 5f) showUi = false
                if (cfg.intervalBell && elapsed >= nextBell && (planned == 0 || elapsed < planned - 30)) {
                    nextBell += 300f
                    scope.launch { Bell.ring(soft = true) }
                }
                if (planned > 0 && elapsed >= planned) finish()
            }
        }
        host.onTap = { pos ->
            lastTouch = 0f; showUi = true
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
        onDispose {
            view.keepScreenOn = false
            host.onPhase = null; host.onFrame = null; host.onTap = null
            host.showCircle = false
            e.paced = false; e.walkOn = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        // the phase word sits inside the breathing circle (the circle is centred 36% from the top)
        if (cfg.paced) BoxWithConstraints(Modifier.fillMaxSize()) {
            val e = host.engine
            val remaining = ceil(e.phaseDuration - e.phaseT).toInt()
            val fade = minOf(1f, e.phaseT / 0.45f, (e.phaseDuration - e.phaseT) / 0.45f).coerceAtLeast(0.25f)
            Column(
                Modifier.fillMaxWidth().offset(y = maxHeight * 0.36f - 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                @Suppress("UNUSED_VARIABLE") val tick = elapsed
                Text(word, style = TitleStyle.copy(fontSize = 34.sp), modifier = Modifier.alpha(fade))
                Text("$remaining", style = SmallStyle)
            }
        }

        if (cfg.type == "thoughts") {
            AnimatedVisibility(elapsed < 9f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                Text("Kad primijetiš da je misao odlutala,\ndodirni nebo i pusti je.", style = BodyStyle.copy(fontSize = 18.sp),
                    textAlign = TextAlign.Center, modifier = Modifier.screenPadding())
            }
        }

        AnimatedVisibility(showUi || ended, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().screenPadding().padding(vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val shown = if (planned > 0) (planned - elapsed).coerceAtLeast(0f) else elapsed
                    Text("%d:%02d".format(shown.toInt() / 60, shown.toInt() % 60), style = BodyStyle.copy(fontSize = 18.sp))
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
