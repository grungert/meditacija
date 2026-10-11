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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette for UI laid over the sky: white ink with a cool shadow, frosted glass for controls. */
object Ink {
    val text = Color.White
    val soft = Color(0xCCFFFFFF)
    val faint = Color(0x99FFFFFF)
    val glass = Color(0x24FFFFFF)
    val glassStrong = Color(0x52FFFFFF)
    val line = Color(0x66FFFFFF)
    val panel = Color(0x8C142246)
    val shade = Color(0x7310204A)
    val accent = Color(0xFFFFE7B8)
}

val shadow = Shadow(Ink.shade, blurRadius = 18f)
val Serif = FontFamily.Serif

val TitleStyle = TextStyle(fontFamily = Serif, fontSize = 34.sp, color = Ink.text, shadow = shadow, letterSpacing = 1.sp)
val HeadStyle = TextStyle(fontFamily = Serif, fontSize = 24.sp, color = Ink.text, shadow = shadow)
val BodyStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = Ink.text, shadow = shadow)
val SmallStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Ink.soft, shadow = shadow)
val LabelStyle = TextStyle(fontSize = 12.sp, letterSpacing = 1.4.sp, color = Ink.soft, fontWeight = FontWeight.Medium, shadow = shadow)

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (strong) Ink.glassStrong else Ink.glass)
            .border(BorderStroke(1.dp, if (strong) Ink.text else Ink.line), RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BodyStyle.copy(color = if (enabled) Ink.text else Ink.faint, fontWeight = FontWeight.Medium), textAlign = TextAlign.Center)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, sub: String? = null) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Ink.glassStrong else Ink.glass)
            .border(BorderStroke(1.dp, if (selected) Ink.text else Ink.line), RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = BodyStyle.copy(fontSize = 15.sp))
        if (sub != null) Text(sub, style = SmallStyle.copy(fontSize = 11.sp))
    }
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ink.panel)
            .border(BorderStroke(1.dp, Ink.line), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String) = Text(text.uppercase(), style = LabelStyle)

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = BodyStyle)
            if (sub != null) Text(sub, style = SmallStyle)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF6FA7DD), checkedThumbColor = Color.White))
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Ink.glass).border(1.dp, Ink.line, CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Nazad", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("←", style = BodyStyle.copy(fontSize = 20.sp)) }
}

/** Darkens the sky a little behind reading-heavy screens. */
@Composable
fun Scrim(modifier: Modifier = Modifier) = Box(modifier.background(Color(0x40081830)))

fun Modifier.screenPadding() = this.padding(horizontal = 20.dp)
