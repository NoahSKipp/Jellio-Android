package com.jellio.android.ui.detail

import android.os.Build
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import com.jellio.android.ui.common.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.jellio.android.ui.common.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.model.PersonDto
import com.jellio.android.data.model.TrailerDto
import com.jellio.android.data.session.Session
import com.jellio.android.ui.PlayAction
import com.jellio.android.ui.common.MediaTechBadgesRow
import com.jellio.android.ui.common.NoOpBringIntoViewSpec
import com.jellio.android.ui.home.CardDialog
import com.jellio.android.ui.home.CardOptionsMenuRow
import com.jellio.android.ui.home.sizedImageRequest
import com.jellio.android.ui.nav.LocalBottomBarPadding
import com.jellio.android.data.books.BooksRepository
import com.jellio.android.data.books.readingTargetForItem
import com.jellio.android.data.books.readingToggleLabelKey
import com.jellio.android.ui.books.ReadingListViewModel
import com.jellio.android.ui.friends.FriendReadingPickerDialog
import com.jellio.android.ui.friends.FriendWatchlistPickerDialog
import com.jellio.android.ui.friends.FriendsViewModel
import com.jellio.android.ui.friends.canShareType
import com.jellio.android.data.episodeReleaseLabel
import com.jellio.android.data.seasonEpisodeReleaseLabels
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioRarityLegendary
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.scaled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat

private val HeroHeight = 480.dp

private fun formatRuntime(ticks: Long?): String {
    if (ticks == null || ticks <= 0) return ""
    val totalMinutes = (ticks / 600000000L).toInt()
    return com.jellio.android.data.i18n.formatDurationMinutes(totalMinutes)
}

private fun formatPremiereDate(raw: String?): String? {
    val ms = com.jellio.android.data.JellyfinDates.parseEpochMs(raw) ?: return null
    return DateFormat.getDateInstance(DateFormat.SHORT, com.jellio.android.data.i18n.displayLocale()).format(java.util.Date(ms))
}

