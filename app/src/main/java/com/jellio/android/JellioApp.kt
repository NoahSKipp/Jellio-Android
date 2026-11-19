package com.jellio.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.repeatOnLifecycle
import com.jellio.android.data.books.BooksRepository
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.offline.OfflineDownloadItem
import com.jellio.android.data.session.Session
import com.jellio.android.ui.AppViewModel
import com.jellio.android.ui.AuthState
import com.jellio.android.ui.PlayAction
import com.jellio.android.ui.audiobook.AudiobookMiniPlayer
import com.jellio.android.ui.audiobook.AudiobookScreen
import com.jellio.android.ui.auth.LoginScreen
import com.jellio.android.ui.books.BookshelfScreen
import com.jellio.android.ui.books.ReaderScreen
import com.jellio.android.ui.books.VocabScreen
import com.jellio.android.ui.detail.DetailScreen
import com.jellio.android.ui.detail.StreamPickerOverlay
import com.jellio.android.ui.downloads.DownloadsScreen
import com.jellio.android.ui.feed.FeedScreen
import com.jellio.android.ui.friends.FriendsViewModel
import com.jellio.android.ui.friends.badgeVisible
import com.jellio.android.ui.home.HomeScreen
import com.jellio.android.ui.home.HomeViewModel
import com.jellio.android.ui.library.LibraryScreen
import com.jellio.android.ui.library.LibraryViewModel
import com.jellio.android.ui.nav.AccountSwitcherOverlay
import com.jellio.android.ui.nav.JellioRoute
import com.jellio.android.ui.nav.LocalBottomBarPadding
import com.jellio.android.ui.nav.MobileBottomNav
import com.jellio.android.ui.nav.MobileBottomNavHeight
import com.jellio.android.ui.nav.SidebarNav
import com.jellio.android.ui.nav.SidebarReservedWidth
import com.jellio.android.ui.nav.isImmersive
import com.jellio.android.ui.nav.jellioNavItems
import com.jellio.android.ui.nowplaying.QuickHubTab
import com.jellio.android.ui.perf.MotionLevel
import com.jellio.android.ui.perf.MotionPolicy
import com.jellio.android.ui.person.PersonScreen
import com.jellio.android.ui.player.PlayerScreen
import com.jellio.android.ui.profile.ProfileScreen
import com.jellio.android.ui.search.SearchScreen
import com.jellio.android.ui.seasonal.SeasonalEffectsOverlay
import com.jellio.android.ui.seasonal.SeasonalEffectsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.jellio.android.ui.seasonal.SeasonalSkyOverlay
import com.jellio.android.ui.service.ServiceScreen
import com.jellio.android.ui.settings.SettingsScreen
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.scaled
import com.jellio.android.ui.update.AppUpdateViewModel
import com.jellio.android.ui.update.BootSplashMark
import com.jellio.android.ui.update.UpdateToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun JellioRoot(
    deepLinkItemId: String?,
    onDeepLinkConsumed: () -> Unit,
    onPlayerActiveChanged: (Boolean) -> Unit = {},
    appViewModel: AppViewModel = hiltViewModel(),
    appUpdateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            appUpdateViewModel.checkPeriodically()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = appViewModel.authState.collectAsState().value) {
            AuthState.Loading -> Box(Modifier.fillMaxSize()) {}
            AuthState.LoggedOut -> LoginScreen(modifier = Modifier.fillMaxSize())
            is AuthState.LoggedIn -> AppBootGate(
                session = state.session,
                appViewModel = appViewModel,
                deepLinkItemId = deepLinkItemId,
                onDeepLinkConsumed = onDeepLinkConsumed,
                onPlayerActiveChanged = onPlayerActiveChanged,
            )
        }

        val updateState by appUpdateViewModel.uiState.collectAsState()
        // Held back during playback, the prompt takes focus; it shows
        // once the player closes.
        updateState.availableVersion?.takeUnless { com.jellio.android.ui.player.PlayerPresence.active }?.let { version ->
            UpdateToast(
                version = version,
                downloading = updateState.downloading,
                onDownload = { appUpdateViewModel.download() },
                onDismiss = { appUpdateViewModel.dismiss() },
                modifier = Modifier.fillMaxSize(),
                error = updateState.error,
            )
        }
    }
}

private const val SPLASH_MIN_MS = 350L
private const val SPLASH_MAX_MS = 1_200L
private const val FRIENDS_POLL_MS = 60_000L

