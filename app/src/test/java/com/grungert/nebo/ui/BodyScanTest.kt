package com.grungert.nebo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyScanTest {
    private val box = Rect(100f, 100f, 100f + 420f, 100f + 1000f)
    private fun at(x: Float, y: Float) = Offset(box.left + x * box.width, box.top + y * box.height)

    @Test
    fun theScanGoesFromTheFeetToTheWholeBody() {
        assertEquals(0, scanStage(0f, 600).first)
        assertEquals(BODY_REGIONS.lastIndex, scanStage(599f, 600).first)
        // open-ended: 45 seconds for each part
        assertEquals(1, scanStage(46f, 0).first)
        assertEquals(BODY_REGIONS.lastIndex, scanStage(10_000f, 0).first)
    }

    @Test
    fun aTouchFindsItsPart() {
        assertEquals("ramena", regionAt(at(0.36f, 0.285f), box, "stopala"))
        assertEquals("stomak", regionAt(at(0.5f, 0.555f), box, "stopala"))
        // the back is reached through the torso, while the scan is on it
        assertEquals("leđa", regionAt(at(0.5f, 0.47f), box, "leđa"))
        assertEquals("ruke", regionAt(at(0.77f, 0.55f), box, "glava"))
        assertEquals("noge", regionAt(at(0.2f, 0.76f), box, "glava"))
        assertNull(regionAt(at(0.98f, 0.04f), box, "noge"))
    }

    @Test
    fun partsAndNoteLabelsMeetInTheMiddle() {
        assertEquals("ramena", regionOfLabel("napeta ramena"))
        assertEquals("grudi", regionOfLabel("lupanje srca"))
        assertNull(regionOfLabel("toplina"))
    }
}
