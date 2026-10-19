package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.grungert.nebo.R
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.grungert.nebo.sky.Palette
import com.grungert.nebo.sky.Season
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** The three minutes of the breathing space, one each: wide, narrow, wide again. */
const val HOURGLASS_SECONDS = 180

/** The stage (0, 1 or 2), with its name, its title and what to do. */
val HOURGLASS_STAGES = listOf(
    Triple("Široko", "Šta je sada tu?", "Primijeti šta je u tebi: misli, osjećaje, tijelo. Ne mijenjaj ništa, samo vidi."),
    Triple("Usko", "Samo dah", "Skupi pažnju u stomak. Prati jedan udah, pa jedan izdah. Kad odlutaš, vrati se dahu."),
    Triple("Opet široko", "Cijelo tijelo", "Raširi pažnju na cijelo tijelo: držanje, lice, ruke, stopala. Kao da cijelo tijelo diše."),
)

fun hourglassStage(elapsed: Float) = (elapsed / 60f).toInt().coerceIn(0, 2)

// a painted palette: warm wood with a cool shadow side, sand like starlight
private val WOOD = Color(0xFF8A5634)
private val WOOD_LIT = Color(0xFFD49B66)
private val WOOD_SHADE = Color(0xFF4A2F27)
private val WOOD_LINE = Color(0xFF2E1D18)
private val FLOOR_LIT = Color(0xFFD9A06A)
private val FLOOR = Color(0xFFA9683F)
private val FLOOR_SHADE = Color(0xFF6B3F2A)
private val MOSS = Color(0xFF6E9A47)
private val MOSS_LIT = Color(0xFFA8C46A)
private val MOSS_DEEP = Color(0xFF3F6634)
private val SAND_LIGHT = Color(0xFFFFF0C8)
private val SAND = Color(0xFFF6CF86)
private val SAND_DEEP = Color(0xFFD99A55)
private val FIREFLY = Color(0xFFE9F7A8)
private val LANTERN = Color(0xFFFFC777)

private class Grain(val u: Float, val v: Float, val ph: Float, val sp: Float, val s: Float, val w: Float)

private fun smooth(a: Float, b: Float, x: Float): Float {
    val k = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

private fun rgb(c: FloatArray) = Color(c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f))

/** The light of the moment: the sky's palette (time of day, season, weather), how much sun there is, and the season. */
class HourglassLight(val pal: Palette, val sun: Float, val season: Season)

/**
 * The three-minute breathing space as a quiet corner of a veranda by the water: an hourglass held by a small tree that
 * grows from its base and curls over its top, on a woven mat, with a paper lantern, a bowl of tea, a few books, a wind
 * chime and a branch of the season at the edge of the roof. The sky and the water behind are the living sky, so the
 * scene follows the hour, the weather and the season; at night the lantern carries the light.
 *
 * Over the three minutes the upper sand runs down into a growing heap below. Each minute also shows in the light: in
 * the first, motes drift above the sand (the wide view of what is here); in the second the neck glows and the falling
 * thread brightens (the breath alone); in the third the lower heap glows and fireflies rise and drift out across the
 * veranda (the whole body).
 */
@Composable
fun HourglassView(elapsed: Float, light: HourglassLight, modifier: Modifier = Modifier) {
    // the hourglass with its tree is a painting, one for each season; everything that moves is drawn over it
    val art = ImageBitmap.imageResource(when (light.season) {
        Season.SPRING -> R.drawable.hourglass_spring
        Season.SUMMER -> R.drawable.hourglass_summer
        Season.AUTUMN -> R.drawable.hourglass_autumn
        Season.WINTER -> R.drawable.hourglass_winter
    })
    val mask = ImageBitmap.imageResource(R.drawable.hourglass_sway)
    val sway = remember(art) { TreeSway(art, mask) }
    val grains = remember {
        val r = Random(7)
        List(70) { Grain(r.nextFloat() * 2 - 1, r.nextFloat(), r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 0.8f, 0.7f + r.nextFloat() * 0.9f, r.nextFloat()) }
    }
    // drawn in its own layer, so the evening can darken what is painted here without touching the sky behind
    Canvas(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) { scene(elapsed, grains, light, art, sway) }
}

/** A point on a cubic curve. */
private fun cubicAt(p0: Offset, p1: Offset, p2: Offset, p3: Offset, f: Float): Offset {
    val u = 1 - f
    return p0 * (u * u * u) + p1 * (3 * u * u * f) + p2 * (3 * u * f * f) + p3 * (f * f * f)
}

private fun curve(p0: Offset, p1: Offset, p2: Offset, p3: Offset, n: Int = 32) = List(n + 1) { cubicAt(p0, p1, p2, p3, it / n.toFloat()) }