@Composable
private fun AppBootGate(
    session: Session,
    appViewModel: AppViewModel,
    deepLinkItemId: String?,
    onDeepLinkConsumed: () -> Unit,
    onPlayerActiveChanged: (Boolean) -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryWarmupViewModel: LibraryViewModel = hiltViewModel(),
) {
    val libraries by appViewModel.libraries.collectAsState()
    var splashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val shownAt = android.os.SystemClock.uptimeMillis()
        withTimeoutOrNull(SPLASH_MAX_MS) { homeViewModel.uiState.first { !it.isLoading } }
        val left = SPLASH_MIN_MS - (android.os.SystemClock.uptimeMillis() - shownAt)
        if (left > 0) delay(left)
        StartupPrefs.await()
        splashDone = true
    }
    // Loads Home for each user and language; HomeScreen only refreshes on return.
    LaunchedEffect(session.userId, com.jellio.android.data.prefs.DisplayPreferences.displayLanguage) { homeViewModel.load(session) }
    LaunchedEffect(libraries) {
        libraries.firstOrNull {
            val ct = it.CollectionType?.lowercase()
            ct != "books" && ct != "audiobooks" && ct != "livetv"
        }?.let { firstLibrary -> libraryWarmupViewModel.load(session, firstLibrary) }
    }
    // Held until Home has its snapshot or first rows, never shorter than a
    // glance at the logo nor longer than the old fixed hold; past that Home
    // draws its own skeletons.
    if (!splashDone) {
        Box(modifier = Modifier.fillMaxSize().background(JellioBg)) {
            BootSplashMark(modifier = Modifier.fillMaxSize())
        }
    } else {
        JellioApp(
            session = session,
            appViewModel = appViewModel,
            deepLinkItemId = deepLinkItemId,
            onDeepLinkConsumed = onDeepLinkConsumed,
            onPlayerActiveChanged = onPlayerActiveChanged,
        )
    }
}