@Composable
@androidx.compose.foundation.ExperimentalFoundationApi
fun DetailScreen(
    session: Session,
    itemId: String,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToDetailReplace: (String) -> Unit = onNavigateToDetail,
    onNavigateToPerson: (String) -> Unit,
    onOpenStreamPicker: (BaseItemDto) -> Unit,
    onPlayDirect: (itemId: String, mediaSourceId: String?) -> Unit,
    onOpenBookReader: ((BaseItemDto) -> Unit)? = null,
    onOpenAudiobook: ((BaseItemDto) -> Unit)? = null,
    onDownloadItem: ((BaseItemDto) -> Unit)? = null,
    isItemDownloaded: ((String) -> Boolean)? = null,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel(),
    readingListViewModel: ReadingListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val friendsState by friendsViewModel.state.collectAsState()
    val readingSupported by readingListViewModel.supported.collectAsState()
    val readingKeys by readingListViewModel.keys.collectAsState()
    // Books and audiobooks use the reading list where other titles use the watchlist.
    val isReadingItem = uiState.item?.Type.let { it == "Book" || it == "AudioBook" }
    val readingTarget = remember(uiState.item?.Id, uiState.item?.Type) {
        uiState.item?.let { readingTargetForItem(it.Id, it.Type, it.Name, it.AlbumArtist, BooksRepository.isComicOrManga(it)) }
    }
    LaunchedEffect(uiState.item?.Id, isReadingItem) { if (isReadingItem) readingListViewModel.refreshKeys() }
    val onReadingList = readingTarget != null && readingTarget.key in readingKeys
    val cardMenuExtras = com.jellio.android.ui.home.rememberCardMenuExtras(session)
    var friendPickerOpen by remember { mutableStateOf(false) }
    val availableEditions by viewModel.availableEditions.collectAsState()
    val selectedEdition by viewModel.selectedEdition.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var episodeMenuTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    val isCompact = com.jellio.android.ui.common.isCompactScreen()
    val isCompactPortrait = com.jellio.android.ui.common.rememberWindowLayoutMode() == com.jellio.android.ui.common.WindowLayoutMode.COMPACT_PORTRAIT
    val bottomBarPadding = LocalBottomBarPadding.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val playButtonFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(itemId) { viewModel.load(session, itemId) }
    androidx.compose.runtime.DisposableEffect(itemId) { onDispose { viewModel.onScreenHidden() } }
    LaunchedEffect(uiState.item?.Id, isCompact) {
        if (uiState.item != null) {
            listState.scrollToItem(0)
            if (!isCompact) {
                for (attempt in 0 until 30) {
                    withFrameNanos {}
                    if (runCatching { playButtonFocusRequester.requestFocus() }.isSuccess && attempt >= 1) {
                        break
                    }
                }
            }
        }
    }
    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> {
                val loadingFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(isCompact) {
                    if (!isCompact) {
                        runCatching { loadingFocus.requestFocus() }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(loadingFocus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            event.key == androidx.compose.ui.input.key.Key.DirectionLeft ||
                                event.key == androidx.compose.ui.input.key.Key.DirectionRight ||
                                event.key == androidx.compose.ui.input.key.Key.DirectionUp ||
                                event.key == androidx.compose.ui.input.key.Key.DirectionDown
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    DetailLoading(uiState.loadingArt)
                }
            }
            uiState.error != null -> com.jellio.android.ui.common.ErrorState(
                message = uiState.error ?: com.jellio.android.ui.i18n.string("detail_load_error"),
                onRetry = { viewModel.retry(session, itemId) },
                modifier = Modifier.fillMaxSize(),
            )
            uiState.item != null -> {
                val item = uiState.item!!
                val episodeReleaseLabels = remember(uiState.selectedSeasonEpisodes, uiState.streamVerifiedTick) {
                    seasonEpisodeReleaseLabels(uiState.selectedSeasonEpisodes)
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 48.dp + if (isCompact) bottomBarPadding else 0.dp),
                    modifier = Modifier.fillMaxSize().then(if (!isCompact) Modifier.focusRestorer() else Modifier),
                ) {
                    item(key = "hero", contentType = "hero") {
                        Box {
                            val heroBringIntoViewSpec = if (isCompact) LocalBringIntoViewSpec.current else NoOpBringIntoViewSpec
                            CompositionLocalProvider(LocalBringIntoViewSpec provides heroBringIntoViewSpec) {
                                DetailHero(
                                    item = item,
                                    state = uiState,
                                    availableEditions = availableEditions,
                                    selectedEdition = selectedEdition,
                                    onSelectEdition = { viewModel.selectEdition(it) },
                                    onRetryDetails = { viewModel.retry(session, itemId) },
                                    onPlayFocusChanged = { viewModel.onPlayFocusChanged(session, it) },
                                    imageUrl = imageUrl,
                                    onSeriesClick = onNavigateToDetail,
                                    playButtonFocusRequester = playButtonFocusRequester,
                                    onPlay = {
                                        if (item.Type == "Book" && onOpenBookReader != null) {
                                            onOpenBookReader(item)
                                            return@DetailHero
                                        }
                                        if ((item.Type == "AudioBook" || item.Type == "MusicAlbum") && onOpenAudiobook != null) {
                                            onOpenAudiobook(item)
                                            return@DetailHero
                                        }
                                        com.jellio.android.ui.player.PlayStartTrace.begin("title page")
                                        scope.launch {
                                            viewModel.setResolvingPlay(true)
                                            try {
                                                val target = viewModel.resolvePlayTarget()
                                                if (target != null) {
                                                    val action = viewModel.resolvePlayAction(session, target)
                                                    com.jellio.android.ui.player.PlayStartTrace.mark(target.Id, "play target", if (action is PlayAction.Direct) "remembered stream" else "picker")
                                                    when (action) {
                                                        is PlayAction.Direct -> onPlayDirect(action.itemId, action.mediaSourceId)
                                                        is PlayAction.ShowPicker -> onOpenStreamPicker(action.item)
                                                    }
                                                }
                                            } finally {
                                                viewModel.setResolvingPlay(false)
                                            }
                                        }
                                    },
                                    onChangeStream = if (item.Type != "Series" && item.Type != "Book" && item.Type != "AudioBook") {
                                        {
                                            scope.launch {
                                                val target = viewModel.resolvePlayTarget()
                                                if (target != null) {
                                                    when (val action = viewModel.resolvePlayAction(session, target, forceChoice = true)) {
                                                        is PlayAction.Direct -> onPlayDirect(action.itemId, action.mediaSourceId)
                                                        is PlayAction.ShowPicker -> onOpenStreamPicker(action.item)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    onDownload = if (onDownloadItem != null && item.Type != "Series" && item.Type != "Season") {
                                        { onDownloadItem(item) }
                                    } else {
                                        null
                                    },
                                    isDownloaded = isItemDownloaded?.invoke(item.Id) == true,
                                    onToggleWatchlist = {
                                        if (!isReadingItem) {
                                            viewModel.toggleWatchlist(session)
                                        } else if (readingTarget != null) {
                                            readingListViewModel.toggle(readingTarget)
                                        }
                                    },
                                    onAddToFriend = if (
                                        friendsState.available && friendsState.friends.isNotEmpty() &&
                                        (if (isReadingItem) readingTarget != null && readingSupported == true else canShareType(item.Type))
                                    ) {
                                        { friendPickerOpen = true }
                                    } else {
                                        null
                                    },
                                    showWatchlist = !isReadingItem || (readingTarget != null && readingSupported == true),
                                    watchlistActive = if (isReadingItem) onReadingList else uiState.isWatchlisted,
                                    watchlistLabel = if (isReadingItem) com.jellio.android.ui.i18n.string("reading_list") else null,
                                    watchlistDescription = if (isReadingItem) com.jellio.android.ui.i18n.string(readingToggleLabelKey(onReadingList)) else null,
                                    friendLabel = if (isReadingItem) com.jellio.android.ui.i18n.string("reading_menu_add_to_friend") else null,
                                    onToggleWatched = { viewModel.toggleWatched(session) },
                                    onLike = { viewModel.setLike(session, true) },
                                    onDislike = { viewModel.setLike(session, false) },
                                )
                            }
                        }
                    }
                    val showSeasons = (item.Type == "Series" || (item.Type == "Episode" && item.SeriesId != null)) && (uiState.seasons.isNotEmpty() || uiState.selectedSeasonEpisodes.isNotEmpty())
                    if (showSeasons) {
                        val onEpisodeClickAction = if (item.Type == "Episode") onNavigateToDetailReplace else onNavigateToDetail
                        val currentEpisodeId = if (item.Type == "Episode") item.Id else null
                        if (isCompactPortrait) {
                            item(key = "seasons_header", contentType = "seasons_header") {
                                SeasonsHeader(
                                    state = uiState,
                                    horizontalPad = 16.dp,
                                    onSelectSeason = { seasonId -> viewModel.selectSeason(session, item.SeriesId ?: item.Id, seasonId, byUser = true) },
                                )
                            }
                            items(
                                items = uiState.selectedSeasonEpisodes,
                                key = { it.Id },
                                contentType = { "episode_row" },
                            ) { episode ->
                                CompactEpisodeRow(
                                    episode = episode,
                                    imageUrl = imageUrl,
                                    releaseLabel = episodeReleaseLabels[episode.Id],
                                    isCurrent = currentEpisodeId != null && episode.Id == currentEpisodeId,
                                    onClick = { onEpisodeClickAction(episode.Id) },
                                    onOptionsClick = { episodeMenuTarget = episode },
                                )
                            }
                        } else {
                            item(key = "seasons", contentType = "seasons") {
                                SeasonsSection(
                                    state = uiState,
                                    imageUrl = imageUrl,
                                    currentEpisodeId = currentEpisodeId,
                                    onSelectSeason = { seasonId -> viewModel.selectSeason(session, item.SeriesId ?: item.Id, seasonId, byUser = true) },
                                    onEpisodeClick = onEpisodeClickAction,
                                    onEpisodeOptions = { episode -> episodeMenuTarget = episode },
                                    onEpisodeFocused = { episodeId -> viewModel.preloadEpisode(episodeId) },
                                )
                            }
                        }
                    }
                    if (uiState.cast.isNotEmpty()) {
                        item(key = "cast", contentType = "cast") {
                            CastRow(cast = uiState.cast, imageUrl = imageUrl, onPersonClick = onNavigateToPerson)
                        }
                    }
                    if (uiState.trailers.isNotEmpty()) {
                        item(key = "trailers", contentType = "trailers") {
                            TrailersRow(trailers = uiState.trailers, onOpen = { url ->
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                                    )
                                }
                            })
                        }
                    }
                }
            }
        }

        if (isCompact) {
            Surface(
                onClick = { backDispatcher?.onBackPressed() },
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.52f),
                    contentColor = Color.White,
                    focusedContainerColor = Color.Black.copy(alpha = 0.72f),
                    focusedContentColor = Color.White,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        shape = CircleShape,
                    ),
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, Color.White),
                        shape = CircleShape,
                    ),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 12.dp)
                    .size(40.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        val pickerItem = uiState.item
        if (friendPickerOpen && pickerItem != null && isReadingItem) {
            if (readingTarget != null) {
                FriendReadingPickerDialog(
                    target = readingTarget,
                    viewModel = friendsViewModel,
                    onDismiss = {
                        friendPickerOpen = false
                        if (!isCompact) {
                            runCatching { playButtonFocusRequester.requestFocus() }
                        }
                    },
                )
            }
        } else if (friendPickerOpen && pickerItem != null) {
            FriendWatchlistPickerDialog(
                item = pickerItem,
                viewModel = friendsViewModel,
                onDismiss = {
                    friendPickerOpen = false
                    if (!isCompact) {
                        runCatching { playButtonFocusRequester.requestFocus() }
                    }
                },
            )
        }

        val menuTarget = episodeMenuTarget
        if (menuTarget != null) {
            val index = uiState.selectedSeasonEpisodes.indexOfFirst { it.Id == menuTarget.Id }
            EpisodeOptionsMenu(
                episode = menuTarget,
                hasPrevious = index > 0,
                onToggleWatched = { viewModel.toggleEpisodeWatched(session, menuTarget) },
                onMarkPreviousWatched = { viewModel.markPreviousWatched(session, menuTarget) },
                onMarkSeasonWatched = { viewModel.markSeasonWatched(session) },
                onMarkSeasonUnwatched = { viewModel.markSeasonUnwatched(session) },
                extras = cardMenuExtras.forEpisode(menuTarget, uiState.selectedSeason),
                onDismiss = { episodeMenuTarget = null },
            )
        }
    }
}

