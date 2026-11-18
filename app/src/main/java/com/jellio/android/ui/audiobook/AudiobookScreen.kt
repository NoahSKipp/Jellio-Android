package com.jellio.android.ui.audiobook

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellio.android.data.books.AudiobookChapterDto
import com.jellio.android.data.offline.OfflineDownloadItem
import com.jellio.android.ui.common.ScreenSpinner
import com.jellio.android.ui.common.formatPlaybackTime
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioDanger
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.JellioTextTertiary

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private val SLEEP_PRESETS = listOf(
    0 to "Off",
    15 to "15 min",
    30 to "30 min",
    45 to "45 min",
    60 to "60 min",
    -1 to "End of chapter",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookScreen(
    itemId: String,
    controller: AudiobookPlayerController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()

    LaunchedEffect(itemId) {
        controller.loadAndPlayById(itemId)
    }

    BackHandler(onBack = onBack)

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JellioBg),
    ) {
        // Subtle ambient cover backdrop
        if (!state.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = state.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
                    .alpha(0.22f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                JellioBg.copy(alpha = 0.55f),
                                JellioBg.copy(alpha = 0.88f),
                                JellioBg,
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(JellioBgElevated.copy(alpha = 0.75f)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = JellioText,
                    )
                }

                Text(
                    text = "NOW LISTENING",
                    color = JellioTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                )

                OfflineDownloadActionButton(
                    download = state.offlineDownload,
                    onQueue = { controller.queueOfflineDownload() },
                    onPause = { controller.pauseOfflineDownload() },
                    onResume = { controller.resumeOfflineDownload() },
                    onDelete = { controller.deleteOfflineDownload() },
                )
            }

            when {
                state.isLoading && state.bookItem == null -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ScreenSpinner(size = 38.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Opening audiobook...",
                                color = JellioTextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                state.error != null && state.chapters.isEmpty() -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = state.error ?: "Unable to play audiobook",
                                color = JellioText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { controller.loadAndPlayById(itemId) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JellioSecondary,
                                    contentColor = Color(0xFF0D0D0D),
                                ),
                            ) {
                                Text("Retry", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // High-res cover art shadow card
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = JellioBgElevated,
                            shadowElevation = 24.dp,
                            modifier = Modifier
                                .widthIn(max = 310.dp)
                                .fillMaxWidth(0.76f)
                                .aspectRatio(1f)
                                .shadow(24.dp, RoundedCornerShape(22.dp))
                                .border(1.dp, JellioBorder.copy(alpha = 0.65f), RoundedCornerShape(22.dp)),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (!state.coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = state.coverUrl,
                                        contentDescription = state.bookItem?.Name ?: "Cover art",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Headphones,
                                        contentDescription = null,
                                        tint = JellioTextTertiary,
                                        modifier = Modifier.size(64.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Book title & author
                        Text(
                            text = state.bookItem?.Name ?: "Audiobook",
                            color = JellioText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (state.author.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.author,
                                color = JellioTextSecondary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Current chapter pill button
                        val currentChapter = state.chapters.getOrNull(state.currentChapterIndex)
                        val chapterLabel = currentChapter?.Title?.takeIf { it.isNotBlank() }
                            ?: "Chapter ${state.currentChapterIndex + 1}"
                        Surface(
                            onClick = { showChaptersSheet = true },
                            color = JellioBgElevated.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .border(1.dp, JellioBorder, RoundedCornerShape(999.dp)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    tint = JellioTextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = chapterLabel,
                                    color = JellioText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 240.dp),
                                )
                            }
                        }

                        if (state.error != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.error ?: "",
                                color = JellioDanger,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Overall Book Timeline Scrubber
                        BookTimelineScrubber(
                            currentPositionMs = state.currentPositionMs,
                            totalDurationMs = state.totalDurationMs,
                            onSeek = { controller.seekToBookPositionMs(it) },
                        )

                        // Current Chapter Progress indicator
                        if (currentChapter != null && currentChapter.DurationSeconds > 0.0) {
                            val chStartMs = (currentChapter.StartSeconds * 1000.0).toLong()
                            val chDurMs = (currentChapter.DurationSeconds * 1000.0).toLong().coerceAtLeast(1L)
                            val chElapsedMs = (state.currentPositionMs - chStartMs).coerceIn(0L, chDurMs)
                            val chFraction = (chElapsedMs.toFloat() / chDurMs.toFloat()).coerceIn(0f, 1f)

                            Spacer(modifier = Modifier.height(10.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "Chapter ${state.currentChapterIndex + 1} of ${state.chapters.size}",
                                        color = JellioTextTertiary,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        text = "${formatPlaybackTime(chElapsedMs)} / ${formatPlaybackTime(chDurMs)}",
                                        color = JellioTextTertiary,
                                        fontSize = 11.sp,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { chFraction },
                                    color = JellioSecondary.copy(alpha = 0.7f),
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Main Transport Controls: Prev Chapter / -15s / Play-Pause / +30s / Next Chapter
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IconButton(
                                onClick = { controller.previousChapter() },
                                enabled = state.chapters.isNotEmpty(),
                                modifier = Modifier.size(46.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Previous chapter",
                                    tint = JellioText,
                                    modifier = Modifier.size(30.dp),
                                )
                            }

                            IconButton(
                                onClick = { controller.skipBackward15s() },
                                modifier = Modifier.size(52.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Replay10,
                                        contentDescription = "Skip back 15 seconds",
                                        tint = JellioText,
                                        modifier = Modifier.size(34.dp),
                                    )
                                    Text(
                                        text = "15",
                                        color = JellioText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(72.dp)
                                    .shadow(12.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(JellioSecondary)
                                    .clickable { controller.togglePlayPause() },
                            ) {
                                if (state.isBuffering) {
                                    ScreenSpinner(size = 28.dp)
                                } else {
                                    Icon(
                                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                                        tint = Color(0xFF0D0D0D),
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                            }

                            IconButton(
                                onClick = { controller.skipForward30s() },
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward30,
                                    contentDescription = "Skip forward 30 seconds",
                                    tint = JellioText,
                                    modifier = Modifier.size(34.dp),
                                )
                            }

                            IconButton(
                                onClick = { controller.nextChapter() },
                                enabled = state.currentChapterIndex < state.chapters.lastIndex,
                                modifier = Modifier.size(46.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Next chapter",
                                    tint = if (state.currentChapterIndex < state.chapters.lastIndex) {
                                        JellioText
                                    } else {
                                        JellioTextTertiary
                                    },
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Secondary Action Bar: Speed / Sleep Timer / Chapters
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SecondaryActionPill(
                                icon = Icons.Filled.Speed,
                                label = formatSpeedLabel(state.playbackSpeed),
                                active = state.playbackSpeed != 1.0f,
                                onClick = { showSpeedSheet = true },
                                modifier = Modifier.weight(1f),
                            )

                            val sleepActive = state.sleepStopAtChapterEnd || state.sleepTimerRemainingSec != null
                            val sleepLabel = when {
                                state.sleepStopAtChapterEnd -> "Chapter end"
                                state.sleepTimerRemainingSec != null -> formatSleepCountdown(state.sleepTimerRemainingSec!!)
                                else -> "Sleep"
                            }
                            SecondaryActionPill(
                                icon = Icons.Filled.Bedtime,
                                label = sleepLabel,
                                active = sleepActive,
                                onClick = { showSleepSheet = true },
                                modifier = Modifier.weight(1f),
                            )

                            SecondaryActionPill(
                                icon = Icons.AutoMirrored.Filled.List,
                                label = "Chapters (${state.chapters.size})",
                                active = false,
                                onClick = { showChaptersSheet = true },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    if (showSpeedSheet) {
        SpeedSelectorSheet(
            currentSpeed = state.playbackSpeed,
            onSelect = { speed ->
                controller.setSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false },
        )
    }

    if (showSleepSheet) {
        SleepTimerSheet(
            remainingSec = state.sleepTimerRemainingSec,
            stopAtChapterEnd = state.sleepStopAtChapterEnd,
            onSelect = { minutes ->
                controller.setSleepTimer(minutes)
                showSleepSheet = false
            },
            onDismiss = { showSleepSheet = false },
        )
    }

    if (showChaptersSheet) {
        ChaptersBottomSheet(
            chapters = state.chapters,
            currentChapterIndex = state.currentChapterIndex,
            onSelectChapter = { idx ->
                controller.selectChapter(idx)
                showChaptersSheet = false
            },
            onDismiss = { showChaptersSheet = false },
        )
    }
}

@Composable
private fun BookTimelineScrubber(
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragRatio by remember { mutableFloatStateOf(0f) }

    val safeDuration = totalDurationMs.coerceAtLeast(1L)
    val actualRatio = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val shownRatio = if (dragging) dragRatio else actualRatio
    val shownPositionMs = if (dragging) (dragRatio * safeDuration).toLong() else currentPositionMs
    val remainingMs = (totalDurationMs - shownPositionMs).coerceAtLeast(0L)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = shownRatio,
            onValueChange = { value ->
                dragging = true
                dragRatio = value.coerceIn(0f, 1f)
            },
            onValueChangeFinished = {
                val seekTarget = (dragRatio * safeDuration).toLong()
                dragging = false
                onSeek(seekTarget)
            },
            enabled = totalDurationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = JellioSecondary,
                activeTrackColor = JellioSecondary,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = formatPlaybackTime(shownPositionMs),
                color = JellioTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "-${formatPlaybackTime(remainingMs)}",
                color = JellioTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SecondaryActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = if (active) JellioSecondary else JellioBgElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(46.dp)
            .border(
                width = 1.dp,
                color = if (active) JellioSecondary else JellioBorder,
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) Color(0xFF0D0D0D) else JellioTextSecondary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active) Color(0xFF0D0D0D) else JellioText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OfflineDownloadActionButton(
    download: OfflineDownloadItem?,
    onQueue: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    when (download?.status) {
        OfflineDownloadItem.STATUS_COMPLETED -> {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(JellioBgElevated.copy(alpha = 0.75f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Downloaded offline (tap to remove)",
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        OfflineDownloadItem.STATUS_DOWNLOADING, OfflineDownloadItem.STATUS_QUEUED -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(JellioBgElevated.copy(alpha = 0.75f))
                    .clickable(onClick = onPause),
            ) {
                CircularProgressIndicator(
                    progress = { download.progress.coerceIn(0.05f, 1f) },
                    color = JellioSecondary,
                    trackColor = JellioBorder,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp),
                )
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Pause download",
                    tint = JellioText,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        OfflineDownloadItem.STATUS_PAUSED, OfflineDownloadItem.STATUS_FAILED -> {
            IconButton(
                onClick = onResume,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(JellioBgElevated.copy(alpha = 0.75f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Resume offline download",
                    tint = if (download.status == OfflineDownloadItem.STATUS_FAILED) JellioDanger else JellioSecondary,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        else -> {
            IconButton(
                onClick = onQueue,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(JellioBgElevated.copy(alpha = 0.75f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Download audiobook offline",
                    tint = JellioText,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSelectorSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JellioBgElevated,
        contentColor = JellioText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Playback Speed",
                color = JellioText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            SPEED_OPTIONS.forEach { speed ->
                val selected = kotlin.math.abs(speed - currentSpeed) < 0.01f
                SheetOptionRow(
                    label = formatSpeedLabel(speed),
                    selected = selected,
                    onClick = { onSelect(speed) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    remainingSec: Int?,
    stopAtChapterEnd: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JellioBgElevated,
        contentColor = JellioText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Sleep Timer",
                color = JellioText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            if (remainingSec != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatSleepCountdown(remainingSec)} remaining (fades out gently)",
                    color = JellioTextSecondary,
                    fontSize = 13.sp,
                )
            } else if (stopAtChapterEnd) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Will pause at the end of this chapter",
                    color = JellioTextSecondary,
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            SLEEP_PRESETS.forEach { (minutes, label) ->
                val selected = when (minutes) {
                    0 -> !stopAtChapterEnd && remainingSec == null
                    -1 -> stopAtChapterEnd
                    else -> false
                }
                SheetOptionRow(
                    label = label,
                    selected = selected,
                    onClick = { onSelect(minutes) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersBottomSheet(
    chapters: List<AudiobookChapterDto>,
    currentChapterIndex: Int,
    onSelectChapter: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val indexedChapters = remember(chapters, query) {
        val trimmed = query.trim()
        chapters.mapIndexed { index, ch -> index to ch }.filter { (_, ch) ->
            trimmed.isEmpty() || ch.Title.contains(trimmed, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JellioBgElevated,
        contentColor = JellioText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "Chapters (${chapters.size})",
                color = JellioText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search chapters...", color = JellioTextTertiary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = JellioTextSecondary,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = JellioTextSecondary,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = JellioText,
                    unfocusedTextColor = JellioText,
                    focusedBorderColor = JellioSecondary,
                    unfocusedBorderColor = JellioBorder,
                    focusedContainerColor = JellioBg,
                    unfocusedContainerColor = JellioBg,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(indexedChapters, key = { _, pair -> pair.first }) { _, (index, chapter) ->
                    val isCurrent = index == currentChapterIndex
                    val durMs = (chapter.DurationSeconds * 1000.0).toLong()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) JellioSecondary.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onSelectChapter(index) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "${index + 1}.",
                            color = if (isCurrent) JellioSecondary else JellioTextTertiary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(34.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chapter.Title.ifBlank { "Chapter ${index + 1}" },
                                color = if (isCurrent) JellioSecondary else JellioText,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (durMs > 0L) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatPlaybackTime(durMs),
                                    color = JellioTextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        if (isCurrent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Current chapter",
                                tint = JellioSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = JellioBorder.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
private fun SheetOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) JellioSecondary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            text = label,
            color = if (selected) JellioSecondary else JellioText,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = JellioSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun formatSpeedLabel(speed: Float): String {
    val rounded = (speed * 100f).toInt()
    return when {
        rounded % 100 == 0 -> "${rounded / 100}.0x"
        rounded % 10 == 0 -> "${rounded / 100}.${(rounded % 100) / 10}x"
        else -> "${speed}x"
    }
}

private fun formatSleepCountdown(totalSec: Int): String {
    val mins = totalSec / 60
    val secs = totalSec % 60
    return if (mins > 0) "%d:%02d".format(mins, secs) else "${secs}s"
}