@Composable
private fun JellioApp(
    session: Session,
    appViewModel: AppViewModel,
    deepLinkItemId: String?,
    onDeepLinkConsumed: () -> Unit,
    onPlayerActiveChanged: (Boolean) -> Unit = {},
    seasonalEffectsViewModel: SeasonalEffectsViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel(),
) {
    var routeStack by remember { mutableStateOf(listOf<JellioRoute>(JellioRoute.Home())) }
    // Home keeps its scroll positions and focused card while a title's page is open.
    val screenStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val route = routeStack.last()
    LaunchedEffect(route) {
        com.jellio.android.data.streams.StreamLookupLog.screen(route::class.simpleName ?: "screen")
        onPlayerActiveChanged(route is JellioRoute.Player || route is JellioRoute.LivePlayer)
    }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    val friendsState by friendsViewModel.state.collectAsState()
    LaunchedEffect(session.userId) {
        friendsViewModel.onSession(session)
        while (true) {
            delay(FRIENDS_POLL_MS)
            friendsViewModel.refresh()
        }
    }
    // A profile switch swaps the session under the open switcher: close it.
    LaunchedEffect(session.userId) { showAccountSwitcher = false }
    var showNotifications by remember { mutableStateOf(false) }
    var showNowPlayingDrawer by remember { mutableStateOf(false) }
    var quickHubInitialTab by remember { mutableStateOf(QuickHubTab.NOW_PLAYING) }
    val nowPlayingSessions by appViewModel.nowPlayingManager.sessions.collectAsState()
    val nowPlayingLocalPaused by appViewModel.nowPlayingManager.localPaused.collectAsState()
    val activeAudiobookId by remember(appViewModel.audiobookController) {
        appViewModel.audiobookController.state
            .map { if (it.isActive) it.bookItem?.Id else null }
            .distinctUntilChanged()
    }.collectAsState(initial = null)
    val downloadsList by appViewModel.offlineDownloadManager.downloads.collectAsState()
    var showChangelog by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(false) }
    var initialAudioLang by remember { mutableStateOf<String?>(null) }
    var initialSubtitleLang by remember { mutableStateOf<String?>(null) }
    var changelogReleaseToDisplay by remember { mutableStateOf(com.jellio.android.ui.changelog.CURRENT_CHANGELOG) }
    var streamPickerItem by remember { mutableStateOf<BaseItemDto?>(null) }
    val libraries by appViewModel.libraries.collectAsState()
    var homeEditMode by remember(route) {
        mutableStateOf((route as? JellioRoute.Home)?.editMode == true)
    }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(session.userId) {
        if (com.jellio.android.ui.onboarding.OnboardingPrefs.shouldShowOnboarding(context, session.userId)) {
            val config = appViewModel.getUserConfiguration(session.userId)
            initialAudioLang = config?.AudioLanguagePreference?.trim()?.ifBlank { null }
            val subMode = config?.SubtitleMode?.trim()
            val subPref = config?.SubtitleLanguagePreference?.trim()?.ifBlank { null }
            initialSubtitleLang = if (subMode.equals("None", ignoreCase = true) || subPref == "off") "off" else subPref
            showNotifications = false
            showChangelog = false
            showOnboarding = true
        } else if (com.jellio.android.ui.changelog.ChangelogPrefs.shouldShowChangelog(context)) {
            changelogReleaseToDisplay = com.jellio.android.ui.changelog.LATEST_BIG_CHANGELOG
            showNotifications = false
            showChangelog = true
        }
    }

    val appLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(appLifecycle) {
        appLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { seasonalEffectsViewModel.runWhileStarted() }
    }
    val seasonalTheme by seasonalEffectsViewModel.activeTheme.collectAsState()
    val sidebarFocusRequester = remember { FocusRequester() }
    var sidebarHasRealFocus by remember { mutableStateOf(false) }
    var sidebarForceCollapsed by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // The shortcut only claims its key where the hub can actually open; elsewhere the key works as usual.
    val quickHubShortcutAllowed = !route.isImmersive() && (
        showNowPlayingDrawer || (
            !homeEditMode && streamPickerItem == null &&
                !(showAccountSwitcher || showNotifications || showChangelog || showOnboarding)
            )
        )
    androidx.compose.runtime.DisposableEffect(route, showNowPlayingDrawer, quickHubShortcutAllowed) {
        val act = (context as? MainActivity)
        act?.isQuickHubOpen = showNowPlayingDrawer && !route.isImmersive()
        val toggleHandler = {
            quickHubInitialTab = QuickHubTab.NOW_PLAYING
            showNowPlayingDrawer = !showNowPlayingDrawer
        }
        act?.onQuickHubShortcut = if (quickHubShortcutAllowed) toggleHandler else null
        onDispose {
            act?.isQuickHubOpen = false
            act?.onQuickHubShortcut = null
        }
    }

    fun switchTab(target: JellioRoute) {
        routeStack = listOf(target)
    }

    fun push(target: JellioRoute) {
        routeStack = routeStack + target
    }

    fun openQuickHub(tab: QuickHubTab) {
        quickHubInitialTab = tab
        showNowPlayingDrawer = true
    }

    BackHandler(enabled = routeStack.size > 1) {
        routeStack = routeStack.dropLast(1)
    }

    val onNavigateToDetail: (String) -> Unit = { itemId -> push(JellioRoute.Detail(itemId)) }
    val onNavigateToDetailReplace: (String) -> Unit = { itemId ->
        if (routeStack.lastOrNull() is JellioRoute.Detail) {
            routeStack = routeStack.dropLast(1) + JellioRoute.Detail(itemId)
        } else {
            push(JellioRoute.Detail(itemId))
        }
    }
    val onNavigateToPerson: (String) -> Unit = { personId -> push(JellioRoute.Person(personId)) }
    fun playItem(itemId: String, mediaSourceId: String? = null, startPositionTicks: Long? = null) {
        push(JellioRoute.Player(itemId, mediaSourceId, startPositionTicks))
    }
    val onPlayDirect: (String, String?) -> Unit = { itemId, mediaSourceId -> playItem(itemId, mediaSourceId) }

    val onOpenItemSmart: (BaseItemDto) -> Unit = { item ->
        when {
            item.Type == "Book" -> {
                val kind = if (BooksRepository.isComicOrManga(item)) "manga" else "book"
                push(JellioRoute.Reader(itemId = item.Id, kind = kind))
            }
            item.Type == "AudioBook" -> {
                push(JellioRoute.AudiobookPlayer(itemId = item.Id))
            }
            else -> onNavigateToDetail(item.Id)
        }
    }

    LaunchedEffect(appViewModel) {
        appViewModel.remoteControlManager.playCommands.collect { cmd ->
            if (routeStack.lastOrNull() is JellioRoute.Player) {
                routeStack = routeStack.dropLast(1)
            }
            playItem(cmd.itemId, cmd.mediaSourceId, cmd.startPositionTicks)
        }
    }
    // Play the way the title page does: one stream plays, several (with
    // nothing remembered) open the picker, none opens the picker to say so.
    val onPlay: (BaseItemDto) -> Unit = { item ->
        when {
            item.Type == "Book" -> {
                val kind = if (BooksRepository.isComicOrManga(item)) "manga" else "book"
                push(JellioRoute.Reader(itemId = item.Id, kind = kind))
            }
            item.Type == "AudioBook" -> {
                push(JellioRoute.AudiobookPlayer(itemId = item.Id))
            }
            item.Type != "Movie" && item.Type != "Episode" -> onPlayDirect(item.Id, null)
            else -> scope.launch {
                when (val action = runCatching { appViewModel.resolvePlayAction(session, item) }.getOrNull()) {
                    is PlayAction.Direct -> onPlayDirect(action.itemId, action.mediaSourceId)
                    is PlayAction.ShowPicker -> streamPickerItem = action.item
                    // The lookup failed: the picker says why and can try again.
                    null -> streamPickerItem = item
                }
            }
        }
    }
    val onPlayId: (String, Boolean) -> Unit = { itemId, leavePlayer ->
        scope.launch {
            val item = appViewModel.getItemOrNull(session, itemId)
            val empty = item != null && (item.Type == "Movie" || item.Type == "Episode") && runCatching { appViewModel.getMediaSources(session, item.Id) }.getOrNull()?.isEmpty() == true
            if (leavePlayer) routeStack = routeStack.dropLast(1)
            if (empty) streamPickerItem = item else onPlayDirect(itemId, null)
        }
    }

    val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    val needsMediaNotificationPermission = activeAudiobookId != null ||
        route is JellioRoute.Player ||
        route is JellioRoute.LivePlayer
    LaunchedEffect(needsMediaNotificationPermission) {
        if (needsMediaNotificationPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching { notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }

    LaunchedEffect(deepLinkItemId) {
        val itemId = deepLinkItemId ?: return@LaunchedEffect
        when {
            itemId == "audiobook:active" -> {
                val activeId = activeAudiobookId
                if (!activeId.isNullOrBlank() && route !is JellioRoute.AudiobookPlayer) {
                    push(JellioRoute.AudiobookPlayer(activeId))
                }
            }
            itemId.startsWith("detail:") -> onNavigateToDetail(itemId.removePrefix("detail:"))
            else -> onPlayId(itemId, false)
        }
        onDeepLinkConsumed()
    }

    val memoizedImageUrl = remember(session) {
        { item: com.jellio.android.data.model.BaseItemDto, imageType: String, maxWidth: Int ->
            appViewModel.imageUrl(session, item, imageType, maxWidth)
        }
    }
    val memoizedRawImageUrl = remember(session) {
        { itemId: String, tag: String?, imageType: String, maxWidth: Int ->
            appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth)
        }
    }
    val memoizedServiceLogoUrl = remember(session) {
        { name: String -> appViewModel.serviceLogoUrl(session, name) }
    }

    val layoutMode = com.jellio.android.ui.common.rememberWindowLayoutMode()
    val useBottomNav = layoutMode == com.jellio.android.ui.common.WindowLayoutMode.COMPACT_PORTRAIT
    val isSecondaryMobileRoute = useBottomNav && (
        route is JellioRoute.Detail ||
            route is JellioRoute.Person ||
            route is JellioRoute.Service ||
            route is JellioRoute.Settings ||
            route is JellioRoute.Vocab
        )
    val miniPlayerVisible = !route.isImmersive() && activeAudiobookId != null
    val bottomNavVisible = useBottomNav && !route.isImmersive() && !isSecondaryMobileRoute
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarInsetDp = if (route.isImmersive() || !useBottomNav) {
        0.dp
    } else {
        navBarBottomInset +
            (if (bottomNavVisible) MobileBottomNavHeight else 0.dp) +
            (if (miniPlayerVisible) 68.dp else 0.dp)
    }
    val navItems = remember(libraries) { jellioNavItems(libraries) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!route.isImmersive()) {
            SeasonalEffectsOverlay(themeKey = seasonalTheme, modifier = Modifier.fillMaxSize())
        }
        CompositionLocalProvider(LocalBottomBarPadding provides bottomBarInsetDp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let {
                        if (route.isImmersive() || useBottomNav) it
                        else {
                            val bottomPad = if (miniPlayerVisible) 76.dp else 0.dp
                            it.padding(start = SidebarReservedWidth.scaled(), bottom = bottomPad)
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = route,
                    contentKey = { it.transitionKey() },
                    transitionSpec = {
                        when (MotionPolicy.level) {
                            MotionLevel.STILL, MotionLevel.OFF ->
                                EnterTransition.None togetherWith ExitTransition.None
                            MotionLevel.REDUCED ->
                                fadeIn(animationSpec = tween(120)) togetherWith fadeOut(animationSpec = tween(100))
                            MotionLevel.FULL -> {
                                val fromTab = initialState.isTopLevelTab()
                                val toTab = targetState.isTopLevelTab()
                                when {
                                    fromTab && toTab ->
                                        fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                                    !toTab ->
                                        (fadeIn(animationSpec = tween(200)) + slideInHorizontally(animationSpec = tween(220)) { it / 16 }) togetherWith
                                            fadeOut(animationSpec = tween(140))
                                    else ->
                                        fadeIn(animationSpec = tween(180)) togetherWith
                                            (fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(180)) { it / 16 })
                                }
                            }
                        }
                    },
                    label = "routeTransition",
                    modifier = Modifier.fillMaxSize(),
                ) { current ->
                    when (current) {
                is JellioRoute.Profile -> ProfileScreen(
                    session = session,
                    userId = current.userId,
                    userImageUrl = { userId, tag, maxWidth -> appViewModel.userImageUrl(session, userId, tag, maxWidth) },
                    bannerUrl = { userId -> appViewModel.bannerUrl(session, userId) },
                    itemImageUrl = memoizedRawImageUrl,
                    onNavigateToDetail = onNavigateToDetail,
                    onLogout = { appViewModel.logout() },
                    onOpenProfile = { id -> push(JellioRoute.Profile(id)) },
                    modifier = Modifier.fillMaxSize(),
                )
                JellioRoute.Feed -> screenStates.SaveableStateProvider("feed") {
                    FeedScreen(
                        userId = session.userId,
                        imageUrl = memoizedRawImageUrl,
                        userImageUrl = { userId, tag, maxWidth -> appViewModel.userImageUrl(session, userId, tag, maxWidth) },
                        onUserClick = { userId -> push(JellioRoute.Profile(userId)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is JellioRoute.Home -> screenStates.SaveableStateProvider("home") {
                    HomeScreen(
                        session = session,
                        imageUrl = memoizedImageUrl,
                        rawImageUrl = memoizedRawImageUrl,
                        serviceLogoUrl = memoizedServiceLogoUrl,
                        onItemClick = onOpenItemSmart,
                        onComingSoonClick = onNavigateToDetail,
                        onServiceClick = { name -> push(JellioRoute.Service(name)) },
                        onEditModeChange = { editing ->
                            homeEditMode = editing
                            if (!editing && current.editMode) {
                                routeStack = listOf(JellioRoute.Home(editMode = false))
                            }
                        },
                        onPlay = onPlay,
                        onOpenNowPlaying = {
                            openQuickHub(QuickHubTab.NOW_PLAYING)
                        },
                        onPlayManually = { item ->
                            scope.launch {
                                when (val action = appViewModel.resolvePlayAction(session, item, forceChoice = true)) {
                                    is PlayAction.Direct -> onPlayDirect(action.itemId, action.mediaSourceId)
                                    is PlayAction.ShowPicker -> streamPickerItem = action.item
                                }
                            }
                        },
                        initialEditMode = current.editMode,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                JellioRoute.Search -> screenStates.SaveableStateProvider("search") {
                    SearchScreen(
                        session = session,
                        imageUrl = memoizedImageUrl,
                        onItemClick = onOpenItemSmart,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is JellioRoute.LibraryTab -> {
                    val library = libraries.firstOrNull { it.Id == current.libraryId && it.Name.orEmpty() == current.name }
                    if (library != null) {
                        val libKey = "library:${current.libraryId}:${current.name}"
                        screenStates.SaveableStateProvider(libKey) {
                            androidx.compose.runtime.key(libKey) {
                                LibraryScreen(
                                    session = session,
                                    library = library,
                                    imageUrl = memoizedImageUrl,
                                    onItemClick = onOpenItemSmart,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                is JellioRoute.Bookshelf -> screenStates.SaveableStateProvider("bookshelf") {
                    BookshelfScreen(
                        initialKind = current.kind,
                        onOpenReader = { itemId, kind, mangaId, chapterId ->
                            push(JellioRoute.Reader(itemId = itemId, kind = kind, mangaId = mangaId, chapterId = chapterId))
                        },
                        onOpenAudiobook = { itemId ->
                            push(JellioRoute.AudiobookPlayer(itemId = itemId))
                        },
                        onOpenVocab = {
                            push(JellioRoute.Vocab)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is JellioRoute.Reader -> androidx.compose.runtime.key("${current.itemId}:${current.kind}:${current.mangaId}:${current.chapterId}") {
                    ReaderScreen(
                        itemId = current.itemId,
                        kind = current.kind,
                        mangaId = current.mangaId,
                        chapterId = current.chapterId,
                        onBack = {
                            if (routeStack.size > 1) routeStack = routeStack.dropLast(1)
                            else switchTab(JellioRoute.Bookshelf())
                        },
                        onOpenChapter = { nextItemId, nextMangaId, nextChapterId ->
                            val nextRoute = JellioRoute.Reader(
                                itemId = nextItemId,
                                kind = "manga",
                                mangaId = nextMangaId,
                                chapterId = nextChapterId,
                            )
                            routeStack = routeStack.dropLast(1) + nextRoute
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is JellioRoute.AudiobookPlayer -> androidx.compose.runtime.key(current.itemId) {
                    AudiobookScreen(
                        itemId = current.itemId,
                        controller = appViewModel.audiobookController,
                        onBack = {
                            if (routeStack.size > 1) routeStack = routeStack.dropLast(1)
                            else switchTab(JellioRoute.Bookshelf("audiobook"))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                JellioRoute.Vocab -> VocabScreen(
                    onBack = {
                        if (routeStack.size > 1) routeStack = routeStack.dropLast(1)
                        else switchTab(JellioRoute.Bookshelf("ebook"))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                JellioRoute.Downloads -> screenStates.SaveableStateProvider("downloads") {
                    DownloadsScreen(
                        onBack = {
                            if (routeStack.size > 1) routeStack = routeStack.dropLast(1)
                            else switchTab(JellioRoute.Home())
                        },
                        onPlayOfflineVideo = { dl ->
                            playItem(dl.id)
                        },
                        onReadOfflineBook = { dl ->
                            val readerKind = if (dl.kind == "manga") "manga" else "book"
                            push(
                                JellioRoute.Reader(
                                    itemId = dl.id,
                                    kind = readerKind,
                                    mangaId = dl.streamMangaId,
                                    chapterId = dl.streamChapterId,
                                ),
                            )
                        },
                        onListenOfflineAudiobook = { dl ->
                            push(JellioRoute.AudiobookPlayer(itemId = dl.id))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                JellioRoute.Settings -> SettingsScreen(
                    session = session,
                    onLogout = { appViewModel.logout() },
                    onViewProfile = { push(JellioRoute.Profile()) },
                    onCustomizeHome = {
                        routeStack = listOf(JellioRoute.Home(editMode = true))
                    },
                    onShowChangelog = {
                        changelogReleaseToDisplay = com.jellio.android.ui.changelog.CURRENT_CHANGELOG
                        showChangelog = true
                    },
                    onLaunchOnboarding = { showOnboarding = true },
                    onOpenQuickHub = {
                        openQuickHub(QuickHubTab.NOW_PLAYING)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is JellioRoute.Detail -> androidx.compose.runtime.key(current.itemId) {
                    DetailScreen(
                        session = session,
                        itemId = current.itemId,
                        imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToDetailReplace = onNavigateToDetailReplace,
                        onNavigateToPerson = onNavigateToPerson,
                        onOpenStreamPicker = { item -> streamPickerItem = item },
                        onPlayDirect = onPlayDirect,
                        onOpenBookReader = { item ->
                            val kind = if (BooksRepository.isComicOrManga(item)) "manga" else "book"
                            push(JellioRoute.Reader(itemId = item.Id, kind = kind))
                        },
                        onOpenAudiobook = { item ->
                            push(JellioRoute.AudiobookPlayer(itemId = item.Id))
                        },
                        onDownloadItem = { item ->
                            scope.launch {
                                when {
                                    item.Type == "Book" -> {
                                        val isManga = BooksRepository.isComicOrManga(item)
                                        appViewModel.offlineDownloadManager.queueBookDownload(
                                            item = item,
                                            isManga = isManga,
                                            userIdOverride = session.userId,
                                        )
                                    }
                                    item.Type == "AudioBook" -> {
                                        val tracks = runCatching {
                                            appViewModel.booksRepository.getAudiobookTracks(session.userId, item)
                                        }.getOrNull().orEmpty().ifEmpty { listOf(item) }
                                        appViewModel.offlineDownloadManager.queueAudiobookDownload(
                                            item = item,
                                            tracks = tracks,
                                            userIdOverride = session.userId,
                                        )
                                    }
                                    else -> {
                                        appViewModel.offlineDownloadManager.queueVideoDownload(
                                            item = item,
                                            quality = "high",
                                            userIdOverride = session.userId,
                                        )
                                    }
                                }
                            }
                        },
                        isItemDownloaded = { checkId ->
                            val norm = BooksRepository.normalizeId(checkId)
                            downloadsList.any {
                                BooksRepository.normalizeId(it.id) == norm &&
                                    (it.status == OfflineDownloadItem.STATUS_COMPLETED ||
                                        it.status == OfflineDownloadItem.STATUS_DOWNLOADING ||
                                        it.status == OfflineDownloadItem.STATUS_QUEUED)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is JellioRoute.Person -> androidx.compose.runtime.key(current.personId) {
                    PersonScreen(
                        session = session,
                        personId = current.personId,
                        imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                        onBack = { routeStack = routeStack.dropLast(1) },
                        onItemClick = onNavigateToDetail,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is JellioRoute.Service -> androidx.compose.runtime.key(current.name) {
                    ServiceScreen(
                        session = session,
                        serviceName = current.name,
                        logoUrl = appViewModel.serviceLogoUrl(session, current.name),
                        imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                        onItemClick = onOpenItemSmart,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is JellioRoute.Player -> PlayerScreen(
                    session = session,
                    itemId = current.itemId,
                    mediaSourceId = current.mediaSourceId,
                    startPositionTicks = current.startPositionTicks,
                    onBack = { routeStack = routeStack.dropLast(1) },
                    onPlayNext = { nextItemId -> onPlayId(nextItemId, true) },
                    onPlayPrevious = { previousId ->
                        routeStack = routeStack.dropLast(1)
                        playItem(previousId, null, 0L)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                JellioRoute.LiveTv -> screenStates.SaveableStateProvider("livetv") {
                    com.jellio.android.ui.live.LiveTvScreen(
                        session = session,
                        imageUrl = { itemId, tag -> memoizedRawImageUrl(itemId, tag, "Primary", 240) },
                        onPlay = { channelId -> push(JellioRoute.LivePlayer(channelId)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is JellioRoute.LivePlayer -> androidx.compose.runtime.key(current.channelId) {
                    com.jellio.android.ui.live.LivePlayerScreen(
                        session = session,
                        channelId = current.channelId,
                        imageUrl = { itemId, tag -> memoizedRawImageUrl(itemId, tag, "Primary", 240) },
                        onBack = { routeStack = routeStack.dropLast(1) },
                    )
                }
                    }
                }
            }
        }

        if (!route.isImmersive()) {
            SeasonalSkyOverlay(themeKey = seasonalTheme, modifier = Modifier.fillMaxSize())
        }

        if (!route.isImmersive()) {
            LaunchedEffect(route) {
                sidebarForceCollapsed = true
                snapshotFlow { sidebarHasRealFocus }.collect { hasFocus ->
                    if (hasFocus && sidebarForceCollapsed) {
                        var moved = focusManager.moveFocus(FocusDirection.Right)
                        var tries = 0
                        while (!moved && tries < 16 && sidebarHasRealFocus) {
                            delay(150)
                            moved = !sidebarHasRealFocus || focusManager.moveFocus(FocusDirection.Right)
                            tries++
                        }
                        if (!moved) sidebarForceCollapsed = false
                    } else if (!hasFocus) {
                        sidebarForceCollapsed = false
                    }
                }
            }
            if (useBottomNav) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .then(
                            if (isSecondaryMobileRoute) {
                                Modifier.navigationBarsPadding().padding(bottom = 8.dp)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    AudiobookMiniPlayer(
                        controller = appViewModel.audiobookController,
                        onExpand = { bookId -> push(JellioRoute.AudiobookPlayer(bookId)) },
                    )
                    if (!isSecondaryMobileRoute) {
                        MobileBottomNav(
                            items = navItems,
                            selected = route,
                            enabled = !homeEditMode,
                            onSelect = { clicked ->
                                if (clicked is JellioRoute.Profile && clicked.userId == null) {
                                    showAccountSwitcher = true
                                } else {
                                    switchTab(clicked)
                                }
                            },
                            profileAvatarUrl = appViewModel.userImageUrl(session, session.userId, null, 200),
                            profileName = session.userName,
                            profileBadge = badgeVisible(friendsState.incoming.size),
                        )
                    }
                }
            } else {
                SidebarNav(
                    items = navItems,
                    selected = route,
                    enabled = !homeEditMode,
                    focusable = !homeEditMode && !(showAccountSwitcher || showNotifications || showChangelog || showOnboarding || showNowPlayingDrawer),
                    onSelect = { clicked ->
                        if (clicked is JellioRoute.Profile && clicked.userId == null) {
                            showAccountSwitcher = true
                        } else {
                            switchTab(clicked)
                        }
                    },
                    profileAvatarUrl = appViewModel.userImageUrl(session, session.userId, null, 200),
                    profileName = session.userName,
                    profileBadge = badgeVisible(friendsState.incoming.size),
                    restoreFocusRequester = sidebarFocusRequester,
                    forceCollapsed = sidebarForceCollapsed,
                    onFocusChange = { sidebarHasRealFocus = it },
                )
                AudiobookMiniPlayer(
                    controller = appViewModel.audiobookController,
                    onExpand = { bookId -> push(JellioRoute.AudiobookPlayer(bookId)) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = SidebarReservedWidth.scaled(), bottom = 12.dp),
                )
            }
            if (showAccountSwitcher) {
                AccountSwitcherOverlay(
                    session = session,
                    onDismiss = {
                        if (!useBottomNav) runCatching { sidebarFocusRequester.requestFocus() }
                        showAccountSwitcher = false
                    },
                    onViewProfile = { push(JellioRoute.Profile()) },
                    onOpenSettings = { switchTab(JellioRoute.Settings) },
                    onSignOut = { appViewModel.logout() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showNotifications) {
                com.jellio.android.ui.notify.NotificationsOverlay(
                    onDismiss = {
                        showNotifications = false
                        runCatching { sidebarFocusRequester.requestFocus() }
                    },
                    onOpenItem = { itemId -> onNavigateToDetail(itemId.replace("-", "")) },
                    onOpenLiveTv = { switchTab(JellioRoute.LiveTv) },
                    onOpenGroupWatch = {
                        showNotifications = false
                        openQuickHub(QuickHubTab.GROUP_WATCH)
                    },
                )
            }
            if (showOnboarding) {
                com.jellio.android.ui.onboarding.OnboardingOverlay(
                    userId = session.userId,
                    initialAudioLang = initialAudioLang,
                    initialSubtitleLang = initialSubtitleLang,
                    onSavePreferences = { audio, subtitle ->
                        appViewModel.saveLanguagePreferences(session, audio, subtitle)
                    },
                    onImportMihon = { bytes ->
                        appViewModel.booksRepository.importMihonBackup(bytes)
                    },
                    onComplete = {
                        showOnboarding = false
                        runCatching { sidebarFocusRequester.requestFocus() }
                    },
                )
            }
            if (showChangelog) {
                com.jellio.android.ui.changelog.ChangelogPopup(
                    release = changelogReleaseToDisplay,
                    onDismiss = {
                        com.jellio.android.ui.changelog.ChangelogPrefs.markSeen(context, changelogReleaseToDisplay.version)
                        showChangelog = false
                    },
                )
            }
            if (showNowPlayingDrawer) {
                com.jellio.android.ui.nowplaying.NowPlayingDrawer(
                    session = session,
                    sessions = nowPlayingSessions,
                    localPaused = nowPlayingLocalPaused,
                    initialTab = quickHubInitialTab,
                    onDismiss = {
                        showNowPlayingDrawer = false
                        runCatching { sidebarFocusRequester.requestFocus() }
                    },
                    onHandoff = { sess ->
                        showNowPlayingDrawer = false
                        sess.Item?.Id?.let { itemId ->
                            playItem(itemId, startPositionTicks = sess.PositionTicks)
                        }
                    },
                    onNavigateToDetail = { itemId ->
                        showNowPlayingDrawer = false
                        onNavigateToDetail(itemId)
                    },
                    onTuneIn = { channelId ->
                        showNowPlayingDrawer = false
                        push(JellioRoute.LivePlayer(channelId))
                    },
                    onResumeLocal = {
                        showNowPlayingDrawer = false
                        nowPlayingLocalPaused?.let { paused ->
                            playItem(paused.itemId, startPositionTicks = paused.positionTicks)
                        }
                    },
                    onDismissLocal = {
                        appViewModel.nowPlayingManager.setLocalPaused(null)
                    },
                    onOpenLiveTv = {
                        showNowPlayingDrawer = false
                        switchTab(JellioRoute.LiveTv)
                    },
                    onOpenWatchlist = {
                        showNowPlayingDrawer = false
                        switchTab(JellioRoute.Home())
                    },
                    onOpenProfile = { id ->
                        showNowPlayingDrawer = false
                        push(JellioRoute.Profile(id))
                    },
                    pollNowPlaying = { appViewModel.nowPlayingManager.pollClosely() },
                    rawImageUrl = memoizedRawImageUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val pickerItem = streamPickerItem
        if (pickerItem != null) {
            StreamPickerOverlay(
                item = pickerItem,
                backdropUrl = pickerItem.BackdropImageTags?.firstOrNull()?.let {
                    appViewModel.rawImageUrl(session, pickerItem.Id, it, "Backdrop", 1920)
                } ?: pickerItem.ImageTags?.get("Primary")?.let {
                    appViewModel.rawImageUrl(session, pickerItem.Id, it, "Primary", 1920)
                },
                loadSources = { forceFresh -> appViewModel.getMediaSources(session, pickerItem.Id, forceFresh) },
                rememberedSourceId = { appViewModel.rememberedMediaSourceId(pickerItem.Id) },
                loadReadyInfo = { appViewModel.readyInfo(pickerItem.Id) },
                onSelect = { source ->
                    streamPickerItem = null
                    val mediaSourceId = source.Id
                    if (mediaSourceId != null) {
                        scope.launch { appViewModel.rememberStreamChoice(pickerItem.Id, mediaSourceId) }
                    }
                    onPlayDirect(pickerItem.Id, mediaSourceId)
                },
                onDismiss = { streamPickerItem = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Announcements from the server, over everything (Home, the
        // player, any screen).
        com.jellio.android.ui.announce.AnnouncementToast(modifier = Modifier.align(Alignment.TopCenter))
        com.jellio.android.ui.notify.NewNotificationsPill(
            immersive = route.isImmersive(),
            modifier = Modifier.align(Alignment.TopCenter),
        )
        com.jellio.android.ui.announce.SoundPlayerHost()
        com.jellio.android.ui.announce.ScreenOverlayHost()
        com.jellio.android.ui.groupwatch.GroupWatchHost(
            session = session,
            isPlaying = { id ->
                (routeStack.lastOrNull() as? JellioRoute.Player)?.let { com.jellio.android.data.syncplay.SyncPlayManager.sameId(it.itemId, id) } == true
            },
            // Following the group replaces whatever the player had open.
            openItem = { id ->
                if (routeStack.lastOrNull() is JellioRoute.Player) routeStack = routeStack.dropLast(1)
                onPlayDirect(id, null)
            },
        )
    }
}

private fun JellioRoute.isTopLevelTab(): Boolean = when (this) {
    is JellioRoute.Home,
    JellioRoute.Search,
    is JellioRoute.LibraryTab,
    is JellioRoute.Bookshelf,
    JellioRoute.Downloads,
    JellioRoute.Feed,
    JellioRoute.LiveTv -> true
    is JellioRoute.Profile -> userId == null
    else -> false
}

private fun JellioRoute.transitionKey(): Any = when (this) {
    is JellioRoute.Home -> "tab:home"
    JellioRoute.Search -> "tab:search"
    is JellioRoute.LibraryTab -> "tab:library:$libraryId:$name"
    is JellioRoute.Bookshelf -> "tab:bookshelf"
    JellioRoute.Downloads -> "tab:downloads"
    JellioRoute.Feed -> "tab:feed"
    JellioRoute.LiveTv -> "tab:livetv"
    else -> this
}
