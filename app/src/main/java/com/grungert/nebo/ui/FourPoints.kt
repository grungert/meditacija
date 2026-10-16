package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.data.FourPoints
import com.grungert.nebo.koan.FourPointsReader
import com.grungert.nebo.koan.FourPointsReader.BODY
import com.grungert.nebo.koan.FourPointsReader.EMOTIONS
import com.grungert.nebo.koan.FourPointsReader.THOUGHTS
import com.grungert.nebo.koan.FourPointsReader.URGES
import com.grungert.nebo.koan.MOODS

/** The colour of each point, the same everywhere it appears. */
val POINT_COLORS = mapOf(
    THOUGHTS to Color(0xFFB9AEF2), EMOTIONS to Color(0xFFF2A88E),
    BODY to Color(0xFF8ED6C0), URGES to Color(0xFFF0A5C6),
)
val POINT_NAMES = mapOf(THOUGHTS to "Misli", EMOTIONS to "Emocije", BODY to "Tijelo", URGES to "Nagoni")

/** The editable four points of one note. The emotions are the note's moods, held by the screen. */
class FourPointsState(initial: FourPoints = FourPoints()) {
    var thoughts by mutableStateOf(initial.thoughts)
    val body: SnapshotStateList<String> = mutableStateListOf<String>().apply { addAll(initial.body) }
    val urges: SnapshotStateList<String> = mutableStateListOf<String>().apply { addAll(initial.urges) }
    /** True once the person has changed something by hand; then nothing recognised overwrites it. */
    var touched by mutableStateOf(false)
    /** Which points were filled in from the person's words, for a short note under the diagram. */
    var recognised by mutableStateOf<List<String>>(emptyList())

    fun toPoints() = FourPoints(thoughts.trim(), body.toList(), urges.toList())

    /** Adds what was read from the words, never taking away what the person chose. */
    fun fillFrom(r: FourPointsReader.Reading, moods: SnapshotStateList<String>) {
        if (thoughts.isBlank()) thoughts = r.thoughts
        r.moods.forEach { if (it !in moods) moods += it }
        r.body.forEach { if (it !in body) body += it }
        r.urges.forEach { if (it !in urges) urges += it }
        recognised = r.found
    }
}

/**
 * Thoughts, emotions, body and urges as four points that all touch each other. A point lights up when it holds
 * something; touching it opens its own small editor below.
 */
