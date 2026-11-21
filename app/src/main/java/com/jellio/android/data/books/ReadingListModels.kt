package com.jellio.android.data.books

import com.squareup.moshi.JsonClass

// The reading list: books, audiobooks and manga kept apart from the
// watchlist (Controllers/ReadingListController.cs, READING_LIST_CONTRACT.md).

@JsonClass(generateAdapter = true)
data class ReadingAddedByDto(
    val UserId: String = "",
    val UserName: String = "",
    val PrimaryImageTag: String? = null,
    val At: String? = null,
)

@JsonClass(generateAdapter = true)
data class ReadingEntryDto(
    val Key: String = "",
    val Kind: String = "ebook",
    val ItemId: String? = null,
    val MangaId: Int? = null,
    val Title: String = "",
    val Author: String? = null,
    val CoverUrl: String? = null,
    // Computed by the server: server-relative (starts with "/") or absolute https.
    val Cover: String? = null,
    val AddedAt: String? = null,
    val AddedBy: List<ReadingAddedByDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ReadingListResponseDto(
    val Items: List<ReadingEntryDto> = emptyList(),
    val Count: Int = 0,
    val Max: Int = 500,
)

@JsonClass(generateAdapter = true)
data class ReadingKeysResponseDto(
    val Keys: List<String> = emptyList(),
    val Count: Int = 0,
    val Max: Int = 500,
)

@JsonClass(generateAdapter = true)
data class ReadingAddBody(
    val Key: String? = null,
    val Kind: String? = null,
    val ItemId: String? = null,
    val Title: String? = null,
    val Author: String? = null,
    val CoverUrl: String? = null,
    val MangaId: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ReadingAddResultDto(
    val Added: Boolean = false,
    val Item: ReadingEntryDto? = null,
)

@JsonClass(generateAdapter = true)
data class ReadingRemoveResultDto(val Removed: Boolean = false)
