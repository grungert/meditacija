package com.grungert.nebo.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import com.grungert.nebo.R
import com.grungert.nebo.sky.Palette
import com.grungert.nebo.sky.Season
import com.grungert.nebo.sky.Weather
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
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

/**
 * How strong the wind is at [t] seconds, 0.3 to 1: two slow rhythms that now and then line up into a gust. The tree,
 * the wind chime, the fern and the falling leaves all follow this one wind.
 */
fun windGust(t: Float) = 0.65f + 0.35f * sin(t * 0.21f) * sin(t * 0.34f + 1.3f)

/**
 * The season to paint and the one it is turning into or out of, with how much of the other to mix in. Seasons follow
 * the calendar's months (as the rest of the sky does); for three weeks either side of a change the two blend, half and
 * half on the day itself, so the tree turns slowly instead of overnight.
 */
fun seasonBlend(c: Calendar = Calendar.getInstance()): Triple<Season, Season, Float> {
    val order = listOf(Season.WINTER, Season.SPRING, Season.SUMMER, Season.AUTUMN)
    // the first day of each season's first month: December, March, June, September
    fun start(year: Int, month: Int) = Calendar.getInstance().apply { clear(); set(year, month, 1) }.timeInMillis
    val now = c.timeInMillis
    val year = c.get(Calendar.YEAR)
    val starts = listOf(
        start(year - 1, Calendar.DECEMBER) to Season.WINTER, start(year, Calendar.MARCH) to Season.SPRING,
        start(year, Calendar.JUNE) to Season.SUMMER, start(year, Calendar.SEPTEMBER) to Season.AUTUMN,
        start(year, Calendar.DECEMBER) to Season.WINTER, start(year + 1, Calendar.MARCH) to Season.SPRING,
    )
    val i = starts.indexOfLast { it.first <= now }
    val cur = starts[i].second
    val window = 21 * 86_400_000f
    val sinceStart = now - starts[i].first
    val untilNext = starts[i + 1].first - now
    return when {
        untilNext < window -> Triple(cur, starts[i + 1].second, 0.5f * (1f - untilNext / window))
        sinceStart < window -> Triple(cur, order[(order.indexOf(cur) + 3) % 4], 0.5f * (1f - sinceStart / window))
        else -> Triple(cur, cur, 0f)
    }
}

// a painted palette: warm wood with a cool shadow side, sand like starlight
private val WOOD = Color(0xFF8A5634)
private val WOOD_LIT = Color(0xFFD49B66)
private val WOOD_SHADE = Color(0xFF4A2F27)
private val WOOD_LINE = Color(0xFF2E1D18)
private val FLOOR_LIT = Color(0xFFD9A06A)
private val FLOOR = Color(0xFFA9683F)
private val FLOOR_SHADE = Color(0xFF6B3F2A)
private val MOSS = Color(0xFF6E9A47)
private val MOSS_DEEP = Color(0xFF3F6634)
private val SAND_LIGHT = Color(0xFFFFF0C8)
private val SAND = Color(0xFFF6CF86)
private val SAND_DEEP = Color(0xFFD99A55)
private val FIREFLY = Color(0xFFE9F7A8)
private val LANTERN = Color(0xFFFFC777)
private val BAMBOO = Color(0xFFC9A86A)

/** The far shore of the lake in the sky shader (1 - LAKE_Y). */
private const val HORIZON = 0.58f
/** The painted base is an ellipse 0.236 as deep as wide: tan(asin(0.236)), how far below the horizon it is per focal length. */
private const val BASE_TAN = 0.2428f

private class Grain(val u: Float, val v: Float, val ph: Float, val sp: Float, val s: Float, val w: Float)

private fun smooth(a: Float, b: Float, x: Float): Float {
    val k = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

private fun rgb(c: FloatArray) = Color(c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f))

/**
 * The light and the weather of the moment: the sky's palette (time of day, season, weather), how much sun there is,
 * the season and the one blending in (with how much), the weather, and whether motion should be kept to a minimum.
 */
class HourglassLight(
    val pal: Palette, val sun: Float, val season: Season,
    val other: Season = season, val blend: Float = 0f,
    val weather: Weather = Weather.CLEAR, val still: Boolean = false,
)

private fun artFor(s: Season) = when (s) {
    Season.SPRING -> R.drawable.hourglass_spring
    Season.SUMMER -> R.drawable.hourglass_summer
    Season.AUTUMN -> R.drawable.hourglass_autumn
    Season.WINTER -> R.drawable.hourglass_winter
}

