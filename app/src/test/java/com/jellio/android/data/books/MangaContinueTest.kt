package com.jellio.android.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaContinueTest {
    private fun prefs(key: String, at: String) = mapOf(key to SeriesPrefsDto(ContinueDismissedAt = at))

    @Test
    fun dismissedUntilTheSeriesIsReadAgain() {
        val stored = prefs("s:one-piece", mangaDismissalMarker("2026-01-01"))
        assertTrue(isMangaContinueDismissed(stored, "one-piece", "2026-01-01"))
        assertFalse(isMangaContinueDismissed(stored, "one-piece", "2026-02-01"))
        assertFalse(isMangaContinueDismissed(stored, "bleach", "2026-01-01"))
    }

    @Test
    fun theShelfKeyWorksWithOrWithoutItsPrefix() {
        assertEquals("s:one-piece", mangaShelfKey("one-piece"))
        assertEquals("s:one-piece", mangaShelfKey("s:one-piece"))
        val stored = prefs("s:one-piece", "2026-01-01")
        assertTrue(isMangaContinueDismissed(stored, "s:one-piece", "2026-01-01"))
    }

    @Test
    fun missingLastReadIsAStableMarker() {
        val stored = prefs("s:k", mangaDismissalMarker(null))
        assertTrue(isMangaContinueDismissed(stored, "k", null))
        assertEquals("-", mangaDismissalMarker(""))
    }

    @Test
    fun noStoredValueMeansNotDismissed() {
        assertFalse(isMangaContinueDismissed(emptyMap(), "k", null))
        assertFalse(isMangaContinueDismissed(mapOf("s:k" to SeriesPrefsDto()), "k", null))
    }
}
