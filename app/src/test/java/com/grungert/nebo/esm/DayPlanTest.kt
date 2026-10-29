package com.grungert.nebo.esm

import com.grungert.nebo.esm.DayPlan.Fixed
import com.grungert.nebo.esm.DayPlan.Kind
import com.grungert.nebo.esm.DayPlan.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

class DayPlanTest {
    private fun at(hour: Int, minute: Int = 0, day: Int = 28, month: Int = Calendar.OCTOBER) =
        Calendar.getInstance().apply { clear(); set(2026, month, day, hour, minute) }.timeInMillis
    private val morning = at(0, 1)

    /** Every kind on: the reminder at 20:00, the evening at 21:00, the question at 8, yesterday's hard day at 15, five
     *  questions of the moment 9–21 and three 50-50 9–20. */
    private fun everything(day: Int = 28) = listOf(
        Fixed(Kind.REMIND, at(20, 0, day)), Fixed(Kind.EVENING, at(21, 0, day)), Fixed(Kind.DAILY, at(8, 0, day)), Fixed(Kind.FOLLOW, at(15, 0, day)),
    ) to listOf(Window(Kind.ESM, 5, at(9, 0, day), at(21, 0, day)), Window(Kind.FIFTY, 3, at(9, 0, day), at(20, 0, day)))

    private fun gapsHold(p: List<DayPlan.Planned>, sent: List<Long> = emptyList()) =
        (p.map { it.at } + sent).sorted().zipWithNext().all { (a, b) -> b - a >= DayPlan.GAP }

    @Test
    fun theBudgetHoldsAcrossAllKinds() {
        val (fixed, windows) = everything()
        for (budget in DayPlan.BUDGETS) {
            val p = DayPlan.plan(fixed, windows, budget, emptyList(), morning, Random(budget))
            assertEquals(budget, p.size)
            assertTrue(gapsHold(p))
        }
    }

    @Test
    fun theLowestGoFirst() {
        val (fixed, windows) = everything()
        // five: the reminder, one question of the moment, yesterday's hard day, then two more questions; no evening, no 50-50
        val p = DayPlan.plan(fixed, windows, 5, emptyList(), morning, Random(1))
        assertEquals(mapOf(Kind.REMIND to 1, Kind.ESM to 3, Kind.FOLLOW to 1), p.groupingBy { it.kind }.eachCount())
        // ten: everything but two of the 50-50
        val ten = DayPlan.plan(fixed, windows, 10, emptyList(), morning, Random(1)).groupingBy { it.kind }.eachCount()
        assertEquals(1, ten[Kind.FIFTY]); assertEquals(1, ten[Kind.EVENING]); assertEquals(1, ten[Kind.DAILY])
    }

    @Test
    fun aQuestionOfTheMomentAlwaysStays() {
        // the smallest budget, with the reminder, the week and the hard day all wanting a place
        val fixed = listOf(Fixed(Kind.REMIND, at(20)), Fixed(Kind.WEEK, at(19)), Fixed(Kind.FOLLOW, at(15)))
        val p = DayPlan.plan(fixed, listOf(Window(Kind.ESM, 4, at(9), at(21))), 3, emptyList(), morning, Random(2))
        assertEquals(setOf(Kind.ESM, Kind.WEEK, Kind.REMIND), p.map { it.kind }.toSet())
        assertEquals(3, p.size)
    }

    @Test
    fun nothingLeavesItsWindow() {
        val (fixed, windows) = everything()
        repeat(50) { seed ->
            val p = DayPlan.plan(fixed, windows, 10, emptyList(), morning, Random(seed))
            for (x in p) when (x.kind) {
                Kind.ESM -> assertTrue(x.at in at(9)..at(21))
                Kind.FIFTY -> assertTrue(x.at in at(9)..at(20))
                else -> {}
            }
            assertTrue(gapsHold(p))
        }
    }

    @Test
    fun aSetMessageNearAHigherOneMovesOn() {
        // the evening look back at the very minute of the reminder comes three quarters of an hour after it
        val p = DayPlan.plan(listOf(Fixed(Kind.REMIND, at(20)), Fixed(Kind.EVENING, at(20))), emptyList(), 5, emptyList(), morning)
        assertEquals(listOf(at(20), at(20, 45)), p.map { it.at })
    }

