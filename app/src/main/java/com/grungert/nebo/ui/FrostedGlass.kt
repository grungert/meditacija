package com.grungert.nebo.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.grungert.nebo.sky.SkyClock
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Frost on the glass while snow falls: it creeps in from the corners and the edges, in feathers of ice, and leaves the
 * middle clear. A finger held on it melts it, and the water runs a little way down; slowly the cold takes it back.
 * When the snow has passed ([melting]) it all thaws. A touch that does not move or stay is passed on ([onTap]).
 */
@Composable
fun FrostedGlass(night: Boolean, onTap: (Offset) -> Unit, melting: Boolean = false, hint: Boolean = false, onMelted: () -> Unit = {}, modifier: Modifier = Modifier) {
    val dp = LocalDensity.current.density
    val slop = LocalViewConfiguration.current.touchSlop
    val frost = remember { Rime(dp) }
    val sprite = remember { dropSprite() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var texture by remember { mutableStateOf<Bitmap?>(null) }
    var melted by remember { mutableStateOf(false) }
    var grown by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val thaw by rememberUpdatedState(melting)
    LaunchedEffect(size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        texture = withContext(Dispatchers.Default) { frostTexture(size.width / 2, size.height / 2, dp / 2) }
    }
    val stepped = remember { longArrayOf(0L) }
    // the hint and the first melt are looked at now and then, not every frame
    LaunchedEffect(Unit) {
        while (!melted) {
            kotlinx.coroutines.delay(500)
            if (!grown && frost.reach > 0.25f) grown = true
            if (frost.meltedSome) { melted = true; onMelted() }
        }
    }
    val paints = remember {
        object {
            val tex = Paint(Paint.FILTER_BITMAP_FLAG)
            val cut = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
            val bead = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val src = Rect(); val dst = RectF()
        }
    }
    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = System.nanoTime()
                    var moved = false
                    frost.touch = down.position
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if (!moved && hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) > slop) moved = true
                        frost.touch = ch.position
                        if (moved) ch.consume()
                    }
                    frost.touch = null
                    // a quick touch is a tap for the screen under the glass; a held or moving one was melting
                    if (!moved && System.nanoTime() - start < 350_000_000L) tap(down.position)
                }
            },
        ) {
            // one step a sky frame, taken here so the frost is drawn with the sky and never between its frames
            val f = SkyClock.frame
            if (f != stepped[0] && size != IntSize.Zero) { stepped[0] = f; frost.step(SkyClock.dt.coerceAtMost(0.1f), size.width, size.height, thaw) }
            val mask = frost.mask ?: return@Canvas
            val tex = texture ?: return@Canvas
            drawIntoCanvas { cv ->
                val c = cv.nativeCanvas
                val w = size.width.toFloat(); val h = size.height.toFloat()
                val layer = c.saveLayer(0f, 0f, w, h, null)
                paints.tex.alpha = if (night) 170 else 235
                paints.src.set(0, 0, tex.width, tex.height); paints.dst.set(0f, 0f, w, h)
                c.drawBitmap(tex, paints.src, paints.dst, paints.tex)
                paints.src.set(0, 0, mask.width, mask.height)
                c.drawBitmap(mask, paints.src, paints.dst, paints.cut)
                c.restoreToCount(layer)
                for (b in frost.beads) {
                    val a = ((b.life - (frost.time - b.born)) / 3f).coerceIn(0f, 1f) * ((frost.time - b.born) / 0.3f).coerceIn(0f, 1f)
                    if (a <= 0.02f) continue
                    paints.bead.alpha = (a * (if (night) 160 else 230)).toInt()
                    paints.src.set(0, 0, sprite.width, sprite.height)
                    paints.dst.set(b.x - b.r, b.y - b.r * 1.15f, b.x + b.r, b.y + b.r)
                    c.drawBitmap(sprite, paints.src, paints.dst, paints.bead)
                }
            }
        }
        AnimatedVisibility(hint && !melted && grown, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp),
            enter = fadeIn(tween(1200)), exit = fadeOut(tween(600))) {
            Text("Zadrži prst na inju da ga otopiš", style = ReadStyle.copy(fontSize = 17.sp, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.85f), shadow = skyShadow))
        }
    }
}

private class Melt(var x: Float, var y: Float, val r: Float, val born: Float, val life: Float, var run: Float)

