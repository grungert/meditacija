package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import java.util.Calendar

/** Where a thread is in its life: newly woken, coming back, or settling down. */
enum class ThreadPhase(val label: String) { WAKING("budi se"), RETURNING("vraća se"), SETTLING("smiruje se") }

/**
 * The face of a thread of insight: how strongly each of the four points shows in its notes, one sentence about
 * when it comes and what comes with it, where it is in its life, and a practice that fits it.
 */
class ThreadFace(
    /** Share of the thread's notes that hold each point, 0..1, keyed like [FourPointsReader.THOUGHTS]. */
    val strength: Map<String, Float>,
    val dominant: String?,
    val sentence: String,
    val phase: ThreadPhase,
    val phaseNote: String,
    /** A session type that fits, and why in a few words. */
    val practice: String,
    val practiceName: String,
    val practiceWhy: String,
)

object ThreadFaces {
    private const val DAY = 86_400_000L
    private val HEAVY = setOf("nemirno", "umorno", "tužno", "ljuto", "zabrinuto", "zbunjeno")
    private val MOOD_NOUN = mapOf(
        "mirno" to "mir", "nemirno" to "nemir", "umorno" to "umor", "tužno" to "tuga", "ljuto" to "ljutnja",
        "zabrinuto" to "briga", "zbunjeno" to "zbunjenost", "zahvalno" to "zahvalnost", "radosno" to "radost", "radoznalo" to "radoznalost",
    )
    private val PART = listOf("ujutru", "danju", "uveče", "noću")
    private val WEEKDAY = mapOf(
        Calendar.MONDAY to "ponedjeljkom", Calendar.TUESDAY to "utorkom", Calendar.WEDNESDAY to "srijedom", Calendar.THURSDAY to "četvrtkom",
        Calendar.FRIDAY to "petkom", Calendar.SATURDAY to "subotom", Calendar.SUNDAY to "nedjeljom",
    )
    private val HABIT_PHRASE = mapOf(
        "moranje" to "misao „moram“", "uvijek" to "„uvijek“ i „nikad“", "staako" to "misao „šta ako“",
        "samokritika" to "samokritika", "tudjemisli" to "nagađanje šta drugi misle",
    )

    /** What one note shows of the four points: its own, or what its words say when nothing was filled in. */
    private class Seen(val thoughts: Boolean, val moods: List<String>, val body: List<String>, val urges: List<String>, val habits: List<String>)

    private fun seen(e: Entry): Seen {
        val read = if (e.points.isEmpty) FourPointsReader.read(e.words) else null
        val habits = MindHabits.habits(MindHabits.mindOf(e))
        return Seen(
            thoughts = e.points.thoughts.isNotBlank() || read?.thoughts?.isNotBlank() == true || habits.isNotEmpty(),
            moods = (e.moods + (read?.moods ?: emptyList())).distinct(),
            body = e.points.body.ifEmpty { read?.body ?: emptyList() },
            urges = e.points.urges.ifEmpty { read?.urges ?: emptyList() },
            habits = habits,
        )
    }

    private fun part(t: Long): Int = when (Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3
    }

