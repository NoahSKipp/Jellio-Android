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

internal enum class DownloadKind { VIDEO, EPISODES }

// Books and audiobooks have their own shelf menu, so only video here.
internal fun downloadKindFor(type: String?): DownloadKind? = when (type) {
    "Movie", "Episode", "Video", "MusicVideo" -> DownloadKind.VIDEO
    "Series", "Season" -> DownloadKind.EPISODES
    else -> null
}

internal enum class EpisodeScope(val labelKey: String) {
    NEXT_1("downloads_scope_next_1"),
    NEXT_5("downloads_scope_next_5"),
    NEXT_10("downloads_scope_next_10"),
    UNWATCHED("downloads_scope_unwatched"),
    ALL("downloads_scope_series"),
}

internal fun EpisodeScope.labelKeyFor(isSeason: Boolean): String =
    if (this == EpisodeScope.ALL && isSeason) "downloads_scope_season" else labelKey

internal fun <T> pickEpisodes(scope: EpisodeScope, episodes: List<T>, isPlayed: (T) -> Boolean): List<T> {
    val unwatched = episodes.filterNot(isPlayed)
    return when (scope) {
        EpisodeScope.NEXT_1 -> unwatched.take(1)
        EpisodeScope.NEXT_5 -> unwatched.take(5)
        EpisodeScope.NEXT_10 -> unwatched.take(10)
        EpisodeScope.UNWATCHED -> unwatched
        EpisodeScope.ALL -> episodes
    }
}

// The value is what the download manager takes as videoQuality.
internal enum class DownloadQuality(val value: String, val labelKey: String) {
    LOW("low", "downloads_quality_low"),
    HIGH("high", "downloads_quality_high"),
    HIGHER("higher", "downloads_quality_higher"),
    ORIGINAL("original", "downloads_quality_original"),
}
