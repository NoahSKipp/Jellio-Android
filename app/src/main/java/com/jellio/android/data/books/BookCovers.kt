package com.jellio.android.data.books

import com.jellio.android.data.model.BaseItemDto
import java.util.Locale

internal fun withApiKey(url: String, token: String?): String {
    if (token.isNullOrEmpty()) return url
    val separator = if (url.contains('?')) "&" else "?"
    return "$url${separator}ApiKey=$token&api_key=$token"
}

// Cover of a book or audiobook: its own image, its album's, else the
// server's cover proxy. 500px wide covers a grid card at 3x density.
fun bookCoverUrlFor(item: BaseItemDto, server: String, token: String?): String {
    val primaryTag = item.ImageTags?.get("Primary")
    if (!primaryTag.isNullOrBlank()) {
        return withApiKey("$server/Items/${item.Id}/Images/Primary?maxWidth=500&quality=90&tag=$primaryTag", token)
    }
    val albumId = item.AlbumId
    val albumTag = item.AlbumPrimaryImageTag
    if (!albumId.isNullOrBlank() && !albumTag.isNullOrBlank()) {
        return withApiKey("$server/Items/$albumId/Images/Primary?maxWidth=500&quality=90&tag=$albumTag", token)
    }
    val targetId = albumId ?: item.Id
    return withApiKey("$server/Jellio/books/item/${targetId.replace("-", "").lowercase(Locale.US)}/cover", token)
}