/** Where the frost has reached, cell by cell, and where a finger melted it. */
private class Rime(private val dp: Float) {
    var mask: Bitmap? = null; private set
    val beads = ArrayList<Melt>()
    var time = 0f; private set
    /** How far in from the edges the frost has crept, as a share of half the narrow side. */
    var reach = 0f; private set
    var meltedSome = false; private set
    @Volatile var touch: Offset? = null
    private var gw = 0; private var gh = 0
    private var arrive = FloatArray(0)    // how far in each cell lies, with a little randomness, so the front is ragged
    private var melt = FloatArray(0)
    private var bytes = ByteArray(0)
    private var buf: ByteBuffer? = null
    private var dripWait = 0f
    private val rnd = Random(31)

    private fun ensure(w: Int, h: Int) {
        val nw = (w / CELL).coerceAtLeast(1); val nh = (h / CELL).coerceAtLeast(1)
        if (nw == gw && nh == gh) return
        gw = nw; gh = nh
        val half = minOf(gw, gh) / 2f
        // two layers of noise, eased between their grid points, so the frost's front is ragged but never blocky
        fun noise(step: Int, seed: Int): (Int, Int) -> Float {
            val cw = gw / step + 2; val ch = gh / step + 2
            val r = Random(seed)
            val g = FloatArray(cw * ch) { r.nextFloat() }
            return { x, y ->
                val fx = x.toFloat() / step; val fy = y.toFloat() / step
                val x0 = fx.toInt(); val y0 = fy.toInt(); val tx = fx - x0; val ty = fy - y0
                fun at(a: Int, b: Int) = g[b.coerceAtMost(ch - 1) * cw + a.coerceAtMost(cw - 1)]
                (at(x0, y0) * (1 - tx) + at(x0 + 1, y0) * tx) * (1 - ty) + (at(x0, y0 + 1) * (1 - tx) + at(x0 + 1, y0 + 1) * tx) * ty
            }
        }
        val big = noise(14, 3); val fine = noise(4, 5)
        arrive = FloatArray(gw * gh) { i ->
            val x = i % gw; val y = i / gw
            val edge = minOf(minOf(x, gw - 1 - x), minOf(y, gh - 1 - y)) / half
            val corner = minOf(minOf(hypot(x.toFloat(), y.toFloat()), hypot((gw - x).toFloat(), y.toFloat())),
                minOf(hypot(x.toFloat(), (gh - y).toFloat()), hypot((gw - x).toFloat(), (gh - y).toFloat()))) / half
            minOf(edge * 1.5f, corner * 0.55f) + 0.16f * big(x, y) + 0.06f * fine(x, y)
        }
        melt = FloatArray(gw * gh)
        bytes = ByteArray(gw * gh)
        buf = ByteBuffer.wrap(bytes)
        mask = Bitmap.createBitmap(gw, gh, Bitmap.Config.ALPHA_8)
    }

    fun step(dt: Float, w: Int, h: Int, thaw: Boolean) {
        ensure(w, h)
        time += dt
        // the frost creeps in over the first minute, then barely more; the middle stays clear
        reach = 0.6f * (1f - kotlin.math.exp(-time / 28f))
        val held = touch
        val r = 34f * dp / CELL
        val cx = (held?.x ?: 0f) / CELL; val cy = (held?.y ?: 0f) / CELL
        for (i in arrive.indices) {
            var m = melt[i]
            if (thaw) m = minOf(1f, m + 0.3f * dt)
            else if (m > 0f) m = maxOf(0f, m - 0.022f * dt)   // the cold takes it back, slowly
            if (held != null) {
                val q = hypot((i % gw) - cx, (i / gw) - cy) / r
                if (q < 1f) m = minOf(1f, m + 1.5f * dt * (1f - q * q))
            }
            melt[i] = m
            val cover = ((reach - arrive[i]) / 0.12f).coerceIn(0f, 1f)
            bytes[i] = (cover * (1f - m) * 255).toInt().coerceIn(0, 255).toByte()
        }
        buf!!.rewind()
        mask!!.copyPixelsFromBuffer(buf!!)
        // the melt water: a drop now and then from under the finger, running a little way down
        if (held != null) {
            val i = (cy.toInt().coerceIn(0, gh - 1)) * gw + cx.toInt().coerceIn(0, gw - 1)
            if (((reach - arrive[i]) / 0.12f) > 0.3f) {
                meltedSome = meltedSome || melt[i] > 0.6f
                dripWait -= dt
                if (dripWait <= 0f && melt[i] > 0.4f && beads.size < 60) {
                    dripWait = 0.35f + rnd.nextFloat() * 0.4f
                    val a = (rnd.nextFloat() - 0.5f) * 1.6f
                    beads += Melt(held.x + sin(a) * 30f * dp, held.y + cos(a) * 30f * dp, (2f + 2.5f * rnd.nextFloat()) * dp, time, 12f + 8f * rnd.nextFloat(), (20f + 40f * rnd.nextFloat()) * dp)
                }
            }
        }
        for (b in beads) if (b.run > 0f) { val d = minOf(b.run, 22f * dp * dt); b.y += d; b.run -= d }
        beads.removeAll { time - it.born > it.life }
    }