/** A limb of wood along a centre line, its width changing from one end to the other, as one closed outline. */
private fun limb(pts: List<Offset>, width: (Float) -> Float): Path {
    val n = pts.size
    val left = ArrayList<Offset>(n); val right = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val a = pts[max(0, i - 1)]; val b = pts[min(n - 1, i + 1)]
        val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(0.001f)
        val nx = -(b.y - a.y) / len; val ny = (b.x - a.x) / len
        val hw = width(i / (n - 1f)) / 2
        left += Offset(pts[i].x + nx * hw, pts[i].y + ny * hw)
        right += Offset(pts[i].x - nx * hw, pts[i].y - ny * hw)
    }
    return Path().apply {
        moveTo(left[0].x, left[0].y)
        for (p in left.drop(1)) lineTo(p.x, p.y)
        for (p in right.reversed()) lineTo(p.x, p.y)
        close()
    }
}

/** A leaf: two curves meeting at a point, with a midrib. */
private fun DrawScope.leaf(at: Offset, size: Float, angle: Float, color: Color, rib: Color) {
    rotate(angle, at) {
        val p = Path().apply {
            moveTo(at.x, at.y)
            quadraticTo(at.x + size * 0.5f, at.y - size * 0.42f, at.x + size, at.y)
            quadraticTo(at.x + size * 0.5f, at.y + size * 0.42f, at.x, at.y)
            close()
        }
        drawPath(p, color)
        drawLine(rib, at, Offset(at.x + size * 0.85f, at.y), 0.7f * density)
    }
}

/** The colours of leaves in this season, from the lit side to the shaded. */
private fun leafColors(season: Season) = when (season) {
    Season.SPRING -> listOf(Color(0xFFB5DB86), Color(0xFF8CC46B), Color(0xFF6FA552))
    Season.SUMMER -> listOf(Color(0xFF86BE5C), Color(0xFF5E9A45), Color(0xFF447D36))
    Season.AUTUMN -> listOf(Color(0xFFEBB64E), Color(0xFFE08A3C), Color(0xFFC0532E))
    Season.WINTER -> listOf(Color(0xFF8A9A70), Color(0xFF6F7F5A), Color(0xFF55624A))
}

/** A crown of foliage: a few soft painted masses, darker below, with leaves catching the light along their edges. */
private fun DrawScope.crown(c: Offset, r: Float, seed: Int, season: Season, t: Float) {
    if (season == Season.WINTER) { leafCluster(c, r, 8, seed, season, t); return }
    val colors = leafColors(season)
    val rnd = Random(seed)
    val sway = Offset(sin(t * 0.6f + seed) * 2 * density, 0f)
    repeat(5) {
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        val d = r * 0.45f * rnd.nextFloat()
        val m = Offset(c.x + cos(a) * d, c.y + sin(a) * d * 0.6f) + sway
        val rr = r * (0.55f + 0.25f * rnd.nextFloat())
        drawOval(Brush.verticalGradient(listOf(colors[1], colors[2], lerp(colors[2], Color.Black, 0.25f)), m.y - rr * 0.7f, m.y + rr * 0.7f),
            Offset(m.x - rr, m.y - rr * 0.7f), Size(rr * 2, rr * 1.4f))
    }
    leafCluster(c + sway, r * 1.05f, 34, seed + 1, season, t)
}

/** A cluster of leaves around [c], stirring in the air; fewer in winter. */
private fun DrawScope.leafCluster(c: Offset, r: Float, n: Int, seed: Int, season: Season, t: Float) {
    val rnd = Random(seed)
    val colors = leafColors(season)
    val count = if (season == Season.WINTER) n / 4 else n
    repeat(count) { i ->
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        val d = r * rnd.nextFloat().pow(0.6f)
        val p = Offset(c.x + cos(a) * d, c.y + sin(a) * d * 0.75f)
        val sway = sin(t * 0.9f + i * 0.7f + seed) * 7f
        // leaves higher up catch more light
        val col = colors[if (sin(a) < -0.2f) 0 else if (i % 3 == 0) 2 else 1]
        leaf(p, r * (0.30f + 0.16f * rnd.nextFloat()), Math.toDegrees(a.toDouble()).toFloat() + sway, col, colors[2].copy(alpha = 0.7f))
    }
}

/** A five-petalled blossom. */
private fun DrawScope.blossom(c: Offset, r: Float, spin: Float) {
    for (k in 0 until 5) {
        val a = spin + k * 2 * PI.toFloat() / 5
        drawCircle(Color(0xFFF8C8D8), r * 0.55f, Offset(c.x + cos(a) * r * 0.55f, c.y + sin(a) * r * 0.55f))
    }
    drawCircle(Color(0xFFFFE8F0), r * 0.35f, c)
    drawCircle(Color(0xFFE9A0B4), r * 0.16f, c)
}

