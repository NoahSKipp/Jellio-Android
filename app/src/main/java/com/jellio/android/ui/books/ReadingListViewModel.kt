package com.jellio.android.ui.books

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.android.data.books.ReadingEntryDto
import com.jellio.android.data.books.ReadingListRepository
import com.jellio.android.data.books.ReadingToggleResult
import com.jellio.android.data.books.httpsCoverOrNull
import com.jellio.android.data.books.isLibraryItemId
import com.jellio.android.data.books.ReadingTarget
import com.jellio.android.data.model.CommunityWatcherDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// One entry of the reading list as a card draws it.
@Immutable
data class ReadingCardModel(
    val key: String,
    val kind: String,
    val itemId: String?,
    val mangaId: Int?,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    // The https cover the entry was added with, for sending it on to a friend.
    val externalCover: String?,
    val addedBy: List<CommunityWatcherDto>,
)

fun ReadingCardModel.toTarget(): ReadingTarget = ReadingTarget(
    key = key,
    kind = kind,
    itemId = itemId?.takeIf { isLibraryItemId(it) },
    mangaId = mangaId,
    title = title,
    author = author,
    coverUrl = httpsCoverOrNull(externalCover),
)

// What a menu, sheet or Discover card offers for one item. `onFriend` is null
// while there is nobody to send it to.
class ReadingMenuActions(
    val onList: Boolean,
    val onToggle: () -> Unit,
    val onFriend: (() -> Unit)? = null,
)

// A friend's notification asked to open a manga series on the Reader tab.
data class SeriesOpenRequest(val key: String, val title: String)

// Activity-scoped: the Reader tab, the title pages, Discover and the friend
// notifications all read the same key set.
@HiltViewModel
class ReadingListViewModel @Inject constructor(
    private val repository: ReadingListRepository,
) : ViewModel() {
    // False once the plugin answered 404: every reading-list control hides.
    val supported: StateFlow<Boolean?> = repository.supported
    val keys: StateFlow<Set<String>> = repository.keys

    private val _cards = MutableStateFlow<List<ReadingCardModel>>(emptyList())
    val cards: StateFlow<List<ReadingCardModel>> = _cards.asStateFlow()

    // String keys of toasts; one collector at the app's root shows them.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _openSeries = MutableStateFlow<SeriesOpenRequest?>(null)
    val openSeries: StateFlow<SeriesOpenRequest?> = _openSeries.asStateFlow()

    private var listJob: Job? = null

    fun refreshKeys() {
        viewModelScope.launch { repository.refreshKeys() }
    }

    // Reads the list for the Reader tab; also refreshes the key set.
    fun loadCards() {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            repository.list()
                .onSuccess { entries -> _cards.value = entries.map { toCard(it) } }
                .onFailure { if (repository.supported.value == false) _cards.value = emptyList() }
        }
    }

    fun toggle(target: ReadingTarget) {
        viewModelScope.launch { finish(repository.toggle(target)) }
    }

    fun remove(key: String) {
        viewModelScope.launch { finish(repository.removeKey(key)) }
    }

    private suspend fun finish(result: ReadingToggleResult) {
        _messages.emit(result.messageKey)
        if (result.ok) {
            // Takes the entry off the shelf at once; the list read brings the rest.
            if (!result.onList) _cards.value = _cards.value.filter { it.key in repository.keys.value }
            loadCards()
        }
    }

    // The cover of one entry, for a notification about it.
    suspend fun coverUrlFor(key: String): String? {
        _cards.value.firstOrNull { it.key == key }?.let { return it.coverUrl }
        val entry = repository.list().getOrNull()?.firstOrNull { it.Key == key } ?: return null
        return repository.coverUrl(entry.Cover)
    }

    fun requestOpenSeries(key: String, title: String) {
        _openSeries.value = SeriesOpenRequest(key.removePrefix("s:"), title)
    }

    fun seriesOpened() {
        _openSeries.value = null
    }

    private suspend fun toCard(entry: ReadingEntryDto) = ReadingCardModel(
        key = entry.Key,
        kind = entry.Kind,
        itemId = entry.ItemId,
        mangaId = entry.MangaId,
        title = entry.Title,
        author = entry.Author,
        coverUrl = repository.coverUrl(entry.Cover),
        externalCover = entry.CoverUrl,
        addedBy = entry.AddedBy
            .filter { it.UserId.isNotBlank() }
            .take(3)
            .map { CommunityWatcherDto(UserId = it.UserId, UserName = it.UserName, PrimaryImageTag = it.PrimaryImageTag) },
    )
}
