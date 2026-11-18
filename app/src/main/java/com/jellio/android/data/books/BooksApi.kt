package com.jellio.android.data.books

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface BooksApi {
    // --- Reading progress & activity ---

    @GET("Jellio/reading/progress/{itemId}")
    suspend fun getReadingProgress(@Path("itemId") itemId: String): ReadingProgressDto

    @POST("Jellio/reading/progress/{itemId}")
    suspend fun saveReadingProgress(
        @Path("itemId") itemId: String,
        @Body body: SaveReadingProgressRequest,
    ): ReadingProgressDto

    @POST("Jellio/reading/progress/mark")
    suspend fun markReadingProgress(@Body body: MarkReadingProgressRequest): Response<Unit>

    @GET("Jellio/reading/progress")
    suspend fun getAllReadingProgress(): Map<String, ReadingProgressDto>

    @GET("Jellio/reading/in-progress")
    suspend fun getInProgressReading(@Query("limit") limit: Int = 20): List<InProgressReadingDto>

    @Streaming
    @GET("Jellio/reading/file/{itemId}")
    suspend fun downloadReadingFile(@Path("itemId") itemId: String): ResponseBody

    @POST("Jellio/reading/session")
    suspend fun reportReadingSession(@Body body: ReadingSessionRequest): Response<Unit>

    // --- Now playing (reading) ---

    @POST("Jellio/now-playing/reading")
    suspend fun reportReadingNowPlaying(@Body body: ReadingNowPlayingRequest): Response<Unit>

    @DELETE("Jellio/now-playing/reading/{itemId}")
    suspend fun clearReadingNowPlaying(@Path("itemId") itemId: String): Response<Unit>

    // --- Annotations (highlights & bookmarks) ---

    @GET("Jellio/reading/annotations/{itemId}")
    suspend fun getAnnotations(@Path("itemId") itemId: String): List<AnnotationDto>

    @POST("Jellio/reading/annotations/{itemId}")
    suspend fun addAnnotation(
        @Path("itemId") itemId: String,
        @Body body: CreateAnnotationRequest,
    ): AnnotationDto

    @PUT("Jellio/reading/annotations/{itemId}/{id}")
    suspend fun updateAnnotation(
        @Path("itemId") itemId: String,
        @Path("id") id: String,
        @Body body: UpdateAnnotationRequest,
    ): AnnotationDto

    @DELETE("Jellio/reading/annotations/{itemId}/{id}")
    suspend fun deleteAnnotation(
        @Path("itemId") itemId: String,
        @Path("id") id: String,
    ): Response<Unit>

    // --- Language & dictionary ---

    @POST("Jellio/language/translate")
    suspend fun translate(@Body body: TranslateRequest): TranslateResponseDto

    @GET("Jellio/language/define")
    suspend fun defineWord(
        @Query("word") word: String,
        @Query("lang") lang: String? = null,
    ): List<DictionarySenseDto>

    // --- Vocabulary SRS ---

    @GET("Jellio/vocab")
    suspend fun getVocabulary(): List<VocabEntryDto>

    @POST("Jellio/vocab")
    suspend fun addVocabulary(@Body body: CreateVocabRequest): VocabEntryDto

    @PUT("Jellio/vocab/{id}")
    suspend fun updateVocabulary(
        @Path("id") id: String,
        @Body body: UpdateVocabRequest,
    ): VocabEntryDto

    @POST("Jellio/vocab/{id}/review")
    suspend fun reviewVocabulary(
        @Path("id") id: String,
        @Body body: ReviewVocabRequest,
    ): VocabEntryDto

    @DELETE("Jellio/vocab/{id}")
    suspend fun deleteVocabulary(@Path("id") id: String): Response<Unit>

    @Streaming
    @GET("Jellio/vocab/export")
    suspend fun exportVocabulary(@Query("format") format: String = "csv"): ResponseBody

    // --- Shelf categories, library, bookmarks, series prefs ---

    @GET("Jellio/shelf/{kind}")
    suspend fun getShelf(@Path("kind") kind: String): ShelfDto

    @POST("Jellio/shelf/{kind}/categories")
    suspend fun createShelfCategory(
        @Path("kind") kind: String,
        @Body body: CreateShelfCategoryRequest,
    ): ShelfCategoryDto

    @PATCH("Jellio/shelf/categories/{id}")
    suspend fun updateShelfCategory(
        @Path("id") id: String,
        @Body body: UpdateShelfCategoryRequest,
    ): ShelfCategoryDto

    @DELETE("Jellio/shelf/categories/{id}")
    suspend fun deleteShelfCategory(@Path("id") id: String): Response<Unit>

    @PUT("Jellio/shelf/{kind}/order")
    suspend fun orderShelfCategories(
        @Path("kind") kind: String,
        @Body body: ShelfOrderRequest,
    ): Response<Unit>

    @PUT("Jellio/shelf/{kind}/membership")
    suspend fun updateShelfMembership(
        @Path("kind") kind: String,
        @Body body: ShelfMembershipRequest,
    ): Response<Unit>

    @PUT("Jellio/shelf/series")
    suspend fun updateSeriesPrefs(@Body body: UpdateSeriesPrefsRequest): SeriesPrefsDto

    @PUT("Jellio/shelf/series/defaults")
    suspend fun updateSeriesDefaults(@Body body: UpdateSeriesDefaultsRequest): Response<Unit>

    @PUT("Jellio/shelf/library")
    suspend fun updateShelfLibrary(@Body body: UpdateShelfLibraryRequest): Response<Unit>

    @PUT("Jellio/shelf/bookmarks/{itemId}")
    suspend fun updateShelfBookmark(
        @Path("itemId") itemId: String,
        @Body body: UpdateShelfBookmarkRequest,
    ): Response<Unit>

    // --- Books metadata, search, discover, requests ---

    @GET("Jellio/books/shelf-info")
    suspend fun getBookShelfInfo(@Query("parentId") parentId: String): Map<String, BookShelfInfoDto>

    @GET("Jellio/books/item/{itemId}/metadata")
    suspend fun getBookMetadata(@Path("itemId") itemId: String): Response<BookMetadataDto>

    @GET("Jellio/books/search")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("mediaType") mediaType: String? = null,
    ): List<BookSearchResultDto>

    @GET("Jellio/books/discover")
    suspend fun discoverBooks(
        @Query("source") source: String,
        @Query("value") value: String? = null,
        @Query("page") page: Int = 0,
    ): List<BookDiscoverResultDto>

    @GET("Jellio/books/discover-manga")
    suspend fun discoverManga(
        @Query("sort") sort: String? = null,
        @Query("genre") genre: String? = null,
        @Query("country") country: String? = null,
        @Query("q") query: String? = null,
        @Query("page") page: Int = 0,
    ): List<MangaDiscoverDto>

    @POST("Jellio/books/request")
    suspend fun requestBook(@Body body: BookRequestBody): BookRequestResultDto

    @GET("Jellio/books/requests")
    suspend fun getBookRequests(@Query("mediaType") mediaType: String): BookRequestsResponseDto

    @POST("Jellio/books/requests/{bookId}/search")
    suspend fun searchBookAgain(@Path("bookId") bookId: Int): Response<Unit>

    @POST("Jellio/books/library/remove")
    suspend fun removeBookFromLibrary(@Body body: BookRemoveRequest): Response<Unit>

    // --- Manga stream & requests ---

    @GET("Jellio/manga/status")
    suspend fun getMangaStatus(): MangaStatusDto

    @GET("Jellio/manga/search")
    suspend fun searchManga(@Query("q") query: String): MangaSearchResponseDto

    @POST("Jellio/manga/request")
    suspend fun requestManga(@Body body: MangaRequestBody): MangaRequestResultDto

    @POST("Jellio/manga/migrate")
    suspend fun migrateManga(@Body body: MangaMigrateBody): MangaMigrateResultDto

    @GET("Jellio/manga/stream/library")
    suspend fun getMangaStreamLibrary(): List<MangaSeriesSummaryDto>

    @GET("Jellio/manga/stream/updates")
    suspend fun getMangaUpdates(): MangaUpdatesResponseDto

    @POST("Jellio/manga/stream/updates/refresh")
    suspend fun refreshMangaUpdates(): MangaUpdatesResponseDto

    @GET("Jellio/manga/stream/by-key")
    suspend fun getMangaStreamByKey(@Query("key") key: String): MangaByKeyDto

    @GET("Jellio/manga/stream/series/{mangaId}")
    suspend fun getMangaStreamSeries(@Path("mangaId") mangaId: Int): MangaStreamSeriesDto

    @GET("Jellio/manga/stream/chapter/{id}")
    suspend fun getMangaStreamChapter(
        @Path("id") id: String,
        @Query("manga") mangaId: Int? = null,
    ): MangaStreamChapterDto

    @GET("Jellio/manga/stream/chapter/{chapterId}/pages")
    suspend fun getMangaStreamChapterPages(@Path("chapterId") chapterId: Int): MangaPagesCountDto

    @Streaming
    @GET("Jellio/manga/stream/chapter/{chapterId}/cbz")
    suspend fun downloadStreamChapterCbz(@Path("chapterId") chapterId: Int): ResponseBody

    // --- Offline download conversion slots ---

    @GET("Jellio/downloads/conversions")
    suspend fun getConversionStatus(): ConversionStatusDto

    @POST("Jellio/downloads/conversions")
    suspend fun claimConversionSlot(@Body body: ClaimConversionRequest): Response<ClaimConversionResponseDto>

    @POST("Jellio/downloads/conversions/{slotId}/heartbeat")
    suspend fun heartbeatConversionSlot(@Path("slotId") slotId: String): Response<Unit>

    @DELETE("Jellio/downloads/conversions/{slotId}")
    suspend fun releaseConversionSlot(@Path("slotId") slotId: String): Response<Unit>
}
