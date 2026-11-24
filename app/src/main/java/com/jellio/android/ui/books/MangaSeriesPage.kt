package com.jellio.android.ui.books

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jellio.android.data.books.ChapterFacts
import com.jellio.android.data.books.MangaDownloadChoice
import com.jellio.android.data.books.RESUME_FAB_SETTLE_MS
import com.jellio.android.data.books.ResumeKind
import com.jellio.android.data.books.SeriesPrefsDto
import com.jellio.android.data.books.TriState
import com.jellio.android.data.books.chapterLabel
import com.jellio.android.data.books.chapterSortKey
import com.jellio.android.data.books.legacyFilter
import com.jellio.android.data.books.nextTriState
import com.jellio.android.data.books.resumeTarget
import com.jellio.android.data.books.seriesHeaderInfo
import com.jellio.android.data.books.topBarFraction
import com.jellio.android.data.books.triStateOf
import com.jellio.android.data.books.triStateValue
import com.jellio.android.data.i18n.Strings
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioDanger
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.JellioTextTertiary
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

// The manga series page: one vertical list, a sticky top bar and a Resume button.
@Composable
internal fun MangaSeriesPage(
    detail: MangaSeriesDetailState,
    onDismiss: () -> Unit,
    onToggleInLibrary: () -> Unit,
    onOpenChapter: (MangaChapterRowItem) -> Unit,
    onToggleBookmark: (MangaChapterRowItem) -> Unit,
    onDownloadChapter: (MangaChapterRowItem) -> Unit,
    onDownload: (MangaDownloadChoice) -> Unit,
    onChange: (SeriesPrefsChange) -> Unit,
    onCategories: () -> Unit,
    reading: ReadingMenuActions? = null,
) {
    val series = detail.seriesItem ?: return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        MangaSeriesContent(
            detail = detail,
            series = series,
            onDismiss = onDismiss,
            onToggleInLibrary = onToggleInLibrary,
            onOpenChapter = onOpenChapter,
            onToggleBookmark = onToggleBookmark,
            onDownloadChapter = onDownloadChapter,
            onDownload = onDownload,
            onChange = onChange,
            onCategories = onCategories,
            reading = reading,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaSeriesContent(
    detail: MangaSeriesDetailState,
    series: MangaSeriesDisplayItem,
    onDismiss: () -> Unit,
    onToggleInLibrary: () -> Unit,
    onOpenChapter: (MangaChapterRowItem) -> Unit,
    onToggleBookmark: (MangaChapterRowItem) -> Unit,
    onDownloadChapter: (MangaChapterRowItem) -> Unit,
    onDownload: (MangaDownloadChoice) -> Unit,
    onChange: (SeriesPrefsChange) -> Unit,
    onCategories: () -> Unit,
    reading: ReadingMenuActions?,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val fadeDistancePx = with(density) { 120.dp.roundToPx() }
    val barFraction by remember(listState, fadeDistancePx) {
        derivedStateOf {
            topBarFraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, fadeDistancePx)
        }
    }

    // The Resume label folds away while the list moves and comes back once it has stopped.
    val scrolling = listState.isScrollInProgress
    var fabExtended by remember { mutableStateOf(true) }
    LaunchedEffect(scrolling) {
        if (scrolling) {
            fabExtended = false
        } else {
            delay(RESUME_FAB_SETTLE_MS)
            fabExtended = true
        }
    }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showDownloadMenu by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showScanlatorDialog by remember { mutableStateOf(false) }

    val resume = remember(detail.allChapters, series.resumeChapterId) {
        val facts = detail.allChapters.map {
            ChapterFacts(it.id, it.number, it.sourceOrder, it.isRead, it.inProgress, it.isDownloaded, it.isDownloading, it.isBookmarked)
        }
        resumeTarget(facts, series.resumeChapterId)
    }
    val resumeRow = resume?.let { target -> detail.allChapters.firstOrNull { it.id == target.key } }
    val resumeLabel = when (resume?.kind) {
        ResumeKind.START -> Strings.get("series_start")
        ResumeKind.READ_AGAIN -> Strings.get("series_read_again")
        else -> Strings.get("player_resume")
    }

    val streamSeries = detail.streamSeries
    val info =
        seriesHeaderInfo(
            detailAuthor = streamSeries?.Author,
            detailArtist = streamSeries?.Artist,
            detailStatus = streamSeries?.Status,
            detailSource = streamSeries?.SourceName?.takeIf { it.isNotBlank() } ?: streamSeries?.Source,
            detailDescription = streamSeries?.Description,
            detailGenres = streamSeries?.Genres,
            shelfAuthor = series.author,
            shelfStatus = series.status,
            shelfGenres = series.streamSummary?.Genres,
            statusLabel = { Strings.get(it) },
        )
    val headerLines = buildList {
        info.author?.let { add(DetailHeaderLine(Icons.Filled.Person, it)) }
        info.artist?.let { add(DetailHeaderLine(Icons.Filled.Brush, it)) }
        info.statusLine?.let { add(DetailHeaderLine(Icons.Filled.Schedule, it)) }
    }
    val actions = buildList {
        add(
            DetailAction(
                icon = if (detail.inLibrary) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = Strings.get(if (detail.inLibrary) "series_in_library" else "series_add_library"),
                active = detail.inLibrary,
                onClick = onToggleInLibrary,
            ),
        )
        if (reading != null) {
            add(
                DetailAction(
                    icon = if (reading.onList) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    label = Strings.get("reading_list"),
                    active = reading.onList,
                    onClick = reading.onToggle,
                ),
            )
            reading.onFriend?.let { onFriend ->
                add(DetailAction(Icons.Filled.PersonAdd, Strings.get("series_friend"), false, onFriend))
            }
        }
        add(DetailAction(Icons.Filled.Category, Strings.get("reader_menu_categories"), false, onCategories))
    }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    val numberLabel = Strings.get("series_chapter_word")

    Box(modifier = Modifier.fillMaxSize().background(JellioBg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "header", contentType = "header") {
                DetailHeader(coverUrl = series.coverUrl, title = series.title, lines = headerLines)
            }
            item(key = "actions", contentType = "actions") {
                DetailActionRow(actions = actions, modifier = Modifier.padding(top = 4.dp))
            }
            val description = info.description
            if (description != null) {
                item(key = "description", contentType = "description") {
                    DetailDescription(text = description)
                }
            }
            val genres = info.genres
            if (genres.isNotEmpty()) {
                item(key = "genres", contentType = "genres") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        items(genres) { genre -> DetailChip(genre) }
                    }
                }
            }
            item(key = "count", contentType = "count") {
                Text(
                    text = if (detail.chapters.size == 1) {
                        Strings.get("series_chapters_one")
                    } else {
                        Strings.format("series_chapters", detail.chapters.size)
                    },
                    color = JellioTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            if (detail.isLoading) {
                item(key = "loading", contentType = "state") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = JellioSecondary, modifier = Modifier.size(28.dp))
                    }
                }
            } else if (detail.error != null) {
                item(key = "error", contentType = "state") {
                    Text(detail.error, color = JellioDanger, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(detail.chapters, key = { it.id }, contentType = { "chapter" }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        display = detail.prefs.ChapterDisplay,
                        numberLabel = numberLabel,
                        dateFormat = dateFormat,
                        onOpen = { onOpenChapter(chapter) },
                        onLongPress = { onToggleBookmark(chapter) },
                        onDownload = { onDownloadChapter(chapter) },
                    )
                }
            }
        }

        DetailTopBar(
            title = series.title,
            fraction = barFraction,
            backLabel = Strings.get("action_back"),
            onBack = onDismiss,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box {
                IconButton(onClick = { showDownloadMenu = true }) {
                    Icon(Icons.Filled.Download, contentDescription = Strings.get("menu_download"), tint = JellioText)
                }
                DownloadMenu(
                    expanded = showDownloadMenu,
                    onDismiss = { showDownloadMenu = false },
                    onPick = { choice ->
                        showDownloadMenu = false
                        onDownload(choice)
                    },
                )
            }
            IconButton(onClick = { showFilterSheet = true }) {
                Icon(Icons.Filled.FilterList, contentDescription = Strings.get("series_tab_filter"), tint = if (hasActiveFilter(detail.prefs)) JellioSecondary else JellioText)
            }
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = Strings.get("series_more"), tint = JellioText)
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false },
                    modifier = Modifier.background(JellioBgElevated),
                ) {
                    DropdownMenuItem(
                        text = { Text(Strings.get("series_scanlators"), color = JellioText, fontSize = 14.sp) },
                        onClick = {
                            showOverflow = false
                            showScanlatorDialog = true
                        },
                    )
                }
            }
        }

        if (resumeRow != null && !detail.isLoading) {
            DetailResumeButton(
                label = resumeLabel,
                extended = fabExtended,
                onClick = { onOpenChapter(resumeRow) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }

    if (showFilterSheet) {
        ChapterSettingsSheet(
            prefs = detail.prefs,
            onChange = onChange,
            onDismiss = { showFilterSheet = false },
        )
    }
    if (showScanlatorDialog) {
        ScanlatorFilterDialog(
            availableScanlators = detail.availableScanlators,
            excluded = detail.prefs.ExcludedScanlators.orEmpty(),
            duplicatesAsOne = detail.prefs.DuplicatesAsOne ?: false,
            onDismiss = { showScanlatorDialog = false },
            onSave = { nextExcluded, dup ->
                onChange(SeriesPrefsChange(excludedScanlators = nextExcluded, duplicatesAsOne = dup))
                showScanlatorDialog = false
            },
        )
    }
}

private fun hasActiveFilter(prefs: SeriesPrefsDto): Boolean =
    triStateOf(prefs.FilterUnread) != TriState.OFF ||
        triStateOf(prefs.FilterDownloaded) != TriState.OFF ||
        triStateOf(prefs.FilterBookmarked) != TriState.OFF ||
        legacyFilter(prefs.ChapterFilter, prefs.FilterUnread, prefs.FilterBookmarked, prefs.FilterDownloaded, "unread") != TriState.OFF ||
        legacyFilter(prefs.ChapterFilter, prefs.FilterUnread, prefs.FilterBookmarked, prefs.FilterDownloaded, "bookmarked") != TriState.OFF

// A few lines of the description; tap to read the rest.
@Composable
internal fun DetailDescription(text: String, modifier: Modifier = Modifier) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, enabled = overflows || expanded) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
    ) {
        Text(
            text = text,
            color = JellioTextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = Strings.get(if (expanded) "series_collapse" else "series_expand"),
                tint = JellioTextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(22.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: MangaChapterRowItem,
    display: String?,
    numberLabel: String,
    dateFormat: DateFormat,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDownload: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val titleColor = if (chapter.isRead) JellioTextTertiary else JellioText
    val metaColor = if (chapter.isRead) JellioTextTertiary else JellioTextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .combinedClickable(
                role = Role.Button,
                onClick = onOpen,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!chapter.isRead) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(JellioSecondary))
                    Spacer(Modifier.width(8.dp))
                }
                if (chapter.isBookmarked) {
                    Icon(Icons.Filled.Bookmark, contentDescription = Strings.get("series_bookmarked"), tint = JellioSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = chapterLabel(display, chapter.title, chapter.number, numberLabel),
                    color = titleColor,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = buildList {
                if (chapter.uploadDate > 0L) add(dateFormat.format(Date(chapter.uploadDate)))
                chapter.page?.let { add(Strings.format("series_page", it)) }
                if (!chapter.scanlator.isNullOrBlank()) add(chapter.scanlator)
            }.joinToString(" • ")
            if (meta.isNotEmpty() || chapter.isRead) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chapter.isRead) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = metaColor, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(meta, color = metaColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        ChapterDownloadIndicator(chapter = chapter, dimmed = chapter.isRead, onClick = onDownload)
    }
}

// Download, in progress or downloaded, in a slot of its own so rows keep their height.
@Composable
private fun ChapterDownloadIndicator(chapter: MangaChapterRowItem, dimmed: Boolean, onClick: () -> Unit) {
    val tint = JellioTextSecondary.copy(alpha = if (dimmed) 0.5f else 1f)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = !chapter.isDownloaded && !chapter.isDownloading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            chapter.isDownloaded ->
                Icon(Icons.Filled.CheckCircle, contentDescription = Strings.get("series_downloaded"), tint = tint, modifier = Modifier.size(22.dp))
            chapter.isDownloading ->
                if (chapter.downloadProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { chapter.downloadProgress.coerceIn(0f, 1f) },
                        color = tint,
                        trackColor = JellioBorder,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            else ->
                Icon(Icons.Outlined.ArrowCircleDown, contentDescription = Strings.get("menu_download"), tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DownloadMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (MangaDownloadChoice) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(JellioBgElevated),
    ) {
        MangaDownloadChoice.entries.forEach { choice ->
            DropdownMenuItem(
                text = { Text(Strings.get(downloadChoiceLabelKey(choice)), color = JellioText, fontSize = 14.sp) },
                onClick = { onPick(choice) },
            )
        }
    }
}

private fun downloadChoiceLabelKey(choice: MangaDownloadChoice): String = when (choice) {
    MangaDownloadChoice.NEXT_1 -> "series_download_next_1"
    MangaDownloadChoice.NEXT_5 -> "series_download_next_5"
    MangaDownloadChoice.NEXT_10 -> "series_download_next_10"
    MangaDownloadChoice.NEXT_25 -> "series_download_next_25"
    MangaDownloadChoice.UNREAD -> "series_unread"
    MangaDownloadChoice.BOOKMARKED -> "series_bookmarked"
    MangaDownloadChoice.ALL -> "filter_all"
}

// Filter, Sort and Display in one sheet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSettingsSheet(
    prefs: SeriesPrefsDto,
    onChange: (SeriesPrefsChange) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JellioBgElevated,
        contentColor = JellioText,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            val tabs = listOf(
                Strings.get("series_tab_filter"),
                Strings.get("library_sort"),
                Strings.get("settings_cat_display"),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, label ->
                    val selected = tab == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.Tab) { tab = index }
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label,
                            color = if (selected) JellioText else JellioTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) JellioSecondary else JellioBorder),
                        )
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp).padding(vertical = 8.dp)) {
                when (tab) {
                    0 -> {
                        val unread = effectiveFilter(prefs, "unread")
                        val downloaded = triStateOf(prefs.FilterDownloaded)
                        val bookmarked = effectiveFilter(prefs, "bookmarked")
                        FilterRow(Strings.get("series_unread"), unread) {
                            onChange(SeriesPrefsChange(filterUnread = triStateValue(nextTriState(unread)), filterBookmarked = pinLegacy(prefs, "bookmarked", bookmarked)))
                        }
                        FilterRow(Strings.get("series_downloaded"), downloaded) {
                            onChange(
                                SeriesPrefsChange(
                                    filterDownloaded = triStateValue(nextTriState(downloaded)),
                                    filterUnread = pinLegacy(prefs, "unread", unread),
                                    filterBookmarked = pinLegacy(prefs, "bookmarked", bookmarked),
                                ),
                            )
                        }
                        FilterRow(Strings.get("series_bookmarked"), bookmarked) {
                            onChange(SeriesPrefsChange(filterBookmarked = triStateValue(nextTriState(bookmarked)), filterUnread = pinLegacy(prefs, "unread", unread)))
                        }
                    }
                    1 -> {
                        val sort = chapterSortKey(prefs.ChapterSort)
                        val descending = prefs.ChapterDescending ?: true
                        listOf(
                            "source" to "series_sort_source",
                            "number" to "series_chapter_number",
                            "date" to "series_sort_date",
                        ).forEach { (key, label) ->
                            SortRow(
                                label = Strings.get(label),
                                selected = sort == key,
                                descending = descending,
                                onClick = {
                                    if (sort == key) {
                                        onChange(SeriesPrefsChange(chapterDescending = !descending))
                                    } else {
                                        onChange(SeriesPrefsChange(chapterSort = key))
                                    }
                                },
                            )
                        }
                    }
                    else -> {
                        val display = if (prefs.ChapterDisplay == "number") "number" else "title"
                        RadioRow(Strings.get("series_display_title"), display == "title") {
                            onChange(SeriesPrefsChange(chapterDisplay = "title"))
                        }
                        RadioRow(Strings.get("series_chapter_number"), display == "number") {
                            onChange(SeriesPrefsChange(chapterDisplay = "number"))
                        }
                    }
                }
            }
        }
    }
}

