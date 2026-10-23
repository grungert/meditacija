package com.grungert.nebo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.R

/**
 * The look: a calm, modern app over a living sky. By night, deep night-blue glass and white type; by day, light glass
 * and night-blue ink, so the screens follow the sky behind them. One warm sand colour marks what is chosen or what to
 * press now; Newsreader carries your own words and the koans, Manrope everything else.
 *
 * [day] is set by the app from the sky's light, for the screens that read in daylight (the tabs and what opens from
 * them); sessions and check-ins always keep the night look. Every colour below is read through it, so a screen
 * recomposes in the new light when the sun comes up or goes down.
 */
object Ink {
    var day by mutableStateOf(false)

    private fun pick(day: Color, night: Color) = if (this.day) day else night

    val text get() = pick(Color(0xFF16233F), Color.White)
    val soft get() = pick(Color(0xFF46557A), Color(0xC7FFFFFF))
    val faint get() = pick(Color(0xFF7A86A3), Color(0x80FFFFFF))
    val surface get() = pick(Color(0xA8FFFFFF), Color(0xB3101C3A))          // cards and sheets: glass
    val surfaceHigh get() = pick(Color(0xC7FFFFFF), Color(0xD9142347))      // controls on cards
    val hairline get() = pick(Color(0x1A16233F), Color(0x1FFFFFFF))
    val shade get() = pick(Color.Transparent, Color(0x6610204A))
    /** A sheet that slides up over a screen. */
    val sheet get() = pick(Color(0xFFFAF7F0), Color(0xFF0C1630))
    /** A card that holds your words. */
    val card get() = pick(Color.White, Color(0x0FFFFFFF))
    /** The bottom bar and other floating glass. */
    val bar get() = pick(Color(0xA8FFFFFF), Color(0xB8101C3C))
    /** Warm sand: what is chosen, and the one thing to press now. */
    val accent = Color(0xFFFFD9A0)
    /** Sand as type or a small icon: on light glass it needs the depth of gold to be read. */
    val accentInk get() = pick(Color(0xFF7A4E16), accent)
    /** Type that sits right on the sky, with nothing behind it. */
    val onSky = Color.White
    val paper = Color(0xFFF8F1E3)
    val paperDeep = Color(0xFFEFE3CC)
    val inkOnPaper = Color(0xFF1F2A44)
    val ink = Color(0xFF16233F)
    val seal = Color(0xFFA2463A)
    val night = Color(0xFF0E1A36)
    /** Saved as an insight: a soft green bookmark. */
    val insight get() = pick(Color(0xFF5FAF97), Color(0xFFA6DFCD))
    val insightBg get() = pick(Color(0x388ED6C0), Color(0x248ED6C0))
    // names older call sites still use
    val glass get() = surfaceHigh
    val glassStrong = paper
    val line get() = hairline
    val panel get() = surface
}

private fun variable(res: Int, weight: Int, style: FontStyle = FontStyle.Normal) =
    Font(res, FontWeight(weight), style, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Manrope: clean and modern, for everything read at a glance or operated. */
val UiFont = FontFamily(
    variable(R.font.manrope, 300), variable(R.font.manrope, 400), variable(R.font.manrope, 500),
    variable(R.font.manrope, 600), variable(R.font.manrope, 700), variable(R.font.manrope, 800),
)
val Display = UiFont
/** Newsreader (optical size 18, Latin only): warm and easy to read, for koans and your own words. */
val Serif = FontFamily(
    variable(R.font.newsreader, 400), variable(R.font.newsreader, 500), variable(R.font.newsreader, 600),
    variable(R.font.newsreader_italic, 400, FontStyle.Italic), variable(R.font.newsreader_italic, 500, FontStyle.Italic),
)

val shadow get() = if (Ink.day) null else Shadow(Ink.shade, blurRadius = 16f)
/** For type right on the sky: a soft dark halo, by day too. */
val skyShadow = Shadow(Color(0x6610204A), blurRadius = 16f)

val TitleStyle get() = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp, color = Ink.text, shadow = shadow)
val HeadStyle get() = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp, color = Ink.text, shadow = shadow)
val BodyStyle get() = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, color = Ink.text, shadow = shadow)
val SmallStyle get() = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = Ink.soft, shadow = shadow)
val LabelStyle get() = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp, color = Ink.soft, shadow = shadow)
/** A small label in gold capitals over a card: "ZA SADA", "OVA SEDMICA". */
val KickerStyle get() = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, letterSpacing = 1.6.sp, color = Ink.accentInk)
/** Large figures in the overview. */
val NumberStyle get() = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1).sp, color = Ink.text)
/** Your words and findings, in the reading face. */
val ReadStyle get() = TextStyle(fontFamily = Serif, fontSize = 19.sp, lineHeight = 27.sp, color = if (Ink.day) Color(0xFF1F2A44) else Color(0xFFF3EEE4))

