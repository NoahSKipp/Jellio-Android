package com.jellio.android

import android.content.Context
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Preference holders the first frame does not need, loaded off the main thread.
// Their readers sit behind the splash, which waits for this; key handling,
// which can come sooner, blocks on it only in that short window.
object StartupPrefs {
    private val started = AtomicBoolean(false)
    private val ready = CountDownLatch(1)
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("StartupPrefs", "Unhandled coroutine error", throwable)
    }

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                com.jellio.android.data.prefs.QuickHubPreferences.init(app)
                com.jellio.android.data.prefs.PlayerPrefs.init(app)
                com.jellio.android.ui.player.BadStreams.init(app)
                com.jellio.android.ui.onboarding.OnboardingPrefs.init(app)
            } finally {
                ready.countDown()
            }
        }
    }

    suspend fun await() {
        if (started.get() && ready.count > 0) withContext(Dispatchers.IO) { ready.await() }
    }

    fun awaitBlocking() {
        if (started.get() && ready.count > 0) ready.await()
    }
}