@Composable
private fun EpisodeOptionsMenu(
    episode: BaseItemDto,
    hasPrevious: Boolean,
    onToggleWatched: () -> Unit,
    onMarkPreviousWatched: () -> Unit,
    onMarkSeasonWatched: () -> Unit,
    onMarkSeasonUnwatched: () -> Unit,
    extras: com.jellio.android.ui.home.EpisodeMenuExtras,
    onDismiss: () -> Unit,
) {
    val isPlayed = episode.UserData?.Played == true
    val episodeTag = com.jellio.android.data.i18n.seasonEpisodeTag(episode.ParentIndexNumber, episode.IndexNumber)
    val episodeTitle = listOf(episodeTag, episode.Name.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(" - ")
    CardDialog(onDismiss = onDismiss) { firstFocus ->
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 460.dp)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            if (episodeTitle.isNotBlank()) {
                Text(
                    text = episodeTitle,
                    color = JellioText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
            CardOptionsMenuRow(
                label = if (isPlayed) com.jellio.android.ui.i18n.string("detail_mark_unwatched") else com.jellio.android.ui.i18n.string("detail_mark_watched"),
                onClick = {
                    onToggleWatched()
                    onDismiss()
                },
                modifier = Modifier.focusRequester(firstFocus),
            )
            if (hasPrevious) {
                CardOptionsMenuRow(
                    label = com.jellio.android.ui.i18n.string("detail_mark_previous_watched"),
                    onClick = {
                        onMarkPreviousWatched()
                        onDismiss()
                    },
                )
            }
            CardOptionsMenuRow(
                label = com.jellio.android.ui.i18n.string("detail_mark_season_watched"),
                onClick = {
                    onMarkSeasonWatched()
                    onDismiss()
                },
            )
            CardOptionsMenuRow(
                label = com.jellio.android.ui.i18n.string("detail_mark_season_unwatched"),
                onClick = {
                    onMarkSeasonUnwatched()
                    onDismiss()
                },
            )
            val rows = listOfNotNull(
                extras.onDownloadEpisode?.let { "menu_download_episode" to it },
                extras.onDownloadSeason?.let { "menu_download_season" to it },
                extras.onQuickSkipEpisode?.let { "menu_quick_skip_episode" to it },
                extras.onDeepSkipEpisode?.let { "menu_deep_skip_episode" to it },
                extras.onQuickSkipSeason?.let { "menu_quick_skip_season" to it },
                extras.onDeepSkipSeason?.let { "menu_deep_skip_season" to it },
            )
            rows.forEach { (key, action) ->
                CardOptionsMenuRow(
                    label = com.jellio.android.ui.i18n.string(key),
                    onClick = {
                        action()
                        onDismiss()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHero(
    item: BaseItemDto,
    state: DetailUiState,
    imageUrl: (String, String?, String, Int) -> String,
    onSeriesClick: (String) -> Unit,
    playButtonFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onChangeStream: (() -> Unit)?,
    onDownload: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    onToggleWatchlist: () -> Unit,
    onAddToFriend: (() -> Unit)? = null,
    onToggleWatched: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onRetryDetails: (() -> Unit)? = null,
    onPlayFocusChanged: (Boolean) -> Unit = {},
    availableEditions: List<String> = emptyList(),
    selectedEdition: String? = null,
    onSelectEdition: (String) -> Unit = {},
    showWatchlist: Boolean = true,
    watchlistActive: Boolean = state.isWatchlisted,
    watchlistLabel: String? = null,
    watchlistDescription: String? = null,
    friendLabel: String? = null,
) {
    val season = state.selectedSeason?.takeIf { item.Type == "Series" }
    // Backdrops, Thumb, then the poster blurred; a failed load moves on to the
    // next one, and when none load the gradients below still frame the title.
    val candidates = remember(item.Id, season?.Id, item.BackdropImageTags, item.ImageTags) {
        com.jellio.android.ui.common.titleArtCandidates(item, { id, tag, type, w -> imageUrl(id, tag, type, w) }, season = season)
    }
    val logos = remember(item.Id, item.ImageTags) {
        if (item.Type == "Episode") emptyList() else com.jellio.android.ui.common.titleLogoCandidates(item, { id, tag, type, w -> imageUrl(id, tag, type, w) })
    }
    var logoIndex by remember(logos) { mutableIntStateOf(0) }
    var artIndex by remember(candidates) { mutableIntStateOf(0) }
    val art = candidates.getOrNull(artIndex)
    val isCompact = com.jellio.android.ui.common.isCompactScreen()
    val haptic = LocalHapticFeedback.current
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val horizontalPad = if (isCompact) 16.dp else 48.dp
    val minHeroHeight = if (isCompact) 360.dp else maxOf(420.dp, HeroHeight.scaled())
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = minHeroHeight)) {
        if (art != null) {
            AsyncImage(
                model = art.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { artIndex++ },
                modifier = Modifier.matchParentSize().then(if (art.blurred && canBlur) Modifier.blur(28.dp) else Modifier),
            )
            if (art.blurred) {
                Box(modifier = Modifier.matchParentSize().background(JellioBg.copy(alpha = if (canBlur) 0.35f else 0.72f)))
            }
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, JellioBg), startY = 0f, endY = Float.POSITIVE_INFINITY),
            ),
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.horizontalGradient(listOf(JellioBg.copy(alpha = if (isCompact) 0.55f else 0.85f), Color.Transparent), endX = 1100f),
            ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .widthIn(max = 920.dp)
                .statusBarsPadding()
                .padding(
                    start = horizontalPad,
                    top = 64.dp,
                    bottom = if (isCompact) 20.dp else 48.dp,
                    end = horizontalPad,
                ),
        ) {
            if (item.Type == "Episode" && item.SeriesId != null && item.SeriesName != null) {
                Surface(
                    onClick = { onSeriesClick(item.SeriesId) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = JellioTextSecondary,
                        focusedContainerColor = Color.White.copy(alpha = 0.25f),
                        focusedContentColor = Color.White,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(8.dp),
                        ),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tv,
                            contentDescription = null,
                            tint = JellioSecondary,
                            modifier = Modifier.size(16.dp).padding(end = 6.dp),
                        )
                        Text(
                            text = item.SeriesName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            val hasEpisodeCode = item.ParentIndexNumber != null && item.IndexNumber != null
            val titleText = if (item.Type == "Episode" && hasEpisodeCode) {
                "${com.jellio.android.data.i18n.seasonEpisodeTag(item.ParentIndexNumber, item.IndexNumber)} \u00B7 ${com.jellio.android.data.i18n.episodeDisplayName(item.Name, item.IndexNumber)}"
            } else {
                item.Name.orEmpty()
            }
            val logo = logos.getOrNull(logoIndex)
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = titleText,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoIndex++ },
                    modifier = Modifier.widthIn(max = if (isCompact) 300.dp else 520.dp).height(if (isCompact) 88.dp else 120.dp),
                )
            } else {
                Text(text = titleText, style = MaterialTheme.typography.titleLarge, color = JellioText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val epReleaseLabel = if (item.Type == "Episode") episodeReleaseLabel(item) else null
                if (epReleaseLabel != null) {
                    Text(
                        text = epReleaseLabel,
                        color = JellioRarityLegendary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                val year = if (item.Type == "Episode") {
                    formatPremiereDate(item.PremiereDate) ?: item.ProductionYear?.toString()
                } else {
                    item.ProductionYear?.toString()
                }
                if (year != null && (epReleaseLabel == null || year != epReleaseLabel)) {
                    Text(text = year, color = JellioTextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                }
                formatRuntime(item.RunTimeTicks).takeIf { it.isNotEmpty() }?.let {
                    Text(text = it, color = JellioTextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                }
                item.OfficialRating?.let {
                    Text(text = it, color = JellioTextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                }
                // TMDB's score, labelled like the cards.
                item.CommunityRating?.let {
                    com.jellio.android.ui.home.RatingChip(
                        it,
                        fontSize = 15.sp,
                        badgeWidth = 28.dp,
                        badgeHeight = 12.dp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            MediaTechBadgesRow(item = item, modifier = Modifier.padding(top = 10.dp))

            item.Genres?.takeIf { it.isNotEmpty() }?.let {
                Text(text = it.joinToString(", ") { g -> com.jellio.android.data.i18n.genreDisplayName(g) }, color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
            }

            (season?.Overview?.takeIf { it.isNotBlank() } ?: item.Overview)?.let { overviewText ->
                var overviewExpanded by remember(item.Id) { mutableStateOf(false) }
                var overviewTruncated by remember(item.Id, overviewText) { mutableStateOf(false) }
                val canToggleOverview = overviewTruncated || overviewExpanded
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .animateContentSize()
                        .clickable(
                            enabled = canToggleOverview,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { overviewExpanded = !overviewExpanded },
                ) {
                    Text(
                        text = overviewText,
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (overviewExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            if (!overviewExpanded) {
                                overviewTruncated = result.hasVisualOverflow
                            }
                        },
                    )
                    if (canToggleOverview) {
                        Text(
                            text = if (overviewExpanded) "Show less" else "Read more",
                            color = JellioSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            if (availableEditions.size > 1) {
                FlowRow(
                    modifier = Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableEditions.forEach { edition ->
                        val isSelected = edition == selectedEdition
                        Surface(
                            onClick = { onSelectEdition(edition) },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) JellioSecondary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                                contentColor = if (isSelected) Color.White else JellioTextSecondary,
                                focusedContainerColor = JellioSecondary,
                                focusedContentColor = Color.White,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(
                                    border = BorderStroke(1.dp, if (isSelected) JellioSecondary.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(999.dp),
                                ),
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = RoundedCornerShape(999.dp),
                                ),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                        ) {
                            Text(
                                text = edition,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            var actionsExpanded by remember(item.Id) { mutableStateOf(false) }
            val primaryButtonLabel = when (item.Type) {
                "Book" -> "Read"
                "AudioBook" -> "Listen"
                else -> state.playLabel
            }
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPlay()
                        },
                        enabled = !state.resolvingPlay,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = JellioText,
                            contentColor = JellioBg,
                            focusedContainerColor = Color.White,
                            focusedContentColor = JellioBg,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.5.dp, Color.White),
                                shape = RoundedCornerShape(999.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .focusRequester(playButtonFocusRequester)
                            .onFocusChanged { onPlayFocusChanged(it.isFocused) },
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                                Text(
                                    text = primaryButtonLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showWatchlist) {
                            CompactActionPill(
                                icon = if (watchlistActive) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkAdd,
                                label = watchlistLabel ?: com.jellio.android.ui.i18n.string("nav_watchlist"),
                                active = watchlistActive,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleWatchlist()
                                },
                            )
                        }
                        if (onDownload != null) {
                            CompactActionPill(
                                icon = if (isDownloaded) Icons.Filled.Check else Icons.Filled.Download,
                                label = if (isDownloaded) "Saved" else "Download",
                                active = isDownloaded,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDownload()
                                },
                            )
                        }
                        if (onChangeStream != null) {
                            CompactActionPill(
                                icon = Icons.Filled.SwapHoriz,
                                label = "Streams",
                                active = false,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onChangeStream()
                                },
                            )
                        }
                        CompactActionPill(
                            icon = Icons.Filled.Check,
                            label = if (state.isWatched) "Watched" else "Mark",
                            active = state.isWatched,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleWatched()
                            },
                        )
                        CompactActionPill(
                            icon = Icons.Filled.MoreVert,
                            label = "More",
                            active = actionsExpanded,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                actionsExpanded = !actionsExpanded
                            },
                        )
                    }

                    AnimatedVisibility(visible = actionsExpanded) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompactActionPill(
                                icon = Icons.Filled.ThumbUp,
                                label = com.jellio.android.ui.i18n.string("detail_like"),
                                active = state.likes == true,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onLike()
                                },
                            )
                            CompactActionPill(
                                icon = Icons.Filled.ThumbDown,
                                label = com.jellio.android.ui.i18n.string("detail_dislike"),
                                active = state.likes == false,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDislike()
                                },
                            )
                            if (onAddToFriend != null) {
                                CompactActionPill(
                                    icon = Icons.Filled.PersonAdd,
                                    label = friendLabel ?: com.jellio.android.ui.i18n.string("friends_add_to_friend_watchlist"),
                                    active = false,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onAddToFriend()
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        onClick = onPlay,
                        enabled = !state.resolvingPlay,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = JellioText,
                            contentColor = JellioBg,
                            focusedContainerColor = Color.White,
                            focusedContentColor = JellioBg,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.5.dp, Color.White),
                                shape = RoundedCornerShape(999.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .focusRequester(playButtonFocusRequester)
                            .onFocusChanged { onPlayFocusChanged(it.isFocused) },
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                            Text(text = primaryButtonLabel, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (onDownload != null) {
                        IconActionButton(
                            icon = if (isDownloaded) Icons.Filled.Check else androidx.compose.material.icons.Icons.Filled.Download,
                            active = isDownloaded,
                            contentDescription = if (isDownloaded) "Downloaded for offline" else "Download for offline",
                            onClick = onDownload,
                        )
                    }
                    if (actionsExpanded) {
                        if (showWatchlist) {
                            IconActionButton(
                                icon = if (watchlistActive) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkAdd,
                                active = watchlistActive,
                                contentDescription = watchlistDescription ?: com.jellio.android.ui.i18n.string("nav_watchlist"),
                                onClick = onToggleWatchlist,
                            )
                        }
                        if (onAddToFriend != null) {
                            IconActionButton(
                                icon = Icons.Filled.PersonAdd,
                                active = false,
                                contentDescription = friendLabel ?: com.jellio.android.ui.i18n.string("friends_add_to_friend_watchlist"),
                                onClick = onAddToFriend,
                            )
                        }
                        IconActionButton(
                            icon = Icons.Filled.Check,
                            active = state.isWatched,
                            contentDescription = com.jellio.android.ui.i18n.string("detail_mark_watched"),
                            onClick = onToggleWatched,
                        )
                        IconActionButton(
                            icon = Icons.Filled.ThumbUp,
                            active = state.likes == true,
                            contentDescription = com.jellio.android.ui.i18n.string("detail_like"),
                            onClick = onLike,
                        )
                        IconActionButton(
                            icon = Icons.Filled.ThumbDown,
                            active = state.likes == false,
                            contentDescription = com.jellio.android.ui.i18n.string("detail_dislike"),
                            onClick = onDislike,
                        )
                        if (onChangeStream != null) {
                            IconActionButton(
                                icon = Icons.Filled.SwapHoriz,
                                active = false,
                                contentDescription = com.jellio.android.ui.i18n.string("detail_change_stream"),
                                onClick = onChangeStream,
                            )
                        }
                    }
                    IconActionButton(
                        icon = Icons.Filled.MoreVert,
                        active = actionsExpanded,
                        contentDescription = if (actionsExpanded) com.jellio.android.ui.i18n.string("detail_fewer_actions") else com.jellio.android.ui.i18n.string("detail_more_actions"),
                        onClick = { actionsExpanded = !actionsExpanded },
                    )
                }
            }
            val status = when {
                state.findingStreams -> com.jellio.android.ui.i18n.string("detail_finding_streams")
                else -> state.streamStatus
            }
            status?.let {
                Text(
                    text = it,
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (state.detailsRetrying) {
                Text(
                    text = com.jellio.android.ui.i18n.string("detail_refreshing"),
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (state.detailsFailed && onRetryDetails != null) {
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = com.jellio.android.ui.i18n.string("detail_refresh_failed"),
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Surface(
                        onClick = onRetryDetails,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(text = com.jellio.android.ui.i18n.string("action_retry"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

// The title's backdrop, dimmed, with a spinner and its name while the page
// loads.
@Composable
private fun DetailLoading(art: TitleLoadingArtInfo?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        art?.art?.takeIf { it.isNotEmpty() }?.let { candidates ->
            com.jellio.android.ui.common.TitleArtBackdrop(candidates, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.jellio.android.ui.common.ScreenSpinner(size = 44.dp)
            Text(
                text = art?.title?.takeIf { it.isNotBlank() }?.let { com.jellio.android.ui.i18n.formatString("detail_loading_title", it) } ?: com.jellio.android.ui.i18n.string("status_loading"),
                color = JellioText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun IconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val popScale = remember { androidx.compose.animation.core.Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        popScale.snapTo(1.22f)
        popScale.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
            ),
        )
    }
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioSecondary else Color.White.copy(alpha = 0.1f),
        label = "actionBtnContainer",
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioBg else JellioText,
        label = "actionBtnContent",
    )
    val focusedContainerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioSecondary else Color.White.copy(alpha = 0.35f),
        label = "actionBtnFocusedContainer",
    )
    val focusedContentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioBg else Color.White,
        label = "actionBtnFocusedContent",
    )
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = focusedContentColor,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, Color.White),
                shape = CircleShape,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = popScale.value
                scaleY = popScale.value
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun CompactActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioSecondary else Color.White.copy(alpha = 0.08f),
        label = "compactActionContainer",
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioBg else JellioText,
        label = "compactActionContent",
    )
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = if (active) JellioSecondary else Color.White.copy(alpha = 0.2f),
            focusedContentColor = if (active) JellioBg else Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, if (active) JellioSecondary.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(999.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(999.dp),
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeasonsHeader(
    state: DetailUiState,
    horizontalPad: androidx.compose.ui.unit.Dp,
    onSelectSeason: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            text = com.jellio.android.ui.i18n.string("detail_episodes"),
            style = MaterialTheme.typography.titleMedium,
            color = JellioText,
            modifier = Modifier.padding(start = horizontalPad, bottom = 12.dp),
        )
        if (state.seasons.isNotEmpty()) {
            val seasonsListState = rememberLazyListState()
            LaunchedEffect(state.selectedSeasonId, state.seasons) {
                val idx = state.seasons.indexOfFirst { it.Id == state.selectedSeasonId }
                if (idx < 0) return@LaunchedEffect
                val laidOut = snapshotFlow { seasonsListState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
                val fullyVisible = laidOut.let { info ->
                    info.visibleItemsInfo.any { it.index == idx && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
                }
                if (!fullyVisible) seasonsListState.scrollToItem(idx)
            }
            LazyRow(
                state = seasonsListState,
                contentPadding = PaddingValues(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.seasons, key = { it.Id }, contentType = { "season_chip" }) { season ->
                    val selected = season.Id == state.selectedSeasonId
                    Surface(
                        onClick = { onSelectSeason(season.Id) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected) JellioSecondary else JellioBgElevated,
                            contentColor = if (selected) JellioBg else JellioText,
                            focusedContainerColor = JellioSecondary,
                            focusedContentColor = JellioBg,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, if (selected) JellioSecondary else Color.White.copy(alpha = 0.10f)),
                                shape = RoundedCornerShape(999.dp),
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(999.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    ) {
                        Text(
                            text = com.jellio.android.data.i18n.seasonDisplayName(season),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactEpisodeRow(
    episode: BaseItemDto,
    imageUrl: (String, String?, String, Int) -> String,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    releaseLabel: String? = null,
    isCurrent: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val thumbWidth = 132.dp
    val thumbHeight = 74.dp
    val thumbWidthPx = remember(density) { with(density) { thumbWidth.roundToPx() } }
    val thumbHeightPx = remember(density) { with(density) { thumbHeight.roundToPx() } }
    val thumbUrl = remember(episode.Id, episode.ImageTags, episode.ParentThumbImageTag, episode.ParentThumbItemId) {
        episode.ImageTags?.get("Primary")?.let { imageUrl(episode.Id, it, "Primary", 400) }
            ?: episode.ParentThumbImageTag?.let { tag -> episode.ParentThumbItemId?.let { id -> imageUrl(id, tag, "Thumb", 400) } }
    }
    val thumbRequest = remember(thumbUrl, thumbWidthPx, thumbHeightPx) {
        thumbUrl?.let { sizedImageRequest(context, it, thumbWidthPx, thumbHeightPx) }
    }
    val progress = remember(episode.UserData, episode.RunTimeTicks) {
        val playedPct = episode.UserData?.PlayedPercentage
        val posTicks = episode.UserData?.PlaybackPositionTicks ?: 0L
        val runTicks = episode.RunTimeTicks ?: 0L
        when {
            playedPct != null && playedPct > 0.0 -> (playedPct / 100.0).toFloat().coerceIn(0f, 1f)
            posTicks > 0L && runTicks > 0L -> (posTicks.toFloat() / runTicks.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
    }
    val episodePrefix = episode.IndexNumber?.let { com.jellio.android.data.i18n.seasonEpisodeTag(null, it) }
    val episodeName = com.jellio.android.data.i18n.episodeDisplayName(episode.Name, episode.IndexNumber)
    val headerTitle = if (!episodePrefix.isNullOrBlank()) "$episodePrefix - $episodeName" else episodeName
    val runtime = formatRuntime(episode.RunTimeTicks)

    Surface(
        onClick = onClick,
        onLongClick = onOptionsClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isCurrent) JellioSecondary.copy(alpha = 0.14f) else JellioBgElevated,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    if (isCurrent) 1.5.dp else 1.dp,
                    if (isCurrent) JellioSecondary.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                ),
                shape = RoundedCornerShape(12.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(JellioBg),
            ) {
                if (thumbRequest != null) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (episode.UserData?.Played == true) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.72f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = JellioSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .background(JellioSecondary),
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = headerTitle,
                    color = JellioText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (runtime.isNotEmpty() || !releaseLabel.isNullOrEmpty() || isCurrent) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (runtime.isNotEmpty()) {
                            Text(
                                text = runtime,
                                color = JellioTextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (!releaseLabel.isNullOrEmpty()) {
                            Text(
                                text = releaseLabel,
                                color = JellioRarityLegendary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isCurrent) {
                            Text(
                                text = com.jellio.android.ui.i18n.string("detail_current_episode"),
                                color = JellioSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                episode.Overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonsSection(
    state: DetailUiState,
    imageUrl: (String, String?, String, Int) -> String,
    currentEpisodeId: String? = null,
    onSelectSeason: (String) -> Unit,
    onEpisodeClick: (String) -> Unit,
    onEpisodeOptions: (BaseItemDto) -> Unit,
    onEpisodeFocused: (String) -> Unit,
) {
    if (state.seasons.isEmpty() && state.selectedSeasonEpisodes.isEmpty()) return
    // The episode the row was brought to, or the user took over first; saveable
    // so the row does not jump again when this section scrolls back into view.
    var settledFor by rememberSaveable { mutableStateOf<String?>(null) }
    // Bumped on each season the user picks, so the episode row starts at its first card.
    var manualSeasonSwitches by remember { mutableIntStateOf(0) }
    val isCompact = com.jellio.android.ui.common.isCompactScreen()
    val horizontalPad = if (isCompact) 16.dp else 48.dp
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(text = com.jellio.android.ui.i18n.string("detail_episodes"), style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = horizontalPad, bottom = 12.dp))
        if (state.seasons.isNotEmpty()) {
            val seasonsListState = rememberLazyListState()
            LaunchedEffect(state.selectedSeasonId, state.seasons) {
                val idx = state.seasons.indexOfFirst { it.Id == state.selectedSeasonId }
                if (idx < 0) return@LaunchedEffect
                val laidOut = snapshotFlow { seasonsListState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
                val fullyVisible = laidOut.let { info ->
                    info.visibleItemsInfo.any { it.index == idx && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
                }
                if (!fullyVisible) seasonsListState.scrollToItem(idx)
            }
            LazyRow(state = seasonsListState, contentPadding = PaddingValues(horizontal = horizontalPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.seasons, key = { it.Id }, contentType = { "season_chip" }) { season ->
                    val selected = season.Id == state.selectedSeasonId
                    Surface(
                        onClick = {
                            settledFor = currentEpisodeId
                            onSelectSeason(season.Id)
                            manualSeasonSwitches++
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected) JellioSecondary else JellioBgElevated,
                            contentColor = if (selected) JellioBg else JellioText,
                            focusedContainerColor = JellioSecondary,
                            focusedContentColor = JellioBg,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(999.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    ) {
                        Text(text = com.jellio.android.data.i18n.seasonDisplayName(season), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                }
            }
        }
        val bringIntoViewSpec = if (isCompact) {
            LocalBringIntoViewSpec.current
        } else {
            com.jellio.android.ui.common.rememberTvHorizontalBringIntoViewSpec()
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            val releaseLabels = remember(state.selectedSeasonEpisodes, state.streamVerifiedTick) { seasonEpisodeReleaseLabels(state.selectedSeasonEpisodes) }
            val episodesListState = rememberLazyListState()
            LaunchedEffect(currentEpisodeId, state.selectedSeasonEpisodes) {
                if (currentEpisodeId == null || settledFor == currentEpisodeId) return@LaunchedEffect
                val idx = state.selectedSeasonEpisodes.indexOfFirst { it.Id == currentEpisodeId }
                if (idx < 0) return@LaunchedEffect
                // Offset 0 lands the card on the start padding, where focus parks a card.
                episodesListState.scrollToItem(idx)
                settledFor = currentEpisodeId
            }
            LaunchedEffect(manualSeasonSwitches) {
                if (manualSeasonSwitches > 0) episodesListState.scrollToItem(0)
            }
            LazyRow(
                state = episodesListState,
                contentPadding = PaddingValues(horizontal = horizontalPad, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.selectedSeasonEpisodes, key = { it.Id }, contentType = { "episode_card" }) { episode ->
                    EpisodeCard(
                        episode = episode,
                        imageUrl = imageUrl,
                        releaseLabel = releaseLabels[episode.Id],
                        isCurrent = currentEpisodeId != null && episode.Id == currentEpisodeId,
                        onClick = { onEpisodeClick(episode.Id) },
                        onOptionsClick = { onEpisodeOptions(episode) },
                        onFocused = {
                            settledFor = currentEpisodeId
                            if (releaseLabels[episode.Id] == null) {
                                onEpisodeFocused(episode.Id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: BaseItemDto,
    imageUrl: (String, String?, String, Int) -> String,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onFocused: () -> Unit = {},
    releaseLabel: String? = null,
    isCurrent: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cardWidth = 320.dp.scaled()
    val thumbWidthPx = remember(density, cardWidth) { with(density) { cardWidth.roundToPx() } }
    val thumbHeightPx = remember(thumbWidthPx) { (thumbWidthPx * 9) / 16 }
    val thumbUrl = remember(episode.Id, episode.ImageTags, episode.ParentThumbImageTag, episode.ParentThumbItemId) {
        episode.ImageTags?.get("Primary")?.let { imageUrl(episode.Id, it, "Primary", 500) }
            ?: episode.ParentThumbImageTag?.let { tag -> episode.ParentThumbItemId?.let { id -> imageUrl(id, tag, "Thumb", 500) } }
    }
    val thumbRequest = remember(thumbUrl, thumbWidthPx, thumbHeightPx) {
        thumbUrl?.let { sizedImageRequest(context, it, thumbWidthPx, thumbHeightPx) }
    }
    var cardFocused by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(cardWidth)) {
        Surface(
            onClick = onClick,
            onLongClick = onOptionsClick,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBgElevated,
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.28f),
                focusedContentColor = JellioText,
            ),
            border = if (isCurrent) {
                ClickableSurfaceDefaults.border(
                    border = Border(
                        border = BorderStroke(1.5.dp, JellioSecondary.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                    ),
                    focusedBorder = Border(
                        border = BorderStroke(2.5.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ),
                )
            } else {
                ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.5.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ),
                )
            },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier
                .width(cardWidth)
                .onFocusChanged {
                    cardFocused = it.isFocused
                    if (it.isFocused) onFocused()
                },
        ) {
            Column {
                Box(modifier = Modifier.width(cardWidth).aspectRatio(16f / 9f)) {
                    if (thumbRequest != null) {
                        AsyncImage(model = thumbRequest, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    episode.IndexNumber?.let {
                        Text(
                            text = com.jellio.android.data.i18n.seasonEpisodeTag(null, it),
                            color = JellioText,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    // The episode's rating, the same chip as on Home's cards.
                    episode.CommunityRating.let { rating ->
                        com.jellio.android.ui.home.RatingChip(
                            rating = rating,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        )
                    }
                    if (!releaseLabel.isNullOrEmpty()) {
                        Text(
                            text = releaseLabel,
                            color = JellioRarityLegendary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(JellioBg.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    if (isCurrent) {
                        Text(
                            text = com.jellio.android.ui.i18n.string("detail_current_episode"),
                            color = JellioSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(JellioBg.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
                                .border(1.dp, JellioSecondary.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    } else if (episode.UserData?.Played == true) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = JellioText,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        )
                    }
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    com.jellio.android.ui.common.FocusMarqueeText(text = com.jellio.android.data.i18n.episodeDisplayName(episode.Name, episode.IndexNumber), focused = cardFocused, color = JellioText)
                    episode.Overview?.let {
                        Text(text = it, color = JellioTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<PersonDto>, imageUrl: (String, String?, String, Int) -> String, onPersonClick: (String) -> Unit) {
    val isCompact = com.jellio.android.ui.common.isCompactScreen()
    val horizontalPad = if (isCompact) 16.dp else 48.dp
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(text = com.jellio.android.ui.i18n.string("detail_cast"), style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = horizontalPad, bottom = 12.dp))
        val avatarWidth = 140.dp.scaled()
        val bringIntoViewSpec = if (isCompact) {
            LocalBringIntoViewSpec.current
        } else {
            com.jellio.android.ui.common.rememberTvHorizontalBringIntoViewSpec()
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            LazyRow(contentPadding = PaddingValues(horizontal = horizontalPad), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(cast, key = { it.Id }, contentType = { "cast_person" }) { person ->
                    Surface(
                        onClick = { onPersonClick(person.Id) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = 0.25f),
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.width(avatarWidth),
                    ) {
                        Column {
                            Box(modifier = Modifier.width(avatarWidth).aspectRatio(1f).clip(CircleShape).background(JellioBgElevated)) {
                                val tag = person.PrimaryImageTag
                                if (tag != null) {
                                    AsyncImage(
                                        model = imageUrl(person.Id, tag, "Primary", 300),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Text(
                                text = person.Name.orEmpty(),
                                color = JellioText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            person.Role?.let {
                                Text(text = it, color = JellioTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailersRow(trailers: List<TrailerDto>, onOpen: (String) -> Unit) {
    val playable = remember(trailers) { trailers.mapNotNull { t -> t.Url?.takeIf { it.isNotEmpty() }?.let { t to it } } }
    if (playable.isEmpty()) return
    val isCompact = com.jellio.android.ui.common.isCompactScreen()
    val horizontalPad = if (isCompact) 16.dp else 48.dp
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(text = com.jellio.android.ui.i18n.string("detail_trailers"), style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = horizontalPad, bottom = 12.dp))
        val trailerCardWidth = 280.dp.scaled()
        val bringIntoViewSpec = if (isCompact) {
            LocalBringIntoViewSpec.current
        } else {
            com.jellio.android.ui.common.rememberTvHorizontalBringIntoViewSpec()
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            LazyRow(contentPadding = PaddingValues(horizontal = horizontalPad), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(playable, key = { (_, url) -> url }, contentType = { "trailer_card" }) { (trailer, url) ->
                    Surface(
                        onClick = { onOpen(url) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = JellioBgElevated,
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.28f),
                            focusedContentColor = JellioText,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.5.dp, Color.White),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.width(trailerCardWidth),
                    ) {
                        Column {
                            Box(modifier = Modifier.width(trailerCardWidth).aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                                // The icon stays underneath: it shows when there is no id or no thumbnail loads.
                                Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null, tint = JellioText)
                                val thumbs = remember(url) { youTubeId(url)?.let(::youTubeThumbnails).orEmpty() }
                                var thumbIndex by remember(thumbs) { mutableIntStateOf(0) }
                                thumbs.getOrNull(thumbIndex)?.let { thumb ->
                                    coil3.compose.AsyncImage(
                                        model = thumb,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        onError = { thumbIndex++ },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Text(
                                text = trailer.Name ?: com.jellio.android.ui.i18n.string("action_trailer"),
                                color = JellioText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
