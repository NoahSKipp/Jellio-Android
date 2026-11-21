package com.jellio.android.data.books

import java.text.Normalizer
import java.util.Locale

// The rules of the reading list that need no Android: keys, what each kind of
// item is sent as, the Reading and Listening sections, covers and refusals.

const val READING_LIST_MAX = 500
const val READING_KIND_EBOOK = "ebook"
const val READING_KIND_AUDIOBOOK = "audiobook"
const val READING_KIND_MANGA = "manga"

private val LIBRARY_ID = Regex("^[0-9a-f]{32}$")
private val WORK_KEY = Regex("^w:[A-Za-z]{2,6}:[^\\s]{1,120}$")
private val CONTROL_CHARS = Regex("[\\u0000-\\u001f\\u007f]")
private val ERROR_CODE = Regex("\"Error\"\\s*:\\s*\"([A-Za-z_]+)\"")
private val NON_ALNUM = Regex("[^a-z0-9]+")

// What is sent to add an item: to the own list and, with the friends' ids
// added, to friends' lists. Title and author may be left to the server when
// the item is a library item.
data class ReadingTarget(
    val key: String,
    val kind: String,
    val itemId: String? = null,
    val mangaId: Int? = null,
    val title: String? = null,
    val author: String? = null,
    val coverUrl: String? = null,
)

fun readingPlainId(id: String?): String = id.orEmpty().trim().replace("-", "").lowercase(Locale.US)

// Library items have GUID ids; a chapter streamed from a source does not.
fun isLibraryItemId(id: String?): Boolean = LIBRARY_ID.matches(readingPlainId(id))

fun itemReadingKey(itemId: String): String = "i:" + readingPlainId(itemId)

// The shelf series key, with or without the "s:" prefix.
fun seriesReadingKey(seriesKey: String): String = if (seriesKey.startsWith("s:")) seriesKey else "s:$seriesKey"

fun isValidSeriesKey(key: String): Boolean {
    if (!key.startsWith("s:") || key.length !in 3..300) return false
    val rest = key.substring(2)
    return rest.isNotEmpty() && rest == rest.trim() && !CONTROL_CHARS.containsMatchIn(rest)
}

fun isValidWorkKey(key: String): Boolean = WORK_KEY.matches(key)

// An external cover goes along only as an absolute https URL the server accepts.
fun httpsCoverOrNull(url: String?): String? =
    url?.trim()?.takeIf { it.startsWith("https://") && it.length <= 300 }

