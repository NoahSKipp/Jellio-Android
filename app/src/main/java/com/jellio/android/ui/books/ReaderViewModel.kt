package com.jellio.android.ui.books

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.android.data.books.AnnotationDto
import com.jellio.android.data.books.BookFileFormat
import com.jellio.android.data.books.BookMetadataDto
import com.jellio.android.data.books.BooksRepository
import com.jellio.android.data.books.CbzBookEngine
import com.jellio.android.data.books.CreateAnnotationRequest
import com.jellio.android.data.books.CreateVocabRequest
import com.jellio.android.data.books.DictionarySenseDto
import com.jellio.android.data.books.EpubBookEngine
import com.jellio.android.data.books.EpubChapterContent
import com.jellio.android.data.books.EpubSearchHit
import com.jellio.android.data.books.EpubTocEntry
import com.jellio.android.data.books.MangaReadingDirection
import com.jellio.android.data.books.MangaStreamChapterDto
import com.jellio.android.data.books.MangaStreamSeriesDto
import com.jellio.android.data.books.PdfBookEngine
import com.jellio.android.data.books.ReadingNowPlayingRequest
import com.jellio.android.data.books.ReadingSessionRequest
import com.jellio.android.data.books.UpdateSeriesPrefsRequest
import com.jellio.android.data.model.BaseItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class ReaderContentMode {
    LOADING,
    EPUB,
    PDF,
    MANGA,
    ERROR,
}

enum class EpubTheme(val label: String, val bgHex: Long, val textHex: Long) {
    DARK("Dark", 0xFF0D0D0DL, 0xFFF5F5F5L),
    SEPIA("Sepia", 0xFFF4ECD8L, 0xFF3A2E20L),
    LIGHT("Light", 0xFFFAFAFBL, 0xFF1A1A1AL),
}

enum class EpubFontFamily(val label: String) {
    SERIF("Serif"),
    SANS("Sans-Serif"),
    MONO("Monospace"),
}

enum class PdfThemeTint(val label: String) {
    NORMAL("Normal"),
    SEPIA("Warm"),
    INVERTED("Inverted Dark"),
}

enum class MangaReadingMode(val key: String, val label: String) {
    RTL("rtl", "Paged RTL"),
    LTR("ltr", "Paged LTR"),
    VERTICAL("vertical", "Webtoon"),
    VERTICAL_GAPS("vertical-gaps", "Vertical Gaps"),
    SPREAD("spread", "Double Spread"),
}

enum class MangaTapZoneMode(val label: String) {
    DEFAULT("Default"),
    L_SHAPED("L-Shaped"),
    KINDLE("Kindle"),
    EDGES("Edges"),
}

enum class MangaColorFilter(val label: String) {
    NONE("None"),
    WARM("Warm"),
    SEPIA("Sepia"),
    GRAYSCALE("Grayscale"),
    INVERT("Invert"),
}

enum class MangaBackgroundColor(val label: String, val hex: Long) {
    BLACK("Black", 0xFF000000L),
    DARK_GRAY("Dark Gray", 0xFF1A1A1AL),
    WHITE("White", 0xFFFAFAFBL),
}

data class ReaderStudyState(
    val selectedText: String = "",
    val sentenceContext: String = "",
    val locator: String = "",
    val position: Double = 0.0,
    val chapterTitle: String? = null,
    val targetLang: String = "EN",
    val sourceLang: String? = null,
    val isDefining: Boolean = false,
    val senses: List<DictionarySenseDto> = emptyList(),
    val defineError: String? = null,
    val isTranslating: Boolean = false,
    val translation: String? = null,
    val detectedSourceLang: String? = null,
    val translateError: String? = null,
    val highlightNote: String = "",
    val selectedHighlightColor: String = "yellow",
    val savedMessage: String? = null,
)

