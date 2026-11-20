package com.jellio.android.ui.books

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jellio.android.data.books.BooksRepository
import com.jellio.android.data.i18n.Strings
import com.jellio.android.data.books.SeriesPrefsDto
import com.jellio.android.data.books.ShelfCategoryDto
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.offline.OfflineDownloadItem
import com.jellio.android.ui.common.isCompactScreen
import com.jellio.android.ui.common.shimmer
import com.jellio.android.ui.nav.LocalBottomBarPadding
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioDanger
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.JellioTextTertiary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    onOpenReader: (itemId: String, kind: String, mangaId: Int?, chapterId: Int?) -> Unit,
    onOpenAudiobook: (itemId: String) -> Unit = {},
    onOpenVocab: () -> Unit = {},
    initialKind: String = "ebook",
    modifier: Modifier = Modifier,
    viewModel: BookshelfViewModel = hiltViewModel(),
) {
    // Picks the tab before the first frame so the shelf never flashes Books.
    remember(initialKind) { viewModel.openKind(initialKind) }
    val state by viewModel.uiState.collectAsState()
    var sortMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JellioBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BookshelfHeader(
                state = state,
                onSelectTab = viewModel::selectTab,
                onOpenVocab = onOpenVocab,
                onOpenDiscover = { viewModel.openDiscoverSheet(true) },
                onOpenPendingRequests = { viewModel.openPendingRequestsSheet(true) },
                onOpenUpdates = { viewModel.openMangaUpdates(true) },
                onRefresh = viewModel::refresh,
            )

            // Search + Sort bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search ${state.activeTab.label.lowercase()}...", color = JellioTextTertiary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = JellioTextSecondary) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = JellioTextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = jellioTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )

                Box {
                    OutlinedButton(
                        onClick = { sortMenuOpen = true },
                        border = BorderStroke(1.dp, JellioBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioText),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(state.sortOption.label, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (state.sortDescending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                        modifier = Modifier.background(JellioBgElevated),
                    ) {
                        val options = if (state.activeTab == BookshelfTab.MANGA) {
                            ShelfSortOption.entries
                        } else {
                            listOf(
                                ShelfSortOption.TITLE,
                                ShelfSortOption.AUTHOR,
                                ShelfSortOption.DATE_ADDED,
                                ShelfSortOption.YEAR,
                                ShelfSortOption.LAST_READ,
                            )
                        }
                        options.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            opt.label,
                                            color = if (state.sortOption == opt) JellioSecondary else JellioText,
                                            fontWeight = if (state.sortOption == opt) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (state.sortOption == opt) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                if (state.sortDescending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                                contentDescription = null,
                                                tint = JellioSecondary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setSortOption(opt)
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            // Custom Shelf Categories Row
            val shelf = state.shelfByKind[state.activeTab.kind]
            val categories = shelf?.Categories.orEmpty()
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategoryId == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All", fontSize = 12.sp) },
                        colors = jellioChipColors(),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = state.selectedCategoryId == null,
                            borderColor = JellioBorder,
                            selectedBorderColor = JellioSecondary,
                        ),
                    )
                }
                items(categories, key = { it.Id }) { cat ->
                    FilterChip(
                        selected = state.selectedCategoryId == cat.Id,
                        onClick = { viewModel.selectCategory(cat.Id) },
                        label = { Text("${cat.Name} (${cat.Items.size})", fontSize = 12.sp) },
                        colors = jellioChipColors(),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = state.selectedCategoryId == cat.Id,
                            borderColor = JellioBorder,
                            selectedBorderColor = JellioSecondary,
                        ),
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.openCategoryManager(true) },
                        border = BorderStroke(1.dp, JellioBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioTextSecondary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Categories", fontSize = 12.sp)
                    }
                }
            }

            state.statusMessage?.let { msg ->
                Surface(
                    color = JellioBgElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, JellioBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(msg, color = JellioText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::clearStatusMessage, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = JellioTextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            val tabLoaded = state.activeTab in state.loadedTabs
            if (!tabLoaded && state.error == null) {
                BookshelfSkeleton(
                    continueTitle = if (state.activeTab == BookshelfTab.AUDIOBOOK) "Continue Listening" else "Continue Reading",
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (state.error != null && !tabLoaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.error.orEmpty(), color = JellioDanger, fontSize = 14.sp)
                        Button(
                            onClick = viewModel::refresh,
                            colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                // Sorting thousands of items is kept out of every recomposition.
                val filteredEbooks = remember(
                    state.ebookItems, state.searchQuery, state.sortOption, state.sortDescending,
                    state.selectedCategoryId, state.shelfByKind, state.shelfInfoById, state.readingProgressById, state.activeTab,
                ) { viewModel.filteredBooks(state.ebookItems) }
                val filteredAudiobooks = remember(
                    state.audiobookItems, state.searchQuery, state.sortOption, state.sortDescending,
                    state.selectedCategoryId, state.shelfByKind, state.shelfInfoById, state.readingProgressById, state.activeTab,
                ) { viewModel.filteredBooks(state.audiobookItems) }
                val filteredManga = remember(
                    state.mangaSeries, state.searchQuery, state.sortOption, state.sortDescending,
                    state.selectedCategoryId, state.shelfByKind, state.activeTab,
                ) { viewModel.filteredMangaSeries() }
                // One scroll state per tab, so each opens on its own continue row.
                androidx.compose.runtime.key(state.activeTab) {
                    BookshelfMainGrid(
                        state = state,
                        filteredEbooks = filteredEbooks,
                        filteredAudiobooks = filteredAudiobooks,
                        filteredManga = filteredManga,
                        onOpenReader = onOpenReader,
                        onOpenAudiobook = onOpenAudiobook,
                        onOpenMangaSeries = viewModel::openMangaSeries,
                        onDownloadItem = viewModel::downloadBookOrAudiobook,
                        onAssignCategories = { key -> viewModel.openAssignCategoriesFor(key) },
                    )
                }
            }
        }

        // Manga Series Detail Modal Sheet
        state.seriesDetail?.let { detail ->
            MangaSeriesDetailSheet(
                detail = detail,
                onDismiss = viewModel::closeMangaSeries,
                onToggleInLibrary = viewModel::toggleSeriesInLibrary,
                onOpenChapter = { ch ->
                    viewModel.closeMangaSeries()
                    onOpenReader(ch.id, "manga", ch.mangaId, ch.streamChapterId)
                },
                onToggleBookmark = viewModel::toggleChapterBookmark,
                onDownloadChapter = { ch ->
                    val s = detail.seriesItem
                    if (s != null) viewModel.downloadMangaChapter(ch, s.title, s.key)
                },
                onDownloadAll = { unreadOnly -> viewModel.downloadAllChapters(unreadOnly) },
                onUpdatePrefs = { excl, dup, fDl, fUn, fBm, sort, desc ->
                    viewModel.updateSeriesPreferences(excl, dup, fDl, fUn, fBm, sort, desc)
                },
            )
        }

        // Category Manager Modal
        if (state.showCategoryManager) {
            CategoryManagerDialog(
                categories = state.shelfByKind[state.activeTab.kind]?.Categories.orEmpty(),
                onDismiss = { viewModel.openCategoryManager(false) },
                onCreate = viewModel::createCategory,
                onRename = viewModel::renameCategory,
                onDelete = viewModel::deleteCategory,
            )
        }

        // Assign Item to Categories Dialog
        state.categoryAssignTargetKey?.let { targetKey ->
            val categories = state.shelfByKind[state.activeTab.kind]?.Categories.orEmpty()
            AssignCategoryDialog(
                targetKey = targetKey,
                categories = categories,
                onDismiss = { viewModel.openAssignCategoriesFor(null) },
                onSave = { selectedIds -> viewModel.updateItemCategories(targetKey, selectedIds) },
            )
        }

        // Discover & Request Sheet
        if (state.showDiscoverSheet) {
            DiscoverAndRequestSheet(
                state = state,
                onDismiss = { viewModel.openDiscoverSheet(false) },
                onSelectDiscoverTab = viewModel::setDiscoverMediaTab,
                onQueryChange = viewModel::setDiscoverQuery,
                onSearch = viewModel::runDiscoverSearch,
                onSelectBookFilter = viewModel::setBookDiscoverFilter,
                onSelectMangaSort = viewModel::setMangaDiscoverSort,
                onSetMangaGenres = viewModel::setMangaDiscoverGenres,
                onToggleMangaGenre = viewModel::toggleMangaDiscoverGenre,
                onSelectMangaCountry = viewModel::setMangaDiscoverCountry,
                onRequestBookSearch = viewModel::requestBookSearchResult,
                onRequestBookDiscover = viewModel::requestBookDiscoverResult,
                onRequestMangaSearch = viewModel::requestMangaFromSearch,
                onRequestMangaDiscover = viewModel::requestMangaFromDiscover,
            )
        }

        // Pending Requests Sheet
        if (state.showPendingRequestsSheet) {
            PendingRequestsSheet(
                status = state.chaptarrStatus,
                requests = state.pendingBookRequests,
                onDismiss = { viewModel.openPendingRequestsSheet(false) },
                onRetrySearch = viewModel::retryBookRequestSearch,
            )
        }

        // Manga Updates Sheet
        if (state.showMangaUpdatesSheet) {
            MangaUpdatesSheet(
                updates = state.mangaUpdates,
                isRefreshing = state.isRefreshingUpdates,
                onRefresh = viewModel::refreshMangaUpdates,
                onDismiss = { viewModel.openMangaUpdates(false) },
                onOpenChapter = { upd ->
                    val ch = upd.Chapter ?: return@MangaUpdatesSheet
                    viewModel.openMangaUpdates(false)
                    onOpenReader(ch.Id, "manga", upd.MangaId, ch.ChapterId)
                },
            )
        }
    }
}

// Placeholders in the real card shapes, so the shelf does not jump when data lands.
@Composable
private fun ContinueCardSkeleton() {
    Surface(
        color = JellioBgElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, JellioBorder),
        modifier = Modifier.width(260.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(82.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmer(),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.85f).height(13.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Box(Modifier.fillMaxWidth(0.55f).height(11.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).shimmer())
            }
        }
    }
}

@Composable
private fun ContinueRowSkeleton(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, color = JellioText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
            items(3) { ContinueCardSkeleton() }
        }
    }
}

