package com.jellio.android.data.books

import com.jellio.android.data.model.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BooksAndFormatEnginesTest {

    @Test
    fun naturalOrderComparatorSortsPageFilenamesNumerically() {
        val pages = listOf("page10.jpg", "page2.jpg", "page1.jpg", "001_cover.png", "page20.jpg")
        val sorted = pages.sortedWith(NaturalOrderComparator)
        assertEquals(
            listOf("001_cover.png", "page1.jpg", "page2.jpg", "page10.jpg", "page20.jpg"),
            sorted,
        )
    }

    @Test
    fun epubLocatorFormattingAndParsingRoundTrips() {
        val formatted = EpubBookEngine.formatLocator(spineIndex = 4, offset = 12)
        assertEquals("epub:4:12", formatted)

        val parsed = EpubBookEngine.parseLocator(formatted)
        assertEquals(4 to 12, parsed)

        assertNull(EpubBookEngine.parseLocator(null))
        assertNull(EpubBookEngine.parseLocator(""))
        assertNull(EpubBookEngine.parseLocator("invalid"))
    }

    @Test
    fun comicAndMangaDetectionMatchesExtensionsAndGenres() {
        val cbzItem = BaseItemDto(Id = "1", Name = "Chapter 1", Path = "/books/One Piece/Ch01.cbz")
        assertTrue(BooksRepository.isComicOrManga(cbzItem))

        val epubItem = BaseItemDto(Id = "2", Name = "Dune", Path = "/books/Dune.epub")
        assertFalse(BooksRepository.isComicOrManga(epubItem))

        val mangaGenreItem = BaseItemDto(Id = "3", Name = "Berserk", Genres = listOf("Action", "Manga"))
        assertTrue(BooksRepository.isComicOrManga(mangaGenreItem))
    }

    @Test
    fun normalizeIdStripsHyphensAndLowercases() {
        assertEquals("a1b2c3d4e5", BooksRepository.normalizeId("A1B2-C3D4-E5"))
    }
}
