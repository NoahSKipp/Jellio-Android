package com.jellio.android.data.books

// What the series page needs to know about a chapter to pick downloads and the resume point.
data class ChapterFacts(
    val key: String,
    val number: Float,
    val sourceOrder: Int,
    val isRead: Boolean,
    val inProgress: Boolean,
    val isDownloaded: Boolean,
    val isQueued: Boolean,
    val isBookmarked: Boolean,
)

enum class MangaDownloadChoice(val count: Int?) {
    NEXT_1(1),
    NEXT_5(5),
    NEXT_10(10),
    NEXT_25(25),
    UNREAD(null),
    BOOKMARKED(null),
    ALL(null),
}

enum class ResumeKind { START, RESUME, READ_AGAIN }

data class ResumeTarget(val key: String, val kind: ResumeKind)

// First chapter first. A chapter without a number goes last, as on the server.
fun readingOrder(chapters: List<ChapterFacts>): List<ChapterFacts> =
    chapters.sortedWith(
        compareBy<ChapterFacts> { if (it.number < 0f) Float.MAX_VALUE else it.number }.thenBy { it.sourceOrder },
    )

// Where reading continues, as an index into [ordered]; -1 when everything is read.
// The server's resume chapter wins, then the furthest chapter in progress, then the
// first unread one after the last chapter read.
fun resumeIndex(ordered: List<ChapterFacts>, serverResumeKey: String?): Int {
    if (serverResumeKey != null) {
        val index = ordered.indexOfFirst { it.key == serverResumeKey }
        if (index >= 0 && !ordered[index].isRead) return index
    }
    val progress = ordered.indexOfLast { it.inProgress && !it.isRead }
    if (progress >= 0) return progress
    val lastRead = ordered.indexOfLast { it.isRead }
    val after = (lastRead + 1 until ordered.size).firstOrNull { !ordered[it].isRead }
    return after ?: ordered.indexOfFirst { !it.isRead }
}

fun resumeTarget(chapters: List<ChapterFacts>, serverResumeKey: String?): ResumeTarget? {
    val ordered = readingOrder(chapters)
    if (ordered.isEmpty()) return null
    val index = resumeIndex(ordered, serverResumeKey)
    if (index < 0) return ResumeTarget(ordered.first().key, ResumeKind.READ_AGAIN)
    val started = ordered.any { it.isRead || it.inProgress }
    return ResumeTarget(ordered[index].key, if (started) ResumeKind.RESUME else ResumeKind.START)
}

// Keys of the chapters to queue, in reading order. Chapters already downloaded or
// queued are skipped, and "next" counts unread ones from the resume point on.
fun chaptersToDownload(
    chapters: List<ChapterFacts>,
    choice: MangaDownloadChoice,
    serverResumeKey: String?,
): List<String> {
    val ordered = readingOrder(chapters)
    fun wanted(chapter: ChapterFacts) = !chapter.isDownloaded && !chapter.isQueued
    val picked = when (choice) {
        MangaDownloadChoice.UNREAD -> ordered.filter { !it.isRead && wanted(it) }
        MangaDownloadChoice.BOOKMARKED -> ordered.filter { it.isBookmarked && wanted(it) }
        MangaDownloadChoice.ALL -> ordered.filter { wanted(it) }
        else -> {
            val from = resumeIndex(ordered, serverResumeKey)
            if (from < 0) {
                emptyList()
            } else {
                ordered.drop(from).filter { !it.isRead && wanted(it) }.take(choice.count ?: 0)
            }
        }
    }
    return picked.map { it.key }
}

// How opaque the sticky top bar is: clear at the top, solid once the header has scrolled away.
fun topBarFraction(firstVisibleIndex: Int, firstVisibleOffsetPx: Int, fadeDistancePx: Int): Float {
    if (firstVisibleIndex > 0) return 1f
    if (fadeDistancePx <= 0) return 1f
    return (firstVisibleOffsetPx.toFloat() / fadeDistancePx).coerceIn(0f, 1f)
}

// The Resume button shows its label only while the list is still.
const val RESUME_FAB_SETTLE_MS = 450L

// "page:12" is the saved place in a chapter, counted from 1.
fun pageFromLocator(locator: String?): Int? {
    val value = locator?.trim().orEmpty()
    val digits = if (value.startsWith("page:")) value.removePrefix("page:") else value
    return digits.toIntOrNull()?.takeIf { it > 0 }
}

// A word for a status from the server (ONGOING, Ongoing, ...), looked up by [label];
// unknown ones are shown as sent and an empty or UNKNOWN status is left out.
fun mangaStatusText(raw: String?, label: (String) -> String): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val key = when (value.uppercase().replace(' ', '_')) {
        "UNKNOWN" -> return null
        "ONGOING" -> "series_status_ongoing"
        "COMPLETED", "FINISHED" -> "series_status_completed"
        "LICENSED" -> "series_status_licensed"
        "PUBLISHING_FINISHED" -> "series_status_publishing_finished"
        "CANCELLED", "CANCELED" -> "series_status_cancelled"
        "ON_HIATUS", "HIATUS" -> "series_status_hiatus"
        else -> return value
    }
    return label(key)
}

// A chapter filter: off, only the matching chapters, or all but the matching ones.
enum class TriState { OFF, INCLUDE, EXCLUDE }

// The server stores a filter as "include", "exclude" or nothing.
fun triStateOf(value: String?): TriState = when (value) {
    "include" -> TriState.INCLUDE
    "exclude" -> TriState.EXCLUDE
    else -> TriState.OFF
}

fun nextTriState(state: TriState): TriState = when (state) {
    TriState.OFF -> TriState.INCLUDE
    TriState.INCLUDE -> TriState.EXCLUDE
    TriState.EXCLUDE -> TriState.OFF
}

// An empty value clears the setting on the server.
fun triStateValue(state: TriState): String = when (state) {
    TriState.OFF -> ""
    TriState.INCLUDE -> "include"
    TriState.EXCLUDE -> "exclude"
}

fun passesTriState(state: TriState, matches: Boolean): Boolean = when (state) {
    TriState.OFF -> true
    TriState.INCLUDE -> matches
    TriState.EXCLUDE -> !matches
}

// A series saved before the three filters existed has one ChapterFilter ("unread" or "bookmarked").
fun legacyFilter(chapterFilter: String?, unread: String?, bookmarked: String?, downloaded: String?, wanted: String): TriState {
    if (!unread.isNullOrEmpty() || !bookmarked.isNullOrEmpty() || !downloaded.isNullOrEmpty()) return TriState.OFF
    return if (chapterFilter == wanted) TriState.INCLUDE else TriState.OFF
}

// "number", "source", "date" or "title"; older spellings still read.
fun chapterSortKey(raw: String?): String = when (raw) {
    "source" -> "source"
    "date", "upload" -> "date"
    "title" -> "title"
    else -> "number"
}

// "Chapter 12.5" when chapters are shown by number, the source's own title otherwise.
fun chapterLabel(display: String?, title: String, number: Float, numberLabel: String): String {
    if (display != "number" || number < 0f) return title
    val shown = if (number == number.toInt().toFloat()) number.toInt().toString() else number.toString()
    return "$numberLabel $shown"
}

// The Resume button of a book or audiobook, from how far it has been read or heard.
fun bookResumeKind(progress: Double, finished: Boolean): ResumeKind = when {
    finished || progress >= 0.98 -> ResumeKind.READ_AGAIN
    progress > 0.0 -> ResumeKind.RESUME
    else -> ResumeKind.START
}
