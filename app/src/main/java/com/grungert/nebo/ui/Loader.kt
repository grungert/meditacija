package com.grungert.nebo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The opening of the app, picking up where the system's opening screen leaves off: the sand orb on the night sky
 * begins to breathe, rings of breath go out from it, a few stars come out and "Nebo" appears with "udahni" and
 * "izdahni" in time with the orb. Once the sky behind is drawn ([ready], and after one breath at least) the orb
 * opens into a widening circle that shows the app through it, and the loader is gone ([onDone]).
 */
@Composable
fun Loader(ready: Boolean, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    var t by remember { mutableFloatStateOf(0f) }
    val open = remember { Animatable(0f) }
    val readyNow by rememberUpdatedState(ready)
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        // the clock of the breath, until the sky is ready (one breath at least, and never more than five seconds)
        while (true) {
            val now = withFrameNanos { it }
            t = (now - start) / 1e9f
            if ((readyNow && t > 1.6f) || t > 5f) break
        }
        open.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        delay(30)
        done()
    }
    val tm = rememberTextMeasurer()
    val stars = remember { val r = Random(11); List(36) { Triple(r.nextFloat(), r.nextFloat() * 0.8f, r.nextFloat()) } }
    val sand = Color(0xFFFFD9A0)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val c = Offset(size.width / 2, size.height / 2)
            val o = open.value
            // the night sky
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1530), Color(0xFF14244A), Color(0xFF1C3160))))
            // stars, each coming out in turn and twinkling
            for ((i, s) in stars.withIndex()) {
                val shown = ((t - 0.15f - i * 0.025f) / 0.5f).coerceIn(0f, 1f)
                val tw = 0.55f + 0.45f * sin(t * (1.3f + s.third) + i)
                drawCircle(Color.White.copy(alpha = 0.75f * shown * tw), (0.8f + 1.4f * s.third) * density, Offset(s.first * size.width, s.second * size.height))
            }
            // the breath: in over two seconds, out over two; the orb follows it from the system screen's size
            val breath = 0.5f - 0.5f * kotlin.math.cos((t / 4f) * 2 * PI.toFloat())
            val grow = (t / 0.5f).coerceIn(0f, 1f)
            val r = (44.dp.toPx() + 10.dp.toPx() * breath * grow)
            // rings of breath going out, one every two seconds
            for (k in 0 until 3) {
                val p = ((t - k * 0.7f) / 2.1f).let { it - kotlin.math.floor(it) }
                if (t < k * 0.7f) continue
                drawCircle(sand.copy(alpha = 0.35f * (1f - p) * grow), r + 90.dp.toPx() * p, c, style = Stroke((2.5f - 1.5f * p).dp.toPx()))
            }
            drawCircle(Brush.radialGradient(listOf(sand.copy(alpha = 0.45f), sand.copy(alpha = 0f)), c, r * 2.4f), r * 2.4f, c)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFF1DA), sand), Offset(c.x - r * 0.25f, c.y - r * 0.3f), r * 1.1f), r, c)
            // the name, and the breath in words
            val nameAlpha = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f) * (1f - o)
            val name = tm.measure("Nebo", TextStyle(fontFamily = Serif, fontSize = 40.sp, color = Color.White))
            drawText(name, topLeft = Offset(c.x - name.size.width / 2f, c.y + 96.dp.toPx()), alpha = nameAlpha)
            val word = if (((t / 2f).toInt()) % 2 == 0) "udahni" else "izdahni"
            val wordAlpha = ((t - 0.9f) / 0.5f).coerceIn(0f, 1f) * (0.45f + 0.35f * sin((t % 2f) / 2f * PI.toFloat())) * (1f - o)
            val w = tm.measure(word, TextStyle(fontFamily = Serif, fontSize = 17.sp, fontStyle = FontStyle.Italic, color = sand))
            drawText(w, topLeft = Offset(c.x - w.size.width / 2f, c.y + 96.dp.toPx() + name.size.height + 4.dp.toPx()), alpha = wordAlpha)
            // the opening: a circle from the orb that clears the night and shows the app through it
            if (o > 0f) drawCircle(Color.Black, hypot(size.width, size.height) * o * o + r * (1f - o), c, blendMode = BlendMode.Clear)
        }
    }
}
