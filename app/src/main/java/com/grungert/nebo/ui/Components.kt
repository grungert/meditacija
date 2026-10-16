package com.grungert.nebo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
 * The look: a calm, modern app over a living sky. Deep night-blue surfaces without outlines,
 * white type in Manrope, one warm paper colour for the main action and for anything chosen,
 * and Cormorant only where words are meant to be read slowly (koans and your own words).
 */
object Ink {
    val text = Color.White
    val soft = Color(0xC7FFFFFF)
    val faint = Color(0x80FFFFFF)
    val surface = Color(0xB3101C3A)          // cards and sheets
    val surfaceHigh = Color(0xD9142347)      // controls on cards
    val hairline = Color(0x1FFFFFFF)
    val shade = Color(0x6610204A)
    val accent = Color(0xFFFFD9A0)           // warm light, used sparingly
    val paper = Color(0xFFF8F1E3)
    val paperDeep = Color(0xFFEFE3CC)
    val inkOnPaper = Color(0xFF1F2A44)
    val seal = Color(0xFFA2463A)
    val night = Color(0xFF0E1A36)
    // names older call sites still use
    val glass = surfaceHigh
    val glassStrong = paper
    val line = hairline
    val panel = surface
}

private fun variable(res: Int, weight: Int, style: FontStyle = FontStyle.Normal) =
    Font(res, FontWeight(weight), style, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Manrope: clean and modern, for everything read at a glance or operated. */
val UiFont = FontFamily(
    variable(R.font.manrope, 300), variable(R.font.manrope, 400), variable(R.font.manrope, 500),
    variable(R.font.manrope, 600), variable(R.font.manrope, 700), variable(R.font.manrope, 800),
)
val Display = UiFont
/** Cormorant Garamond, with lining figures (its default 0 and 1 read as o and i): for koans and words read slowly. */
val Serif = FontFamily(
    variable(R.font.cormorant, 400), variable(R.font.cormorant, 500), variable(R.font.cormorant, 600),
    variable(R.font.cormorant_italic, 400, FontStyle.Italic), variable(R.font.cormorant_italic, 500, FontStyle.Italic),
)

val shadow = Shadow(Ink.shade, blurRadius = 16f)

val TitleStyle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp, color = Ink.text, shadow = shadow)
val HeadStyle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp, color = Ink.text, shadow = shadow)
val BodyStyle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, color = Ink.text, shadow = shadow)
val SmallStyle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = Ink.soft, shadow = shadow)
val LabelStyle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp, color = Ink.soft, shadow = shadow)
/** Large figures in the overview. */
val NumberStyle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp, color = Ink.text)

/** Primary actions are warm paper; secondary ones are quiet night-blue. */
@Composable
fun GlassButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false, enabled: Boolean = true,
    icon: NeboIcon? = null, sub: String? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val ink = when { !enabled -> if (strong) Ink.inkOnPaper.copy(alpha = 0.4f) else Ink.faint; strong -> Ink.inkOnPaper; else -> Ink.text }
    Row(
        modifier.clip(shape).background(if (strong) Ink.paper else Ink.surfaceHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (sub != null) 11.dp else 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, ink, 18.dp); Spacer(Modifier.width(10.dp)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = BodyStyle.copy(color = ink, fontWeight = FontWeight.Bold, shadow = null), textAlign = TextAlign.Center)
            if (sub != null) Text(sub, style = SmallStyle.copy(color = ink.copy(alpha = 0.7f), fontSize = 12.sp, shadow = null))
        }
    }
}

/** A round icon with a label underneath. */
@Composable
fun IconButton(icon: NeboIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, circle: Dp = 56.dp) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(circle).clip(CircleShape).background(if (selected) Ink.paper else Ink.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, if (selected) Ink.inkOnPaper else Ink.text, 24.dp)
        }
        Text(label, style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink.text), maxLines = 1, softWrap = false)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, sub: String? = null, dot: Color? = null) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.clip(shape).background(if (selected) Ink.paper else Ink.surfaceHigh)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val ink = if (selected) Ink.inkOnPaper else Ink.text
            Text(text, style = BodyStyle.copy(fontSize = 14.sp, color = ink, fontWeight = FontWeight.SemiBold, shadow = null))
            if (sub != null) Text(sub, style = SmallStyle.copy(fontSize = 11.sp, color = ink.copy(alpha = 0.65f), shadow = null))
        }
    }
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** A row of equal segments, one chosen: for short exclusive choices. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.surfaceHigh).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) Ink.paper else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = SmallStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Ink.inkOnPaper else Ink.text, shadow = null), maxLines = 1)
            }
        }
    }
}

/** A card on the night-blue surface. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink.surface).padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Warm paper, for reading: koans, messages and your own words given back to you. */
@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(Ink.paper, Ink.paperDeep))).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

val PaperText = TextStyle(fontFamily = Serif, fontFeatureSettings = "lnum", fontSize = 22.sp, lineHeight = 31.sp, color = Ink.inkOnPaper)
val PaperSmall = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 19.sp, color = Ink.inkOnPaper.copy(alpha = 0.65f))

@Composable
fun SectionLabel(text: String) = Text(text.uppercase(), style = LabelStyle)

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = BodyStyle.copy(shadow = null))
            if (sub != null) Text(sub, style = SmallStyle.copy(shadow = null))
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = Ink.accent, checkedThumbColor = Ink.night, uncheckedTrackColor = Color(0x33FFFFFF),
            uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
        ))
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Ink.surfaceHigh)
            .clickable(role = Role.Button, onClickLabel = "Nazad", onClick = onClick),
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

/** Darkens the sky behind reading-heavy screens, more towards the bottom where the meadow is busy. */
@Composable
fun Scrim(modifier: Modifier = Modifier) =
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0x8C081830), Color(0x99081830), Color(0xCC081830)))))

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

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xEB0C1730))
            .border(1.dp, Ink.hairline, RoundedCornerShape(28.dp)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (t in Tab.entries) {
            val on = t == current
            val k by animateFloatAsState(if (on) 1f else 0f, label = "tab")
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).clickable(role = Role.Tab, onClickLabel = t.label) { onSelect(t) }.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Ink.paper.copy(alpha = 0.16f * k)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Icon(t.icon, if (on) Ink.accent else Ink.soft, 22.dp, Modifier.scale(1f + 0.06f * k))
                }
                Spacer(Modifier.height(2.dp))
                Text(t.label, style = SmallStyle.copy(fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Ink.text else Ink.soft, shadow = null))
            }
        }
    }
}

/** Mood colours: warm for light moods, cool for heavy ones; only ever a small dot or a thin stripe. */
val MOOD_COLORS = mapOf(
    "mirno" to Color(0xFF9FD3C7), "nemirno" to Color(0xFFF2B36B), "umorno" to Color(0xFF9AA6C9), "tužno" to Color(0xFF7FA3D9),
    "ljuto" to Color(0xFFE07A62), "zabrinuto" to Color(0xFFC59ADB), "zbunjeno" to Color(0xFFB8B8B8), "zahvalno" to Color(0xFFFFD98A),
    "radosno" to Color(0xFFFFC2A8), "radoznalo" to Color(0xFFA8E0A0),
)

/** Our plural: 1, 21, 31… take the first form; 2–4, 22–24… the second; everything else the third. */
fun plural(n: Int, one: String, few: String, many: String): String = when {
    n % 10 == 1 && n % 100 != 11 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}
