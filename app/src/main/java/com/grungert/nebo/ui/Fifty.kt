package com.grungert.nebo.ui

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
/** Where the water meets the far shore, from the top of the screen; matches LAKE_Y in the sky shader (1 - 0.42). */
private const val SHORE = 0.58f

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

    DisposableEffect(round) {
        val e = host.engine
        host.showCircle = false
        host.lake = true
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
    LaunchedEffect(done) { if (done) app.scope.launch { Bell.ring(soft = true) } }

    Box(Modifier.fillMaxSize()) {
        val e = host.engine
        Canvas(Modifier.fillMaxSize()) {
            if (clock < 0f) return@Canvas   // reading clock redraws every frame
            // the far bank's trees take the colour of the meadow at this hour, darker in the distance
            val pal = com.grungert.nebo.sky.DayCycle.paletteAt(host.day.hour)
            val tree = Color(pal.gBot[0] * 0.55f, pal.gBot[1] * 0.62f, pal.gBot[2] * 0.6f)
            treeline(tree, clock)
            lakeLife(clock, elapsed, e.breath, ripples)
        }

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
                    GlassButton("Još minut", { elapsed = 0f; round++ })
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

/** Rounded clumps of trees along the far shore, and their softer, upside-down twins in the water. */
private fun DrawScope.treeline(color: Color, t: Float) {
    val w = size.width; val shore = size.height * SHORE; val px = density
    val clumps = List(16) { i ->
        val x = w * (i / 15f) + ((i * 37) % 11 - 5) * 3 * px
        val hgt = (10 + ((i * 53) % 9) * 3.5f + (if (i % 5 == 2) 16f else 0f) + (if (i % 7 == 4) -6f else 0f)) * px
        Triple(x, hgt, i)
    }
    fun paint(mirror: Boolean) {
        // every clump goes into one shape, so overlapping lobes merge instead of showing their edges
        val shape = Path()
        val light = Path()
        for ((x, hgt, i) in clumps) {
            if (x > w * 0.42f && x < w * 0.58f && i % 2 == 0) continue   // a gap in the middle, where the light comes in
            val sgn = if (mirror) 1f else -1f
            val sway = if (mirror) sin(t * 0.8f + i) * 1.5f * px else 0f
            val r = hgt * 0.55f
            for ((dx, dy, k) in listOf(Triple(0f, 0.55f, 1f), Triple(-0.7f, 0.35f, 0.8f), Triple(0.75f, 0.32f, 0.75f), Triple(-0.25f, 0.8f, 0.6f))) {
                val cx = x + r * dx + sway; val cy = shore + sgn * hgt * dy; val rr = r * k
                shape.addOval(androidx.compose.ui.geometry.Rect(cx - rr, cy - rr, cx + rr, cy + rr))
            }
            shape.addRect(androidx.compose.ui.geometry.Rect(x - r * 1.3f + sway, if (mirror) shore else shore - hgt * 0.3f,
                x + r * 1.3f + sway, if (mirror) shore + hgt * 0.3f else shore))
            // the side towards the light, a few lighter dabs
            if (!mirror) for ((dx, dy, k) in listOf(Triple(-0.35f, 0.74f, 0.5f), Triple(-0.95f, 0.47f, 0.38f), Triple(0.35f, 0.62f, 0.3f))) {
                val cx = x + r * dx; val cy = shore - hgt * dy; val rr = r * k
                light.addOval(androidx.compose.ui.geometry.Rect(cx - rr, cy - rr, cx + rr, cy + rr))
            }
        }
        drawPath(shape, color.copy(alpha = if (mirror) 0.30f else 1f))
        if (!mirror) drawPath(light, lerpColor(color, Color(0xFFE8F0D8), 0.25f).copy(alpha = 0.45f))
    }
    paint(false)
    clipRect(top = shore) { paint(true) }
}

private fun lerpColor(a: Color, b: Color, k: Float) = androidx.compose.ui.graphics.lerp(a, b, k)

/** Everything that lives on and around the water, drawn over the painted lake. */
private fun DrawScope.lakeLife(t: Float, elapsed: Float, breath: Float, ripples: List<Float>) {
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

    // lily pads, gently rocking, one with a flower
    lilyPad(Offset(w * 0.20f, h * 0.905f), w * 0.11f, t, 0.3f, flower = true)
    lilyPad(Offset(w * 0.74f, h * 0.865f), w * 0.07f, t, 1.7f, flower = false)
    lilyPad(Offset(w * 0.86f, h * 0.95f), w * 0.10f, t, 2.9f, flower = false)
    lilyPad(Offset(w * 0.38f, h * 0.975f), w * 0.06f, t, 4.1f, flower = false)

    // reeds at both edges, swaying in the breeze
    reeds(0f, w * 0.20f, h, t, 1)
    reeds(w, w * 0.18f, h, t, -1)

    // a dragonfly that hovers near the reeds and darts to a new place every few seconds
    fun spot(n: Int) = Offset(w * (0.70f + 0.12f * sin(n * 2.3f)), h * (0.64f + 0.05f * cos(n * 1.7f)))
    val hop = (t / 6f).toInt()
    val from = spot(hop - 1); val to = spot(hop)
    val f = ((t % 6f) / 0.6f).coerceAtMost(1f)
    dragonfly(Offset(from.x + (to.x - from.x) * f + sin(t * 3f) * 2 * px, from.y + (to.y - from.y) * f + cos(t * 2.4f) * 2 * px), t)
}

private fun DrawScope.lilyPad(c: Offset, r: Float, t: Float, ph: Float, flower: Boolean) {
    val ry = r * 0.36f
    val o = Offset(c.x, c.y + sin(t * 0.9f + ph) * 1.2f * density)
    // a soft shadow on the water, then the pad with its notch
    drawOval(Color(0x33000000), Offset(o.x - r * 1.04f, o.y - ry * 0.8f), Size(r * 2.08f, ry * 2.1f))
    val pad = Path().apply {
        moveTo(o.x, o.y)
        var a = 0.25f + 0.1f * sin(ph)
        lineTo(o.x + cos(a) * r, o.y + sin(a) * ry)
        while (a < 2 * PI.toFloat() - 0.05f) { a += 0.12f; lineTo(o.x + cos(a) * r, o.y + sin(a) * ry) }
        close()
    }
    drawPath(pad, Brush.radialGradient(listOf(Color(0xFF6E9E58), Color(0xFF3F6B3A)), Offset(o.x - r * 0.2f, o.y - ry * 0.3f), r))
    drawPath(pad, Color(0xFF8FBF74).copy(alpha = 0.5f), style = Stroke(1.2f * density))
    for (i in 0 until 6) {
        val a = 0.6f + i * 0.9f
        drawLine(Color(0xFF2F5530).copy(alpha = 0.4f), o, Offset(o.x + cos(a) * r * 0.85f, o.y + sin(a) * ry * 0.85f), 0.8f * density)
    }
    if (flower) {
        val fc = Offset(o.x + r * 0.15f, o.y - ry * 0.35f)
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4 + 0.2f
            drawLine(Color(0xFFF7C6D8), fc, Offset(fc.x + cos(a) * r * 0.32f, fc.y + sin(a) * r * 0.16f - r * 0.06f), r * 0.13f, StrokeCap.Round)
        }
        for (i in 0 until 5) {
            val a = i * 2 * PI.toFloat() / 5
            drawLine(Color(0xFFFFE4EE), fc, Offset(fc.x + cos(a) * r * 0.18f, fc.y + sin(a) * r * 0.09f - r * 0.12f), r * 0.10f, StrokeCap.Round)
        }
        drawCircle(Color(0xFFFFD36B), r * 0.06f, Offset(fc.x, fc.y - r * 0.1f))
    }
}

private fun DrawScope.reeds(edge: Float, spread: Float, h: Float, t: Float, dir: Int) {
    for (i in 0 until 9) {
        val k = i / 8f
        val x = edge + dir * spread * (0.05f + 0.95f * ((i * 37) % 9) / 8f)
        val tall = h * (0.16f + 0.16f * ((i * 53) % 7) / 6f)
        val sway = (sin(t * 0.8f + i * 0.9f) * 0.6f + sin(t * 1.9f + i) * 0.25f) * 10 * density * (0.6f + 0.4f * k)
        val base = Offset(x, h + 8 * density)
        val tip = Offset(x + sway - dir * 6 * density, h - tall)
        val stem = Path().apply { moveTo(base.x, base.y); quadraticTo(x - dir * 2 * density, h - tall * 0.5f, tip.x, tip.y) }
        drawPath(stem, Color(0xFF243A2A).copy(alpha = 0.92f), style = Stroke((2.2f - 0.8f * k) * density, cap = StrokeCap.Round))
        if (i % 3 == 0) {
            // a cattail head a little below the tip
            val head = Offset(base.x + (tip.x - base.x) * 0.82f, base.y + (tip.y - base.y) * 0.82f)
            rotate(sway * 0.6f, head) {
                drawRoundRect(Color(0xFF5A3A24), Offset(head.x - 2.6f * density, head.y - 11 * density), Size(5.2f * density, 20 * density),
                    CornerRadius(2.6f * density))
            }
        } else {
            // a long leaf bending outwards
            val leaf = Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(x + dir * 14 * density + sway, h - tall * 0.55f, x + dir * 26 * density + sway * 1.3f, h - tall * 0.75f)
            }
            drawPath(leaf, Color(0xFF2E4A32).copy(alpha = 0.85f), style = Stroke(1.6f * density, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.dragonfly(c: Offset, t: Float) {
    val px = density
    val wing = Color(0xFFE8F4FF).copy(alpha = 0.55f * (0.6f + 0.4f * sin(t * 40f)))
    drawOval(wing, Offset(c.x - 16 * px, c.y - 5.5f * px), Size(14 * px, 4.5f * px))
    drawOval(wing, Offset(c.x + 2 * px, c.y - 5.5f * px), Size(14 * px, 4.5f * px))
    drawOval(wing, Offset(c.x - 14 * px, c.y + 0.5f * px), Size(12 * px, 4 * px))
    drawOval(wing, Offset(c.x + 2 * px, c.y + 0.5f * px), Size(12 * px, 4 * px))
    drawLine(Color(0xFF2A6F8F), Offset(c.x, c.y - 4 * px), Offset(c.x, c.y + 14 * px), 2.2f * px, StrokeCap.Round)
    drawCircle(Color(0xFF3B8DB0), 2.6f * px, Offset(c.x, c.y - 5 * px))
}