/**
 * The three-minute breathing space as a quiet corner of a veranda by the water: the painted hourglass and its tree on a
 * straw rug, a paper lantern, a bowl of tea, a fern, a wind chime and a bamboo blind. The sky and the water behind are
 * the living sky, so the scene follows the hour, the weather and the season; at night the lantern and the sand carry
 * the light. One wind moves the tree, the chime, the fern and the falling leaves. Tilting the phone shifts the near
 * things a little against the far ones.
 *
 * Over the three minutes the upper sand runs down into a growing heap below. Each minute also shows in the light: in
 * the first, motes drift above the sand (the wide view of what is here); in the second the neck glows and the falling
 * thread brightens (the breath alone); in the third the lower heap glows and fireflies rise and drift out across the
 * veranda (the whole body).
 */
@Composable
fun HourglassView(elapsed: Float, light: HourglassLight, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // the hourglass with its tree is a painting, one for each season; everything that moves is drawn over it
    val art = ImageBitmap.imageResource(artFor(light.season))
    val art2 = ImageBitmap.imageResource(artFor(light.other))
    val mask = ImageBitmap.imageResource(R.drawable.hourglass_sway)
    val sway = remember(art, art2) { TreeSway(art, art2, mask) }
    val grain = remember { paperGrain() }
    val grains = remember {
        val r = Random(7)
        List(70) { Grain(r.nextFloat() * 2 - 1, r.nextFloat(), r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 0.8f, 0.7f + r.nextFloat() * 0.9f, r.nextFloat()) }
    }
    // how far the phone leans from where it was held at the start, -1..1 each way, smoothed
    var tilt by remember { mutableStateOf(Offset.Zero) }
    if (!light.still) DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        var base: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val b = base ?: e.values.copyOf().also { base = it }
                val tx = ((e.values[0] - b[0]) / 3f).coerceIn(-1f, 1f)
                val ty = ((e.values[1] - b[1]) / 3f).coerceIn(-1f, 1f)
                tilt = Offset(tilt.x + (tx - tilt.x) * 0.12f, tilt.y + (ty - tilt.y) * 0.12f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    // drawn in its own layer, so the evening can darken what is painted here without touching the sky behind
    Canvas(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        scene(elapsed, grains, light, art, sway, grain, tilt)
    }
}

/** A small tile of paper grain, laid over the drawn wood so it sits closer to the painted hourglass. */
private fun paperGrain(): ImageBitmap {
    val n = 96
    val r = Random(41)
    val px = IntArray(n * n) {
        val v = (128 + (r.nextFloat() - 0.5f) * 90).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * The wind in the painted tree: an AGSL shader that draws the painting, moving each pixel a little by how far it is
 * from the still parts (the sway mask: glass, caps, base and trunk are 0, the tips of the crowns 1). The crowns sway
 * slowly in two rhythms, harder in a gust, each part a little out of step with the next, and the leaves flutter
 * finely; the trunk and the hourglass never move. Near a change of season it blends this season's painting with the
 * next one's, so the tree turns over weeks.
 */
private class TreeSway(art: ImageBitmap, art2: ImageBitmap, mask: ImageBitmap) {
    private val shader = android.graphics.RuntimeShader(
        """
        uniform shader art;
        uniform shader art2;
        uniform shader mask;
        uniform float2 origin;
        uniform float scale;
        uniform float maskScale;
        uniform float time;
        uniform float gust;
        uniform float blend;
        half4 main(float2 frag) {
            float2 p = (frag - origin) / scale;                  // a pixel of the painting
            float m = mask.eval(p * maskScale).r;
            float phase = p.x * 0.004 + p.y * 0.006;
            float sway = sin(time * 0.85 + phase) * 0.7 + sin(time * 1.6 + phase * 2.3) * 0.3;
            float2 d = float2(sway * 11.0 * gust, cos(time * 1.05 + phase) * 2.5 * gust) * m;
            d += float2(sin(time * 3.6 + p.y * 0.08), cos(time * 3.1 + p.x * 0.09)) * (0.7 + 0.9 * gust) * m;
            float2 q = p - d;
            half4 a = art.eval(q);
            if (blend <= 0.0) return a;
            return mix(a, art2.eval(q), blend);
        }
        """.trimIndent()
    )

    init {
        fun bitmap(b: ImageBitmap, edge: android.graphics.Shader.TileMode) = android.graphics.BitmapShader(b.asAndroidBitmap(), edge, edge)
        shader.setInputShader("art", bitmap(art, android.graphics.Shader.TileMode.DECAL))
        shader.setInputShader("art2", bitmap(art2, android.graphics.Shader.TileMode.DECAL))
        shader.setInputShader("mask", bitmap(mask, android.graphics.Shader.TileMode.CLAMP))
        shader.setFloatUniform("maskScale", mask.width / art.width.toFloat())
    }
    private val brush = ShaderBrush(shader)

    /** Draws the painting with its top-left at [ox], [oy], [sc] screen pixels to one of its own. */
    fun DrawScope.draw(ox: Float, oy: Float, sc: Float, w: Float, h: Float, time: Float, blend: Float) {
        shader.setFloatUniform("origin", ox, oy)
        shader.setFloatUniform("scale", sc)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("gust", windGust(time))
        shader.setFloatUniform("blend", blend)
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
    const val BOTTOM = 997f      // the middle of the lower bulb's floor, an ellipse seen a little from above
    const val FLOOR_RY = 16f
    /** The centre of the ellipse of the base's bottom rim (335 wide each way, 79 deep), and its centre across. */
    const val BASE_Y = 1145f
    const val BASE_X = 660f
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

private val AUTUMN_LEAVES = listOf(Color(0xFFEBB64E), Color(0xFFE08A3C), Color(0xFFC0532E))

/** What the season lets fall: a petal in spring, a leaf in autumn, a flake in winter, a mote of light in summer. */
private fun DrawScope.fallen(season: Season, at: Offset, spin: Float, i: Int, glow: Color) {
    val px = density
    when (season) {
        Season.SPRING -> rotate(spin, at) { drawOval(Color(0xFFF8C3D3).copy(alpha = 0.92f), Offset(at.x - 4 * px, at.y - 2.4f * px), Size(8 * px, 4.8f * px)) }
        Season.AUTUMN -> leaf(at, 10 * px, spin, AUTUMN_LEAVES[i % 3], AUTUMN_LEAVES[2])
        Season.WINTER -> drawCircle(Color.White.copy(alpha = 0.85f), 2.2f * px, at)
        Season.SUMMER -> drawCircle(glow, 1.6f * px, at)
    }
}

/** The wind chime under the roof: a glass bell, its clapper, and a paper strip; the wind swings it harder in a gust. */
private fun DrawScope.windChime(x: Float, top: Float, h: Float, t: Float) {
    val px = density
    val gust = windGust(t)
    val swing = (sin(t * 0.8f) * 6f + sin(t * 1.9f) * 2f) * (0.4f + 0.9f * gust)
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
            leaf(Offset(x - 2.5f * px, stripTop + h * 0.04f), 8 * px, -60f, MOSS, MOSS_DEEP)
        }
    }
}

/** A bamboo blind under the beam, rolled up most of the way: the roll, a fringe of slats below it, two cords. */
private fun DrawScope.blind(left: Float, right: Float, top: Float, h: Float) {
    val px = density
    val drop = h * 0.055f
    drawRect(Brush.verticalGradient(listOf(BAMBOO, lerp(BAMBOO, WOOD_SHADE, 0.35f)), top, top + drop), Offset(left, top), Size(right - left, drop))
    var y = top + 2 * px
    while (y < top + drop) { drawLine(lerp(BAMBOO, WOOD_SHADE, 0.5f).copy(alpha = 0.6f), Offset(left, y), Offset(right, y), 0.8f * px); y += 3.2f * px }
    val roll = top + drop
    drawRoundRect(Brush.verticalGradient(listOf(lerp(BAMBOO, Color.White, 0.25f), BAMBOO, lerp(BAMBOO, WOOD_SHADE, 0.5f)), roll - 7 * px, roll + 7 * px),
        Offset(left, roll - 7 * px), Size(right - left, 14 * px), androidx.compose.ui.geometry.CornerRadius(7 * px))
    for (cx in listOf(left + (right - left) * 0.25f, left + (right - left) * 0.75f)) {
        drawLine(Color(0xFF8C3B2E), Offset(cx, top), Offset(cx, roll + 9 * px), 1.4f * px)
        drawCircle(Color(0xFF8C3B2E), 2.4f * px, Offset(cx, roll + 10 * px))
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

/** A fern in a clay pot: arching fronds of small leaflets, stirring with the wind. */
private fun DrawScope.fern(cx: Float, base: Float, s: Float, t: Float) {
    val px = density
    val gust = windGust(t)
    val potTop = base - s * 0.42f
    val pot = Path().apply {
        moveTo(cx - s * 0.32f, potTop); lineTo(cx + s * 0.32f, potTop); lineTo(cx + s * 0.24f, base); lineTo(cx - s * 0.24f, base); close()
    }
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(cx - s * 0.32f, base - s * 0.05f), Size(s * 0.64f, s * 0.12f))
    for (i in 0 until 9) {
        val side = if (i % 2 == 0) -1f else 1f
        val reach = s * (0.55f + 0.35f * ((i * 37) % 5) / 4f)
        val lean = side * (0.25f + 0.75f * (i / 8f))
        val sway = sin(t * 1.1f + i) * 6f * px * gust
        val tip = Offset(cx + lean * reach + sway, potTop - reach * (0.55f + 0.35f * (1 - abs(lean))))
        val ctrl = Offset(cx + lean * reach * 0.2f, potTop - reach * 1.0f)
        val frond = Path().apply { moveTo(cx, potTop); quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y) }
        drawPath(frond, MOSS_DEEP, style = Stroke(1.4f * px))
        for (j in 1..7) {
            val f = j / 8f
            val u = 1 - f
            val p = Offset(u * u * cx + 2 * u * f * ctrl.x + f * f * tip.x, u * u * potTop + 2 * u * f * ctrl.y + f * f * tip.y)
            val leafLen = s * 0.11f * (1 - f * 0.6f)
            leaf(p, leafLen, -60f + side * 20f, if (j % 2 == 0) MOSS else Color(0xFF86B85A), MOSS_DEEP)
            leaf(p, leafLen, -120f + side * 20f, MOSS, MOSS_DEEP)
        }
    }
    drawPath(pot, Brush.horizontalGradient(listOf(Color(0xFFB4683E), Color(0xFF8E4B2C), Color(0xFF6A3620)), cx - s * 0.32f, cx + s * 0.32f))
    drawRect(Color(0xFFC7794A), Offset(cx - s * 0.34f, potTop - s * 0.05f), Size(s * 0.68f, s * 0.07f))
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

private fun DrawScope.scene(t: Float, grains: List<Grain>, light: HourglassLight, art: ImageBitmap, sway: TreeSway, grain: ImageBitmap, tilt: Offset) {
    val w = size.width; val h = size.height; val px = density
    val sunCol = rgb(light.pal.sun)
    val night = light.pal.stars
    val dusk = (1f - light.sun).coerceIn(0f, 1f)
    val floorTop = h * 0.615f
    val gust = windGust(t)
    val rain = light.weather == Weather.RAIN
    val snow = light.weather == Weather.SNOW
    // a little parallax: the veranda shifts a few pixels with the phone's lean, the nearer hourglass a little more
    val near = Offset(tilt.x * 14 * px, -tilt.y * 8 * px)
    val mid = Offset(tilt.x * 6 * px, -tilt.y * 4 * px)

    // where the painting stands: scaled to the screen, its base resting just above the card. One perspective serves the
    // floor, the lake and the painting: the horizon is the far shore of the lake; the painted base is an ellipse 0.24 as
    // deep as wide (a view about 14 degrees from above), and with the base where it stands that gives the focal length.
    // Every circle on the floor (the rug, the shadows) flattens by sin(atan(distance below the horizon / focal length)).
    val sc = min(h * 0.48f / (Glass.ART_BOTTOM - Glass.ART_TOP), w * 0.92f / (Glass.ART_RIGHT - Glass.ART_LEFT))
    val ox = w * 0.5f - (Glass.ART_LEFT + Glass.ART_RIGHT) / 2 * sc + near.x
    val oy = h * 0.705f - Glass.ART_BOTTOM * sc + near.y
    val vx = w * 0.5f; val vy = h * HORIZON
    val focal = (oy + Glass.BASE_Y * sc - vy) / BASE_TAN
    fun flat(y: Float) = sin(atan((y - vy).coerceAtLeast(1f) / focal))

    // ---- a haze over the land and water outside, so the veranda and the hourglass stand forward
    drawRect(Brush.verticalGradient(listOf(rgb(light.pal.skyHor).copy(alpha = 0.10f), rgb(light.pal.skyHor).copy(alpha = 0.22f)), h * 0.3f, floorTop),
        Offset(0f, h * 0.3f), Size(w, floorTop - h * 0.3f))

    translate(mid.x, mid.y) {
        val ow = 40 * px        // drawn a little past the edges, so the parallax never shows a gap
        // ---- the veranda: floor, the sill at its edge, a post and the beam of the roof
        drawRect(Brush.verticalGradient(listOf(FLOOR_SHADE, FLOOR, FLOOR_LIT, FLOOR), floorTop, h), Offset(-ow, floorTop), Size(w + 2 * ow, h - floorTop + ow))
        for (i in -6..6) {
            val bx = w * 0.5f + i * w * 0.16f
            val k = (floorTop - vy) / (h - vy)
            drawLine(FLOOR_SHADE.copy(alpha = 0.45f), Offset(vx + (bx - vx) * k, floorTop), Offset(bx, h), 1.2f * px)
        }
        // the ends of the boards, closer together towards the horizon as they are further away
        for (n in 1..9) {
            val y = vy + (h - vy) * 0.95f / n
            if (y < floorTop + 4 * px) break
            for (j in 0 until 3) {
                val x0 = w * (((n * 0.37f + j * 0.33f) % 1f))
                drawLine(FLOOR_SHADE.copy(alpha = 0.32f), Offset(x0, y), Offset(x0 + w * 0.16f * (y - vy) / (h - vy) + 6 * px, y), 1 * px)
            }
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
        // in rain the boards nearest the open side are wet and give back the sky
        if (rain) {
            drawRect(Brush.verticalGradient(listOf(rgb(light.pal.skyHor).copy(alpha = 0.32f), Color.Transparent), floorTop, floorTop + h * 0.07f),
                Offset(-ow, floorTop), Size(w + 2 * ow, h * 0.07f))
            for (i in 0 until 12) {
                val x = w * (0.04f + 0.08f * i) + sin(i * 7f) * 6 * px
                drawLine(Color.White.copy(alpha = 0.10f), Offset(x, floorTop + 4 * px), Offset(x + 2 * px, floorTop + h * (0.02f + 0.02f * ((i * 37) % 3))), 2 * px)
            }
        }
        // what the season left on the floor: petals in spring, leaves in autumn
        if (light.season == Season.SPRING || light.season == Season.AUTUMN) {
            val lie = Random(57)
            repeat(7) { i ->
                val x = w * (0.2f + 0.75f * lie.nextFloat()); val y = h * (0.66f + 0.25f * lie.nextFloat())
                scale(1f, flat(y) * 2.2f, Offset(x, y)) { fallen(light.season, Offset(x, y), lie.nextFloat() * 360f, i, Color.Transparent) }
            }
        }
        // the sill, with snow along it when it snows
        drawRect(Brush.verticalGradient(listOf(WOOD_LIT, WOOD, WOOD_SHADE), floorTop - h * 0.022f, floorTop + h * 0.006f), Offset(-ow, floorTop - h * 0.022f), Size(w + 2 * ow, h * 0.028f))
        drawLine(Color.White.copy(alpha = 0.25f), Offset(-ow, floorTop - h * 0.021f), Offset(w + ow, floorTop - h * 0.021f), 1.2f * px)
        if (snow) {
            val drift = Path().apply {
                moveTo(-ow, floorTop - h * 0.021f)
                for (i in 0..24) { val x = -ow + (w + 2 * ow) * i / 24f; lineTo(x, floorTop - h * 0.021f - (4 + 3 * sin(i * 1.7f)) * px) }
                lineTo(w + ow, floorTop - h * 0.018f); lineTo(-ow, floorTop - h * 0.018f); close()
            }
            drawPath(drift, Color.White.copy(alpha = 0.92f))
        }
        // rain dripping from the edge of the roof, falling past the open side to the sill
        if (rain) for (i in 0 until 7) {
            val x = w * (0.14f + 0.13f * i) + ((i * 53) % 7) * px
            val cycle = 1.6f + 0.3f * (i % 3)
            val f = ((t + i * 0.37f) % cycle) / cycle
            val y = h * 0.06f + (floorTop - h * 0.08f) * f * f
            drawLine(Color(0xFFDDEBFF).copy(alpha = 0.55f), Offset(x, y), Offset(x, y + 9 * px), 1.4f * px, StrokeCap.Round)
            if (f > 0.92f) drawCircle(Color(0xFFDDEBFF).copy(alpha = 0.5f * (1 - f) * 12f), (4 + 20 * (f - 0.92f)) * px, Offset(x, floorTop - h * 0.022f), style = Stroke(1 * px))
        }
        // the post on the left, the beam above with a bamboo blind and the wind chime under it
        drawRect(Brush.horizontalGradient(listOf(WOOD_SHADE, WOOD, WOOD_LIT, WOOD), -ow, w * 0.07f), Offset(-ow, -ow), Size(w * 0.07f + ow, floorTop + ow))
        drawLine(WOOD_LINE.copy(alpha = 0.3f), Offset(w * 0.02f, h * 0.08f), Offset(w * 0.022f, floorTop), 1 * px)
        drawRect(Brush.verticalGradient(listOf(WOOD_SHADE, WOOD, WOOD_SHADE), 0f, h * 0.05f), Offset(-ow, -ow), Size(w + 2 * ow, h * 0.05f + ow))
        blind(w * 0.56f, w + ow, h * 0.05f, h)
        windChime(w * 0.22f, h * 0.05f, h, t)
        // paper grain over the drawn wood, so it sits nearer the painted hourglass
        val grainBrush = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
        drawRect(grainBrush, Offset(-ow, floorTop - h * 0.022f), Size(w + 2 * ow, h - floorTop + h * 0.022f + ow), alpha = 0.10f, blendMode = BlendMode.Overlay)
        drawRect(grainBrush, Offset(-ow, -ow), Size(w * 0.07f + ow, floorTop + ow), alpha = 0.12f, blendMode = BlendMode.Overlay)
        drawRect(grainBrush, Offset(-ow, -ow), Size(w + 2 * ow, h * 0.05f + ow), alpha = 0.12f, blendMode = BlendMode.Overlay)
    }

    // ---- the lantern, and a fern on the other side
    val lanternGlow = (0.25f + 0.75f * max(dusk, night)).coerceIn(0f, 1f)
    val flicker = 0.9f + 0.06f * sin(t * 7.3f) + 0.04f * sin(t * 13.1f + 1f)
    translate(mid.x, mid.y) {
        lantern(w * 0.03f, floorTop + h * 0.045f, w * 0.14f, h * 0.10f, lanternGlow * flicker)
        fern(w * 0.87f, floorTop + h * 0.07f, w * 0.21f, t)
    }

    // ---- the hourglass and its tree
    fun sy(yImg: Float) = oy + yImg * sc
    val cx = ox + Glass.CX * sc
    val cy = sy(Glass.NECK)
    val topFull = sy(Glass.TOP)
    val bottom = sy(Glass.BOTTOM)
    val y0 = sy(410f); val y1 = bottom
    val gh = y1 - y0
    val gw = 2 * Glass.inner(480f) * sc
    val neck = Glass.inner(Glass.NECK) * sc
    fun halfWidth(y: Float) = Glass.inner((y - oy) / sc) * sc
    val k = (t / HOURGLASS_SECONDS).coerceIn(0f, 1f)          // how much sand has run
    val gather = smooth(60f, 66f, t) * (1 - smooth(120f, 128f, t))
    val rise = smooth(120f, 130f, t)
    val spread = smooth(126f, 172f, t)
    val appear = smooth(0f, 2.5f, t)

    // a round woven rug under it, flattened exactly like the painted base, so both are seen from the same height
    val bx = ox + Glass.BASE_X * sc
    val rug = Offset(bx, sy(Glass.BASE_Y + 6f))
    val rugRx = 500f * sc; val rugRy = rugRx * flat(rug.y)
    fun oval(c: Offset, rx: Float, ry: Float) = Rect(c.x - rx, c.y - ry, c.x + rx, c.y + ry)
    drawOval(Color.Black.copy(alpha = 0.16f), oval(rug + Offset(0f, 2 * px), rugRx * 1.01f, rugRy * 1.06f).topLeft, oval(rug, rugRx * 1.01f, rugRy * 1.06f).size)
    // woven straw: a lit surface, then the coils of the weave, a darker binding at the edge
    val whole = oval(rug, rugRx, rugRy)
    drawOval(Brush.verticalGradient(listOf(Color(0xFFD8B97E), Color(0xFFB8935A)), whole.top, whole.bottom), whole.topLeft, whole.size)
    for (i in 1 until 14) {
        val f = 1f - i / 14f
        val o = oval(rug, rugRx * f, rugRy * f)
        drawOval(Color(0xFF8C6A3C).copy(alpha = 0.28f), o.topLeft, o.size, style = Stroke(1.2f * px))
    }
    drawOval(Color(0xFF6E4E2C), whole.topLeft, whole.size, style = Stroke(5f * px))
    drawOval(Color(0xFFE9D3A3).copy(alpha = 0.5f), oval(rug, rugRx * 0.985f, rugRy * 0.97f).topLeft, oval(rug, rugRx * 0.985f, rugRy * 0.97f).size, style = Stroke(1.2f * px))
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), whole.topLeft, whole.size, alpha = 0.08f, blendMode = BlendMode.Overlay)
    // shadows that hold it on the rug: a soft one a little away from the light, and a dark one where the base touches
    fun softOval(c: Offset, rx: Float, ry: Float, alpha: Float) {
        scale(1f, ry / rx, c) {
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = alpha), Color.Black.copy(alpha = alpha * 0.5f), Color.Transparent), c, rx), rx, c)
        }
    }
    softOval(Offset(bx - 45f * sc, sy(Glass.BASE_Y + 10f)), 420f * sc, 420f * sc * flat(sy(Glass.BASE_Y + 10f)), 0.24f)
    softOval(Offset(bx, sy(Glass.BASE_Y + 4f)), 350f * sc, 350f * sc * flat(sy(Glass.BASE_Y + 4f)), 0.75f)
    // the bowl of tea stands nearer than the rug, so it and its steam are drawn over it
    teaBowl(Offset(w * 0.10f + near.x, h * 0.71f + near.y), w * 0.075f, t)
    with(sway) { draw(ox, oy, sc, art.width * sc, art.height * sc, t, light.blend) }

    // ---- evening and night darken everything painted so far; the sky behind is not touched
    val dark = (0.62f * night + 0.18f * dusk * (1 - night)).coerceIn(0f, 0.7f)
    if (dark > 0.01f) drawRect(Color(0xFF141C3A).copy(alpha = dark), blendMode = BlendMode.SrcAtop)
    if (light.sun > 0.01f) drawRect(sunCol.copy(alpha = 0.10f * light.sun), blendMode = BlendMode.SrcAtop)

    // ---- the lantern lights what is near it: a warm pool on the floor, a warm edge on the tree and the base, flickering
    val lc = Offset(w * 0.10f + mid.x, floorTop - h * 0.005f + mid.y)
    val reach = w * 0.75f
    drawRect(Brush.radialGradient(listOf(LANTERN.copy(alpha = 0.55f * lanternGlow * flicker), LANTERN.copy(alpha = 0.18f * lanternGlow * flicker), Color.Transparent), lc, reach),
        Offset(0f, 0f), Size(w, h), blendMode = BlendMode.SrcAtop)
    scale(1f, flat(lc.y + h * 0.06f) * 2f, Offset(lc.x, lc.y + h * 0.05f)) {
        drawCircle(Brush.radialGradient(listOf(LANTERN.copy(alpha = 0.30f * lanternGlow * flicker), Color.Transparent), Offset(lc.x, lc.y + h * 0.05f), w * 0.3f),
            w * 0.3f, Offset(lc.x, lc.y + h * 0.05f))
    }
    drawCircle(Brush.radialGradient(listOf(LANTERN.copy(alpha = 0.35f * lanternGlow * flicker), Color.Transparent), lc, w * 0.18f), w * 0.18f, lc)

    // ---- by night the sand is a small light of its own: it warms the glass and the wood around it
    val floorY = sy(Glass.BOTTOM)
    val lowerHeight = (floorY - cy) * 0.62f * k.pow(0.8f)
    if (night > 0.05f) {
        val sandAt = Offset(cx, floorY - lowerHeight * 0.4f)
        val r = gw * (0.9f + 0.6f * rise)
        drawRect(Brush.radialGradient(listOf(SAND.copy(alpha = (0.30f + 0.25f * rise) * night), Color.Transparent), sandAt, r),
            Offset(sandAt.x - r, sandAt.y - r), Size(2 * r, 2 * r), blendMode = BlendMode.SrcAtop)
    }

    // a shaft of light from the open side by day, with dust shining in it
    if (light.sun > 0.05f) {
        val shaft = Path().apply { moveTo(w * 0.95f, 0f); lineTo(w * 1.1f, 0f); lineTo(w * 0.45f, h); lineTo(w * 0.05f, h); close() }
        drawPath(shaft, Brush.linearGradient(listOf(sunCol.copy(alpha = 0.16f * light.sun), Color.Transparent), Offset(w, 0f), Offset(w * 0.3f, h)))
        val dust = Random(23)
        repeat(26) {
            val f = dust.nextFloat(); val sp = 0.3f + 0.5f * dust.nextFloat()
            val y = h * ((dust.nextFloat() + t * 0.01f * sp) % 1f)
            val x = w * (1.0f - 0.55f * y / h) - w * 0.15f * f + sin(t * sp + f * 9f) * 6 * px
            drawCircle(Color.White.copy(alpha = 0.5f * light.sun * (0.5f + 0.5f * sin(t * 2 * sp + f * 20f))), 1.2f * px, Offset(x, y))
        }
    }

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
    // the inside of the glass: its walls row by row, and the floor of the lower bulb, an ellipse seen a little from above
    val interior = Path().apply {
        var yi = 415f
        moveTo(cx - Glass.inner(yi) * sc, sy(yi))
        while (yi < Glass.BOTTOM) { yi = min(yi + 6f, Glass.BOTTOM); lineTo(cx - Glass.inner(yi) * sc, sy(yi)) }
        val fr = Glass.inner(Glass.BOTTOM) * sc
        for (i in 0..24) { val a = PI.toFloat() * i / 24; lineTo(cx - fr * cos(a), floorY + Glass.FLOOR_RY * sc * sin(a)) }
        while (yi > 415f) { yi = max(yi - 6f, 415f); lineTo(cx + Glass.inner(yi) * sc, sy(yi)) }
        close()
    }
    val wide = 420f * sc
    clipPath(interior) {
        // the upper sand: its surface sinking, with a small hollow where it runs out
        if (k < 0.995f) {
            val level = cy - (cy - topFull) * (1 - k)
            val dip = 7 * px * (1 - k)
            val hw = halfWidth(level)
            val heap = Path()
            heap.moveTo(cx - wide, level)
            for (i in 0..24) { val x = cx - hw + 2 * hw * i / 24f; val u = (x - cx) / hw; heap.lineTo(x, level + dip * (1 - u * u)) }
            heap.lineTo(cx + wide, level); heap.lineTo(cx + wide, cy + 2 * px); heap.lineTo(cx - wide, cy + 2 * px); heap.close()
            sandFill(heap, level, cy, 0.6f)
        }
        // the lower sand: from the floor of the bulb up to a soft peak
        if (k > 0.005f) {
            val top = floorY - lowerHeight
            val hw = halfWidth(floorY - lowerHeight * 0.3f)
            val heap = Path()
            heap.moveTo(cx - wide, top + lowerHeight * 0.75f)
            for (i in 0..32) {
                val x = cx - hw + 2 * hw * i / 32f
                val u = ((x - cx) / hw).coerceIn(-1f, 1f)
                heap.lineTo(x, top + lowerHeight * 0.7f * u * u)
            }
            heap.lineTo(cx + wide, top + lowerHeight * 0.75f); heap.lineTo(cx + wide, floorY + 40 * sc); heap.lineTo(cx - wide, floorY + 40 * sc); heap.close()
            sandFill(heap, top, floorY + Glass.FLOOR_RY * sc, 0.6f + 1.2f * rise)
        }
    }
    if (k < 0.995f) {
        val bottomTop = floorY - lowerHeight
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

    // ---- what the wind carries: the season's petals, leaves or snow (more of them in a gust, mixed near a change of
    // season), and the motes of the three minutes
    val fall = Random(17)
    val glowCol = sunCol.copy(alpha = 0.5f * (0.3f + light.sun))
    repeat(12) { i ->
        val sp = 0.6f + 0.6f * fall.nextFloat()
        val ph = fall.nextFloat()
        if (i >= 5 + (7 * gust).toInt()) return@repeat
        val f = (t * 0.035f * sp * (0.7f + 0.6f * gust) + ph) % 1f
        val x = w * (0.98f - 0.9f * f) + sin(t * 0.9f * sp + i) * w * 0.04f * (0.6f + gust)
        val y = h * (0.12f + 0.55f * f) + sin(t * 1.3f + i) * 6 * px
        val season = if (i < (12 * light.blend).toInt()) light.other else light.season
        fallen(season, Offset(x, y), t * 60f * sp + i * 40f, i, glowCol)
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
