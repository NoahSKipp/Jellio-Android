package com.jellio.android.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingListLogicTest {
    private val guid = "0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9"
    private val plain = "0a1b2c3d4e5f60718293a4b5c6d7e8f9"

    private fun entry(key: String, kind: String) = ReadingEntryDto(Key = key, Kind = kind, Title = key)

    @Test
    fun itemKeyIsLowercaseWithoutDashes() {
        assertEquals("i:$plain", itemReadingKey(guid))
        assertTrue(isLibraryItemId(guid))
        assertFalse(isLibraryItemId("12345"))
        assertFalse(isLibraryItemId(null))
    }

    @Test
    fun itemTargetFollowsTheItemType() {
        val ebook = readingTargetForItem(guid, "Book", " Dune ", null, manga = false)!!
        assertEquals("i:$plain", ebook.key)
        assertEquals(READING_KIND_EBOOK, ebook.kind)
        assertEquals(plain, ebook.itemId)
        assertEquals("Dune", ebook.title)
        assertNull(ebook.author)
        assertEquals(READING_KIND_MANGA, readingTargetForItem(guid, "Book", "Ch 1", null, manga = true)!!.kind)
        assertEquals(READING_KIND_AUDIOBOOK, readingTargetForItem(guid, "AudioBook", "Dune", "Frank", manga = true)!!.kind)
        assertNull(readingTargetForItem(guid, "Movie", "x", null, false))
        assertNull(readingTargetForItem("chapter-7", "Book", "x", null, false))
    }

    @Test
    fun seriesTargetKeepsOnlyWhatTheServerAccepts() {
        val target = readingTargetForSeries(
            seriesKey = "one piece",
            title = "One Piece",
            author = " Oda ",
            mangaId = 12,
            itemId = guid,
            coverUrl = "https://img.example/cover.jpg",
        )!!
        assertEquals("s:one piece", target.key)
        assertEquals(READING_KIND_MANGA, target.kind)
        assertEquals(12, target.mangaId)
        assertEquals(plain, target.itemId)
        assertEquals("Oda", target.author)
        assertEquals("https://img.example/cover.jpg", target.coverUrl)
        assertEquals("s:berserk", readingTargetForSeries("s:berserk", "Berserk")!!.key)
        assertNull(readingTargetForSeries("berserk", "Berserk", coverUrl = "http://img.example/c.jpg")!!.coverUrl)
        assertNull(readingTargetForSeries("berserk", "Berserk", coverUrl = "https://" + "a".repeat(300))!!.coverUrl)
        assertNull(readingTargetForSeries("berserk", "Berserk", mangaId = 0)!!.mangaId)
        assertNull(readingTargetForSeries("berserk", "Berserk", itemId = "chapter-7")!!.itemId)
        assertNull(readingTargetForSeries("berserk", " "))
        assertNull(readingTargetForSeries(" ", "Berserk"))
        assertEquals("s:a", readingTargetForSeries("a", "A")!!.key)
        assertNull(readingTargetForSeries("x".repeat(300), "A"))
        assertNull(readingTargetForSeries(" padded", "A"))
    }

    @Test
    fun workTargetNeedsAProviderPrefixedId() {
        val book = readingTargetForWork("hc:12345", "Dune", "Frank Herbert", "https://covers.example/1.jpg", audiobook = false)!!
        assertEquals("w:hc:12345", book.key)
        assertEquals(READING_KIND_EBOOK, book.kind)
        assertNull(book.itemId)
        assertEquals(READING_KIND_AUDIOBOOK, readingTargetForWork("hc:1", "Dune", null, null, audiobook = true)!!.kind)
        assertNull(readingTargetForWork("12345", "Dune", null, null, false))
        assertNull(readingTargetForWork("hc:with space", "Dune", null, null, false))
        assertNull(readingTargetForWork("h:1", "Dune", null, null, false))
        assertNull(readingTargetForWork("hc:1", "", null, null, false))
        assertTrue(isValidWorkKey("w:ol:OL45883W"))
        assertFalse(isValidWorkKey("w:ol:" + "a".repeat(121)))
    }

    @Test
    fun targetBecomesTheRequestBody() {
        val body = readingTargetForSeries("berserk", "Berserk", mangaId = 4)!!.toAddBody()
        assertEquals("s:berserk", body.Key)
        assertEquals("manga", body.Kind)
        assertEquals(4, body.MangaId)
        assertNull(body.ItemId)
    }

    @Test
    fun sectionsSplitBooksAndMangaFromAudiobooks() {
        val sections = splitReadingEntries(
            listOf(entry("a", "ebook"), entry("b", "audiobook"), entry("c", "manga"), entry("d", "audiobook")),
        )
        assertEquals(listOf("a", "c"), sections.reading.map { it.Key })
        assertEquals(listOf("b", "d"), sections.listening.map { it.Key })
        val empty = splitReadingEntries(emptyList())
        assertTrue(empty.reading.isEmpty() && empty.listening.isEmpty())
    }

    @Test
    fun entriesOpenLikeTheShelvesOpenThem() {
        assertEquals(ReadingOpen.Item(plain, "audiobook"), readingOpenFor("i:$plain", "audiobook", guid, "T"))
        assertEquals(ReadingOpen.Item(plain, "ebook"), readingOpenFor("i:$plain", "ebook", null, "T"))
        assertEquals(ReadingOpen.Series("one piece"), readingOpenFor("s:one piece", "manga", null, "One Piece"))
        assertEquals(ReadingOpen.Work("Dune", "ebook"), readingOpenFor("w:hc:1", "ebook", null, "Dune"))
        assertEquals(ReadingOpen.Work("Dune", "ebook"), readingOpenFor("i:nonsense", "ebook", null, "Dune"))
    }

    @Test
    fun titlesMatchIgnoringAccentsAndPunctuation() {
        assertTrue(sameReadingTitle("Les Misérables", "les miserables"))
        assertTrue(sameReadingTitle("Dune: Messiah", "Dune Messiah"))
        assertFalse(sameReadingTitle("Dune", "Dune Messiah"))
        assertFalse(sameReadingTitle("", ""))
        assertFalse(sameReadingTitle(null, "x"))
    }

    @Test
    fun coversResolveAgainstTheServer() {
        assertNull(readingCoverUrl(null, "https://s", "t"))
        assertNull(readingCoverUrl("", "https://s", "t"))
        assertEquals(
            "https://s/Jellio/books/item/a/cover?ApiKey=t&api_key=t",
            readingCoverUrl("/Jellio/books/item/a/cover", "https://s/", "t"),
        )
        assertEquals(
            "https://s/Jellio/manga/thumbnail/3?title=X&ApiKey=t&api_key=t",
            readingCoverUrl("/Jellio/manga/thumbnail/3?title=X", "https://s", "t"),
        )
        assertEquals("https://s/x", readingCoverUrl("/x", "https://s", null))
        assertEquals("https://img.example/c.jpg", readingCoverUrl("https://img.example/c.jpg", "https://s", "t"))
        assertNull(readingCoverUrl("http://img.example/c.jpg", "https://s", "t"))
        assertNull(readingCoverUrl("/x", null, "t"))
    }

    @Test
    fun refusalsMapToMessages() {
        assertEquals("list_full", readingErrorCode("""{"Error":"list_full"}"""))
        assertEquals("invalid_cover", readingErrorCode("""{ "Error" : "invalid_cover" }"""))
        assertNull(readingErrorCode("not json"))
        assertNull(readingErrorCode(null))
        assertEquals("reading_toast_full", readingErrorKey(409, "list_full"))
        assertEquals("reading_toast_error", readingErrorKey(409, "other"))
        assertEquals("reading_toast_error", readingErrorKey(400, "invalid_key"))
        assertEquals("reading_toast_error", readingErrorKey(500, null))
    }

    @Test
    fun labelsFollowKindAndState() {
        assertEquals("reading_kind_ebook", readingKindLabelKey("ebook"))
        assertEquals("reading_kind_audiobook", readingKindLabelKey("audiobook"))
        assertEquals("reading_kind_manga", readingKindLabelKey("manga"))
        assertEquals("reading_kind_ebook", readingKindLabelKey(null))
        assertEquals("reading_remove", readingToggleLabelKey(true))
        assertEquals("reading_add", readingToggleLabelKey(false))
        assertEquals("reading_toast_added", readingToggleSuccess(true).messageKey)
        assertEquals("reading_toast_removed", readingToggleSuccess(false).messageKey)
        assertFalse(readingToggleFailure(true, "reading_toast_error").ok)
        assertTrue(readingToggleFailure(true, "reading_toast_error").onList)
    }
}