/** The branch at the edge of the roof: blossom in spring, leaves in summer, red and gold in autumn, snow in winter. */
private fun DrawScope.seasonBranch(w: Float, h: Float, season: Season, t: Float) {
    val px = density
    val main = curve(Offset(w + 10 * px, h * 0.055f), Offset(w * 0.86f, h * 0.10f), Offset(w * 0.74f, h * 0.10f), Offset(w * 0.58f, h * 0.17f))
    val twig = curve(Offset(w * 0.80f, h * 0.10f), Offset(w * 0.76f, h * 0.15f), Offset(w * 0.70f, h * 0.19f), Offset(w * 0.66f, h * 0.24f), 16)
    drawPath(limb(main) { 9 * px * (1 - 0.7f * it) }, Color(0xFF4A3329))
    drawPath(limb(twig) { 5 * px * (1 - 0.7f * it) }, Color(0xFF4A3329))
    val spots = listOf(main[6], main[12], main[18], main[24], main[30], twig[6], twig[12], twig[16])
    for ((i, p) in spots.withIndex()) {
        val sway = Offset(sin(t * 0.7f + i) * 2 * px, cos(t * 0.6f + i) * 1.5f * px)
        when (season) {
            Season.SPRING -> {
                blossom(p + sway, 9 * px, i * 0.8f)
                blossom(p + sway + Offset(11 * px, 6 * px), 7 * px, i * 1.3f)
                if (i % 2 == 0) blossom(p + sway + Offset(-6 * px, 10 * px), 6 * px, i * 0.4f)
            }
            Season.SUMMER, Season.AUTUMN -> leafCluster(p + sway, 18 * px, 6, 40 + i, season, t)
            Season.WINTER -> drawOval(Color.White.copy(alpha = 0.92f), Offset(p.x - 8 * px, p.y - 5 * px), Size(16 * px, 5 * px))
        }
    }
}

/** The wind chime under the roof: a glass bell, its clapper, and a paper strip the air keeps turning. */
private fun DrawScope.windChime(x: Float, top: Float, h: Float, t: Float) {
    val px = density
    val swing = sin(t * 0.8f) * 6f + sin(t * 1.9f) * 2f
    rotate(swing * 0.4f, Offset(x, top)) {
        val bellTop = top + h * 0.06f
        drawLine(Color(0xFF3A2A22), Offset(x, top), Offset(x, bellTop), 1 * px)
        val r = 11 * px
        val bell = Path().apply {
            moveTo(x - r, bellTop + r * 1.1f)
            quadraticTo(x - r, bellTop, x, bellTop)
            quadraticTo(x + r, bellTop, x + r, bellTop + r * 1.1f)
            close()
        }
        drawPath(bell, Color(0xFFDDEFF5).copy(alpha = 0.85f))
        drawLine(Color(0xFF7FB3C8), Offset(x - r * 0.8f, bellTop + r * 0.7f), Offset(x + r * 0.8f, bellTop + r * 0.7f), 1.5f * px)
        drawCircle(Color.White.copy(alpha = 0.8f), 1.6f * px, Offset(x - r * 0.4f, bellTop + r * 0.35f))
        rotate(swing, Offset(x, bellTop + r)) {
            drawLine(Color(0xFF3A2A22), Offset(x, bellTop + r), Offset(x, bellTop + r + h * 0.03f), 0.8f * px)
            val stripTop = bellTop + r + h * 0.03f
            drawRect(Color(0xFFF2E8D2), Offset(x - 7 * px, stripTop), Size(14 * px, h * 0.075f))
            // a small painted leaf on the strip
            leaf(Offset(x - 2.5f * px, stripTop + h * 0.04f), 8 * px, -60f, MOSS, MOSS_DEEP)
        }
    }
}

/** A paper lantern on the floor: a wooden frame and glowing panels, brighter as the light outside fades. */
private fun DrawScope.lantern(x: Float, bottom: Float, w: Float, h: Float, glow: Float) {
    val px = density
    val top = bottom - h
    drawRect(Color.Black.copy(alpha = 0.25f), Offset(x - w * 0.05f, bottom - 3 * px), Size(w * 1.1f, 6 * px))
    drawRect(Brush.verticalGradient(listOf(lerp(Color(0xFFF7E3BE), LANTERN, glow), lerp(Color(0xFFE5C894), Color(0xFFFFA94D), glow)), top, bottom),
        Offset(x, top + h * 0.08f), Size(w, h * 0.84f))
    for (i in 1..2) drawLine(WOOD_SHADE.copy(alpha = 0.7f), Offset(x + w * i / 3f, top + h * 0.08f), Offset(x + w * i / 3f, bottom - h * 0.08f), 1.4f * px)
    drawLine(WOOD_SHADE.copy(alpha = 0.7f), Offset(x, top + h * 0.5f), Offset(x + w, top + h * 0.5f), 1.4f * px)
    drawRect(WOOD_SHADE, Offset(x - 3 * px, top), Size(w + 6 * px, h * 0.08f))
    drawRect(WOOD_SHADE, Offset(x - 3 * px, bottom - h * 0.08f), Size(w + 6 * px, h * 0.08f))
    for (sx in listOf(x - 3 * px, x + w - 1 * px)) drawRect(WOOD, Offset(sx, top), Size(4 * px, h))
}

