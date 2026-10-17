package com.grungert.nebo.koan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MindHabitsTest {
    @Test
    fun findsTheHabits() {
        val h = MindHabits.habits("Moram sve sam da završim, a šta ako ne stignem do petka")
        assertTrue(h.toString(), "moranje" in h && "staako" in h)
        assertTrue("samokritika" in MindHabits.habits("Glup sam, opet sam zakasnio"))
        assertTrue("tudjemisli" in MindHabits.habits("Sigurno misli da sam lijen"))
        assertTrue("uvijek" in MindHabits.habits("Nikad mi ništa ne uspije"))
    }

    @Test
    fun aTurnOfPhraseIsNotAHabit() {
        assertFalse("uvijek" in MindHabits.habits("Kao i uvijek, pijem kafu na terasi"))
        assertTrue(MindHabits.habits("Šetao sam pored rijeke, bilo je lijepo").isEmpty())
    }

    @Test
    fun whereTheMindIs() {
        assertEquals(MindHabits.Time.PAST, MindHabits.time("Sjećam se kako smo nekad svi bili zajedno"))
        assertEquals(MindHabits.Time.FUTURE, MindHabits.time("Sutra imam rok i brinem da neću stići"))
        assertEquals(MindHabits.Time.NOW, MindHabits.time("Sada dišem polako i čujem kišu"))
        // an ordinary day told in the past tense is not a mind that keeps going back
        assertNull(MindHabits.time("Razgovarao sam sa sestrom."))
    }
}
