package com.jellio.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidsTest {
    @Test
    fun dashedAndPlainGuidsMatch() {
        assertEquals(
            normalizeGuid("0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9"),
            normalizeGuid("0a1b2c3d4e5f60718293a4b5c6d7e8f9"),
        )
    }
}