/** A celadon bowl of tea, steam rising from it. */
private fun DrawScope.teaBowl(c: Offset, r: Float, t: Float) {
    val px = density
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(c.x - r * 1.05f, c.y + r * 0.55f), Size(r * 2.1f, r * 0.4f))
    val bowl = Path().apply {
        moveTo(c.x - r, c.y)
        cubicTo(c.x - r, c.y + r * 0.9f, c.x + r, c.y + r * 0.9f, c.x + r, c.y)
        close()
    }
    drawPath(bowl, Brush.horizontalGradient(listOf(Color(0xFF9DBFAE), Color(0xFF6E8F84), Color(0xFF4C675F)), c.x - r, c.x + r))
    drawOval(Color(0xFF55705F), Offset(c.x - r, c.y - r * 0.18f), Size(r * 2, r * 0.36f))
    drawOval(Color(0xFF8FA65A), Offset(c.x - r * 0.86f, c.y - r * 0.12f), Size(r * 1.72f, r * 0.26f))
    for (k in 0 until 3) {
        val s = Path()
        for (i in 0..20) {
            val f = i / 20f
            val y = c.y - r * 0.2f - r * 2.4f * f
            val x = c.x + (k - 1) * r * 0.35f + sin(f * 6f + t * 0.8f + k * 2f) * r * 0.18f * f
            if (i == 0) s.moveTo(x, y) else s.lineTo(x, y)
        }
        drawPath(s, Color.White.copy(alpha = 0.16f), style = Stroke(2.2f * px, cap = StrokeCap.Round))
    }
}

/** Two books lying one on the other. */
private fun DrawScope.books(x: Float, y: Float, w: Float) {
    val px = density
    val h = w * 0.16f
    drawRect(Color.Black.copy(alpha = 0.22f), Offset(x - 4 * px, y + 2 * h - 2 * px), Size(w + 10 * px, 6 * px))
    drawRect(Color(0xFF2E4A6B), Offset(x, y + h), Size(w, h))
    drawRect(Color(0xFFF1E6CF), Offset(x + w * 0.05f, y + h * 1.15f), Size(w * 0.9f, h * 0.18f))
    drawRect(Color(0xFF7A3E2E), Offset(x + w * 0.06f, y), Size(w * 0.86f, h))
    drawRect(Color(0xFFF1E6CF), Offset(x + w * 0.1f, y + h * 0.15f), Size(w * 0.78f, h * 0.18f))
    drawLine(Color(0xFFD9B36A), Offset(x + w * 0.18f, y + h * 0.6f), Offset(x + w * 0.5f, y + h * 0.6f), 1.2f * px)
}

/**
 * The wind in the painted tree: an AGSL shader that draws the painting, moving each pixel a little by how far it is
 * from the still parts (the sway mask: glass, caps, base and trunk are 0, the tips of the crowns 1). The crowns sway
 * slowly in two rhythms with the odd gust, each part a little out of step with the next, and the leaves flutter
 * finely; the trunk and the hourglass never move. Amplitudes are a few pixels, slow enough not to pull at attention.
 */
private class TreeSway(art: ImageBitmap, mask: ImageBitmap) {
    private val shader = android.graphics.RuntimeShader(
        """
        uniform shader art;
        uniform shader mask;
        uniform float2 origin;
        uniform float scale;
        uniform float maskScale;
        uniform float time;
        half4 main(float2 frag) {
            float2 p = (frag - origin) / scale;                  // a pixel of the painting
            float m = mask.eval(p * maskScale).r;
            float gust = 0.65 + 0.35 * sin(time * 0.21) * sin(time * 0.34 + 1.3);
            float phase = p.x * 0.004 + p.y * 0.006;
            float sway = sin(time * 0.85 + phase) * 0.7 + sin(time * 1.6 + phase * 2.3) * 0.3;
            float2 d = float2(sway * 10.0 * gust, cos(time * 1.05 + phase) * 2.5 * gust) * m;
            d += float2(sin(time * 3.6 + p.y * 0.08), cos(time * 3.1 + p.x * 0.09)) * 1.3 * m;
            return art.eval(p - d);
        }
        """.trimIndent()
    )
    private val scaleFactor: Float

    init {
        shader.setInputShader("art", android.graphics.BitmapShader(art.asAndroidBitmap(), android.graphics.Shader.TileMode.DECAL, android.graphics.Shader.TileMode.DECAL))
        shader.setInputShader("mask", android.graphics.BitmapShader(mask.asAndroidBitmap(), android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP))
        scaleFactor = mask.width / art.width.toFloat()
        shader.setFloatUniform("maskScale", scaleFactor)
    }
    private val brush = ShaderBrush(shader)

