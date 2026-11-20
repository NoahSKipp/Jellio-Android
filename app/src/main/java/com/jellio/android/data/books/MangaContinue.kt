package com.jellio.android.data.books

private const val SEPARATOR = '\t'

private fun entry(seriesKey: String, lastReadAt: String?) = "$seriesKey$SEPARATOR${lastReadAt.orEmpty()}"

// A dismissal is tied to the last read time it was made at: reading the
// series again changes that time and the card comes back.
fun isMangaContinueDismissed(entries: Set<String>, seriesKey: String, lastReadAt: String?): Boolean =
    entry(seriesKey, lastReadAt) in entries

fun withMangaDismissal(entries: Set<String>, seriesKey: String, lastReadAt: String?): Set<String> =
    entries.filterNot { it.startsWith("$seriesKey$SEPARATOR") }.toSet() + entry(seriesKey, lastReadAt)
