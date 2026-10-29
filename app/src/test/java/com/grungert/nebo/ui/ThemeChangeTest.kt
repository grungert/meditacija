package com.grungert.nebo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeChangeTest {
    @Test
    fun aWeekWithMoreNotesIsNotMoreOfEverything() {
        // work in 4 of 10 notes last week, 8 of 20 this week: the same share, not "more"
        assertEquals(ThemeChange.SAME, themeChange(8, 20, 4, 10))
    }

    @Test
    fun oneNoteMoreIsNoChange() {
        assertEquals(ThemeChange.SAME, themeChange(3, 10, 2, 10))
    }

    @Test
    fun aClearChangeIsSaid() {
        assertEquals(ThemeChange.MORE, themeChange(6, 10, 2, 10))
        assertEquals(ThemeChange.LESS, themeChange(1, 10, 5, 10))
    }

    @Test
    fun newOnlyWhenItCameBackAndNothingWithoutAWeekBefore() {
        assertEquals(ThemeChange.NEW, themeChange(2, 10, 0, 10))
        assertNull(themeChange(1, 10, 0, 10))
        assertNull(themeChange(3, 10, 0, 0))
    }
}
