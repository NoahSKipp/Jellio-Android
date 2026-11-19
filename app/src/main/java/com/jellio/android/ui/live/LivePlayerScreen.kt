package com.jellio.android.ui.live

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import com.jellio.android.ui.common.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.jellio.android.ui.common.Surface
import androidx.tv.material3.Text
import com.jellio.android.LocalIsInPipMode
import com.jellio.android.MainActivity
import com.jellio.android.data.network.APP_VERSION
import com.jellio.android.data.network.buildEmbyAuthorizationHeader
import com.jellio.android.data.session.Session
import com.jellio.android.ui.common.ScreenSpinner
import com.jellio.android.ui.common.isCompactScreen
import com.jellio.android.ui.common.primaryButtonColors
import com.jellio.android.data.i18n.Strings
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay

private const val INFO_VISIBLE_MS = 5_000L

@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    session: Session,
    channelId: String,
    imageUrl: (String, String?) -> String,
    onBack: () -> Unit,
    viewModel: LivePlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current
    val focus = remember { FocusRequester() }
    val isInPipMode = LocalIsInPipMode.current
    val mainActivity = context as? MainActivity

    LaunchedEffect(channelId) { viewModel.start(session, channelId) }

    val serverHeaders = remember(session.accessToken) {
        mapOf(
            "X-Emby-Token" to session.accessToken,
            "Authorization" to buildEmbyAuthorizationHeader(viewModel.deviceId, APP_VERSION, session.accessToken),
        )
    }
    val dataSource = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Jellio-Android/$APP_VERSION")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            // Tuners can go quiet for several seconds; let the read wait it out.
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(serverHeaders)
    }

    val player = remember {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        // Start on a small buffer, keep a deeper one to ride out stalls, capped by system heap.
        val heap = Runtime.getRuntime().maxMemory()
        val targetLiveBytes = minOf(64L * 1024 * 1024, (heap * 0.20).toLong()).toInt()
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setTargetBufferBytes(targetLiveBytes)
            .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        val sessionActivityPendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            android.content.Intent(context, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val mediaSession = MediaSession.Builder(context, player)
            .setId("jellio_live")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
        com.jellio.android.ui.audiobook.MediaSessionRegistry.setVideoSession(context, mediaSession)
        onDispose {
            com.jellio.android.ui.audiobook.MediaSessionRegistry.setVideoSession(context, null)
            mediaSession.release()
        }
    }

    var isPlaying by remember(player) { mutableStateOf(false) }

    DisposableEffect(player) {
        var renderedFirst = false
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (!renderedFirst) {
                    renderedFirst = true
                    viewModel.onFirstFrame()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> viewModel.onBuffering(true)
                    Player.STATE_READY -> viewModel.onBuffering(false)
                    Player.STATE_ENDED -> viewModel.onPlayerError("ended")
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlayerError(error.errorCodeName)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                renderedFirst = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            viewModel.stop()
        }
    }

    LaunchedEffect(isPlaying, player) {
        val vSize = player.videoSize
        val w = if (vSize.width > 0) vSize.width else 16
        val h = if (vSize.height > 0) vSize.height else 9
        mainActivity?.updatePipParams(isPlaying = isPlaying, aspectWidth = w, aspectHeight = h)
    }

    DisposableEffect(mainActivity) {
        onDispose {
            mainActivity?.updatePipParams(isPlaying = false)
        }
    }

    LaunchedEffect(uiState.streamUrl, uiState.channel?.name) {
        val url = uiState.streamUrl
        if (url == null) {
            player.stop()
            player.clearMediaItems()
        } else {
            // Headers are chosen per stream: the token only ever goes to the server.
            val headers = uiState.requestHeaders
            dataSource.setDefaultRequestProperties(headers ?: serverHeaders)
            dataSource.setUserAgent(headers?.get("User-Agent") ?: "Jellio-Android/$APP_VERSION")
            val mime = if (headers == null) MimeTypes.VIDEO_MP2T else uiState.mimeType
            val chName = uiState.channel?.name.orEmpty()
            val progTitle = uiState.channel?.now?.title?.takeIf { it.isNotBlank() }
            val logoUrl = uiState.channel?.logoUrl?.takeIf { it.isNotBlank() }
            val metadata = MediaMetadata.Builder()
                .setTitle(chName)
                .setDisplayTitle(chName)
                .setSubtitle(progTitle)
                .setArtist(progTitle ?: "Live TV")
                .apply {
                    logoUrl?.let { raw ->
                        runCatching { android.net.Uri.parse(raw) }.getOrNull()?.let { setArtworkUri(it) }
                    }
                }
                .build()
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(metadata)
                    .apply { if (mime != null) setMimeType(mime) }
                    .build()
            )
            player.prepare()
            player.play()
        }
    }

    DisposableEffect(lifecycleOwner, mainActivity) {
        var backgrounded = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (mainActivity?.isInPictureInPictureMode != true) {
                        backgrounded = true
                        viewModel.stop()
                    }
                }
                Lifecycle.Event.ON_START -> if (backgrounded) {
                    backgrounded = false
                    viewModel.resume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activity = context as? android.app.Activity
    DisposableEffect(activity, hostView) {
        hostView.keepScreenOn = true
        val window = activity?.window
        val prevCutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && window != null) {
            window.attributes.layoutInDisplayCutoutMode.also {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        } else {
            null
        }
        val controller = if (window != null) androidx.core.view.WindowCompat.getInsetsController(window, hostView) else null
        controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        if (controller != null) {
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            hostView.keepScreenOn = false
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && window != null && prevCutoutMode != null) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = prevCutoutMode
                }
            }
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun toggleOrientation() {
        val act = activity ?: return
        val current = act.requestedOrientation
        act.requestedOrientation = if (
            current == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
            current == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    var guideOpen by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf<List<LiveProgramme>?>(null) }
    var guideFailure by remember { mutableStateOf<com.jellio.android.ui.common.LoadFailure?>(null) }
    var guideAttempt by remember { mutableStateOf(0) }
    var infoVisible by remember { mutableStateOf(true) }
    var tapVersion by remember { mutableStateOf(0) }
    LaunchedEffect(uiState.infoVersion, tapVersion) {
        if (infoVisible) {
            delay(INFO_VISIBLE_MS)
            infoVisible = false
        }
    }

    BackHandler(enabled = !guideOpen) {
        viewModel.stop()
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(guideOpen, isInPipMode) {
                detectTapGestures(
                    onTap = {
                        if (!guideOpen && !isInPipMode) {
                            infoVisible = !infoVisible
                            if (infoVisible) tapVersion++
                        }
                    }
                )
            }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (guideOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { viewModel.zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { viewModel.zap(1); true }
                    Key.DirectionCenter, Key.Enter, Key.Info, Key.DirectionLeft, Key.DirectionRight -> {
                        if (uiState.status is LiveStatus.Failed) viewModel.retry() else viewModel.showInfo()
                        true
                    }
                    Key.Guide, Key.Menu -> {
                        val channel = uiState.channel
                        if (channel != null && !channel.userStream) {
                            guide = null
                            guideFailure = null
                            guideOpen = true
                        }
                        true
                    }
                    Key.MediaPlayPause, Key.MediaPause, Key.MediaStop -> {
                        // Live has no pause: stopping frees the tuner.
                        viewModel.stop()
                        onBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = false; setKeepContentOnPlayerReset(false) } },
            modifier = Modifier.fillMaxSize(),
        )

        if (!isInPipMode) {
            val status = uiState.status
            if (status == LiveStatus.Starting || status == LiveStatus.Reconnecting) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    ScreenSpinner()
                    Text(
                        text = Strings.get(
                            when {
                                status == LiveStatus.Reconnecting -> "live_reconnecting"
                                uiState.channel?.userStream == true -> "live_opening_link"
                                else -> "live_tuning"
                            },
                        ),
                        color = JellioTextSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            if (status is LiveStatus.Failed) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = status.message, color = JellioText, style = MaterialTheme.typography.titleMedium)
                    Surface(
                        onClick = viewModel::retry,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = primaryButtonColors(),
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(text = Strings.get("live_retry"), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
            }

            val channel = uiState.channel
            val isCompact = isCompactScreen()
            val horizontalPad = if (isCompact) 20.dp else 48.dp

            if (infoVisible || status != LiveStatus.Playing) {
                // Top touch & action bar: Back, Previous/Next Channel, EPG Guide, Rotate, PiP
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(JellioBg.copy(alpha = 0.8f), Color.Transparent)))
                        .padding(horizontal = horizontalPad, vertical = if (isCompact) 16.dp else 24.dp),
                ) {
                    LiveCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = Strings.get("action_back"),
                        onClick = {
                            viewModel.stop()
                            onBack()
                        },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LiveCircleButton(
                            icon = Icons.Filled.KeyboardArrowUp,
                            label = "Previous channel",
                            onClick = {
                                viewModel.zap(-1)
                                tapVersion++
                            },
                        )
                        LiveCircleButton(
                            icon = Icons.Filled.KeyboardArrowDown,
                            label = "Next channel",
                            onClick = {
                                viewModel.zap(1)
                                tapVersion++
                            },
                        )
                        if (channel != null && !channel.userStream) {
                            LiveCircleButton(
                                icon = Icons.Filled.ViewList,
                                label = Strings.get("live_guide"),
                                onClick = {
                                    guide = null
                                    guideFailure = null
                                    guideOpen = true
                                },
                            )
                        }
                        LiveCircleButton(
                            icon = Icons.Filled.ScreenRotation,
                            label = "Rotate screen",
                            onClick = { toggleOrientation() },
                        )
                        if (mainActivity?.supportsPip() == true) {
                            LiveCircleButton(
                                icon = Icons.Filled.PictureInPictureAlt,
                                label = "Picture in Picture",
                                onClick = {
                                    val vSize = player.videoSize
                                    val w = if (vSize.width > 0) vSize.width else 16
                                    val h = if (vSize.height > 0) vSize.height else 9
                                    mainActivity.enterPipNow(w, h)
                                },
                            )
                        }
                    }
                }
            }

            if (channel != null && (infoVisible || status != LiveStatus.Playing)) {
                InfoBar(channel, uiState.now, uiState.next, imageUrl, Modifier.align(Alignment.BottomCenter))
            }

            if (guideOpen && channel != null) {
                LaunchedEffect(channel.id, guideAttempt) {
                    val now = System.currentTimeMillis()
                    com.jellio.android.data.catching { viewModel.loadGuide(channel.id, now) }
                        .onSuccess { guide = it }
                        .onFailure { guideFailure = com.jellio.android.ui.common.loadFailureOf(it) }
                }
                GuideSheet(
                    channel = channel,
                    programmes = guide,
                    imageUrl = imageUrl,
                    onPlay = null,
                    onClose = { guideOpen = false },
                    failure = guideFailure,
                    onRetry = {
                        guide = null
                        guideFailure = null
                        guideAttempt++
                    },
                )
            }
        }
    }

    LaunchedEffect(guideOpen) { if (!guideOpen) runCatching { focus.requestFocus() } }
}

