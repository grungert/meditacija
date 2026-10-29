package com.grungert.nebo.ui

import androidx.compose.runtime.mutableStateOf

import kotlinx.coroutines.withContext

import kotlinx.coroutines.Dispatchers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.SkyHost
import com.grungert.nebo.app
import com.grungert.nebo.audio.Bell
import com.grungert.nebo.sky.Phase
import com.grungert.nebo.sky.patternById
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val FIFTY_SECONDS = 60f

/** What to notice, inside and outside; a new pair every twelve seconds. */
private val INSIDE = listOf("Stopala na podu", "Dah u stomaku", "Težina ruku", "Vazduh na licu", "Ramena, da li su meka?")
private val OUTSIDE = listOf("Zvukovi oko tebe", "Svjetlo i boje", "Jedan predmet ispred tebe", "Najdalji zvuk", "Temperatura vazduha")

private val WARM = Color(0xFFFFD9A0)
private val COOL = Color(0xFFCFE3F6)
private val INK = Color(0xFF14223F)

/**
 * 50-50 by a still lake. The world stands on the far shore and lies upside down in the water (painted by the sky
 * shader); the outside is everything above and around it. Below the surface a warm light breathes, and each in-breath
 * sends one calm ring across the water: the inside. Reeds sway at the edges, lily pads rest on the water, a dragonfly
 * hovers. Over the minute a thin light spreads along the shore; in the last seconds the warm light rises to meet it.
 */
@Composable
fun FiftyScreen(host: SkyHost, go: (Screen) -> Unit) {
    val app = LocalContext.current.app
    var elapsed by remember { mutableFloatStateOf(0f) }
    var clock by remember { mutableFloatStateOf(0f) }          // keeps running after the minute, for the water and the reeds
    var round by remember { mutableIntStateOf(0) }            // starts again on "Još minut"
    val ripples = remember { ArrayList<Float>() }              // when each ring left the centre
    val done = elapsed >= FIFTY_SECONDS
    var banked by remember { mutableFloatStateOf(0f) }        // seconds of the rounds before this one
    var sessionId by remember { mutableStateOf<Long?>(null) }

    // the minute is kept like any sit: in the journal, the week's minutes and its days, and what helps. "Još minut"
    // goes on in the same one; closed in its first seconds (opened by mistake) it leaves nothing
    LaunchedEffect(Unit) {
        val call = app.fiftyCall.also { app.fiftyCall = null }
        sessionId = withContext(Dispatchers.IO) {
            app.db.insertSession(com.grungert.nebo.data.Session(startedAt = System.currentTimeMillis(), type = "fifty", plannedSec = FIFTY_SECONDS.toInt(),
                pattern = "46", walk = false)).also { id -> call?.let { app.db.fiftyCallAnswered(it, id) } }
        }
    }
    DisposableEffect(Unit) {
        app.sitting = true
        onDispose {
            app.sitting = false
            val id = sessionId ?: return@onDispose
            val sat = (banked + elapsed).toInt()
            app.scope.launch(Dispatchers.IO) { if (sat < 10) app.db.deleteSession(id) else app.db.finishSession(id, System.currentTimeMillis(), sat, 0) }
        }
    }

    DisposableEffect(round) {
        val e = host.engine
        host.showCircle = false
        host.lake = true
        host.clearing = false
        e.paced = true; e.running = true
        e.setPatternAndRestart(patternById("46"))
        ripples.clear(); ripples += clock
        host.onPhase = { p -> if (p == Phase.IN && elapsed < FIFTY_SECONDS) ripples += clock }
        host.onFrame = { dt ->
            clock += dt
            if (elapsed < FIFTY_SECONDS) elapsed = (elapsed + dt).coerceAtMost(FIFTY_SECONDS)
            ripples.removeAll { clock - it > 9f }
        }
        onDispose { host.onFrame = null; host.onPhase = null; e.paced = false; host.lake = false }
    }
    LaunchedEffect(done) { if (done) { host.clearing = true; app.scope.launch { Bell.ring(soft = true) } } }

    Box(Modifier.fillMaxSize()) {
        val e = host.engine
        Canvas(Modifier.fillMaxSize()) {
            if (clock < 0f) return@Canvas   // reading clock redraws every frame
            treeline(treeColor(host), clock)
            lakeLife(clock, elapsed, e.breath, ripples, rainy = host.weather == com.grungert.nebo.sky.Weather.RAIN)
        }
        // under a rainy sky, rain on the glass too
        if (host.weather == com.grungert.nebo.sky.Weather.RAIN) RainOnGlass(night = !Ink.day, still = e.reducedMotion, raining = !host.clearing)
        // in the mist, the glass mists over too, and a finger wipes it
        if (host.weather == com.grungert.nebo.sky.Weather.FOG) FoggedGlass(
            night = !Ink.day,
            breathOut = { val b = host.engine; if (b.paced && b.phase == com.grungert.nebo.sky.Phase.OUT) kotlin.math.sin(Math.PI * b.progress).toFloat() else 0f },
            onTap = { host.onTap?.invoke(it) },
            hint = !app.settings.fogWiped, onWiped = { app.settings.fogWiped = true }, lifting = host.clearing,
        )
        if (host.weather == com.grungert.nebo.sky.Weather.SNOW) FrostedGlass(night = !Ink.day, onTap = { host.onTap?.invoke(it) }, melting = host.clearing,
            hint = !app.settings.frostMelted, onMelted = { app.settings.frostMelted = true })

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val h = maxHeight
            val pair = (elapsed / 12f).toInt().coerceAtMost(INSIDE.lastIndex)
            val merge = ((elapsed - 50f) / 10f).coerceIn(0f, 1f)
            // outside: above the far shore, in the sky
            PromptPair("NAPOLJU", OUTSIDE[pair], COOL, Modifier.offset(y = h * 0.30f).alpha(1f - merge))
            // inside: in the warm light under the water
            PromptPair("UNUTRA", INSIDE[pair], WARM, Modifier.offset(y = h * 0.69f).alpha(1f - merge))
            if (merge > 0f) Text("Jedno, cijelo.", style = TitleStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Light,
                shadow = Shadow(INK.copy(alpha = 0.6f), Offset(0f, 1f), 14f)), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().offset(y = h * SHORE - 64.dp).alpha(merge))
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().screenPadding().padding(vertical = 16.dp)) {
            ScreenHeader("50-50", { go(Screen.Home) })
            Text("Pola pažnje u tijelu, pola u svijetu oko tebe.", style = BodyStyle.copy(color = Ink.soft), modifier = Modifier.fillMaxWidth())
            Box(Modifier.weight(1f))
            if (done) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ponesi ovu podijeljenu pažnju sa sobom.", style = BodyStyle, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("Još minut", {
                        banked += elapsed; elapsed = 0f; round++
                        sessionId?.let { id -> val planned = ((round + 1) * FIFTY_SECONDS).toInt(); app.scope.launch(Dispatchers.IO) { app.db.setSessionPlanned(id, planned) } }
                    })
                    GlassButton("Gotovo", { go(Screen.Home) }, strong = true)
                }
            }
        }
    }
}

