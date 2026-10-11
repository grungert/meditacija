package com.grungert.nebo.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The look: white ink with a cool shadow over the living sky, frosted night-blue glass for panels,
 * and one warm paper colour for the things that matter most (the main action, a chosen chip, the koan).
 */
object Ink {
    val text = Color.White
    val soft = Color(0xD9FFFFFF)
    val faint = Color(0x8CFFFFFF)
    val glass = Color(0x26FFFFFF)
    val glassStrong = Color(0x4DFFFFFF)
    val line = Color(0x59FFFFFF)
    val shade = Color(0x8010204A)
    val accent = Color(0xFFFFE3B0)          // warm light, used sparingly
    val paper = Color(0xFFF8F1E3)
    val paperDeep = Color(0xFFEFE3CC)
    val inkOnPaper = Color(0xFF26304A)
    val seal = Color(0xFFA2463A)
    val night = Color(0xFF14254A)
}

private fun variable(res: Int, weight: Int, style: FontStyle = FontStyle.Normal) =
    Font(res, FontWeight(weight), style, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Cormorant Garamond: the voice of the sky (titles, the breath word, koans). Styles ask for lining figures (lnum): its default old-style 0 and 1 read as o and i. */
val Display = FontFamily(
    variable(R.font.cormorant, 400), variable(R.font.cormorant, 500), variable(R.font.cormorant, 600),
    variable(R.font.cormorant_italic, 400, FontStyle.Italic), variable(R.font.cormorant_italic, 500, FontStyle.Italic),
)
/** Nunito: rounded and friendly for everything you operate. */
val UiFont = FontFamily(variable(R.font.nunito, 400), variable(R.font.nunito, 600), variable(R.font.nunito, 700))
val Serif = Display

val shadow = Shadow(Ink.shade, blurRadius = 20f)

val TitleStyle = TextStyle(fontFamily = Display, fontFeatureSettings = "lnum", fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 44.sp, color = Ink.text, shadow = shadow)
val HeadStyle = TextStyle(fontFamily = Display, fontFeatureSettings = "lnum", fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, color = Ink.text, shadow = shadow)
val BodyStyle = TextStyle(fontFamily = UiFont, fontSize = 16.sp, lineHeight = 24.sp, color = Ink.text, shadow = shadow)
val SmallStyle = TextStyle(fontFamily = UiFont, fontSize = 13.sp, lineHeight = 19.sp, color = Ink.soft, shadow = shadow)
val LabelStyle = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp, color = Ink.soft, shadow = shadow)

private val glassFill = Brush.verticalGradient(listOf(Color(0x8C1B305E), Color(0xB30C1A38)))
private val glassEdge = Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x33FFFFFF)))
// controls sit on dark night-blue glass, so white text stays readable over a bright cloud or the sun itself
private val pillFill = Brush.verticalGradient(listOf(Color(0x8C1E3466), Color(0xB3122347)))

@Composable
fun GlassButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false, enabled: Boolean = true,
    icon: NeboIcon? = null, sub: String? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    val bg = if (strong) Modifier.background(Ink.paper, shape) else Modifier.background(pillFill, shape).border(BorderStroke(1.dp, glassEdge), shape)
    val ink = when { !enabled -> if (strong) Ink.inkOnPaper.copy(alpha = 0.4f) else Ink.faint; strong -> Ink.inkOnPaper; else -> Ink.text }
    Row(
        modifier.clip(shape).then(bg).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = if (sub != null) 10.dp else 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, ink, 18.dp); Spacer(Modifier.width(10.dp)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val st = BodyStyle.copy(color = ink, fontWeight = FontWeight.Bold, shadow = if (strong) null else shadow)
            Text(text, style = st, textAlign = TextAlign.Center)
            if (sub != null) Text(sub, style = SmallStyle.copy(color = ink.copy(alpha = 0.75f), fontSize = 12.sp, shadow = if (strong) null else shadow))
        }
    }
}

@Composable
fun IconButton(icon: NeboIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(pillFill).border(BorderStroke(1.dp, glassEdge), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, Ink.text, 22.dp)
        }
        Text(label, style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, sub: String? = null, dot: Color? = null) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.clip(shape)
            .then(if (selected) Modifier.background(Ink.paper, shape) else Modifier.background(pillFill, shape).border(BorderStroke(1.dp, glassEdge), shape))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val ink = if (selected) Ink.inkOnPaper else Ink.text
            Text(text, style = BodyStyle.copy(fontSize = 15.sp, color = ink, fontWeight = FontWeight.SemiBold, shadow = if (selected) null else shadow))
            if (sub != null) Text(sub, style = SmallStyle.copy(fontSize = 11.sp, color = ink.copy(alpha = 0.7f), shadow = if (selected) null else shadow))
        }
    }
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(glassFill).border(BorderStroke(1.dp, glassEdge), shape).padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Warm paper, for reading: koans, messages and your own words given back to you. */
@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(6.dp, 26.dp, 6.dp, 26.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(Ink.paper, Ink.paperDeep))).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

val PaperText = TextStyle(fontFamily = Display, fontFeatureSettings = "lnum", fontSize = 22.sp, lineHeight = 31.sp, color = Ink.inkOnPaper)
val PaperSmall = TextStyle(fontFamily = UiFont, fontSize = 13.sp, lineHeight = 19.sp, color = Ink.inkOnPaper.copy(alpha = 0.7f))

@Composable
fun SectionLabel(text: String) = Text(text.uppercase(), style = LabelStyle)

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = BodyStyle)
            if (sub != null) Text(sub, style = SmallStyle)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = Ink.accent, checkedThumbColor = Ink.night, uncheckedTrackColor = Color(0x33FFFFFF),
            uncheckedThumbColor = Color.White, uncheckedBorderColor = Color(0x66FFFFFF),
        ))
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(pillFill).border(BorderStroke(1.dp, glassEdge), CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Nazad", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(NeboIcon.Back, Ink.text, 20.dp) }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BackButton(onBack)
        Text(title, style = HeadStyle, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Darkens the sky a little behind reading-heavy screens, more towards the bottom where the meadow is busy. */
@Composable
fun Scrim(modifier: Modifier = Modifier) =
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0x33081830), Color(0x59081830), Color(0x8C081830)))))

fun Modifier.screenPadding() = this.padding(horizontal = 20.dp)

/** Mood colours: warm for light moods, cool for heavy ones; only ever a small dot. */
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
