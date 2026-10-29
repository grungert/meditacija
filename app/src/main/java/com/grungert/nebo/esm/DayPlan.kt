package com.grungert.nebo.esm

import kotlin.random.Random

/**
 * One plan for the day's messages, all kinds together, within one budget. Seven kinds were each scheduled on their own
 * with no limit on the whole, which for an app about calm was too many and could not be foreseen.
 *
 * The plan sees the whole day: the messages at a set time (the reminder, the week, yesterday's hard day, the evening, the
 * question of the day) and those at random moments in a window (the questions "Kako si sada?" and the 50-50). When there
 * are more than the budget, the lowest in [Kind] order go. Any two messages are at least [GAP] apart: a random moment
 * is never drawn near another message, and a set one that falls near a higher one moves on after it (by an hour at
 * most, or it does not come). A random moment never leaves its window, and those of one kind are spread over it.
 */
object DayPlan {
    /** The kinds, highest first. The reminder and the week are what the person asked for at a set time. */
    enum class Kind { REMIND, WEEK, FOLLOW, ESM, EVENING, DAILY, FIFTY }

    const val GAP = 45 * 60_000L
    const val BUDGET = 5
    val BUDGETS = 3..10
    /** A random moment is not drawn sooner than this from now. */
    private const val LEAD = 10 * 60_000L
    /** A set message moves on at most this far past its time to keep the gap; further off, it is not sent that day. */
    const val LATE = 60 * 60_000L

    /** A message at a set time. */
    data class Fixed(val kind: Kind, val at: Long)
    /** [count] messages at random moments between [from] and [to]. */
    data class Window(val kind: Kind, val count: Int, val from: Long, val to: Long)
    data class Planned(val kind: Kind, val at: Long)

    /**
     * The rest of the day: what is still to come after [now], at most [budget] messages less those already [sent]
     * today (their times), highest kinds first. The reminder the person set is kept even when the budget is spent;
     * when the questions of the moment are on, at least one of them is kept, before the day after a hard one.
     */
    fun plan(fixed: List<Fixed>, windows: List<Window>, budget: Int, sent: List<Long>, now: Long, rnd: Random = Random): List<Planned> {
        val ahead = fixed.filter { it.at > now }
        val open = windows.map { w -> w.copy(from = maxOf(w.from, now + LEAD)) }.filter { it.count > 0 && it.to > it.from }
        // what is wanted, highest first: a set message carries its time, a random one its window
        val wanted = ArrayList<Any>()
        fun fixedOf(k: Kind) = ahead.filter { it.kind == k }.forEach { wanted += it }
        fun windowOf(k: Kind, skip: Int = 0, n: Int = Int.MAX_VALUE) =
            open.filter { it.kind == k }.forEach { w -> repeat(minOf(n, w.count - skip).coerceAtLeast(0)) { wanted += w } }
        fixedOf(Kind.REMIND); fixedOf(Kind.WEEK)
        windowOf(Kind.ESM, n = 1)
        fixedOf(Kind.FOLLOW)
        windowOf(Kind.ESM, skip = 1)
        fixedOf(Kind.EVENING); fixedOf(Kind.DAILY)
        windowOf(Kind.FIFTY)
        val room = maxOf(0, budget - sent.size)
        // the random moments of a kind that will come are spread: the window in as many equal parts, one drawn in each
        // (the parts in a random order, so the first placed is not always the morning's)
        val parts = open.associateWith { w -> ArrayDeque((0 until wanted.take(room).count { it === w }).shuffled(rnd)) }
        val partLen = parts.mapValues { (w, q) -> if (q.isEmpty()) 0L else (w.to - w.from) / q.size }

        val placed = ArrayList<Planned>()
        fun clear(t: Long) = sent.all { kotlin.math.abs(it - t) >= GAP } && placed.all { kotlin.math.abs(it.at - t) >= GAP }
        fun draw(from: Long, to: Long, tries: Int): Long? {
            repeat(tries) { val c = from + (rnd.nextDouble() * (to - from)).toLong(); if (clear(c)) return c }
            return null
        }
        // one after another, highest first, until the budget is spent; one that finds no room gives its place to the next
        for (w in wanted) {
            val reminder = w is Fixed && w.kind == Kind.REMIND
            // the reminder asked for at its minute stays, whatever came before it today
            if (placed.size >= room && !reminder) break
            when (w) {
                is Fixed -> {
                    var t = w.at
                    // the reminder keeps its minute; another set one near a message placed before it moves on after it,
                    // by an hour at most
                    if (!reminder) while (!clear(t) && t <= w.at + LATE) t += 5 * 60_000L
                    if (reminder || (clear(t) && t <= w.at + LATE)) placed += Planned(w.kind, t)
                }
                is Window -> {
                    var t = parts[w]?.removeFirstOrNull()?.let { i ->
                        val len = partLen.getValue(w)
                        draw(w.from + i * len, w.from + (i + 1) * len, 200)
                    }
                    // no room in its part (or one more than was counted): anywhere in the window
                    if (t == null) t = draw(w.from, w.to, 400)
                    // a crowded window: the first free moment on a five-minute step, if any
                    if (t == null) t = generateSequence(w.from) { it + 5 * 60_000L }.takeWhile { it <= w.to }.firstOrNull { clear(it) }
                    t?.let { placed += Planned(w.kind, it) }
                }
            }
        }
        return placed.sortedBy { it.at }
    }

    /** Written into the settings as "KIND@time;KIND@time". */
    fun encode(p: List<Planned>) = p.joinToString(";") { "${it.kind.name}@${it.at}" }
    fun decode(s: String): List<Planned> = s.split(';').filter { '@' in it }.mapNotNull { x ->
        val (k, t) = x.split('@', limit = 2)
        runCatching { Planned(Kind.valueOf(k), t.toLong()) }.getOrNull()
    }
}
