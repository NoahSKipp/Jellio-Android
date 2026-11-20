package com.jellio.android.data.books

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ChaptarrStatusTest {

    @Test
    fun noAnswerYetIsLoadingNotUnconfigured() {
        assertEquals(ChaptarrStatus.LOADING, resolveChaptarrStatus(null, null))
    }

    @Test
    fun failedRequestIsUnreachableNotUnconfigured() {
        assertEquals(ChaptarrStatus.UNREACHABLE, resolveChaptarrStatus(null, IOException("timeout")))
    }

    @Test
    fun serverAnswerDecidesConfigured() {
        assertEquals(
            ChaptarrStatus.CONFIGURED,
            resolveChaptarrStatus(BookRequestsResponseDto(Configured = true), null),
        )
        assertEquals(
            ChaptarrStatus.NOT_CONFIGURED,
            resolveChaptarrStatus(BookRequestsResponseDto(Configured = false), null),
        )
    }
}
