package com.jellio.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.android.data.CardMenuRepository
import com.jellio.android.data.JellioRepository
import com.jellio.android.data.catching
import com.jellio.android.data.i18n.Strings
import com.jellio.android.data.isUnreleasedByDate
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.normalizeGuid
import com.jellio.android.data.offline.OfflineDownloadItem
import com.jellio.android.data.offline.OfflineDownloadManager
import com.jellio.android.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardMenuState(
    val grouplistEnabled: Boolean = false,
    // Normalized item ids, see normalizeGuid.
    val grouplistIds: Set<String> = emptySet(),
    val isAdmin: Boolean = false,
    // The admin switch in the plugin settings for the skip timestamp entries.
    val skipMenuEnabled: Boolean = false,
)

// Activity-scoped, so every screen's options menu shares what it has read.
@HiltViewModel
class CardMenuViewModel @Inject constructor(
    private val menu: CardMenuRepository,
    private val repository: JellioRepository,
    private val offline: OfflineDownloadManager,
) : ViewModel() {
    private val _state = MutableStateFlow(CardMenuState())
    val state: StateFlow<CardMenuState> = _state.asStateFlow()

    private var loadedFor: String? = null
    private var loadedAt = 0L

    // Read when a menu opens, at most every couple of minutes, so a switch
    // made on the web shows up without a restart.
    fun load(session: Session) {
        val now = System.currentTimeMillis()
        if (loadedFor == session.userId && now - loadedAt < RELOAD_MS) return
        if (loadedFor != session.userId) _state.value = CardMenuState()
        loadedFor = session.userId
        loadedAt = now
        viewModelScope.launch {
            val enabled = menu.isGrouplistEnabled()
            val ids = if (enabled) menu.grouplistIds() else emptySet()
            val admin = menu.isAdmin(session.userId)
            val skipMenu = admin && menu.isSkipMenuEnabled()
            if (loadedFor == session.userId) {
                _state.update {
                    it.copy(grouplistEnabled = enabled, grouplistIds = ids, isAdmin = admin, skipMenuEnabled = skipMenu)
                }
            }
        }
    }

    fun toggleGrouplist(item: BaseItemDto, onError: () -> Unit) {
        val on = normalizeGuid(item.Id) !in _state.value.grouplistIds
        viewModelScope.launch {
            catching { menu.setOnGrouplist(item.Id, on) }
                .onSuccess { ids -> _state.update { it.copy(grouplistIds = ids) } }
                .onFailure { onError() }
        }
    }

    // Looks for intro and credits timestamps; a show's scan can run for minutes,
    // so this outlives the menu and says how it went in toasts.
    internal fun scanSkipTimestamps(item: BaseItemDto, action: SkipAction, toast: (String) -> Unit) {
        viewModelScope.launch {
            toast(Strings.format("skip_looking", item.Name ?: Strings.get("skip_this_title")))
            catching { menu.scanSkipTimestamps(item.Id, action.useAnalyzerFallback) }
                .onSuccess { result ->
                    val (hits, total) = skipFoundCounts(result.CommunityHits, result.AnalyzerHits, result.EpisodesScanned)
                    toast(Strings.format("skip_found_count", hits, total))
                }
                .onFailure { toast(Strings.get("toast_skip_timestamps_error")) }
        }
    }

    // A film or an episode is queued as it is; a show or a season queues the
    // episodes the scope picks, skipping any already on the device.
    internal fun download(session: Session, item: BaseItemDto, scope: EpisodeScope?, quality: DownloadQuality, toast: (String) -> Unit) {
        viewModelScope.launch {
            when (downloadKindFor(item.Type)) {
                DownloadKind.VIDEO -> downloadOne(session, item, quality, toast)
                DownloadKind.EPISODES -> downloadEpisodes(session, item, scope ?: EpisodeScope.ALL, quality, toast)
                null -> Unit
            }
        }
    }

    private fun isQueued(userId: String, itemId: String): OfflineDownloadItem? =
        offline.getDownload(itemId, userId)?.takeIf { it.status != OfflineDownloadItem.STATUS_FAILED }

    private suspend fun downloadOne(session: Session, item: BaseItemDto, quality: DownloadQuality, toast: (String) -> Unit) {
        val existing = isQueued(session.userId, item.Id)
        if (existing != null) {
            toast(Strings.get(if (existing.status == OfflineDownloadItem.STATUS_COMPLETED) "downloads_already_done" else "downloads_already_active"))
            return
        }
        // The card's copy of a title is thin; the offline copy plays from this one.
        val full = catching { repository.getItemDetails(session.userId, item.Id) }.getOrNull() ?: item
        catching { offline.queueVideoDownload(full, quality.value, session.userId) }
            .onSuccess { toast(Strings.get("downloads_finding_under")) }
            .onFailure { toast(Strings.get("toast_download_start_error")) }
    }

    private suspend fun downloadEpisodes(session: Session, item: BaseItemDto, scope: EpisodeScope, quality: DownloadQuality, toast: (String) -> Unit) {
        val isSeason = item.Type == "Season"
        val seriesId = if (isSeason) item.SeriesId else item.Id
        if (seriesId == null) {
            toast(Strings.get("downloads_load_episodes_error"))
            return
        }
        val episodes = catching {
            repository.getEpisodesForDownload(seriesId, session.userId, if (isSeason) item.Id else null)
        }.getOrElse {
            toast(Strings.get("downloads_load_episodes_error"))
            return
        }
        val chosen = pickEpisodes(scope, episodes.filterNot { isUnreleasedByDate(it) }) { it.UserData?.Played == true }
        if (chosen.isEmpty()) {
            toast(Strings.get("downloads_no_episodes"))
            return
        }
        toast(Strings.format("downloads_getting_ready", chosen.size, episodeWord(chosen.size)))
        var queued = 0
        for (episode in chosen) {
            if (isQueued(session.userId, episode.Id) != null) continue
            if (catching { offline.queueVideoDownload(episode, quality.value, session.userId) }.isSuccess) queued++
        }
        toast(
            if (queued > 0) Strings.format("downloads_episodes_started", queued, episodeWord(queued))
            else Strings.get("downloads_episodes_already"),
        )
    }

    private fun episodeWord(count: Int): String =
        Strings.get(if (count == 1) "word_episode_singular" else "word_episode_plural")

    private companion object {
        const val RELOAD_MS = 120_000L
    }
}
