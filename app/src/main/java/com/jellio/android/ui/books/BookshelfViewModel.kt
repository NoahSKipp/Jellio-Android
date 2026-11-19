package com.jellio.android.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.android.data.books.BookDiscoverResultDto
import com.jellio.android.data.books.BookRequestEntryDto
import com.jellio.android.data.books.BookSearchResultDto
import com.jellio.android.data.books.BookShelfInfoDto
import com.jellio.android.data.books.BooksRepository
import com.jellio.android.data.books.InProgressReadingDto
import com.jellio.android.data.books.MangaDiscoverDto
import com.jellio.android.data.books.MangaSearchResponseDto
import com.jellio.android.data.books.MangaSeriesSummaryDto
import com.jellio.android.data.books.MangaStatusDto
import com.jellio.android.data.books.MangaStreamChapterDto
import com.jellio.android.data.books.MangaStreamSeriesDto
import com.jellio.android.data.books.MangaUpdateItemDto
import com.jellio.android.data.books.ReadingProgressDto
import com.jellio.android.data.books.SeriesPrefsDto
import com.jellio.android.data.books.ShelfCategoryDto
import com.jellio.android.data.books.ShelfDto
import com.jellio.android.data.books.UpdateSeriesPrefsRequest
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.offline.OfflineDownloadItem
import com.jellio.android.data.offline.OfflineDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BookshelfTab(val kind: String, val label: String) {
    EBOOK("ebook", "Books"),
    MANGA("manga", "Manga"),
    AUDIOBOOK("audiobook", "Audiobooks"),
}

enum class ShelfSortOption(val key: String, val label: String) {
    TITLE("title", "Title"),
    AUTHOR("author", "Author"),
    DATE_ADDED("dateAdded", "Date Added"),
    YEAR("year", "Year"),
    LAST_READ("lastRead", "Last Read"),
    UNREAD_CHAPTERS("unreadChapters", "Unread Chapters"),
    TOTAL_CHAPTERS("totalChapters", "Total Chapters"),
    LATEST_CHAPTER("latestChapter", "Latest Chapter"),
}

data class ContinueReadingCardModel(
    val itemId: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
    val progress: Double,
    val kind: String,
    val mangaId: Int? = null,
    val chapterId: Int? = null,
)

data class MangaSeriesDisplayItem(
    val key: String,
    val title: String,
    val author: String?,
    val status: String?,
    val coverUrl: String?,
    val mangaId: Int?,
    val chapterCount: Int,
    val downloadedCount: Int,
    val readCount: Int,
    val latestUpload: Long,
    val lastReadAt: String?,
    val resumeChapterId: String?,
    val resumeStreamChapterId: Int?,
    val firstChapterId: String?,
    val localChapters: List<BaseItemDto> = emptyList(),
    val streamSummary: MangaSeriesSummaryDto? = null,
) {
    val unreadCount: Int get() = (chapterCount - readCount).coerceAtLeast(0)
}

data class MangaChapterRowItem(
    val id: String,
    val title: String,
    val number: Float,
    val scanlator: String?,
    val pageCount: Int,
    val uploadDate: Long,
    val isDownloaded: Boolean,
    val isRead: Boolean,
    val isBookmarked: Boolean,
    val progress: Double,
    val streamChapterId: Int?,
    val mangaId: Int?,
    val sourceOrder: Int,
    val baseItem: BaseItemDto? = null,
)

data class MangaSeriesDetailState(
    val isLoading: Boolean = false,
    val seriesItem: MangaSeriesDisplayItem? = null,
    val streamSeries: MangaStreamSeriesDto? = null,
    val chapters: List<MangaChapterRowItem> = emptyList(),
    val availableScanlators: List<String> = emptyList(),
    val prefs: SeriesPrefsDto = SeriesPrefsDto(),
    val inLibrary: Boolean = true,
    val error: String? = null,
)

