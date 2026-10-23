package com.grungert.nebo.ui

import com.grungert.nebo.data.Entry
import com.grungert.nebo.data.Session
import com.grungert.nebo.koan.Lexicon
import com.grungert.nebo.koan.MOODS
import com.grungert.nebo.koan.MindHabits
import java.util.Calendar

/** The spans the overview can look at; the custom one is chosen on a calendar. */
enum class RangeKind(val pill: String) { WEEK("Sedmica"), MONTH("Mjesec"), QUARTER("3 mjeseca"), YEAR("Godina"), CUSTOM("Period") }

/** A stretch of time in the overview's charts: a day, a week or a month, with its label under the chart. */
class Bucket(val from: Long, val to: Long, val label: String)

private const val DAY = 86_400_000L
private val WEEKDAY_LETTER = mapOf(
    Calendar.MONDAY to "P", Calendar.TUESDAY to "U", Calendar.WEDNESDAY to "S", Calendar.THURSDAY to "Č",
    Calendar.FRIDAY to "P", Calendar.SATURDAY to "S", Calendar.SUNDAY to "N",
)
private val MONTH_SHORT = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "avg", "sep", "okt", "nov", "dec")

private fun midnight(t: Long) = Calendar.getInstance().apply {
    timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}
private fun Calendar.plusDays(n: Int) = (clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, n) }
private fun Calendar.plusMonths(n: Int) = (clone() as Calendar).apply { add(Calendar.MONTH, n) }
private fun dm(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { "${it.get(Calendar.DAY_OF_MONTH)}.${it.get(Calendar.MONTH) + 1}." }

/**
 * What the overview looks at: from (inclusive) to (exclusive) midnight, on the person's own calendar, so a week with a
 * clock change is still Monday to Monday. The week is this calendar week; the month and three months end with today;
 * the year is the last twelve whole months with this one.
 */
class StatsRange(val kind: RangeKind, val from: Long, val to: Long) {
    val days get() = ((to - from + DAY / 2) / DAY).toInt()

    /** "Ova sedmica", "Posljednjih 30 dana", or the dates of a chosen period. */
    val title: String get() = when (kind) {
        RangeKind.WEEK -> "Ova sedmica"
        RangeKind.MONTH -> "Posljednjih 30 dana"
        RangeKind.QUARTER -> "Posljednja 3 mjeseca"
        RangeKind.YEAR -> "Posljednjih 12 mjeseci"
        RangeKind.CUSTOM -> "${dm(from)} – ${dm(to - 1)}"
    }

    /** The same span just before this one: last week, the 30 days before, and so on. */
    fun previous(): StatsRange {
        val f = midnight(from)
        return when (kind) {
            RangeKind.WEEK -> StatsRange(kind, f.plusDays(-7).timeInMillis, from)
            RangeKind.YEAR -> StatsRange(kind, f.plusMonths(-12).timeInMillis, from)
            else -> StatsRange(kind, f.plusDays(-days).timeInMillis, from)
        }
    }

    /**
     * The stretches the charts are drawn in: days for up to a month (letters for a week), weeks for up to half a
     * year, months beyond. [coarse] asks for fewer, wider ones (for the moods, which need a few notes in each).
     */
    fun buckets(coarse: Boolean = false): List<Bucket> {
        val dayLimit = if (coarse) 10 else 31
        val weekLimit = if (coarse) 120 else 190
        val out = ArrayList<Bucket>()
        var c = midnight(from)
        when {
            days <= dayLimit -> while (c.timeInMillis < to) {
                val n = c.plusDays(1)
                out += Bucket(c.timeInMillis, n.timeInMillis, if (days <= 7) WEEKDAY_LETTER.getValue(c.get(Calendar.DAY_OF_WEEK)) else dm(c.timeInMillis))
                c = n
            }
            days <= weekLimit -> while (c.timeInMillis < to) {
                val n = c.plusDays(7)
                out += Bucket(c.timeInMillis, minOf(n.timeInMillis, to), dm(c.timeInMillis))
                c = n
            }
            else -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                while (c.timeInMillis < to) {
                    val n = c.plusMonths(1)
                    out += Bucket(maxOf(c.timeInMillis, from), minOf(n.timeInMillis, to), MONTH_SHORT[c.get(Calendar.MONTH)])
                    c = n
                }
            }
        }
        // a short stub at the end (two days of a week) would read as a sudden drop: it joins the stretch before it
        if (coarse && out.size > 1) {
            val last = out.last(); val prev = out[out.size - 2]
            if (last.to - last.from < (prev.to - prev.from) / 2) { out.removeAt(out.size - 1); out[out.size - 1] = Bucket(prev.from, last.to, prev.label) }
        }
        return out
    }

    /** This span and the ones before it, oldest first: for the bars of how it has gone over time. */
    fun history(n: Int): List<StatsRange> = generateSequence(this) { it.previous() }.take(n).toList().reversed()

    companion object {
        fun of(kind: RangeKind, now: Long = System.currentTimeMillis()): StatsRange {
            val today = midnight(now)
            val tomorrow = today.plusDays(1)
            return when (kind) {
                RangeKind.WEEK -> {
                    val mon = (today.clone() as Calendar).apply { firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
                    if (mon.after(today)) mon.add(Calendar.DAY_OF_YEAR, -7)
                    StatsRange(kind, mon.timeInMillis, mon.plusDays(7).timeInMillis)
                }
                RangeKind.MONTH -> StatsRange(kind, tomorrow.plusDays(-30).timeInMillis, tomorrow.timeInMillis)
                RangeKind.QUARTER -> StatsRange(kind, tomorrow.plusDays(-91).timeInMillis, tomorrow.timeInMillis)
                RangeKind.YEAR, RangeKind.CUSTOM -> {
                    val next = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.plusMonths(1)
                    StatsRange(RangeKind.YEAR, next.plusMonths(-12).timeInMillis, next.timeInMillis)
                }
            }
        }

        /** A chosen period, from the first day through the last, whole days. */
        fun custom(firstDay: Long, lastDay: Long) = StatsRange(RangeKind.CUSTOM, midnight(firstDay).timeInMillis, midnight(lastDay).plusDays(1).timeInMillis)
    }
}

class StatsData(
    val range: StatsRange,
    /** Minutes of all sessions; sessions and days counted only from half a minute up, as in the journal. */
    val minutes: Int, val sessions: Int, val notes: Int,
    val prevMinutes: Int, val prevSessions: Int, val prevNotes: Int,
    /** Days with a session, and the days of the span so far. */
    val practicedDays: Int, val daysSoFar: Int,
    /** Minutes in each stretch of the span; -1 for stretches still ahead. */
    val wave: List<Int>, val waveLabels: List<String>,
    /** The span and the ones before it: minutes in each. */
    val history: List<Int>, val historyLabels: List<String>,
    val dayParts: List<Int>, val dayPartSessions: List<Int>,
    val types: List<Pair<String, Int>>, val themes: List<Pair<String, Int>>,
    val promptsFired: Int, val promptsAnswered: Int,
    /** The moods in each wider stretch of the span. */
    val moodBuckets: List<Map<String, Int>>, val moodLabels: List<String>,
    /** How often each part of the body was named as tense: in scans (once a scan) and in notes' body point. */
    val tension: Map<String, Int> = emptyMap(),
    val scans: Int = 0,
    /** The habits of mind in the span's notes, the mind's time, and where it went during clouds sessions. */
    val habits: List<Pair<String, Int>> = emptyList(),
    val times: Map<MindHabits.Time, Int> = emptyMap(),
    val wander: List<Pair<String, Int>> = emptyList(),
    /** Waves ridden, per pull: how many, and how strong on average before and after. */
    val waves: List<WaveSummary> = emptyList(),
    /** What helps: sessions with a note before and after, and what they show. */
    val helps: com.grungert.nebo.koan.WhatHelps.Finding? = null,
    /** The first mood before and the first after a session, and how many sessions went that way. */
    val flows: List<Triple<String, String, Int>> = emptyList(),
    /** Sessions with a note both before and after. */
    val flowPairs: Int = 0,
    /** How much of the planned time is sat through: in the span, or since the start when the span holds too few. */
    val completion: com.grungert.nebo.koan.Practice.Completion? = null,
    val completionSinceStart: Boolean = false,
    /** Thoughts a minute in clouds sessions, from the first one up to the end of the span. */
    val clouds: com.grungert.nebo.koan.Practice.Clouds? = null,
)

private fun partOfDay(t: Long): Int {
    val h = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
    return when (h) { in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3 }
}

/** Everything the overview shows, for one span of time. */
fun computeStats(allSessions: List<Session>, allEntries: List<Entry>, prompts: List<com.grungert.nebo.data.Prompt>,
    range: StatsRange = StatsRange.of(RangeKind.WEEK), now: Long = System.currentTimeMillis()): StatsData {
    fun inRange(t: Long, r: StatsRange) = t >= r.from && t < r.to
    val sessions = allSessions.filter { inRange(it.startedAt, range) }
    val real = sessions.filter { it.actualSec >= 30 }
    val entries = allEntries.filter { inRange(it.createdAt, range) }
    val prev = range.previous()
    val prevSessions = allSessions.filter { inRange(it.startedAt, prev) }

    val buckets = range.buckets()
    val wave = buckets.map { b -> if (b.from > now) -1 else sessions.filter { it.startedAt >= b.from && it.startedAt < b.to }.sumOf { it.actualSec } / 60 }
    val history = range.history(if (range.kind == RangeKind.YEAR) 3 else if (range.kind == RangeKind.QUARTER) 4 else 8)
    val coarse = range.buckets(coarse = true)

    val themeCount = HashMap<String, Int>()
    for (e in entries) {
        val ts = (e.themes.ifEmpty { Lexicon.themes(e.words) } + e.moods.mapNotNull { m -> MOODS.firstOrNull { it.first == m }?.second }).toSet()
        ts.forEach { themeCount[it] = (themeCount[it] ?: 0) + 1 }
    }
    val flows = HashMap<Pair<String, String>, Int>()
    for (s in sessions) {
        val b = allEntries.firstOrNull { it.sessionId == s.id && it.kind == "before" }?.moods?.firstOrNull() ?: continue
        val a = allEntries.firstOrNull { it.sessionId == s.id && it.kind == "after" }?.moods?.firstOrNull() ?: continue
        flows[b to a] = (flows[b to a] ?: 0) + 1
    }
    val fired = prompts.filter { it.firedAt != null && inRange(it.firedAt, range) }
    val mind = entries.map { MindHabits.mindOf(it) }
    // a span with few sessions says little about sitting through: then everything up to its end
    val inSpan = com.grungert.nebo.koan.Practice.completion(sessions)
    val sinceStart = inSpan.count < 8
    val completion = if (sinceStart) com.grungert.nebo.koan.Practice.completion(allSessions.filter { it.startedAt < range.to }) else inSpan
    val dayOf = { t: Long -> midnight(t).timeInMillis }
    return StatsData(
        range = range,
        minutes = sessions.sumOf { it.actualSec } / 60, sessions = real.size, notes = entries.size,
        prevMinutes = prevSessions.sumOf { it.actualSec } / 60, prevSessions = prevSessions.count { it.actualSec >= 30 },
        prevNotes = allEntries.count { inRange(it.createdAt, prev) },
        practicedDays = real.map { dayOf(it.startedAt) }.distinct().size,
        daysSoFar = (((minOf(range.to, dayOf(now) + DAY)) - range.from + DAY / 2) / DAY).toInt().coerceIn(1, range.days),
        wave = wave, waveLabels = buckets.map { it.label },
        history = history.map { r -> (allSessions.filter { inRange(it.startedAt, r) }.sumOf { it.actualSec } + 59) / 60 },
        historyLabels = history.map { r -> if (r.kind == RangeKind.YEAR) Calendar.getInstance().apply { timeInMillis = r.from }.get(Calendar.YEAR).toString() else dm(r.from) },
        dayParts = IntArray(4).also { a -> sessions.forEach { a[partOfDay(it.startedAt)] += it.actualSec } }.map { (it + 59) / 60 },
        dayPartSessions = IntArray(4).also { a -> real.forEach { a[partOfDay(it.startedAt)]++ } }.toList(),
        types = real.groupingBy { it.type }.eachCount().toList().sortedByDescending { it.second },
        themes = themeCount.toList().sortedByDescending { it.second }.take(8),
        promptsFired = fired.size, promptsAnswered = fired.count { it.answeredEntryId != null },
        moodBuckets = coarse.map { b -> entries.filter { it.createdAt >= b.from && it.createdAt < b.to }.flatMap { it.moods }.groupingBy { it }.eachCount() },
        moodLabels = coarse.map { it.label },
        tension = tensionCounts(sessions, entries),
        scans = sessions.count { it.type == "bodyscan" },
        habits = MindHabits.HABITS.map { h -> h.id to mind.count { h.id in MindHabits.habits(it) } }.filter { it.second > 0 }.sortedByDescending { it.second },
        times = mind.mapNotNull { MindHabits.time(it) }.groupingBy { it }.eachCount(),
        wander = sessions.flatMap { it.wander }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second },
        waves = waveSummaries(sessions),
        helps = com.grungert.nebo.koan.WhatHelps.find(sessions, allEntries),
        flows = flows.entries.sortedByDescending { it.value }.map { Triple(it.key.first, it.key.second, it.value) },
        flowPairs = flows.values.sum(),
        completion = completion, completionSinceStart = sinceStart,
        clouds = com.grungert.nebo.koan.Practice.clouds(allSessions.filter { it.startedAt < range.to }),
    )
}
