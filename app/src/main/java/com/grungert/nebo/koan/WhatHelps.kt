package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.Session
import java.util.Calendar

/**
 * What helps, from the person's own notes: for each session with a note before and a note after it, whether the
 * moods after were lighter than before. Only what enough sessions show is said; until then, how far there is to go.
 *
 * "Before" is the note asked for before the sit, or else the last note of the two hours before it (how the person was
 * as they sat down); "after" is the note asked for after it, or else the first note of the two hours after (the 50-50
 * asks for none). A mood is the one chosen, or else the one the words say ("umorna sam" counts as tired).
 *
 * Feeling lighter after a sit is not yet the sit's doing: one sits down when it is hard, and hard moments ease by
 * themselves. So the same change is measured where no sit was: two ordinary notes as far apart, with nothing between
 * ([drifts]). A sit helps when it leaves things lighter than that, from the same starting mood; until there are
 * enough such pairs ([ENOUGH_DRIFTS]) nothing is claimed for it.
 */
object WhatHelps {
    /** How a mood sits: lighter (+1), heavy (-1), or in between. */
    private val WEIGHT = mapOf(
        "mirno" to 1f, "zahvalno" to 1f, "radosno" to 1f, "radoznalo" to 0.5f,
        "umorno" to -0.5f, "zbunjeno" to -0.5f, "nemirno" to -1f, "tužno" to -1f, "ljuto" to -1f, "zabrinuto" to -1f,
    )
    /** Sessions of one kind needed before anything is said about it. */
    const val ENOUGH = 5

    private fun weight(moods: List<String>): Float? = moods.mapNotNull { WEIGHT[it] }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    /** The moods of a note: as chosen, or read from its words when none were. */
    private fun moodsOf(e: Entry): List<String> = e.moods.ifEmpty { if (e.words.isBlank()) emptyList() else FourPointsReader.read(e.words).moods }

    /** Whether a note can stand for how the person was before a sit: a note of the moment, with a mood that weighs. */
    fun tellsHow(e: Entry): Boolean = e.kind !in NOT_BEFORE && moodsOf(e).any { it in WEIGHT }

    /** How long before a sit a note still tells how the person sat down. */
    const val BEFORE_WINDOW = 2 * 60 * 60_000L
    /** Notes that are not about the moment they were written in, or that belong to the end of a sit. */
    private val NOT_BEFORE = setOf("after", "evening", "week", "rain")

    /** One session with a note before and after: its kind, when it was, how it began and how much lighter it left (after − before). */
    class Shift(val type: String, val part: Int, val shift: Float, val before: Float = 0f)

    /** Two ordinary notes with no sit between them: how the mood began and how it changed by itself. */
    class Drift(val before: Float, val shift: Float)

    /** Pairs of ordinary notes needed before a sit's effect is told apart from how things change by themselves. */
    const val ENOUGH_DRIFTS = 8
    private const val SHORTEST = 10 * 60_000L

    /**
     * The control: consecutive ordinary notes, ten minutes to two and a half hours apart (as far as a sit's notes can
     * be), with no sit between them.
     */
    fun drifts(sessions: List<Session>, entries: List<Entry>): List<Drift> {
        val loose = entries.filter { it.sessionId == null && it.kind !in NOT_BEFORE }.sortedBy { it.createdAt }
            .mapNotNull { e -> weight(moodsOf(e))?.let { e.createdAt to it } }
        val sits = sessions.filter { it.actualSec >= 30 }
        return loose.zipWithNext().mapNotNull { (a, b) ->
            val gap = b.first - a.first
            if (gap < SHORTEST || gap > BEFORE_WINDOW + 30 * 60_000L) return@mapNotNull null
            if (sits.any { it.startedAt + it.actualSec * 1000L > a.first && it.startedAt < b.first }) return@mapNotNull null
            Drift(a.second, b.second - a.second)
        }
    }

    /** How a mood usually changes by itself from where it began: heavy, in between, or light. */
    private fun band(w: Float) = when { w < -0.25f -> 0; w > 0.25f -> 2; else -> 1 }

    private fun usual(drifts: List<Drift>): (Float) -> Float {
        val all = drifts.map { it.shift }.average().toFloat()
        val byBand = drifts.groupBy { band(it.before) }.filter { it.value.size >= 3 }.mapValues { (_, l) -> l.map { it.shift }.average().toFloat() }
        return { before -> byBand[band(before)] ?: all }
    }

