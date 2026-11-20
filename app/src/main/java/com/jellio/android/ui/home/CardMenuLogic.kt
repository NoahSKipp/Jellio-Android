package com.jellio.android.ui.home

import java.net.URLEncoder

// The rules of the card options menu that need no Android, so tests can run them.

// The page the plugin adds to the server's own web client.
internal fun shareUrl(serverAddress: String, itemId: String): String =
    serverAddress.trimEnd('/') + "/web/#/item?id=" + URLEncoder.encode(itemId, "UTF-8")

internal fun shareLabel(
    type: String?,
    name: String?,
    seriesName: String?,
    parentIndex: Int?,
    index: Int?,
): String {
    fun season(n: Int) = if (n == 0) "Specials" else "Season $n"
    val series = seriesName.orEmpty()
    when (type) {
        "Episode" -> {
            if (series.isNotEmpty() && parentIndex != null && index != null) {
                return "$series - ${season(parentIndex)} Episode $index"
            }
            if (series.isNotEmpty() && index != null) return "$series - Episode $index"
            if (series.isNotEmpty() && !name.isNullOrEmpty()) return "$series - $name"
            if (parentIndex != null && index != null) return "${season(parentIndex)} Episode $index"
            return name?.takeIf { it.isNotEmpty() } ?: series.ifEmpty { "Episode" }
        }
        "Season" -> {
            if (series.isNotEmpty() && index != null) return "$series - ${season(index)}"
            return name?.takeIf { it.isNotEmpty() } ?: if (series.isNotEmpty()) "$series Season" else "Season"
        }
    }
    return name.orEmpty()
}

internal fun shareText(label: String, url: String): String =
    if (label.isNotEmpty()) "Look at $label on Jellyfin - $url" else url
