package com.jellio.android.data.books

fun mangaShelfKey(seriesKey: String): String = if (seriesKey.startsWith("s:")) seriesKey else "s:$seriesKey"

// What is stored when a series is removed from Continue reading: the last
// read time it was removed at, so reading it again brings the card back.
fun mangaDismissalMarker(lastReadAt: String?): String = lastReadAt?.takeIf { it.isNotBlank() } ?: "-"

fun isMangaContinueDismissed(prefs: Map<String, SeriesPrefsDto>, seriesKey: String, lastReadAt: String?): Boolean {
    val stored = (prefs[mangaShelfKey(seriesKey)] ?: prefs[seriesKey])?.ContinueDismissedAt ?: return false
    return stored == mangaDismissalMarker(lastReadAt)
}

// A server that predates the stored removal answers without it. The removal
// is then kept on this device so the card still goes away here.
private const val LOCAL_SEPARATOR = '\t'

fun withLocalMangaDismissal(entries: Set<String>, seriesKey: String, lastReadAt: String?): Set<String> {
    val key = mangaShelfKey(seriesKey)
    return entries.filterNot { it.startsWith("$key$LOCAL_SEPARATOR") }.toSet() + "$key$LOCAL_SEPARATOR${mangaDismissalMarker(lastReadAt)}"
}

fun isLocallyDismissedManga(entries: Set<String>, seriesKey: String, lastReadAt: String?): Boolean =
    "${mangaShelfKey(seriesKey)}$LOCAL_SEPARATOR${mangaDismissalMarker(lastReadAt)}" in entries