    /** Draws the painting with its top-left at [ox], [oy], [sc] screen pixels to one of its own, at [time] seconds. */
    fun DrawScope.draw(ox: Float, oy: Float, sc: Float, w: Float, h: Float, time: Float) {
        shader.setFloatUniform("origin", ox, oy)
        shader.setFloatUniform("scale", sc)
        shader.setFloatUniform("time", time)
        drawRect(brush, Offset(ox, oy), Size(w, h))
    }
}

/**
 * The glass in the painting: its centre, the neck, where the sand can lie at the top and the bottom, and its inner
 * half-width row by row, all in the painting's pixels (measured on the summer one; the seasons share the glass).
 */
private object Glass {
    const val CX = 685f
    const val NECK = 692f
    const val TOP = 445f
    const val BOTTOM = 985f
    const val ART_TOP = 0f
    const val ART_BOTTOM = 1242f
    const val ART_LEFT = 167f
    const val ART_RIGHT = 1077f
    private val Y = floatArrayOf(400f, 420f, 440f, 460f, 480f, 500f, 520f, 540f, 560f, 580f, 600f, 620f, 640f, 660f, 680f, 692f, 700f, 720f, 740f,
        760f, 780f, 800f, 820f, 840f, 860f, 880f, 900f, 920f, 940f, 960f, 980f, 990f)
    private val HW = floatArrayOf(160f, 155f, 163f, 168f, 169f, 167f, 162f, 154f, 142f, 127f, 109f, 87f, 65f, 46f, 35f, 33f, 34f, 42f, 59f,
        81f, 102f, 122f, 138f, 151f, 160f, 167f, 170f, 170f, 168f, 162f, 153f, 148f)

    /** The inner half-width at a row of the painting: the outer edge less the glass itself. */
    fun inner(y: Float): Float {
        if (y <= Y.first()) return HW.first() - 12
        if (y >= Y.last()) return HW.last() - 12
        var i = 0
        while (Y[i + 1] < y) i++
        val f = (y - Y[i]) / (Y[i + 1] - Y[i])
        return (HW[i] + (HW[i + 1] - HW[i]) * f - 12f).coerceAtLeast(3f)
    }
}

