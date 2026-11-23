package com.jellio.android.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaSeriesLogicTest {

    private fun ch(
        n: Int,
        read: Boolean = false,
        progress: Boolean = false,
        downloaded: Boolean = false,
        queued: Boolean = false,
        bookmarked: Boolean = false,
    ) = ChapterFacts("c$n", n.toFloat(), n, read, progress, downloaded, queued, bookmarked)

    private fun series(readUpTo: Int, total: Int = 30) = (1..total).map { ch(it, read = it <= readUpTo) }.shuffled(java.util.Random(7))

    @Test
    fun nextChaptersStartAtTheResumePointAndSkipDownloaded() {
        val chapters = (1..30).map { ch(it, read = it <= 4, downloaded = it == 6) }
        assertEquals(listOf("c5"), chaptersToDownload(chapters, MangaDownloadChoice.NEXT_1, null))
        assertEquals(listOf("c5", "c7", "c8", "c9", "c10"), chaptersToDownload(chapters, MangaDownloadChoice.NEXT_5, null))
        assertEquals(10, chaptersToDownload(chapters, MangaDownloadChoice.NEXT_10, null).size)
        assertEquals(25, chaptersToDownload(chapters, MangaDownloadChoice.NEXT_25, null).size)
    }

    @Test
    fun nextChaptersSkipQueuedOnes() {
        val chapters = (1..10).map { ch(it, queued = it <= 2) }
        assertEquals(listOf("c3", "c4"), chaptersToDownload(chapters, MangaDownloadChoice.NEXT_5, null).take(2))
    }

    @Test
    fun nextStartsAtTheChapterInProgress() {
        val chapters = (1..10).map { ch(it, read = it <= 3, progress = it == 6) }
        assertEquals(listOf("c6", "c7"), chaptersToDownload(chapters, MangaDownloadChoice.NEXT_5, null).take(2))
    }

    @Test
    fun serverResumeChapterIsUsedWhenUnread() {
        val chapters = (1..10).map { ch(it, read = it <= 3) }
        assertEquals(listOf("c8", "c9"), chaptersToDownload(chapters, MangaDownloadChoice.NEXT_5, "c8").take(2))
        // A read resume chapter is ignored.
        assertEquals("c4", resumeTarget(chapters, "c2")?.key)
    }

    @Test
    fun nextDownloadsNothingWhenEverythingIsRead() {
        assertEquals(emptyList<String>(), chaptersToDownload(series(30), MangaDownloadChoice.NEXT_5, null))
    }

    @Test
    fun unreadBookmarkedAndAll() {
        val chapters = (1..6).map { ch(it, read = it <= 2, downloaded = it == 3, bookmarked = it == 2 || it == 5 || it == 3) }
        assertEquals(listOf("c4", "c5", "c6"), chaptersToDownload(chapters, MangaDownloadChoice.UNREAD, null))
        assertEquals(listOf("c2", "c5"), chaptersToDownload(chapters, MangaDownloadChoice.BOOKMARKED, null))
        assertEquals(listOf("c1", "c2", "c4", "c5", "c6"), chaptersToDownload(chapters, MangaDownloadChoice.ALL, null))
    }

    @Test
    fun unnumberedChaptersGoLast() {
        val chapters = listOf(
            ChapterFacts("x", -1f, 0, false, false, false, false, false),
            ChapterFacts("a", 1f, 1, false, false, false, false, false),
        )
        assertEquals(listOf("a", "x"), chaptersToDownload(chapters, MangaDownloadChoice.ALL, null))
    }

    @Test
    fun resumeButtonStates() {
        assertNull(resumeTarget(emptyList(), null))
        assertEquals(ResumeTarget("c1", ResumeKind.START), resumeTarget(series(0), null))
        assertEquals(ResumeTarget("c11", ResumeKind.RESUME), resumeTarget(series(10), null))
        assertEquals(ResumeTarget("c1", ResumeKind.READ_AGAIN), resumeTarget(series(30), null))
        val partway = (1..5).map { ch(it, progress = it == 3) }
        assertEquals(ResumeTarget("c3", ResumeKind.RESUME), resumeTarget(partway, null))
    }

    @Test
    fun resumeFallsBackToAnUnreadGapBeforeTheLastRead() {
        val chapters = (1..5).map { ch(it, read = it != 2) }
        assertEquals(ResumeTarget("c2", ResumeKind.RESUME), resumeTarget(chapters, null))
    }

    @Test
    fun topBarFadesInThenStaysSolid() {
        assertEquals(0f, topBarFraction(0, 0, 200), 0.001f)
        assertEquals(0.5f, topBarFraction(0, 100, 200), 0.001f)
        assertEquals(1f, topBarFraction(0, 400, 200), 0.001f)
        assertEquals(1f, topBarFraction(2, 0, 200), 0.001f)
        assertEquals(1f, topBarFraction(0, 0, 0), 0.001f)
    }

    @Test
    fun readsThePageFromTheSavedPlace() {
        assertEquals(65, pageFromLocator("page:65"))
        assertEquals(3, pageFromLocator("3"))
        assertNull(pageFromLocator("page:0"))
        assertNull(pageFromLocator("epub:2:10"))
        assertNull(pageFromLocator(null))
    }

    @Test
    fun statusTextIsLookedUpOrShownAsSent() {
        val label = { key: String -> "L:$key" }
        assertEquals("L:series_status_ongoing", mangaStatusText("ONGOING", label))
        assertEquals("L:series_status_ongoing", mangaStatusText("Ongoing", label))
        assertEquals("L:series_status_publishing_finished", mangaStatusText("PUBLISHING_FINISHED", label))
        assertEquals("Weekly", mangaStatusText("Weekly", label))
        assertNull(mangaStatusText("UNKNOWN", label))
        assertNull(mangaStatusText("  ", label))
        assertNull(mangaStatusText(null, label))
    }

    @Test
    fun triStateCyclesAndMapsToServerValues() {
        assertEquals(TriState.OFF, triStateOf(null))
        assertEquals(TriState.INCLUDE, triStateOf("include"))
        assertEquals(TriState.EXCLUDE, triStateOf("exclude"))
        assertEquals(TriState.INCLUDE, nextTriState(TriState.OFF))
        assertEquals(TriState.EXCLUDE, nextTriState(TriState.INCLUDE))
        assertEquals(TriState.OFF, nextTriState(TriState.EXCLUDE))
        assertEquals("", triStateValue(TriState.OFF))
        assertEquals("include", triStateValue(TriState.INCLUDE))
        assertEquals(true, passesTriState(TriState.EXCLUDE, false))
        assertEquals(false, passesTriState(TriState.INCLUDE, false))
        assertEquals(true, passesTriState(TriState.OFF, false))
    }

    @Test
    fun legacySingleFilterOnlyAppliesWithoutNewOnes() {
        assertEquals(TriState.INCLUDE, legacyFilter("unread", null, null, null, "unread"))
        assertEquals(TriState.OFF, legacyFilter("unread", null, null, null, "bookmarked"))
        assertEquals(TriState.OFF, legacyFilter("unread", "exclude", null, null, "unread"))
    }

    @Test
    fun sortKeysAndChapterLabels() {
        assertEquals("number", chapterSortKey(null))
        assertEquals("number", chapterSortKey("chapter"))
        assertEquals("date", chapterSortKey("upload"))
        assertEquals("source", chapterSortKey("source"))
        assertEquals("Chapter 12", chapterLabel("number", "Ch.12 - Name", 12f, "Chapter"))
        assertEquals("Chapter 12.5", chapterLabel("number", "x", 12.5f, "Chapter"))
        assertEquals("Ch.12 - Name", chapterLabel("title", "Ch.12 - Name", 12f, "Chapter"))
        assertEquals("Prologue", chapterLabel("number", "Prologue", -1f, "Chapter"))
    }

    @Test
    fun bookResumeKindFollowsProgress() {
        assertEquals(ResumeKind.START, bookResumeKind(0.0, false))
        assertEquals(ResumeKind.RESUME, bookResumeKind(0.4, false))
        assertEquals(ResumeKind.READ_AGAIN, bookResumeKind(0.99, false))
        assertEquals(ResumeKind.READ_AGAIN, bookResumeKind(0.2, true))
    }
}
