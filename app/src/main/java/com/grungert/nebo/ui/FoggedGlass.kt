package com.grungert.nebo.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.ByteBuffer
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The glass fogs over while you sit in the mist, and a finger wipes it clear; the wiped water gathers into beads at
 * the edges of the stroke, and the fog comes back, unevenly, a little faster on every out-breath, as if you breathed
 * on the window. Like the mind: cleared for a moment, and clouding again, and that is all right.
 * A touch that does not move is passed on ([onTap]), so the screen under the glass still answers it.
 * [breathOut]: 0..1, how much of an out-breath is happening now. [lifting]: the mist has passed, and the glass clears.
 */
@Composable
fun FoggedGlass(night: Boolean, breathOut: () -> Float, onTap: (Offset) -> Unit, hint: Boolean = false, onWiped: () -> Unit = {}, lifting: Boolean = false, modifier: Modifier = Modifier) {
    val dp = LocalDensity.current.density
    val slop = LocalViewConfiguration.current.touchSlop
    val fog = remember { Condensation(dp) }
    val grain = remember { grainTile() }
    val sprite = remember { dropSprite() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableLongStateOf(0L) }
    var wiped by remember { mutableStateOf(false) }
    var misted by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val breath by rememberUpdatedState(breathOut)
    val lift by rememberUpdatedState(lifting)
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            if (size != IntSize.Zero) fog.step(dt, size.width, size.height, breath(), lift)
            if (!misted && fog.thick > 0.8f) misted = true
            frame = now
        }
    }
    val paints = remember {
        object {
            val veil = Paint()
            val grain = Paint().apply { shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
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
                    var last = down.position
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if (!moved && hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) > slop) moved = true
                        if (moved && ch.positionChange() != Offset.Zero) {
                            fog.wipe(last.x, last.y, ch.position.x, ch.position.y)
                            last = ch.position
                            ch.consume()
                            if (!wiped) { wiped = true; onWiped() }
                        }
                    }
                    if (!moved) tap(down.position)
                }
            },
        ) {
            frame   // read, so every step draws anew
            val mask = fog.mask ?: return@Canvas
            val tint = if (night) floatArrayOf(0.5f, 0.56f, 0.68f) else floatArrayOf(0.9f, 0.93f, 0.96f)
            val most = if (night) 0.5f else 0.6f
            drawIntoCanvas { cv ->
                val c = cv.nativeCanvas
                val w = size.width.toFloat(); val h = size.height.toFloat()
                // the misted glass, its fine beads of condensation, kept only where the fog is (the mask)
                val layer = c.saveLayer(0f, 0f, w, h, null)
                paints.veil.color = android.graphics.Color.argb((most * 255).toInt(), (tint[0] * 255).toInt(), (tint[1] * 255).toInt(), (tint[2] * 255).toInt())
                c.drawRect(0f, 0f, w, h, paints.veil)
                paints.grain.alpha = if (night) 120 else 200
                c.drawRect(0f, 0f, w, h, paints.grain)
                paints.src.set(0, 0, mask.width, mask.height)
                paints.dst.set(0f, 0f, w, h)
                c.drawBitmap(mask, paints.src, paints.dst, paints.cut)
                c.restoreToCount(layer)
                // the water a finger gathered, at the edges of what it wiped
                for (b in fog.beads) {
                    val left = b.life - (fog.time - b.born)
                    val a = (left / 2f).coerceIn(0f, 1f) * ((fog.time - b.born) / 0.2f).coerceIn(0f, 1f)
                    if (a <= 0.02f) continue
                    paints.bead.alpha = (a * (if (night) 160 else 235)).toInt()
                    paints.src.set(0, 0, sprite.width, sprite.height)
                    paints.dst.set(b.x - b.r, b.y - b.r, b.x + b.r, b.y + b.r * 1.1f)
                    c.drawBitmap(sprite, paints.src, paints.dst, paints.bead)
                }
            }
        }
        // the first time, a word on what the glass is for
        AnimatedVisibility(hint && !wiped && misted, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp),
            enter = fadeIn(tween(1200)), exit = fadeOut(tween(600))) {
            Text("Prstom obriši staklo", style = ReadStyle.copy(fontSize = 17.sp, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.85f), shadow = skyShadow))
        }
    }
}

private class FogBead(val x: Float, val y: Float, val r: Float, val born: Float, val life: Float)

/** How misted the glass is, cell by cell (a cell is [CELL] px across), and the beads the wiping left. */
private class Condensation(private val dp: Float) {
    var mask: Bitmap? = null; private set
    val beads = ArrayList<FogBead>()
    var time = 0f; private set
    /** How misted the glass is on the whole, 0..1. */
    var thick = 0f; private set
    private var gw = 0; private var gh = 0
    private var dens = FloatArray(0)
    private var rate = FloatArray(0)      // each place mists again at its own pace
    private var bytes = ByteArray(0)
    private var buf: ByteBuffer? = null
    private val rnd = Random(23)

