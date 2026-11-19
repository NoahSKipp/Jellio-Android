package com.jellio.android.ui.audiobook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellio.android.ui.common.ScreenSpinner
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating glassmorphic audiobook mini-player pill that docks cleanly above
 * the mobile bottom navigation bar whenever an audiobook session is active.
 */
@Composable
fun AudiobookMiniPlayer(
    controller: AudiobookPlayerController,
    onExpand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val book = state.bookItem

    AnimatedVisibility(
        visible = state.isActive && book != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        if (book == null) return@AnimatedVisibility
        val currentChapter = state.chapters.getOrNull(state.currentChapterIndex)
        val subtitleText = currentChapter?.Title?.takeIf { it.isNotBlank() }
            ?: state.author.takeIf { it.isNotBlank() }
            ?: "Audiobook"

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val density = LocalDensity.current
        val dismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }
        val offsetX = remember(book.Id) { Animatable(0f) }

        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .alpha((1f - (abs(offsetX.value) / (dismissThresholdPx * 2.2f))).coerceIn(0.25f, 1f))
                .pointerInput(book.Id, dismissThresholdPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(offsetX.value) >= dismissThresholdPx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val target = if (offsetX.value > 0f) dismissThresholdPx * 3f else -dismissThresholdPx * 3f
                                    offsetX.animateTo(target, tween(160))
                                    controller.stopAndDismiss()
                                } else {
                                    offsetX.animateTo(0f, spring())
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, spring())
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount)
                            }
                        },
                    )
                }
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            JellioBgElevated.copy(alpha = 0.94f),
                            Color(0xFF121212).copy(alpha = 0.96f),
                        ),
                    ),
                )
                .border(1.dp, JellioBorder.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                .clickable { onExpand(book.Id) },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    ) {
                        if (!state.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = state.coverUrl,
                                contentDescription = book.Name ?: "Audiobook cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Headphones,
                                contentDescription = null,
                                tint = JellioTextSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.Name ?: "Audiobook",
                            color = JellioText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitleText,
                            color = JellioTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // -15s skip back
                    IconButton(
                        onClick = { controller.skipBackward15s() },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Replay,
                                contentDescription = "Rewind 15 seconds",
                                tint = JellioTextSecondary,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = "15",
                                color = JellioText,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    // Play / Pause pill button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(JellioSecondary)
                            .clickable { controller.togglePlayPause() },
                    ) {
                        if (state.isLoading || state.isBuffering) {
                            ScreenSpinner(size = 18.dp)
                        } else {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color(0xFF0D0D0D),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    // Dismiss / Stop X button
                    IconButton(
                        onClick = { controller.stopAndDismiss() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close audiobook player",
                            tint = JellioTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Thin bottom progress bar isolated from outer mini-player recomposition
                MiniPlayerProgressBar(
                    positionFlow = controller.positionMs,
                    totalDurationMs = state.totalDurationMs,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerProgressBar(
    positionFlow: StateFlow<Long>,
    totalDurationMs: Long,
) {
    val positionMs by positionFlow.collectAsState()
    val progress = if (totalDurationMs > 0L) {
        (positionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = progress)
                .height(2.5.dp)
                .background(JellioSecondary),
        )
    }
}