/**
 * A gentle give under the finger: whatever this is put on shrinks a little while pressed and springs back.
 * Put it first in the chain, before the clip and background, so the whole thing moves; pass the same [source] to clickable.
 */
fun Modifier.pressScale(source: MutableInteractionSource, to: Float = 0.96f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed) to else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "press")
    graphicsLayer { scaleX = k; scaleY = k }
}

/** Pressable in one go: the give, the ripple, a light tick under the finger, and the click. Put it first in the chain. */
fun Modifier.tap(label: String? = null, role: Role? = Role.Button, enabled: Boolean = true, shape: Shape? = null, haptic: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val h = LocalHapticFeedback.current
    pressScale(src).let { if (shape != null) it.clip(shape) else it }
        .clickable(src, LocalIndication.current, enabled = enabled, onClickLabel = label, role = role) {
            if (haptic) h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** A card's lift: by day a soft shadow sets the light glass off the light sky; by night the glass alone does it. */
fun Modifier.lift(shape: Shape, elevation: Dp = 10.dp): Modifier = composed {
    if (Ink.day) shadow(elevation, shape, clip = false, ambientColor = Color(0x3316233F), spotColor = Color(0x2216233F)) else this
}

/** Primary actions are warm sand; secondary ones are quiet glass. */
@Composable
fun GlassButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false, enabled: Boolean = true,
    icon: NeboIcon? = null, sub: String? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val ink = when { !enabled -> if (strong) Ink.ink.copy(alpha = 0.4f) else Ink.faint; strong -> Ink.ink; else -> Ink.text }
    Row(
        modifier.tap(enabled = enabled, shape = shape, onClick = onClick).background(if (strong) Ink.accent else Ink.surfaceHigh, shape)
            .padding(horizontal = 20.dp, vertical = if (sub != null) 11.dp else 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, ink, 19.dp, filled = strong); Spacer(Modifier.width(10.dp)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = BodyStyle.copy(color = ink, fontWeight = FontWeight.Bold, shadow = null), textAlign = TextAlign.Center)
            if (sub != null) Text(sub, style = SmallStyle.copy(color = ink.copy(alpha = 0.7f), fontSize = 12.sp, shadow = null))
        }
    }
}

/** A round icon with a label underneath. */
@Composable
fun IconButton(icon: NeboIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, circle: Dp = 56.dp) {
    val bg by animateColorAsState(if (selected) Ink.accent else Ink.surfaceHigh, label = "icon-bg")
    Column(
        modifier.tap(label, shape = RoundedCornerShape(18.dp), onClick = onClick).padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(circle).clip(RoundedCornerShape(circle * 0.36f)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, if (selected) Ink.ink else Ink.text, 24.dp, filled = selected)
        }
        Text(label, style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink.text), maxLines = 1, softWrap = false)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, sub: String? = null, dot: Color? = null) {
    val shape = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (selected) Ink.accent else Ink.surfaceHigh, label = "chip")
    Row(
        Modifier.tap(role = Role.RadioButton, shape = shape, onClick = onClick).background(bg, shape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val ink = if (selected) Ink.ink else Ink.text
            Text(text, style = BodyStyle.copy(fontSize = 14.sp, color = ink, fontWeight = FontWeight.SemiBold, shadow = null))
            if (sub != null) Text(sub, style = SmallStyle.copy(fontSize = 11.sp, color = ink.copy(alpha = 0.65f), shadow = null))
        }
    }
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** A row of equal segments, one chosen in sand: for short exclusive choices. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.surfaceHigh).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Ink.accent else Color.Transparent, label = "segment")
            Box(
                Modifier.weight(1f).tap(role = Role.Tab, shape = RoundedCornerShape(16.dp)) { onSelect(i) }.background(bg, RoundedCornerShape(16.dp)).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = SmallStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Ink.ink else Ink.text, shadow = null), maxLines = 1)
            }
        }
    }
}

