package com.jellio.android.data

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import javax.inject.Inject
import javax.inject.Singleton

// The one scope stream and playback warm-ups run in, so signing out can
// cancel all of them at once.
@Singleton
class PrefetchScope @Inject constructor() {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("PrefetchScope", "Unhandled coroutine error", throwable)
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    fun cancelAll() {
        scope.coroutineContext.cancelChildren()
    }
}
