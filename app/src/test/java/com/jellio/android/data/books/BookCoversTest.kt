package com.jellio.android.data.books

import com.jellio.android.data.model.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Test

class BookCoversTest {
    private val server = "https://jf.example"

    @Test
    fun primaryImageUsesItemTagAndToken() {
        val item = BaseItemDto(Id = "abc", ImageTags = mapOf("Primary" to "t1"))
        assertEquals(
            "https://jf.example/Items/abc/Images/Primary?maxWidth=500&quality=90&tag=t1&ApiKey=k&api_key=k",
            bookCoverUrlFor(item, server, "k"),
        )
    }

    @Test
    fun albumImageIsUsedWhenItemHasNone() {
        val item = BaseItemDto(Id = "abc", AlbumId = "alb", AlbumPrimaryImageTag = "t2")
        assertEquals(
            "https://jf.example/Items/alb/Images/Primary?maxWidth=500&quality=90&tag=t2",
            bookCoverUrlFor(item, server, null),
        )
    }

    @Test
    fun fallsBackToCoverProxyWithNormalizedId() {
        val item = BaseItemDto(Id = "AB-CD")
        assertEquals(
            "https://jf.example/Jellio/books/item/abcd/cover?ApiKey=k&api_key=k",
            bookCoverUrlFor(item, server, "k"),
        )
    }
}