data class ReaderUiState(
    val itemId: String = "",
    val kind: String = "book",
    val mangaId: Int? = null,
    val chapterId: Int? = null,
    val mode: ReaderContentMode = ReaderContentMode.LOADING,
    val title: String = "",
    val subtitle: String? = null,
    val error: String? = null,
    val fromOfflineDownload: Boolean = false,
    val progress: Double = 0.0,

    // EPUB state
    val epubChapters: List<EpubTocEntry> = emptyList(),
    val currentSpineIndex: Int = 0,
    val totalChapters: Int = 0,
    val currentChapterContent: EpubChapterContent? = null,
    val epubContinuousScroll: Boolean = false,
    val epubLoadedChapters: Map<Int, EpubChapterContent> = emptyMap(),
    val epubTheme: EpubTheme = EpubTheme.DARK,
    val epubFontScale: Float = 1.0f, // 0.7..2.0
    val epubLineHeight: Float = 1.55f, // 1.2..2.0
    val epubFontFamily: EpubFontFamily = EpubFontFamily.SERIF,
    val epubMarginDp: Int = 20,
    val epubSearchQuery: String = "",
    val epubSearchHits: List<EpubSearchHit> = emptyList(),

    // PDF state
    val pdfPageCount: Int = 0,
    val pdfCurrentPage: Int = 0,
    val pdfContinuousScroll: Boolean = false,
    val pdfThemeTint: PdfThemeTint = PdfThemeTint.NORMAL,

    // Manga state
    val mangaPageCount: Int = 0,
    val mangaCurrentPage: Int = 0,
    val mangaStreamPageUrls: List<String> = emptyList(),
    val mangaReadingMode: MangaReadingMode = MangaReadingMode.RTL,
    val mangaTapZoneMode: MangaTapZoneMode = MangaTapZoneMode.DEFAULT,
    val mangaBrightness: Float = 1.0f, // 0.3..1.0
    val mangaColorFilter: MangaColorFilter = MangaColorFilter.NONE,
    val mangaBackground: MangaBackgroundColor = MangaBackgroundColor.BLACK,
    val mangaSeriesTitle: String? = null,
    val mangaSeriesKey: String? = null,
    val mangaChapterNumber: Float? = null,
    val prevChapter: MangaStreamChapterDto? = null,
    val nextChapter: MangaStreamChapterDto? = null,
    // First pages of the next chapter, for the screen to fetch ahead.
    val nextChapterPrefetchUrls: List<String> = emptyList(),

    // Annotations
    val annotations: List<AnnotationDto> = emptyList(),

    // Study popover
    val studyState: ReaderStudyState? = null,
    val toastMessage: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val booksRepository: BooksRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var activeEngine: Closeable? = null
    private val epubEngine: EpubBookEngine? get() = activeEngine as? EpubBookEngine
    private val pdfEngine: PdfBookEngine? get() = activeEngine as? PdfBookEngine
    private val cbzEngine: CbzBookEngine? get() = activeEngine as? CbzBookEngine

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private var heartbeatJob: Job? = null
    private var progressSaveJob: Job? = null
    private var sessionStartMs: Long = 0L
    private var startPageIndex: Int = 0
    private var maxPageReached: Int = 0
    private var sessionFlushed: Boolean = false
    private var loadJob: Job? = null
    private var nextPrefetchChapterId: Int? = null

    fun load(itemId: String, kind: String, mangaId: Int?, chapterId: Int?) {
        val current = _uiState.value
        if (current.itemId == itemId && current.mangaId == mangaId && current.chapterId == chapterId && current.mode != ReaderContentMode.LOADING) {
            return
        }
        closeResources()
        sessionStartMs = System.currentTimeMillis()
        startPageIndex = 0
        maxPageReached = 0
        sessionFlushed = false
        nextPrefetchChapterId = null

        _uiState.value = ReaderUiState(
            itemId = itemId,
            kind = kind,
            mangaId = mangaId,
            chapterId = chapterId,
            mode = ReaderContentMode.LOADING,
        )

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val streamed = kind == "manga" && chapterId != null
            // Streamed chapters are not library items, so there is no metadata to ask for.
            val metadataDeferred = if (streamed) null else async { booksRepository.getBookMetadata(itemId) }
            val progressDeferred = async { booksRepository.getReadingProgress(itemId) }
            launch {
                val annotations = booksRepository.getAnnotations(itemId)
                _uiState.update { it.copy(annotations = annotations) }
            }

            val savedProgress = progressDeferred.await()
            val metadata = metadataDeferred?.await()
            _uiState.update {
                it.copy(
                    title = metadata?.Title ?: it.title,
                    progress = savedProgress.Progress,
                )
            }

            if (kind == "manga" && chapterId != null) {
                loadStreamMangaChapter(itemId, mangaId, chapterId, savedProgress.Locator)
            } else {
                loadFileBook(itemId, kind, metadata, savedProgress.Locator)
            }
        }
    }

    private suspend fun loadFileBook(
        itemId: String,
        kind: String,
        metadata: BookMetadataDto?,
        savedLocator: String,
    ) {
        val opened = booksRepository.openBookFile(itemId)
        if (opened.isFailure) {
            _uiState.update {
                it.copy(
                    mode = ReaderContentMode.ERROR,
                    error = opened.exceptionOrNull()?.message ?: "Failed to open book file",
                )
            }
            return
        }
        val bookFile = opened.getOrThrow()
        when (bookFile.format) {
            BookFileFormat.EPUB -> {
                val engine = withContext(Dispatchers.IO) { EpubBookEngine(bookFile.file) }
                activeEngine = engine
                val parsed = EpubBookEngine.parseLocator(savedLocator)
                val initialSpine = (parsed?.first ?: 0).coerceIn(0, (engine.chapterCount - 1).coerceAtLeast(0))
                val initialChapter = withContext(Dispatchers.IO) { engine.getChapter(initialSpine) }
                val progress = engine.calculateProgress(initialSpine)
                _uiState.update {
                    it.copy(
                        mode = ReaderContentMode.EPUB,
                        title = engine.title ?: metadata?.Title ?: "Book",
                        subtitle = engine.author ?: metadata?.Authors,
                        fromOfflineDownload = bookFile.fromOfflineDownload,
                        epubChapters = engine.tocEntries,
                        currentSpineIndex = initialSpine,
                        totalChapters = engine.chapterCount,
                        currentChapterContent = initialChapter,
                        epubLoadedChapters = if (initialChapter != null) mapOf(initialSpine to initialChapter) else emptyMap(),
                        progress = progress,
                    )
                }
                startHeartbeat()
            }

            BookFileFormat.PDF -> {
                val engine = withContext(Dispatchers.IO) { PdfBookEngine(bookFile.file) }
                activeEngine = engine
                val total = engine.pageCount
                val initialPage = parsePageLocator(savedLocator, total)
                startPageIndex = initialPage
                maxPageReached = initialPage
                _uiState.update {
                    it.copy(
                        mode = ReaderContentMode.PDF,
                        title = metadata?.Title ?: bookFile.file.nameWithoutExtension,
                        subtitle = metadata?.Authors,
                        fromOfflineDownload = bookFile.fromOfflineDownload,
                        pdfPageCount = total,
                        pdfCurrentPage = initialPage,
                        progress = if (total > 0) (initialPage + 1).toDouble() / total else 0.0,
                    )
                }
                startHeartbeat()
            }

            BookFileFormat.CBZ, BookFileFormat.UNKNOWN -> {
                val engine = runCatching { withContext(Dispatchers.IO) { CbzBookEngine(bookFile.file) } }.getOrNull()
                if (engine == null || engine.pageCount == 0) {
                    _uiState.update {
                        it.copy(
                            mode = ReaderContentMode.ERROR,
                            error = "Unsupported or empty book archive",
                        )
                    }
                    return
                }
                activeEngine = engine
                val total = engine.pageCount
                val initialPage = parsePageLocator(savedLocator, total)
                startPageIndex = initialPage
                maxPageReached = initialPage
                val defaultMode = when (engine.comicInfo?.mangaDirection) {
                    MangaReadingDirection.LTR -> MangaReadingMode.LTR
                    else -> MangaReadingMode.RTL
                }
                val resolvedSeriesTitle = engine.comicInfo?.series ?: metadata?.Title
                val resolvedSeriesKey = resolvedSeriesTitle?.takeIf { it.isNotBlank() }?.let {
                    BooksRepository.mangaSeriesKey(it)
                }
                val (prevLocal, nextLocal) = withContext(Dispatchers.IO) {
                    resolveLocalSiblingChapters(itemId, resolvedSeriesTitle, engine.comicInfo?.number)
                }
                _uiState.update {
                    it.copy(
                        mode = ReaderContentMode.MANGA,
                        kind = "manga",
                        title = engine.comicInfo?.title ?: metadata?.Title ?: bookFile.file.nameWithoutExtension,
                        subtitle = engine.comicInfo?.writer ?: engine.comicInfo?.series ?: metadata?.Authors,
                        fromOfflineDownload = bookFile.fromOfflineDownload,
                        mangaPageCount = total,
                        mangaCurrentPage = initialPage,
                        mangaReadingMode = defaultMode,
                        mangaSeriesTitle = resolvedSeriesTitle,
                        mangaSeriesKey = resolvedSeriesKey,
                        mangaChapterNumber = engine.comicInfo?.number?.toFloatOrNull(),
                        prevChapter = prevLocal,
                        nextChapter = nextLocal,
                        progress = if (total > 0) (initialPage + 1).toDouble() / total else 0.0,
                    )
                }
                startHeartbeat()
            }
        }
    }

    private suspend fun resolveLocalSiblingChapters(
        currentItemId: String,
        seriesTitleHint: String?,
        chapterNumberHint: String?,
    ): Pair<MangaStreamChapterDto?, MangaStreamChapterDto?> {
        val uid = booksRepository.currentUserId() ?: return null to null
        val views = runCatching { booksRepository.getBookshelfLibraries(uid) }.getOrNull().orEmpty()
        if (views.isEmpty()) return null to null
        val allComics = views.flatMap { lib ->
            runCatching { booksRepository.getBookshelfItems(uid, lib.Id, "manga") }.getOrNull().orEmpty()
        }.distinctBy { BooksRepository.normalizeId(it.Id) }

        val normCurrent = BooksRepository.normalizeId(currentItemId)
        val currentDto = allComics.firstOrNull { BooksRepository.normalizeId(it.Id) == normCurrent }
        val seriesRaw = currentDto?.SeriesName?.takeIf { it.isNotBlank() }
            ?: seriesTitleHint?.takeIf { it.isNotBlank() }
            ?: currentDto?.Name
            ?: return null to null
        val targetKey = BooksRepository.mangaSeriesKey(seriesRaw)

        val siblings = allComics.filter { item ->
            val itemSeries = item.SeriesName?.takeIf { it.isNotBlank() } ?: item.Name.orEmpty()
            BooksRepository.mangaSeriesKey(itemSeries) == targetKey
        }.sortedWith(
            compareBy<BaseItemDto> { it.IndexNumber ?: Int.MAX_VALUE }
                .thenBy { it.Name.orEmpty() },
        )
        if (siblings.size <= 1) return null to null
        val idx = siblings.indexOfFirst { BooksRepository.normalizeId(it.Id) == normCurrent }
        if (idx < 0) return null to null

        fun BaseItemDto.toSiblingChapter(order: Int): MangaStreamChapterDto = MangaStreamChapterDto(
            Id = Id,
            ChapterId = 0,
            MangaId = 0,
            SeriesTitle = SeriesName ?: seriesRaw,
            SeriesKey = targetKey,
            Name = Name ?: "Chapter ${IndexNumber ?: (order + 1)}",
            Number = IndexNumber?.toFloat() ?: (order + 1).toFloat(),
            SourceOrder = order,
        )

        val prev = if (idx > 0) siblings[idx - 1].toSiblingChapter(idx - 1) else null
        val next = if (idx < siblings.lastIndex) siblings[idx + 1].toSiblingChapter(idx + 1) else null
        return prev to next
    }

    private suspend fun loadStreamMangaChapter(
        itemId: String,
        mangaId: Int?,
        chapterId: Int,
        savedLocator: String,
    ) {
        // A chapter kept on this device is checked locally; the stream path
        // never downloads the whole CBZ first.
        val offlineFile = booksRepository.offlineStreamChapterFile(itemId)
        coroutineScope {
            val chapterInfoDeferred = async { booksRepository.getMangaStreamChapter(itemId, mangaId).getOrNull() }
            val shelfDeferred = async { booksRepository.getShelf("manga") }
            val pageCountDeferred = async {
                if (offlineFile != null) 0 else booksRepository.getMangaStreamPageCount(chapterId)
            }
            // Previous and next chapter come from the whole series, the slowest
            // answer; the first page does not wait for them.
            val seriesDeferred = async {
                val id = mangaId ?: chapterInfoDeferred.await()?.MangaId
                id?.let { booksRepository.getMangaStreamSeries(it).getOrNull() }
            }

            val chapterInfo = chapterInfoDeferred.await()
            val resolvedMangaId = mangaId ?: chapterInfo?.MangaId
            val seriesKey = chapterInfo?.SeriesKey?.takeIf { it.isNotBlank() }
            val shelf = shelfDeferred.await()
            val prefs = seriesKey?.let { key ->
                val prefixed = if (key.startsWith("s:")) key else "s:$key"
                shelf.Series[prefixed] ?: shelf.Series[key]
            } ?: shelf.SeriesDefaults

            val preferredMode = MangaReadingMode.entries.firstOrNull {
                it.key == prefs?.ComicLayout || it.key == prefs?.ComicDirection
            } ?: MangaReadingMode.RTL

            if (offlineFile != null) {
                val engine = withContext(Dispatchers.IO) { CbzBookEngine(offlineFile) }
                activeEngine = engine
                val total = engine.pageCount
                val initialPage = parsePageLocator(savedLocator, total)
                startPageIndex = initialPage
                maxPageReached = initialPage
                _uiState.update {
                    it.copy(
                        mode = ReaderContentMode.MANGA,
                        kind = "manga",
                        mangaId = resolvedMangaId,
                        chapterId = chapterId,
                        title = chapterInfo?.Name ?: "Chapter",
                        subtitle = chapterInfo?.SeriesTitle,
                        fromOfflineDownload = true,
                        mangaPageCount = total,
                        mangaCurrentPage = initialPage,
                        mangaReadingMode = preferredMode,
                        mangaSeriesTitle = chapterInfo?.SeriesTitle,
                        mangaSeriesKey = seriesKey,
                        mangaChapterNumber = chapterInfo?.Number,
                        progress = if (total > 0) (initialPage + 1).toDouble() / total else 0.0,
                    )
                }
                startHeartbeat()
                applySeries(seriesDeferred.await(), itemId, chapterId)
                return@coroutineScope
            }

            // Streamed chapter page URLs
            val countFromApi = pageCountDeferred.await()
            val total = if (countFromApi > 0) countFromApi else (chapterInfo?.PageCount ?: 0)
            if (total <= 0) {
                _uiState.update {
                    it.copy(
                        mode = ReaderContentMode.ERROR,
                        error = "No pages available for this chapter",
                    )
                }
                return@coroutineScope
            }
            val urls = (0 until total).mapNotNull { idx ->
                booksRepository.mangaStreamPageUrl(chapterId, idx)
            }
            val initialPage = parsePageLocator(savedLocator, total)
            startPageIndex = initialPage
            maxPageReached = initialPage

            _uiState.update {
                it.copy(
                    mode = ReaderContentMode.MANGA,
                    kind = "manga",
                    mangaId = resolvedMangaId,
                    chapterId = chapterId,
                    title = chapterInfo?.Name ?: "Chapter",
                    subtitle = chapterInfo?.SeriesTitle,
                    mangaPageCount = urls.size,
                    mangaCurrentPage = initialPage,
                    mangaStreamPageUrls = urls,
                    mangaReadingMode = preferredMode,
                    mangaSeriesTitle = chapterInfo?.SeriesTitle,
                    mangaSeriesKey = seriesKey,
                    mangaChapterNumber = chapterInfo?.Number,
                    progress = if (urls.isNotEmpty()) (initialPage + 1).toDouble() / urls.size else 0.0,
                )
            }
            startHeartbeat()
            applySeries(seriesDeferred.await(), itemId, chapterId)
        }
    }

    // Fills in the series title and the neighbouring chapters once the series answers.
    private fun applySeries(series: MangaStreamSeriesDto?, itemId: String, chapterId: Int) {
        if (series == null || _uiState.value.chapterId != chapterId) return
        val ordered = series.Chapters
            .sortedWith(compareBy<MangaStreamChapterDto> { it.Number }.thenBy { it.SourceOrder })
        val curIdx = ordered.indexOfFirst { it.ChapterId == chapterId || it.Id == itemId }
        val prevCh = if (curIdx > 0) ordered[curIdx - 1] else null
        val nextCh = if (curIdx >= 0 && curIdx < ordered.lastIndex) ordered[curIdx + 1] else null
        _uiState.update {
            it.copy(
                prevChapter = prevCh,
                nextChapter = nextCh,
                subtitle = series.Title.ifBlank { null } ?: it.subtitle,
                mangaSeriesTitle = series.Title.ifBlank { null } ?: it.mangaSeriesTitle,
                mangaSeriesKey = it.mangaSeriesKey ?: series.Key.takeIf { key -> key.isNotBlank() },
            )
        }
        maybePrefetchNextChapter()
    }

    // Near the end of a streamed chapter, ask for the next chapter's page
    // count and hand its first pages to the screen to warm the image cache.
    private fun maybePrefetchNextChapter() {
        val st = _uiState.value
        val next = st.nextChapter ?: return
        if (st.mode != ReaderContentMode.MANGA || st.mangaStreamPageUrls.isEmpty()) return
        if (st.mangaCurrentPage < st.mangaPageCount - NEXT_CHAPTER_PREFETCH_LEAD) return
        if (next.ChapterId <= 0 || nextPrefetchChapterId == next.ChapterId) return
        nextPrefetchChapterId = next.ChapterId
        viewModelScope.launch {
            val count = booksRepository.getMangaStreamPageCount(next.ChapterId)
            if (count <= 0) return@launch
            val urls = (0 until minOf(count, NEXT_CHAPTER_PREFETCH_PAGES)).mapNotNull { idx ->
                booksRepository.mangaStreamPageUrl(next.ChapterId, idx)
            }
            _uiState.update { it.copy(nextChapterPrefetchUrls = urls) }
        }
    }

    // --- Page & Chapter Rendering ---

    suspend fun renderPdfPage(pageIndex: Int, zoom: Float = 1.5f): Bitmap? {
        val cacheKey = "pdf_${pageIndex}_${(zoom * 10).toInt()}"
        bitmapCache[cacheKey]?.let { return it }
        val engine = pdfEngine ?: return null
        val bmp = withContext(Dispatchers.IO) {
            engine.renderPage(pageIndex, zoom = zoom, baseWidthPx = 1280, maxDimensionPx = 3072)
        } ?: return null
        if (bitmapCache.size > 16) bitmapCache.clear()
        bitmapCache[cacheKey] = bmp
        return bmp
    }

    suspend fun renderCbzPage(pageIndex: Int): Bitmap? {
        val cacheKey = "cbz_$pageIndex"
        bitmapCache[cacheKey]?.let { return it }
        val engine = cbzEngine ?: return null
        val bmp = withContext(Dispatchers.IO) {
            engine.renderPageBitmap(pageIndex, maxDimension = 2048)
        } ?: return null
        if (bitmapCache.size > 20) bitmapCache.clear()
        bitmapCache[cacheKey] = bmp
        return bmp
    }

    suspend fun loadEpubChapterIfNeeded(spineIndex: Int): EpubChapterContent? {
        val existing = _uiState.value.epubLoadedChapters[spineIndex]
        if (existing != null) return existing
        val engine = epubEngine ?: return null
        val loaded = withContext(Dispatchers.IO) { engine.getChapter(spineIndex) } ?: return null
        _uiState.update { it.copy(epubLoadedChapters = it.epubLoadedChapters + (spineIndex to loaded)) }
        return loaded
    }

    fun selectEpubChapter(spineIndex: Int) {
        val engine = epubEngine ?: return
        val clamped = spineIndex.coerceIn(0, (engine.chapterCount - 1).coerceAtLeast(0))
        viewModelScope.launch {
            val content = loadEpubChapterIfNeeded(clamped)
            val progress = engine.calculateProgress(clamped)
            _uiState.update {
                it.copy(
                    currentSpineIndex = clamped,
                    currentChapterContent = content,
                    progress = progress,
                )
            }
            scheduleProgressSave(EpubBookEngine.formatLocator(clamped, 0), progress, engine.chapterCount)
        }
    }

    fun updateEpubReadingPosition(spineIndex: Int, paragraphOffset: Int = 0) {
        val engine = epubEngine ?: return
        val clamped = spineIndex.coerceIn(0, (engine.chapterCount - 1).coerceAtLeast(0))
        val chLen = _uiState.value.epubLoadedChapters[clamped]?.paragraphs?.size?.coerceAtLeast(1) ?: 1
        val progress = engine.calculateProgress(clamped, paragraphOffset, chLen)
        _uiState.update { it.copy(currentSpineIndex = clamped, progress = progress) }
        scheduleProgressSave(EpubBookEngine.formatLocator(clamped, paragraphOffset), progress, engine.chapterCount)
    }

    fun searchEpub(query: String) {
        _uiState.update { it.copy(epubSearchQuery = query) }
        val engine = epubEngine ?: return
        if (query.isBlank()) {
            _uiState.update { it.copy(epubSearchHits = emptyList()) }
            return
        }
        viewModelScope.launch {
            val hits = withContext(Dispatchers.IO) { engine.search(query, maxResults = 60) }
            _uiState.update { it.copy(epubSearchHits = hits) }
        }
    }

    fun updatePdfPage(pageIndex: Int) {
        val total = _uiState.value.pdfPageCount
        if (total <= 0) return
        val clamped = pageIndex.coerceIn(0, total - 1)
        if (clamped > maxPageReached) maxPageReached = clamped
        val progress = (clamped + 1).toDouble() / total.toDouble()
        _uiState.update { it.copy(pdfCurrentPage = clamped, progress = progress) }
        scheduleProgressSave("page:${clamped + 1}", progress, total)
    }

    fun updateMangaPage(pageIndex: Int) {
        val total = _uiState.value.mangaPageCount
        if (total <= 0) return
        val clamped = pageIndex.coerceIn(0, total - 1)
        if (clamped > maxPageReached) maxPageReached = clamped
        val progress = (clamped + 1).toDouble() / total.toDouble()
        _uiState.update { it.copy(mangaCurrentPage = clamped, progress = progress) }
        scheduleProgressSave("page:${clamped + 1}", progress, total)
        maybePrefetchNextChapter()
    }

    // --- EPUB & PDF Settings ---

    fun setEpubContinuousScroll(enabled: Boolean) = _uiState.update { it.copy(epubContinuousScroll = enabled) }
    fun setEpubTheme(theme: EpubTheme) = _uiState.update { it.copy(epubTheme = theme) }
    fun setEpubFontScale(scale: Float) = _uiState.update { it.copy(epubFontScale = scale.coerceIn(0.7f, 2.0f)) }
    fun setEpubLineHeight(height: Float) = _uiState.update { it.copy(epubLineHeight = height.coerceIn(1.2f, 2.0f)) }
    fun setEpubFontFamily(family: EpubFontFamily) = _uiState.update { it.copy(epubFontFamily = family) }
    fun setEpubMarginDp(dp: Int) = _uiState.update { it.copy(epubMarginDp = dp.coerceIn(8, 48)) }

    fun setPdfContinuousScroll(enabled: Boolean) = _uiState.update { it.copy(pdfContinuousScroll = enabled) }
    fun setPdfThemeTint(tint: PdfThemeTint) = _uiState.update { it.copy(pdfThemeTint = tint) }

    // --- Manga Settings (persisted per-series) ---

    fun setMangaReadingMode(mode: MangaReadingMode) {
        _uiState.update { it.copy(mangaReadingMode = mode) }
        val seriesKey = _uiState.value.mangaSeriesKey ?: return
        val prefixed = if (seriesKey.startsWith("s:")) seriesKey else "s:$seriesKey"
        val dir = if (mode == MangaReadingMode.LTR) "ltr" else if (mode == MangaReadingMode.RTL) "rtl" else null
        viewModelScope.launch {
            booksRepository.updateSeriesPrefs(
                UpdateSeriesPrefsRequest(
                    Key = prefixed,
                    ComicLayout = mode.key,
                    ComicDirection = dir,
                ),
            )
        }
    }

    fun setMangaTapZoneMode(zone: MangaTapZoneMode) = _uiState.update { it.copy(mangaTapZoneMode = zone) }
    fun setMangaBrightness(b: Float) = _uiState.update { it.copy(mangaBrightness = b.coerceIn(0.3f, 1.0f)) }
    fun setMangaColorFilter(f: MangaColorFilter) = _uiState.update { it.copy(mangaColorFilter = f) }
    fun setMangaBackground(bg: MangaBackgroundColor) = _uiState.update { it.copy(mangaBackground = bg) }

    // --- Annotations & Bookmarks ---

    fun addBookmarkAtCurrentPosition() {
        val st = _uiState.value
        val locator = when (st.mode) {
            ReaderContentMode.EPUB -> EpubBookEngine.formatLocator(st.currentSpineIndex, 0)
            ReaderContentMode.PDF -> "page:${st.pdfCurrentPage + 1}"
            ReaderContentMode.MANGA -> "page:${st.mangaCurrentPage + 1}"
            else -> ""
        }
        val chapterLabel = when (st.mode) {
            ReaderContentMode.EPUB -> st.currentChapterContent?.title ?: "Chapter ${st.currentSpineIndex + 1}"
            ReaderContentMode.PDF -> "Page ${st.pdfCurrentPage + 1}"
            ReaderContentMode.MANGA -> "Page ${st.mangaCurrentPage + 1}"
            else -> null
        }
        viewModelScope.launch {
            val created = booksRepository.addAnnotation(
                st.itemId,
                CreateAnnotationRequest(
                    Kind = "bookmark",
                    Locator = locator,
                    Position = st.progress,
                    Chapter = chapterLabel,
                ),
            )
            _uiState.update {
                it.copy(
                    annotations = it.annotations + created,
                    toastMessage = "Bookmark added ($chapterLabel)",
                )
            }
        }
    }

    fun deleteAnnotation(id: String) {
        val itemId = _uiState.value.itemId
        viewModelScope.launch {
            booksRepository.deleteAnnotation(itemId, id)
            _uiState.update { it.copy(annotations = it.annotations.filterNot { a -> a.Id == id }) }
        }
    }

    fun jumpToAnnotation(ann: AnnotationDto) {
        val st = _uiState.value
        when (st.mode) {
            ReaderContentMode.EPUB -> {
                val parsed = EpubBookEngine.parseLocator(ann.Locator)
                if (parsed != null) selectEpubChapter(parsed.first)
            }
            ReaderContentMode.PDF -> {
                val page = parsePageLocator(ann.Locator, st.pdfPageCount)
                updatePdfPage(page)
            }
            ReaderContentMode.MANGA -> {
                val page = parsePageLocator(ann.Locator, st.mangaPageCount)
                updateMangaPage(page)
            }
            else -> Unit
        }
    }

    // --- Study & Language Popover ---

    fun openStudyPopover(text: String, sentenceContext: String = text, locator: String = "") {
        val st = _uiState.value
        val resolvedLocator = locator.ifBlank {
            when (st.mode) {
                ReaderContentMode.EPUB -> EpubBookEngine.formatLocator(st.currentSpineIndex, 0)
                ReaderContentMode.PDF -> "page:${st.pdfCurrentPage + 1}"
                ReaderContentMode.MANGA -> "page:${st.mangaCurrentPage + 1}"
                else -> ""
            }
        }
        val firstWord = text.trim()
            .split(Regex("""\s+"""))
            .firstOrNull()
            ?.trim { !it.isLetterOrDigit() }
            .orEmpty()

        val study = ReaderStudyState(
            selectedText = text.trim(),
            sentenceContext = sentenceContext.trim(),
            locator = resolvedLocator,
            position = st.progress,
            chapterTitle = st.currentChapterContent?.title,
            sourceLang = epubEngine?.language,
            isDefining = firstWord.isNotEmpty(),
            isTranslating = text.isNotBlank(),
        )
        _uiState.update { it.copy(studyState = study) }

        if (firstWord.isNotEmpty()) {
            lookupWord(firstWord)
        }
        if (text.isNotBlank()) {
            translateSelection(text.trim(), study.targetLang)
        }
    }

    fun closeStudyPopover() {
        _uiState.update { it.copy(studyState = null) }
    }

    fun updateStudyHighlightColor(color: String) {
        _uiState.update { it.copy(studyState = it.studyState?.copy(selectedHighlightColor = color)) }
    }

    fun updateStudyHighlightNote(note: String) {
        _uiState.update { it.copy(studyState = it.studyState?.copy(highlightNote = note)) }
    }

    fun updateStudySelectedText(text: String) {
        val study = _uiState.value.studyState ?: return
        val firstWord = text.trim()
            .split(Regex("""\s+"""))
            .firstOrNull()
            ?.trim { !it.isLetterOrDigit() }
            .orEmpty()
        _uiState.update {
            it.copy(
                studyState = study.copy(
                    selectedText = text,
                    isDefining = firstWord.isNotEmpty(),
                    isTranslating = text.isNotBlank(),
                ),
            )
        }
        if (firstWord.isNotEmpty()) {
            lookupWord(firstWord)
        }
        if (text.isNotBlank()) {
            translateSelection(text.trim(), study.targetLang)
        }
    }

    fun updateStudyTargetLang(lang: String) {
        val study = _uiState.value.studyState ?: return
        _uiState.update { it.copy(studyState = study.copy(targetLang = lang, isTranslating = true)) }
        translateSelection(study.selectedText, lang)
    }

    private fun lookupWord(word: String) {
        val lang = _uiState.value.studyState?.sourceLang
        viewModelScope.launch {
            booksRepository.defineWord(word, lang)
                .onSuccess { senses ->
                    _uiState.update {
                        it.copy(
                            studyState = it.studyState?.copy(
                                isDefining = false,
                                senses = senses,
                                defineError = if (senses.isEmpty()) "No dictionary entries found" else null,
                            ),
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            studyState = it.studyState?.copy(
                                isDefining = false,
                                defineError = err.message ?: "Dictionary lookup failed",
                            ),
                        )
                    }
                }
        }
    }

    private fun translateSelection(text: String, targetLang: String) {
        val study = _uiState.value.studyState ?: return
        viewModelScope.launch {
            booksRepository.translate(
                text = text,
                targetLang = targetLang,
                sourceLang = study.sourceLang,
                contextSentence = study.sentenceContext,
            )
                .onSuccess { resp ->
                    _uiState.update {
                        it.copy(
                            studyState = it.studyState?.copy(
                                isTranslating = false,
                                translation = resp.Text,
                                detectedSourceLang = resp.DetectedSourceLang,
                                translateError = resp.Error,
                            ),
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            studyState = it.studyState?.copy(
                                isTranslating = false,
                                translateError = err.message ?: "Translation failed",
                            ),
                        )
                    }
                }
        }
    }

    fun saveHighlightFromStudy() {
        val st = _uiState.value
        val study = st.studyState ?: return
        viewModelScope.launch {
            val created = booksRepository.addAnnotation(
                st.itemId,
                CreateAnnotationRequest(
                    Kind = "highlight",
                    Locator = study.locator,
                    Position = study.position,
                    Text = study.selectedText,
                    Note = study.highlightNote.ifBlank { null },
                    Color = study.selectedHighlightColor,
                    Chapter = study.chapterTitle,
                ),
            )
            _uiState.update {
                it.copy(
                    annotations = it.annotations + created,
                    studyState = it.studyState?.copy(savedMessage = "Highlight saved"),
                )
            }
        }
    }

    fun saveToVocabFromStudy() {
        val st = _uiState.value
        val study = st.studyState ?: return
        val word = study.selectedText.trim().split(Regex("""\s+""")).firstOrNull().orEmpty().ifBlank { study.selectedText }
        val defSummary = study.senses.firstOrNull()?.Definitions?.firstOrNull()
        viewModelScope.launch {
            booksRepository.addVocabulary(
                CreateVocabRequest(
                    Word = word,
                    Sentence = study.sentenceContext.ifBlank { null },
                    Definition = defSummary,
                    Translation = study.translation,
                    SourceLang = study.detectedSourceLang ?: study.sourceLang,
                    TargetLang = study.targetLang,
                    ItemId = st.itemId,
                    ItemName = st.title,
                    Locator = study.locator,
                ),
            )
                .onSuccess {
                    _uiState.update {
                        it.copy(studyState = it.studyState?.copy(savedMessage = "Added \"$word\" to Vocab SRS deck"))
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(studyState = it.studyState?.copy(savedMessage = err.message ?: "Queued offline for Vocab deck"))
                    }
                }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // --- Progress, Heartbeat, Session Sync ---

    private fun scheduleProgressSave(locator: String, progress: Double, totalPages: Int?) {
        val itemId = _uiState.value.itemId
        if (itemId.isBlank()) return
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(500L)
            booksRepository.saveReadingProgress(itemId, locator, progress, totalPages)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                val st = _uiState.value
                if (st.itemId.isNotBlank()) {
                    val page = when (st.mode) {
                        ReaderContentMode.EPUB -> st.currentSpineIndex + 1
                        ReaderContentMode.PDF -> st.pdfCurrentPage + 1
                        ReaderContentMode.MANGA -> st.mangaCurrentPage + 1
                        else -> null
                    }
                    val pageCount = when (st.mode) {
                        ReaderContentMode.EPUB -> st.totalChapters
                        ReaderContentMode.PDF -> st.pdfPageCount
                        ReaderContentMode.MANGA -> st.mangaPageCount
                        else -> null
                    }
                    booksRepository.reportNowReading(
                        ReadingNowPlayingRequest(
                            ItemId = st.itemId,
                            Kind = st.kind,
                            Title = st.title,
                            SeriesTitle = st.mangaSeriesTitle,
                            MangaId = st.mangaId,
                            Page = page,
                            PageCount = pageCount,
                        ),
                    )
                }
                delay(30_000L)
            }
        }
    }

    fun flushSessionAndClose() {
        val st = _uiState.value
        val itemId = st.itemId
        if (!sessionFlushed && itemId.isNotBlank() && st.mode != ReaderContentMode.LOADING && st.mode != ReaderContentMode.ERROR) {
            sessionFlushed = true
            val elapsedSec = ((System.currentTimeMillis() - sessionStartMs) / 1000L).toInt().coerceAtLeast(1)
            val currentPage = when (st.mode) {
                ReaderContentMode.EPUB -> st.currentSpineIndex + 1
                ReaderContentMode.PDF -> st.pdfCurrentPage + 1
                ReaderContentMode.MANGA -> st.mangaCurrentPage + 1
                else -> 1
            }
            val totalPages = when (st.mode) {
                ReaderContentMode.EPUB -> st.totalChapters
                ReaderContentMode.PDF -> st.pdfPageCount
                ReaderContentMode.MANGA -> st.mangaPageCount
                else -> 0
            }
            val pagesRead = (maxPageReached - startPageIndex).coerceAtLeast(0)
            val locator = when (st.mode) {
                ReaderContentMode.EPUB -> EpubBookEngine.formatLocator(st.currentSpineIndex, 0)
                else -> "page:$currentPage"
            }
            val isFinished = st.progress >= 0.98
            CoroutineScope(SupervisorJob() + Dispatchers.IO + NonCancellable).launch {
                booksRepository.saveReadingProgress(itemId, locator, st.progress, totalPages.takeIf { it > 0 })
                booksRepository.reportReadingSession(
                    ReadingSessionRequest(
                        ItemId = itemId,
                        Kind = st.kind,
                        PagesRead = pagesRead,
                        CurrentPage = currentPage,
                        PageCount = totalPages.takeIf { it > 0 },
                        Finished = isFinished,
                        MangaId = st.mangaId,
                        ReadSeconds = elapsedSec,
                        ChapterNumber = st.mangaChapterNumber,
                    ),
                )
                if (isFinished && st.kind == "manga") {
                    booksRepository.syncTracker(st.mangaSeriesKey, st.mangaId)
                }
                booksRepository.clearNowReading(itemId)
            }
        }
        closeResources()
    }

    private fun closeResources() {
        heartbeatJob?.cancel()
        progressSaveJob?.cancel()
        bitmapCache.clear()
        runCatching { activeEngine?.close() }
        activeEngine = null
    }

    override fun onCleared() {
        flushSessionAndClose()
        super.onCleared()
    }

    private fun parsePageLocator(locator: String, totalPages: Int): Int {
        if (totalPages <= 0) return 0
        if (locator.startsWith("page:")) {
            val oneBased = locator.removePrefix("page:").toIntOrNull() ?: 1
            return (oneBased - 1).coerceIn(0, totalPages - 1)
        }
        val direct = locator.toIntOrNull()
        if (direct != null) {
            return (direct - 1).coerceIn(0, totalPages - 1)
        }
        return 0
    }

    private companion object {
        // Start fetching the next chapter this many pages before the end.
        const val NEXT_CHAPTER_PREFETCH_LEAD = 4
        const val NEXT_CHAPTER_PREFETCH_PAGES = 3
    }
}