/** A small label and the words to notice, centred, readable over sky or water. */
@Composable
private fun PromptPair(label: String, words: String, color: Color, modifier: Modifier) {
    // a wide soft shade behind the words, with no edge, so they read over a white cloud as well as over water
    Column(modifier.fillMaxWidth().drawBehind {
        val r = size.width * 0.42f
        scale(1f, 0.38f, center) {
            drawCircle(Brush.radialGradient(listOf(INK.copy(alpha = 0.38f), INK.copy(alpha = 0.14f), Color.Transparent), center, r), r, center)
        }
    }.padding(horizontal = 32.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = LabelStyle.copy(color = color, shadow = Shadow(INK.copy(alpha = 0.6f), Offset(0f, 1f), 10f)))
        AnimatedContent(words, transitionSpec = { fadeIn(tween(1400)) togetherWith fadeOut(tween(1400)) }, label = label) { w ->
            Text(w, style = HeadStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium,
                shadow = Shadow(INK.copy(alpha = 0.65f), Offset(0f, 1f), 16f)), textAlign = TextAlign.Center)
        }
    }
}

/** Everything that lives on and around the water, drawn over the painted lake. */
private fun DrawScope.lakeLife(t: Float, elapsed: Float, breath: Float, ripples: List<Float>, rainy: Boolean) {
    val w = size.width; val h = size.height
    val shore = h * SHORE
    val heart = Offset(w / 2, h * 0.80f)
    val merge = ((elapsed - 50f) / 10f).coerceIn(0f, 1f)
    val px = density

    // the warm light under the surface, breathing; at the end it rises towards the shore and spreads along it
    val glowY = heart.y + (shore + 10 * px - heart.y) * merge
    val gr = (90 + 70 * breath) * px * (1f + 1.6f * merge)
    val glowAt = Offset(w / 2, glowY)
    scale(1f + 1.4f * merge, 0.42f + 0.1f * breath - 0.2f * merge, glowAt) {
        drawCircle(Brush.radialGradient(listOf(WARM.copy(alpha = 0.55f + 0.30f * breath), WARM.copy(alpha = 0.18f), Color.Transparent),
            glowAt, gr), gr, glowAt)
    }

    // one calm ring for each in-breath, spreading across the water in perspective
    for (r0 in ripples) {
        val k = ((t - r0) / 9f).coerceIn(0f, 1f)
        if (k >= 1f) continue
        val rx = (18 + w / px * 0.55f * k) * px
        val ry = rx * 0.26f
        val a = 0.55f * (1f - k) * (1f - k)
        drawOval(Color.White.copy(alpha = a), Offset(heart.x - rx, heart.y - ry), Size(rx * 2, ry * 2), style = Stroke((1.8f - 1.1f * k) * px))
        drawOval(WARM.copy(alpha = a * 0.5f), Offset(heart.x - rx * 0.96f, heart.y - ry * 0.9f), Size(rx * 1.92f, ry * 1.8f), style = Stroke(0.8f * px))
    }

    // the minute: a thin light spreading along the shore from the middle
    val half = w / 2 * (elapsed / FIFTY_SECONDS).coerceIn(0f, 1f)
    if (half > 1f) drawLine(Brush.horizontalGradient(listOf(COOL.copy(alpha = 0f), WARM.copy(alpha = 0.9f), COOL.copy(alpha = 0f)), w / 2 - half, w / 2 + half),
        Offset(w / 2 - half, shore), Offset(w / 2 + half, shore), 2.2f * px, StrokeCap.Round)

    lakeDecor(t, rainy = rainy)
}
