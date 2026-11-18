package com.jellio.android.ui.books

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jellio.android.data.books.AnnotationDto
import com.jellio.android.data.books.EpubBookEngine
import com.jellio.android.data.books.MangaStreamChapterDto
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioDanger
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.JellioTextTertiary
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

private val HIGHLIGHT_COLORS = listOf(
    "yellow" to Color(0xFFFACC15),
    "green" to Color(0xFF4ADE80),
    "blue" to Color(0xFF60A5FA),
    "pink" to Color(0xFFF472B6),
    "purple" to Color(0xFFC084FC),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    itemId: String,
    kind: String,
    mangaId: Int? = null,
    chapterId: Int? = null,
    onBack: () -> Unit,
    onOpenChapter: (itemId: String, mangaId: Int?, chapterId: Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var hudVisible by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showAnnotationsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(itemId, kind, mangaId, chapterId) {
        viewModel.load(itemId, kind, mangaId, chapterId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.flushSessionAndClose()
        }
    }

    BackHandler {
        viewModel.flushSessionAndClose()
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                when (state.mode) {
                    ReaderContentMode.EPUB -> Color(state.epubTheme.bgHex)
                    ReaderContentMode.MANGA -> Color(state.mangaBackground.hex)
                    else -> JellioBg
                },
            ),
    ) {
        when (state.mode) {
            ReaderContentMode.LOADING -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JellioSecondary)
                }
            }

            ReaderContentMode.ERROR -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(state.error ?: "Unable to open book", color = JellioDanger, fontSize = 15.sp)
                        Button(
                            onClick = {
                                viewModel.flushSessionAndClose()
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }

            ReaderContentMode.EPUB -> {
                EpubReaderBody(
                    state = state,
                    onToggleHud = { hudVisible = !hudVisible },
                    onSelectChapter = viewModel::selectEpubChapter,
                    onLoadChapter = { viewModel.loadEpubChapterIfNeeded(it) },
                    onUpdatePosition = viewModel::updateEpubReadingPosition,
                    onStudyParagraph = { text, locator ->
                        viewModel.openStudyPopover(text = text, sentenceContext = text, locator = locator)
                    },
                )
            }

            ReaderContentMode.PDF -> {
                PdfReaderBody(
                    state = state,
                    renderPage = viewModel::renderPdfPage,
                    onPageChanged = viewModel::updatePdfPage,
                    onToggleHud = { hudVisible = !hudVisible },
                )
            }

            ReaderContentMode.MANGA -> {
                MangaReaderBody(
                    state = state,
                    renderCbzPage = viewModel::renderCbzPage,
                    onPageChanged = viewModel::updateMangaPage,
                    onToggleHud = { hudVisible = !hudVisible },
                    onOpenAdjacentChapter = { ch ->
                        onOpenChapter(ch.Id, ch.MangaId, ch.ChapterId)
                    },
                )
                if (state.mangaBrightness < 0.99f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = (1f - state.mangaBrightness).coerceIn(0f, 0.7f))),
                    )
                }
            }
        }

        // Top & Bottom HUD overlays
        if (hudVisible && state.mode != ReaderContentMode.LOADING) {
            ReaderTopBar(
                state = state,
                onBack = {
                    viewModel.flushSessionAndClose()
                    onBack()
                },
                onOpenToc = { showTocSheet = true },
                onOpenSearch = { showSearchSheet = true },
                onOpenAnnotations = { showAnnotationsSheet = true },
                onAddBookmark = viewModel::addBookmarkAtCurrentPosition,
                onOpenSettings = { showSettingsSheet = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            ReaderBottomBar(
                state = state,
                onSelectEpubChapter = viewModel::selectEpubChapter,
                onSelectPdfPage = viewModel::updatePdfPage,
                onSelectMangaPage = viewModel::updateMangaPage,
                onStudyCurrent = {
                    val sample = state.currentChapterContent?.paragraphs?.firstOrNull().orEmpty()
                    viewModel.openStudyPopover(text = sample, sentenceContext = sample)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        state.toastMessage?.let { toast ->
            Surface(
                color = JellioBgElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, JellioBorder),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp, start = 24.dp, end = 24.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(toast, color = JellioText, fontSize = 13.sp)
                    IconButton(onClick = viewModel::clearToast, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    // Settings Sheet
    if (showSettingsSheet) {
        ReaderSettingsSheet(
            state = state,
            onDismiss = { showSettingsSheet = false },
            onEpubContinuousChange = viewModel::setEpubContinuousScroll,
            onEpubThemeChange = viewModel::setEpubTheme,
            onEpubFontScaleChange = viewModel::setEpubFontScale,
            onEpubLineHeightChange = viewModel::setEpubLineHeight,
            onEpubFontFamilyChange = viewModel::setEpubFontFamily,
            onEpubMarginChange = viewModel::setEpubMarginDp,
            onPdfContinuousChange = viewModel::setPdfContinuousScroll,
            onPdfThemeTintChange = viewModel::setPdfThemeTint,
            onMangaModeChange = viewModel::setMangaReadingMode,
            onMangaTapZoneChange = viewModel::setMangaTapZoneMode,
            onMangaBrightnessChange = viewModel::setMangaBrightness,
            onMangaColorFilterChange = viewModel::setMangaColorFilter,
            onMangaBackgroundChange = viewModel::setMangaBackground,
        )
    }

    // EPUB Table of Contents Sheet
    if (showTocSheet && state.mode == ReaderContentMode.EPUB) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            sheetState = sheetState,
            containerColor = JellioBg,
            contentColor = JellioText,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 560.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Text("Table of Contents", color = JellioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.epubChapters) { entry ->
                        val isCurrent = entry.spineIndex == state.currentSpineIndex
                        Surface(
                            onClick = {
                                viewModel.selectEpubChapter(entry.spineIndex)
                                showTocSheet = false
                            },
                            color = if (isCurrent) JellioBgElevated else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = entry.title,
                                color = if (isCurrent) JellioSecondary else JellioText,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (12 + entry.level * 14).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // EPUB Full-Book Search Sheet
    if (showSearchSheet && state.mode == ReaderContentMode.EPUB) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            sheetState = sheetState,
            containerColor = JellioBg,
            contentColor = JellioText,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 580.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Search Book", color = JellioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = state.epubSearchQuery,
                    onValueChange = viewModel::searchEpub,
                    placeholder = { Text("Search text across all chapters...", color = JellioTextTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    colors = jellioTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.epubSearchHits) { hit ->
                        Surface(
                            onClick = {
                                viewModel.selectEpubChapter(hit.spineIndex)
                                showSearchSheet = false
                            },
                            color = JellioBgElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, JellioBorder),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(hit.chapterTitle, color = JellioSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(hit.snippet, color = JellioText, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    // Bookmarks & Highlights Drawer Sheet
    if (showAnnotationsSheet) {
        AnnotationsDrawerSheet(
            annotations = state.annotations,
            onDismiss = { showAnnotationsSheet = false },
            onJump = { ann ->
                viewModel.jumpToAnnotation(ann)
                showAnnotationsSheet = false
            },
            onDelete = viewModel::deleteAnnotation,
        )
    }

    // Study & Language Popover Sheet
    state.studyState?.let { study ->
        ReaderStudySheet(
            study = study,
            onDismiss = viewModel::closeStudyPopover,
            onHighlightColorSelect = viewModel::updateStudyHighlightColor,
            onHighlightNoteChange = viewModel::updateStudyHighlightNote,
            onSaveHighlight = viewModel::saveHighlightFromStudy,
            onTargetLangChange = viewModel::updateStudyTargetLang,
            onSaveToVocab = viewModel::saveToVocabFromStudy,
        )
    }
}

@Composable
private fun ReaderTopBar(
    state: ReaderUiState,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onAddBookmark: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = JellioBg.copy(alpha = 0.9f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JellioText)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    color = JellioText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Text(
                        text = sub,
                        color = JellioTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.mode == ReaderContentMode.EPUB) {
                IconButton(onClick = onOpenToc) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of Contents", tint = JellioText)
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search Book", tint = JellioText)
                }
            }
            IconButton(onClick = onAddBookmark) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = "Add Bookmark", tint = JellioText)
            }
            IconButton(onClick = onOpenAnnotations) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Bookmarks & Highlights", tint = JellioSecondary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Reader Settings", tint = JellioText)
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState,
    onSelectEpubChapter: (Int) -> Unit,
    onSelectPdfPage: (Int) -> Unit,
    onSelectMangaPage: (Int) -> Unit,
    onStudyCurrent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = JellioBg.copy(alpha = 0.9f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (state.mode) {
                ReaderContentMode.EPUB -> {
                    val total = state.totalChapters.coerceAtLeast(1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = { onSelectEpubChapter((state.currentSpineIndex - 1).coerceAtLeast(0)) },
                            enabled = state.currentSpineIndex > 0,
                        ) {
                            Icon(Icons.Filled.NavigateBefore, contentDescription = "Previous Chapter", tint = JellioText)
                        }
                        Text(
                            text = "Chapter ${state.currentSpineIndex + 1} / $total (${(state.progress * 100).roundToInt()}%)",
                            color = JellioTextSecondary,
                            fontSize = 12.sp,
                        )
                        Row {
                            IconButton(onClick = onStudyCurrent) {
                                Icon(Icons.Filled.Translate, contentDescription = "Study / Translate", tint = JellioSecondary)
                            }
                            IconButton(
                                onClick = { onSelectEpubChapter((state.currentSpineIndex + 1).coerceAtMost(total - 1)) },
                                enabled = state.currentSpineIndex < total - 1,
                            ) {
                                Icon(Icons.Filled.NavigateNext, contentDescription = "Next Chapter", tint = JellioText)
                            }
                        }
                    }
                }

                ReaderContentMode.PDF -> {
                    val total = state.pdfPageCount.coerceAtLeast(1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${state.pdfCurrentPage + 1} / $total", color = JellioText, fontSize = 12.sp)
                        Slider(
                            value = state.pdfCurrentPage.toFloat(),
                            onValueChange = { onSelectPdfPage(it.roundToInt()) },
                            valueRange = 0f..(total - 1).coerceAtLeast(0).toFloat(),
                            colors = SliderDefaults.colors(thumbColor = JellioSecondary, activeTrackColor = JellioSecondary),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                ReaderContentMode.MANGA -> {
                    val total = state.mangaPageCount.coerceAtLeast(1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${state.mangaCurrentPage + 1} / $total", color = JellioText, fontSize = 12.sp)
                        Slider(
                            value = state.mangaCurrentPage.toFloat(),
                            onValueChange = { onSelectMangaPage(it.roundToInt()) },
                            valueRange = 0f..(total - 1).coerceAtLeast(0).toFloat(),
                            colors = SliderDefaults.colors(thumbColor = JellioSecondary, activeTrackColor = JellioSecondary),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                else -> Unit
            }
        }
    }
}

// --- EPUB Reader Body ---

@Composable
private fun EpubReaderBody(
    state: ReaderUiState,
    onToggleHud: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onLoadChapter: suspend (Int) -> com.jellio.android.data.books.EpubChapterContent?,
    onUpdatePosition: (Int, Int) -> Unit,
    onStudyParagraph: (text: String, locator: String) -> Unit,
) {
    val bgColor = Color(state.epubTheme.bgHex)
    val textColor = Color(state.epubTheme.textHex)
    val fontFamily = when (state.epubFontFamily) {
        EpubFontFamily.SERIF -> FontFamily.Serif
        EpubFontFamily.SANS -> FontFamily.SansSerif
        EpubFontFamily.MONO -> FontFamily.Monospace
    }
    val fontSize = (16f * state.epubFontScale).sp
    val lineHeight = (16f * state.epubFontScale * state.epubLineHeight).sp

    if (state.epubContinuousScroll) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentSpineIndex)
        LaunchedEffect(state.currentSpineIndex) {
            if (listState.firstVisibleItemIndex != state.currentSpineIndex) {
                listState.scrollToItem(state.currentSpineIndex)
            }
        }
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
                onUpdatePosition(idx, 0)
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 64.dp, bottom = 72.dp, start = state.epubMarginDp.dp, end = state.epubMarginDp.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .clickable(onClick = onToggleHud),
        ) {
            items(state.totalChapters.coerceAtLeast(0)) { spineIdx ->
                var chapter by remember(spineIdx, state.epubLoadedChapters[spineIdx]) {
                    mutableStateOf(state.epubLoadedChapters[spineIdx])
                }
                LaunchedEffect(spineIdx) {
                    if (chapter == null) {
                        chapter = onLoadChapter(spineIdx)
                    }
                }
                val ch = chapter
                if (ch != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = ch.title,
                            color = textColor,
                            fontFamily = fontFamily,
                            fontSize = (20f * state.epubFontScale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ch.paragraphs.forEachIndexed { pIdx, p ->
                                    Text(
                                        text = p,
                                        color = textColor,
                                        fontFamily = fontFamily,
                                        fontSize = fontSize,
                                        lineHeight = lineHeight,
                                        modifier = Modifier.pointerInput(spineIdx, pIdx, p) {
                                            detectTapGestures(
                                                onTap = { onToggleHud() },
                                                onLongPress = {
                                                    onStudyParagraph(p, EpubBookEngine.formatLocator(spineIdx, pIdx))
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = JellioSecondary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    } else {
        val pageCount = state.totalChapters.coerceAtLeast(1)
        val pagerState = rememberPagerState(initialPage = state.currentSpineIndex.coerceIn(0, pageCount - 1)) { pageCount }
        LaunchedEffect(state.currentSpineIndex) {
            if (pagerState.currentPage != state.currentSpineIndex && state.currentSpineIndex in 0 until pageCount) {
                pagerState.scrollToPage(state.currentSpineIndex)
            }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                if (page != state.currentSpineIndex) {
                    onSelectChapter(page)
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
        ) { spineIdx ->
            var chapter by remember(spineIdx, state.epubLoadedChapters[spineIdx]) {
                mutableStateOf(state.epubLoadedChapters[spineIdx])
            }
            LaunchedEffect(spineIdx) {
                if (chapter == null) {
                    chapter = onLoadChapter(spineIdx)
                }
            }
            val ch = chapter
            if (ch == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JellioSecondary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 64.dp, bottom = 78.dp, start = state.epubMarginDp.dp, end = state.epubMarginDp.dp)
                        .clickable(onClick = onToggleHud),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = ch.title,
                        color = textColor,
                        fontFamily = fontFamily,
                        fontSize = (20f * state.epubFontScale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ch.paragraphs.forEachIndexed { pIdx, p ->
                                Text(
                                    text = p,
                                    color = textColor,
                                    fontFamily = fontFamily,
                                    fontSize = fontSize,
                                    lineHeight = lineHeight,
                                    modifier = Modifier.pointerInput(spineIdx, pIdx, p) {
                                        detectTapGestures(
                                            onTap = { onToggleHud() },
                                            onLongPress = {
                                                onStudyParagraph(p, EpubBookEngine.formatLocator(spineIdx, pIdx))
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- PDF Reader Body ---

@Composable
private fun PdfReaderBody(
    state: ReaderUiState,
    renderPage: suspend (Int, Float) -> Bitmap?,
    onPageChanged: (Int) -> Unit,
    onToggleHud: () -> Unit,
) {
    val count = state.pdfPageCount.coerceAtLeast(1)
    val tintColorFilter = when (state.pdfThemeTint) {
        PdfThemeTint.NORMAL -> null
        PdfThemeTint.SEPIA -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.96f, 0f, 0f, 0f, 12f,
                    0f, 0.90f, 0f, 0f, 6f,
                    0f, 0f, 0.78f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        PdfThemeTint.INVERTED -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    if (state.pdfContinuousScroll) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.pdfCurrentPage)
        LaunchedEffect(state.pdfCurrentPage) {
            if (listState.firstVisibleItemIndex != state.pdfCurrentPage) {
                listState.scrollToItem(state.pdfCurrentPage)
            }
        }
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { onPageChanged(it) }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 56.dp, bottom = 72.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count) { pageIdx ->
                ZoomablePdfPage(
                    pageIndex = pageIdx,
                    renderPage = renderPage,
                    colorFilter = tintColorFilter,
                    onTap = onToggleHud,
                )
            }
        }
    } else {
        val pagerState = rememberPagerState(initialPage = state.pdfCurrentPage.coerceIn(0, count - 1)) { count }
        LaunchedEffect(state.pdfCurrentPage) {
            if (pagerState.currentPage != state.pdfCurrentPage && state.pdfCurrentPage in 0 until count) {
                pagerState.scrollToPage(state.pdfCurrentPage)
            }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIdx ->
            ZoomablePdfPage(
                pageIndex = pageIdx,
                renderPage = renderPage,
                colorFilter = tintColorFilter,
                onTap = onToggleHud,
            )
        }
    }
}

@Composable
private fun ZoomablePdfPage(
    pageIndex: Int,
    renderPage: suspend (Int, Float) -> Bitmap?,
    colorFilter: ColorFilter?,
    onTap: () -> Unit,
) {
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, scale > 1.8f) {
        val zoomQuality = if (scale > 1.8f) 2.5f else 1.5f
        bitmap = renderPage(pageIndex, zoomQuality)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp)
            .pointerInput(pageIndex) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = nextScale
                    offset = if (nextScale > 1.05f) offset + pan else Offset.Zero
                }
            }
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        } else {
            CircularProgressIndicator(color = JellioSecondary)
        }
    }
}

// --- Manga / Comic Reader Body ---

@Composable
private fun MangaReaderBody(
    state: ReaderUiState,
    renderCbzPage: suspend (Int) -> Bitmap?,
    onPageChanged: (Int) -> Unit,
    onToggleHud: () -> Unit,
    onOpenAdjacentChapter: (MangaStreamChapterDto) -> Unit,
) {
    val totalPages = state.mangaPageCount
    if (totalPages <= 0) return

    val colorFilter = mangaColorFilterFor(state.mangaColorFilter)
    val coroutineScope = rememberCoroutineScope()

    when (state.mangaReadingMode) {
        MangaReadingMode.VERTICAL, MangaReadingMode.VERTICAL_GAPS -> {
            val gap = if (state.mangaReadingMode == MangaReadingMode.VERTICAL_GAPS) 12.dp else 0.dp
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.mangaCurrentPage)
            LaunchedEffect(state.mangaCurrentPage) {
                if (listState.firstVisibleItemIndex != state.mangaCurrentPage &&
                    kotlin.math.abs(listState.firstVisibleItemIndex - state.mangaCurrentPage) > 1
                ) {
                    listState.scrollToItem(state.mangaCurrentPage)
                }
            }
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
                    if (idx < totalPages) onPageChanged(idx)
                }
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onToggleHud),
            ) {
                if (state.prevChapter != null) {
                    item(key = "prev_ch_card") {
                        ChapterTransitionCard(
                            label = "Previous Chapter",
                            chapter = state.prevChapter,
                            onClick = { onOpenAdjacentChapter(state.prevChapter) },
                        )
                    }
                }
                items(totalPages, key = { "page_$it" }) { pageIdx ->
                    MangaPageView(
                        pageIndex = pageIdx,
                        streamUrl = state.mangaStreamPageUrls.getOrNull(pageIdx),
                        renderCbzPage = renderCbzPage,
                        colorFilter = colorFilter,
                        allowPinchZoom = false,
                        onTapPosition = { _, _ -> onToggleHud() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.nextChapter != null) {
                    item(key = "next_ch_card") {
                        ChapterTransitionCard(
                            label = "Next Chapter",
                            chapter = state.nextChapter,
                            onClick = { onOpenAdjacentChapter(state.nextChapter) },
                        )
                    }
                }
            }
        }

        MangaReadingMode.SPREAD -> {
            val spreadCount = ceil(totalPages / 2.0).toInt().coerceAtLeast(1)
            val initialSpread = (state.mangaCurrentPage / 2).coerceIn(0, spreadCount - 1)
            val pagerState = rememberPagerState(initialPage = initialSpread) { spreadCount }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { spreadIdx ->
                    onPageChanged((spreadIdx * 2).coerceIn(0, totalPages - 1))
                }
            }
            HorizontalPager(
                state = pagerState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
            ) { spreadIdx ->
                val p1 = spreadIdx * 2
                val p2 = p1 + 1
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onToggleHud),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (p2 < totalPages) {
                        MangaPageView(
                            pageIndex = p2,
                            streamUrl = state.mangaStreamPageUrls.getOrNull(p2),
                            renderCbzPage = renderCbzPage,
                            colorFilter = colorFilter,
                            allowPinchZoom = false,
                            onTapPosition = { _, _ -> onToggleHud() },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    MangaPageView(
                        pageIndex = p1,
                        streamUrl = state.mangaStreamPageUrls.getOrNull(p1),
                        renderCbzPage = renderCbzPage,
                        colorFilter = colorFilter,
                        allowPinchZoom = false,
                        onTapPosition = { _, _ -> onToggleHud() },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }

        MangaReadingMode.RTL, MangaReadingMode.LTR -> {
            val isRtl = state.mangaReadingMode == MangaReadingMode.RTL
            // Extra slot at end for Next Chapter transition card if available
            val hasNext = state.nextChapter != null
            val hasPrev = state.prevChapter != null
            val offsetStart = if (hasPrev) 1 else 0
            val pagerTotal = totalPages + offsetStart + (if (hasNext) 1 else 0)
            val pagerState = rememberPagerState(
                initialPage = (state.mangaCurrentPage + offsetStart).coerceIn(0, pagerTotal - 1),
            ) { pagerTotal }

            LaunchedEffect(state.mangaCurrentPage) {
                val target = (state.mangaCurrentPage + offsetStart).coerceIn(0, pagerTotal - 1)
                if (pagerState.currentPage != target) {
                    pagerState.scrollToPage(target)
                }
            }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { slot ->
                    val pageIdx = slot - offsetStart
                    if (pageIdx in 0 until totalPages) {
                        onPageChanged(pageIdx)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                reverseLayout = isRtl,
                modifier = Modifier.fillMaxSize(),
            ) { slot ->
                when {
                    hasPrev && slot == 0 -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ChapterTransitionCard(
                                label = "Previous Chapter",
                                chapter = state.prevChapter!!,
                                onClick = { onOpenAdjacentChapter(state.prevChapter) },
                            )
                        }
                    }
                    hasNext && slot == pagerTotal - 1 -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ChapterTransitionCard(
                                label = "Next Chapter",
                                chapter = state.nextChapter!!,
                                onClick = { onOpenAdjacentChapter(state.nextChapter) },
                            )
                        }
                    }
                    else -> {
                        val pageIdx = (slot - offsetStart).coerceIn(0, totalPages - 1)
                        MangaPageView(
                            pageIndex = pageIdx,
                            streamUrl = state.mangaStreamPageUrls.getOrNull(pageIdx),
                            renderCbzPage = renderCbzPage,
                            colorFilter = colorFilter,
                            allowPinchZoom = true,
                            onTapPosition = { nx, ny ->
                                val action = resolveTapZoneAction(
                                    nx = nx,
                                    ny = ny,
                                    zoneMode = state.mangaTapZoneMode,
                                    isRtl = isRtl,
                                )
                                when (action) {
                                    TapAction.CENTER -> onToggleHud()
                                    TapAction.NEXT -> {
                                        val nextSlot = (pagerState.currentPage + 1).coerceAtMost(pagerTotal - 1)
                                        coroutineScope.launch { pagerState.animateScrollToPage(nextSlot) }
                                    }
                                    TapAction.PREV -> {
                                        val prevSlot = (pagerState.currentPage - 1).coerceAtLeast(0)
                                        coroutineScope.launch { pagerState.animateScrollToPage(prevSlot) }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private enum class TapAction { PREV, CENTER, NEXT }

private fun resolveTapZoneAction(
    nx: Float,
    ny: Float,
    zoneMode: MangaTapZoneMode,
    isRtl: Boolean,
): TapAction {
    val raw = when (zoneMode) {
        MangaTapZoneMode.DEFAULT -> when {
            nx < 0.3f -> if (isRtl) TapAction.NEXT else TapAction.PREV
            nx > 0.7f -> if (isRtl) TapAction.PREV else TapAction.NEXT
            else -> TapAction.CENTER
        }
        MangaTapZoneMode.L_SHAPED -> when {
            ny > 0.72f -> TapAction.NEXT
            nx < 0.28f -> TapAction.PREV
            nx > 0.72f -> TapAction.NEXT
            else -> TapAction.CENTER
        }
        MangaTapZoneMode.KINDLE -> when {
            nx < 0.25f -> TapAction.PREV
            nx > 0.75f -> TapAction.NEXT
            else -> TapAction.CENTER
        }
        MangaTapZoneMode.EDGES -> when {
            nx < 0.18f -> if (isRtl) TapAction.NEXT else TapAction.PREV
            nx > 0.82f -> if (isRtl) TapAction.PREV else TapAction.NEXT
            else -> TapAction.CENTER
        }
    }
    return raw
}

@Composable
private fun MangaPageView(
    pageIndex: Int,
    streamUrl: String?,
    renderCbzPage: suspend (Int) -> Bitmap?,
    colorFilter: ColorFilter?,
    allowPinchZoom: Boolean,
    onTapPosition: (nx: Float, ny: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    var cbzBitmap by remember(pageIndex, streamUrl) { mutableStateOf<Bitmap?>(null) }

    if (streamUrl == null) {
        LaunchedEffect(pageIndex) {
            cbzBitmap = renderCbzPage(pageIndex)
        }
    }

    val gestureMod = if (allowPinchZoom) {
        Modifier
            .pointerInput(pageIndex) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = nextScale
                    offset = if (nextScale > 1.05f) offset + pan else Offset.Zero
                }
            }
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onTap = { pos ->
                        val w = size.width.coerceAtLeast(1)
                        val h = size.height.coerceAtLeast(1)
                        onTapPosition(pos.x / w, pos.y / h)
                    },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    },
                )
            }
    } else {
        Modifier.pointerInput(pageIndex) {
            detectTapGestures(
                onTap = { pos ->
                    val w = size.width.coerceAtLeast(1)
                    val h = size.height.coerceAtLeast(1)
                    onTapPosition(pos.x / w, pos.y / h)
                },
            )
        }
    }

    Box(
        modifier = modifier.then(gestureMod),
        contentAlignment = Alignment.Center,
    ) {
        if (streamUrl != null) {
            AsyncImage(
                model = streamUrl,
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = if (allowPinchZoom) ContentScale.Fit else ContentScale.FillWidth,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        } else {
            val bmp = cbzBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = if (allowPinchZoom) ContentScale.Fit else ContentScale.FillWidth,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JellioSecondary)
                }
            }
        }
    }
}

@Composable
private fun ChapterTransitionCard(
    label: String,
    chapter: MangaStreamChapterDto,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = JellioBgElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, JellioBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, color = JellioSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(chapter.Name, color = JellioText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
            ) {
                Text("Open Chapter")
            }
        }
    }
}

private fun mangaColorFilterFor(filter: MangaColorFilter): ColorFilter? = when (filter) {
    MangaColorFilter.NONE -> null
    MangaColorFilter.WARM -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                1.0f, 0f, 0f, 0f, 8f,
                0f, 0.94f, 0f, 0f, 2f,
                0f, 0f, 0.84f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    MangaColorFilter.SEPIA -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    MangaColorFilter.GRAYSCALE -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    MangaColorFilter.INVERT -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

// --- Reader Settings Bottom Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onEpubContinuousChange: (Boolean) -> Unit,
    onEpubThemeChange: (EpubTheme) -> Unit,
    onEpubFontScaleChange: (Float) -> Unit,
    onEpubLineHeightChange: (Float) -> Unit,
    onEpubFontFamilyChange: (EpubFontFamily) -> Unit,
    onEpubMarginChange: (Int) -> Unit,
    onPdfContinuousChange: (Boolean) -> Unit,
    onPdfThemeTintChange: (PdfThemeTint) -> Unit,
    onMangaModeChange: (MangaReadingMode) -> Unit,
    onMangaTapZoneChange: (MangaTapZoneMode) -> Unit,
    onMangaBrightnessChange: (Float) -> Unit,
    onMangaColorFilterChange: (MangaColorFilter) -> Unit,
    onMangaBackgroundChange: (MangaBackgroundColor) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JellioBg,
        contentColor = JellioText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Reader Settings", color = JellioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            when (state.mode) {
                ReaderContentMode.EPUB -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Continuous Vertical Scroll", color = JellioText, fontSize = 14.sp)
                        Switch(checked = state.epubContinuousScroll, onCheckedChange = onEpubContinuousChange)
                    }

                    Text("Theme", color = JellioTextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EpubTheme.entries.forEach { t ->
                            FilterChip(
                                selected = state.epubTheme == t,
                                onClick = { onEpubThemeChange(t) },
                                label = { Text(t.label, fontSize = 12.sp) },
                                colors = jellioChipColors(),
                            )
                        }
                    }

                    Text("Font Family", color = JellioTextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EpubFontFamily.entries.forEach { f ->
                            FilterChip(
                                selected = state.epubFontFamily == f,
                                onClick = { onEpubFontFamilyChange(f) },
                                label = { Text(f.label, fontSize = 12.sp) },
                                colors = jellioChipColors(),
                            )
                        }
                    }

                    Text("Font Size: ${(state.epubFontScale * 100).roundToInt()}%", color = JellioTextSecondary, fontSize = 12.sp)
                    Slider(
                        value = state.epubFontScale,
                        onValueChange = onEpubFontScaleChange,
                        valueRange = 0.7f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = JellioSecondary, activeTrackColor = JellioSecondary),
                    )

                    Text("Line Height: ${String.format("%.2f", state.epubLineHeight)}", color = JellioTextSecondary, fontSize = 12.sp)
                    Slider(
                        value = state.epubLineHeight,
                        onValueChange = onEpubLineHeightChange,
                        valueRange = 1.2f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = JellioSecondary, activeTrackColor = JellioSecondary),
                    )

                    Text("Horizontal Margin: ${state.epubMarginDp} dp", color = JellioTextSecondary, fontSize = 12.sp)
                    Slider(
                        value = state.epubMarginDp.toFloat(),
                        onValueChange = { onEpubMarginChange(it.roundToInt()) },
                        valueRange = 8f..48f,
                        colors = SliderDefaults.colors(thumbColor = JellioSecondary, activeTrackColor = JellioSecondary),
                    )
                }

                ReaderContentMode.PDF -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Continuous Vertical Scroll", color = JellioText, fontSize = 14.sp)
                        Switch(checked = state.pdfContinuousScroll, onCheckedChange = onPdfContinuousChange)
                    }
                    Text("Page Tint", color = JellioTextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PdfThemeTint.entries.forEach { tint ->
                            FilterChip(
                                selected = state.pdfThemeTint == tint,
                                onClick = { onPdfThemeTintChange(tint) },
                                label = { Text(tint.label, fontSize = 12.sp) },
                                colors = jellioChipColors(),
                            )
                        }
                    }
                }

                ReaderContentMode.MANGA -> {
                    Text("Reading Mode", color = JellioTextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MangaReadingMode.entries) { m ->
                            FilterChip(
                                selected = state.mangaReadingMode == m,
                                onClick = { onMangaModeChange(m) },
                                label = { Text(m.label, fontSize = 12.sp) },
                                colors = jellioChipColors(),
                            )
                        }
                    }

                    Text("Tap Zones", color = JellioTextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MangaTapZoneMode.entries) { z ->
                            FilterChip(
                                selected = state.mangaTapZoneMode == z,
                                onClick = { onMangaTapZoneChange(z) },
                                label = { Text(z.label, fontSize = 12.sp) },
                                colors = jellioChipColors(),
                            )
                        }
                    }

                    Text("Color Filter", color = JellioTextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MangaColorFilter.entries) { f ->
                            FilterChip(
                                selected = state.mangaColorFilter == f,
                                onClick = { onMangaColorFilterChange(f) },
                                label = { Text(f.label, fontSize = 12.sp) },
                                colors = jellioChipColors(),
                            )
                        }
                    }

                    Text("Background", color = JellioTextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MangaBackgroundColor.entries.forEach { bg ->
                            FilterChip(
                                selected = state.mangaBackground == bg,
                                onClick = { onMangaBackgroundChange(bg) },
                                label = { Text(bg.label, fontSize = 12.sp) },
                                colors = jellioChipColors(),
                            )
                        }
                    }

                    Text("Brightness Dimmer: ${(state.mangaBrightness * 100).roundToInt()}%", color = JellioTextSecondary, fontSize = 12.sp)
                    Slider(
                        value = state.mangaBrightness,
                        onValueChange = onMangaBrightnessChange,
                        valueRange = 0.3f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = JellioSecondary, activeTrackColor = JellioSecondary),
                    )
                }

                else -> Unit
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// --- Bookmarks & Highlights Drawer Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnotationsDrawerSheet(
    annotations: List<AnnotationDto>,
    onDismiss: () -> Unit,
    onJump: (AnnotationDto) -> Unit,
    onDelete: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JellioBg,
        contentColor = JellioText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Bookmarks & Highlights (${annotations.size})", color = JellioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (annotations.isEmpty()) {
                Text("No bookmarks or highlights yet. Long-press any paragraph to highlight or study.", color = JellioTextSecondary, fontSize = 13.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(annotations, key = { it.Id }) { ann ->
                        val chipColor = HIGHLIGHT_COLORS.firstOrNull { it.first == ann.Color }?.second ?: JellioSecondary
                        Surface(
                            onClick = { onJump(ann) },
                            color = JellioBgElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, JellioBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(chipColor),
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        ann.Chapter ?: ann.Locator,
                                        color = JellioSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    ann.Text?.takeIf { it.isNotBlank() }?.let { txt ->
                                        Text(txt, color = JellioText, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                    ann.Note?.takeIf { it.isNotBlank() }?.let { note ->
                                        Text("Note: $note", color = JellioTextSecondary, fontSize = 12.sp)
                                    }
                                }
                                IconButton(onClick = { onDelete(ann.Id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = JellioDanger, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Study & Language Popover Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderStudySheet(
    study: ReaderStudyState,
    onDismiss: () -> Unit,
    onHighlightColorSelect: (String) -> Unit,
    onHighlightNoteChange: (String) -> Unit,
    onSaveHighlight: () -> Unit,
    onTargetLangChange: (String) -> Unit,
    onSaveToVocab: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JellioBg,
        contentColor = JellioText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Study & Highlight", color = JellioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            if (study.selectedText.isNotBlank()) {
                Surface(
                    color = JellioBgElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, JellioBorder),
                ) {
                    Text(
                        text = study.selectedText,
                        color = JellioText,
                        fontSize = 14.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Highlight Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HIGHLIGHT_COLORS.forEach { (name, color) ->
                    val selected = study.selectedHighlightColor == name
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (selected) 2.dp else 0.dp, if (selected) JellioText else Color.Transparent, CircleShape)
                            .clickable { onHighlightColorSelect(name) },
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onSaveHighlight,
                    colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Save Highlight", fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = study.highlightNote,
                onValueChange = onHighlightNoteChange,
                placeholder = { Text("Add optional note...", color = JellioTextTertiary, fontSize = 12.sp) },
                singleLine = true,
                colors = jellioTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(color = JellioBorder)

            // DeepL Translation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Translation (DeepL)", color = JellioSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("EN", "JA", "ES", "DE", "FR").forEach { lang ->
                        FilterChip(
                            selected = study.targetLang == lang,
                            onClick = { onTargetLangChange(lang) },
                            label = { Text(lang, fontSize = 11.sp) },
                            colors = jellioChipColors(),
                        )
                    }
                }
            }
            when {
                study.isTranslating -> CircularProgressIndicator(color = JellioSecondary, modifier = Modifier.size(20.dp))
                study.translateError != null -> Text(study.translateError, color = JellioDanger, fontSize = 12.sp)
                !study.translation.isNullOrBlank() -> {
                    Surface(
                        color = JellioBgElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, JellioBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(study.translation, color = JellioText, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            HorizontalDivider(color = JellioBorder)

            // Wiktionary Dictionary
            Text("Dictionary (Wiktionary)", color = JellioSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            when {
                study.isDefining -> CircularProgressIndicator(color = JellioSecondary, modifier = Modifier.size(20.dp))
                study.defineError != null -> Text(study.defineError, color = JellioTextSecondary, fontSize = 12.sp)
                study.senses.isNotEmpty() -> {
                    study.senses.take(4).forEach { sense ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                listOfNotNull(sense.PartOfSpeech, sense.Language).joinToString(" | "),
                                color = JellioTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            sense.Definitions.take(3).forEachIndexed { idx, def ->
                                Text("${idx + 1}. $def", color = JellioText, fontSize = 13.sp)
                            }
                            sense.Examples.take(2).forEach { ex ->
                                Text("\"$ex\"", color = JellioTextTertiary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Save to Vocab SRS Deck
            Button(
                onClick = onSaveToVocab,
                colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save to Vocabulary SRS Deck", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            study.savedMessage?.let { msg ->
                Text(msg, color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