/** Tabs as pills that size to their words, the chosen one in sand: for a few views of one screen. */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Ink.accent else Color.Transparent, label = "pill")
            Text(o, style = SmallStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Ink.ink else Ink.soft, shadow = null),
                modifier = Modifier.tap(role = Role.Tab, shape = RoundedCornerShape(18.dp)) { onSelect(i) }.background(bg, RoundedCornerShape(18.dp))
                    .padding(horizontal = 15.dp, vertical = 8.dp))
        }
    }
}

/** A card of glass. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxWidth().lift(shape).clip(shape).background(Ink.surface)
            .then(if (Ink.day) Modifier.border(1.dp, Color(0xCCFFFFFF), shape) else Modifier).padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Warm paper, for reading: koans, messages and your own words given back to you. */
@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxWidth().lift(shape).clip(shape).background(Brush.verticalGradient(listOf(Ink.paper, Ink.paperDeep))).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

val PaperText get() = TextStyle(fontFamily = Serif, fontSize = 21.sp, lineHeight = 30.sp, color = Ink.inkOnPaper)
val PaperSmall get() = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 19.sp, color = Ink.inkOnPaper.copy(alpha = 0.65f))

@Composable
fun SectionLabel(text: String) = Text(text.uppercase(), style = KickerStyle.copy(color = if (Ink.day) Ink.accentInk else Ink.soft))

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = BodyStyle.copy(shadow = null))
            if (sub != null) Text(sub, style = SmallStyle.copy(shadow = null))
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = Ink.accent, checkedThumbColor = Ink.ink, uncheckedTrackColor = if (Ink.day) Color(0x2216233F) else Color(0x33FFFFFF),
            uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
        ))
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).tap("Nazad", shape = CircleShape, onClick = onClick).background(Ink.surfaceHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(NeboIcon.Back, Ink.text, 20.dp) }
}

/** A screen title: large and left-aligned, with an optional back button and a trailing action. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (onBack != null) BackButton(onBack)
        Text(title, style = TitleStyle.copy(fontSize = if (onBack != null) 26.sp else 32.sp), modifier = Modifier.weight(1f))
        trailing()
    }
}

/**
 * Settles the sky behind reading-heavy screens: by night it darkens, more towards the bottom where the meadow is busy;
 * by day it fades the sky into a soft cream, so ink reads on it.
 */
@Composable
fun Scrim(modifier: Modifier = Modifier) =
    Box(modifier.background(
        if (Ink.day) Brush.verticalGradient(listOf(Color(0x4DF4F1EA), Color(0xB3F4F1EA), Color(0xEBF7F3EC)))
        else Brush.verticalGradient(listOf(Color(0x8C081830), Color(0x99081830), Color(0xCC081830)))
    ))

fun Modifier.screenPadding() = this.padding(horizontal = 20.dp)

/** A big figure with its label, for the top of the overview. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Ink.surface).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(value, style = NumberStyle)
        Text(label, style = SmallStyle.copy(shadow = null))
    }
}

/** The four main places of the app. */
enum class Tab(val label: String, val icon: NeboIcon) {
    TODAY("Danas", NeboIcon.Sun), JOURNAL("Dnevnik", NeboIcon.Journal), OVERVIEW("Pregled", NeboIcon.Stats), SETTINGS("Postavke", NeboIcon.Settings),
}

/**
 * The floating bar: a pill of glass with the four places, the chosen one in sand with its name, and beside it the
 * microphone, always one tap from a note. The chosen pill grows and shrinks between places with a soft spring.
 */
