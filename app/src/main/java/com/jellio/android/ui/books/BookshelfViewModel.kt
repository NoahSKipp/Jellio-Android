package com.jellio.android.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.android.data.books.BookDiscoverResultDto
import com.jellio.android.data.books.BookRequestEntryDto
import com.jellio.android.data.books.BookSearchResultDto
import com.jellio.android.data.books.BookShelfInfoDto
import com.jellio.android.data.books.ChaptarrStatus
import com.jellio.android.data.books.resolveChaptarrStatus
import com.jellio.android.data.books.BooksRepository
import com.jellio.android.data.books.CachedContinueCard
import com.jellio.android.data.books.ShelfSnapshot
import com.jellio.android.data.books.ShelfSnapshotStore
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

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
    val continueManga: List<ContinueReadingCardModel> = emptyList(),

    // Tabs that show data already (cached or fresh) and tabs whose continue row has answered.
    val loadedTabs: Set<BookshelfTab> = emptySet(),
    val continueLoadedTabs: Set<BookshelfTab> = emptySet(),

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
    val discoverMediaTab: BookshelfTab = BookshelfTab.EBOOK,
    val discoverQuery: String = "",
    val bookDiscoverSource: String = "trending",
    val bookDiscoverValue: String? = null,
    val bookDiscoverResults: List<BookDiscoverResultDto> = emptyList(),
    val bookSearchResults: List<BookSearchResultDto> = emptyList(),
    val mangaDiscoverSort: String = "trending",
    val mangaDiscoverGenres: Set<String> = emptySet(),
    val mangaDiscoverCountry: String? = null,
    val mangaDiscoverResults: List<MangaDiscoverDto> = emptyList(),
    val mangaSearchResponse: MangaSearchResponseDto? = null,
    val discoverProxyCoverByKey: Map<String, String> = emptyMap(),
    val isDiscoverLoading: Boolean = false,
    val pendingBookRequests: List<BookRequestEntryDto> = emptyList(),
    val chaptarrStatus: ChaptarrStatus = ChaptarrStatus.LOADING,
    val showPendingRequestsSheet: Boolean = false,

    // Offline downloads snapshot keyed by normalized item id
    val downloadsById: Map<String, OfflineDownloadItem> = emptyMap(),

    val statusMessage: String? = null,
)

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val booksRepository: BooksRepository,
    private val offlineDownloads: OfflineDownloadManager,
    private val snapshots: ShelfSnapshotStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookshelfUiState())
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    private var started = false
    private var mangaStream: List<MangaSeriesSummaryDto>? = null
    private var mangaLocal: List<BaseItemDto>? = null
    private var ebookContinuePairs: List<Pair<BaseItemDto, InProgressReadingDto>> = emptyList()
    private var audiobookContinueItems: List<BaseItemDto> = emptyList()
    private val tabJobs = HashMap<BookshelfTab, Job>()
    private val tabLoadedAt = HashMap<BookshelfTab, Long>()
    private var librariesJob: Deferred<List<BaseItemDto>>? = null
    private val rawItemsCache = HashMap<String, Pair<Long, Deferred<List<BaseItemDto>>>>()
    private var discoverJob: Job? = null
    private var discoverSearchDebounceJob: Job? = null
    private val mangaDiscoverCache = HashMap<String, List<MangaDiscoverDto>>()
    private val bookDiscoverCache = HashMap<String, List<BookDiscoverResultDto>>()

    init {
        viewModelScope.launch {
            offlineDownloads.downloads.collect { list ->
                val mapped = list.associateBy { BooksRepository.normalizeId(it.id) }
                _uiState.update { it.copy(downloadsById = mapped) }
            }
        }
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
        loadTab(tab, force = false)
    }

    // First call decides the tab the shelf opens on and starts loading it;
    // later calls (back from the reader, bottom bar) just switch tabs.
    fun openKind(kind: String) {
        val tab = when (kind) {
            "manga" -> BookshelfTab.MANGA
            "audiobook" -> BookshelfTab.AUDIOBOOK
            else -> BookshelfTab.EBOOK
        }
        if (!started) {
            started = true
            _uiState.update { it.copy(activeTab = tab) }
            loadInitial()
        } else if (_uiState.value.activeTab != tab) {
            selectTab(tab)
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
            _uiState.update { it.copy(userId = uid) }
            if (force) {
                tabLoadedAt.clear()
                rawItemsCache.clear()
                librariesJob = null
            }
            loadTab(_uiState.value.activeTab, force = force)
        }
    }

    private fun librariesFor(uid: String): Deferred<List<BaseItemDto>> =
        librariesJob ?: viewModelScope.async { booksRepository.getBookshelfLibraries(uid) }.also { librariesJob = it }

    // One query per library and item type, shared by Books and Manga (both
    // read the Book type) and by quick tab switches.
    private fun rawItemsFor(uid: String, libraryId: String, kind: String): Deferred<List<BaseItemDto>> {
        val key = "$libraryId|${if (kind == "audiobook") "a" else "b"}"
        val now = System.currentTimeMillis()
        rawItemsCache[key]?.let { (at, deferred) ->
            if (now - at < RAW_ITEMS_TTL_MS && !(deferred.isCompleted && deferred.isCancelled)) return deferred
        }
        val deferred = viewModelScope.async { booksRepository.getBookshelfRaw(uid, libraryId, kind) }
        rawItemsCache[key] = now to deferred
        deferred.invokeOnCompletion { cause -> if (cause != null) rawItemsCache.remove(key) }
        return deferred
    }

    private suspend fun itemsFor(uid: String, libs: List<BaseItemDto>, kind: String): List<BaseItemDto> {
        val rawKind = if (kind == "audiobook") "audiobook" else "ebook"
        val raw = coroutineScope {
            libs.map { lib -> async { rawItemsFor(uid, lib.Id, rawKind).await() } }.awaitAll()
        }.flatten()
        return booksRepository.filterBookshelfItems(raw, kind)
            .distinctBy { BooksRepository.normalizeId(it.Id) }
    }

    private suspend fun runGuarded(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        t
    }

    private fun loadTab(tab: BookshelfTab, force: Boolean) {
        val existing = tabJobs[tab]
        if (existing?.isActive == true && !force) return
        val loadedAt = tabLoadedAt[tab]
        val fresh = loadedAt != null && System.currentTimeMillis() - loadedAt < TAB_FRESH_MS
        if (!force && fresh && tab in _uiState.value.loadedTabs) return
        existing?.cancel()
        tabJobs[tab] = viewModelScope.launch { runTabLoad(tab) }
    }

    // Each request publishes its own result as it returns, so the first
    // thing the person sees is the cached shelf, then whichever of the
    // continue row, library and metadata answers first.
    private suspend fun runTabLoad(tab: BookshelfTab) {
        val uid = _uiState.value.userId.ifBlank { booksRepository.currentUserId().orEmpty() }
        if (uid.isBlank()) return
        _uiState.update { it.copy(userId = uid, error = null) }
        if (tab !in _uiState.value.loadedTabs) restoreSnapshot(tab, uid)
        _uiState.update { it.copy(isLoading = true) }

        val errors = ArrayList<Throwable>()
        coroutineScope {
            val librariesDeferred = librariesFor(uid)
            launch {
                runGuarded {
                    val shelf = booksRepository.getShelf(tab.kind)
                    _uiState.update { it.copy(shelfByKind = it.shelfByKind + (tab.kind to shelf)) }
                }
            }
            launch {
                runGuarded {
                    val progress = booksRepository.getAllReadingProgress().mapKeys { BooksRepository.normalizeId(it.key) }
                    _uiState.update { it.copy(readingProgressById = it.readingProgressById + progress) }
                    if (tab == BookshelfTab.MANGA) publishManga()
                }
            }
            when (tab) {
                BookshelfTab.EBOOK -> {
                    launch {
                        runGuarded {
                            ebookContinuePairs = booksRepository.getContinueReadingItems(uid, limit = 16)
                            publishEbookContinue()
                        }
                    }
                    launch {
                        runGuarded {
                            val libs = librariesDeferred.await().also { publishLibraries(it) }
                            val items = itemsFor(uid, libs, "ebook")
                            val covers = booksRepository.bookCoverUrls(items)
                            _uiState.update {
                                it.copy(
                                    ebookItems = items,
                                    coverUrlsByKey = it.coverUrlsByKey + covers,
                                    loadedTabs = it.loadedTabs + tab,
                                )
                            }
                            fetchShelfInfo(libs)
                            publishEbookContinue()
                        }?.let { errors += it }
                    }
                }

                BookshelfTab.AUDIOBOOK -> {
                    launch {
                        runGuarded {
                            audiobookContinueItems = booksRepository.getContinueListening(uid, limit = 16)
                            publishAudiobookContinue()
                        }
                    }
                    launch {
                        runGuarded {
                            val libs = librariesDeferred.await().also { publishLibraries(it) }
                            val items = itemsFor(uid, libs, "audiobook")
                            val covers = booksRepository.bookCoverUrls(items)
                            _uiState.update {
                                it.copy(
                                    audiobookItems = items,
                                    coverUrlsByKey = it.coverUrlsByKey + covers,
                                    loadedTabs = it.loadedTabs + tab,
                                )
                            }
                            fetchShelfInfo(libs)
                            publishAudiobookContinue()
                        }?.let { errors += it }
                    }
                }

                BookshelfTab.MANGA -> {
                    launch {
                        runGuarded {
                            val status = booksRepository.getMangaStatus()
                            _uiState.update { it.copy(mangaStatus = status) }
                        }
                    }
                    launch {
                        runGuarded {
                            val updates = booksRepository.getMangaUpdates().getOrNull()?.Items.orEmpty()
                            _uiState.update { it.copy(mangaUpdates = updates) }
                        }
                    }
                    launch {
                        runGuarded {
                            booksRepository.getMangaStreamLibrary().getOrNull()?.let { list ->
                                mangaStream = list
                                publishManga()
                            }
                        }
                    }
                    launch {
                        runGuarded {
                            val libs = librariesDeferred.await().also { publishLibraries(it) }
                            mangaLocal = itemsFor(uid, libs, "manga")
                            publishManga()
                        }?.let { errors += it }
                    }
                }
            }
        }

        tabLoadedAt[tab] = System.currentTimeMillis()
        val failed = errors.firstOrNull()
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                // Stale data stays on screen; an error only replaces an empty shelf.
                error = if (failed != null && tab !in state.loadedTabs) (failed.message ?: "Failed to load bookshelf") else null,
                loadedTabs = if (failed == null) state.loadedTabs + tab else state.loadedTabs,
            )
        }
        if (failed == null && _uiState.value.libraries.isNotEmpty() && tab in _uiState.value.loadedTabs) saveSnapshot(tab, uid)
    }

    private fun publishLibraries(libs: List<BaseItemDto>) {
        // A failed library lookup comes back empty; look again next time.
        if (libs.isEmpty()) librariesJob = null
        _uiState.update { it.copy(libraries = libs, selectedLibraryId = it.selectedLibraryId ?: libs.firstOrNull()?.Id) }
    }

    private suspend fun fetchShelfInfo(libs: List<BaseItemDto>) {
        val merged = HashMap<String, BookShelfInfoDto>()
        coroutineScope {
            libs.map { lib -> async { booksRepository.getBookShelfInfo(lib.Id) } }.awaitAll()
        }.forEach { merged.putAll(it) }
        val info = merged.mapKeys { BooksRepository.normalizeId(it.key) }
        _uiState.update { it.copy(shelfInfoById = it.shelfInfoById + info) }
    }

    private suspend fun publishEbookContinue() {
        val pairs = ebookContinuePairs.filterNot { BooksRepository.isComicOrManga(it.first) }
        val covers = booksRepository.bookCoverUrls(pairs.map { it.first })
        val info = _uiState.value.shelfInfoById
        val cards = pairs.map { (item, prog) ->
            val norm = BooksRepository.normalizeId(item.Id)
            ContinueReadingCardModel(
                itemId = item.Id,
                title = item.Name.orEmpty(),
                subtitle = info[norm]?.Authors
                    ?: item.People?.firstOrNull()?.Name
                    ?: item.AlbumArtist
                    ?: "",
                coverUrl = covers[norm],
                progress = prog.Progress.coerceIn(0.0, 1.0),
                kind = "book",
            )
        }
        _uiState.update {
            it.copy(
                continueReading = cards,
                coverUrlsByKey = it.coverUrlsByKey + covers,
                continueLoadedTabs = it.continueLoadedTabs + BookshelfTab.EBOOK,
            )
        }
    }

    private suspend fun publishAudiobookContinue() {
        val resumed = audiobookContinueItems
        val covers = booksRepository.bookCoverUrls(resumed)
        val info = _uiState.value.shelfInfoById
        val cards = resumed.map { item ->
            val norm = BooksRepository.normalizeId(item.Id)
            val pct = (item.UserData?.PlayedPercentage ?: 0.0) / 100.0
            ContinueReadingCardModel(
                itemId = item.Id,
                title = item.Name.orEmpty(),
                subtitle = item.AlbumArtist ?: item.Artists?.firstOrNull() ?: info[norm]?.Authors.orEmpty(),
                coverUrl = covers[norm],
                progress = pct.coerceIn(0.0, 1.0),
                kind = "audiobook",
            )
        }
        _uiState.update {
            it.copy(
                continueListening = cards,
                coverUrlsByKey = it.coverUrlsByKey + covers,
                continueLoadedTabs = it.continueLoadedTabs + BookshelfTab.AUDIOBOOK,
            )
        }
    }

    // Builds the manga shelf from whichever of the source library and the
    // local comics has arrived; called again as the other one lands.
    private suspend fun publishManga() {
        val stream = mangaStream
        val local = mangaLocal
        if (stream == null && local == null) return
        val progress = _uiState.value.readingProgressById
        val coverMap = HashMap<String, String>(_uiState.value.coverUrlsByKey)
        val seriesList = withContext(Dispatchers.Default) {
            buildMangaSeriesList(stream.orEmpty(), local.orEmpty(), progress, coverMap)
        }
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
                mangaSeries = seriesList,
                coverUrlsByKey = it.coverUrlsByKey + coverMap,
                continueManga = continueCards,
                continueLoadedTabs = it.continueLoadedTabs + BookshelfTab.MANGA,
                // A half-loaded shelf (only one source in) already counts as loaded.
                loadedTabs = it.loadedTabs + BookshelfTab.MANGA,
            )
        }
    }

    private suspend fun restoreSnapshot(tab: BookshelfTab, uid: String) {
        val snap = snapshots.load(uid, tab.kind) ?: return
        if (tab in _uiState.value.loadedTabs) return
        when (tab) {
            BookshelfTab.EBOOK -> {
                val covers = booksRepository.bookCoverUrls(snap.Items)
                val cards = snap.Continue.map { c ->
                    ContinueReadingCardModel(c.ItemId, c.Title, c.Subtitle, covers[BooksRepository.normalizeId(c.ItemId)] ?: booksRepository.bookCoverUrl(c.ItemId), c.Progress, c.Kind, c.MangaId, c.ChapterId)
                }
                _uiState.update {
                    it.copy(
                        ebookItems = snap.Items,
                        shelfInfoById = it.shelfInfoById + snap.Info,
                        readingProgressById = it.readingProgressById + snap.Progress,
                        coverUrlsByKey = it.coverUrlsByKey + covers,
                        continueReading = cards,
                        shelfByKind = snap.Shelf?.let { s -> it.shelfByKind + (tab.kind to s) } ?: it.shelfByKind,
                        loadedTabs = it.loadedTabs + tab,
                        continueLoadedTabs = it.continueLoadedTabs + tab,
                    )
                }
            }
            BookshelfTab.AUDIOBOOK -> {
                val covers = booksRepository.bookCoverUrls(snap.Items)
                val cards = snap.Continue.map { c ->
                    ContinueReadingCardModel(c.ItemId, c.Title, c.Subtitle, covers[BooksRepository.normalizeId(c.ItemId)] ?: booksRepository.bookCoverUrl(c.ItemId), c.Progress, c.Kind, c.MangaId, c.ChapterId)
                }
                _uiState.update {
                    it.copy(
                        audiobookItems = snap.Items,
                        shelfInfoById = it.shelfInfoById + snap.Info,
                        readingProgressById = it.readingProgressById + snap.Progress,
                        coverUrlsByKey = it.coverUrlsByKey + covers,
                        continueListening = cards,
                        shelfByKind = snap.Shelf?.let { s -> it.shelfByKind + (tab.kind to s) } ?: it.shelfByKind,
                        loadedTabs = it.loadedTabs + tab,
                        continueLoadedTabs = it.continueLoadedTabs + tab,
                    )
                }
            }
            BookshelfTab.MANGA -> {
                mangaStream = snap.Stream
                mangaLocal = snap.Local
                _uiState.update {
                    it.copy(
                        mangaUpdates = snap.Updates,
                        readingProgressById = it.readingProgressById + snap.Progress,
                        shelfByKind = snap.Shelf?.let { s -> it.shelfByKind + (tab.kind to s) } ?: it.shelfByKind,
                    )
                }
                publishManga()
            }
        }
    }

    private suspend fun saveSnapshot(tab: BookshelfTab, uid: String) {
        val state = _uiState.value
        val snap = when (tab) {
            BookshelfTab.EBOOK -> ShelfSnapshot(
                Items = state.ebookItems,
                Info = state.shelfInfoById,
                Continue = state.continueReading.map { it.toCached() },
                Progress = state.readingProgressById,
                Shelf = state.shelfByKind[tab.kind],
            )
            BookshelfTab.AUDIOBOOK -> ShelfSnapshot(
                Items = state.audiobookItems,
                Info = state.shelfInfoById,
                Continue = state.continueListening.map { it.toCached() },
                Progress = state.readingProgressById,
                Shelf = state.shelfByKind[tab.kind],
            )
            BookshelfTab.MANGA -> ShelfSnapshot(
                Stream = mangaStream.orEmpty(),
                Local = mangaLocal.orEmpty(),
                Updates = state.mangaUpdates,
                Progress = state.readingProgressById,
                Shelf = state.shelfByKind[tab.kind],
            )
        }
        snapshots.save(uid, tab.kind, snap)
    }

    private fun ContinueReadingCardModel.toCached() = CachedContinueCard(
        ItemId = itemId,
        Title = title,
        Subtitle = subtitle,
        Progress = progress,
        Kind = kind,
        MangaId = mangaId,
        ChapterId = chapterId,
    )

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
        if (open) {
            discoverJob?.cancel()
            _uiState.update {
                it.copy(
                    showDiscoverSheet = true,
                    discoverMediaTab = it.activeTab,
                    discoverQuery = "",
                    bookSearchResults = emptyList(),
                    mangaSearchResponse = null,
                )
            }
            loadDiscoverFeed()
        } else {
            discoverJob?.cancel()
            _uiState.update { it.copy(showDiscoverSheet = false, isDiscoverLoading = false) }
        }
    }

    fun setDiscoverMediaTab(tab: BookshelfTab) {
        if (_uiState.value.discoverMediaTab == tab) return
        discoverJob?.cancel()
        _uiState.update {
            it.copy(
                discoverMediaTab = tab,
                discoverQuery = "",
                bookSearchResults = emptyList(),
                mangaSearchResponse = null,
            )
        }
        loadDiscoverFeed()
    }

    fun setDiscoverQuery(query: String) {
        _uiState.update { it.copy(discoverQuery = query) }
        discoverSearchDebounceJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(bookSearchResults = emptyList(), mangaSearchResponse = null) }
            loadDiscoverFeed()
            return
        }
        discoverSearchDebounceJob = viewModelScope.launch {
            delay(400L)
            runDiscoverSearch()
        }
    }

    fun setBookDiscoverFilter(source: String, value: String? = null) {
        discoverSearchDebounceJob?.cancel()
        _uiState.update {
            it.copy(
                bookDiscoverSource = source,
                bookDiscoverValue = value,
                discoverQuery = "",
                bookSearchResults = emptyList(),
            )
        }
        loadDiscoverFeed()
    }

    fun setMangaDiscoverSort(sort: String) {
        discoverSearchDebounceJob?.cancel()
        _uiState.update {
            it.copy(
                mangaDiscoverSort = sort,
                discoverQuery = "",
                mangaSearchResponse = null,
            )
        }
        loadDiscoverFeed()
    }

    fun setMangaDiscoverGenres(genres: Set<String>) {
        discoverSearchDebounceJob?.cancel()
        _uiState.update {
            it.copy(
                mangaDiscoverGenres = genres,
                discoverQuery = "",
                mangaSearchResponse = null,
            )
        }
        loadDiscoverFeed()
    }

    fun toggleMangaDiscoverGenre(genre: String) {
        discoverSearchDebounceJob?.cancel()
        _uiState.update { state ->
            val next = if (genre in state.mangaDiscoverGenres) {
                state.mangaDiscoverGenres - genre
            } else {
                state.mangaDiscoverGenres + genre
            }
            state.copy(
                mangaDiscoverGenres = next,
                discoverQuery = "",
                mangaSearchResponse = null,
            )
        }
        loadDiscoverFeed()
    }

    fun setMangaDiscoverCountry(country: String?) {
        discoverSearchDebounceJob?.cancel()
        _uiState.update {
            it.copy(
                mangaDiscoverCountry = country,
                discoverQuery = "",
                mangaSearchResponse = null,
            )
        }
        loadDiscoverFeed()
    }

    fun runDiscoverSearch() {
        discoverSearchDebounceJob?.cancel()
        val state = _uiState.value
        val q = state.discoverQuery.trim()
        if (q.isEmpty()) {
            _uiState.update { it.copy(bookSearchResults = emptyList(), mangaSearchResponse = null) }
            loadDiscoverFeed()
            return
        }
        discoverJob?.cancel()
        _uiState.update { it.copy(isDiscoverLoading = true) }
        discoverJob = viewModelScope.launch {
            if (state.discoverMediaTab == BookshelfTab.MANGA) {
                val primaryGenre = state.mangaDiscoverGenres.toList().sorted().firstOrNull()
                val (searchRes, discoverRes) = coroutineScope {
                    val s = async { booksRepository.searchManga(q) }
                    val d = async {
                        booksRepository.discoverManga(
                            sort = state.mangaDiscoverSort,
                            genre = primaryGenre,
                            country = state.mangaDiscoverCountry,
                            query = q,
                        )
                    }
                    s.await() to d.await()
                }
                val qLower = q.lowercase()
                val resp = searchRes.getOrNull()?.let { raw ->
                    raw.copy(Sources = raw.Sources.filter { it.Results.isNotEmpty() })
                }
                val rawDiscover = discoverRes.getOrNull().orEmpty()
                val matchingDiscover = rawDiscover.filter { item ->
                    item.Title.lowercase().contains(qLower) ||
                        item.AltTitle.orEmpty().lowercase().contains(qLower) ||
                        item.Author.orEmpty().lowercase().contains(qLower)
                }
                val resolvedDiscover = if (matchingDiscover.isNotEmpty()) matchingDiscover else emptyList()
                val searchCovers = if (resp != null) resolveMangaSearchCovers(resp) else emptyMap()
                val discoverCovers = if (resolvedDiscover.isNotEmpty()) resolveMangaDiscoverCovers(resolvedDiscover) else emptyMap()
                val errMsg = if (resp == null && discoverRes.isFailure) {
                    searchRes.exceptionOrNull()?.message ?: discoverRes.exceptionOrNull()?.message ?: "Manga search failed"
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        mangaSearchResponse = resp ?: MangaSearchResponseDto(Sources = emptyList()),
                        mangaDiscoverResults = resolvedDiscover,
                        discoverProxyCoverByKey = it.discoverProxyCoverByKey + searchCovers + discoverCovers,
                        isDiscoverLoading = false,
                        statusMessage = errMsg ?: it.statusMessage,
                    )
                }
            } else {
                val mediaType = if (state.discoverMediaTab == BookshelfTab.AUDIOBOOK) "audiobook" else "ebook"
                booksRepository.searchBooks(q, mediaType)
                    .onSuccess { res ->
                        val covers = resolveBookSearchCovers(res)
                        _uiState.update {
                            it.copy(
                                bookSearchResults = res,
                                discoverProxyCoverByKey = it.discoverProxyCoverByKey + covers,
                                isDiscoverLoading = false,
                            )
                        }
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
        discoverJob?.cancel()
        if (state.discoverMediaTab == BookshelfTab.MANGA) {
            val selectedGenres = state.mangaDiscoverGenres.toList().sorted()
            val primaryGenre = selectedGenres.firstOrNull()
            val cacheKey = "${state.mangaDiscoverSort}|${primaryGenre.orEmpty()}|${state.mangaDiscoverCountry.orEmpty()}|"
            val cached = mangaDiscoverCache[cacheKey]
            if (cached != null) {
                val filtered = filterMangaByGenres(cached, selectedGenres)
                discoverJob = viewModelScope.launch {
                    val covers = resolveMangaDiscoverCovers(filtered)
                    _uiState.update {
                        it.copy(
                            mangaDiscoverResults = filtered,
                            discoverProxyCoverByKey = it.discoverProxyCoverByKey + covers,
                            isDiscoverLoading = false,
                        )
                    }
                }
                return
            }
            _uiState.update { it.copy(isDiscoverLoading = true) }
            discoverJob = viewModelScope.launch {
                booksRepository.discoverManga(
                    sort = state.mangaDiscoverSort,
                    genre = primaryGenre,
                    country = state.mangaDiscoverCountry,
                )
                    .onSuccess { list ->
                        mangaDiscoverCache[cacheKey] = list
                        val filtered = filterMangaByGenres(list, selectedGenres)
                        val covers = resolveMangaDiscoverCovers(filtered)
                        _uiState.update {
                            it.copy(
                                mangaDiscoverResults = filtered,
                                discoverProxyCoverByKey = it.discoverProxyCoverByKey + covers,
                                isDiscoverLoading = false,
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isDiscoverLoading = false) }
                    }
            }
        } else {
            val source = state.bookDiscoverSource
            val value = state.bookDiscoverValue
            val cacheKey = "$source|${value.orEmpty()}"
            val cached = bookDiscoverCache[cacheKey]
            if (cached != null) {
                discoverJob = viewModelScope.launch {
                    val covers = resolveBookDiscoverCovers(cached)
                    _uiState.update {
                        it.copy(
                            bookDiscoverResults = cached,
                            discoverProxyCoverByKey = it.discoverProxyCoverByKey + covers,
                            isDiscoverLoading = false,
                        )
                    }
                }
                return
            }
            _uiState.update { it.copy(isDiscoverLoading = true) }
            discoverJob = viewModelScope.launch {
                booksRepository.discoverBooks(source = source, value = value)
                    .onSuccess { list ->
                        bookDiscoverCache[cacheKey] = list
                        val covers = resolveBookDiscoverCovers(list)
                        _uiState.update {
                            it.copy(
                                bookDiscoverResults = list,
                                discoverProxyCoverByKey = it.discoverProxyCoverByKey + covers,
                                isDiscoverLoading = false,
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isDiscoverLoading = false) }
                    }
            }
        }
    }

    private fun filterMangaByGenres(
        items: List<MangaDiscoverDto>,
        selectedGenres: List<String>,
    ): List<MangaDiscoverDto> {
        if (selectedGenres.size <= 1) return items
        return items.filter { item ->
            selectedGenres.all { target ->
                item.Genres.any { it.equals(target, ignoreCase = true) }
            }
        }
    }

    private suspend fun resolveMangaDiscoverCovers(items: List<MangaDiscoverDto>): Map<String, String> {
        val out = HashMap<String, String>()
        items.forEach { item ->
            booksRepository.resolveDiscoverCoverUrl(item.CoverUrl)?.let { resolved ->
                out[mangaDiscoverCoverKey(item.Id)] = resolved
            }
        }
        return out
    }

    private suspend fun resolveMangaSearchCovers(resp: MangaSearchResponseDto): Map<String, String> {
        val out = HashMap<String, String>()
        resp.Sources.forEach { source ->
            source.Results.forEach { res ->
                booksRepository.resolveDiscoverCoverUrl(
                    rawUrl = res.ThumbnailUrl,
                    mangaId = res.MangaId,
                    title = res.Title,
                )?.let { resolved ->
                    out[mangaSearchCoverKey(res.MangaId)] = resolved
                }
            }
        }
        return out
    }

    private suspend fun resolveBookDiscoverCovers(items: List<BookDiscoverResultDto>): Map<String, String> {
        val out = HashMap<String, String>()
        items.forEach { item ->
            booksRepository.resolveDiscoverCoverUrl(item.CoverUrl)?.let { resolved ->
                out[bookDiscoverCoverKey(item.WorkId, item.Title)] = resolved
            }
        }
        return out
    }

    private suspend fun resolveBookSearchCovers(items: List<BookSearchResultDto>): Map<String, String> {
        val out = HashMap<String, String>()
        items.forEach { item ->
            booksRepository.resolveDiscoverCoverUrl(item.CoverUrl)?.let { resolved ->
                out[bookSearchCoverKey(item.WorkId, item.Title)] = resolved
            }
        }
        return out
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
                    val status = res.Status.lowercase()
                    val isOk = status.isEmpty() || status == "ok" || status == "added" || status == "duplicate" || status == "queued"
                    val key = BooksRepository.mangaSeriesKey(title)
                    val existing = _uiState.value.mangaSeries.firstOrNull {
                        it.mangaId == mangaId || it.key.removePrefix("s:") == key
                    }
                    val targetSeries = existing ?: MangaSeriesDisplayItem(
                        key = key,
                        title = title,
                        author = null,
                        status = null,
                        coverUrl = booksRepository.mangaStreamCoverUrl(mangaId, title),
                        mangaId = mangaId,
                        chapterCount = res.TotalChapters,
                        downloadedCount = 0,
                        readCount = 0,
                        latestUpload = 0L,
                        lastReadAt = null,
                        resumeChapterId = null,
                        resumeStreamChapterId = null,
                        firstChapterId = null,
                    )
                    _uiState.update {
                        it.copy(
                            showDiscoverSheet = if (isOk) false else it.showDiscoverSheet,
                            isDiscoverLoading = false,
                            statusMessage = res.Message
                                ?: "Added \"$title\" (${res.QueuedChapters}/${res.TotalChapters} chapters)",
                        )
                    }
                    loadTab(BookshelfTab.MANGA, force = true)
                    if (isOk) {
                        openMangaSeries(targetSeries)
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Manga request failed") }
                }
        }
    }

    fun requestMangaFromDiscover(item: MangaDiscoverDto) {
        val titleKey = BooksRepository.mangaSeriesKey(item.Title)
        val altKey = item.AltTitle?.takeIf { it.isNotBlank() }?.let { BooksRepository.mangaSeriesKey(it) }
        val existing = _uiState.value.mangaSeries.firstOrNull { series ->
            val sKey = series.key.removePrefix("s:")
            sKey == titleKey || (altKey != null && sKey == altKey)
        }
        if (existing != null) {
            discoverJob?.cancel()
            _uiState.update { it.copy(showDiscoverSheet = false, isDiscoverLoading = false) }
            openMangaSeries(existing)
            return
        }

        discoverJob?.cancel()
        _uiState.update { it.copy(isDiscoverLoading = true) }
        discoverJob = viewModelScope.launch {
            val byKey = booksRepository.findMangaStreamByKey(titleKey)?.takeIf { it.MangaId > 0 }
                ?: altKey?.let { booksRepository.findMangaStreamByKey(it)?.takeIf { r -> r.MangaId > 0 } }
            if (byKey != null) {
                val resolvedTitle = byKey.Title.ifBlank { item.Title }
                val cover = booksRepository.mangaStreamCoverUrl(byKey.MangaId, resolvedTitle)
                    ?: booksRepository.resolveDiscoverCoverUrl(item.CoverUrl)
                val series = MangaSeriesDisplayItem(
                    key = byKey.Key.ifBlank { titleKey },
                    title = resolvedTitle,
                    author = item.Author,
                    status = item.Status,
                    coverUrl = cover,
                    mangaId = byKey.MangaId,
                    chapterCount = 0,
                    downloadedCount = 0,
                    readCount = 0,
                    latestUpload = 0L,
                    lastReadAt = null,
                    resumeChapterId = null,
                    resumeStreamChapterId = null,
                    firstChapterId = null,
                )
                _uiState.update { it.copy(showDiscoverSheet = false, isDiscoverLoading = false) }
                openMangaSeries(series)
                return@launch
            }

            _uiState.update { it.copy(discoverQuery = item.Title) }
            val primarySearch = booksRepository.searchManga(item.Title)
            var finalResp = primarySearch.getOrNull()
            if ((finalResp == null || finalResp.Sources.all { it.Results.isEmpty() }) && !item.AltTitle.isNullOrBlank()) {
                val altSearch = booksRepository.searchManga(item.AltTitle)
                altSearch.getOrNull()?.let { altResp ->
                    if (altResp.Sources.any { it.Results.isNotEmpty() } || finalResp == null) {
                        finalResp = altResp
                    }
                }
            }
            val resolvedResp = finalResp
            if (resolvedResp != null) {
                val covers = resolveMangaSearchCovers(resolvedResp)
                _uiState.update {
                    it.copy(
                        mangaSearchResponse = resolvedResp,
                        discoverProxyCoverByKey = it.discoverProxyCoverByKey + covers,
                        isDiscoverLoading = false,
                    )
                }
            } else {
                val err = primarySearch.exceptionOrNull()
                _uiState.update {
                    it.copy(isDiscoverLoading = false, statusMessage = err?.message ?: "Source search failed")
                }
            }
        }
    }

    fun openPendingRequestsSheet(open: Boolean) {
        _uiState.update {
            // Keep a known-good status so reopening does not flash a loader.
            it.copy(
                showPendingRequestsSheet = open,
                chaptarrStatus = if (open && it.chaptarrStatus != ChaptarrStatus.CONFIGURED) ChaptarrStatus.LOADING else it.chaptarrStatus,
            )
        }
        if (open) {
            val mediaType = if (_uiState.value.activeTab == BookshelfTab.AUDIOBOOK) "audiobook" else "ebook"
            viewModelScope.launch {
                val result = booksRepository.getBookRequests(mediaType)
                val response = result.getOrNull()
                val status = resolveChaptarrStatus(response, result.exceptionOrNull())
                _uiState.update {
                    it.copy(
                        chaptarrStatus = status,
                        pendingBookRequests = response?.Items ?: it.pendingBookRequests,
                    )
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

    companion object {
        private const val RAW_ITEMS_TTL_MS = 60_000L
        private const val TAB_FRESH_MS = 60_000L

        fun mangaDiscoverCoverKey(id: Int): String = "md:$id"
        fun mangaSearchCoverKey(mangaId: Int): String = "ms:$mangaId"
        fun bookDiscoverCoverKey(workId: String, title: String): String = "bd:$workId|$title"
        fun bookSearchCoverKey(workId: String, title: String): String = "bs:$workId|$title"
    }
}
