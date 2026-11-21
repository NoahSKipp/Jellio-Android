package com.jellio.android.data.notify

// A watchlist_added notification about a book, audiobook or manga carries
// ReadingKey (i:, s: or w:) and Type Book, AudioBook or Manga. Old ones and
// movie or series notices have no ReadingKey.

fun isReadingNotice(readingKey: String?): Boolean = !readingKey.isNullOrBlank()

fun readingNoticeTypeKey(type: String?): String = when (type) {
    "AudioBook" -> "reading_kind_audiobook"
    "Manga" -> "reading_kind_manga"
    else -> "reading_kind_ebook"
}

// With count 1 a reading item says "reading list"; several titles (which may
// mix movies and books) keep the watchlist wording.
fun watchlistNoticeTextKey(count: Int, readingKey: String?): String = when {
    count > 1 -> "friends_notice_added_many"
    isReadingNotice(readingKey) -> "friends_notice_added_reading_one"
    else -> "friends_notice_added_one"
}

// The subtitle shows the type of a single reading item.
fun watchlistNoticeSubKey(count: Int, readingKey: String?, type: String?): String =
    if (count == 1 && isReadingNotice(readingKey)) readingNoticeTypeKey(type) else "friends_notice_sub_watchlist"

// The shelf key of a manga series, from an s: reading key; null otherwise.
fun readingNoticeSeriesKey(readingKey: String?): String? =
    readingKey?.takeIf { it.startsWith("s:") }?.removePrefix("s:")?.takeIf { it.isNotEmpty() }
