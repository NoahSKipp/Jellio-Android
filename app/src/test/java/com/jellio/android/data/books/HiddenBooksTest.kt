package com.jellio.android.data.books

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenBooksTest {
    @Test
    fun onlyItemKeysCountAndIdsAreNormalised() {
        val ids = hiddenBookIds(listOf("i:ABCD-1234", "s:some-series", "i:ffff0000"))
        assertEquals(setOf(BooksRepository.normalizeId("ABCD-1234"), BooksRepository.normalizeId("ffff0000")), ids)
    }

    @Test
    fun nothingRemovedMeansNothingHidden() {
        assertEquals(emptySet<String>(), hiddenBookIds(emptyList()))
    }
}
