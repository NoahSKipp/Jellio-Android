package com.jellio.android.ui.downloads

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.android.data.offline.OfflineDownloadItem
import com.jellio.android.data.offline.OfflineDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DownloadsFilter(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    BOOKS("Books"),
    MANGA("Manga"),
    AUDIOBOOKS("Audiobooks"),
    ACTIVE("Active"),
}

@Immutable
data class MangaSeriesDownloadGroup(
    val groupKey: String,
    val title: String,
    val coverPath: String?,
    val chapters: List<OfflineDownloadItem>,
    val totalBytes: Long,
    val activeCount: Int,
)

@Immutable
data class DownloadsUiState(
    val filter: DownloadsFilter = DownloadsFilter.ALL,
    val allItems: List<OfflineDownloadItem> = emptyList(),
    val filteredItems: List<OfflineDownloadItem> = emptyList(),
    val mangaGroups: List<MangaSeriesDownloadGroup> = emptyList(),
    val totalDiskBytes: Long = 0L,
    val activeCount: Int = 0,
    val pausedCount: Int = 0,
    val hasActiveOrQueued: Boolean = false,
    val autoDeleteCompleted: Boolean = true,
    val isSyncingQueue: Boolean = false,
    val syncStatusMessage: String? = null,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val offlineDownloadManager: OfflineDownloadManager,
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(DownloadsFilter.ALL)
    private val autoDeleteState = MutableStateFlow(
        offlineDownloadManager.isAutoDeleteWatchedEnabled() && offlineDownloadManager.isAutoDeleteReadEnabled(),
    )
    private val syncingState = MutableStateFlow(false)
    private val syncMessageState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DownloadsUiState> = combine(
        offlineDownloadManager.downloads,
        selectedFilter,
        autoDeleteState,
        syncingState,
        syncMessageState,
    ) { items, filter, autoDelete, isSyncing, syncMsg ->
        val totalDiskBytes = items.sumOf { entry ->
            when {
                entry.totalBytes > 0L -> entry.totalBytes
                else -> entry.bytesDownloaded
            }.coerceAtLeast(0L)
        }
        val activeCount = items.count {
            it.status == OfflineDownloadItem.STATUS_DOWNLOADING ||
                it.status == OfflineDownloadItem.STATUS_QUEUED
        }
        val pausedCount = items.count {
            it.status == OfflineDownloadItem.STATUS_PAUSED ||
                it.status == OfflineDownloadItem.STATUS_FAILED
        }

        val filtered = when (filter) {
            DownloadsFilter.ALL -> items
            DownloadsFilter.VIDEO -> items.filter { it.kind == "video" }
            DownloadsFilter.BOOKS -> items.filter { it.kind == "book" }
            DownloadsFilter.MANGA -> items.filter { it.kind == "manga" }
            DownloadsFilter.AUDIOBOOKS -> items.filter { it.kind == "audiobook" }
            DownloadsFilter.ACTIVE -> items.filter {
                it.status == OfflineDownloadItem.STATUS_DOWNLOADING ||
                    it.status == OfflineDownloadItem.STATUS_QUEUED ||
                    it.status == OfflineDownloadItem.STATUS_PAUSED ||
                    it.status == OfflineDownloadItem.STATUS_FAILED
            }
        }

        val mangaItems = filtered.filter { it.kind == "manga" }
        val nonMangaItems = filtered.filterNot { it.kind == "manga" }

        val mangaGroups = mangaItems
            .groupBy { item ->
                val rawKey = item.seriesKey?.takeIf { it.isNotBlank() }
                    ?: item.subtitle.takeIf { it.isNotBlank() }
                    ?: item.title
                rawKey.lowercase()
            }
            .map { (key, chapters) ->
                val sorted = chapters.sortedWith(
                    compareBy<OfflineDownloadItem> { extractFirstNumber(it.title) }
                        .thenBy { it.title },
                )
                val first = sorted.first()
                val title = first.subtitle.takeIf { it.isNotBlank() } ?: first.title
                val coverPath = sorted.firstNotNullOfOrNull { it.coverPath?.takeIf(String::isNotBlank) }
                val groupBytes = sorted.sumOf {
                    (if (it.totalBytes > 0L) it.totalBytes else it.bytesDownloaded).coerceAtLeast(0L)
                }
                val busy = sorted.count {
                    it.status == OfflineDownloadItem.STATUS_DOWNLOADING ||
                        it.status == OfflineDownloadItem.STATUS_QUEUED
                }
                MangaSeriesDownloadGroup(
                    groupKey = key,
                    title = title,
                    coverPath = coverPath,
                    chapters = sorted,
                    totalBytes = groupBytes,
                    activeCount = busy,
                )
            }
            .sortedBy { it.title.lowercase() }

        DownloadsUiState(
            filter = filter,
            allItems = items,
            filteredItems = nonMangaItems,
            mangaGroups = mangaGroups,
            totalDiskBytes = totalDiskBytes,
            activeCount = activeCount,
            pausedCount = pausedCount,
            hasActiveOrQueued = activeCount > 0,
            autoDeleteCompleted = autoDelete,
            isSyncingQueue = isSyncing,
            syncStatusMessage = syncMsg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsUiState(
            autoDeleteCompleted = autoDeleteState.value,
        ),
    )

    fun setFilter(filter: DownloadsFilter) {
        selectedFilter.value = filter
    }

    fun setAutoDeleteCompleted(enabled: Boolean) {
        offlineDownloadManager.setAutoDeleteWatchedEnabled(enabled)
        offlineDownloadManager.setAutoDeleteReadEnabled(enabled)
        autoDeleteState.value = enabled
    }

    fun pauseDownload(itemId: String) {
        viewModelScope.launch {
            offlineDownloadManager.pauseDownload(itemId)
        }
    }

    fun resumeDownload(itemId: String) {
        viewModelScope.launch {
            offlineDownloadManager.resumeDownload(itemId)
        }
    }

    fun cancelDownload(itemId: String) {
        viewModelScope.launch {
            offlineDownloadManager.cancelDownload(itemId)
        }
    }

    fun deleteDownload(itemId: String) {
        viewModelScope.launch {
            offlineDownloadManager.deleteDownload(itemId)
        }
    }

    fun deleteMangaGroup(group: MangaSeriesDownloadGroup) {
        viewModelScope.launch {
            for (chapter in group.chapters) {
                offlineDownloadManager.deleteDownload(chapter.id)
            }
        }
    }

    fun pauseAll() {
        viewModelScope.launch {
            val active = offlineDownloadManager.downloads.value.filter {
                it.status == OfflineDownloadItem.STATUS_DOWNLOADING ||
                    it.status == OfflineDownloadItem.STATUS_QUEUED
            }
            for (item in active) {
                offlineDownloadManager.pauseDownload(item.id)
            }
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            val resumable = offlineDownloadManager.downloads.value.filter {
                it.status == OfflineDownloadItem.STATUS_PAUSED ||
                    it.status == OfflineDownloadItem.STATUS_FAILED
            }
            for (item in resumable) {
                offlineDownloadManager.resumeDownload(item.id)
            }
        }
    }

    fun syncOfflineProgressNow() {
        if (syncingState.value) return
        viewModelScope.launch {
            syncingState.value = true
            syncMessageState.value = null
            val count = runCatching { offlineDownloadManager.replaySyncQueue() }.getOrDefault(-1)
            syncingState.value = false
            syncMessageState.value = when {
                count < 0 -> "Sync failed. Check server connection."
                count == 0 -> "All offline progress is up to date."
                count == 1 -> "Synced 1 offline progress update."
                else -> "Synced $count offline progress updates."
            }
        }
    }

    fun clearSyncMessage() {
        syncMessageState.value = null
    }

    private fun extractFirstNumber(raw: String): Double {
        val match = NUMBER_REGEX.find(raw) ?: return Double.MAX_VALUE
        return match.value.toDoubleOrNull() ?: Double.MAX_VALUE
    }

    companion object {
        private val NUMBER_REGEX = Regex("\\d+(?:\\.\\d+)?")
    }
}