private fun clean(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

// A Jellyfin Book or AudioBook. `manga` says a Book is a comic file.
fun readingTargetForItem(itemId: String?, type: String?, title: String?, author: String?, manga: Boolean): ReadingTarget? {
    val audiobook = type.equals("AudioBook", ignoreCase = true)
    if (!audiobook && !type.equals("Book", ignoreCase = true)) return null
    if (!isLibraryItemId(itemId)) return null
    val plain = readingPlainId(itemId)
    val kind = when {
        audiobook -> READING_KIND_AUDIOBOOK
        manga -> READING_KIND_MANGA
        else -> READING_KIND_EBOOK
    }
    return ReadingTarget(key = "i:$plain", kind = kind, itemId = plain, title = clean(title), author = clean(author))
}

// A manga series by its shelf key. `mangaId` is the Suwayomi id when known
// and `itemId` a local chapter file the cover can be drawn from.
fun readingTargetForSeries(
    seriesKey: String?,
    title: String?,
    author: String? = null,
    mangaId: Int? = null,
    itemId: String? = null,
    coverUrl: String? = null,
): ReadingTarget? {
    if (seriesKey.isNullOrBlank()) return null
    val key = seriesReadingKey(seriesKey)
    val name = clean(title)
    if (name == null || !isValidSeriesKey(key)) return null
    return ReadingTarget(
        key = key,
        kind = READING_KIND_MANGA,
        itemId = itemId?.takeIf { isLibraryItemId(it) }?.let { readingPlainId(it) },
        mangaId = mangaId?.takeIf { it > 0 },
        title = name,
        author = clean(author),
        coverUrl = httpsCoverOrNull(coverUrl),
    )
}

// A book or audiobook found in Discover that is not a library item.
fun readingTargetForWork(workId: String?, title: String?, author: String?, coverUrl: String?, audiobook: Boolean): ReadingTarget? {
    val key = "w:" + workId.orEmpty().trim()
    val name = clean(title)
    if (name == null || !isValidWorkKey(key)) return null
    return ReadingTarget(
        key = key,
        kind = if (audiobook) READING_KIND_AUDIOBOOK else READING_KIND_EBOOK,
        title = name,
        author = clean(author),
        coverUrl = httpsCoverOrNull(coverUrl),
    )
}

fun ReadingTarget.toAddBody(): ReadingAddBody =
    ReadingAddBody(Key = key, Kind = kind, ItemId = itemId, Title = title, Author = author, CoverUrl = coverUrl, MangaId = mangaId)

// The "Reading" section holds books and manga, "Listening" audiobooks.
class ReadingSections<T>(val reading: List<T>, val listening: List<T>)

fun <T> splitReadingEntries(entries: List<T>, kindOf: (T) -> String): ReadingSections<T> {
    val (listening, reading) = entries.partition { kindOf(it) == READING_KIND_AUDIOBOOK }
    return ReadingSections(reading, listening)
}

fun readingKindLabelKey(kind: String?): String = when (kind) {
    READING_KIND_AUDIOBOOK -> "reading_kind_audiobook"
    READING_KIND_MANGA -> "reading_kind_manga"
    else -> "reading_kind_ebook"
}

fun readingToggleLabelKey(onList: Boolean): String = if (onList) "reading_remove" else "reading_add"

// What tapping an entry opens, the way the shelves open the same thing.
sealed interface ReadingOpen {
    data class Item(val itemId: String, val kind: String) : ReadingOpen
    data class Series(val seriesKey: String) : ReadingOpen

    // Not a library item: looked up among the user's books by title.
    data class Work(val title: String, val kind: String) : ReadingOpen
}

fun readingOpenFor(key: String, kind: String, itemId: String?, title: String): ReadingOpen {
    if (key.startsWith("i:")) {
        val id = itemId?.takeIf { isLibraryItemId(it) } ?: key.removePrefix("i:").takeIf { isLibraryItemId(it) }
        if (id != null) return ReadingOpen.Item(readingPlainId(id), kind)
    }
    if (key.startsWith("s:")) return ReadingOpen.Series(key.removePrefix("s:"))
    return ReadingOpen.Work(title, kind)
}

// Whether a title found by search is this entry: the same words once accents
// and punctuation are gone.
fun sameReadingTitle(a: String?, b: String?): Boolean {
    fun fold(text: String?): String {
        val decomposed = Normalizer.normalize(text.orEmpty().lowercase(Locale.US), Normalizer.Form.NFD)
        return NON_ALNUM.replace(decomposed.filter { it !in '̀'..'ͯ' }, " ").trim()
    }
    val left = fold(a)
    return left.isNotEmpty() && left == fold(b)
}

// A server-relative cover (starts with "/") needs the token like the other
// plugin images; an absolute https one loads as it is.
fun readingCoverUrl(cover: String?, server: String?, token: String?): String? {
    val value = cover?.trim().orEmpty()
    return when {
        value.isEmpty() -> null
        value.startsWith("/") -> server?.trimEnd('/')?.takeIf { it.isNotEmpty() }?.let { withApiKey(it + value, token) }
        value.startsWith("https://") -> value
        else -> null
    }
}

// The refusal's code from a JSON { Error } body.
fun readingErrorCode(body: String?): String? = body?.let { ERROR_CODE.find(it)?.groupValues?.get(1) }

fun readingErrorKey(status: Int, code: String?): String =
    if (status == 409 && code == "list_full") "reading_toast_full" else "reading_toast_error"

// How a toggle ended: the new state and the message to show.
class ReadingToggleResult(val ok: Boolean, val onList: Boolean, val messageKey: String)

fun readingToggleSuccess(nowOn: Boolean) =
    ReadingToggleResult(ok = true, onList = nowOn, messageKey = if (nowOn) "reading_toast_added" else "reading_toast_removed")

fun readingToggleFailure(wasOn: Boolean, messageKey: String) =
    ReadingToggleResult(ok = false, onList = wasOn, messageKey = messageKey)