    private fun ensure(w: Int, h: Int) {
        val nw = (w / CELL).coerceAtLeast(1); val nh = (h / CELL).coerceAtLeast(1)
        if (nw == gw && nh == gh) return
        gw = nw; gh = nh
        dens = FloatArray(gw * gh)
        // a lumpy pace: random at a coarse grid, eased between, so the fog returns in patches, not all at once
        val cw = gw / 8 + 2; val ch = gh / 8 + 2
        val coarse = FloatArray(cw * ch) { 0.55f + 0.9f * rnd.nextFloat() }
        rate = FloatArray(gw * gh) { i ->
            val x = (i % gw) / 8f; val y = (i / gw) / 8f
            val x0 = x.toInt(); val y0 = y.toInt(); val fx = x - x0; val fy = y - y0
            fun at(a: Int, b: Int) = coarse[(b.coerceAtMost(ch - 1)) * cw + a.coerceAtMost(cw - 1)]
            (at(x0, y0) * (1 - fx) + at(x0 + 1, y0) * fx) * (1 - fy) + (at(x0, y0 + 1) * (1 - fx) + at(x0 + 1, y0 + 1) * fx) * fy
        }
        bytes = ByteArray(gw * gh)
        buf = ByteBuffer.wrap(bytes)
        mask = Bitmap.createBitmap(gw, gh, Bitmap.Config.ALPHA_8)
    }

    fun step(dt: Float, w: Int, h: Int, breathOut: Float, lifting: Boolean) {
        ensure(w, h)
        time += dt
        // misting again: quickly where it is thin, slowing as it thickens; faster on an out-breath.
        // Once the mist has passed, it clears instead, in patches, over a few seconds
        val k = 0.055f * (1f + 1.8f * breathOut) * dt
        var sum = 0f
        for (i in dens.indices) {
            val d = if (lifting) maxOf(0f, dens[i] - 0.3f * dt * rate[i]) else dens[i] + (1f - dens[i]) * k * rate[i]
            dens[i] = d
            sum += d
            bytes[i] = (d * 255).toInt().coerceIn(0, 255).toByte()
        }
        thick = sum / dens.size
        buf!!.rewind()
        mask!!.copyPixelsFromBuffer(buf!!)
        beads.removeAll { time - it.born > it.life }
    }

    /** A finger drawn from one point to the next: the glass is cleared along it, the water pushed to its edges. */
    fun wipe(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (gw == 0) return
        val r = 26f * dp
        val len = hypot(x1 - x0, y1 - y0)
        val steps = (len / (r * 0.3f)).toInt().coerceAtLeast(1)
        val rc = r / CELL
        for (s in 0..steps) {
            val cx = (x0 + (x1 - x0) * s / steps) / CELL
            val cy = (y0 + (y1 - y0) * s / steps) / CELL
            for (gy in (cy - rc).toInt().coerceAtLeast(0)..(cy + rc).toInt().coerceAtMost(gh - 1))
                for (gx in (cx - rc).toInt().coerceAtLeast(0)..(cx + rc).toInt().coerceAtMost(gw - 1)) {
                    val q = hypot(gx - cx, gy - cy) / rc
                    if (q >= 1f) continue
                    val i = gy * gw + gx
                    dens[i] = minOf(dens[i], q * q * q)   // clear at the middle, soft at the rim
                }
        }
        // the water it gathered, as beads along both sides of the stroke
        if (len > 0f && beads.size < 160) {
            val nx = -(y1 - y0) / len; val ny = (x1 - x0) / len
            val n = (len / (14f * dp)).toInt().coerceAtLeast(if (rnd.nextFloat() < 0.5f) 1 else 0)
            repeat(n) {
                val t = rnd.nextFloat()
                val side = if (rnd.nextBoolean()) 1f else -1f
                val off = r * (0.9f + 0.15f * rnd.nextFloat()) * side
                val br = (1.2f + 2.6f * rnd.nextFloat().let { it * it }) * dp
                beads += FogBead(x0 + (x1 - x0) * t + nx * off, y0 + (y1 - y0) * t + ny * off, br, time, 10f + 12f * rnd.nextFloat())
            }
        }
    }

    companion object { const val CELL = 6 }
}

/** A tile of the glass's fine condensation: many tiny beads, some catching the light. */
private fun grainTile(): Bitmap {
    val n = 160
    val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = Random(5)
    repeat(520) {
        val x = r.nextFloat() * n; val y = r.nextFloat() * n
        val s = 0.5f + 1.6f * r.nextFloat().let { it * it }
        p.color = android.graphics.Color.argb(25 + r.nextInt(70), 255, 255, 255)
        c.drawCircle(x, y, s, p)
        // a few with a darker underside, like real beads
        if (s > 1.3f) { p.color = android.graphics.Color.argb(30, 30, 40, 60); c.drawCircle(x, y + s * 0.5f, s * 0.6f, p) }
    }
    return b
}