@Composable
private fun LiveCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.35f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, Color.White),
                shape = CircleShape,
            )
        ),
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = label, tint = JellioText, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun InfoBar(
    channel: LiveChannel,
    now: LiveProgramme?,
    next: LiveProgramme?,
    imageUrl: (String, String?) -> String,
    modifier: Modifier = Modifier,
) {
    val isCompact = isCompactScreen()
    val horizontalPad = if (isCompact) 20.dp else 48.dp
    val nowMs = System.currentTimeMillis()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = horizontalPad, end = horizontalPad, top = if (isCompact) 36.dp else 64.dp, bottom = if (isCompact) 20.dp else 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isCompact) 14.dp else 18.dp)) {
            ChannelLogo(channel, imageUrl)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = Strings.get("live_badge"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE53935)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text(text = channel.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (channel.userStream && !channel.description.isNullOrBlank()) {
                    Text(text = channel.description, color = JellioText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
                if (channel.userStream && !channel.group.isNullOrBlank()) {
                    Text(
                        text = channel.group + (channel.quality?.let { " · $it" } ?: ""),
                        color = JellioTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (now != null) {
                    Text(
                        text = formatClock(now.start) + " - " + formatClock(now.end) + "  " + now.name.ifBlank { Strings.get("live_nothing_listed") },
                        color = JellioText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Box(modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                        Box(modifier = Modifier.fillMaxWidth(now.progress(nowMs).coerceIn(0f, 1f)).height(3.dp).background(Color(0xFFFF9800)))
                    }
                }
                if (next != null) {
                    Text(
                        text = Strings.get("live_next") + "  " + formatClock(next.start) + "  " + next.name,
                        color = JellioTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = if (isCompact) "Tap screen for channel & guide controls" else Strings.get("live_player_hint"),
                    color = JellioTextSecondary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
