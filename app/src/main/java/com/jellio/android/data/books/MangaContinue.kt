package com.jellio.android.data.books

fun mangaShelfKey(seriesKey: String): String = if (seriesKey.startsWith("s:")) seriesKey else "s:$seriesKey"

// What is stored when a series is removed from Continue reading: the last
// read time it was removed at, so reading it again brings the card back.
fun mangaDismissalMarker(lastReadAt: String?): String = lastReadAt?.takeIf { it.isNotBlank() } ?: "-"

fun isMangaContinueDismissed(prefs: Map<String, SeriesPrefsDto>, seriesKey: String, lastReadAt: String?): Boolean {
    val stored = (prefs[mangaShelfKey(seriesKey)] ?: prefs[seriesKey])?.ContinueDismissedAt ?: return false
    return stored == mangaDismissalMarker(lastReadAt)
}
