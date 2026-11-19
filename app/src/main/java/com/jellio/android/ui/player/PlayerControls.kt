package com.jellio.android.ui.player

import com.jellio.android.data.i18n.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.tv.material3.Border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellio.android.ui.common.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import com.jellio.android.ui.common.Surface
import androidx.tv.material3.Text
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary

import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.media3.ui.AspectRatioFrameLayout

@Composable
internal fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    positionMs: () -> Long,
    durationMs: Long,
    hasSubtitleTracks: Boolean,
    hasAudioTracks: Boolean,
    hasSourceOptions: Boolean,
    hasEpisodes: Boolean,
    speedLabel: String,
    sleepTimerActive: Boolean,
    enabled: Boolean = true,
    onPlayPause: () -> Unit,
    onBack: () -> Unit = {},
    onSkip: (Long) -> Unit,
    onScrubEnd: (Long?) -> Unit,
    onOpenSubtitleMenu: () -> Unit,
    onOpenSpeedMenu: () -> Unit,
    onOpenSleepMenu: () -> Unit,
    onOpenAudioMenu: () -> Unit,
    onOpenSourceMenu: () -> Unit,
    onOpenEpisodesMenu: () -> Unit,
    episodeNav: EpisodeNav = EpisodeNav(show = false, hasPrevious = false, hasNext = false),
    onPreviousEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    inGroup: Boolean = false,
    onOpenReactMenu: () -> Unit = {},
    supportsPip: Boolean = false,
    onEnterPip: () -> Unit = {},
    onToggleOrientation: () -> Unit = {},
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onCycleResizeMode: () -> Unit = {},
) {
    val isCompact = com.jellio.android.ui.common.isCompactScreen()
    val horizontalPad = if (isCompact) 20.dp else 48.dp
    val centerSpacing = if (isCompact) 18.dp else 36.dp
    val playSize = if (isCompact) 72.dp else 96.dp
    val skipSize = if (isCompact) 52.dp else 68.dp
    val epSize = if (isCompact) 44.dp else 56.dp

    if (isLocked) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                onClick = onToggleLock,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = JellioBgElevated.copy(alpha = 0.85f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.28f),
                    focusedContentColor = JellioText,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, Color.White),
                        shape = RoundedCornerShape(999.dp),
                    )
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isCompact) 24.dp else 36.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(999.dp)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = "Unlock", modifier = Modifier.size(18.dp))
                    Text(text = "Tap to Unlock", style = androidx.tv.material3.MaterialTheme.typography.labelLarge)
                }
            }
        }
        return
    }

    val resizeLabel = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
        else -> "Fit"
    }

    Box(modifier = Modifier.fillMaxSize().focusProperties { canFocus = enabled }) {
        val playPauseFocusRequester = remember { FocusRequester() }
        LaunchedEffect(enabled) {
            if (enabled) {
                playPauseFocusRequester.requestFocus()
            }
        }

        // Top bar with Back button and quick mobile actions (Lock, Resize, Rotate, PiP)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(JellioBg.copy(alpha = 0.78f), Color.Transparent)))
                .padding(horizontal = horizontalPad, vertical = if (isCompact) 16.dp else 24.dp),
        ) {
            TransportButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = Strings.get("action_back"),
                size = 44.dp,
                onClick = onBack,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TransportButton(
                    icon = Icons.Filled.LockOpen,
                    label = "Lock screen",
                    size = 44.dp,
                    onClick = onToggleLock,
                )
                TransportButton(
                    icon = Icons.Filled.AspectRatio,
                    label = resizeLabel,
                    size = 44.dp,
                    onClick = onCycleResizeMode,
                )
                TransportButton(
                    icon = Icons.Filled.ScreenRotation,
                    label = "Rotate screen",
                    size = 44.dp,
                    onClick = onToggleOrientation,
                )
                if (supportsPip) {
                    TransportButton(
                        icon = Icons.Filled.PictureInPictureAlt,
                        label = "Picture in Picture",
                        size = 44.dp,
                        onClick = onEnterPip,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(centerSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            if (episodeNav.show) {
                TransportButton(
                    icon = Icons.Filled.SkipPrevious,
                    label = Strings.get("player_previous_episode"),
                    size = epSize,
                    onClick = onPreviousEpisode,
                    enabled = episodeNav.hasPrevious,
                    modifier = Modifier.focusProperties { canFocus = episodeNav.hasPrevious }.alpha(if (episodeNav.hasPrevious) 1f else 0f),
                )
            }
            TransportButton(icon = Icons.Filled.Replay10, label = Strings.get("player_back_10s"), size = skipSize, onClick = { onSkip(-SEEK_STEP_MS) })
            TransportButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (isPlaying) Strings.get("action_pause") else Strings.get("action_play"),
                size = playSize,
                onClick = onPlayPause,
                modifier = Modifier.focusRequester(playPauseFocusRequester),
            )
            TransportButton(icon = Icons.Filled.Forward10, label = Strings.get("player_forward_10s"), size = skipSize, onClick = { onSkip(SEEK_STEP_MS) })
            if (episodeNav.show) {
                TransportButton(
                    icon = Icons.Filled.SkipNext,
                    label = Strings.get("player_next_episode"),
                    size = epSize,
                    onClick = onNextEpisode,
                    enabled = episodeNav.hasNext,
                    modifier = Modifier.focusProperties { canFocus = episodeNav.hasNext }.alpha(if (episodeNav.hasNext) 1f else 0f),
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(
                Brush.verticalGradient(listOf(Color.Transparent, JellioBg)),
            ).padding(horizontal = horizontalPad, vertical = if (isCompact) 18.dp else 32.dp),
        ) {
            Text(
                text = title,
                color = JellioText,
                fontSize = if (isCompact) 18.sp else 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = JellioText.copy(alpha = 0.85f),
                    fontSize = if (isCompact) 14.sp else 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onScrubEnd = onScrubEnd,
                onPlayPause = onPlayPause,
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(JellioBgElevated.copy(alpha = 0.82f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(999.dp)),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(6.dp),
                    ) {
                        PlayerPillButton(icon = Icons.Filled.Speed, label = speedLabel, onClick = onOpenSpeedMenu)
                        if (hasSubtitleTracks) {
                            PlayerPillButton(icon = Icons.Filled.ClosedCaption, label = Strings.get("player_subtitle_track"), onClick = onOpenSubtitleMenu)
                        }
                        if (hasAudioTracks) {
                            PlayerPillButton(icon = Icons.Filled.GraphicEq, label = Strings.get("player_audio_track"), onClick = onOpenAudioMenu)
                        }
                        if (hasSourceOptions) {
                            PlayerPillButton(icon = Icons.Filled.SwapHoriz, label = Strings.get("detail_streams"), onClick = onOpenSourceMenu)
                        }
                        if (hasEpisodes) {
                            PlayerPillButton(icon = Icons.Filled.VideoLibrary, label = Strings.get("detail_episodes"), onClick = onOpenEpisodesMenu)
                        }
                        PlayerPillButton(
                            icon = Icons.Filled.Bedtime,
                            label = Strings.get("settings_sleep_timer"),
                            active = sleepTimerActive,
                            onClick = onOpenSleepMenu,
                        )
                        PlayerPillButton(
                            icon = Icons.Filled.AspectRatio,
                            label = resizeLabel,
                            active = resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            onClick = onCycleResizeMode,
                        )
                        PlayerPillButton(
                            icon = Icons.Filled.LockOpen,
                            label = "Lock",
                            onClick = onToggleLock,
                        )
                        if (inGroup) {
                            PlayerPillButton(icon = Icons.Filled.EmojiEmotions, label = Strings.get("player_react"), onClick = onOpenReactMenu)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.35f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = CircleShape,
            )
        ),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = label, tint = JellioText, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
internal fun SeekBar(
    positionMs: () -> Long,
    durationMs: Long,
    onScrubEnd: (Long?) -> Unit,
    onPlayPause: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Long?>(null) }
    val shown = target ?: positionMs()
    val progress = if (durationMs > 0) (shown.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused && target != null) {
                    target = null
                    onScrubEnd(null)
                }
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (durationMs <= 0) return@onPreviewKeyEvent true
                        if (event.type == KeyEventType.KeyDown) {
                            val held = event.nativeKeyEvent.repeatCount
                            val step = when {
                                held > 40 -> 60_000L
                                held > 15 -> 30_000L
                                else -> SEEK_STEP_MS
                            }
                            val next = ((target ?: positionMs()) + if (event.key == Key.DirectionRight) step else -step).coerceIn(0L, durationMs)
                            target = next
                        } else if (event.type == KeyEventType.KeyUp) {
                            val landing = target
                            target = null
                            onScrubEnd(landing)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (event.type == KeyEventType.KeyUp) {
                            val landing = target
                            if (landing != null) {
                                target = null
                                onScrubEnd(landing)
                            } else {
                                onPlayPause()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val barHeight = if (focused || target != null) 8.dp else 4.dp
        var barWidthPx by remember { mutableIntStateOf(1) }
        var dragFraction by remember { mutableFloatStateOf(progress) }
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { barWidthPx = it.width.coerceAtLeast(1) }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            onScrubEnd((frac * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (durationMs > 0) {
                                dragFraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                target = (dragFraction * durationMs).toLong()
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (durationMs > 0) {
                                dragFraction = (dragFraction + dragAmount / barWidthPx).coerceIn(0f, 1f)
                                target = (dragFraction * durationMs).toLong()
                            }
                        },
                        onDragEnd = {
                            val landing = target
                            target = null
                            onScrubEnd(landing)
                        },
                        onDragCancel = {
                            target = null
                            onScrubEnd(null)
                        },
                    )
                },
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(barHeight).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(barHeight)
                        .background(if (focused || target != null) JellioSecondary else JellioText, RoundedCornerShape(4.dp)),
                )
            }
            if (focused || target != null) {
                Box(modifier = Modifier.fillMaxWidth(progress), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(text = com.jellio.android.ui.common.formatPlaybackTime(shown), color = if (target != null) JellioText else JellioTextSecondary)
            Text(text = " / " + com.jellio.android.ui.common.formatPlaybackTime(durationMs), color = JellioTextSecondary)
            if (focused && target == null) {
                Text(
                    text = "   " + Strings.get("player_scrub_hint"),
                    color = JellioTextSecondary,
                    style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
internal fun PlayerPillButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (active) JellioSecondary else JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = if (active) JellioSecondary else JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(999.dp),
            )
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label)
            Text(text = label, style = androidx.tv.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
