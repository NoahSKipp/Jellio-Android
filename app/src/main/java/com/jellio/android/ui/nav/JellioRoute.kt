package com.jellio.android.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

private val ANIME_NAME = Regex("anime", RegexOption.IGNORE_CASE)
private val MANGA_NAME = Regex("manga|comic", RegexOption.IGNORE_CASE)
private val AUDIOBOOK_NAME = Regex("audiobook", RegexOption.IGNORE_CASE)

sealed interface JellioRoute {
    data class Profile(val userId: String? = null) : JellioRoute
    data class Home(val editMode: Boolean = false) : JellioRoute
    data object Search : JellioRoute
    data object Feed : JellioRoute
    // One library as its own rail button (the Anime entry shares the
    // Shows library's id, so the name tells them apart).
    data class LibraryTab(val libraryId: String, val name: String, val collectionType: String?) : JellioRoute
    data class Bookshelf(val kind: String = "ebook") : JellioRoute
    data class Reader(
        val itemId: String,
        val kind: String = "book",
        val mangaId: Int? = null,
        val chapterId: Int? = null,
    ) : JellioRoute
    data class AudiobookPlayer(val itemId: String) : JellioRoute
    data object Vocab : JellioRoute
    data object Downloads : JellioRoute
    data object Settings : JellioRoute
    data class Detail(val itemId: String) : JellioRoute
    data class Person(val personId: String) : JellioRoute
    data class Service(val name: String) : JellioRoute
    data class Player(val itemId: String, val mediaSourceId: String? = null, val startPositionTicks: Long? = null) : JellioRoute
    data object LiveTv : JellioRoute
    data class LivePlayer(val channelId: String) : JellioRoute
}

fun jellioNavItems(libraries: List<com.jellio.android.data.model.BaseItemDto>): List<JellioRoute> = buildList {
    add(JellioRoute.Profile())
    add(JellioRoute.Home())
    add(JellioRoute.Search)
    var hasBookshelfEntry = false
    libraries.forEach { library ->
        val cType = library.CollectionType?.lowercase()
        val libName = library.Name.orEmpty()
        when {
            cType == "livetv" -> add(JellioRoute.LiveTv)
            cType == "audiobooks" || AUDIOBOOK_NAME.containsMatchIn(libName) -> {
                add(JellioRoute.Bookshelf(kind = "audiobook"))
                hasBookshelfEntry = true
            }
            cType == "books" -> {
                val kind = if (MANGA_NAME.containsMatchIn(libName)) "manga" else "ebook"
                add(JellioRoute.Bookshelf(kind = kind))
                hasBookshelfEntry = true
            }
            else -> add(JellioRoute.LibraryTab(library.Id, libName, library.CollectionType))
        }
    }
    if (!hasBookshelfEntry) {
        add(JellioRoute.Bookshelf(kind = "ebook"))
    }
    add(JellioRoute.Downloads)
    add(JellioRoute.Feed)
}

fun JellioRoute.icon(): ImageVector = when (this) {
    is JellioRoute.Profile -> Icons.Filled.AccountCircle
    is JellioRoute.Home -> Icons.Filled.Home
    JellioRoute.Search -> SearchIconVector
    JellioRoute.Feed -> Icons.Filled.DynamicFeed
    is JellioRoute.Bookshelf -> when (kind) {
        "manga" -> Icons.Filled.AutoStories
        "audiobook" -> Icons.Filled.Headphones
        else -> Icons.AutoMirrored.Filled.MenuBook
    }
    JellioRoute.Downloads -> Icons.Filled.DownloadForOffline
    JellioRoute.Vocab -> Icons.Filled.School
    // The same icons Jellio web draws (components/navShared.js SVG_ICONS),
    // ported in JellioIcons.kt.
    is JellioRoute.LibraryTab -> when {
        ANIME_NAME.containsMatchIn(name) -> AnimeIconVector
        collectionType == "movies" -> MovieIconVector
        collectionType == "tvshows" -> TvShowsIconVector
        else -> LibraryIconVector
    }
    JellioRoute.Settings -> Icons.Filled.Settings
    JellioRoute.LiveTv -> Icons.Filled.LiveTv
    is JellioRoute.Detail,
    is JellioRoute.Person,
    is JellioRoute.Service,
    is JellioRoute.Player,
    is JellioRoute.LivePlayer,
    is JellioRoute.Reader,
    is JellioRoute.AudiobookPlayer,
    -> Icons.Filled.Home
}

fun JellioRoute.iconScale(): Float = when (this) {
    JellioRoute.Settings -> 2.4f
    else -> 1f
}

fun JellioRoute.label(): String = when (this) {
    is JellioRoute.Profile -> com.jellio.android.data.i18n.Strings.get("nav_profile")
    is JellioRoute.Home -> com.jellio.android.data.i18n.Strings.get("nav_home")
    JellioRoute.Search -> com.jellio.android.data.i18n.Strings.get("nav_search")
    JellioRoute.Feed -> com.jellio.android.data.i18n.Strings.get("nav_feed")
    is JellioRoute.Bookshelf -> when (kind) {
        "manga" -> "Manga"
        "audiobook" -> "Audiobooks"
        else -> "Books"
    }
    JellioRoute.Downloads -> "Downloads"
    JellioRoute.Vocab -> "Vocabulary"
    is JellioRoute.LibraryTab -> com.jellio.android.data.i18n.libraryDisplayName(name, collectionType)
    JellioRoute.Settings -> com.jellio.android.data.i18n.Strings.get("nav_settings")
    JellioRoute.LiveTv -> com.jellio.android.data.i18n.Strings.get("nav_live_tv")
    is JellioRoute.Detail,
    is JellioRoute.Person,
    is JellioRoute.Service,
    is JellioRoute.Player,
    is JellioRoute.LivePlayer,
    is JellioRoute.Reader,
    is JellioRoute.AudiobookPlayer,
    -> ""
}

fun JellioRoute.isImmersive(): Boolean =
    this is JellioRoute.Player ||
        this is JellioRoute.LivePlayer ||
        this is JellioRoute.Reader ||
        this is JellioRoute.AudiobookPlayer
