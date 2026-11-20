package com.jellio.android.ui.books

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.Disposable
import coil3.request.ImageRequest

private const val PAGES_AHEAD = 3
private const val NEXT_CHAPTER_LEAD = 4

// The pages worth having on disk before the reader turns to them: the next
// few of this chapter, and the next chapter's first pages once the end is close.
fun prefetchTargets(urls: List<String>, current: Int, nextChapterUrls: List<String>): List<String> {
    if (urls.isEmpty()) return nextChapterUrls
    val from = (current + 1).coerceAtLeast(0)
    val to = (current + PAGES_AHEAD).coerceAtMost(urls.lastIndex)
    val ahead = if (from <= to) urls.subList(from, to + 1) else emptyList()
    val nearEnd = current >= urls.size - NEXT_CHAPTER_LEAD
    return if (nearEnd) ahead + nextChapterUrls else ahead
}

// Warms Coil's disk cache for pages ahead and cancels the ones the reader
// has moved away from. Pages are cached as sent, so the page itself still
// decodes at the size it is shown at.
class MangaPagePrefetcher(context: Context) {
    private val appContext = context.applicationContext
    private val inFlight = LinkedHashMap<String, Disposable>()
    private val requested = HashSet<String>()

    fun update(urls: List<String>, current: Int, nextChapterUrls: List<String>) {
        val wanted = prefetchTargets(urls, current, nextChapterUrls).toSet()
        val iterator = inFlight.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isDisposed) {
                iterator.remove()
            } else if (entry.key !in wanted) {
                entry.value.dispose()
                requested.remove(entry.key)
                iterator.remove()
            }
        }
        val loader = SingletonImageLoader.get(appContext)
        for (url in wanted) {
            if (!requested.add(url)) continue
            // Not decoded into memory: the bytes on disk are what makes the page instant.
            val request = ImageRequest.Builder(appContext)
                .data(url)
                .size(64, 64)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build()
            inFlight[url] = loader.enqueue(request)
        }
    }

    fun cancelAll() {
        inFlight.values.forEach { it.dispose() }
        inFlight.clear()
        requested.clear()
    }
}