private fun DrawScope.scene(t: Float, grains: List<Grain>, light: HourglassLight, art: ImageBitmap, sway: TreeSway) {
    val w = size.width; val h = size.height; val px = density
    val sunCol = rgb(light.pal.sun)
    val night = light.pal.stars
    val dusk = (1f - light.sun).coerceIn(0f, 1f)
    val floorTop = h * 0.615f

    // ---- the veranda: floor, the sill at its edge, a post and the beam of the roof
    drawRect(Brush.verticalGradient(listOf(FLOOR_SHADE, FLOOR, FLOOR_LIT, FLOOR), floorTop, h), Offset(0f, floorTop), Size(w, h - floorTop))
    // planks running towards the water, and the joints between them
    val vx = w * 0.5f; val vy = h * 0.25f
    for (i in -6..6) {
        val bx = w * 0.5f + i * w * 0.16f
        val k = (floorTop - vy) / (h - vy)
        drawLine(FLOOR_SHADE.copy(alpha = 0.45f), Offset(vx + (bx - vx) * k, floorTop), Offset(bx, h), 1.2f * px)
    }
    for ((i, jy) in listOf(0.70f, 0.80f, 0.92f).withIndex()) {
        val y = h * jy
        drawLine(FLOOR_SHADE.copy(alpha = 0.35f), Offset(w * (0.1f + 0.2f * i), y), Offset(w * (0.3f + 0.2f * i), y), 1 * px)
    }
    // each plank its own tone, with a few lines of grain
    val planks = Random(31)
    for (i in -6..5) {
        val bxA = w * 0.5f + i * w * 0.16f; val bxB = bxA + w * 0.16f
        val kk = (floorTop - vy) / (h - vy)
        val plank = Path().apply {
            moveTo(vx + (bxA - vx) * kk, floorTop); lineTo(vx + (bxB - vx) * kk, floorTop); lineTo(bxB, h); lineTo(bxA, h); close()
        }
        drawPath(plank, (if (planks.nextBoolean()) FLOOR_LIT else FLOOR_SHADE).copy(alpha = 0.10f + 0.08f * planks.nextFloat()))
        clipPath(plank) {
            repeat(4) {
                val f = 0.2f + 0.6f * planks.nextFloat()
                val top = Offset(vx + (bxA + (bxB - bxA) * f - vx) * kk, floorTop)
                val bottom = Offset(bxA + (bxB - bxA) * f, h)
                val g = Path().apply { moveTo(top.x, top.y); quadraticTo((top.x + bottom.x) / 2 + 6 * px, (top.y + bottom.y) / 2, bottom.x, bottom.y) }
                drawPath(g, FLOOR_SHADE.copy(alpha = 0.18f), style = Stroke(0.8f * px))
            }
        }
    }
    // light lying on the floor from the open side: a warm wash and two bright window shapes
    drawRect(Brush.radialGradient(listOf(sunCol.copy(alpha = 0.30f * light.sun), Color.Transparent), Offset(w * 0.75f, h * 0.78f), w * 0.7f),
        Offset(0f, floorTop), Size(w, h - floorTop))
    for ((x0, wd) in listOf(0.55f to 0.16f, 0.76f to 0.16f)) {
        val patch = Path().apply {
            moveTo(w * x0, h * 0.70f); lineTo(w * (x0 + wd), h * 0.70f); lineTo(w * (x0 + wd + 0.10f), h * 0.86f); lineTo(w * (x0 + 0.08f), h * 0.86f); close()
        }
        drawPath(patch, sunCol.copy(alpha = 0.22f * light.sun))
    }
    // the sill
    drawRect(Brush.verticalGradient(listOf(WOOD_LIT, WOOD, WOOD_SHADE), floorTop - h * 0.022f, floorTop + h * 0.006f), Offset(0f, floorTop - h * 0.022f), Size(w, h * 0.028f))
    drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, floorTop - h * 0.021f), Offset(w, floorTop - h * 0.021f), 1.2f * px)
    // the post on the left and the beam above
    drawRect(Brush.horizontalGradient(listOf(WOOD_SHADE, WOOD, WOOD_LIT, WOOD), 0f, w * 0.07f), Offset(0f, 0f), Size(w * 0.07f, floorTop))
    drawLine(WOOD_LINE.copy(alpha = 0.3f), Offset(w * 0.02f, h * 0.08f), Offset(w * 0.022f, floorTop), 1 * px)
    drawRect(Brush.verticalGradient(listOf(WOOD_SHADE, WOOD, WOOD_SHADE), 0f, h * 0.05f), Offset(0f, 0f), Size(w, h * 0.05f))
    windChime(w * 0.22f, h * 0.05f, h, t)

    // ---- the lantern, a bowl of tea and the books
    val lanternGlow = (0.25f + 0.75f * max(dusk, night)).coerceIn(0f, 1f)
    lantern(w * 0.03f, floorTop + h * 0.045f, w * 0.14f, h * 0.10f, lanternGlow)
    teaBowl(Offset(w * 0.10f, h * 0.705f), w * 0.075f, t)
    books(w * 0.80f, h * 0.672f, w * 0.18f)

    // ---- the hourglass and its tree, a painting placed so its base stands on the mat
    val sc = min(h * 0.48f / (Glass.ART_BOTTOM - Glass.ART_TOP), w * 0.92f / (Glass.ART_RIGHT - Glass.ART_LEFT))
    val ox = w * 0.5f - (Glass.ART_LEFT + Glass.ART_RIGHT) / 2 * sc
    val oy = h * 0.705f - Glass.ART_BOTTOM * sc
    fun sy(yImg: Float) = oy + yImg * sc
    val cx = ox + Glass.CX * sc
    val cy = sy(Glass.NECK)
    val topFull = sy(Glass.TOP)
    val bottom = sy(Glass.BOTTOM)
    val y0 = sy(410f); val y1 = bottom
    val gh = y1 - y0
    val half = gh / 2
    val gw = 2 * Glass.inner(480f) * sc
    val neck = Glass.inner(Glass.NECK) * sc
    fun halfWidth(y: Float) = Glass.inner((y - oy) / sc) * sc
    val k = (t / HOURGLASS_SECONDS).coerceIn(0f, 1f)          // how much sand has run
    val gather = smooth(60f, 66f, t) * (1 - smooth(120f, 128f, t))
    val rise = smooth(120f, 130f, t)
    val spread = smooth(126f, 172f, t)
    val appear = smooth(0f, 2.5f, t)

    // the woven mat under it
    val matY = sy(1185f)
    val mat = Path().apply {
        moveTo(cx - w * 0.34f, matY); lineTo(cx + w * 0.30f, matY)
        lineTo(cx + w * 0.38f, matY + h * 0.07f); lineTo(cx - w * 0.42f, matY + h * 0.07f); close()
    }
    drawPath(mat, Brush.verticalGradient(listOf(Color(0xFF51617E), Color(0xFF7F8FAA)), matY, matY + h * 0.07f))
    clipPath(mat) {
        for (row in 0 until 5) for (col in 0 until 12) {
            if ((row + col) % 2 == 0) continue
            val fa = col / 12f; val fb = (col + 1) / 12f
            val ya = matY + h * 0.014f * row; val yb = ya + h * 0.014f
            val ka = row / 5f; val kb = (row + 1) / 5f
            fun xAt(f: Float, k2: Float) = cx - w * (0.34f + 0.08f * k2) + w * (0.64f + 0.16f * k2) * f
            val cell = Path().apply { moveTo(xAt(fa, ka), ya); lineTo(xAt(fb, ka), ya); lineTo(xAt(fb, kb), yb); lineTo(xAt(fa, kb), yb); close() }
            drawPath(cell, Color(0xFFDCE3EE).copy(alpha = 0.22f))
        }
        for (i in 0..16) {
            val f = i / 16f
            drawLine(Color.White.copy(alpha = 0.15f), Offset(cx - w * 0.34f + w * 0.64f * f, matY), Offset(cx - w * 0.42f + w * 0.80f * f, matY + h * 0.07f), 1 * px)
        }
    }
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(cx - w * 0.36f, sy(1205f)), Size(w * 0.62f, h * 0.035f))
    with(sway) { draw(ox, oy, sc, art.width * sc, art.height * sc, t) }

    // ---- evening and night darken everything painted so far; the sky behind is not touched
    val dark = (0.62f * night + 0.18f * dusk * (1 - night)).coerceIn(0f, 0.7f)
    if (dark > 0.01f) drawRect(Color(0xFF141C3A).copy(alpha = dark), blendMode = BlendMode.SrcAtop)
    if (light.sun > 0.01f) drawRect(sunCol.copy(alpha = 0.10f * light.sun), blendMode = BlendMode.SrcAtop)

    // a shaft of light from the open side by day, the lantern's glow by evening
    if (light.sun > 0.05f) {
        val shaft = Path().apply { moveTo(w * 0.95f, 0f); lineTo(w * 1.1f, 0f); lineTo(w * 0.45f, h); lineTo(w * 0.05f, h); close() }
        drawPath(shaft, Brush.linearGradient(listOf(sunCol.copy(alpha = 0.16f * light.sun), Color.Transparent), Offset(w, 0f), Offset(w * 0.3f, h)))
    }
    if (light.sun > 0.05f) {
        val dust = Random(23)
        repeat(26) {
            val f = dust.nextFloat(); val sp = 0.3f + 0.5f * dust.nextFloat()
            val y = h * ((dust.nextFloat() + t * 0.01f * sp) % 1f)
            val x = w * (1.0f - 0.55f * y / h) - w * 0.15f * f + sin(t * sp + f * 9f) * 6 * px
            drawCircle(Color.White.copy(alpha = 0.5f * light.sun * (0.5f + 0.5f * sin(t * 2 * sp + f * 20f))), 1.2f * px, Offset(x, y))
        }
    }
    val lc = Offset(w * 0.10f, floorTop - h * 0.005f)
    drawCircle(Brush.radialGradient(listOf(LANTERN.copy(alpha = 0.55f * lanternGlow), LANTERN.copy(alpha = 0.12f * lanternGlow), Color.Transparent), lc, w * 0.45f), w * 0.45f, lc)

    // ---- the sand: starlight-coloured, grainy, glowing softly (drawn after the dusk, it keeps its own light)
    val specks = Random(5)
    fun sandFill(heap: Path, top: Float, bottom: Float, glow: Float) {
        drawPath(heap, SAND.copy(alpha = (0.35f * glow).coerceAtMost(1f)), style = Stroke(7 * px))
        drawPath(heap, Brush.verticalGradient(listOf(SAND_LIGHT, SAND, SAND_DEEP), top, bottom))
        clipPath(heap) {
            repeat(90) {
                val x = cx + (specks.nextFloat() - 0.5f) * gw
                val y = top + (bottom - top) * specks.nextFloat()
                drawCircle((if (specks.nextFloat() < 0.5f) SAND_DEEP else Color.White).copy(alpha = 0.35f), 0.9f * px, Offset(x, y))
            }
            drawRect(Brush.horizontalGradient(listOf(SAND_DEEP.copy(alpha = 0.45f), Color.Transparent, Color.Transparent, SAND_DEEP.copy(alpha = 0.45f)),
                cx - gw / 2, cx + gw / 2), Offset(cx - gw / 2, top), Size(gw, bottom - top))
        }
    }
    if (k < 0.995f) {
        val level = cy - (cy - topFull) * (1 - k)
        val dip = 7 * px * (1 - k)
        val heap = Path()
        val hw = halfWidth(level) * 0.96f
        heap.moveTo(cx - hw, level)
        for (i in 0..20) { val f = i / 20f; val x = cx - hw + 2 * hw * f; val u = (x - cx) / hw; heap.lineTo(x, level + dip * (1 - u * u)) }
        for (i in 0..24) { val y = level + (cy - level) * i / 24f; heap.lineTo(cx + halfWidth(y) * 0.96f, y) }
        for (i in 24 downTo 0) { val y = level + (cy - level) * i / 24f; heap.lineTo(cx - halfWidth(y) * 0.96f, y) }
        heap.close()
        sandFill(heap, level, cy, 0.6f)
    }
    if (k > 0.005f) {
        val height = (bottom - cy) * 0.62f * k.pow(0.8f)
        val base = bottom
        val top = base - height
        val heap = Path()
        heap.moveTo(cx - halfWidth(base) * 0.96f, base)
        for (i in 0..24) { val y = base - (base - top) * 0.55f * i / 24f; heap.lineTo(cx - halfWidth(y) * 0.96f, y) }
        val shoulder = base - (base - top) * 0.55f
        val sw = halfWidth(shoulder) * 0.96f
        heap.quadraticTo(cx - sw * 0.3f, top - height * 0.08f, cx, top)
        heap.quadraticTo(cx + sw * 0.3f, top - height * 0.08f, cx + sw, shoulder)
        for (i in 24 downTo 0) { val y = base - (base - top) * 0.55f * i / 24f; heap.lineTo(cx + halfWidth(y) * 0.96f, y) }
        heap.close()
        sandFill(heap, top, base, 0.6f + 1.2f * rise)
    }
    if (k < 0.995f) {
        val bottomTop = bottom - (bottom - cy) * 0.62f * k.pow(0.8f)
        val sw = (1.3f + 1.2f * gather) * px
        drawLine(SAND.copy(alpha = 0.22f * (0.6f + gather)), Offset(cx, cy), Offset(cx, bottomTop), sw * 6, StrokeCap.Round)
        drawLine(SAND_LIGHT, Offset(cx, cy - 2 * px), Offset(cx, bottomTop), sw, StrokeCap.Round)
    }
    if (gather > 0.01f) {
        val r = (22 + 5 * sin(t * 0.7f)) * px
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * gather), SAND.copy(alpha = 0.35f * gather), Color.Transparent), Offset(cx, cy), r * 2f),
            r * 2f, Offset(cx, cy))
    }

    // a soft gleam over the sand, so it sits behind the glass rather than on it
    for (topBulb in listOf(true, false)) {
        val g = Path()
        for (i in 0..16) {
            val f = 0.3f + 0.5f * i / 16f
            val y = if (topBulb) cy - (cy - y0) * f else cy + (y1 - cy) * f
            val x = cx - halfWidth(y) * 0.72f
            if (i == 0) g.moveTo(x, y) else g.lineTo(x, y)
        }
        drawPath(g, Color.White.copy(alpha = 0.22f), style = Stroke(3 * px, cap = StrokeCap.Round))
    }

    // ---- what drifts in the air: the season's petals, leaves or snow, and the motes of the three minutes
    val fall = Random(17)
    repeat(10) { i ->
        val sp = 0.6f + 0.6f * fall.nextFloat()
        val ph = fall.nextFloat()
        val f = (t * 0.035f * sp + ph) % 1f
        val x = w * (0.98f - 0.9f * f) + sin(t * 0.9f * sp + i) * w * 0.04f
        val y = h * (0.12f + 0.55f * f) + sin(t * 1.3f + i) * 6 * px
        val spin = t * 60f * sp + i * 40f
        when (light.season) {
            Season.SPRING -> rotate(spin, Offset(x, y)) { drawOval(Color(0xFFF8C3D3).copy(alpha = 0.9f), Offset(x - 4 * px, y - 2.4f * px), Size(8 * px, 4.8f * px)) }
            Season.AUTUMN -> leaf(Offset(x, y), 10 * px, spin, leafColors(Season.AUTUMN)[i % 3], leafColors(Season.AUTUMN)[2])
            Season.WINTER -> drawCircle(Color.White.copy(alpha = 0.85f), 2.2f * px, Offset(x, y))
            Season.SUMMER -> drawCircle(sunCol.copy(alpha = 0.5f * (0.3f + light.sun)), 1.6f * px, Offset(x, y))
        }
    }
    for (g in grains) {
        val level = cy - (cy - topFull) * (1 - k)
        val yt = y0 + (level - y0) * (0.15f + 0.75f * g.v) + gh * 0.012f * sin(t * g.sp * 0.25f + g.ph * 1.3f)
        val xt = cx + halfWidth(yt) * 0.78f * (g.u + 0.05f * sin(t * g.sp * 0.3f + g.ph)).coerceIn(-1f, 1f)
        val q = (t * 0.12f * g.sp + g.v) % 1f
        val ys = cy - gh * 0.08f + gh * 0.30f * q
        val xs = cx + neck * 1.2f * g.u * (1f - q)
        val lift = (t * 0.035f * g.sp + g.w) % 1f
        val yf = y1 - (y1 - h * 0.08f) * lift * (0.35f + 0.65f * spread)
        val xf = cx + w * 0.46f * g.u * (0.3f + 0.7f * spread) + w * 0.04f * sin(t * 0.6f * g.sp + g.ph)
        var x = xt + (xs - xt) * gather
        var y = yt + (ys - yt) * gather
        x += (xf - x) * rise
        y += (yf - y) * rise
        val blink = 0.5f + 0.5f * sin(t * g.sp * 1.6f + g.ph)
        val fade = if (rise > 0.5f) (1f - lift).coerceIn(0f, 1f) * 1.3f else 1f
        val a = (appear * fade * (0.35f + 0.65f * blink)).coerceIn(0f, 1f)
        val color = lerp(SAND_LIGHT, FIREFLY, rise)
        val r = g.s * px * (1f + 0.6f * rise)
        drawCircle(color.copy(alpha = 0.22f * a), r * 4f, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.9f * a), r, Offset(x, y))
    }
}