    fun pairs(sessions: List<Session>, entries: List<Entry>): List<Shift> {
        val bySession = entries.filter { it.sessionId != null }.groupBy { it.sessionId!! }
        // the notes that can stand for "before", each with its weight, read once (not once for every session)
        val loose = entries.filter { it.sessionId == null && it.kind !in NOT_BEFORE }.sortedBy { it.createdAt }
            .mapNotNull { e -> weight(moodsOf(e))?.let { e.createdAt to it } }
        // the sits themselves, in order: a note stands for the one nearest it, never across another sit
        val sits = sessions.filter { it.actualSec >= 30 }.sortedBy { it.startedAt }
        fun endOf(x: Session) = x.startedAt + x.actualSec * 1000L
        return sessions.mapNotNull { s ->
            val notes = bySession[s.id].orEmpty()
            // a sit known only by the notes around it must have been one, not a start of a few seconds
            if (notes.isEmpty() && s.actualSec < 30) return@mapNotNull null
            val end = endOf(s)
            val prevEnd = sits.lastOrNull { it.startedAt < s.startedAt && it.id != s.id }?.let { endOf(it) } ?: Long.MIN_VALUE
            val nextStart = sits.firstOrNull { it.startedAt > s.startedAt && it.id != s.id }?.startedAt ?: Long.MAX_VALUE
            val after = weight(notes.filter { it.kind == "after" }.flatMap { moodsOf(it) })
                ?: loose.firstOrNull { it.first in end..minOf(end + BEFORE_WINDOW, nextStart) }?.second ?: return@mapNotNull null
            val asked = weight(notes.filter { it.kind == "before" }.flatMap { moodsOf(it) })
            val before = asked ?: loose.lastOrNull { it.first in maxOf(s.startedAt - BEFORE_WINDOW, prevEnd)..s.startedAt }?.second ?: return@mapNotNull null
            Shift(s.type, part(s.startedAt), after - before, before)
        }
    }

    /** Morning, day, evening, night. */
    fun part(t: Long): Int = when (Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3
    }

    /**
     * For one kind of session: how many had both notes, in how many it was lighter after, by how much on average; and,
     * once there is a control, in how many it was lighter than things get by themselves ([beyond]) and by how much
     * more ([effect]).
     */
    class Kind(val type: String, val count: Int, val lighter: Int, val shift: Float, val beyond: Int? = null, val effect: Float? = null)

    /**
     * What can be said: per kind with enough sessions, best first, and a time of day that clearly helps more for one
     * kind. [controlled]: there were enough ordinary pairs ([drifts]) to tell a sit's effect from time passing.
     */
    class Finding(val kinds: List<Kind>, val partFor: Triple<String, Int, Int>?, val pairs: Int, val drifts: Int = 0) {
        val controlled get() = drifts >= ENOUGH_DRIFTS
    }

    fun find(sessions: List<Session>, entries: List<Entry>): Finding {
        val ps = pairs(sessions, entries)
        val ds = drifts(sessions, entries)
        val expect = if (ds.size >= ENOUGH_DRIFTS) usual(ds) else null
        fun effectOf(x: Shift) = x.shift - (expect?.invoke(x.before) ?: 0f)
        val kinds = ps.groupBy { it.type }.filter { it.value.size >= ENOUGH }
            .map { (t, l) ->
                Kind(t, l.size, l.count { it.shift > 0f }, l.map { it.shift }.average().toFloat(),
                    expect?.let { e -> l.count { it.shift > e(it.before) } }, expect?.let { l.map { effectOf(it) }.average().toFloat() })
            }
            .sortedWith(compareByDescending<Kind> { (it.beyond ?: it.lighter).toFloat() / it.count }.thenByDescending { it.effect ?: it.shift })
        // a part of the day that helps more for a kind: at least four sessions on each side, and a clear gap between them;
        // only with a control, since hard evenings ease by themselves more than calm mornings do
        var partFor: Triple<String, Int, Int>? = null
        if (expect != null) for (k in kinds.filter { it.type !in NOT_SUGGESTED }) {
            val byPart = ps.filter { it.type == k.type }.groupBy { it.part }.filter { it.value.size >= 4 }
                .mapValues { (_, l) -> l.map { effectOf(it) }.average().toFloat() }
            if (byPart.size < 2) continue
            val best = byPart.maxBy { it.value }; val worst = byPart.minBy { it.value }
            if (best.value - worst.value >= 0.5f) { partFor = Triple(k.type, best.key, worst.key); break }
        }
        return Finding(kinds, partFor, ps.size, ds.size)
    }

    /**
     * The kind to suggest today: the one that most often left things lighter than they get by themselves, if it did so
     * more often than not and clearly (a quarter of a mood or more). Nothing without a control.
     */
    fun suggestion(f: Finding): String? = if (!f.controlled) null else
        f.kinds.firstOrNull { it.type !in NOT_SUGGESTED }?.takeIf { k -> (k.beyond ?: 0) * 2 > k.count && (k.effect ?: 0f) >= 0.25f }?.type

    /** Kinds the app does not offer as a sit to start (the 50-50 comes by its own reminders). */
    private val NOT_SUGGESTED = setOf("fifty")
}
