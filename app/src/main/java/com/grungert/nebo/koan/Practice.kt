package com.grungert.nebo.koan

import com.grungert.nebo.data.Session
import java.util.Calendar

/**
 * What the sessions themselves say, beside the notes: how much of what was planned is sat through, and how many
 * thoughts come in a clouds session as the weeks go by. Counted, never guessed, and said only from enough sessions.
 * A sit under half a minute was only a start (a wrong tap, a phone call) and counts for neither.
 */
object Practice {
    /** A session counts as sat through at 90% of what was planned: the last breath before the bell is no failure. */
    const val DONE = 0.9f
    /** Sessions a group needs before anything is said about it. */
    const val ENOUGH = 4

    class Group(val key: String, val done: Int, val count: Int) {
        val share get() = done.toFloat() / count.coerceAtLeast(1)
    }

    class Completion(
        val done: Int, val count: Int,
        /** By planned minutes (3, 5, 10…), by kind, and by part of the day (0 morning … 3 night); only groups with [ENOUGH]. */
        val byLength: List<Group>, val byKind: List<Group>, val byPart: List<Group>,
    ) {
        /**
         * The one thing worth saying: the clearest gap between two lengths (or else two parts of the day) of at least
         * 30 points, or else the plain share.
         */
        fun sentence(): String? {
            if (count < ENOUGH) return null
            fun gap(gs: List<Group>) = if (gs.size < 2) null else (gs.maxBy { it.share } to gs.minBy { it.share }).takeIf { (a, b) -> a.share - b.share >= 0.3f }
            gap(byLength)?.let { (a, b) ->
                return "Sesije od ${a.key} min završiš ${a.done} od ${a.count} puta, a od ${b.key} min ${b.done} od ${b.count}."
            }
            gap(byPart)?.let { (a, b) ->
                val w = Links.PART_WHEN
                return "${w[a.key.toInt()].replaceFirstChar { it.uppercase() }} završiš ${a.done} od ${a.count} sesija, a ${w[b.key.toInt()]} ${b.done} od ${b.count}."
            }
            return "Do kraja odsjediš $done od $count sesija."
        }
    }

    private fun part(t: Long): Int = when (Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3
    }

    /** How much of the planned time was sat through, of the sessions that had a planned length. */
    fun completion(sessions: List<Session>): Completion {
        val s = sessions.filter { it.plannedSec > 0 && it.actualSec >= 30 }
        fun done(x: Session) = x.actualSec >= x.plannedSec * DONE
        fun groups(key: (Session) -> String) = s.groupBy(key).filter { it.value.size >= ENOUGH }
            .map { (k, l) -> Group(k, l.count(::done), l.size) }
        return Completion(
            s.count(::done), s.size,
            groups { (it.plannedSec / 60).toString() }.sortedBy { it.key.toInt() },
            groups { it.type }.sortedByDescending { it.share },
            groups { part(it.startedAt).toString() }.sortedBy { it.key.toInt() },
        )
    }

    /** One clouds session: when, and how many thoughts it noticed in a minute. */
    class Cloud(val at: Long, val perMinute: Float)

    class Clouds(
        val sessions: List<Cloud>,
        /** Thoughts a minute in the first and in the last third of the sessions; null with fewer than six sessions. */
        val early: Float?, val late: Float?,
    ) {
        /** Fewer thoughts, more, or about the same: said only when the change is at least a quarter. */
        fun sentence(): String? {
            val e = early ?: return null; val l = late ?: return null
            fun n(x: Float) = "%.1f".format(x).replace('.', ',')
            return when {
                l <= e * 0.75f -> "Misli dolaze rjeđe nego na početku: ${n(e)} u minuti tada, ${n(l)} sada. Um se lakše smiruje."
                l >= e * 1.25f -> "Ovih dana primijetiš više misli: ${n(e)} u minuti na početku, ${n(l)} sada. To nije nazadak, nego budnije oko."
                else -> "Misli dolaze otprilike jednako često, oko ${n((e + l) / 2)} u minuti."
            }
        }
    }

    /** The clouds sessions of at least a minute, oldest first, and how the first third compares with the last. */
    fun clouds(sessions: List<Session>): Clouds {
        val c = sessions.filter { it.type == "thoughts" && it.actualSec >= 60 }.sortedBy { it.startedAt }
            .map { Cloud(it.startedAt, it.thoughts / (it.actualSec / 60f)) }
        if (c.size < 6) return Clouds(c, null, null)
        val third = c.size / 3
        return Clouds(c, c.take(third).map { it.perMinute }.average().toFloat(), c.takeLast(third).map { it.perMinute }.average().toFloat())
    }
}
