package com.grungert.nebo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.SkyHost
import com.grungert.nebo.app
import com.grungert.nebo.audio.Bell
import com.grungert.nebo.sky.patternById
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val FIFTY_SECONDS = 60f
/** Where the points of light of the outside half sit: angle and distance, both 0..1. */
private val SPARKS = kotlin.random.Random(9).let { r -> List(18) { floatArrayOf(r.nextFloat(), kotlin.math.sqrt(r.nextFloat())) } }

/** What to notice, inside and outside; a new pair every twelve seconds. */
private val INSIDE = listOf("Stopala na podu", "Dah u stomaku", "Težina ruku", "Vazduh na licu", "Ramena, da li su meka?")
private val OUTSIDE = listOf("Zvukovi oko tebe", "Svjetlo i boje", "Jedan predmet ispred tebe", "Najdalji zvuk", "Temperatura vazduha")

/**
 * 50-50: one minute with half the attention in the body and half on the world outside.
 * The left half of the circle breathes with a slow rhythm, the right half sparkles with what comes from outside;
 * a thin line around it fills over the minute.
 */
@Composable
fun FiftyScreen(host: SkyHost, go: (Screen) -> Unit) {
    val app = LocalContext.current.app
    var elapsed by remember { mutableFloatStateOf(0f) }
    var round by remember { mutableIntStateOf(0) }    // starts again on "Još minut"
    val done = elapsed >= FIFTY_SECONDS

    DisposableEffect(round) {
        val e = host.engine
        host.showCircle = false
        e.paced = true; e.running = true
        e.setPatternAndRestart(patternById("46"))
        host.onFrame = { dt -> if (elapsed < FIFTY_SECONDS) elapsed = (elapsed + dt).coerceAtMost(FIFTY_SECONDS) }
        onDispose { host.onFrame = null; e.paced = false }
    }
    LaunchedEffect(done) { if (done) app.scope.launch { Bell.ring(soft = true) } }

    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().screenPadding().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("50-50", { go(Screen.Home) })
            Text("Pola pažnje u tijelu, pola napolju.", style = BodyStyle.copy(color = Ink.soft), modifier = Modifier.fillMaxWidth())
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val e = host.engine
                Canvas(Modifier.size(280.dp)) {
                    if (elapsed < 0f) return@Canvas   // reading elapsed redraws every frame
                    val c = center
                    val r = size.minDimension * 0.34f
                    val b = e.breath
                    val warm = Color(0xFFFFD9A0); val cool = Color(0xFFB9D6EE)
                    // the minute, once around
                    val ring = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    val rr = r * 1.38f
                    drawCircle(Color.White.copy(alpha = 0.10f), rr, c, style = ring)
                    drawArc(Brush.sweepGradient(listOf(warm, cool, warm), c), -90f, 360f * elapsed / FIFTY_SECONDS, false,
                        Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = ring)
                    // inside: a warm glow on the left that swells and settles with the breath, around a quiet centre
                    clipRect(right = c.x) {
                        val gi = r * (0.85f + 0.3f * b)
                        drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = 0.95f), warm.copy(alpha = 0.5f), warm.copy(alpha = 0f)), c, gi * 1.2f), gi * 1.2f, c)
                        drawCircle(warm.copy(alpha = 0.9f), r * (0.07f + 0.02f * b), Offset(c.x - r * 0.42f, c.y))
                    }
                    // outside: sound spreading in rings on the right, and points of light drifting in from far away
                    clipRect(left = c.x) {
                        drawCircle(Brush.radialGradient(listOf(cool.copy(alpha = 0.28f), cool.copy(alpha = 0f)), c, r * 1.1f), r * 1.1f, c)
                        for (i in 0 until 4) {
                            val k = ((elapsed / 3.2f + i / 4f) % 1f)
                            drawCircle(cool.copy(alpha = 0.55f * (1f - k)), r * (0.25f + 0.95f * k), c, style = Stroke((2.2f - 1.4f * k).dp.toPx()))
                        }
                        for ((i, p) in SPARKS.withIndex()) {
                            val a = (-75f + 150f * p[0]) * (Math.PI / 180).toFloat()
                            val travel = ((elapsed * (0.05f + 0.04f * p[1]) + p[1]) % 1f)
                            val d = r * (1.15f - 0.95f * travel)
                            val tw = 0.5f + 0.5f * sin(elapsed * (1.3f + i % 4 * 0.4f) + i)
                            drawCircle(Color.White.copy(alpha = (0.2f + 0.6f * tw) * (1f - travel * 0.6f)), (1.4f + 2f * tw) * density,
                                Offset(c.x + cos(a) * d, c.y + sin(a) * d))
                        }
                    }
                    // the seam between the two halves, soft at the ends
                    drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent), c.y - r * 1.2f, c.y + r * 1.2f),
                        Offset(c.x, c.y - r * 1.2f), Offset(c.x, c.y + r * 1.2f), 1.dp.toPx())
                }
            }
            val pair = (elapsed / 12f).toInt().coerceAtMost(INSIDE.lastIndex)
            // the two things to notice now, each on its own card in its own light
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((label, words, color) in listOf(Triple("UNUTRA", INSIDE, Color(0xFFFFD9A0)), Triple("NAPOLJU", OUTSIDE, Color(0xFFB9D6EE)))) {
                    Column(
                        Modifier.weight(1f).height(108.dp).clip(RoundedCornerShape(22.dp)).background(Ink.surface)
                            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(22.dp)).padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                            Text(label, style = LabelStyle.copy(color = color))
                        }
                        AnimatedContent(words[pair], transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(900)) }, label = label) { w ->
                            Text(w, style = HeadStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 23.sp, shadow = null), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Box(Modifier.padding(top = 28.dp)) {
                if (done) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ponesi ovu podijeljenu pažnju sa sobom.", style = BodyStyle.copy(color = Ink.soft), textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton("Još minut", { elapsed = 0f; round++ })
                        GlassButton("Gotovo", { go(Screen.Home) }, strong = true)
                    }
                } else Text("${(FIFTY_SECONDS - elapsed).toInt()} s", style = SmallStyle)
            }
        }
    }
}