    /** The most common value, when it shows in at least two notes and in at least 40% of them: two of twenty is not "usually". */
    private fun <T> common(items: List<List<T>>): T? {
        val n = items.size
        return items.flatMap { it.distinct() }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value >= 2 && it.value >= 0.4f * n }?.key
    }

    private fun joinAnd(items: List<String>) = when (items.size) {
        0 -> ""; 1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " i " + items.last()
    }

    private fun plural(n: Int, one: String, few: String, many: String) = when {
        n % 10 == 1 && n % 100 != 11 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }

    fun of(entries: List<Entry>, now: Long = System.currentTimeMillis()): ThreadFace {
        val notes = entries.sortedBy { it.createdAt }
        val seen = notes.map { seen(it) }
        val n = notes.size.coerceAtLeast(1)
        val strength = mapOf(
            FourPointsReader.THOUGHTS to seen.count { it.thoughts } / n.toFloat(),
            FourPointsReader.EMOTIONS to seen.count { it.moods.isNotEmpty() } / n.toFloat(),
            FourPointsReader.BODY to seen.count { it.body.isNotEmpty() } / n.toFloat(),
            FourPointsReader.URGES to seen.count { it.urges.isNotEmpty() } / n.toFloat(),
        )
        // moods are one tap away and show up everywhere; a pull to act or the body says more about a thread
        val weight = mapOf(FourPointsReader.URGES to 1.6f, FourPointsReader.BODY to 1.3f, FourPointsReader.THOUGHTS to 1.1f, FourPointsReader.EMOTIONS to 1f)
        val dominant = strength.filter { it.value > 0f }.maxByOrNull { it.value * weight.getValue(it.key) }?.key

        // when it comes
        val parts = notes.groupingBy { part(it.createdAt) }.eachCount().maxByOrNull { it.value }
        val days = notes.groupingBy { Calendar.getInstance().apply { timeInMillis = it.createdAt }.get(Calendar.DAY_OF_WEEK) }.eachCount().maxByOrNull { it.value }
        val whenParts = listOfNotNull(
            // two Thursdays, or two evenings, out of three is chance: "najčešće" needs four notes at least
            days?.takeIf { notes.size >= 4 && it.value >= 0.6f * notes.size }?.let { WEEKDAY[it.key] },
            parts?.takeIf { notes.size >= 4 && it.value >= 0.6f * notes.size }?.let { PART[it.key] },
        )
        // what comes with it
        val with = listOfNotNull(
            common(seen.map { it.moods })?.let { MOOD_NOUN[it] ?: it },
            common(seen.map { it.habits })?.let { HABIT_PHRASE[it] },
            common(seen.map { it.body }),
            common(seen.map { it.urges })?.let { "poriv „$it“" },
        )
        val sentence = when {
            whenParts.isNotEmpty() && with.isNotEmpty() -> "Najčešće ${whenParts.joinToString(" ")}, uz ${joinAnd(with)}."
            whenParts.isNotEmpty() -> "Najčešće ${whenParts.joinToString(" ")}."
            with.isNotEmpty() -> "Uz nju: ${joinAnd(with)}."
            else -> ""
        }

        // where it is in its life
        val first = notes.firstOrNull()?.createdAt ?: now
        val last = notes.lastOrNull()?.createdAt ?: now
        val quiet = ((now - last) / DAY).toInt()
        fun heaviness(e: Seen) = if (e.moods.isEmpty()) null else e.moods.count { it in HEAVY } / e.moods.size.toFloat()
        val felt = seen.mapNotNull { heaviness(it) }
        val lighter = felt.size >= 3 && felt.takeLast(2).all { it == 0f } && felt.dropLast(2).average() >= 0.5
        val (phase, note) = when {
            quiet >= 21 -> ThreadPhase.SETTLING to "tiho je već $quiet dana"
            lighter -> ThreadPhase.SETTLING to "posljednji zapisi su lakši"
            now - first <= 14 * DAY -> ThreadPhase.WAKING to ((now - first) / DAY).toInt().let { d ->
                if (d <= 1) "pojavila se ovih dana" else "pojavila se prije $d dana"
            }
            else -> {
                val weeks = ((last - first) / (7 * DAY)).toInt().coerceAtLeast(1)
                ThreadPhase.RETURNING to "${notes.size} ${plural(notes.size, "put", "puta", "puta")} za $weeks ${plural(weeks, "sedmicu", "sedmice", "sedmica")}"
            }
        }

        // a practice that fits what the thread holds most
        val heavyShare = seen.count { s -> s.moods.any { it in HEAVY } } / n.toFloat()
        val (practice, name, why) = when (dominant) {
            FourPointsReader.URGES -> Triple("hourglass", "Pješčani sat", "Tri minute između poriva i onoga što uradiš.")
            FourPointsReader.BODY -> Triple("bodyscan", "Skeniranje tijela", "Da vidiš gdje ovo nosiš u tijelu.")
            FourPointsReader.THOUGHTS -> Triple("thoughts", "Oblaci", "Da misli vidiš kao oblake koji dolaze i prolaze.")
            FourPointsReader.EMOTIONS -> if (heavyShare >= 0.5f) Triple("points", "4 tačke", "Da vidiš kako emocija pomjera tijelo i misli.")
                else Triple("silence", "Tišina", "Samo budi s tim što je dobro.")
            else -> Triple("breath", "Disanje", "Nekoliko minuta s dahom, pa vidi šta ostaje.")
        }
        return ThreadFace(strength, dominant, sentence, phase, note, practice, name, why)
    }
}
