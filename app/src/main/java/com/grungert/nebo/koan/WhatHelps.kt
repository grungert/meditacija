package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.Session
import java.util.Calendar

/**
 * What helps, from the person's own notes: for each session with a note before and a note after it, whether the
 * moods after were lighter than before. Only what enough sessions show is said; until then, how far there is to go.
 *
 * "Before" is the note asked for before the sit, or else the last note of the two hours before it (how the person was
 * as they sat down). A mood is the one chosen, or else the one the words say ("umorna sam" counts as tired).
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

    /** One session with a note before and after: its kind, when it was, and how much lighter it left (after − before). */
    class Shift(val type: String, val part: Int, val shift: Float)

    fun pairs(sessions: List<Session>, entries: List<Entry>): List<Shift> {
        val bySession = entries.filter { it.sessionId != null }.groupBy { it.sessionId!! }
        // the notes that can stand for "before", each with its weight, read once (not once for every session)
        val loose = entries.filter { it.sessionId == null && it.kind !in NOT_BEFORE }.sortedBy { it.createdAt }
            .mapNotNull { e -> weight(moodsOf(e))?.let { e.createdAt to it } }
        return sessions.mapNotNull { s ->
            val notes = bySession[s.id] ?: return@mapNotNull null
            val after = weight(notes.filter { it.kind == "after" }.flatMap { moodsOf(it) }) ?: return@mapNotNull null
            val asked = weight(notes.filter { it.kind == "before" }.flatMap { moodsOf(it) })
            val before = asked ?: loose.lastOrNull { it.first in (s.startedAt - BEFORE_WINDOW)..s.startedAt }?.second ?: return@mapNotNull null
            Shift(s.type, part(s.startedAt), after - before)
        }
    }

    /** Morning, day, evening, night. */
    fun part(t: Long): Int = when (Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3
    }

    /** For one kind of session: how many had both notes, in how many it was lighter after, and by how much on average. */
    class Kind(val type: String, val count: Int, val lighter: Int, val shift: Float)

    /** What can be said: per kind with enough sessions, best first, and a time of day that clearly helps more for one kind. */
    class Finding(val kinds: List<Kind>, val partFor: Triple<String, Int, Int>?, val pairs: Int)

    fun find(sessions: List<Session>, entries: List<Entry>): Finding {
        val ps = pairs(sessions, entries)
        val kinds = ps.groupBy { it.type }.filter { it.value.size >= ENOUGH }
            .map { (t, l) -> Kind(t, l.size, l.count { it.shift > 0f }, l.map { it.shift }.average().toFloat()) }
            .sortedWith(compareByDescending<Kind> { it.lighter.toFloat() / it.count }.thenByDescending { it.shift })
        // a part of the day that helps more for a kind: at least four sessions on each side, and a clear gap between them
        var partFor: Triple<String, Int, Int>? = null
        for (k in kinds) {
            val byPart = ps.filter { it.type == k.type }.groupBy { it.part }.filter { it.value.size >= 4 }
                .mapValues { (_, l) -> l.map { it.shift }.average().toFloat() }
            if (byPart.size < 2) continue
            val best = byPart.maxBy { it.value }; val worst = byPart.minBy { it.value }
            if (best.value - worst.value >= 0.5f) { partFor = Triple(k.type, best.key, worst.key); break }
        }
        return Finding(kinds, partFor, ps.size)
    }

    /** The kind to suggest today: the one that most often left things lighter, if it did so more often than not. */
    fun suggestion(f: Finding): String? = f.kinds.firstOrNull()?.takeIf { it.lighter * 2 > it.count && it.shift > 0f }?.type
}
