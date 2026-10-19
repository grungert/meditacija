package com.grungert.nebo.koan

import com.grungert.nebo.data.Entry
import java.util.Calendar

/**
 * Coming back the next day: after a note that says it was hard, the app asks once, the following day, how it is now.
 * The answer is kept as a note that follows the first one, so the two can be read as a pair.
 */
object FollowUp {
    private val HEAVY = setOf("tužno", "ljuto", "zabrinuto", "nemirno")
    private val LIGHT = setOf("mirno", "zahvalno", "radosno")

    /** A note that says it was hard: heavier moods than light ones, or a RAIN, which is for a hard feeling. */
    fun isHard(e: Entry): Boolean {
        if (e.kind == "followup" || e.kind == "week") return false
        if (e.kind == "rain") return true
        val heavy = e.moods.count { it in HEAVY }
        return heavy > 0 && heavy > e.moods.count { it in LIGHT }
    }

    /**
     * Yesterday's hard note to come back to today, if there is one that has not been answered: the latest of them.
     * [entries] are the notes from yesterday on; [today] is local midnight today.
     */
    fun pending(entries: List<Entry>, today: Calendar): Entry? {
        val start = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        // once a day at most: after today's answer there is no second question
        if (entries.any { it.kind == "followup" && it.createdAt >= today.timeInMillis }) return null
        val answered = entries.mapNotNull { it.follows }.toSet()
        return entries.filter { it.createdAt in start until today.timeInMillis && isHard(it) && it.id !in answered }.maxByOrNull { it.createdAt }
    }

    /** When to ask today: at the hour the note was written, but not before ten in the morning or after eight in the evening. */
    fun askAt(note: Entry, today: Calendar): Long {
        val hour = Calendar.getInstance().apply { timeInMillis = note.createdAt }.get(Calendar.HOUR_OF_DAY).coerceIn(10, 20)
        return (today.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0) }.timeInMillis
    }

    /** What the hard note was about, in a few words: its theme, or the start of its words. */
    fun about(e: Entry): String? = e.themes.firstOrNull()?.let { THEME_NAMES[it] }
        ?: e.words.split(Regex("\\s+")).filter { it.isNotBlank() }.take(6).joinToString(" ").ifBlank { null }
}