@Composable
fun FourPointsCard(state: FourPointsState, moods: SnapshotStateList<String>, startWith: String = EMOTIONS) {
    var open by remember { mutableStateOf(startWith) }
    var explain by remember { mutableStateOf(false) }
    val filled = mapOf(
        THOUGHTS to state.thoughts.isNotBlank(), EMOTIONS to moods.isNotEmpty(),
        BODY to state.body.isNotEmpty(), URGES to state.urges.isNotEmpty(),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel("Četiri tačke (po želji)") }
            Text(if (explain) "Sakrij" else "Šta je ovo?", style = SmallStyle.copy(color = Ink.accent, shadow = null),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { explain = !explain }.padding(4.dp))
        }
        if (explain) Text("Misli, emocije, tijelo i nagoni utiču jedni na druge: misao probudi strah, strah stegne stomak, " +
            "stisnut stomak traži bijeg u telefon. Kad primijetiš sva četiri, vidiš lanac, i možeš ga prekinuti tamo gdje je najlakše.",
            style = SmallStyle.copy(shadow = null))

        // the diagram: four points, every one joined to every other
        BoxWithConstraints(Modifier.fillMaxWidth().height(118.dp)) {
            val w = maxWidth; val h = maxHeight
            val pill = 112.dp; val ph = 40.dp
            val spots = mapOf(
                THOUGHTS to Offset(0f, 0f), EMOTIONS to Offset(1f, 0f),
                BODY to Offset(0f, 1f), URGES to Offset(1f, 1f),
            )
            Canvas(Modifier.fillMaxSize()) {
                val cx = { k: String -> pill.toPx() / 2 + spots.getValue(k).x * (size.width - pill.toPx()) }
                val cy = { k: String -> ph.toPx() / 2 + spots.getValue(k).y * (size.height - ph.toPx()) }
                val keys = spots.keys.toList()
                for (i in keys.indices) for (j in i + 1 until keys.size) {
                    val a = keys[i]; val b = keys[j]
                    val lit = filled.getValue(a) && filled.getValue(b)
                    drawLine(Color.White.copy(alpha = if (lit) 0.55f else 0.14f), Offset(cx(a), cy(a)), Offset(cx(b), cy(b)), (if (lit) 2f else 1.2f) * density)
                }
            }
            for ((k, s) in spots) {
                val c = POINT_COLORS.getValue(k)
                val on = filled.getValue(k)
                Box(
                    Modifier.offset(x = (w - pill) * s.x, y = (h - ph) * s.y).width(pill).height(ph)
                        .clip(RoundedCornerShape(20.dp)).background(if (on) c.copy(alpha = 0.92f) else Ink.surfaceHigh)
                        .then(if (open == k) Modifier.border(2.dp, c, RoundedCornerShape(20.dp)) else Modifier)
                        .clickable(role = Role.Tab, onClickLabel = POINT_NAMES[k]) { open = k },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!on) Box(Modifier.size(7.dp).clip(CircleShape).background(c))
                        if (!on) Box(Modifier.width(7.dp))
                        Text(POINT_NAMES.getValue(k), style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on) Ink.inkOnPaper else Ink.text, shadow = null))
                    }
                }
            }
        }
        if (state.recognised.isNotEmpty()) Text("Prepoznato iz tvojih riječi: " + state.recognised.joinToString(", ") { POINT_NAMES.getValue(it).lowercase() } +
            ". Možeš ispraviti.", style = SmallStyle.copy(color = Ink.accent, shadow = null))

        // the editor of the chosen point
        when (open) {
            THOUGHTS -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.surfaceHigh).padding(14.dp)) {
                if (state.thoughts.isEmpty()) Text("Šta ti se vrti u glavi…", style = BodyStyle.copy(color = Ink.faint, shadow = null))
                BasicTextField(state.thoughts, { state.thoughts = it; state.touched = true },
                    textStyle = BodyStyle.copy(fontSize = 16.sp, lineHeight = 23.sp, shadow = null), cursorBrush = SolidColor(Ink.accent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
            EMOTIONS -> ChipFlow(MOODS.map { it.first }, moods, { MOOD_COLORS[it] }) { state.touched = true }
            BODY -> ChipFlow(FourPointsReader.BODY_LABELS.map { it.first }, state.body, { POINT_COLORS[BODY] }) { state.touched = true }
            URGES -> ChipFlow(FourPointsReader.URGE_LABELS.map { it.first }, state.urges, { POINT_COLORS[URGES] }) { state.touched = true }
        }
    }
}

/** The offered labels, plus anything chosen that is not among them (words the person said), all toggleable. */
@Composable
private fun ChipFlow(offered: List<String>, chosen: SnapshotStateList<String>, dot: (String) -> Color?, onChange: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (label in (chosen.filter { it !in offered } + offered)) {
            Chip(label, label in chosen, {
                if (label in chosen) chosen.remove(label) else chosen.add(label)
                onChange()
            }, dot = dot(label))
        }
    }
}

/** One short line for a note's points, as in the journal: "Tijelo: stisnut stomak · Nagon: da uzmem telefon". */
fun FourPoints.summary(): String = listOfNotNull(
    body.takeIf { it.isNotEmpty() }?.let { "Tijelo: " + it.joinToString(", ") },
    urges.takeIf { it.isNotEmpty() }?.let { "Nagon: " + it.joinToString(", ") },
).joinToString(" · ")
