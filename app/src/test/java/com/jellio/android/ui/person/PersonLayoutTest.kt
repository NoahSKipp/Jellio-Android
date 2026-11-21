package com.jellio.android.ui.person

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonLayoutTest {
    @Test
    fun phonesGetThreeColumns() {
        assertEquals(3, creditGridColumns(360, 16, 10))
        assertEquals(3, creditGridColumns(599, 16, 10))
    }

    @Test
    fun wideScreensAdaptAndNeverDropBelowThree() {
        assertEquals(6, creditGridColumns(800, 48, 16))
        assertEquals(4, creditGridColumns(600, 48, 16))
        assertEquals(10, creditGridColumns(1280, 48, 16))
    }

    @Test
    fun compactBoundary() {
        assertTrue(isCompactWidth(599))
        assertFalse(isCompactWidth(600))
    }

    @Test
    fun bioToggleSurvivesExpansion() {
        assertTrue(bioOverflowMemo(expanded = false, previous = false, hasVisualOverflow = true))
        assertTrue(bioOverflowMemo(expanded = true, previous = true, hasVisualOverflow = false))
        assertFalse(bioOverflowMemo(expanded = false, previous = true, hasVisualOverflow = false))
        assertFalse(bioOverflowMemo(expanded = true, previous = false, hasVisualOverflow = false))
    }

    @Test
    fun batching() {
        assertEquals(10, initialCreditCount(10))
        assertEquals(24, initialCreditCount(100))
        assertEquals(48, nextCreditCount(24, 100))
        assertEquals(30, nextCreditCount(24, 30))
        assertEquals(0, initialCreditCount(0))
    }

    @Test
    fun loadMoreNearEnd() {
        // 4 leading items; 24 shown means credit indices 0..23 -> grid indices 4..27
        assertFalse(shouldLoadMoreCredits(10, 4, 24, 100))
        assertTrue(shouldLoadMoreCredits(24, 4, 24, 100))
        assertTrue(shouldLoadMoreCredits(27, 4, 24, 100))
        assertFalse(shouldLoadMoreCredits(27, 4, 24, 24))
        assertFalse(shouldLoadMoreCredits(0, 4, 24, 100))
    }

    @Test
    fun detectsMidWordBreaks() {
        val text = "Tatsumaru Tachibana"
        assertTrue(breaksMidWord(text, listOf(6, text.length)))
        assertFalse(breaksMidWord(text, listOf(10, text.length)))
        assertFalse(breaksMidWord(text, listOf(9, text.length)))
        assertFalse(breaksMidWord("Jean-Claude Van", listOf(5, 15)))
        assertFalse(breaksMidWord(text, listOf(text.length)))
    }

    @Test
    fun nameSizes() {
        assertEquals(NameBaseSp, initialNameSize("Tom Hanks", 220f), 0.001f)
        val big = initialNameSize("Gwendolyn Wolfeschlegelsteinhausenbergerdorff", 200f)
        assertEquals(NameMinSp, big, 0.001f)
        val mid = initialNameSize("Tatsuhiro Tachibana", 100f)
        assertTrue(mid < NameBaseSp && mid >= NameMinSp)
        assertEquals(NameMinSp, shrunkNameSize(16f)!!, 0.001f)
        assertNull(shrunkNameSize(NameMinSp))
        assertEquals(NameBaseSp * NameShrinkFactor, shrunkNameSize(NameBaseSp)!!, 0.001f)
    }
}
