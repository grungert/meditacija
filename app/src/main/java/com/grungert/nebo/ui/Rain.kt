package com.grungert.nebo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.koan.MOODS

/**
 * RAIN, for a hard feeling: Recognize (name it), Allow (let it be there), Investigate (where it sits in the body),
 * Nurture (a kind word to oneself). Each step takes as long as it takes; the person moves on when ready.
 */
val RAIN_STEPS = listOf(
    Triple("R · Prepoznaj", "Šta je tu?", "Daj mu ime, tiho, u sebi. Ne mora biti tačno, dovoljno je blizu."),
    Triple("A · Dozvoli", "Neka bude tu", "Ne moraš ga popraviti ni otjerati. Možeš reći sebi: i ovo smije da bude."),
    Triple("I · Istraži", "Gdje je u tijelu?", "Dodirni mjesto gdje ga osjećaš. Kakvo je: toplo, stegnuto, teško, nemirno?"),
    Triple("N · Njeguj", "Malo topline", "Šta bi rekao prijatelju koji ovo osjeća? Reci to sebi. Ruka na srcu pomaže."),
)

/** RAIN's colour: a soft rain-blue, between the lake and the evening. */
val RAIN_TONE = Color(0xFFA9C7F0)

/**
 * One step of RAIN on a card: its words, and what it asks for (moods to name, a sentence to keep), with the way on.
 */
@Composable
fun RainCard(
    step: Int, moods: List<String>, onMood: (String) -> Unit, touched: Int, words: String, onWords: (String) -> Unit,
    onNext: () -> Unit,
) {
    val (label, title, line) = RAIN_STEPS[step]
    Column(
        Modifier.widthIn(max = 380.dp).padding(horizontal = 18.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xF20E1834))
            .border(1.dp, RAIN_TONE.copy(alpha = 0.45f), RoundedCornerShape(26.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(label.uppercase() + " · ${step + 1} OD 4", style = LabelStyle.copy(color = RAIN_TONE.copy(alpha = 0.85f), shadow = null))
        Text(title, style = TitleStyle.copy(fontSize = 27.sp, fontWeight = FontWeight.Light, color = RAIN_TONE, shadow = null), textAlign = TextAlign.Center)
        Text(line, style = BodyStyle.copy(color = Ink.text, lineHeight = 23.sp, shadow = null), textAlign = TextAlign.Center)
        when (step) {
            0 -> ChipRow { for ((m, _) in MOODS) Chip(m, m in moods, { onMood(m) }, dot = MOOD_COLORS[m]) }
            2 -> Text(if (touched == 0) "Dodirni tijelo iznad." else "Označeno: $touched " + plural(touched, "mjesto", "mjesta", "mjesta") + ".",
                style = SmallStyle.copy(shadow = null))
            3 -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.surface).padding(12.dp)) {
                if (words.isEmpty()) Text("Ono što sebi kažeš (po želji)…", style = BodyStyle.copy(color = Ink.faint, shadow = null))
                BasicTextField(words, onWords, textStyle = BodyStyle.copy(fontSize = 16.sp, shadow = null), cursorBrush = SolidColor(RAIN_TONE),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            GlassButton(if (step < 3) "Dalje" else "Završi", onNext, Modifier.fillMaxWidth(), strong = true)
        }
    }
}