data class BookshelfUiState(
    val activeTab: BookshelfTab = BookshelfTab.EBOOK,
    val isLoading: Boolean = true,
    val error: String? = null,
    val userId: String = "",
    val libraries: List<BaseItemDto> = emptyList(),
    val selectedLibraryId: String? = null,

    // Ebook & Audiobook items
    val ebookItems: List<BaseItemDto> = emptyList(),
    val audiobookItems: List<BaseItemDto> = emptyList(),
    val shelfInfoById: Map<String, BookShelfInfoDto> = emptyMap(),
    val readingProgressById: Map<String, ReadingProgressDto> = emptyMap(),
    val coverUrlsByKey: Map<String, String> = emptyMap(),

    // Continue reading / listening
    val continueReading: List<ContinueReadingCardModel> = emptyList(),
    val continueListening: List<ContinueReadingCardModel> = emptyList(),

    // Manga library (stream + Jellyfin comic items)
    val mangaStatus: MangaStatusDto = MangaStatusDto(),
    val mangaSeries: List<MangaSeriesDisplayItem> = emptyList(),
    val mangaUpdates: List<MangaUpdateItemDto> = emptyList(),
    val showMangaUpdatesSheet: Boolean = false,
    val isRefreshingUpdates: Boolean = false,

    // Shelf categories & sorting
    val shelfByKind: Map<String, ShelfDto> = emptyMap(),
    val selectedCategoryId: String? = null, // null = All
    val searchQuery: String = "",
    val sortOption: ShelfSortOption = ShelfSortOption.TITLE,
    val sortDescending: Boolean = false,
    val showCategoryManager: Boolean = false,
    val categoryAssignTargetKey: String? = null,

    // Manga series detail modal/sheet
    val seriesDetail: MangaSeriesDetailState? = null,

    // Discover & Request modals
    val showDiscoverSheet: Boolean = false,
    val discoverQuery: String = "",
    val bookDiscoverSource: String = "trending",
    val bookDiscoverResults: List<BookDiscoverResultDto> = emptyList(),
    val bookSearchResults: List<BookSearchResultDto> = emptyList(),
    val mangaDiscoverSort: String = "trending",
    val mangaDiscoverResults: List<MangaDiscoverDto> = emptyList(),
    val mangaSearchResponse: MangaSearchResponseDto? = null,
    val isDiscoverLoading: Boolean = false,
    val pendingBookRequests: List<BookRequestEntryDto> = emptyList(),
    val chaptarrConfigured: Boolean = false,
    val showPendingRequestsSheet: Boolean = false,

    // Offline downloads snapshot keyed by normalized item id
    val downloadsById: Map<String, OfflineDownloadItem> = emptyMap(),

    val statusMessage: String? = null,
)

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val booksRepository: BooksRepository,
    private val offlineDownloads: OfflineDownloadManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookshelfUiState())
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    private var rawComicItems: List<BaseItemDto> = emptyList()

    init {
        viewModelScope.launch {
            offlineDownloads.downloads.collect { list ->
                val mapped = list.associateBy { BooksRepository.normalizeId(it.id) }
                _uiState.update { it.copy(downloadsById = mapped) }
            }
        }
        loadInitial()
    }

    fun selectTab(tab: BookshelfTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                selectedCategoryId = null,
                searchQuery = "",
                sortOption = if (tab == BookshelfTab.MANGA) ShelfSortOption.TITLE else ShelfSortOption.TITLE,
            )
        }
        viewModelScope.launch {
            ensureTabDataLoaded(tab)
        }
    }

    fun refresh() {
        loadInitial(force = true)
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortOption(option: ShelfSortOption) {
        _uiState.update { current ->
            if (current.sortOption == option) {
                current.copy(sortDescending = !current.sortDescending)
            } else {
                current.copy(sortOption = option, sortDescending = false)
            }
        }
    }

    fun toggleSortDirection() {
        _uiState.update { it.copy(sortDescending = !it.sortDescending) }
    }

    fun selectCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun openCategoryManager(open: Boolean) {
        _uiState.update { it.copy(showCategoryManager = open) }
    }

    fun openAssignCategoriesFor(itemKey: String?) {
        _uiState.update { it.copy(categoryAssignTargetKey = itemKey) }
    }

    private fun loadInitial(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val uid = booksRepository.currentUserId()
            if (uid.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }
            val views = booksRepository.getBookshelfLibraries(uid)
            val defaultLib = views.firstOrNull()?.Id
            _uiState.update {
                it.copy(
                    userId = uid,
                    libraries = views,
                    selectedLibraryId = it.selectedLibraryId ?: defaultLib,
                )
            }
            ensureTabDataLoaded(_uiState.value.activeTab, force = force)
        }
    }

    private suspend fun ensureTabDataLoaded(tab: BookshelfTab, force: Boolean = false) {
        val state = _uiState.value
        val uid = state.userId.ifBlank { booksRepository.currentUserId().orEmpty() }
        if (uid.isBlank()) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            coroutineScope {
                val shelfDeferred = async { booksRepository.getShelf(tab.kind) }
                val allProgressDeferred = async { booksRepository.getAllReadingProgress() }

                val shelf = shelfDeferred.await()
                val progressMap = allProgressDeferred.await()
                    .mapKeys { BooksRepository.normalizeId(it.key) }

                when (tab) {
                    BookshelfTab.EBOOK -> {
                        val libIds = state.libraries.map { it.Id }
                        val itemsDeferred = async {
                            libIds.flatMap { libId ->
                                booksRepository.getBookshelfItems(uid, libId, "ebook")
                            }.distinctBy { BooksRepository.normalizeId(it.Id) }
                        }
                        val infoDeferred = async {
                            val merged = HashMap<String, BookShelfInfoDto>()
                            libIds.forEach { libId ->
                                merged.putAll(booksRepository.getBookShelfInfo(libId))
                            }
                            merged
                        }
                        val continueDeferred = async { booksRepository.getContinueReadingItems(uid, limit = 16) }

                        val items = itemsDeferred.await()
                        val info = infoDeferred.await().mapKeys { BooksRepository.normalizeId(it.key) }
                        val continuePairs = continueDeferred.await()

                        val coverMap = HashMap<String, String>(state.coverUrlsByKey)
                        items.forEach { item ->
                            val key = BooksRepository.normalizeId(item.Id)
                            if (!coverMap.containsKey(key)) {
                                booksRepository.bookCoverUrl(item)?.let { coverMap[key] = it }
                            }
                        }
                        val continueCards = continuePairs.map { (item, prog) ->
                            val norm = BooksRepository.normalizeId(item.Id)
                            val cover = coverMap[norm] ?: booksRepository.bookCoverUrl(item)
                            if (cover != null) coverMap[norm] = cover
                            val author = info[norm]?.Authors
                                ?: item.People?.firstOrNull()?.Name
                                ?: item.AlbumArtist
                                ?: ""
                            ContinueReadingCardModel(
                                itemId = item.Id,
                                title = item.Name.orEmpty(),
                                subtitle = author,
                                coverUrl = cover,
                                progress = prog.Progress.coerceIn(0.0, 1.0),
                                kind = if (BooksRepository.isComicOrManga(item)) "manga" else "book",
                            )
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                ebookItems = items,
                                shelfInfoById = it.shelfInfoById + info,
                                readingProgressById = it.readingProgressById + progressMap,
                                coverUrlsByKey = coverMap,
                                continueReading = continueCards,
                                shelfByKind = it.shelfByKind + (tab.kind to shelf),
                            )
                        }
                    }

                    BookshelfTab.MANGA -> {
                        val statusDeferred = async { booksRepository.getMangaStatus() }
                        val streamLibDeferred = async { booksRepository.getMangaStreamLibrary() }
                        val updatesDeferred = async { booksRepository.getMangaUpdates() }
                        val localComicsDeferred = async {
                            state.libraries.flatMap { lib ->
                                booksRepository.getBookshelfItems(uid, lib.Id, "manga")
                                    .filter { BooksRepository.isComicOrManga(it) }
                            }.distinctBy { BooksRepository.normalizeId(it.Id) }
                        }

                        val status = statusDeferred.await()
                        val streamList = streamLibDeferred.await().getOrDefault(emptyList())
                        val updates = updatesDeferred.await().getOrNull()?.Items.orEmpty()
                        val localComics = localComicsDeferred.await()
                        rawComicItems = localComics

                        val coverMap = HashMap<String, String>(state.coverUrlsByKey)
                        val seriesList = buildMangaSeriesList(streamList, localComics, progressMap, coverMap)

                        // Populate continue reading cards from series with resume or progress
                        val continueCards = seriesList
                            .filter { it.readCount in 1 until it.chapterCount || it.resumeChapterId != null }
                            .take(15)
                            .mapNotNull { series ->
                                val chId = series.resumeStreamChapterId
                                val itemId = series.resumeChapterId ?: series.firstChapterId ?: return@mapNotNull null
                                val pct = if (series.chapterCount > 0) {
                                    (series.readCount.toDouble() / series.chapterCount.toDouble()).coerceIn(0.0, 1.0)
                                } else {
                                    0.0
                                }
                                ContinueReadingCardModel(
                                    itemId = itemId,
                                    title = series.title,
                                    subtitle = series.streamSummary?.Resume?.Name ?: "${series.readCount}/${series.chapterCount} ch",
                                    coverUrl = series.coverUrl,
                                    progress = pct,
                                    kind = "manga",
                                    mangaId = series.mangaId,
                                    chapterId = chId,
                                )
                            }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                mangaStatus = status,
                                mangaSeries = seriesList,
                                mangaUpdates = updates,
                                readingProgressById = it.readingProgressById + progressMap,
                                coverUrlsByKey = coverMap,
                                continueReading = if (continueCards.isNotEmpty()) continueCards else it.continueReading,
                                shelfByKind = it.shelfByKind + (tab.kind to shelf),
                            )
                        }
                    }

                    BookshelfTab.AUDIOBOOK -> {
                        val libIds = state.libraries.map { it.Id }
                        val itemsDeferred = async {
                            libIds.flatMap { libId ->
                                booksRepository.getBookshelfItems(uid, libId, "audiobook")
                            }.distinctBy { BooksRepository.normalizeId(it.Id) }
                        }
                        val infoDeferred = async {
                            val merged = HashMap<String, BookShelfInfoDto>()
                            libIds.forEach { libId ->
                                merged.putAll(booksRepository.getBookShelfInfo(libId))
                            }
                            merged
                        }
                        val continueDeferred = async { booksRepository.getContinueListening(uid, limit = 16) }

                        val items = itemsDeferred.await()
                        val info = infoDeferred.await().mapKeys { BooksRepository.normalizeId(it.key) }
                        val resumeAudiobooks = continueDeferred.await()

                        val coverMap = HashMap<String, String>(state.coverUrlsByKey)
                        items.forEach { item ->
                            val norm = BooksRepository.normalizeId(item.Id)
                            if (!coverMap.containsKey(norm)) {
                                booksRepository.bookCoverUrl(item)?.let { coverMap[norm] = it }
                            }
                        }

                        val listeningCards = resumeAudiobooks.map { item ->
                            val norm = BooksRepository.normalizeId(item.Id)
                            val cover = coverMap[norm] ?: booksRepository.bookCoverUrl(item)
                            if (cover != null) coverMap[norm] = cover
                            val pct = (item.UserData?.PlayedPercentage ?: 0.0) / 100.0
                            ContinueReadingCardModel(
                                itemId = item.Id,
                                title = item.Name.orEmpty(),
                                subtitle = item.AlbumArtist ?: item.Artists?.firstOrNull() ?: info[norm]?.Authors.orEmpty(),
                                coverUrl = cover,
                                progress = pct.coerceIn(0.0, 1.0),
                                kind = "audiobook",
                            )
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                audiobookItems = items,
                                shelfInfoById = it.shelfInfoById + info,
                                readingProgressById = it.readingProgressById + progressMap,
                                coverUrlsByKey = coverMap,
                                continueListening = listeningCards,
                                shelfByKind = it.shelfByKind + (tab.kind to shelf),
                            )
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            _uiState.update { it.copy(isLoading = false, error = t.message ?: "Failed to load bookshelf") }
        }
    }

    private suspend fun buildMangaSeriesList(
        streamList: List<MangaSeriesSummaryDto>,
        localComics: List<BaseItemDto>,
        progressMap: Map<String, ReadingProgressDto>,
        coverMap: MutableMap<String, String>,
    ): List<MangaSeriesDisplayItem> {
        val byKey = LinkedHashMap<String, MangaSeriesDisplayItem>()

        for (summary in streamList) {
            val key = summary.Key.ifBlank { BooksRepository.mangaSeriesKey(summary.Title) }
            val cover = booksRepository.mangaStreamCoverUrl(summary.MangaId, summary.Title)
            if (cover != null) coverMap["m:${summary.MangaId}"] = cover
            byKey[key] = MangaSeriesDisplayItem(
                key = key,
                title = summary.Title,
                author = summary.Author,
                status = summary.Status,
                coverUrl = cover,
                mangaId = summary.MangaId,
                chapterCount = summary.ChapterCount,
                downloadedCount = summary.DownloadedCount,
                readCount = summary.ReadCount,
                latestUpload = summary.LatestUpload,
                lastReadAt = summary.LastReadAt,
                resumeChapterId = summary.Resume?.Id,
                resumeStreamChapterId = summary.Resume?.ChapterId,
                firstChapterId = summary.FirstChapterId,
                streamSummary = summary,
            )
        }

        val groupedLocal = localComics.groupBy { item ->
            val rawSeries = item.SeriesName?.takeIf { it.isNotBlank() } ?: item.Name.orEmpty()
            BooksRepository.mangaSeriesKey(rawSeries)
        }

        for ((key, chapters) in groupedLocal) {
            val sortedChapters = chapters.sortedWith(compareBy<BaseItemDto> { it.IndexNumber ?: Int.MAX_VALUE }.thenBy { it.Name.orEmpty() })
            val first = sortedChapters.first()
            val title = first.SeriesName?.takeIf { it.isNotBlank() } ?: first.Name.orEmpty()
            val existing = byKey[key]
            if (existing != null) {
                byKey[key] = existing.copy(localChapters = sortedChapters)
            } else {
                val coverItem = sortedChapters.firstOrNull { !it.ImageTags?.get("Primary").isNullOrBlank() } ?: first
                val cover = booksRepository.mangaSeriesCoverUrl(coverItem, title)
                if (cover != null) coverMap[key] = cover
                val readCount = sortedChapters.count { ch ->
                    val norm = BooksRepository.normalizeId(ch.Id)
                    (progressMap[norm]?.Progress ?: 0.0) >= 0.95 || ch.UserData?.Played == true
                }
                val lastRead = sortedChapters.mapNotNull { progressMap[BooksRepository.normalizeId(it.Id)]?.UpdatedAt }.maxOrNull()
                byKey[key] = MangaSeriesDisplayItem(
                    key = key,
                    title = title,
                    author = first.People?.firstOrNull()?.Name,
                    status = first.Status,
                    coverUrl = cover,
                    mangaId = null,
                    chapterCount = sortedChapters.size,
                    downloadedCount = sortedChapters.count {
                        offlineDownloads.getCompletedFile(it.Id, _uiState.value.userId) != null
                    },
                    readCount = readCount,
                    latestUpload = 0L,
                    lastReadAt = lastRead,
                    resumeChapterId = sortedChapters.firstOrNull { ch ->
                        val p = progressMap[BooksRepository.normalizeId(ch.Id)]?.Progress ?: 0.0
                        p in 0.01..0.95
                    }?.Id ?: sortedChapters.firstOrNull()?.Id,
                    resumeStreamChapterId = null,
                    firstChapterId = sortedChapters.firstOrNull()?.Id,
                    localChapters = sortedChapters,
                )
            }
        }

        return byKey.values.toList()
    }

    // --- Category management ---

    fun createCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val kind = _uiState.value.activeTab.kind
        viewModelScope.launch {
            booksRepository.createShelfCategory(kind, trimmed)
                .onSuccess {
                    val refreshed = booksRepository.getShelf(kind)
                    _uiState.update {
                        it.copy(
                            shelfByKind = it.shelfByKind + (kind to refreshed),
                            statusMessage = "Category \"$trimmed\" created",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Failed to create category") }
                }
        }
    }

    fun renameCategory(category: ShelfCategoryDto, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val kind = _uiState.value.activeTab.kind
        viewModelScope.launch {
            booksRepository.updateShelfCategory(category.Id, name = trimmed)
                .onSuccess {
                    val refreshed = booksRepository.getShelf(kind)
                    _uiState.update {
                        it.copy(
                            shelfByKind = it.shelfByKind + (kind to refreshed),
                            statusMessage = "Category renamed",
                        )
                    }
                }
        }
    }

    fun deleteCategory(categoryId: String) {
        val kind = _uiState.value.activeTab.kind
        viewModelScope.launch {
            booksRepository.deleteShelfCategory(categoryId)
                .onSuccess {
                    val refreshed = booksRepository.getShelf(kind)
                    _uiState.update { state ->
                        state.copy(
                            shelfByKind = state.shelfByKind + (kind to refreshed),
                            selectedCategoryId = if (state.selectedCategoryId == categoryId) null else state.selectedCategoryId,
                            statusMessage = "Category deleted",
                        )
                    }
                }
        }
    }

    fun updateItemCategories(itemKey: String, categoryIds: List<String>) {
        val kind = _uiState.value.activeTab.kind
        viewModelScope.launch {
            booksRepository.updateShelfMembership(kind, listOf(itemKey), categoryIds)
                .onSuccess {
                    val refreshed = booksRepository.getShelf(kind)
                    _uiState.update {
                        it.copy(
                            shelfByKind = it.shelfByKind + (kind to refreshed),
                            categoryAssignTargetKey = null,
                            statusMessage = "Updated categories",
                        )
                    }
                }
        }
    }

    // --- Manga Series Detail & Chapter Controls ---

    fun openMangaSeries(series: MangaSeriesDisplayItem) {
        val shelf = _uiState.value.shelfByKind[BookshelfTab.MANGA.kind] ?: ShelfDto()
        val seriesPrefKey = if (series.key.startsWith("s:")) series.key else "s:${series.key}"
        val prefs = shelf.Series[seriesPrefKey] ?: shelf.Series[series.key] ?: shelf.SeriesDefaults ?: SeriesPrefsDto()
        val inLibrary = seriesPrefKey !in shelf.LibraryRemoved && series.key !in shelf.LibraryRemoved

        _uiState.update {
            it.copy(
                seriesDetail = MangaSeriesDetailState(
                    isLoading = series.mangaId != null,
                    seriesItem = series,
                    prefs = prefs,
                    inLibrary = inLibrary,
                ),
            )
        }

        if (series.mangaId != null) {
            viewModelScope.launch {
                booksRepository.getMangaStreamSeries(series.mangaId)
                    .onSuccess { streamSeries ->
                        val rows = buildChapterRows(streamSeries.Chapters, series.localChapters, streamSeries.MangaId, shelf)
                        val scanlators = streamSeries.Chapters.mapNotNull { it.Scanlator?.takeIf { s -> s.isNotBlank() } }.distinct().sorted()
                        _uiState.update { state ->
                            val current = state.seriesDetail ?: return@update state
                            state.copy(
                                seriesDetail = current.copy(
                                    isLoading = false,
                                    streamSeries = streamSeries,
                                    chapters = applyChapterFiltersAndSort(rows, current.prefs),
                                    availableScanlators = scanlators,
                                ),
                            )
                        }
                    }
                    .onFailure { err ->
                        val rows = buildChapterRows(emptyList(), series.localChapters, series.mangaId, shelf)
                        _uiState.update { state ->
                            val current = state.seriesDetail ?: return@update state
                            state.copy(
                                seriesDetail = current.copy(
                                    isLoading = false,
                                    chapters = applyChapterFiltersAndSort(rows, current.prefs),
                                    error = if (rows.isEmpty()) (err.message ?: "Failed to load chapters") else null,
                                ),
                            )
                        }
                    }
            }
        } else {
            val rows = buildChapterRows(emptyList(), series.localChapters, null, shelf)
            _uiState.update { state ->
                val current = state.seriesDetail ?: return@update state
                state.copy(
                    seriesDetail = current.copy(
                        isLoading = false,
                        chapters = applyChapterFiltersAndSort(rows, current.prefs),
                    ),
                )
            }
        }
    }

    fun closeMangaSeries() {
        _uiState.update { it.copy(seriesDetail = null) }
    }

    private fun buildChapterRows(
        streamChapters: List<MangaStreamChapterDto>,
        localChapters: List<BaseItemDto>,
        mangaId: Int?,
        shelf: ShelfDto,
    ): List<MangaChapterRowItem> {
        val progressMap = _uiState.value.readingProgressById
        val bookmarks = shelf.Bookmarks.map { BooksRepository.normalizeId(it) }.toSet()
        val downloads = _uiState.value.downloadsById

        if (streamChapters.isNotEmpty()) {
            return streamChapters.map { ch ->
                val normId = BooksRepository.normalizeId(ch.Id)
                val prog = progressMap[normId]?.Progress ?: 0.0
                val isDl = ch.IsDownloaded || downloads[normId]?.status == OfflineDownloadItem.STATUS_COMPLETED
                MangaChapterRowItem(
                    id = ch.Id,
                    title = ch.Name.ifBlank { "Chapter ${ch.Number}" },
                    number = ch.Number,
                    scanlator = ch.Scanlator,
                    pageCount = ch.PageCount,
                    uploadDate = ch.UploadDate,
                    isDownloaded = isDl,
                    isRead = prog >= 0.95,
                    isBookmarked = normId in bookmarks,
                    progress = prog,
                    streamChapterId = ch.ChapterId,
                    mangaId = ch.MangaId,
                    sourceOrder = ch.SourceOrder,
                )
            }
        }

        return localChapters.mapIndexed { idx, item ->
            val normId = BooksRepository.normalizeId(item.Id)
            val prog = progressMap[normId]?.Progress ?: 0.0
            val isDl = downloads[normId]?.status == OfflineDownloadItem.STATUS_COMPLETED
            MangaChapterRowItem(
                id = item.Id,
                title = item.Name.orEmpty().ifBlank { "Chapter ${item.IndexNumber ?: (idx + 1)}" },
                number = item.IndexNumber?.toFloat() ?: (idx + 1).toFloat(),
                scanlator = null,
                pageCount = 0,
                uploadDate = 0L,
                isDownloaded = isDl,
                isRead = prog >= 0.95 || item.UserData?.Played == true,
                isBookmarked = normId in bookmarks,
                progress = prog,
                streamChapterId = null,
                mangaId = mangaId,
                sourceOrder = idx,
                baseItem = item,
            )
        }
    }

    private fun applyChapterFiltersAndSort(
        allRows: List<MangaChapterRowItem>,
        prefs: SeriesPrefsDto,
    ): List<MangaChapterRowItem> {
        val excluded = prefs.ExcludedScanlators.orEmpty().toSet()
        var filtered = allRows.filter { row ->
            val scan = row.scanlator
            if (scan != null && scan in excluded) return@filter false
            when (prefs.FilterDownloaded) {
                "downloaded" -> if (!row.isDownloaded) return@filter false
                "not_downloaded" -> if (row.isDownloaded) return@filter false
            }
            when (prefs.FilterUnread) {
                "unread" -> if (row.isRead) return@filter false
                "read" -> if (!row.isRead) return@filter false
            }
            when (prefs.FilterBookmarked) {
                "bookmarked" -> if (!row.isBookmarked) return@filter false
                "not_bookmarked" -> if (row.isBookmarked) return@filter false
            }
            true
        }

        if (prefs.DuplicatesAsOne == true) {
            val seenNumbers = HashSet<Float>()
            filtered = filtered.filter { seenNumbers.add(it.number) }
        }

        val descending = prefs.ChapterDescending ?: true
        val comparator: Comparator<MangaChapterRowItem> = when (prefs.ChapterSort) {
            "source" -> compareBy { it.sourceOrder }
            "upload" -> compareBy { it.uploadDate }
            else -> compareBy<MangaChapterRowItem> { it.number }.thenBy { it.sourceOrder }
        }
        return if (descending) filtered.sortedWith(comparator.reversed()) else filtered.sortedWith(comparator)
    }

    fun updateSeriesPreferences(
        excludedScanlators: List<String>? = null,
        duplicatesAsOne: Boolean? = null,
        filterDownloaded: String? = null,
        filterUnread: String? = null,
        filterBookmarked: String? = null,
        chapterSort: String? = null,
        chapterDescending: Boolean? = null,
    ) {
        val detail = _uiState.value.seriesDetail ?: return
        val series = detail.seriesItem ?: return
        val key = if (series.key.startsWith("s:")) series.key else "s:${series.key}"
        val nextPrefs = detail.prefs.copy(
            ExcludedScanlators = excludedScanlators ?: detail.prefs.ExcludedScanlators,
            DuplicatesAsOne = duplicatesAsOne ?: detail.prefs.DuplicatesAsOne,
            FilterDownloaded = filterDownloaded ?: detail.prefs.FilterDownloaded,
            FilterUnread = filterUnread ?: detail.prefs.FilterUnread,
            FilterBookmarked = filterBookmarked ?: detail.prefs.FilterBookmarked,
            ChapterSort = chapterSort ?: detail.prefs.ChapterSort,
            ChapterDescending = chapterDescending ?: detail.prefs.ChapterDescending,
        )
        val shelf = _uiState.value.shelfByKind[BookshelfTab.MANGA.kind] ?: ShelfDto()
        val rawRows = buildChapterRows(
            detail.streamSeries?.Chapters.orEmpty(),
            series.localChapters,
            series.mangaId,
            shelf,
        )
        _uiState.update { state ->
            state.copy(
                seriesDetail = detail.copy(
                    prefs = nextPrefs,
                    chapters = applyChapterFiltersAndSort(rawRows, nextPrefs),
                ),
            )
        }

        viewModelScope.launch {
            booksRepository.updateSeriesPrefs(
                UpdateSeriesPrefsRequest(
                    Key = key,
                    ExcludedScanlators = nextPrefs.ExcludedScanlators,
                    DuplicatesAsOne = nextPrefs.DuplicatesAsOne,
                    FilterDownloaded = nextPrefs.FilterDownloaded,
                    FilterUnread = nextPrefs.FilterUnread,
                    FilterBookmarked = nextPrefs.FilterBookmarked,
                    ChapterSort = nextPrefs.ChapterSort,
                    ChapterDescending = nextPrefs.ChapterDescending,
                ),
            )
        }
    }

    fun toggleSeriesInLibrary() {
        val detail = _uiState.value.seriesDetail ?: return
        val series = detail.seriesItem ?: return
        val next = !detail.inLibrary
        _uiState.update { it.copy(seriesDetail = detail.copy(inLibrary = next)) }
        viewModelScope.launch {
            booksRepository.setMangaInLibrary(series.key, next)
        }
    }

    fun toggleChapterBookmark(chapter: MangaChapterRowItem) {
        val next = !chapter.isBookmarked
        viewModelScope.launch {
            booksRepository.setChapterBookmark(chapter.id, next)
                .onSuccess {
                    val normId = BooksRepository.normalizeId(chapter.id)
                    _uiState.update { state ->
                        val detail = state.seriesDetail ?: return@update state
                        val updatedChapters = detail.chapters.map {
                            if (BooksRepository.normalizeId(it.id) == normId) it.copy(isBookmarked = next) else it
                        }
                        state.copy(seriesDetail = detail.copy(chapters = updatedChapters))
                    }
                }
        }
    }

    fun downloadMangaChapter(chapter: MangaChapterRowItem, seriesTitle: String, seriesKey: String) {
        viewModelScope.launch {
            val dto = chapter.baseItem ?: BaseItemDto(
                Id = chapter.id,
                Name = chapter.title,
                Type = "Book",
                SeriesName = seriesTitle,
                IndexNumber = chapter.number.toInt(),
            )
            offlineDownloads.queueBookDownload(
                item = dto,
                isManga = true,
                seriesKey = seriesKey,
                streamChapterId = chapter.streamChapterId,
                streamMangaId = chapter.mangaId,
                userIdOverride = _uiState.value.userId,
            )
            _uiState.update { it.copy(statusMessage = "Queued download: ${chapter.title}") }
        }
    }

    fun downloadAllChapters(unreadOnly: Boolean) {
        val detail = _uiState.value.seriesDetail ?: return
        val series = detail.seriesItem ?: return
        val targets = detail.chapters.filter { ch ->
            !ch.isDownloaded && (!unreadOnly || !ch.isRead)
        }
        if (targets.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Nothing to download") }
            return
        }
        viewModelScope.launch {
            targets.forEach { ch ->
                val dto = ch.baseItem ?: BaseItemDto(
                    Id = ch.id,
                    Name = ch.title,
                    Type = "Book",
                    SeriesName = series.title,
                    IndexNumber = ch.number.toInt(),
                )
                offlineDownloads.queueBookDownload(
                    item = dto,
                    isManga = true,
                    seriesKey = series.key,
                    streamChapterId = ch.streamChapterId,
                    streamMangaId = ch.mangaId,
                    userIdOverride = _uiState.value.userId,
                )
            }
            _uiState.update { it.copy(statusMessage = "Queued ${targets.size} chapters") }
        }
    }

    fun downloadBookOrAudiobook(item: BaseItemDto) {
        viewModelScope.launch {
            val uid = _uiState.value.userId
            if (_uiState.value.activeTab == BookshelfTab.AUDIOBOOK) {
                val tracks = booksRepository.getAudiobookTracks(uid, item)
                offlineDownloads.queueAudiobookDownload(item, tracks, userIdOverride = uid)
            } else {
                offlineDownloads.queueBookDownload(item, isManga = false, userIdOverride = uid)
            }
            _uiState.update { it.copy(statusMessage = "Queued offline download: ${item.Name}") }
        }
    }

    // --- Manga Updates Sheet ---

    fun openMangaUpdates(open: Boolean) {
        _uiState.update { it.copy(showMangaUpdatesSheet = open) }
    }

    fun refreshMangaUpdates() {
        _uiState.update { it.copy(isRefreshingUpdates = true) }
        viewModelScope.launch {
            booksRepository.refreshMangaUpdates()
                .onSuccess { resp ->
                    _uiState.update {
                        it.copy(
                            mangaUpdates = resp.Items,
                            isRefreshingUpdates = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isRefreshingUpdates = false) }
                }
        }
    }

    // --- Discover & Request Modals ---

    fun openDiscoverSheet(open: Boolean) {
        _uiState.update { it.copy(showDiscoverSheet = open) }
        if (open) {
            loadDiscoverFeed()
        }
    }

    fun setDiscoverQuery(query: String) {
        _uiState.update { it.copy(discoverQuery = query) }
    }

    fun setBookDiscoverSource(source: String) {
        _uiState.update { it.copy(bookDiscoverSource = source) }
        loadDiscoverFeed()
    }

    fun setMangaDiscoverSort(sort: String) {
        _uiState.update { it.copy(mangaDiscoverSort = sort) }
        loadDiscoverFeed()
    }

    fun runDiscoverSearch() {
        val state = _uiState.value
        val q = state.discoverQuery.trim()
        if (q.isEmpty()) {
            _uiState.update { it.copy(bookSearchResults = emptyList(), mangaSearchResponse = null) }
            loadDiscoverFeed()
            return
        }
        _uiState.update { it.copy(isDiscoverLoading = true) }
        viewModelScope.launch {
            if (state.activeTab == BookshelfTab.MANGA) {
                booksRepository.searchManga(q)
                    .onSuccess { resp ->
                        _uiState.update { it.copy(mangaSearchResponse = resp, isDiscoverLoading = false) }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(isDiscoverLoading = false, statusMessage = err.message ?: "Manga search failed")
                        }
                    }
            } else {
                val mediaType = if (state.activeTab == BookshelfTab.AUDIOBOOK) "audiobook" else "ebook"
                booksRepository.searchBooks(q, mediaType)
                    .onSuccess { res ->
                        _uiState.update { it.copy(bookSearchResults = res, isDiscoverLoading = false) }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(isDiscoverLoading = false, statusMessage = err.message ?: "Book search failed")
                        }
                    }
            }
        }
    }

    private fun loadDiscoverFeed() {
        val state = _uiState.value
        _uiState.update { it.copy(isDiscoverLoading = true) }
        viewModelScope.launch {
            if (state.activeTab == BookshelfTab.MANGA) {
                booksRepository.discoverManga(sort = state.mangaDiscoverSort)
                    .onSuccess { list ->
                        _uiState.update { it.copy(mangaDiscoverResults = list, isDiscoverLoading = false) }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isDiscoverLoading = false) }
                    }
            } else {
                booksRepository.discoverBooks(source = state.bookDiscoverSource)
                    .onSuccess { list ->
                        _uiState.update { it.copy(bookDiscoverResults = list, isDiscoverLoading = false) }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isDiscoverLoading = false) }
                    }
            }
        }
    }

    fun requestBookSearchResult(result: BookSearchResultDto, bookType: String) {
        viewModelScope.launch {
            booksRepository.requestBook(result, bookType)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(statusMessage = res.Message ?: "Requested \"${result.Title}\" via Chaptarr (${res.Status})")
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Request failed") }
                }
        }
    }

    fun requestBookDiscoverResult(result: BookDiscoverResultDto, bookType: String) {
        viewModelScope.launch {
            booksRepository.requestBook(result, bookType)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(statusMessage = res.Message ?: "Requested \"${result.Title}\" via Chaptarr (${res.Status})")
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Request failed") }
                }
        }
    }

    fun requestMangaFromSearch(mangaId: Int, title: String) {
        viewModelScope.launch {
            booksRepository.requestManga(mangaId, title, addToLibrary = true)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            statusMessage = res.Message
                                ?: "Added \"$title\" (${res.QueuedChapters}/${res.TotalChapters} chapters)",
                        )
                    }
                    ensureTabDataLoaded(BookshelfTab.MANGA, force = true)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Manga request failed") }
                }
        }
    }

    fun requestMangaFromDiscover(item: MangaDiscoverDto) {
        _uiState.update { it.copy(discoverQuery = item.Title, isDiscoverLoading = true) }
        viewModelScope.launch {
            booksRepository.searchManga(item.Title)
                .onSuccess { resp ->
                    _uiState.update { it.copy(mangaSearchResponse = resp, isDiscoverLoading = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isDiscoverLoading = false, statusMessage = err.message ?: "Source search failed")
                    }
                }
        }
    }

    fun openPendingRequestsSheet(open: Boolean) {
        _uiState.update { it.copy(showPendingRequestsSheet = open) }
        if (open) {
            val mediaType = if (_uiState.value.activeTab == BookshelfTab.AUDIOBOOK) "audiobook" else "ebook"
            viewModelScope.launch {
                booksRepository.getBookRequests(mediaType)
                    .onSuccess { resp ->
                        _uiState.update {
                            it.copy(
                                chaptarrConfigured = resp.Configured,
                                pendingBookRequests = resp.Items,
                            )
                        }
                    }
            }
        }
    }

    fun retryBookRequestSearch(chaptarrBookId: Int) {
        viewModelScope.launch {
            booksRepository.searchBookAgain(chaptarrBookId)
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Triggered Chaptarr search") }
                }
        }
    }

    // --- Filtered & sorted shelf lists ---

    fun filteredBooks(items: List<BaseItemDto>): List<BaseItemDto> {
        val state = _uiState.value
        val shelf = state.shelfByKind[state.activeTab.kind]
        val catIds = state.selectedCategoryId?.let { id ->
            shelf?.Categories?.firstOrNull { it.Id == id }?.Items?.map {
                BooksRepository.normalizeId(it.removePrefix("i:").removePrefix("s:"))
            }?.toSet()
        }
        val q = state.searchQuery.trim().lowercase()
        val filtered = items.filter { item ->
            val norm = BooksRepository.normalizeId(item.Id)
            if (catIds != null && norm !in catIds) return@filter false
            if (q.isNotEmpty()) {
                val info = state.shelfInfoById[norm]
                val matchesTitle = item.Name.orEmpty().lowercase().contains(q)
                val matchesAuthor = info?.Authors.orEmpty().lowercase().contains(q) ||
                    item.AlbumArtist.orEmpty().lowercase().contains(q)
                if (!matchesTitle && !matchesAuthor) return@filter false
            }
            true
        }
        val comparator: Comparator<BaseItemDto> = when (state.sortOption) {
            ShelfSortOption.AUTHOR -> compareBy {
                val norm = BooksRepository.normalizeId(it.Id)
                (state.shelfInfoById[norm]?.Authors ?: it.AlbumArtist ?: it.People?.firstOrNull()?.Name ?: "").lowercase()
            }
            ShelfSortOption.YEAR -> compareBy {
                val norm = BooksRepository.normalizeId(it.Id)
                state.shelfInfoById[norm]?.Year ?: it.ProductionYear ?: 0
            }
            ShelfSortOption.DATE_ADDED -> compareBy { it.PremiereDate.orEmpty() }
            ShelfSortOption.LAST_READ -> compareBy {
                val norm = BooksRepository.normalizeId(it.Id)
                state.readingProgressById[norm]?.UpdatedAt ?: it.UserData?.LastPlayedDate.orEmpty()
            }
            else -> compareBy { (it.Name ?: "").lowercase() }
        }
        return if (state.sortDescending) filtered.sortedWith(comparator.reversed()) else filtered.sortedWith(comparator)
    }

    fun filteredMangaSeries(): List<MangaSeriesDisplayItem> {
        val state = _uiState.value
        val shelf = state.shelfByKind[BookshelfTab.MANGA.kind]
        val removed = shelf?.LibraryRemoved.orEmpty().toSet()
        val catItems = state.selectedCategoryId?.let { id ->
            shelf?.Categories?.firstOrNull { it.Id == id }?.Items?.toSet()
        }
        val q = state.searchQuery.trim().lowercase()
        val filtered = state.mangaSeries.filter { series ->
            val rawKey = series.key.removePrefix("s:")
            val sKey = "s:$rawKey"
            if (rawKey in removed || sKey in removed || series.key in removed) return@filter false
            if (catItems != null && series.key !in catItems && sKey !in catItems && rawKey !in catItems) return@filter false
            if (q.isNotEmpty()) {
                val matchTitle = series.title.lowercase().contains(q)
                val matchAuthor = series.author.orEmpty().lowercase().contains(q)
                if (!matchTitle && !matchAuthor) return@filter false
            }
            true
        }
        val comparator: Comparator<MangaSeriesDisplayItem> = when (state.sortOption) {
            ShelfSortOption.AUTHOR -> compareBy { (it.author ?: "").lowercase() }
            ShelfSortOption.LAST_READ -> compareBy { it.lastReadAt.orEmpty() }
            ShelfSortOption.UNREAD_CHAPTERS -> compareBy { it.unreadCount }
            ShelfSortOption.TOTAL_CHAPTERS -> compareBy { it.chapterCount }
            ShelfSortOption.LATEST_CHAPTER, ShelfSortOption.DATE_ADDED -> compareBy { it.latestUpload }
            else -> compareBy { it.title.lowercase() }
        }
        return if (state.sortDescending) filtered.sortedWith(comparator.reversed()) else filtered.sortedWith(comparator)
    }
}
