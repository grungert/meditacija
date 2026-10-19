package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.data.Session
import com.grungert.nebo.koan.FourPointsReader
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The wave (urge surfing): a pull to do something rises, crests and falls away on its own, like a wave, if it is watched
 * instead of obeyed. The session names the pull and how strong it is, follows it through five parts, and asks again
 * at the end.
 */
val WAVE_STAGES = listOf(
    Triple("Primijeti", "Poriv je tu", "Ne moraš ništa da uradiš. Samo primijeti da te nešto vuče, i kako se to zove."),
    Triple("Tijelo", "Gdje je u tijelu?", "Potraži ga: stomak, grudi, ruke, grlo. Je li toplo, stegnuto, nemirno?"),
    Triple("Dah", "Diši s njim", "Udahni prema tom mjestu. Na izdahu mu daj malo više prostora, bez guranja."),
    Triple("Vrh", "Vrh talasa", "Možda baš sada raste. Svaki talas ima vrh, i svaki se spusti. Ostani na njemu."),
    Triple("Oseka", "Talas se spušta", "Primijeti kako se mijenja. Ne nestaje odjednom, nego polako, kao voda koja se povlači."),
)

/** Which of the five parts, in equal shares of the planned time. */
fun waveStage(elapsed: Float, planned: Int): Int = (elapsed / (planned.coerceAtLeast(60) / 5f)).toInt().coerceIn(0, 4)

/** The pulls one can name, in the words of the four points, plus one for anything else. */
val WAVE_URGES = FourPointsReader.URGE_LABELS.map { it.first } + "nešto drugo"

/**
 * How high the wave stands at a point of the session, 0..1: it builds through the first half, crests a little after the
 * middle and ebbs to a low swell by the end.
 */
fun waveHeight(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 0.18f + 0.82f * exp(-((x - 0.58f) * (x - 0.58f)) / 0.055f)
}

/** The sea of the wave session: three layers of swell over the lake, rising and falling with [waveHeight]. */
@Composable
fun WaveView(elapsed: Float, planned: Int, modifier: Modifier = Modifier) {
    val h = waveHeight(elapsed / planned.coerceAtLeast(1))
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val base = size.height * 0.80f
        val amp = size.height * (0.015f + 0.10f * h)
        val layers = listOf(
            Triple(0.55f, 1.0f, Color(0xFF8FD3E8)),
            Triple(0.75f, 1.6f, Color(0xFF5AA9C9)),
            Triple(1.00f, 2.3f, Color(0xFF2E6F96)),
        )
        for ((i, layer) in layers.withIndex()) {
            val (scale, speed, tone) = layer
            val y0 = base + i * size.height * 0.035f
            val path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= w + 8f) {
                    val k = x / w
                    // a long swell with a crest that leans, and a smaller chop on top of it
                    val swell = sin((k * 1.6f + elapsed * 0.05f * speed + i) * 2f * PI.toFloat())
                    val chop = sin((k * 5.3f - elapsed * 0.11f * speed + i * 1.7f) * 2f * PI.toFloat()) * 0.22f
                    lineTo(x, y0 - amp * scale * (0.5f + 0.5f * swell + chop))
                    x += 8f
                }
                lineTo(w, size.height); close()
            }
            drawPath(path, Brush.verticalGradient(listOf(tone.copy(alpha = 0.38f), tone.copy(alpha = 0.12f)), y0 - amp, size.height))
        }
    }
}

/** A strength from 1 to 10 on a slider, with the number large above it. */
@Composable
private fun Strength(value: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("$value", style = NumberStyle.copy(fontSize = 40.sp, fontWeight = FontWeight.Light))
        Slider(value.toFloat(), { onChange(it.roundToInt()) }, valueRange = 1f..10f, steps = 8,
            colors = SliderDefaults.colors(thumbColor = Ink.paper, activeTrackColor = Ink.accent, inactiveTrackColor = Ink.surfaceHigh))
        Row(Modifier.fillMaxWidth()) {
            Text("jedva", style = SmallStyle.copy(shadow = null), modifier = Modifier.weight(1f))
            Text("jako", style = SmallStyle.copy(shadow = null))
        }
    }
}

/** Before the wave: what pulls, and how strongly. */
@Composable
fun WaveIntro(urge: String?, strength: Int, onUrge: (String) -> Unit, onStrength: (Int) -> Unit, onStart: () -> Unit) {
    Column(
        Modifier.widthIn(max = 380.dp).padding(horizontal = 18.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xF20E1834)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Šta te vuče?", style = HeadStyle.copy(fontSize = 22.sp, shadow = null))
        ChipRow { for (u in WAVE_URGES) Chip(u.removePrefix("da "), u == urge, { onUrge(u) }) }
        Text("Koliko jako, upravo sada?", style = BodyStyle.copy(shadow = null))
        Strength(strength, onStrength)
        GlassButton("Kreni", onStart, Modifier.fillMaxWidth(), strong = true, enabled = urge != null)
    }
}

/** After the wave: how strong it is now, next to how it was. */
@Composable
fun WaveRating(before: Int, after: Int, onAfter: (Int) -> Unit, onDone: () -> Unit, enabled: Boolean) {
    Column(
        Modifier.widthIn(max = 380.dp).padding(horizontal = 18.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xF20E1834)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Koliko je jak sada?", style = HeadStyle.copy(fontSize = 21.sp, shadow = null))
        Text("Na početku je bio $before.", style = SmallStyle.copy(shadow = null))
        Strength(after, onAfter)
        GlassButton("Dalje", onDone, Modifier.fillMaxWidth(), strong = true, enabled = enabled)
    }
}

/** For the overview: per pull, how many waves and how strong on average before and after. */
class WaveSummary(val urge: String, val count: Int, val before: Float, val after: Float)

fun waveSummaries(sessions: List<Session>): List<WaveSummary> =
    sessions.filter { it.type == "urge" && it.urge != null && it.urgeBefore != null && it.urgeAfter != null }
        .groupBy { it.urge!! }
        .map { (u, ss) -> WaveSummary(u, ss.size, ss.map { it.urgeBefore!! }.average().toFloat(), ss.map { it.urgeAfter!! }.average().toFloat()) }
        .sortedByDescending { it.count }
