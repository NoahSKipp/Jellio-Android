package com.jellio.android.data.books

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReadingProgressDto(
    val Locator: String = "",
    val Progress: Double = 0.0,
    val TotalPages: Int? = null,
    val UpdatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class SaveReadingProgressRequest(
    val Locator: String,
    val Progress: Double,
    val TotalPages: Int? = null,
)

@JsonClass(generateAdapter = true)
data class MarkReadingProgressRequest(
    val ItemIds: List<String>,
    val Read: Boolean,
)

@JsonClass(generateAdapter = true)
data class InProgressReadingDto(
    val ItemId: String,
    val Progress: Double = 0.0,
    val TotalPages: Int? = null,
    val UpdatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ReadingSessionRequest(
    val ItemId: String,
    val Kind: String,
    val PagesRead: Int = 0,
    val CurrentPage: Int? = null,
    val PageCount: Int? = null,
    val ListenedSeconds: Int = 0,
    val Finished: Boolean = false,
    val MangaId: Int? = null,
    val ReadSeconds: Int? = null,
    val ChapterNumber: Float? = null,
)

@JsonClass(generateAdapter = true)
data class ReadingNowPlayingRequest(
    val ItemId: String,
    val Kind: String,
    val Title: String,
    val SeriesTitle: String? = null,
    val MangaId: Int? = null,
    val Page: Int? = null,
    val PageCount: Int? = null,
)

@JsonClass(generateAdapter = true)
data class AnnotationDto(
    val Id: String = "",
    val Kind: String = "highlight",
    val Locator: String = "",
    val Position: Double = 0.0,
    val Text: String? = null,
    val Note: String? = null,
    val Color: String? = "yellow",
    val Chapter: String? = null,
    val CreatedAt: String? = null,
    val UpdatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class CreateAnnotationRequest(
    val Kind: String,
    val Locator: String,
    val Position: Double,
    val Text: String? = null,
    val Note: String? = null,
    val Color: String? = "yellow",
    val Chapter: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateAnnotationRequest(
    val Note: String? = null,
    val Color: String? = null,
)

@JsonClass(generateAdapter = true)
data class TranslateRequest(
    val Text: String,
    val TargetLang: String,
    val SourceLang: String? = null,
    val Context: String? = null,
)

@JsonClass(generateAdapter = true)
data class TranslateResponseDto(
    val Text: String? = null,
    val DetectedSourceLang: String? = null,
    val Error: String? = null,
)

@JsonClass(generateAdapter = true)
data class DictionarySenseDto(
    val PartOfSpeech: String? = null,
    val Language: String? = null,
    val Definitions: List<String> = emptyList(),
    val Examples: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class VocabEntryDto(
    val Id: String = "",
    val Word: String = "",
    val Sentence: String? = null,
    val Definition: String? = null,
    val Translation: String? = null,
    val SourceLang: String? = null,
    val TargetLang: String? = null,
    val ItemId: String? = null,
    val ItemName: String? = null,
    val Locator: String? = null,
    val CreatedAt: String? = null,
    val DueAt: String? = null,
    val IntervalDays: Double = 0.0,
    val Ease: Double = 2.5,
    val Reps: Int = 0,
    val Lapses: Int = 0,
)

@JsonClass(generateAdapter = true)
data class CreateVocabRequest(
    val Word: String,
    val Sentence: String? = null,
    val Definition: String? = null,
    val Translation: String? = null,
    val SourceLang: String? = null,
    val TargetLang: String? = null,
    val ItemId: String? = null,
    val ItemName: String? = null,
    val Locator: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateVocabRequest(
    val Translation: String? = null,
    val Definition: String? = null,
    val Sentence: String? = null,
)

@JsonClass(generateAdapter = true)
data class ReviewVocabRequest(
    val Grade: Int,
)

@JsonClass(generateAdapter = true)
data class ShelfCategoryDto(
    val Id: String = "",
    val Kind: String = "",
    val Name: String = "",
    val Sort: String? = null,
    val Descending: Boolean = false,
    val Items: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SeriesPrefsDto(
    val Note: String? = null,
    val ChapterDescending: Boolean? = null,
    val ChapterFilter: String? = null,
    val ComicLayout: String? = null,
    val ComicDirection: String? = null,
    val ExcludedScanlators: List<String>? = null,
    val DuplicatesAsOne: Boolean? = null,
    val FilterDownloaded: String? = null,
    val FilterUnread: String? = null,
    val FilterBookmarked: String? = null,
    val ChapterSort: String? = null,
    val ChapterDisplay: String? = null,
    val SkipUpdates: Boolean? = null,
    val DeleteAfterRead: Boolean? = null,
    val ContinueDismissedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ShelfDto(
    val Categories: List<ShelfCategoryDto> = emptyList(),
    val Series: Map<String, SeriesPrefsDto> = emptyMap(),
    val Bookmarks: List<String> = emptyList(),
    val Library: List<String> = emptyList(),
    val LibraryRemoved: List<String> = emptyList(),
    val SeriesDefaults: SeriesPrefsDto? = null,
)

@JsonClass(generateAdapter = true)
data class CreateShelfCategoryRequest(
    val Name: String,
)

@JsonClass(generateAdapter = true)
data class UpdateShelfCategoryRequest(
    val Name: String? = null,
    val Sort: String? = null,
    val Descending: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class ShelfOrderRequest(
    val Ids: List<String>,
)

@JsonClass(generateAdapter = true)
data class ShelfMembershipRequest(
    val Keys: List<String>,
    val CategoryIds: List<String>? = null,
    val AddTo: List<String>? = null,
    val RemoveFrom: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateSeriesPrefsRequest(
    val Key: String,
    val Note: String? = null,
    val ChapterDescending: Boolean? = null,
    val ChapterFilter: String? = null,
    val ComicLayout: String? = null,
    val ComicDirection: String? = null,
    val ExcludedScanlators: List<String>? = null,
    val DuplicatesAsOne: Boolean? = null,
    val FilterDownloaded: String? = null,
    val FilterUnread: String? = null,
    val FilterBookmarked: String? = null,
    val ChapterSort: String? = null,
    val ChapterDisplay: String? = null,
    val Reset: Boolean? = null,
    val SkipUpdates: Boolean? = null,
    val DeleteAfterRead: Boolean? = null,
    val ContinueDismissedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateSeriesDefaultsRequest(
    val ChapterDescending: Boolean? = null,
    val ChapterSort: String? = null,
    val ChapterDisplay: String? = null,
    val FilterDownloaded: String? = null,
    val FilterUnread: String? = null,
    val FilterBookmarked: String? = null,
    val ApplyToLibrary: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class UpdateShelfLibraryRequest(
    val Key: String,
    val InLibrary: Boolean,
)

@JsonClass(generateAdapter = true)
data class UpdateShelfBookmarkRequest(
    val Bookmarked: Boolean,
)

@JsonClass(generateAdapter = true)
data class BookShelfInfoDto(
    val Authors: String? = null,
    val Genres: List<String> = emptyList(),
    val Year: Int? = null,
    val SeriesTitle: String? = null,
)

@JsonClass(generateAdapter = true)
data class BookMetadataDto(
    val Title: String? = null,
    val Authors: String? = null,
    val Overview: String? = null,
    val Genres: List<String> = emptyList(),
    val Publisher: String? = null,
    val PageCount: Int? = null,
    val Year: Int? = null,
    val Isbn: String? = null,
    val SeriesTitle: String? = null,
    val HasCover: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class BookSearchResultDto(
    val WorkId: String = "",
    val Title: String = "",
    val Author: String? = null,
    val Year: Int? = null,
    val CoverUrl: String? = null,
    val SeriesTitle: String? = null,
    val HasEbook: Boolean = false,
    val HasAudiobook: Boolean = false,
    val EbookDownloaded: Boolean = false,
    val AudiobookDownloaded: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class BookDiscoverResultDto(
    val WorkId: String = "",
    val Title: String = "",
    val Author: String? = null,
    val Year: Int? = null,
    val CoverUrl: String? = null,
    val HasEbook: Boolean = false,
    val HasAudiobook: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class BookRequestBody(
    val WorkId: String,
    val BookType: String,
    val Title: String? = null,
    val Author: String? = null,
)

@JsonClass(generateAdapter = true)
data class BookRequestResultDto(
    val Status: String = "",
    val Message: String? = null,
)

@JsonClass(generateAdapter = true)
data class BookRequestEntryDto(
    val ChaptarrBookId: Int = 0,
    val Title: String = "",
    val Author: String? = null,
    val CoverUrl: String? = null,
    val State: String = "",
    val Progress: Double? = null,
    val TimeLeft: String? = null,
    val Message: String? = null,
    val RequestedBy: List<String> = emptyList(),
    val Mine: Boolean = false,
    val RequestedAt: String? = null,
    val ItemId: String? = null,
)

@JsonClass(generateAdapter = true)
data class BookRequestsResponseDto(
    val Configured: Boolean = false,
    val Items: List<BookRequestEntryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BookRemoveRequest(
    val MediaType: String,
    val ItemId: String? = null,
    val ChaptarrBookId: Int = 0,
    val Title: String? = null,
)

// Mode is "personal" when other readers still have the book, so only this
// reader's copy is gone; anything else means it was deleted from the server.
@JsonClass(generateAdapter = true)
data class BookRemoveResultDto(
    val Mode: String? = null,
    val Deleted: Int = 0,
    val LibraryOnly: Int = 0,
)

@JsonClass(generateAdapter = true)
data class MangaStatusDto(
    val Configured: Boolean = false,
    val Reachable: Boolean = false,
    val DownloadAsCbz: Boolean = false,
    val AutoDownloadNewChapters: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class MangaStreamResumeDto(
    val Id: String = "",
    val ChapterId: Int = 0,
    val Name: String? = null,
)

@JsonClass(generateAdapter = true)
data class MangaSeriesSummaryDto(
    val MangaId: Int = 0,
    val Title: String = "",
    val Author: String? = null,
    val Status: String? = null,
    val Key: String = "",
    val ChapterCount: Int = 0,
    val DownloadedCount: Int = 0,
    val ReadCount: Int = 0,
    val LatestUpload: Long = 0,
    val LastReadAt: String? = null,
    val Resume: MangaStreamResumeDto? = null,
    val FirstChapterId: String? = null,
)

@JsonClass(generateAdapter = true)
data class MangaStreamChapterDto(
    val Id: String = "",
    val ChapterId: Int = 0,
    val MangaId: Int = 0,
    val Name: String = "",
    val Number: Float = 0f,
    val Scanlator: String? = null,
    val PageCount: Int = 0,
    val UploadDate: Long = 0L,
    val IsDownloaded: Boolean = false,
    val SourceOrder: Int = 0,
    val SeriesTitle: String? = null,
    val SeriesKey: String? = null,
)

@JsonClass(generateAdapter = true)
data class MangaStreamSeriesDto(
    val MangaId: Int = 0,
    val Title: String = "",
    val Author: String? = null,
    val Status: String? = null,
    val Key: String = "",
    val Chapters: List<MangaStreamChapterDto> = emptyList(),
    val Source: String? = null,
    val SourceName: String? = null,
)

@JsonClass(generateAdapter = true)
data class MangaByKeyDto(
    val MangaId: Int = 0,
    val Title: String = "",
    val Key: String = "",
)

@JsonClass(generateAdapter = true)
data class MangaPagesCountDto(
    val Count: Int = 0,
)

@JsonClass(generateAdapter = true)
data class MangaUpdateItemDto(
    val SeriesKey: String = "",
    val SeriesTitle: String = "",
    val MangaId: Int = 0,
    val Chapter: MangaStreamChapterDto? = null,
    val SeenAt: Long = 0L,
    val Read: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class MangaUpdatesResponseDto(
    val LastRefreshAt: Long = 0L,
    val Items: List<MangaUpdateItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MangaDiscoverDto(
    val Id: Int = 0,
    val Title: String = "",
    val AltTitle: String? = null,
    val Author: String? = null,
    val Year: Int? = null,
    val CoverUrl: String? = null,
    val Country: String? = null,
    val Volumes: Int? = null,
    val Status: String? = null,
    val Genres: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MangaSearchResultItemDto(
    val MangaId: Int = 0,
    val Title: String = "",
    val Author: String? = null,
    val Status: String? = null,
    val InLibrary: Boolean = false,
    val ThumbnailUrl: String = "",
    val Match: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class MangaSourceSearchDto(
    val SourceId: Long = 0L,
    val SourceName: String = "",
    val Lang: String = "",
    val Results: List<MangaSearchResultItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MangaSearchResponseDto(
    val Searched: Int = 0,
    val Failed: Int = 0,
    val Languages: List<String> = emptyList(),
    val InstalledLanguages: List<String> = emptyList(),
    val TotalSources: Int = 0,
    val NsfwSources: Int = 0,
    val Sources: List<MangaSourceSearchDto> = emptyList(),
    val Unavailable: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class MangaRequestBody(
    val MangaId: Int,
    val Title: String? = null,
    val AddToLibrary: Boolean = true,
    val Force: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class MangaRequestResultDto(
    val Status: String = "",
    val QueuedChapters: Int = 0,
    val TotalChapters: Int = 0,
    val Message: String? = null,
)

@JsonClass(generateAdapter = true)
data class MangaMigrateBody(
    val FromMangaId: Int,
    val ToMangaId: Int,
    val ToTitle: String? = null,
)

@JsonClass(generateAdapter = true)
data class MangaMigrateResultDto(
    val Migrated: Int = 0,
    val Removed: Boolean = false,
    val MangaId: Int = 0,
    val Title: String = "",
    val Key: String = "",
)

@JsonClass(generateAdapter = true)
data class AudiobookChapterDto(
    val Title: String = "",
    val StartSeconds: Double = 0.0,
    val DurationSeconds: Double = 0.0,
    val TrackId: String? = null,
    val TrackOffsetSeconds: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class ConversionStatusDto(
    val Active: Int = 0,
    val Max: Int = 2,
    val Yours: Int = 0,
    val Available: Boolean = true,
    val HevcEncoding: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ClaimConversionRequest(
    val ItemId: String,
)

@JsonClass(generateAdapter = true)
data class ClaimConversionResponseDto(
    val SlotId: String? = null,
    val Active: Int = 0,
    val Max: Int = 2,
    val Message: String? = null,
)

@JsonClass(generateAdapter = true)
data class TrackerSyncRequest(
    val Key: String? = null,
    val MangaId: Int? = null,
)

@JsonClass(generateAdapter = true)
data class MangaImportJobDto(
    val Status: String = "running",
    val Total: Int = 0,
    val Processed: Int = 0,
)

@JsonClass(generateAdapter = true)
data class MangaImportStatusDto(
    val Job: MangaImportJobDto? = null,
    val PendingChapters: Int = 0,
)
