package com.jellio.android.data.books

import android.content.Context
import com.jellio.android.data.catching
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.network.JellyfinApi
import com.jellio.android.data.offline.OfflineDownloadManager
import com.jellio.android.data.session.SessionManager
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class OpenedBookFile(
    val file: File,
    val format: BookFileFormat,
    val fromOfflineDownload: Boolean,
)

enum class BookFileFormat {
    EPUB,
    PDF,
    CBZ,
    UNKNOWN,
}

@Singleton
class BooksRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    retrofit: Retrofit,
    private val jellyfinApi: JellyfinApi,
    private val okHttpClient: OkHttpClient,
    private val sessionManager: SessionManager,
    private val offlineDownloads: OfflineDownloadManager,
    private val moshi: Moshi,
) {
    private val api: BooksApi = retrofit.create(BooksApi::class.java)

    private val progressSaveAdapter by lazy { moshi.adapter(SaveReadingProgressRequest::class.java) }
    private val sessionAdapter by lazy { moshi.adapter(ReadingSessionRequest::class.java) }
    private val createAnnotationAdapter by lazy { moshi.adapter(CreateAnnotationRequest::class.java) }
    private val updateAnnotationAdapter by lazy { moshi.adapter(UpdateAnnotationRequest::class.java) }
    private val createVocabAdapter by lazy { moshi.adapter(CreateVocabRequest::class.java) }

    private val readerPrefs by lazy { context.getSharedPreferences("jellio_reader", Context.MODE_PRIVATE) }

    private val bookCacheDir: File by lazy {
        File(context.cacheDir, "jellio_book_files").apply { mkdirs() }
    }

    suspend fun currentUserId(): String? = sessionManager.sessionFlow.first()?.userId

    // --- Shelf & Jellyfin library queries ---

    suspend fun getBookshelfLibraries(userId: String): List<BaseItemDto> =
        catching {
            jellyfinApi.getUserViews(userId).Items.filter {
                it.CollectionType.equals("books", ignoreCase = true) ||
                    it.Name?.contains("manga", ignoreCase = true) == true ||
                    it.Name?.contains("book", ignoreCase = true) == true ||
                    it.Name?.contains("audiobook", ignoreCase = true) == true
            }
        }.getOrDefault(emptyList())

    // The raw Book or AudioBook list of one library; ebooks and manga come
    // from the same query, so callers can share it.
    suspend fun getBookshelfRaw(userId: String, parentId: String, kind: String): List<BaseItemDto> {
        val includeTypes = if (kind == "audiobook") "AudioBook" else "Book"
        return jellyfinApi.getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = includeTypes,
            recursive = true,
            limit = 2000,
            sortBy = "SortName",
            sortOrder = "Ascending",
            fields = "PrimaryImageAspectRatio,ProductionYear,DateCreated,Genres,RunTimeTicks,Path,SeriesName,Album,AlbumId,AlbumArtist,Artists,ParentId",
        ).Items
    }

    fun filterBookshelfItems(raw: List<BaseItemDto>, kind: String): List<BaseItemDto> = when (kind) {
        "audiobook" -> collapseAudiobookTracks(raw)
        "ebook" -> raw.filterNot { isComicOrManga(it) }
        "manga" -> raw.filter { isComicOrManga(it) }
        else -> raw
    }

    suspend fun getBookshelfItems(userId: String, parentId: String, kind: String): List<BaseItemDto> =
        filterBookshelfItems(getBookshelfRaw(userId, parentId, kind), kind)

    suspend fun getBookShelfInfo(parentId: String): Map<String, BookShelfInfoDto> =
        catching { api.getBookShelfInfo(parentId) }.getOrDefault(emptyMap())

    suspend fun getBookMetadata(itemId: String): BookMetadataDto? =
        catching {
            val resp = api.getBookMetadata(itemId)
            if (resp.code() == 204 || !resp.isSuccessful) null else resp.body()
        }.getOrNull()

    suspend fun getInProgressReading(limit: Int = 20): List<InProgressReadingDto> =
        catching { api.getInProgressReading(limit) }.getOrDefault(emptyList())

    suspend fun getContinueReadingItems(userId: String, limit: Int = 20): List<Pair<BaseItemDto, InProgressReadingDto>> {
        val entries = getInProgressReading(limit)
        if (entries.isEmpty()) return emptyList()
        val ids = entries.joinToString(",") { it.ItemId }
        val items = catching {
            jellyfinApi.getItemsByIds(
                userId = userId,
                ids = ids,
                fields = "PrimaryImageAspectRatio,RunTimeTicks,Path,SeriesName,ProductionYear",
            ).Items
        }.getOrDefault(emptyList())
        val byId = items.associateBy { normalizeId(it.Id) }
        return entries.mapNotNull { entry ->
            val item = byId[normalizeId(entry.ItemId)] ?: return@mapNotNull null
            item to entry
        }
    }

    suspend fun getContinueListening(userId: String, limit: Int = 20): List<BaseItemDto> =
        catching {
            val items = jellyfinApi.getResumeItems(
                userId = userId,
                limit = limit,
                fields = "PrimaryImageAspectRatio,RunTimeTicks,Album,AlbumId,AlbumArtist,Artists,ParentId,Path",
            ).Items.filter { it.Type.equals("AudioBook", ignoreCase = true) }
            collapseAudiobookTracks(items)
        }.getOrDefault(emptyList())

    suspend fun getAudiobookTracks(userId: String, item: BaseItemDto): List<BaseItemDto> {
        val parentId = item.ParentId ?: return listOf(item)
        val tracks = catching {
            jellyfinApi.getItems(
                userId = userId,
                parentId = parentId,
                includeItemTypes = "AudioBook",
                recursive = false,
                sortBy = "ParentIndexNumber,IndexNumber,SortName",
                sortOrder = "Ascending",
                fields = "RunTimeTicks,Chapters,Path,Album,AlbumId,AlbumArtist,Artists,ParentId",
            ).Items
        }.getOrNull()

        if (tracks.isNullOrEmpty()) {
            val offline = offlineDownloads.getDownload(item.Id, userId)
            if (offline != null) {
                if (offline.tracksSnapshot.isNotEmpty()) {
                    return offline.tracksSnapshot
                }
                if (offline.audiobookTracks.isNotEmpty()) {
                    return offline.audiobookTracks.mapIndexed { idx, t ->
                        BaseItemDto(
                            Id = t.trackId,
                            Name = t.title.ifBlank { "Track ${t.indexNumber ?: (idx + 1)}" },
                            Type = "AudioBook",
                            RunTimeTicks = t.durationTicks.takeIf { it > 0L },
                            IndexNumber = t.indexNumber ?: (idx + 1),
                            ParentId = item.ParentId,
                            Album = item.Album ?: item.Name,
                            AlbumId = item.AlbumId ?: item.Id,
                            AlbumArtist = item.AlbumArtist,
                        )
                    }
                }
            }
            return listOf(item)
        }
        val key = audiobookGroupKey(item)
        val filtered = tracks.filter { audiobookGroupKey(it) == key }
        return filtered.ifEmpty { listOf(item) }
    }

    suspend fun getAudiobookChapters(itemId: String): List<AudiobookChapterDto> =
        catching {
            val server = sessionManager.serverAddress()?.trimEnd('/') ?: return@catching emptyList()
            val url = "$server/AudiobookLibrary/Books/$itemId/Chapters"
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string() ?: return@use emptyList()
                    val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, AudiobookChapterDto::class.java)
                    val adapter = moshi.adapter<List<AudiobookChapterDto>>(type)
                    adapter.fromJson(body).orEmpty()
                }
            }
        }.getOrDefault(emptyList())

    // --- Reading progress & activity ---

    suspend fun getAllReadingProgress(): Map<String, ReadingProgressDto> =
        catching { api.getAllReadingProgress() }.getOrDefault(emptyMap())

    suspend fun getReadingProgress(itemId: String): ReadingProgressDto {
        val uid = currentUserId() ?: "default"
        val local = offlineDownloads.readLocalProgress(uid, itemId)
        val remote = catching { api.getReadingProgress(itemId) }.getOrNull()
        if (remote == null) return local ?: ReadingProgressDto()
        if (local != null && local.Locator.isNotBlank()) {
            val localTime = local.UpdatedAt.orEmpty()
            val remoteTime = remote.UpdatedAt.orEmpty()
            if (remote.Locator.isBlank() || localTime > remoteTime) {
                return local
            }
        }
        return remote
    }

    suspend fun saveReadingProgress(
        itemId: String,
        locator: String,
        progress: Double,
        totalPages: Int? = null,
    ): ReadingProgressDto? {
        val uid = currentUserId() ?: "default"
        val clamped = progress.coerceIn(0.0, 1.0)
        val localDto = ReadingProgressDto(
            Locator = locator,
            Progress = clamped,
            TotalPages = totalPages,
            UpdatedAt = OfflineDownloadManager.isoNowUtc(),
        )
        offlineDownloads.saveLocalProgress(uid, itemId, localDto)

        val request = SaveReadingProgressRequest(
            Locator = locator,
            Progress = clamped,
            TotalPages = totalPages,
        )
        val result = catching { api.saveReadingProgress(itemId, request) }
        if (result.isFailure) {
            offlineDownloads.queueMutation(
                method = "POST",
                path = "Jellio/reading/progress/${normalizeId(itemId)}",
                bodyJson = progressSaveAdapter.toJson(request),
                userIdOverride = uid,
            )
        }
        offlineDownloads.onProgressUpdated(itemId, clamped, "book")
        return result.getOrDefault(localDto)
    }

    suspend fun markReadingProgress(itemIds: List<String>, read: Boolean): Boolean {
        val cleaned = itemIds.map { normalizeId(it) }.distinct()
        if (cleaned.isEmpty()) return true
        return catching {
            api.markReadingProgress(MarkReadingProgressRequest(cleaned, read)).isSuccessful
        }.getOrDefault(false)
    }

    suspend fun reportReadingSession(request: ReadingSessionRequest) {
        val uid = currentUserId() ?: "default"
        val res = catching { api.reportReadingSession(request) }
        if (res.isFailure) {
            offlineDownloads.queueMutation(
                method = "POST",
                path = "Jellio/reading/session",
                bodyJson = sessionAdapter.toJson(request),
                userIdOverride = uid,
            )
        }
    }

    suspend fun reportNowReading(request: ReadingNowPlayingRequest) {
        catching { api.reportReadingNowPlaying(request) }
    }

    suspend fun clearNowReading(itemId: String) {
        catching { api.clearReadingNowPlaying(itemId) }
    }

    // --- Book / CBZ / PDF / EPUB file loading (offline-first, disk-cached) ---

    suspend fun openBookFile(itemId: String, pathHint: String? = null): Result<OpenedBookFile> {
        val uid = currentUserId() ?: "default"
        val offlineFile = offlineDownloads.getCompletedFile(itemId, uid)
        if (offlineFile != null) {
            return Result.success(
                OpenedBookFile(
                    file = offlineFile,
                    format = detectBookFormat(offlineFile, pathHint),
                    fromOfflineDownload = true,
                ),
            )
        }

        val normalizedId = normalizeId(itemId)
        val cached = findCachedBookFile(normalizedId)
        if (cached != null) {
            return Result.success(
                OpenedBookFile(
                    file = cached,
                    format = detectBookFormat(cached, pathHint),
                    fromOfflineDownload = false,
                ),
            )
        }

        return withContext(Dispatchers.IO) {
            catching {
                val server = sessionManager.serverAddress()?.trimEnd('/')
                    ?: throw IOException("No server configured")
                val url = "$server/Jellio/reading/file/$normalizedId"
                val tmp = File(bookCacheDir, "$normalizedId.tmp")
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} loading book")
                    }
                    val body = response.body ?: throw IOException("Empty book response")
                    body.byteStream().use { input ->
                        FileOutputStream(tmp).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    }
                    val contentType = response.header("Content-Type").orEmpty()
                    val format = detectBookFormat(tmp, pathHint, contentType)
                    val ext = when (format) {
                        BookFileFormat.EPUB -> "epub"
                        BookFileFormat.PDF -> "pdf"
                        BookFileFormat.CBZ -> "cbz"
                        BookFileFormat.UNKNOWN -> "bin"
                    }
                    val finalFile = File(bookCacheDir, "$normalizedId.$ext")
                    if (finalFile.exists()) finalFile.delete()
                    if (!tmp.renameTo(finalFile)) {
                        tmp.copyTo(finalFile, overwrite = true)
                        tmp.delete()
                    }
                    OpenedBookFile(file = finalFile, format = format, fromOfflineDownload = false)
                }
            }
        }
    }

    suspend fun openStreamChapterCbz(chapterId: Int, itemId: String): Result<OpenedBookFile> {
        val uid = currentUserId() ?: "default"
        val offlineFile = offlineDownloads.getCompletedFile(itemId, uid)
        if (offlineFile != null) {
            return Result.success(OpenedBookFile(offlineFile, BookFileFormat.CBZ, fromOfflineDownload = true))
        }

        val cached = File(bookCacheDir, "stream_ch_$chapterId.cbz")
        if (cached.exists() && cached.length() > 0L) {
            return Result.success(OpenedBookFile(cached, BookFileFormat.CBZ, fromOfflineDownload = false))
        }

        return withContext(Dispatchers.IO) {
            catching {
                val server = sessionManager.serverAddress()?.trimEnd('/')
                    ?: throw IOException("No server configured")
                val url = "$server/Jellio/manga/stream/chapter/$chapterId/cbz"
                val tmp = File(bookCacheDir, "stream_ch_$chapterId.tmp")
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} downloading manga chapter")
                    }
                    val body = response.body ?: throw IOException("Empty chapter response")
                    body.byteStream().use { input ->
                        FileOutputStream(tmp).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    }
                    if (cached.exists()) cached.delete()
                    if (!tmp.renameTo(cached)) {
                        tmp.copyTo(cached, overwrite = true)
                        tmp.delete()
                    }
                    OpenedBookFile(file = cached, format = BookFileFormat.CBZ, fromOfflineDownload = false)
                }
            }
        }
    }

    // --- Annotations ---

    suspend fun getAnnotations(itemId: String): List<AnnotationDto> {
        val uid = currentUserId() ?: "default"
        val remote = catching { api.getAnnotations(itemId) }.getOrNull()
        if (remote != null) {
            offlineDownloads.saveLocalAnnotations(uid, itemId, remote)
            return remote
        }
        return offlineDownloads.readLocalAnnotations(uid, itemId)
    }

    suspend fun addAnnotation(itemId: String, request: CreateAnnotationRequest): AnnotationDto {
        val uid = currentUserId() ?: "default"
        val remote = catching { api.addAnnotation(itemId, request) }.getOrNull()
        if (remote != null) {
            val existing = offlineDownloads.readLocalAnnotations(uid, itemId)
            offlineDownloads.saveLocalAnnotations(uid, itemId, existing + remote)
            return remote
        }
        val local = AnnotationDto(
            Id = "offline-${System.currentTimeMillis()}",
            Kind = request.Kind,
            Locator = request.Locator,
            Position = request.Position,
            Text = request.Text,
            Note = request.Note,
            Color = if (request.Kind == "highlight") request.Color ?: "yellow" else null,
            Chapter = request.Chapter,
            CreatedAt = OfflineDownloadManager.isoNowUtc(),
        )
        val existing = offlineDownloads.readLocalAnnotations(uid, itemId)
        offlineDownloads.saveLocalAnnotations(uid, itemId, existing + local)
        offlineDownloads.queueMutation(
            method = "POST",
            path = "Jellio/reading/annotations/${normalizeId(itemId)}",
            bodyJson = createAnnotationAdapter.toJson(request),
            userIdOverride = uid,
        )
        return local
    }

    suspend fun updateAnnotation(itemId: String, id: String, note: String?, color: String?): AnnotationDto? {
        val uid = currentUserId() ?: "default"
        val body = UpdateAnnotationRequest(Note = note, Color = color)
        val remote = catching { api.updateAnnotation(itemId, id, body) }.getOrNull()
        val existing = offlineDownloads.readLocalAnnotations(uid, itemId)
        val updatedList = existing.map {
            if (it.Id == id) it.copy(Note = note ?: it.Note, Color = color ?: it.Color) else it
        }
        offlineDownloads.saveLocalAnnotations(uid, itemId, updatedList)
        if (remote == null && !id.startsWith("offline-")) {
            offlineDownloads.queueMutation(
                method = "PUT",
                path = "Jellio/reading/annotations/${normalizeId(itemId)}/${URLEncoder.encode(id, "UTF-8")}",
                bodyJson = updateAnnotationAdapter.toJson(body),
                userIdOverride = uid,
            )
        }
        return remote ?: updatedList.firstOrNull { it.Id == id }
    }

    suspend fun deleteAnnotation(itemId: String, id: String): Boolean {
        val uid = currentUserId() ?: "default"
        val existing = offlineDownloads.readLocalAnnotations(uid, itemId)
        offlineDownloads.saveLocalAnnotations(uid, itemId, existing.filterNot { it.Id == id })
        val res = catching { api.deleteAnnotation(itemId, id).isSuccessful }
        if (res.isFailure && !id.startsWith("offline-")) {
            offlineDownloads.queueMutation(
                method = "DELETE",
                path = "Jellio/reading/annotations/${normalizeId(itemId)}/${URLEncoder.encode(id, "UTF-8")}",
                bodyJson = null,
                userIdOverride = uid,
            )
        }
        return res.getOrDefault(true)
    }

    // --- Language & Vocabulary SRS ---

    suspend fun translate(text: String, targetLang: String, sourceLang: String? = null, contextSentence: String? = null): Result<TranslateResponseDto> =
        catching { api.translate(TranslateRequest(text, targetLang, sourceLang, contextSentence)) }

    suspend fun defineWord(word: String, lang: String? = null): Result<List<DictionarySenseDto>> =
        catching { api.defineWord(word, lang) }

    suspend fun getVocabulary(): Result<List<VocabEntryDto>> =
        catching { api.getVocabulary() }

    suspend fun addVocabulary(request: CreateVocabRequest): Result<VocabEntryDto> {
        val uid = currentUserId() ?: "default"
        val res = catching { api.addVocabulary(request) }
        if (res.isFailure) {
            offlineDownloads.queueMutation(
                method = "POST",
                path = "Jellio/vocab",
                bodyJson = createVocabAdapter.toJson(request),
                userIdOverride = uid,
            )
        }
        return res
    }

    suspend fun updateVocabulary(id: String, translation: String?, definition: String?, sentence: String?): Result<VocabEntryDto> =
        catching { api.updateVocabulary(id, UpdateVocabRequest(translation, definition, sentence)) }

    suspend fun reviewVocabulary(id: String, grade: Int): Result<VocabEntryDto> =
        catching { api.reviewVocabulary(id, ReviewVocabRequest(grade)) }

    suspend fun deleteVocabulary(id: String): Result<Unit> =
        catching {
            api.deleteVocabulary(id)
            Unit
        }

    // --- Shelf (categories, library membership, bookmarks, series prefs) ---

    suspend fun getShelf(kind: String): ShelfDto =
        catching { api.getShelf(kind) }.getOrDefault(ShelfDto())

    suspend fun createShelfCategory(kind: String, name: String): Result<ShelfCategoryDto> =
        catching { api.createShelfCategory(kind, CreateShelfCategoryRequest(name)) }

    suspend fun updateShelfCategory(id: String, name: String? = null, sort: String? = null, descending: Boolean? = null): Result<ShelfCategoryDto> =
        catching { api.updateShelfCategory(id, UpdateShelfCategoryRequest(name, sort, descending)) }

    suspend fun deleteShelfCategory(id: String): Result<Unit> =
        catching {
            api.deleteShelfCategory(id)
            Unit
        }

    suspend fun updateShelfMembership(kind: String, keys: List<String>, categoryIds: List<String>): Result<Unit> =
        catching {
            val formattedKeys = keys.map { raw ->
                when {
                    raw.startsWith("i:") || raw.startsWith("s:") -> raw
                    kind == "manga" -> "s:$raw"
                    else -> "i:${normalizeId(raw)}"
                }
            }
            api.updateShelfMembership(kind, ShelfMembershipRequest(Keys = formattedKeys, CategoryIds = categoryIds))
            Unit
        }

    suspend fun updateSeriesPrefs(request: UpdateSeriesPrefsRequest): Result<SeriesPrefsDto> =
        catching { api.updateSeriesPrefs(request) }

    suspend fun setMangaInLibrary(seriesKey: String, inLibrary: Boolean): Result<Unit> =
        catching {
            val key = if (seriesKey.startsWith("s:")) seriesKey else "s:$seriesKey"
            api.updateShelfLibrary(UpdateShelfLibraryRequest(key, inLibrary))
            Unit
        }

    suspend fun setChapterBookmark(itemId: String, bookmarked: Boolean): Result<Unit> =
        catching {
            api.updateShelfBookmark(normalizeId(itemId), UpdateShelfBookmarkRequest(bookmarked))
            Unit
        }

    // --- Books Discover, Search, Requests ---

    suspend fun searchBooks(query: String, mediaType: String? = null): Result<List<BookSearchResultDto>> =
        catching { api.searchBooks(query, mediaType) }

    suspend fun discoverBooks(source: String = "trending", value: String? = null, page: Int = 0): Result<List<BookDiscoverResultDto>> =
        catching { api.discoverBooks(source, value, page) }

    suspend fun requestBook(result: BookSearchResultDto, bookType: String): Result<BookRequestResultDto> =
        catching { api.requestBook(BookRequestBody(result.WorkId, bookType, result.Title, result.Author)) }

    suspend fun requestBook(result: BookDiscoverResultDto, bookType: String): Result<BookRequestResultDto> =
        catching { api.requestBook(BookRequestBody(result.WorkId, bookType, result.Title, result.Author)) }

    suspend fun getBookRequests(mediaType: String): Result<BookRequestsResponseDto> =
        catching { api.getBookRequests(mediaType) }

    // Takes a book off this reader's shelf; deletes it server side when nobody else has it.
    suspend fun removeBookFromLibrary(mediaType: String, itemId: String): Result<BookRemoveResultDto> =
        catching { api.removeBookFromLibrary(BookRemoveRequest(MediaType = mediaType, ItemId = itemId)) }

    // An audiobook is a Continue Listening entry through the resume position
    // of its tracks; zeroing them drops it without marking it played.
    suspend fun clearAudiobookResume(userId: String, item: BaseItemDto): Boolean {
        val tracks = catching { getAudiobookTracks(userId, item) }.getOrDefault(listOf(item))
        var ok = true
        for (track in tracks) {
            val result = catching { jellyfinApi.updateUserData(track.Id, userId, com.jellio.android.data.model.UserDataUpdate(0L)) }
            if (result.isFailure) ok = false
        }
        return ok
    }

    suspend fun markItemUnplayed(userId: String, itemId: String): Boolean =
        catching { jellyfinApi.markUnplayed(userId, itemId) }.isSuccess

    // Manga has no server side continue list to edit, so dismissals live on
    // this device and lapse once the series is read again.
    fun mangaContinueDismissals(): Set<String> =
        readerPrefs.getStringSet(PREF_MANGA_CONTINUE_DISMISSED, emptySet()).orEmpty().toSet()

    fun dismissMangaContinue(seriesKey: String, lastReadAt: String?) {
        val next = withMangaDismissal(mangaContinueDismissals(), seriesKey, lastReadAt)
        readerPrefs.edit().putStringSet(PREF_MANGA_CONTINUE_DISMISSED, next).apply()
    }

    suspend fun searchBookAgain(chaptarrBookId: Int): Result<Unit> =
        catching {
            api.searchBookAgain(chaptarrBookId)
            Unit
        }

    // --- Manga Stream, Updates, Discover, Search, Requests ---

    suspend fun getMangaStatus(): MangaStatusDto =
        catching { api.getMangaStatus() }.getOrDefault(MangaStatusDto())

    suspend fun getMangaStreamLibrary(): Result<List<MangaSeriesSummaryDto>> =
        catching { api.getMangaStreamLibrary() }

    suspend fun getMangaUpdates(): Result<MangaUpdatesResponseDto> =
        catching { api.getMangaUpdates() }

    suspend fun refreshMangaUpdates(): Result<MangaUpdatesResponseDto> =
        catching { api.refreshMangaUpdates() }

    suspend fun findMangaStreamByKey(key: String): MangaByKeyDto? =
        catching { api.getMangaStreamByKey(key) }.getOrNull()

    suspend fun getMangaStreamSeries(mangaId: Int): Result<MangaStreamSeriesDto> =
        catching { api.getMangaStreamSeries(mangaId) }

    suspend fun getMangaStreamChapter(id: String, mangaId: Int? = null): Result<MangaStreamChapterDto> =
        catching { api.getMangaStreamChapter(id, mangaId) }

    suspend fun getMangaStreamPageCount(chapterId: Int): Int =
        catching { api.getMangaStreamChapterPages(chapterId).Count }.getOrDefault(0)

    suspend fun discoverManga(
        sort: String = "trending",
        genre: String? = null,
        country: String? = null,
        query: String? = null,
        page: Int = 0,
    ): Result<List<MangaDiscoverDto>> =
        catching { api.discoverManga(sort, genre, country, query, page) }

    suspend fun searchManga(query: String): Result<MangaSearchResponseDto> =
        catching { api.searchManga(query) }

    suspend fun requestManga(mangaId: Int, title: String?, addToLibrary: Boolean = true, force: Boolean = false): Result<MangaRequestResultDto> =
        catching { api.requestManga(MangaRequestBody(mangaId, title, addToLibrary, force)) }

    suspend fun migrateManga(fromMangaId: Int, toMangaId: Int, toTitle: String? = null): Result<MangaMigrateResultDto> =
        catching { api.migrateManga(MangaMigrateBody(fromMangaId, toMangaId, toTitle)) }

    suspend fun syncTracker(seriesKey: String?, mangaId: Int?): Result<Unit> =
        catching {
            val cleanKey = seriesKey?.removePrefix("s:")?.takeIf { it.isNotBlank() }
            if (cleanKey != null || mangaId != null) {
                api.syncTracker(TrackerSyncRequest(Key = cleanKey, MangaId = mangaId))
            }
            Unit
        }

    // --- URL builders ---

    suspend fun bookCoverUrl(item: BaseItemDto): String? = bookCoverUrls(listOf(item))[normalizeId(item.Id)]

    // Covers for a whole shelf: session, token and downloads are looked up
    // once instead of once per item. Keyed by normalized item id.
    suspend fun bookCoverUrls(items: List<BaseItemDto>): Map<String, String> {
        if (items.isEmpty()) return emptyMap()
        val uid = currentUserId() ?: "default"
        val server = sessionManager.serverAddress()?.trimEnd('/')
        val token = sessionManager.accessToken()
        val hasDownloads = offlineDownloads.downloads.value.isNotEmpty()
        val out = HashMap<String, String>(items.size)
        for (item in items) {
            val offlineCover = if (hasDownloads) {
                offlineDownloads.getCompletedCoverFile(item.Id, uid)
                    ?: item.AlbumId?.let { offlineDownloads.getCompletedCoverFile(it, uid) }
            } else {
                null
            }
            val url = when {
                offlineCover != null -> offlineCover.toURI().toString()
                server != null -> bookCoverUrlFor(item, server, token)
                else -> null
            }
            if (url != null) out[normalizeId(item.Id)] = url
        }
        return out
    }

    suspend fun bookCoverUrl(itemId: String): String? {
        val uid = currentUserId() ?: "default"
        val offlineCover = offlineDownloads.getCompletedCoverFile(itemId, uid)
        if (offlineCover != null) return offlineCover.toURI().toString()
        val server = sessionManager.serverAddress()?.trimEnd('/') ?: return null
        val token = sessionManager.accessToken()
        return "$server/Jellio/books/item/${normalizeId(itemId)}/cover" +
            if (!token.isNullOrEmpty()) "?ApiKey=$token&api_key=$token" else ""
    }

    suspend fun mangaSeriesCoverUrl(item: BaseItemDto, title: String? = null): String? {
        val uid = currentUserId() ?: "default"
        val offlineCover = offlineDownloads.getCompletedCoverFile(item.Id, uid)
        if (offlineCover != null) return offlineCover.toURI().toString()
        val server = sessionManager.serverAddress()?.trimEnd('/') ?: return null
        val token = sessionManager.accessToken()
        val primaryTag = item.ImageTags?.get("Primary")
        if (!primaryTag.isNullOrBlank()) {
            val base = "$server/Items/${item.Id}/Images/Primary?maxWidth=500&quality=90&tag=$primaryTag"
            return if (!token.isNullOrEmpty()) "$base&ApiKey=$token&api_key=$token" else base
        }
        val sb = StringBuilder("$server/Jellio/manga/series-cover/${normalizeId(item.Id)}")
        var hasQuery = false
        if (!token.isNullOrEmpty()) {
            sb.append("?ApiKey=").append(token).append("&api_key=").append(token)
            hasQuery = true
        }
        if (!title.isNullOrBlank()) {
            sb.append(if (hasQuery) "&" else "?").append("title=").append(URLEncoder.encode(title, "UTF-8"))
        }
        return sb.toString()
    }

    suspend fun importMihonBackup(bytes: ByteArray): Result<MangaImportStatusDto> = catching {
        val body = okhttp3.RequestBody.Companion.run {
            bytes.toRequestBody(okhttp3.MediaType.Companion.run { "application/octet-stream".toMediaType() })
        }
        api.importMihonBackup(body)
    }

    suspend fun getMihonImportStatus(): Result<MangaImportStatusDto> = catching {
        api.getMihonImportStatus()
    }

    suspend fun mangaStreamCoverUrl(mangaId: Int, title: String? = null): String? {
        val server = sessionManager.serverAddress()?.trimEnd('/') ?: return null
        val token = sessionManager.accessToken()
        val sb = StringBuilder("$server/Jellio/manga/thumbnail/$mangaId")
        var hasQuery = false
        if (!token.isNullOrEmpty()) {
            sb.append("?ApiKey=").append(token).append("&api_key=").append(token)
            hasQuery = true
        }
        if (!title.isNullOrBlank()) {
            sb.append(if (hasQuery) "&" else "?").append("title=").append(URLEncoder.encode(title, "UTF-8"))
        }
        return sb.toString()
    }

    suspend fun resolveDiscoverCoverUrl(
        rawUrl: String?,
        mangaId: Int? = null,
        title: String? = null,
    ): String? {
        if (mangaId != null && mangaId > 0) {
            return mangaStreamCoverUrl(mangaId, title)
        }
        val trimmed = rawUrl?.trim()
        if (trimmed.isNullOrEmpty()) return null
        val server = sessionManager.serverAddress()?.trimEnd('/')
        val token = sessionManager.accessToken()
        if (trimmed.startsWith("/")) {
            if (server.isNullOrBlank()) return null
            val sep = if (trimmed.contains('?')) "&" else "?"
            val auth = if (!token.isNullOrEmpty()) "${sep}ApiKey=$token&api_key=$token" else ""
            return "$server$trimmed$auth"
        }
        if (trimmed.contains("anilist.co", ignoreCase = true) && !server.isNullOrBlank()) {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val auth = if (!token.isNullOrEmpty()) "&ApiKey=$token&api_key=$token" else ""
            return "$server/Jellio/books/manga-cover?url=$encoded$auth"
        }
        return trimmed
    }

    suspend fun mangaStreamPageUrl(chapterId: Int, pageIndex: Int): String? {
        val server = sessionManager.serverAddress()?.trimEnd('/') ?: return null
        val token = sessionManager.accessToken()
        return "$server/Jellio/manga/stream/chapter/$chapterId/page/$pageIndex" +
            if (!token.isNullOrEmpty()) "?ApiKey=$token&api_key=$token" else ""
    }

    suspend fun audiobookStreamUrl(trackId: String): String? {
        val uid = currentUserId() ?: "default"
        val offlineTrack = offlineDownloads.getCompletedTrackFile(trackId, trackId, uid)
            ?: offlineDownloads.getCompletedFile(trackId, uid)
        if (offlineTrack != null) return offlineTrack.toURI().toString()
        val server = sessionManager.serverAddress()?.trimEnd('/') ?: return null
        val token = sessionManager.accessToken()
        val deviceId = sessionManager.getCachedDeviceId()
        return "$server/Audio/$trackId/stream?static=true&DeviceId=$deviceId" +
            if (!token.isNullOrEmpty()) "&ApiKey=$token&api_key=$token" else ""
    }

    // --- Helpers ---

    private fun findCachedBookFile(normalizedId: String): File? {
        val candidates = listOf("epub", "pdf", "cbz", "bin")
        for (ext in candidates) {
            val file = File(bookCacheDir, "$normalizedId.$ext")
            if (file.exists() && file.length() > 0L) return file
        }
        return null
    }

    private fun detectBookFormat(
        file: File,
        pathHint: String? = null,
        contentType: String = "",
    ): BookFileFormat {
        val ext = (pathHint ?: file.name).substringAfterLast('.', "").lowercase(Locale.US)
        if (ext == "pdf") return BookFileFormat.PDF
        if (ext in listOf("cbz", "cbr", "cb7", "cbt")) return BookFileFormat.CBZ

        val header = ByteArray(4)
        val bytesRead = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(0)
        if (bytesRead >= 4 &&
            header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() &&
            header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()
        ) {
            return BookFileFormat.PDF
        }
        if (bytesRead >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
            val zipFormat = runCatching {
                java.util.zip.ZipFile(file).use { z ->
                    val hasContainer = z.getEntry("META-INF/container.xml") != null
                    val mimeEntry = z.getEntry("mimetype")
                    val hasEpubMime = mimeEntry != null &&
                        z.getInputStream(mimeEntry).use { it.readBytes().toString(Charsets.UTF_8) }
                            .contains("epub", ignoreCase = true)
                    if (hasContainer || hasEpubMime) {
                        BookFileFormat.EPUB
                    } else {
                        var hasImages = false
                        val entries = z.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            if (!e.isDirectory && CbzBookEngine.isSupportedImage(e.name)) {
                                hasImages = true
                                break
                            }
                        }
                        if (hasImages || ext != "epub") BookFileFormat.CBZ else BookFileFormat.EPUB
                    }
                }
            }.getOrNull()
            if (zipFormat != null) return zipFormat
        }
        if (ext == "epub") return BookFileFormat.EPUB
        if (contentType.contains("epub", ignoreCase = true)) return BookFileFormat.EPUB
        if (contentType.contains("pdf", ignoreCase = true)) return BookFileFormat.PDF
        if (contentType.contains("comicbook", ignoreCase = true)) return BookFileFormat.CBZ
        return BookFileFormat.UNKNOWN
    }

    companion object {
        private const val PREF_MANGA_CONTINUE_DISMISSED = "manga_continue_dismissed"

        private val COMIC_REGEX = Regex("""\.(cbz|cbr|cb7|cbt)$""", RegexOption.IGNORE_CASE)
        private val CHAPTER_NAME_REGEX = Regex(
            """\b(ch(apter)?|ep(isode)?|vol(ume)?|issue|part)\s*\d+|\b(prologue|epilogue|oneshot|one-shot)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val COMIC_PATH_REGEX = Regex(
            """(/|\\)(manga|comics?|manhwa|manhua|webtoons?)(/|\\)""",
            RegexOption.IGNORE_CASE,
        )
        private val COMIC_GENRES = setOf("manga", "comic", "comics", "manhwa", "manhua", "webtoon", "webtoons")
        private val NON_ALNUM_REGEX = Regex("""[^a-z0-9]+""")

        fun isComicItem(item: BaseItemDto): Boolean =
            !item.Path.isNullOrBlank() && COMIC_REGEX.containsMatchIn(item.Path)

        fun isComicOrManga(item: BaseItemDto): Boolean {
            if (isComicItem(item)) return true
            if (!item.SeriesName.isNullOrBlank()) return true
            if (!item.Path.isNullOrBlank() && COMIC_PATH_REGEX.containsMatchIn(item.Path)) return true
            if (!item.Name.isNullOrBlank() && CHAPTER_NAME_REGEX.containsMatchIn(item.Name)) return true
            if (item.Genres?.any { it.trim().lowercase(Locale.US) in COMIC_GENRES } == true) return true
            return false
        }

        fun normalizeId(id: String): String =
            id.replace("-", "").lowercase(Locale.US)

        fun mangaSeriesKey(title: String): String {
            val decomposed = Normalizer.normalize(title.lowercase(Locale.US), Normalizer.Form.NFD)
            val stripped = buildString(decomposed.length) {
                for (c in decomposed) {
                    if (c !in '\u0300'..'\u036f') append(c)
                }
            }
            return NON_ALNUM_REGEX.replace(stripped, " ").trim()
        }

        fun audiobookGroupKey(item: BaseItemDto): String =
            "${item.ParentId.orEmpty()}|${item.Album.orEmpty()}"

        fun audiobookTitle(item: BaseItemDto, trackCount: Int = 1): String {
            if (!item.Album.isNullOrBlank()) return item.Album
            if (trackCount > 1 && !item.Path.isNullOrBlank()) {
                val parentName = File(item.Path).parentFile?.name
                if (!parentName.isNullOrBlank()) return parentName
            }
            return item.Name.orEmpty()
        }

        fun collapseAudiobookTracks(items: List<BaseItemDto>): List<BaseItemDto> {
            val counts = HashMap<String, Int>()
            for (item in items) {
                if (item.Type.equals("AudioBook", ignoreCase = true)) {
                    val key = audiobookGroupKey(item)
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
            val seen = HashSet<String>()
            val out = mutableListOf<BaseItemDto>()
            for (item in items) {
                if (!item.Type.equals("AudioBook", ignoreCase = true)) {
                    out.add(item)
                    continue
                }
                val key = audiobookGroupKey(item)
                if (!seen.add(key)) continue
                val title = audiobookTitle(item, counts[key] ?: 1)
                out.add(if (title.isNotBlank() && title != item.Name) item.copy(Name = title) else item)
            }
            return out
        }
    }
}
