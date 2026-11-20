package com.jellio.android.ui.books

import org.junit.Assert.assertEquals
import org.junit.Test

class MangaPrefetchTest {
    private val pages = (0..19).map { "p$it" }

    @Test
    fun fetchesTheNextThreePagesOnly() {
        assertEquals(listOf("p6", "p7", "p8"), prefetchTargets(pages, 5, listOf("n0")))
    }

    @Test
    fun addsNextChapterNearTheEnd() {
        assertEquals(listOf("p17", "p18", "p19", "n0", "n1"), prefetchTargets(pages, 16, listOf("n0", "n1")))
    }

    @Test
    fun lastPageOnlyWantsTheNextChapter() {
        assertEquals(listOf("n0"), prefetchTargets(pages, 19, listOf("n0")))
    }

    @Test
    fun emptyChapterHasNothingBeyondTheNextChapter() {
        assertEquals(emptyList<String>(), prefetchTargets(emptyList(), 0, emptyList()))
    }
}
