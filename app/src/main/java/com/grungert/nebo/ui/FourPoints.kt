package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.DpOffset
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

val POINT_ICONS = mapOf(THOUGHTS to NeboIcon.Cloud, EMOTIONS to NeboIcon.Heart, BODY to NeboIcon.Body, URGES to NeboIcon.Urge)

/** Where each point sits: a diamond read clockwise, thought above, the body below. */
private val SPOTS = mapOf(
    THOUGHTS to Offset(0.5f, 0f), EMOTIONS to Offset(1f, 0.5f), BODY to Offset(0.5f, 1f), URGES to Offset(0f, 0.5f),
)

/**
 * The four points as lights in a diamond, every one joined to every other by a thread. A point glows in its colour
 * when it holds something; a thread brightens when both its ends do. [selected] gets a ring, [highlight] (in a session)
 * is drawn larger while the others rest. Labels are left out when [labels] is false. With [strength] (0..1 for each
 * point, as in a thread) the glow of each light and thread follows how strongly that point shows.
 */
@Composable
fun PointsConstellation(
    filled: Map<String, Boolean>, modifier: Modifier = Modifier, selected: String? = null, highlight: String? = null,
    labels: Boolean = true, strength: Map<String, Float>? = null, nodeSize: androidx.compose.ui.unit.Dp? = null, onTap: ((String) -> Unit)? = null,
) {
    fun power(k: String) = strength?.get(k) ?: 1f
    val glow = com.grungert.nebo.sky.slowPulse(2600)
    BoxWithConstraints(modifier) {
        val node = nodeSize ?: if (labels) 52.dp else 26.dp
        val labelH = if (labels) 22.dp else 0.dp
        val w = maxWidth; val h = maxHeight
        fun center(k: String) = SPOTS.getValue(k).let { o -> DpOffset((w - node) * o.x + node / 2, (h - node - labelH) * o.y + node / 2) }
        Canvas(Modifier.fillMaxSize()) {
            val keys = SPOTS.keys.toList()
            fun pt(k: String) = center(k).let { Offset(it.x.toPx(), it.y.toPx()) }
            val mid = Offset(size.width / 2, (size.height - labelH.toPx()) / 2)
            for (i in keys.indices) for (j in i + 1 until keys.size) {
                val a = keys[i]; val b = keys[j]
                val pa = pt(a); val pb = pt(b)
                val lit = filled.getValue(a) && filled.getValue(b)
                // threads bow gently towards the middle, like strings, and are drawn twice for a soft glow
                val ctrl = Offset((pa.x + pb.x) / 2 * 0.8f + mid.x * 0.2f, (pa.y + pb.y) / 2 * 0.8f + mid.y * 0.2f)
                val path = Path().apply { moveTo(pa.x, pa.y); quadraticTo(ctrl.x, ctrl.y, pb.x, pb.y) }
                val tone = if (lit) lerp(POINT_COLORS.getValue(a), POINT_COLORS.getValue(b), 0.5f) else Ink.text
                val both = minOf(power(a), power(b)).coerceIn(0.25f, 1f)
                if (lit) drawPath(path, tone.copy(alpha = (0.18f + 0.12f * glow) * both), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, tone.copy(alpha = if (lit) 0.85f * both else 0.13f), style = Stroke((if (lit) 1.6f else 1f).dp.toPx(), cap = StrokeCap.Round))
            }
            for (k in keys) {
                if (!filled.getValue(k) && highlight != k) continue
                val c = POINT_COLORS.getValue(k)
                val r = node.toPx() * (if (highlight == k) 1.25f else 0.95f) * (0.9f + 0.1f * glow) * (0.7f + 0.5f * power(k).coerceIn(0f, 1f))
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f * power(k).coerceIn(0.35f, 1f)), c.copy(alpha = 0f)), pt(k), r), r, pt(k))
            }
        }
        for (k in SPOTS.keys) {
            val c = POINT_COLORS.getValue(k)
            val on = filled.getValue(k)
            val dim = highlight != null && highlight != k
            val ctr = center(k)
            val size = if (highlight == k) node * 1.15f else node
            // without labels the column is only as wide as its light, so a small card cannot squeeze it off centre
            val colW = if (labels) 100.dp else size
            Column(
                Modifier.offset(x = ctr.x - colW / 2, y = ctr.y - size / 2).width(colW)
                    .then(if (onTap != null) Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Tab, onClickLabel = POINT_NAMES[k]) { onTap(k) } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(size).clip(CircleShape)
                        .background(if (on) c else Ink.surfaceHigh.copy(alpha = if (dim) 0.5f else 1f))
                        .then(if (selected == k) Modifier.border(2.dp, Ink.text.copy(alpha = 0.9f), CircleShape) else Modifier)
                        .then(if (!on) Modifier.border(1.5.dp, c.copy(alpha = if (dim) 0.3f else 0.7f), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { if (node >= 20.dp) Icon(POINT_ICONS.getValue(k), if (on) Ink.inkOnPaper else c.copy(alpha = if (dim) 0.45f else 1f), size * 0.46f) }
                if (labels) Text(POINT_NAMES.getValue(k), style = SmallStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (on || selected == k) Ink.text else Ink.soft, shadow = null), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** The editable four points of one note. The emotions are the note's moods, held by the screen. */
class FourPointsState(initial: FourPoints = FourPoints()) {
    var thoughts by mutableStateOf(initial.thoughts)
    val body: SnapshotStateList<String> = mutableStateListOf<String>().apply { addAll(initial.body) }
    val urges: SnapshotStateList<String> = mutableStateListOf<String>().apply { addAll(initial.urges) }
    /** True once the person has changed something by hand; then nothing recognised overwrites it. */
    var touched by mutableStateOf(false)
    /** Which points were filled in from the person's words, for a short note under the diagram. */
    var recognised by mutableStateOf<List<String>>(emptyList())
    /** Where they were read from: the person's words, or the places touched in a body scan. */
    var fromScan by mutableStateOf(false)
    /** The body labels the scan brought; they stay whatever the words say. */
    var scanBody: List<String> = emptyList()

    fun toPoints() = FourPoints(thoughts.trim(), body.toList(), urges.toList())

    /**
     * The points as the words say them now. Until the person changes something by hand ([touched], after which this is
     * not called), everything here came from the words, so it is simply read again: a word typed and then deleted takes
     * back what it brought, and the thoughts follow the whole text rather than its first few words.
     */
    fun fillFrom(r: FourPointsReader.Reading, moods: SnapshotStateList<String>) {
        thoughts = r.thoughts
        moods.clear(); moods.addAll(r.moods)
        body.clear(); body.addAll((scanBody + r.body).distinct())
        urges.clear(); urges.addAll(r.urges)
        recognised = ((if (fromScan) listOf(FourPointsReader.BODY) else emptyList()) + r.found).distinct()
    }
}

/**
 * Thoughts, emotions, body and urges as four points that all touch each other. A point lights up when it holds
 * something; touching it opens its own small editor below.
 */
@Composable
fun FourPointsCard(state: FourPointsState, moods: SnapshotStateList<String>, startWith: String = EMOTIONS) {
    var open by remember { mutableStateOf(startWith) }
    LaunchedEffect(startWith) { open = startWith }
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
            Text(if (explain) "Sakrij" else "Šta je ovo?", style = SmallStyle.copy(color = Ink.accentInk, shadow = null),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { explain = !explain }.padding(4.dp))
        }
        if (explain) Text("Misli, emocije, tijelo i nagoni utiču jedni na druge: misao probudi strah, strah stegne stomak, " +
            "stisnut stomak traži bijeg u telefon. Kad primijetiš sva četiri, vidiš lanac, i možeš ga prekinuti tamo gdje je najlakše.",
            style = SmallStyle.copy(shadow = null))

        PointsConstellation(filled, selected = open, onTap = { open = it }, modifier = Modifier.fillMaxWidth().height(200.dp))
        if (state.recognised.isNotEmpty()) Text(when {
            state.fromScan && state.recognised.size > 1 -> "Iz skeniranja i tvojih riječi: "
            state.fromScan -> "Tijelo je popunjeno iz skeniranja"
            else -> "Prepoznato iz tvojih riječi: "
        } +
            (if (state.fromScan && state.recognised.size == 1) "" else state.recognised.joinToString(", ") { POINT_NAMES.getValue(it).lowercase() }) + ". Možeš ispraviti.", style = SmallStyle.copy(color = Ink.accent, shadow = null))

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
