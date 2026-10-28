package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import java.util.Calendar

/** One thing a note can hold, under its point: "zabrinuto" (emocije), "napeta ramena" (tijelo), "„šta ako“" (misli). */
data class Thing(val point: String, val label: String)

/**
 * How the four points touch in someone's notes. Everything here is counted, never guessed: two things are linked when
 * they come in the same note, and the link is as strong as the number of notes that hold both. Within one note we
 * cannot tell which came first, so this says "comes with", never "causes".
 *
 * Small numbers lie easily, so: a link shows only once it has come back ([threshold]: twice while there are few notes,
 * three times after), and "how much more often than chance" only once two things have come together five times; on
 * three notes it reads 6.6 times, which says more about three notes than about a person.
 *
 * Tried on the 35 test notes on the emulator: two clear clusters came out (an evening worry around "zabrinuto" and tense
 * shoulders, and tiredness at night with the phone), and the time of day was the strongest signal of all.
 */
object Links {
    const val LIFT_FROM = 5
    /** Below this, two things come together hardly more often than chance would bring them. */
    const val MIN_LIFT = 1.2f
    val PART_WHEN = listOf("ujutru", "danju", "uveče", "noću")

    private val HABIT_LABEL = mapOf(
        "moranje" to "„moram“", "uvijek" to "„uvijek, nikad“", "staako" to "„šta ako“", "samokritika" to "samokritika", "tudjemisli" to "tuđe misli",
    )

    /** Kinds of notes that look back over a whole day or week: written in the evening about the morning too, good and hard side by side. */
    private val LOOKING_BACK = setOf("evening", "week")
    private val BODY_LABELS = FourPointsReader.BODY_LABELS.map { it.first }.toSet()
    private val URGE_LABELS = FourPointsReader.URGE_LABELS.map { it.first }.toSet()
    // the named ones, and the person's own
    private val BODY_NAMES get() = BODY_LABELS + FourPointsReader.custom[FourPointsReader.BODY].orEmpty()
    private val URGE_NAMES get() = URGE_LABELS + FourPointsReader.custom[FourPointsReader.URGES].orEmpty()

    /**
     * The things one note holds: its moods (as chosen; read from the words only when none were chosen), its body and
     * urges (read from the words when the points were left empty), and its habits of mind. Only the named body
     * sensations and urges count, since a sentence in a person's own words never comes back word for word. A look back
     * over a day or a week holds nothing here: what it names did not happen at the hour it was written, nor together.
     */
    fun things(e: Entry): Set<Thing> {
        if (e.kind in LOOKING_BACK) return emptySet()
        val read = if (e.points.isEmpty && e.words.isNotBlank()) FourPointsReader.read(e.words) else null
        val out = LinkedHashSet<Thing>()
        e.moods.ifEmpty { read?.moods ?: emptyList() }.distinct().forEach { out += Thing(FourPointsReader.EMOTIONS, it) }
        val bodyNames = BODY_NAMES; val urgeNames = URGE_NAMES
        e.points.body.ifEmpty { read?.body ?: emptyList() }.filter { it in bodyNames }.forEach { out += Thing(FourPointsReader.BODY, it) }
        e.points.urges.ifEmpty { read?.urges ?: emptyList() }.filter { it in urgeNames }.forEach { out += Thing(FourPointsReader.URGES, it) }
        MindHabits.habits(MindHabits.mindOf(e)).forEach { id -> HABIT_LABEL[id]?.let { out += Thing(FourPointsReader.THOUGHTS, it) } }
        return out
    }

    /** Morning, day, evening, night: 0 to 3. */
    fun part(t: Long): Int = when (Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3
    }

    /** How many times a link must come back to be shown: twice while there are few notes, three times once there are many. */
    fun threshold(notes: Int) = if (notes < 50) 2 else 3

    class Link(val a: Thing, val b: Thing, val together: Int, val lift: Float?)

    class Web(
        val notes: Int,
        val counts: Map<Thing, Int>,
        /** Links between things of different points that came back at least [threshold] times, strongest first. */
        val links: List<Link>,
        /** For each pair of points, the share of notes that hold both. */
        val pointPairs: Map<Pair<String, String>, Float>,
        /** For each point, the share of notes that hold it. */
        val pointShare: Map<String, Float>,
        val threshold: Int,
        /** Every pair of things of different points and the notes that hold both, links or not yet. */
        val pairCounts: Map<Pair<Thing, Thing>, Int> = emptyMap(),
    ) {
        /** How many notes hold both, in either order. */
        fun together(a: Thing, b: Thing): Int = pairCounts[a to b] ?: pairCounts[b to a] ?: 0

        /** The things worth drawing: those with a link, the most frequent first, at most [max]. */
        fun stars(max: Int = 10): List<Thing> =
            links.flatMap { listOf(it.a, it.b) }.distinct().sortedByDescending { counts[it] ?: 0 }.take(max)
    }

