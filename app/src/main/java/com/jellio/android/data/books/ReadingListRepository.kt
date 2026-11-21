package com.jellio.android.data.books

import com.jellio.android.data.catching
import com.jellio.android.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import retrofit2.HttpException
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

// The reading list. A plugin without it answers 404 for the keys probe; from
// then on it counts as unavailable and every reading-list control hides. The
// key set is held in memory for the add and remove toggles.
@Singleton
class ReadingListRepository @Inject constructor(
    retrofit: Retrofit,
    private val sessionManager: SessionManager,
) {
    private val api: ReadingListApi = retrofit.create(ReadingListApi::class.java)

    // True or false once the server has answered, null before.
    private val _supported = MutableStateFlow<Boolean?>(null)
    val supported: StateFlow<Boolean?> = _supported.asStateFlow()

    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    @Volatile
    private var forUser: String? = null

    // Another account starts clean and asks the server again.
    private suspend fun ensureUser() {
        val id = sessionManager.sessionFlow.first()?.userId
        if (id != forUser) {
            forUser = id
            _supported.value = null
            _keys.value = emptySet()
        }
    }

    private fun noteFailure(error: Throwable) {
        if (error is HttpException && error.code() == 404) _supported.value = false
    }

    // Paints the toggles; false when the server has no reading list.
    suspend fun refreshKeys(): Boolean {
        ensureUser()
        if (_supported.value == false) return false
        catching { api.keys() }
            .onSuccess { response ->
                _keys.value = response.Keys.toSet()
                _supported.value = true
            }
            .onFailure { noteFailure(it) }
        return _supported.value != false
    }

    suspend fun list(): Result<List<ReadingEntryDto>> {
        ensureUser()
        if (_supported.value == false) return Result.failure(IllegalStateException("reading list unavailable"))
        return catching { api.list().Items }
            .onSuccess { items ->
                _keys.value = items.mapTo(HashSet()) { it.Key }
                _supported.value = true
            }
            .onFailure { noteFailure(it) }
    }

    // Adds or removes at once in the key set and puts it back when the server refuses.
    suspend fun toggle(target: ReadingTarget): ReadingToggleResult {
        ensureUser()
        val wasOn = target.key in _keys.value
        return if (wasOn) removeKey(target.key) else add(target)
    }

    suspend fun add(target: ReadingTarget): ReadingToggleResult {
        _keys.update { it + target.key }
        val response = catching { api.add(target.toAddBody()) }.getOrElse {
            _keys.update { keys -> keys - target.key }
            return readingToggleFailure(false, "reading_toast_error")
        }
        if (response.isSuccessful) return readingToggleSuccess(true)
        _keys.update { it - target.key }
        val body = catching { response.errorBody()?.string() }.getOrNull()
        return readingToggleFailure(false, readingErrorKey(response.code(), readingErrorCode(body)))
    }

    suspend fun removeKey(key: String): ReadingToggleResult {
        _keys.update { it - key }
        val response = catching { api.remove(key) }.getOrElse {
            _keys.update { keys -> keys + key }
            return readingToggleFailure(true, "reading_toast_error")
        }
        if (response.isSuccessful) return readingToggleSuccess(false)
        _keys.update { it + key }
        return readingToggleFailure(true, "reading_toast_error")
    }

    // The cover a card draws: server-relative ones get the token.
    suspend fun coverUrl(cover: String?): String? =
        readingCoverUrl(cover, sessionManager.serverAddress(), sessionManager.accessToken())
}