@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit, onMic: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val pill = RoundedCornerShape(32.dp)
        Row(
            Modifier.weight(1f).height(64.dp).shadow(14.dp, pill, ambientColor = Color(0x3308122A), spotColor = Color(0x3808122A)).clip(pill).background(Ink.bar)
                .border(1.dp, if (Ink.day) Color(0xCCFFFFFF) else Color(0x1FFFFFFF), pill).padding(7.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            for (t in Tab.entries) {
                val on = t == current
                val bg by animateColorAsState(if (on) Ink.accent else Color.Transparent, spring(stiffness = Spring.StiffnessMediumLow), label = "tab")
                Row(
                    Modifier.height(50.dp).tap(t.label, Role.Tab, shape = RoundedCornerShape(25.dp)) { onSelect(t) }.background(bg, RoundedCornerShape(25.dp))
                        .padding(horizontal = if (on) 16.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(t.icon, if (on) Ink.ink else Ink.text.copy(alpha = 0.85f), 22.dp, filled = on)
                    AnimatedVisibility(on, enter = fadeIn() + expandHorizontally(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut() + shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium))) {
                        Text(t.label, style = SmallStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ink.ink, shadow = null),
                            maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
        }
        MicButton(onMic)
    }
}

/** The microphone: a sand circle with a slow halo, the one way to a note from any of the four places. */
@Composable
fun MicButton(onClick: () -> Unit, size: Dp = 64.dp) {
    val pulse = com.grungert.nebo.sky.slowPulse(3600)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            drawCircle(Ink.accent.copy(alpha = 0.22f * pulse), r * (1f + 0.08f * pulse))
        }
        Box(
            Modifier.size(size - 4.dp).tap("Zapiši kako si", shape = CircleShape, onClick = onClick)
                .shadow(14.dp, CircleShape, ambientColor = Color(0x4008122A), spotColor = Color(0x4008122A)).background(Ink.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(NeboIcon.Mic, Ink.ink, 26.dp, filled = true) }
    }
}

/** Mood colours, one family with the four points: cool for heavy moods, warm for light ones; a dot or a thin stripe. */
val MOOD_COLORS = mapOf(
    "mirno" to Color(0xFF8ED6C0), "nemirno" to Color(0xFFF2B36B), "umorno" to Color(0xFFA3ADCB), "tužno" to Color(0xFF8FB4F0),
    "ljuto" to Color(0xFFEC8B74), "zabrinuto" to Color(0xFFB9AEF2), "zbunjeno" to Color(0xFFBFC4D4), "zahvalno" to Color(0xFFFFD9A0),
    "radosno" to Color(0xFFFFC2A8), "radoznalo" to Color(0xFFA8E0A0),
)

/** Our plural: 1, 21, 31… take the first form; 2–4, 22–24… the second; everything else the third. */
fun plural(n: Int, one: String, few: String, many: String): String = when {
    n % 10 == 1 && n % 100 != 11 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

/**
 * What a card says before it has anything to show: a faint icon in a dashed ring, what will appear here and what
 * brings it, and the one step that does, when there is one. Never an empty box, never a blame.
 */
@Composable
fun EmptyState(icon: NeboIcon, text: String, action: String? = null, onAction: (() -> Unit)? = null, progress: Pair<Int, Int>? = null) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Ink.text.copy(alpha = 0.04f))
            .border(1.dp, Ink.text.copy(alpha = 0.12f), shape).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val pulse = com.grungert.nebo.sky.slowPulse(4000)
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(52.dp)) {
                drawCircle(Ink.accent.copy(alpha = 0.10f + 0.08f * pulse), size.minDimension / 2)
                drawCircle(Ink.text.copy(alpha = 0.25f), size.minDimension / 2 - 1.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 6f))))
            }
            Icon(icon, Ink.accentInk, 24.dp)
        }
        Text(text, style = SmallStyle.copy(fontSize = 13.5.sp, lineHeight = 19.sp, color = Ink.text, shadow = null), textAlign = TextAlign.Center)
        // how far there is to go, as dots: three of five
        progress?.let { (have, need) ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(need) { i -> Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < have) Ink.accent else Ink.text.copy(alpha = 0.15f))) }
                Text("  $have od $need", style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = null))
            }
        }
        if (action != null && onAction != null) Text(action, style = SmallStyle.copy(fontWeight = FontWeight.ExtraBold, color = Ink.ink, shadow = null),
            modifier = Modifier.tap(action, shape = RoundedCornerShape(14.dp), onClick = onAction).background(Ink.accent, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp))
    }
}
