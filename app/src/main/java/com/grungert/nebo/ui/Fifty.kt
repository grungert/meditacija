package com.grungert.nebo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
                Canvas(Modifier.size(260.dp)) {
                    if (elapsed < 0f) return@Canvas   // reading elapsed redraws every frame
                    val c = center
                    val r = size.minDimension * 0.36f
                    val b = e.breath
                    // the minute, once around
                    val ring = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(Color.White.copy(alpha = 0.12f), r * 1.32f, c, style = ring)
                    drawArc(Ink.accent, -90f, 360f * elapsed / FIFTY_SECONDS, false, Offset(c.x - r * 1.32f, c.y - r * 1.32f), Size(r * 2.64f, r * 2.64f), style = ring)
                    // inside: a warm half that swells with the breath
                    val ri = r * (0.78f + 0.22f * b)
                    drawArc(Color(0xFFFFD9A0).copy(alpha = 0.30f + 0.35f * b), 90f, 180f, true, Offset(c.x - ri, c.y - ri), Size(ri * 2, ri * 2))
                    // outside: a cool half with points of light coming and going
                    drawArc(Color(0xFFB9D6EE).copy(alpha = 0.22f), -90f, 180f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
                    for ((i, p) in SPARKS.withIndex()) {
                        val a = (-80f + 160f * p[0]) * (Math.PI / 180).toFloat()
                        val d = r * (0.2f + 0.75f * p[1])
                        val tw = 0.5f + 0.5f * sin(elapsed * (1.3f + i % 4 * 0.4f) + i)
                        drawCircle(Color.White.copy(alpha = 0.25f + 0.65f * tw), (1.5f + 2.5f * tw) * density, Offset(c.x + cos(a) * d, c.y + sin(a) * d))
                    }
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x, c.y - r * 1.05f), Offset(c.x, c.y + r * 1.05f), 1.dp.toPx())
                }
            }
            val pair = (elapsed / 12f).toInt().coerceAtMost(INSIDE.lastIndex)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((label, words, color) in listOf(Triple("UNUTRA", INSIDE, Color(0xFFFFD9A0)), Triple("NAPOLJU", OUTSIDE, Color(0xFFB9D6EE)))) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, style = LabelStyle.copy(color = color))
                        AnimatedContent(words[pair], transitionSpec = { fadeIn() togetherWith fadeOut() }, label = label) { w ->
                            Text(w, style = HeadStyle.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium), textAlign = TextAlign.Center)
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