    private val POINTS = listOf(FourPointsReader.THOUGHTS, FourPointsReader.EMOTIONS, FourPointsReader.BODY, FourPointsReader.URGES)

    fun web(entries: List<Entry>): Web = webOf(entries.map { things(it) })

    /** Each note's things and time, read once, for many questions about the same notes. */
    fun seen(entries: List<Entry>): List<Pair<Set<Thing>, Long>> = entries.map { things(it) to it.createdAt }

    internal fun webOf(seen: List<Set<Thing>>): Web {
        val notes = seen.filter { it.isNotEmpty() }
        val n = notes.size
        val counts = notes.flatten().groupingBy { it }.eachCount()
        val pairs = HashMap<Pair<Thing, Thing>, Int>()
        for (s in notes) {
            val l = s.sortedWith(compareBy({ it.point }, { it.label }))
            for (i in l.indices) for (j in i + 1 until l.size) if (l[i].point != l[j].point) {
                val k = l[i] to l[j]; pairs[k] = (pairs[k] ?: 0) + 1
            }
        }
        val th = threshold(n)
        // two frequent things meet often by chance alone: once there are enough notes to tell (LIFT_FROM together),
        // a pair that comes together no more often than chance is no link, however many times it was counted
        val links = pairs.filter { it.value >= th }.map { (k, c) ->
            Link(k.first, k.second, c, if (c >= LIFT_FROM) c.toFloat() * n / (counts.getValue(k.first) * counts.getValue(k.second)) else null)
        }.filter { (it.lift ?: Float.MAX_VALUE) >= MIN_LIFT }.sortedWith(compareByDescending<Link> { it.together }.thenBy { it.a.label })
        val pts = notes.map { s -> s.map { it.point }.toSet() }
        val pointPairs = HashMap<Pair<String, String>, Float>()
        for (i in POINTS.indices) for (j in i + 1 until POINTS.size) {
            val a = POINTS[i]; val b = POINTS[j]
            pointPairs[a to b] = if (n == 0) 0f else pts.count { a in it && b in it }.toFloat() / n
        }
        val share = POINTS.associateWith { p -> if (n == 0) 0f else pts.count { p in it }.toFloat() / n }
        return Web(n, counts, links, pointPairs, share, th, pairs)
    }

    class With(val thing: Thing, val together: Int, val lift: Float?)

    class Focus(
        val thing: Thing,
        /** Notes that hold it. */
        val notes: Int,
        /** What comes with it, most often first: only what came back at least [Web.threshold] times. */
        val with: List<With>,
        /** The part of the day it comes in, when one clearly leads (at least two thirds of the notes, at least twice). */
        val part: Int?,
        val partCount: Int,
        /** Notes per part of the day, morning to night. */
        val parts: List<Int>,
    ) {
        /** The finding in words: "Uveče, kad je zabrinuto, napeta ramena dođu 3 od 4 puta." */
        fun sentence(): String? {
            val top = with.firstOrNull() ?: return part?.let { "Najčešće dolazi ${PART_WHEN[it]}: $partCount od $notes puta." }
            val whenPart = part?.let { PART_WHEN[it].replaceFirstChar { c -> c.uppercase() } + ", " } ?: ""
            // habits of mind carry their own quotes ("„šta ako“"): the sentence adds them once
            val name = thing.label.trim('„', '“')
            val lead = if (whenPart.isEmpty()) "Kad je tu „$name“, " else whenPart + "kad je tu „$name“, "
            return lead + "uz to dođe „${top.thing.label.trim('„', '“')}“ ${top.together} od $notes puta."
        }
    }

    fun focus(entries: List<Entry>, thing: Thing): Focus = focusOf(entries.map { things(it) to it.createdAt }, thing)

    internal fun focusOf(seen: List<Pair<Set<Thing>, Long>>, thing: Thing): Focus {
        val all = seen.filter { it.first.isNotEmpty() }
        val mine = all.filter { thing in it.first }
        val n = mine.size
        val counts = all.flatMap { it.first }.groupingBy { it }.eachCount()
        val th = threshold(all.size)
        val with = mine.flatMap { it.first }.filter { it != thing && it.point != thing.point }.groupingBy { it }.eachCount()
            .filter { it.value >= th }
            .map { (t, c) -> With(t, c, if (c >= LIFT_FROM) c.toFloat() * all.size / (n * counts.getValue(t)) else null) }
            .filter { (it.lift ?: Float.MAX_VALUE) >= MIN_LIFT }
            .sortedWith(compareByDescending<With> { it.together }.thenBy { it.thing.label })
        val parts = IntArray(4).also { a -> mine.forEach { a[part(it.second)]++ } }.toList()
        val lead = parts.indices.maxByOrNull { parts[it] }
        val clear = lead != null && parts[lead] >= 2 && parts[lead] * 3 >= n * 2
        return Focus(thing, n, with, if (clear) lead else null, if (lead != null) parts[lead] else 0, parts)
    }
}
