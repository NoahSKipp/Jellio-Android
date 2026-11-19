package com.jellio.android.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jellio.android.data.offline.OfflineDownloadItem
import com.jellio.android.ui.common.ScreenSpinner
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioDanger
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.JellioTextTertiary
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayOfflineVideo: (OfflineDownloadItem) -> Unit,
    onReadOfflineBook: (OfflineDownloadItem) -> Unit,
    onListenOfflineAudiobook: (OfflineDownloadItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JellioBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header
        val isCompact = com.jellio.android.ui.common.isCompactScreen()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (!isCompact) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(JellioBgElevated),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = JellioText,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Offline Downloads",
                    color = JellioText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${uiState.allItems.size} ${if (uiState.allItems.size == 1) "item" else "items"} stored",
                    color = JellioTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        // Filter Chips
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(DownloadsFilter.entries.size) { idx ->
                val filter = DownloadsFilter.entries[idx]
                val selected = uiState.filter == filter
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setFilter(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = JellioBgElevated,
                        labelColor = JellioTextSecondary,
                        selectedContainerColor = JellioSecondary,
                        selectedLabelColor = Color(0xFF0D0D0D),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = JellioBorder,
                        selectedBorderColor = JellioSecondary,
                    ),
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Storage Summary & Queue Controls Card
            item(key = "storage_summary") {
                StorageSummaryCard(
                    uiState = uiState,
                    onSyncNow = { viewModel.syncOfflineProgressNow() },
                    onPauseAll = { viewModel.pauseAll() },
                    onResumeAll = { viewModel.resumeAll() },
                    onToggleAutoDelete = { viewModel.setAutoDeleteCompleted(it) },
                )
            }

            if (uiState.mangaGroups.isEmpty() && uiState.filteredItems.isEmpty()) {
                item(key = "empty_state") {
                    EmptyDownloadsState(filter = uiState.filter)
                }
            } else {
                items(
                    items = uiState.mangaGroups,
                    key = { "manga_group_${it.groupKey}" },
                ) { group ->
                    val expanded = expandedGroups[group.groupKey] ?: true
                    MangaSeriesDownloadCard(
                        group = group,
                        expanded = expanded,
                        onToggleExpand = { expandedGroups[group.groupKey] = !expanded },
                        onDeleteGroup = { viewModel.deleteMangaGroup(group) },
                        onOpenChapter = onReadOfflineBook,
                        onPause = { viewModel.pauseDownload(it) },
                        onResume = { viewModel.resumeDownload(it) },
                        onCancelOrDelete = { item ->
                            if (item.status == OfflineDownloadItem.STATUS_COMPLETED) {
                                viewModel.deleteDownload(item.id)
                            } else {
                                viewModel.cancelDownload(item.id)
                            }
                        },
                    )
                }

                items(
                    items = uiState.filteredItems,
                    key = { "item_${it.id}" },
                ) { item ->
                    DownloadItemCard(
                        item = item,
                        onOpenOffline = {
                            when (item.kind) {
                                "video" -> onPlayOfflineVideo(item)
                                "audiobook" -> onListenOfflineAudiobook(item)
                                else -> onReadOfflineBook(item)
                            }
                        },
                        onPause = { viewModel.pauseDownload(item.id) },
                        onResume = { viewModel.resumeDownload(item.id) },
                        onCancelOrDelete = {
                            if (item.status == OfflineDownloadItem.STATUS_COMPLETED) {
                                viewModel.deleteDownload(item.id)
                            } else {
                                viewModel.cancelDownload(item.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(
    uiState: DownloadsUiState,
    onSyncNow: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onToggleAutoDelete: (Boolean) -> Unit,
) {
    Surface(
        color = JellioBgElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JellioBorder, RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(JellioSecondary.copy(alpha = 0.12f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = JellioSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatBytes(uiState.totalDiskBytes),
                        color = JellioText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val queueSummary = when {
                        uiState.activeCount > 0 && uiState.pausedCount > 0 ->
                            "${uiState.activeCount} active/queued, ${uiState.pausedCount} paused"
                        uiState.activeCount > 0 ->
                            "${uiState.activeCount} ${if (uiState.activeCount == 1) "download" else "downloads"} active or queued"
                        uiState.pausedCount > 0 ->
                            "${uiState.pausedCount} paused or failed"
                        else -> "No active downloads"
                    }
                    Text(
                        text = queueSummary,
                        color = JellioTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onSyncNow,
                    enabled = !uiState.isSyncingQueue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JellioSecondary,
                        contentColor = Color(0xFF0D0D0D),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (uiState.isSyncingQueue) {
                        ScreenSpinner(size = 16.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isSyncingQueue) "Syncing..." else "Sync Offline Progress Now",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (uiState.activeCount > 0 || uiState.pausedCount > 0) {
                    val showPauseAll = uiState.hasActiveOrQueued
                    OutlinedButton(
                        onClick = if (showPauseAll) onPauseAll else onResumeAll,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, JellioBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioText),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = if (showPauseAll) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showPauseAll) "Pause All" else "Resume All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            if (!uiState.syncStatusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.syncStatusMessage,
                    color = JellioTextSecondary,
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = JellioBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Auto-delete completed (>= 98%)",
                        color = JellioText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Remove finished videos, books, and audiobooks automatically",
                        color = JellioTextSecondary,
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = uiState.autoDeleteCompleted,
                    onCheckedChange = onToggleAutoDelete,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF0D0D0D),
                        checkedTrackColor = JellioSecondary,
                        uncheckedThumbColor = JellioTextSecondary,
                        uncheckedTrackColor = JellioBg,
                        uncheckedBorderColor = JellioBorder,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MangaSeriesDownloadCard(
    group: MangaSeriesDownloadGroup,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDeleteGroup: () -> Unit,
    onOpenChapter: (OfflineDownloadItem) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancelOrDelete: (OfflineDownloadItem) -> Unit,
) {
    Surface(
        color = JellioBgElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JellioBorder, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(12.dp),
            ) {
                OfflineCoverThumbnail(
                    coverPath = group.coverPath,
                    fallbackIcon = Icons.Filled.CollectionsBookmark,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        color = JellioText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val chCount = group.chapters.size
                    val unit = if (chCount == 1) "chapter" else "chapters"
                    val statusSuffix = if (group.activeCount > 0) " · ${group.activeCount} active" else ""
                    Text(
                        text = "$chCount $unit · ${formatBytes(group.totalBytes)}$statusSuffix",
                        color = JellioTextSecondary,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onDeleteGroup) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Delete series downloads",
                        tint = JellioTextSecondary,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = JellioTextSecondary,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    HorizontalDivider(color = JellioBorder.copy(alpha = 0.55f))
                    Spacer(modifier = Modifier.height(8.dp))
                    group.chapters.forEachIndexed { idx, chapter ->
                        MangaChapterRow(
                            item = chapter,
                            onRead = { onOpenChapter(chapter) },
                            onPause = { onPause(chapter.id) },
                            onResume = { onResume(chapter.id) },
                            onCancelOrDelete = { onCancelOrDelete(chapter) },
                        )
                        if (idx < group.chapters.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaChapterRow(
    item: OfflineDownloadItem,
    onRead: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelOrDelete: () -> Unit,
) {
    val isCompleted = item.status == OfflineDownloadItem.STATUS_COMPLETED
    val isDownloading = item.status == OfflineDownloadItem.STATUS_DOWNLOADING ||
        item.status == OfflineDownloadItem.STATUS_QUEUED
    val isPausedOrFailed = item.status == OfflineDownloadItem.STATUS_PAUSED ||
        item.status == OfflineDownloadItem.STATUS_FAILED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JellioBg.copy(alpha = 0.7f))
            .border(1.dp, JellioBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = JellioText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusLabel(item),
                    color = if (item.status == OfflineDownloadItem.STATUS_FAILED) JellioDanger else JellioTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isCompleted) {
                Surface(
                    onClick = onRead,
                    color = JellioSecondary,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = "Read",
                        color = Color(0xFF0D0D0D),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else if (isDownloading) {
                IconButton(onClick = onPause, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pause",
                        tint = JellioText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else if (isPausedOrFailed) {
                IconButton(onClick = onResume, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Resume",
                        tint = JellioSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            IconButton(onClick = onCancelOrDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = if (isCompleted) "Delete" else "Cancel",
                    tint = JellioTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (!isCompleted) {
            Spacer(modifier = Modifier.height(6.dp))
            DownloadProgressBar(item = item)
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: OfflineDownloadItem,
    onOpenOffline: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelOrDelete: () -> Unit,
) {
    val isCompleted = item.status == OfflineDownloadItem.STATUS_COMPLETED
    val isDownloading = item.status == OfflineDownloadItem.STATUS_DOWNLOADING ||
        item.status == OfflineDownloadItem.STATUS_QUEUED
    val isPausedOrFailed = item.status == OfflineDownloadItem.STATUS_PAUSED ||
        item.status == OfflineDownloadItem.STATUS_FAILED

    val fallbackIcon = when (item.kind) {
        "video" -> Icons.Filled.Movie
        "audiobook" -> Icons.Filled.Headphones
        "manga" -> Icons.Filled.CollectionsBookmark
        else -> Icons.AutoMirrored.Filled.MenuBook
    }
    val actionLabel = when (item.kind) {
        "video" -> "Play Offline"
        "audiobook" -> "Listen Offline"
        else -> "Read Offline"
    }

    Surface(
        color = JellioBgElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JellioBorder, RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OfflineCoverThumbnail(
                    coverPath = item.coverPath,
                    fallbackIcon = fallbackIcon,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = JellioText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            color = JellioTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = statusLabel(item),
                        color = if (item.status == OfflineDownloadItem.STATUS_FAILED) JellioDanger else JellioTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isDownloading) {
                    IconButton(onClick = onPause) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "Pause download",
                            tint = JellioText,
                        )
                    }
                } else if (isPausedOrFailed) {
                    IconButton(onClick = onResume) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Resume download",
                            tint = JellioSecondary,
                        )
                    }
                }

                IconButton(onClick = onCancelOrDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = if (isCompleted) "Delete download" else "Cancel download",
                        tint = JellioTextSecondary,
                    )
                }
            }

            if (!isCompleted) {
                Spacer(modifier = Modifier.height(10.dp))
                DownloadProgressBar(item = item)
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenOffline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JellioSecondary,
                        contentColor = Color(0xFF0D0D0D),
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = when (item.kind) {
                            "video" -> Icons.Filled.PlayArrow
                            "audiobook" -> Icons.Filled.Headphones
                            else -> Icons.AutoMirrored.Filled.MenuBook
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = actionLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressBar(item: OfflineDownloadItem) {
    val pct = (item.progress.coerceIn(0f, 1f) * 100f).roundToInt()
    Column(modifier = Modifier.fillMaxWidth()) {
        if (item.status == OfflineDownloadItem.STATUS_DOWNLOADING && item.totalBytes <= 0L && item.progress <= 0f) {
            LinearProgressIndicator(
                color = JellioSecondary,
                trackColor = Color.White.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        } else {
            LinearProgressIndicator(
                progress = { item.progress.coerceIn(0f, 1f) },
                color = if (item.status == OfflineDownloadItem.STATUS_FAILED) JellioDanger else JellioSecondary,
                trackColor = Color.White.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val byteCounter = if (item.totalBytes > 0L) {
                "${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)}"
            } else {
                formatBytes(item.bytesDownloaded)
            }
            Text(
                text = byteCounter,
                color = JellioTextTertiary,
                fontSize = 11.sp,
            )
            Text(
                text = "$pct%",
                color = JellioTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun OfflineCoverThumbnail(
    coverPath: String?,
    fallbackIcon: ImageVector,
) {
    val file = coverPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 52.dp, height = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, JellioBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
    ) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = JellioTextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun EmptyDownloadsState(filter: DownloadsFilter) {
    Surface(
        color = JellioBgElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JellioBorder, RoundedCornerShape(16.dp)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
        ) {
            Icon(
                imageVector = if (filter == DownloadsFilter.ALL) Icons.Filled.DownloadForOffline else Icons.Filled.CloudDone,
                contentDescription = null,
                tint = JellioTextTertiary,
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (filter == DownloadsFilter.ALL) "No offline downloads yet" else "No ${filter.label.lowercase(Locale.US)} downloads",
                color = JellioText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Downloaded movies, shows, books, manga, and audiobooks will appear here.",
                color = JellioTextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

private fun statusLabel(item: OfflineDownloadItem): String = when (item.status) {
    OfflineDownloadItem.STATUS_COMPLETED -> {
        val size = formatBytes(if (item.totalBytes > 0L) item.totalBytes else item.bytesDownloaded)
        val quality = item.videoQuality?.takeIf { item.kind == "video" && it.isNotBlank() }?.let { " · $it" }.orEmpty()
        "Downloaded · $size$quality"
    }
    OfflineDownloadItem.STATUS_DOWNLOADING -> "Downloading..."
    OfflineDownloadItem.STATUS_QUEUED -> item.errorMessage?.takeIf { it.isNotBlank() } ?: "Queued"
    OfflineDownloadItem.STATUS_PAUSED -> "Paused"
    OfflineDownloadItem.STATUS_FAILED -> item.errorMessage?.takeIf { it.isNotBlank() } ?: "Download failed"
    else -> item.status
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024.0 && idx < units.lastIndex) {
        value /= 1024.0
        idx++
    }
    return if (idx == 0) {
        "${bytes} B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[idx])
    }
}