    companion object { const val CELL = 6 }
}

/**
 * The frost itself, drawn once: a pale bloom of ice along the edges, and feathers of crystals growing inward from
 * them, each stem with side-branches at sixty degrees, as frost grows on a cold window.
 */
private fun frostTexture(w: Int, h: Int, px: Float): Bitmap {
    val b = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val r = Random(41)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    // the bloom: white haze, thickest at the very edge
    val edge = minOf(w, h) * 0.45f
    for ((x, y) in listOf(0f to 0f, w.toFloat() to 0f, 0f to h.toFloat(), w.toFloat() to h.toFloat())) {
        fill.shader = RadialGradient(x, y, edge * 1.4f, intArrayOf(0xB0F2F8FF.toInt(), 0x50E6F0FF, 0x00E6F0FF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, edge * 1.4f, fill)
    }
    fill.shader = null
    fill.color = 0x22EEF4FF
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
    // crystals
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    fun fern(x0: Float, y0: Float, ang: Float, len: Float, depth: Int) {
        var x = x0; var y = y0; var a = ang
        var walked = 0f
        val step = 4f * px
        line.strokeWidth = (1.4f - 0.35f * depth).coerceAtLeast(0.5f) * px
        line.color = android.graphics.Color.argb(150 - 35 * depth, 248, 252, 255)
        var nextBranch = (6f + r.nextFloat() * 8f) * px
        while (walked < len) {
            a += (r.nextFloat() - 0.5f) * 0.18f
            val nx = x + cos(a) * step; val ny = y + sin(a) * step
            c.drawLine(x, y, nx, ny, line)
            x = nx; y = ny; walked += step
            if (depth < 2 && walked > nextBranch) {
                nextBranch = walked + (6f + r.nextFloat() * 9f) * px
                val left = (len - walked) * (0.35f + 0.2f * r.nextFloat())
                val side = if (r.nextBoolean()) 1f else -1f
                fern(x, y, a + side * 1.05f, left, depth + 1)
                if (r.nextFloat() < 0.6f) fern(x, y, a - side * 1.05f, left * 0.8f, depth + 1)
                line.strokeWidth = (1.4f - 0.35f * depth).coerceAtLeast(0.5f) * px
                line.color = android.graphics.Color.argb(150 - 35 * depth, 248, 252, 255)
            }
        }
    }
    val inward = minOf(w, h) * 0.5f
    repeat(70) {
        // seeds along the edges, more of them by the corners
        val side = r.nextInt(4)
        val t = r.nextFloat().let { if (r.nextBoolean()) it * it * 0.5f else 1f - it * it * 0.5f }
        val (x, y, a) = when (side) {
            0 -> Triple(t * w, 0f, (Math.PI / 2).toFloat())
            1 -> Triple(t * w, h.toFloat(), (-Math.PI / 2).toFloat())
            2 -> Triple(0f, t * h, 0f)
            else -> Triple(w.toFloat(), t * h, Math.PI.toFloat())
        }
        fern(x, y, a + (r.nextFloat() - 0.5f) * 1.2f, inward * (0.25f + 0.55f * r.nextFloat()), 0)
    }
    // and fine grains of rime between them
    fill.color = 0x60FFFFFF
    repeat(2500) { c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, (0.4f + r.nextFloat()) * px, fill) }
    return b
}
