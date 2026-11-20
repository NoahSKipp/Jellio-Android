package com.jellio.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.android.data.CardMenuRepository
import com.jellio.android.data.catching
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.normalizeGuid
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
)

// Activity-scoped, so every screen's options menu shares what it has read.
@HiltViewModel
class CardMenuViewModel @Inject constructor(
    private val menu: CardMenuRepository,
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
            if (loadedFor == session.userId) {
                _state.update { it.copy(grouplistEnabled = enabled, grouplistIds = ids) }
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

    private companion object {
        const val RELOAD_MS = 120_000L
    }
}