@Composable
private fun ShelfCardSkeleton() {
    Surface(
        color = JellioBgElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, JellioBorder),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.68f).shimmer())
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(13.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            }
        }
    }
}

@Composable
private fun BookshelfSkeleton(continueTitle: String, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 136.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ContinueRowSkeleton(continueTitle) }
        items(9) { ShelfCardSkeleton() }
    }
}

@Composable
private fun BookshelfHeader(
    state: BookshelfUiState,
    onSelectTab: (BookshelfTab) -> Unit,
    onOpenVocab: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenPendingRequests: () -> Unit,
    onOpenUpdates: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JellioBg)
            .statusBarsPadding()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Reader",
                color = JellioText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onOpenVocab,
                    border = BorderStroke(1.dp, JellioBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioSecondary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Vocab", fontSize = 12.sp)
                }

                if (state.activeTab == BookshelfTab.MANGA) {
                    IconButton(onClick = onOpenUpdates) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Manga Updates", tint = JellioText)
                    }
                } else {
                    IconButton(onClick = onOpenPendingRequests) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = "Pending Requests", tint = JellioText)
                    }
                }

                IconButton(onClick = onOpenDiscover) {
                    Icon(Icons.Filled.Explore, contentDescription = "Discover & Request", tint = JellioSecondary)
                }

                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = JellioTextSecondary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BookshelfTab.entries.forEach { tab ->
                val selected = state.activeTab == tab
                val icon = when (tab) {
                    BookshelfTab.EBOOK -> Icons.Filled.MenuBook
                    BookshelfTab.MANGA -> Icons.Filled.AutoStories
                    BookshelfTab.AUDIOBOOK -> Icons.Filled.Headphones
                }
                Surface(
                    onClick = { onSelectTab(tab) },
                    color = if (selected) JellioSecondary else JellioBgElevated,
                    contentColor = if (selected) JellioBg else JellioTextSecondary,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (selected) JellioSecondary else JellioBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = tab.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookshelfMainGrid(
    state: BookshelfUiState,
    filteredEbooks: List<BaseItemDto>,
    filteredAudiobooks: List<BaseItemDto>,
    filteredManga: List<MangaSeriesDisplayItem>,
    onOpenReader: (String, String, Int?, Int?) -> Unit,
    onOpenAudiobook: (String) -> Unit,
    onOpenMangaSeries: (MangaSeriesDisplayItem) -> Unit,
    onDownloadItem: (BaseItemDto) -> Unit,
    onAssignCategories: (String) -> Unit,
) {
    val continueList = when (state.activeTab) {
        BookshelfTab.AUDIOBOOK -> state.continueListening
        BookshelfTab.MANGA -> state.continueManga
        BookshelfTab.EBOOK -> state.continueReading
    }

    val isCompact = isCompactScreen()
    val bottomBarPadding = if (isCompact) LocalBottomBarPadding.current else 0.dp
    val continueTitle = if (state.activeTab == BookshelfTab.AUDIOBOOK) "Continue Listening" else "Continue Reading"
    val searching = state.searchQuery.isNotBlank()
    val hasContinue = continueList.isNotEmpty() && !searching
    // Until the continue row has answered, a placeholder holds its place under
    // the same key, so the library never slides up into view before it.
    val continuePending = state.activeTab !in state.continueLoadedTabs && !searching
    val gridState = rememberLazyGridState()
    var userScrolled by remember { mutableStateOf(false) }
    var hadContinue by remember { mutableStateOf(hasContinue || continuePending) }
    LaunchedEffect(gridState) {
        gridState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) userScrolled = true
        }
    }
    LaunchedEffect(hasContinue, continuePending) {
        // The row appeared above a library the person has not touched: stay on it.
        if ((hasContinue || continuePending) && !hadContinue && !userScrolled) gridState.scrollToItem(0)
        hadContinue = hasContinue || continuePending
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 136.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 16.dp + bottomBarPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (continuePending && !hasContinue) {
            item(key = "continue_section", span = { GridItemSpan(maxLineSpan) }, contentType = "continue_section") {
                ContinueRowSkeleton(continueTitle)
            }
        } else if (hasContinue) {
            item(key = "continue_section", span = { GridItemSpan(maxLineSpan) }, contentType = "continue_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = continueTitle,
                        color = JellioText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueList, key = { "${it.kind}:${it.itemId}" }, contentType = { "continue_card" }) { card ->
                            ContinueReadingCard(
                                card = card,
                                onClick = {
                                    if (card.kind == "audiobook") {
                                        onOpenAudiobook(card.itemId)
                                    } else {
                                        onOpenReader(card.itemId, card.kind, card.mangaId, card.chapterId)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        when (state.activeTab) {
            BookshelfTab.EBOOK -> {
                if (filteredEbooks.isEmpty()) {
                    item(key = "empty_ebook", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_placeholder") {
                        EmptyShelfPlaceholder("No books found in this shelf.")
                    }
                } else {
                    items(filteredEbooks, key = { it.Id }, contentType = { "ebook_card" }) { item ->
                        val norm = BooksRepository.normalizeId(item.Id)
                        val info = state.shelfInfoById[norm]
                        val progress = state.readingProgressById[norm]?.Progress ?: 0.0
                        val dl = state.downloadsById[norm]
                        BookShelfGridCard(
                            title = item.Name.orEmpty(),
                            author = info?.Authors ?: item.People?.firstOrNull()?.Name,
                            coverUrl = state.coverUrlsByKey[norm],
                            progress = progress,
                            downloadItem = dl,
                            badgeText = item.Path?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.isNotBlank() },
                            onClick = { onOpenReader(item.Id, "book", null, null) },
                            onDownload = { onDownloadItem(item) },
                            onAssignCategories = { onAssignCategories(norm) },
                        )
                    }
                }
            }

            BookshelfTab.MANGA -> {
                if (filteredManga.isEmpty()) {
                    item(key = "empty_manga", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_placeholder") {
                        EmptyShelfPlaceholder("No manga found. Tap Discover to add series.")
                    }
                } else {
                    items(filteredManga, key = { it.key }, contentType = { "manga_card" }) { series ->
                        val pct = if (series.chapterCount > 0) {
                            (series.readCount.toDouble() / series.chapterCount.toDouble()).coerceIn(0.0, 1.0)
                        } else {
                            0.0
                        }
                        val badge = if (series.unreadCount > 0) "${series.unreadCount} unread" else "${series.chapterCount} ch"
                        BookShelfGridCard(
                            title = series.title,
                            author = series.author ?: series.status,
                            coverUrl = series.coverUrl,
                            progress = pct,
                            downloadItem = null,
                            badgeText = badge,
                            onClick = { onOpenMangaSeries(series) },
                            onDownload = null,
                            onAssignCategories = {
                                val sKey = if (series.key.startsWith("s:")) series.key else "s:${series.key}"
                                onAssignCategories(sKey)
                            },
                        )
                    }
                }
            }

            BookshelfTab.AUDIOBOOK -> {
                if (filteredAudiobooks.isEmpty()) {
                    item(key = "empty_audiobook", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_placeholder") {
                        EmptyShelfPlaceholder("No audiobooks found.")
                    }
                } else {
                    items(filteredAudiobooks, key = { it.Id }, contentType = { "audiobook_card" }) { item ->
                        val norm = BooksRepository.normalizeId(item.Id)
                        val info = state.shelfInfoById[norm]
                        val pct = (item.UserData?.PlayedPercentage ?: 0.0) / 100.0
                        val dl = state.downloadsById[norm]
                        BookShelfGridCard(
                            title = item.Name.orEmpty(),
                            author = item.AlbumArtist ?: item.Artists?.firstOrNull() ?: info?.Authors,
                            coverUrl = state.coverUrlsByKey[norm],
                            progress = pct.coerceIn(0.0, 1.0),
                            downloadItem = dl,
                            badgeText = "AUDIO",
                            onClick = { onOpenAudiobook(item.Id) },
                            onDownload = { onDownloadItem(item) },
                            onAssignCategories = { onAssignCategories(norm) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    card: ContinueReadingCardModel,
    onClick: () -> Unit,
) {
    val pctText = "${(card.progress * 100).roundToInt().coerceIn(0, 100)}%"
    Surface(
        onClick = onClick,
        color = JellioBgElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, JellioBorder),
        modifier = Modifier.width(260.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(82.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(JellioBg),
            ) {
                var coverFailed by remember(card.coverUrl) { mutableStateOf(false) }
                if (!card.coverUrl.isNullOrBlank() && !coverFailed) {
                    val context = LocalContext.current
                    val coverRequest = remember(card.coverUrl) {
                        ImageRequest.Builder(context)
                            .data(card.coverUrl)
                            .size(168, 246)
                            .build()
                    }
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        onError = { coverFailed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = JellioTextTertiary, modifier = Modifier.size(22.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(JellioBg.copy(alpha = 0.85f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(pctText, color = JellioSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = card.title,
                    color = JellioText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.subtitle.isNotBlank()) {
                    Text(
                        text = card.subtitle,
                        color = JellioTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(JellioBorder),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(card.progress.toFloat().coerceIn(0.02f, 1f))
                            .background(JellioSecondary),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookShelfGridCard(
    title: String,
    author: String?,
    coverUrl: String?,
    progress: Double,
    downloadItem: OfflineDownloadItem?,
    badgeText: String?,
    onClick: () -> Unit,
    onDownload: (() -> Unit)?,
    onAssignCategories: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = JellioBgElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, JellioBorder),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(JellioBg),
            ) {
                val context = LocalContext.current
                var coverFailed by remember(coverUrl) { mutableStateOf(false) }
                if (!coverUrl.isNullOrBlank() && !coverFailed) {
                    val coverRequest = remember(coverUrl) {
                        ImageRequest.Builder(context)
                            .data(coverUrl)
                            .size(360, 540)
                            .build()
                    }
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        onError = { coverFailed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = JellioTextTertiary, modifier = Modifier.size(36.dp))
                    }
                }

                if (!badgeText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(JellioBg.copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(badgeText, color = JellioSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (onDownload != null) {
                        val isDone = downloadItem?.status == OfflineDownloadItem.STATUS_COMPLETED
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(JellioBg.copy(alpha = 0.82f))
                                .clickable(enabled = !isDone) { onDownload() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isDone) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                contentDescription = "Download",
                                tint = if (isDone) Color(0xFF4ADE80) else JellioText,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(JellioBg.copy(alpha = 0.82f))
                            .clickable { onAssignCategories() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Category,
                            contentDescription = "Assign Category",
                            tint = JellioText,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                if (progress > 0.01) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(JellioBg.copy(alpha = 0.7f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                                .background(JellioSecondary),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = JellioText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!author.isNullOrBlank()) {
                    Text(
                        text = author,
                        color = JellioTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyShelfPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = JellioTextSecondary, fontSize = 14.sp)
    }
}

// --- Manga Series Detail Modal Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaSeriesDetailSheet(
    detail: MangaSeriesDetailState,
    onDismiss: () -> Unit,
    onToggleInLibrary: () -> Unit,
    onOpenChapter: (MangaChapterRowItem) -> Unit,
    onToggleBookmark: (MangaChapterRowItem) -> Unit,
    onDownloadChapter: (MangaChapterRowItem) -> Unit,
    onDownloadAll: (unreadOnly: Boolean) -> Unit,
    onUpdatePrefs: (
        excludedScanlators: List<String>?,
        duplicatesAsOne: Boolean?,
        filterDownloaded: String?,
        filterUnread: String?,
        filterBookmarked: String?,
        chapterSort: String?,
        chapterDescending: Boolean?,
    ) -> Unit,
) {
    val series = detail.seriesItem ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showScanlatorDialog by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JellioBg,
        contentColor = JellioText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .height(132.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(JellioBgElevated)
                        .border(1.dp, JellioBorder, RoundedCornerShape(10.dp)),
                ) {
                    var coverFailed by remember(series.coverUrl) { mutableStateOf(false) }
                    if (!series.coverUrl.isNullOrBlank() && !coverFailed) {
                        AsyncImage(
                            model = series.coverUrl,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            onError = { coverFailed = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = JellioTextTertiary, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(series.title, color = JellioText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!series.author.isNullOrBlank()) {
                        Text(series.author, color = JellioTextSecondary, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!series.status.isNullOrBlank()) {
                            Text(series.status, color = JellioSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        detail.streamSeries?.SourceName?.let { src ->
                            Text(src, color = JellioTextTertiary, fontSize = 11.sp)
                        }
                    }
                    Text(
                        "${detail.chapters.size} chapters shown (${series.readCount}/${series.chapterCount} read)",
                        color = JellioTextSecondary,
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onToggleInLibrary,
                            border = BorderStroke(1.dp, if (detail.inLibrary) JellioSecondary else JellioBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (detail.inLibrary) JellioSecondary else JellioTextSecondary,
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                if (detail.inLibrary) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (detail.inLibrary) "In Library" else "Add to Library", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action & Filter toolbar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    Button(
                        onClick = {
                            val target = detail.chapters.lastOrNull { !it.isRead } ?: detail.chapters.lastOrNull()
                            if (target != null) onOpenChapter(target)
                        },
                        enabled = detail.chapters.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("Read", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onDownloadAll(true) },
                        border = BorderStroke(1.dp, JellioBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioText),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download Unread", fontSize = 12.sp)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onDownloadAll(false) },
                        border = BorderStroke(1.dp, JellioBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioText),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text("Download All", fontSize = 12.sp)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showScanlatorDialog = true },
                        border = BorderStroke(1.dp, JellioBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioText),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text("Scanlators", fontSize = 12.sp)
                    }
                }
                item {
                    Box {
                        OutlinedButton(
                            onClick = { showFilterMenu = true },
                            border = BorderStroke(1.dp, JellioBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioText),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Filter / Sort", fontSize = 12.sp)
                        }
                        ChapterFilterDropdown(
                            expanded = showFilterMenu,
                            prefs = detail.prefs,
                            onDismiss = { showFilterMenu = false },
                            onUpdatePrefs = onUpdatePrefs,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = JellioBorder)

            if (detail.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JellioSecondary)
                }
            } else if (detail.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(detail.error, color = JellioDanger, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(detail.chapters, key = { it.id }) { ch ->
                        Surface(
                            onClick = { onOpenChapter(ch) },
                            color = JellioBgElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, JellioBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = ch.title,
                                        color = if (ch.isRead) JellioTextTertiary else JellioText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val meta = buildList {
                                        if (!ch.scanlator.isNullOrBlank()) add(ch.scanlator)
                                        if (ch.pageCount > 0) add("${ch.pageCount} pages")
                                        if (ch.progress in 0.01..0.94) add("${(ch.progress * 100).roundToInt()}%")
                                    }.joinToString(" | ")
                                    if (meta.isNotEmpty()) {
                                        Text(meta, color = JellioTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { onToggleBookmark(ch) }) {
                                    Icon(
                                        if (ch.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = "Bookmark chapter",
                                        tint = if (ch.isBookmarked) JellioSecondary else JellioTextSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onDownloadChapter(ch) },
                                    enabled = !ch.isDownloaded,
                                ) {
                                    Icon(
                                        if (ch.isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                        contentDescription = "Download chapter",
                                        tint = if (ch.isDownloaded) Color(0xFF4ADE80) else JellioTextSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScanlatorDialog) {
        ScanlatorFilterDialog(
            availableScanlators = detail.availableScanlators,
            excluded = detail.prefs.ExcludedScanlators.orEmpty(),
            duplicatesAsOne = detail.prefs.DuplicatesAsOne ?: false,
            onDismiss = { showScanlatorDialog = false },
            onSave = { nextExcluded, dup ->
                onUpdatePrefs(nextExcluded, dup, null, null, null, null, null)
                showScanlatorDialog = false
            },
        )
    }
}

@Composable
private fun ChapterFilterDropdown(
    expanded: Boolean,
    prefs: SeriesPrefsDto,
    onDismiss: () -> Unit,
    onUpdatePrefs: (
        excludedScanlators: List<String>?,
        duplicatesAsOne: Boolean?,
        filterDownloaded: String?,
        filterUnread: String?,
        filterBookmarked: String?,
        chapterSort: String?,
        chapterDescending: Boolean?,
    ) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(JellioBgElevated),
    ) {
        val dlNext = when (prefs.FilterDownloaded) {
            null, "all" -> "downloaded"
            "downloaded" -> "not_downloaded"
            else -> "all"
        }
        DropdownMenuItem(
            text = { Text("Downloaded: ${prefs.FilterDownloaded ?: "all"}", color = JellioText, fontSize = 13.sp) },
            onClick = { onUpdatePrefs(null, null, dlNext, null, null, null, null) },
        )
        val unreadNext = when (prefs.FilterUnread) {
            null, "all" -> "unread"
            "unread" -> "read"
            else -> "all"
        }
        DropdownMenuItem(
            text = { Text("Read status: ${prefs.FilterUnread ?: "all"}", color = JellioText, fontSize = 13.sp) },
            onClick = { onUpdatePrefs(null, null, null, unreadNext, null, null, null) },
        )
        val bmNext = when (prefs.FilterBookmarked) {
            null, "all" -> "bookmarked"
            "bookmarked" -> "not_bookmarked"
            else -> "all"
        }
        DropdownMenuItem(
            text = { Text("Bookmarked: ${prefs.FilterBookmarked ?: "all"}", color = JellioText, fontSize = 13.sp) },
            onClick = { onUpdatePrefs(null, null, null, null, bmNext, null, null) },
        )
        HorizontalDivider(color = JellioBorder)
        val desc = prefs.ChapterDescending ?: true
        DropdownMenuItem(
            text = { Text("Order: ${if (desc) "Descending" else "Ascending"}", color = JellioSecondary, fontSize = 13.sp) },
            onClick = { onUpdatePrefs(null, null, null, null, null, null, !desc) },
        )
        val nextSort = when (prefs.ChapterSort) {
            "source" -> "upload"
            "upload" -> "chapter"
            else -> "source"
        }
        DropdownMenuItem(
            text = { Text("Sort by: ${prefs.ChapterSort ?: "chapter"}", color = JellioText, fontSize = 13.sp) },
            onClick = { onUpdatePrefs(null, null, null, null, null, nextSort, null) },
        )
    }
}

@Composable
private fun ScanlatorFilterDialog(
    availableScanlators: List<String>,
    excluded: List<String>,
    duplicatesAsOne: Boolean,
    onDismiss: () -> Unit,
    onSave: (excludedScanlators: List<String>, duplicatesAsOne: Boolean) -> Unit,
) {
    var selectedExcluded by remember(excluded) { mutableStateOf(excluded.toSet()) }
    var dupOne by remember(duplicatesAsOne) { mutableStateOf(duplicatesAsOne) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JellioBgElevated,
        titleContentColor = JellioText,
        textContentColor = JellioText,
        title = { Text("Scanlator & Duplicate Filters", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dupOne = !dupOne },
                ) {
                    Checkbox(checked = dupOne, onCheckedChange = { dupOne = it })
                    Spacer(Modifier.width(6.dp))
                    Text("Deduplicate chapters (DuplicatesAsOne)", color = JellioText, fontSize = 13.sp)
                }
                HorizontalDivider(color = JellioBorder)
                if (availableScanlators.isEmpty()) {
                    Text("No scanlator metadata reported for this series.", color = JellioTextSecondary, fontSize = 12.sp)
                } else {
                    Text("Check scanlators to EXCLUDE:", color = JellioTextSecondary, fontSize = 12.sp)
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(availableScanlators) { scan ->
                            val isExcluded = scan in selectedExcluded
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedExcluded = if (isExcluded) selectedExcluded - scan else selectedExcluded + scan
                                    },
                            ) {
                                Checkbox(
                                    checked = isExcluded,
                                    onCheckedChange = { checked ->
                                        selectedExcluded = if (checked) selectedExcluded + scan else selectedExcluded - scan
                                    },
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(scan, color = JellioText, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedExcluded.toList(), dupOne) },
                colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = JellioTextSecondary)
            }
        },
    )
}

// --- Category Manager & Assignment Modals ---

@Composable
private fun CategoryManagerDialog(
    categories: List<ShelfCategoryDto>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (ShelfCategoryDto, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var editingCat by remember { mutableStateOf<ShelfCategoryDto?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JellioBgElevated,
        titleContentColor = JellioText,
        textContentColor = JellioText,
        title = { Text("Manage Categories", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New category name", color = JellioTextTertiary, fontSize = 12.sp) },
                        singleLine = true,
                        colors = jellioTextFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            onCreate(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
                    ) {
                        Text("Add", fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = JellioBorder)
                if (categories.isEmpty()) {
                    Text("No custom categories yet.", color = JellioTextSecondary, fontSize = 13.sp)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories, key = { it.Id }) { cat ->
                            if (editingCat?.Id == cat.Id) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = renameText,
                                        onValueChange = { renameText = it },
                                        singleLine = true,
                                        colors = jellioTextFieldColors(),
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        onRename(cat, renameText)
                                        editingCat = null
                                    }) {
                                        Icon(Icons.Filled.Check, contentDescription = "Save", tint = JellioSecondary)
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "${cat.Name} (${cat.Items.size})",
                                        color = JellioText,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        editingCat = cat
                                        renameText = cat.Name
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = JellioTextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onDelete(cat.Id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = JellioDanger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = JellioSecondary)
            }
        },
    )
}

@Composable
private fun AssignCategoryDialog(
    targetKey: String,
    categories: List<ShelfCategoryDto>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val normTarget = BooksRepository.normalizeId(targetKey.removePrefix("i:").removePrefix("s:"))
    val initialSelected = remember(targetKey, categories) {
        categories.filter { cat ->
            cat.Items.any {
                it == targetKey ||
                    BooksRepository.normalizeId(it.removePrefix("i:").removePrefix("s:")) == normTarget
            }
        }.map { it.Id }.toSet()
    }
    var selected by remember(initialSelected) { mutableStateOf(initialSelected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JellioBgElevated,
        titleContentColor = JellioText,
        textContentColor = JellioText,
        title = { Text("Assign to Categories", fontWeight = FontWeight.Bold) },
        text = {
            if (categories.isEmpty()) {
                Text("Create a category first from the Categories button.", color = JellioTextSecondary, fontSize = 13.sp)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(categories, key = { it.Id }) { cat ->
                        val checked = cat.Id in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - cat.Id else selected + cat.Id
                                },
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selected = if (isChecked) selected + cat.Id else selected - cat.Id
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(cat.Name, color = JellioText, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selected.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = JellioTextSecondary)
            }
        },
    )
}

// --- Discover & Request Sheet ---

private val ANILIST_MANGA_GENRES = listOf(
    "Action",
    "Adventure",
    "Comedy",
    "Drama",
    "Fantasy",
    "Horror",
    "Mystery",
    "Psychological",
    "Romance",
    "Sci-Fi",
    "Slice of Life",
    "Sports",
    "Supernatural",
    "Thriller",
)

private val MANGA_COUNTRY_FILTERS: List<Pair<String?, String>> = listOf(
    null to "All",
    "JP" to "Manga (JP)",
    "KR" to "Manhwa (KR)",
    "CN" to "Manhua (CN)",
)

private data class BookDiscoverOption(
    val source: String,
    val value: String?,
    val label: String,
)

private val BOOK_DISCOVER_OPTIONS = listOf(
    BookDiscoverOption("trending", null, "Trending"),
    BookDiscoverOption("subject", "fantasy", "Fantasy"),
    BookDiscoverOption("subject", "science_fiction", "Sci-Fi"),
    BookDiscoverOption("subject", "mystery_and_detective_stories", "Mystery"),
    BookDiscoverOption("subject", "thrillers", "Thrillers"),
    BookDiscoverOption("subject", "romance", "Romance"),
    BookDiscoverOption("subject", "classics", "Classics"),
    BookDiscoverOption("subject", "horror", "Horror"),
    BookDiscoverOption("subject", "history", "History"),
    BookDiscoverOption("subject", "philosophy", "Philosophy"),
    BookDiscoverOption("subject", "biography", "Biography"),
    BookDiscoverOption("subject", "psychology", "Psychology"),
    BookDiscoverOption("subject", "science", "Science"),
    BookDiscoverOption("subject", "poetry", "Poetry"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverAndRequestSheet(
    state: BookshelfUiState,
    onDismiss: () -> Unit,
    onSelectDiscoverTab: (BookshelfTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectBookFilter: (String, String?) -> Unit,
    onSelectMangaSort: (String) -> Unit,
    onSetMangaGenres: (Set<String>) -> Unit,
    onToggleMangaGenre: (String) -> Unit,
    onSelectMangaCountry: (String?) -> Unit,
    onRequestBookSearch: (com.jellio.android.data.books.BookSearchResultDto, String) -> Unit,
    onRequestBookDiscover: (com.jellio.android.data.books.BookDiscoverResultDto, String) -> Unit,
    onRequestMangaSearch: (Int, String) -> Unit,
    onRequestMangaDiscover: (com.jellio.android.data.books.MangaDiscoverDto) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val discoverTab = state.discoverMediaTab
    val isManga = discoverTab == BookshelfTab.MANGA
    val bookType = if (discoverTab == BookshelfTab.AUDIOBOOK) "audiobook" else "ebook"
    var showGenreDialog by remember { mutableStateOf(false) }

    val libraryMangaKeys = remember(state.mangaSeries) {
        state.mangaSeries.map { it.key.removePrefix("s:") }.toSet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JellioBg,
        contentColor = JellioText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (isManga) "Discover & Read Manga" else "Discover & Request ${discoverTab.label}",
                color = JellioText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            // Media tab switcher inside Discover
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookshelfTab.entries.forEach { tab ->
                    val selected = discoverTab == tab
                    val icon = when (tab) {
                        BookshelfTab.EBOOK -> Icons.Filled.MenuBook
                        BookshelfTab.MANGA -> Icons.Filled.AutoStories
                        BookshelfTab.AUDIOBOOK -> Icons.Filled.Headphones
                    }
                    Surface(
                        onClick = { onSelectDiscoverTab(tab) },
                        color = if (selected) JellioSecondary else JellioBgElevated,
                        contentColor = if (selected) JellioBg else JellioTextSecondary,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) JellioSecondary else JellioBorder),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = tab.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.discoverQuery,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            if (isManga) "Search Suwayomi sources..." else "Search Open Library / Chaptarr...",
                            color = JellioTextTertiary,
                            fontSize = 13.sp,
                        )
                    },
                    trailingIcon = {
                        if (state.discoverQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                onQueryChange("")
                                onSearch()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = JellioTextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    colors = jellioTextFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onSearch,
                    colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
                ) {
                    Text("Search", fontSize = 12.sp)
                }
            }

            if (isManga) {
                // Sort + Genre picker row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(listOf("trending" to "Trending", "popular" to "Popular", "top" to "Top Rated")) { (key, label) ->
                        FilterChip(
                            selected = state.mangaDiscoverSort == key,
                            onClick = { onSelectMangaSort(key) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = jellioChipColors(),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = state.mangaDiscoverSort == key,
                                borderColor = JellioBorder,
                                selectedBorderColor = JellioSecondary,
                            ),
                        )
                    }
                    item {
                        val genreCount = state.mangaDiscoverGenres.size
                        OutlinedButton(
                            onClick = { showGenreDialog = true },
                            border = BorderStroke(1.dp, if (genreCount > 0) JellioSecondary else JellioBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (genreCount > 0) JellioSecondary else JellioText,
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (genreCount > 0) "Genres ($genreCount)" else "Genres",
                                fontSize = 12.sp,
                                fontWeight = if (genreCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Country filter chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(MANGA_COUNTRY_FILTERS) { (code, label) ->
                        val selected = state.mangaDiscoverCountry == code
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectMangaCountry(code) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = jellioChipColors(),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = JellioBorder,
                                selectedBorderColor = JellioSecondary,
                            ),
                        )
                    }
                }

                // Selected genre chips
                if (state.mangaDiscoverGenres.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(state.mangaDiscoverGenres.toList().sorted()) { genre ->
                            FilterChip(
                                selected = true,
                                onClick = { onToggleMangaGenre(genre) },
                                label = { Text(genre, fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove $genre", modifier = Modifier.size(13.dp))
                                },
                                colors = jellioChipColors(),
                            )
                        }
                        item {
                            TextButton(
                                onClick = { onSetMangaGenres(emptySet()) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Clear", color = JellioTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(BOOK_DISCOVER_OPTIONS, key = { "${it.source}_${it.value.orEmpty()}" }) { opt ->
                        val selected = state.bookDiscoverSource == opt.source && state.bookDiscoverValue == opt.value
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectBookFilter(opt.source, opt.value) },
                            label = { Text(opt.label, fontSize = 12.sp) },
                            colors = jellioChipColors(),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = JellioBorder,
                                selectedBorderColor = JellioSecondary,
                            ),
                        )
                    }
                }
            }

            if (state.isDiscoverLoading) {
                DiscoverSkeletonList(modifier = Modifier.fillMaxSize())
            } else if (isManga) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    val searchResp = state.mangaSearchResponse
                    val nonEmptySources = searchResp?.Sources?.filter { it.Results.isNotEmpty() }.orEmpty()
                    val activeQuery = state.discoverQuery.trim()
                    val shownDiscover = if (activeQuery.isNotEmpty()) {
                        val qLower = activeQuery.lowercase()
                        state.mangaDiscoverResults.filter { item ->
                            item.Title.lowercase().contains(qLower) ||
                                item.AltTitle.orEmpty().lowercase().contains(qLower) ||
                                item.Author.orEmpty().lowercase().contains(qLower)
                        }
                    } else {
                        state.mangaDiscoverResults
                    }
                    if (nonEmptySources.isNotEmpty()) {
                        nonEmptySources.forEach { source ->
                            item(key = "src_hdr_${source.SourceId}") {
                                Text(
                                    "${source.SourceName} (${source.Lang})",
                                    color = JellioSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            items(source.Results, key = { "${source.SourceId}_${it.MangaId}" }) { res ->
                                val proxyCover = state.discoverProxyCoverByKey[BookshelfViewModel.mangaSearchCoverKey(res.MangaId)]
                                val fallbackCover = res.ThumbnailUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                                DiscoverRowCard(
                                    title = res.Title,
                                    subtitle = listOfNotNull(res.Author, res.Status).joinToString(" | "),
                                    primaryCoverUrl = proxyCover ?: fallbackCover,
                                    fallbackCoverUrl = fallbackCover,
                                    actionLabel = if (res.InLibrary) "Open" else "Read",
                                    actionEnabled = true,
                                    onAction = { onRequestMangaSearch(res.MangaId, res.Title) },
                                )
                            }
                        }
                    } else if (shownDiscover.isEmpty()) {
                        item {
                            EmptyShelfPlaceholder(
                                if (activeQuery.isNotEmpty()) {
                                    "No manga found for \"$activeQuery\"."
                                } else {
                                    "No manga matched the current filters."
                                },
                            )
                        }
                    } else {
                        items(shownDiscover, key = { it.Id }) { item ->
                            val titleKey = BooksRepository.mangaSeriesKey(item.Title)
                            val altKey = item.AltTitle?.takeIf { it.isNotBlank() }?.let { BooksRepository.mangaSeriesKey(it) }
                            val inLibrary = titleKey in libraryMangaKeys || (altKey != null && altKey in libraryMangaKeys)
                            val proxyCover = state.discoverProxyCoverByKey[BookshelfViewModel.mangaDiscoverCoverKey(item.Id)]
                            val fallbackCover = item.CoverUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            DiscoverRowCard(
                                title = item.Title,
                                subtitle = listOfNotNull(item.Author, item.Year?.toString(), item.Status).joinToString(" | "),
                                primaryCoverUrl = proxyCover ?: fallbackCover,
                                fallbackCoverUrl = fallbackCover,
                                actionLabel = if (inLibrary) "Open" else "Read",
                                actionEnabled = true,
                                onAction = { onRequestMangaDiscover(item) },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (state.bookSearchResults.isNotEmpty()) {
                        items(state.bookSearchResults, key = { it.WorkId + it.Title }) { res ->
                            val already = if (bookType == "audiobook") res.AudiobookDownloaded else res.EbookDownloaded
                            val proxyCover = state.discoverProxyCoverByKey[BookshelfViewModel.bookSearchCoverKey(res.WorkId, res.Title)]
                            val fallbackCover = res.CoverUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            DiscoverRowCard(
                                title = res.Title,
                                subtitle = listOfNotNull(res.Author, res.Year?.toString()).joinToString(" | "),
                                primaryCoverUrl = proxyCover ?: fallbackCover,
                                fallbackCoverUrl = fallbackCover,
                                actionLabel = if (already) "Available" else "Request",
                                actionEnabled = !already,
                                onAction = { onRequestBookSearch(res, bookType) },
                            )
                        }
                    } else if (state.bookDiscoverResults.isEmpty()) {
                        item {
                            EmptyShelfPlaceholder("No titles found for this subject.")
                        }
                    } else {
                        items(state.bookDiscoverResults, key = { it.WorkId + it.Title }) { res ->
                            val proxyCover = state.discoverProxyCoverByKey[BookshelfViewModel.bookDiscoverCoverKey(res.WorkId, res.Title)]
                            val fallbackCover = res.CoverUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            DiscoverRowCard(
                                title = res.Title,
                                subtitle = listOfNotNull(res.Author, res.Year?.toString()).joinToString(" | "),
                                primaryCoverUrl = proxyCover ?: fallbackCover,
                                fallbackCoverUrl = fallbackCover,
                                actionLabel = "Request",
                                actionEnabled = true,
                                onAction = { onRequestBookDiscover(res, bookType) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGenreDialog) {
        MangaGenrePickerDialog(
            selectedGenres = state.mangaDiscoverGenres,
            onDismiss = { showGenreDialog = false },
            onApply = { next ->
                onSetMangaGenres(next)
                showGenreDialog = false
            },
        )
    }
}

@Composable
private fun MangaGenrePickerDialog(
    selectedGenres: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var draft by remember(selectedGenres) { mutableStateOf(selectedGenres) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JellioBgElevated,
        titleContentColor = JellioText,
        textContentColor = JellioText,
        title = { Text("Filter by Genre", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(ANILIST_MANGA_GENRES) { genre ->
                    val checked = genre in draft
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = if (checked) draft - genre else draft + genre
                            },
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                draft = if (isChecked) draft + genre else draft - genre
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(genre, color = JellioText, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(draft) },
                colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (draft.isNotEmpty()) {
                    TextButton(onClick = { draft = emptySet() }) {
                        Text("Clear", color = JellioTextSecondary)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = JellioTextSecondary)
                }
            }
        },
    )
}

@Composable
private fun DiscoverSkeletonList(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "discoverSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.16f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(850),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "discoverSkeletonAlpha",
    )
    val fillColor = Color.White.copy(alpha = alpha)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        userScrollEnabled = false,
    ) {
        items(7) {
            Surface(
                color = JellioBgElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, JellioBorder),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(70.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(fillColor),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(fillColor),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.48f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(fillColor),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(fillColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverRowCard(
    title: String,
    subtitle: String,
    primaryCoverUrl: String?,
    fallbackCoverUrl: String? = null,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    var coverStage by remember(primaryCoverUrl, fallbackCoverUrl) { mutableStateOf(0) }
    val activeUrl = when {
        coverStage == 0 && !primaryCoverUrl.isNullOrBlank() -> primaryCoverUrl
        coverStage <= 1 && !fallbackCoverUrl.isNullOrBlank() && fallbackCoverUrl != primaryCoverUrl -> fallbackCoverUrl
        else -> null
    }

    Surface(
        color = JellioBgElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, JellioBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(JellioBg),
            ) {
                if (activeUrl != null) {
                    val request = remember(activeUrl) {
                        ImageRequest.Builder(context)
                            .data(activeUrl)
                            .size(192, 280)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        onError = {
                            coverStage = if (coverStage == 0 && !fallbackCoverUrl.isNullOrBlank() && fallbackCoverUrl != primaryCoverUrl) {
                                1
                            } else {
                                2
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = JellioTextTertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = JellioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = JellioTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JellioSecondary,
                    contentColor = JellioBg,
                    disabledContainerColor = JellioBorder,
                    disabledContentColor = JellioTextSecondary,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// --- Pending Book Requests Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingRequestsSheet(
    status: com.jellio.android.data.books.ChaptarrStatus,
    requests: List<com.jellio.android.data.books.BookRequestEntryDto>,
    onDismiss: () -> Unit,
    onRetrySearch: (Int) -> Unit,
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
            Text("Pending Book Requests", color = JellioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (status == com.jellio.android.data.books.ChaptarrStatus.LOADING) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JellioSecondary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (status == com.jellio.android.data.books.ChaptarrStatus.NOT_CONFIGURED) {
                Text("Chaptarr is not configured on the server.", color = JellioTextSecondary, fontSize = 13.sp)
            } else if (status == com.jellio.android.data.books.ChaptarrStatus.UNREACHABLE) {
                Text(Strings.get("reader_chaptarr_unreachable"), color = JellioTextSecondary, fontSize = 13.sp)
            } else if (requests.isEmpty()) {
                Text("No active requests.", color = JellioTextSecondary, fontSize = 13.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(requests, key = { "${it.ChaptarrBookId}_${it.Title}" }) { req ->
                        Surface(
                            color = JellioBgElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, JellioBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(req.Title, color = JellioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOfNotNull(req.Author, req.State, req.TimeLeft).joinToString(" | "),
                                        color = JellioTextSecondary,
                                        fontSize = 12.sp,
                                    )
                                    req.Message?.let { Text(it, color = JellioTextTertiary, fontSize = 11.sp) }
                                }
                                IconButton(onClick = { onRetrySearch(req.ChaptarrBookId) }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Search again", tint = JellioSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Manga Updates Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaUpdatesSheet(
    updates: List<com.jellio.android.data.books.MangaUpdateItemDto>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onOpenChapter: (com.jellio.android.data.books.MangaUpdateItemDto) -> Unit,
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
                .heightIn(min = 240.dp, max = 580.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Manga Updates", color = JellioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    border = BorderStroke(1.dp, JellioBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioSecondary),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = JellioSecondary)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Check Now", fontSize = 12.sp)
                }
            }
            if (updates.isEmpty()) {
                Text("No recent chapter updates.", color = JellioTextSecondary, fontSize = 13.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(updates, key = { "${it.MangaId}_${it.Chapter?.Id ?: it.SeenAt}" }) { upd ->
                        Surface(
                            onClick = { onOpenChapter(upd) },
                            color = JellioBgElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, JellioBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(upd.SeriesTitle, color = JellioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        upd.Chapter?.Name ?: "New chapter",
                                        color = if (upd.Read) JellioTextTertiary else JellioSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(if (upd.Read) "Read" else "Unread", color = JellioTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun jellioTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = JellioText,
    unfocusedTextColor = JellioText,
    focusedBorderColor = JellioSecondary,
    unfocusedBorderColor = JellioBorder,
    cursorColor = JellioSecondary,
    focusedContainerColor = JellioBgElevated,
    unfocusedContainerColor = JellioBgElevated,
)

@Composable
internal fun jellioChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = JellioBgElevated,
    labelColor = JellioTextSecondary,
    selectedContainerColor = JellioSecondary,
    selectedLabelColor = JellioBg,
)