// What a filter shows, including the single filter older settings carry.
private fun effectiveFilter(prefs: SeriesPrefsDto, which: String): TriState {
    val own = triStateOf(if (which == "unread") prefs.FilterUnread else prefs.FilterBookmarked)
    if (own != TriState.OFF) return own
    return legacyFilter(prefs.ChapterFilter, prefs.FilterUnread, prefs.FilterBookmarked, prefs.FilterDownloaded, which)
}

// An older single filter is written out as the new ones when another filter is changed.
private fun pinLegacy(prefs: SeriesPrefsDto, which: String, shown: TriState): String? {
    val hasOwn = if (which == "unread") !prefs.FilterUnread.isNullOrEmpty() else !prefs.FilterBookmarked.isNullOrEmpty()
    return if (!hasOwn && shown != TriState.OFF) triStateValue(shown) else null
}

@Composable
private fun FilterRow(label: String, state: TriState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = when (state) {
                TriState.OFF -> ToggleableState.Off
                TriState.INCLUDE -> ToggleableState.On
                TriState.EXCLUDE -> ToggleableState.Indeterminate
            },
            onClick = onClick,
            colors = CheckboxDefaults.colors(
                checkedColor = JellioSecondary,
                uncheckedColor = JellioTextSecondary,
                checkmarkColor = JellioBg,
            ),
        )
        Text(label, color = JellioText, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SortRow(label: String, selected: Boolean, descending: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    if (descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = JellioSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            label,
            color = if (selected) JellioText else JellioTextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = JellioSecondary, unselectedColor = JellioTextSecondary),
        )
        Text(label, color = JellioText, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
    }
}