    @Test
    fun aChangeDuringTheDayPlansOnlyTheRest() {
        val (fixed, windows) = everything()
        // at two in the afternoon, three already came (at 8, 10 and 13): two more at most, none before now, none near those
        val sent = listOf(at(8), at(10), at(13))
        val p = DayPlan.plan(fixed, windows, 5, sent, at(14), Random(3))
        assertEquals(2, p.size)
        assertTrue(p.all { it.at > at(14) })
        assertTrue(gapsHold(p, sent))
        // the budget spent: only the reminder the person set
        assertEquals(listOf(Kind.REMIND), DayPlan.plan(fixed, windows, 3, sent, at(14), Random(3)).map { it.kind })
    }

    @Test
    fun aClockChangeDayKeepsTheWindowAndTheGaps() {
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Podgorica"))
        try {
            // 25 October 2026: the clocks go back an hour at three in the morning
            val (fixed, windows) = everything(25)
            val p = DayPlan.plan(fixed, windows, 10, emptyList(), at(0, 1, 25), Random(4))
            assertEquals(10, p.size)
            assertTrue(gapsHold(p))
            assertTrue(p.filter { it.kind == Kind.ESM }.all { it.at in at(9, 0, 25)..at(21, 0, 25) })
        } finally { TimeZone.setDefault(tz) }
    }

    @Test
    fun aPlanIsWrittenAndReadBack() {
        val (fixed, windows) = everything()
        val p = DayPlan.plan(fixed, windows, 7, emptyList(), morning, Random(5))
        assertEquals(p, DayPlan.decode(DayPlan.encode(p)))
    }

    @Test
    fun aMessageWithNoRoomGivesItsPlaceToTheNext() {
        // at 19:37: the questions of the moment have only 19:47–21:00, and the reminder at 20:30 leaves no room 45 minutes
        // from it; the evening look back takes the place they cannot (moved on after the reminder)
        val fixed = listOf(Fixed(Kind.REMIND, at(20, 30)), Fixed(Kind.EVENING, at(21)))
        val windows = listOf(Window(Kind.ESM, 5, at(9), at(21)))
        val p = DayPlan.plan(fixed, windows, 5, emptyList(), at(19, 37), Random(6))
        assertEquals(listOf(Kind.REMIND, Kind.EVENING), p.map { it.kind })
        assertEquals(at(21, 15), p[1].at)
    }

    @Test
    fun theQuestionsOfTheMomentAreSpreadOverTheWindow() {
        // five questions 9–21: one in each part of two hours and twenty-four minutes, whatever the draw
        val windows = listOf(Window(Kind.ESM, 5, at(9), at(21)))
        val part = (at(21) - at(9)) / 5
        repeat(200) { seed ->
            val p = DayPlan.plan(emptyList(), windows, 5, emptyList(), morning, Random(seed))
            assertEquals(5, p.size)
            assertEquals((0L until 5L).toList(), p.map { (it.at - at(9)) / part })
            assertTrue(gapsHold(p))
        }
        // three of them with the budget at three: still over the whole day, not bunched
        repeat(200) { seed ->
            val p = DayPlan.plan(emptyList(), windows, 3, emptyList(), morning, Random(seed))
            assertEquals(listOf(0L, 1L, 2L), p.map { (it.at - at(9)) / ((at(21) - at(9)) / 3) })
        }
    }

    @Test
    fun aSetMessageMovesOnAnHourAtMost() {
        // the reminder at 20:00 and the week at 19:00 leave the evening look back at 20:15 its first free moment at
        // 20:45 — within the hour; the question of the day at 19:15 would find room only at 20:45 too, an hour and a
        // half on, so it does not come and the budget goes to the 50-50
        val fixed = listOf(Fixed(Kind.REMIND, at(20)), Fixed(Kind.WEEK, at(19)), Fixed(Kind.EVENING, at(20, 15)),
            Fixed(Kind.DAILY, at(19, 15)))
        val p = DayPlan.plan(fixed, listOf(Window(Kind.FIFTY, 1, at(9), at(12))), 4, emptyList(), morning, Random(7))
        assertEquals(listOf(Kind.FIFTY, Kind.WEEK, Kind.REMIND, Kind.EVENING), p.map { it.kind })
        assertEquals(at(20, 45), p.last().at)
    }
}
