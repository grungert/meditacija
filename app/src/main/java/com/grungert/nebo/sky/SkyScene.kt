package com.grungert.nebo.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** How the time of day moves: following the clock, cycling through a day in three minutes, or held at one hour. */
class DayClock(mode: String) {
    var mode: String = mode
        set(v) { field = v; if (v != "clock" && v != "cycle") hold = v.toFloatOrNull() }
    private var hold: Float? = mode.toFloatOrNull()
    var hour: Float = hold ?: DayCycle.clockHour(); private set

    fun step(dt: Float) {
        if (mode == "cycle") { hour = (hour + dt * 24f / 180f) % 24f; return }
        val target = if (mode == "clock") DayCycle.clockHour() else hold ?: DayCycle.clockHour()
        var diff = (((target - hour) % 24f) + 24f) % 24f  // always travel forward through the day
        if (diff > 23.95f) diff = 0f
        hour = if (diff < 0.002f) target else (hour + minOf(diff, maxOf(0.6f, diff * 0.8f) * dt)) % 24f
    }
}

/**
 * The living sky: the GL shader underneath, the painted meadow, walk and breathing circle on top,
 * all driven from one frame loop.
 */
@Composable
fun SkyScene(
    engine: BreathEngine,
    day: DayClock,
    showCircle: Boolean,
    modifier: Modifier = Modifier,
    onTap: ((Offset) -> Unit)? = null,
    onPhase: (Phase) -> Unit = {},
    onFrame: (dt: Float) -> Unit = {},
    onLight: (pal: Palette, sunAmount: Float) -> Unit = { _, _ -> },
    dark: Boolean = false,
    season: Season = Season.SUMMER,
    weather: Weather = Weather.CLEAR,
    scenery: Scenery = rememberScenery(),
) {
    val context = LocalContext.current
    val skyView = remember { SkyView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val phaseCb by rememberUpdatedState(onPhase)
    val frameCb by rememberUpdatedState(onFrame)
    val lightCb by rememberUpdatedState(onLight)
    val isDark by rememberUpdatedState(dark)
    val seasonNow by rememberUpdatedState(season)
    val weatherNow by rememberUpdatedState(weather)
    var tick by remember { mutableLongStateOf(0L) }
    val state = remember { FrameState() }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> skyView.onPause()
                Lifecycle.Event.ON_RESUME -> skyView.onResume()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    DisposableEffect(engine) {
        engine.onPhaseChange = { p ->
            scenery.ripple(state.time, p == Phase.IN || p == Phase.OUT)
            phaseCb(p)
        }
        onDispose { engine.onPhaseChange = null }
    }

    LaunchedEffect(engine, day) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                state.time += dt
                val dWalk = engine.step(dt)
                scenery.stepWalk(dWalk * 0.55f, engine.walkOn)
                day.step(dt)
                state.walkAmt += ((if (engine.walkOn) 1f else 0f) - state.walkAmt) * minOf(1f, dt * 1.5f)
                // eyes closed: the whole scene fades to black inside GL and the overlay, never under an opaque layer
                state.dim += ((if (isDark) 1f else 0f) - state.dim) * minOf(1f, dt * 1.8f)
                val h = day.hour
                state.pal = Seasons.apply(DayCycle.paletteAt(h), seasonNow, weatherNow)
                state.season = seasonNow; state.weather = weatherNow
                val through = Seasons.sunThrough(weatherNow)
                state.light = DayCycle.lightDir(h)
                state.dt = dt
                skyView.renderer.frame = SkyFrame(
                    state.time, engine.drift, engine.morph, engine.windPhase, engine.walk, engine.breath,
                    if (engine.reducedMotion) 0.3f else 1f, state.pal, DayCycle.sunPos(h), DayCycle.moonPos(h),
                    DayCycle.sunAmount(h) * through, DayCycle.moonAmount(h) * through, state.light, state.dim, Seasons.cover(weatherNow),
                )
                lightCb(state.pal, DayCycle.sunAmount(h) * through)
                frameCb(dt)
                tick = now
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView({ skyView }, Modifier.fillMaxSize())
        val tapMod = if (onTap != null) Modifier.pointerInput(Unit) { detectTapGestures { onTap(it) } } else Modifier
        Canvas(Modifier.fillMaxSize().then(tapMod)) {
            if (tick == -1L || state.dim > 0.98f) return@Canvas // reading tick redraws the overlay every frame
            scenery.resize(size.width, size.height)
            drawIntoCanvas { c ->
                scenery.draw(c.nativeCanvas, engine, state.pal, state.light, state.time, state.dt,
                    SceneFlags(showCircle = showCircle, showLandmarks = true, season = state.season, weather = state.weather), state.walkAmt)
                if (state.dim > 0.01f) c.nativeCanvas.drawColor(android.graphics.Color.argb((state.dim * 255).toInt(), 0, 0, 0))
            }
        }
    }
}

private class FrameState {
    var time = 0f
    var dt = 0f
    var walkAmt = 0f
    var dim = 0f
    var season = Season.SUMMER
    var weather = Weather.CLEAR
    var pal: Palette = DayCycle.paletteAt(12f)
    var light = floatArrayOf(0f, 1f, 0.3f)
}

@Composable
fun rememberScenery(): Scenery {
    val density = LocalDensity.current.density
    return remember { Scenery(density) }
}
