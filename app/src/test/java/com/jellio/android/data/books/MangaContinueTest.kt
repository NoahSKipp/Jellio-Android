package com.jellio.android.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaContinueTest {
    @Test
    fun dismissedUntilTheSeriesIsReadAgain() {
        val entries = withMangaDismissal(emptySet(), "one-piece", "2026-01-01")
        assertTrue(isMangaContinueDismissed(entries, "one-piece", "2026-01-01"))
        assertFalse(isMangaContinueDismissed(entries, "one-piece", "2026-02-01"))
        assertFalse(isMangaContinueDismissed(entries, "bleach", "2026-01-01"))
    }

    @Test
    fun dismissingAgainReplacesTheOldEntry() {
        val first = withMangaDismissal(emptySet(), "one-piece", "2026-01-01")
        val second = withMangaDismissal(first, "one-piece", "2026-02-01")
        assertEquals(1, second.size)
        assertTrue(isMangaContinueDismissed(second, "one-piece", "2026-02-01"))
    }

    @Test
    fun missingLastReadIsAStableKey() {
        val entries = withMangaDismissal(emptySet(), "k", null)
        assertTrue(isMangaContinueDismissed(entries, "k", null))
    }
}
