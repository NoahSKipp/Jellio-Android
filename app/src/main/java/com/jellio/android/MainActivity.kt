package com.jellio.android

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Surface
import com.jellio.android.data.prefs.QuickHubKeyConfig
import com.jellio.android.data.prefs.QuickHubKeyGate
import com.jellio.android.data.prefs.QuickHubKeys
import com.jellio.android.data.prefs.QuickHubPreferences
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioTvTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalIsInPipMode = compositionLocalOf { false }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val deepLinkItemId = mutableStateOf<String?>(null)
    val isInPipModeState = mutableStateOf(false)
    private var isPlayerActive: Boolean = false
    var isPlayerCurrentlyPlaying: Boolean = false
    var pipAspectWidth: Int = 16
    var pipAspectHeight: Int = 9
    var onPipPlayPause: (() -> Unit)? = null
    var onPipSeekDeltaMs: ((Long) -> Unit)? = null
    var isQuickHubOpen: Boolean = false
    var isRecordingShortcut: Boolean = false
    var onShortcutKeyRecorded: ((Int, KeyEvent) -> Boolean)? = null
    var onQuickHubShortcut: (() -> Unit)? = null
    var isQuickHubSuppressed: Boolean = false

    @javax.inject.Inject
    lateinit var nowPlayingManager: com.jellio.android.data.nowplaying.NowPlayingManager

    private val quickHubGate = QuickHubKeyGate()
    private var quickHubHoldJob: kotlinx.coroutines.Job? = null
    private var quickHubHeldDown: KeyEvent? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PIP_CONTROL) return
            when (intent.getIntExtra(EXTRA_PIP_CONTROL_TYPE, 0)) {
                PIP_CONTROL_PLAY_PAUSE -> onPipPlayPause?.invoke()
                PIP_CONTROL_REWIND -> onPipSeekDeltaMs?.invoke(-10_000L)
                PIP_CONTROL_FAST_FORWARD -> onPipSeekDeltaMs?.invoke(10_000L)
            }
        }
    }

    private fun quickHubAvailable(): Boolean = !isPlayerActive && !isQuickHubSuppressed && onQuickHubShortcut != null

    fun supportsPip(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun buildPipParams(
        isPlaying: Boolean = isPlayerCurrentlyPlaying,
        aspectWidth: Int = pipAspectWidth,
        aspectHeight: Int = pipAspectHeight,
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val safeW = aspectWidth.coerceAtLeast(1)
        val safeH = aspectHeight.coerceAtLeast(1)
        val ratio = Rational(safeW, safeH).coerceIn(Rational(100, 239), Rational(239, 100))
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
        if (onPipPlayPause != null || onPipSeekDeltaMs != null) {
            val actions = mutableListOf<RemoteAction>()
            if (onPipSeekDeltaMs != null) {
                actions.add(
                    buildPipRemoteAction(
                        requestCode = PIP_CONTROL_REWIND,
                        iconRes = android.R.drawable.ic_media_rew,
                        title = "Rewind 10s",
                        controlType = PIP_CONTROL_REWIND,
                    )
                )
            }
            if (onPipPlayPause != null) {
                actions.add(
                    buildPipRemoteAction(
                        requestCode = PIP_CONTROL_PLAY_PAUSE,
                        iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        title = if (isPlaying) "Pause" else "Play",
                        controlType = PIP_CONTROL_PLAY_PAUSE,
                    )
                )
            }
            if (onPipSeekDeltaMs != null) {
                actions.add(
                    buildPipRemoteAction(
                        requestCode = PIP_CONTROL_FAST_FORWARD,
                        iconRes = android.R.drawable.ic_media_ff,
                        title = "Forward 10s",
                        controlType = PIP_CONTROL_FAST_FORWARD,
                    )
                )
            }
            builder.setActions(actions)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isPlayerActive && isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun buildPipRemoteAction(
        requestCode: Int,
        iconRes: Int,
        title: String,
        controlType: Int,
    ): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_CONTROL_TYPE, controlType)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(this, iconRes), title, title, pendingIntent)
    }

    fun updatePipParams(
        isPlaying: Boolean,
        aspectWidth: Int = pipAspectWidth,
        aspectHeight: Int = pipAspectHeight,
    ) {
        isPlayerCurrentlyPlaying = isPlaying
        if (aspectWidth > 0 && aspectHeight > 0) {
            pipAspectWidth = aspectWidth
            pipAspectHeight = aspectHeight
        }
        if (!supportsPip()) return
        try {
            buildPipParams(isPlaying, pipAspectWidth, pipAspectHeight)?.let { setPictureInPictureParams(it) }
        } catch (_: Exception) {}
    }

    fun enterPipNow(aspectWidth: Int = pipAspectWidth, aspectHeight: Int = pipAspectHeight): Boolean {
        if (!supportsPip()) return false
        if (aspectWidth > 0 && aspectHeight > 0) {
            pipAspectWidth = aspectWidth
            pipAspectHeight = aspectHeight
        }
        return try {
            val params = buildPipParams(isPlayerCurrentlyPlaying, pipAspectWidth, pipAspectHeight)
            if (params != null) enterPictureInPictureMode(params) else false
        } catch (_: Exception) {
            false
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState.value = isInPictureInPictureMode
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (!isDown && event.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event)

        if (isRecordingShortcut) {
            if (onShortcutKeyRecorded?.invoke(keyCode, event) == true) {
                // Recording ends on the DOWN; its repeats and UP must not reach the dialog's buttons.
                if (isDown) quickHubGate.swallowUntilUp(keyCode) else quickHubGate.forget(keyCode)
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        StartupPrefs.awaitBlocking()
        val action = quickHubGate.onKey(
            keyCode = keyCode,
            isDown = isDown,
            repeatCount = event.repeatCount,
            isCanceled = event.isCanceled,
            config = QuickHubKeyConfig(
                button = QuickHubPreferences.button,
                trigger = QuickHubPreferences.trigger,
                customKeyCode = QuickHubPreferences.customKeyCode,
                available = quickHubAvailable(),
                hubOpen = isQuickHubOpen,
            ),
        )
        val heldDown = quickHubHeldDown
        if (!quickHubGate.isHolding) cancelQuickHubHold()

        return when (action) {
            QuickHubKeyGate.Action.PASS -> super.dispatchKeyEvent(event)
            QuickHubKeyGate.Action.CONSUME -> true
            QuickHubKeyGate.Action.TOGGLE -> {
                onQuickHubShortcut?.invoke()
                true
            }
            QuickHubKeyGate.Action.START_HOLD -> {
                quickHubHoldJob?.cancel()
                // Input events are recycled after dispatch, so keep a copy for a possible replay.
                quickHubHeldDown = KeyEvent(event)
                quickHubHoldJob = lifecycleScope.launch {
                    delay(QuickHubKeys.HOLD_TIMEOUT_MS)
                    if (quickHubGate.onHoldTimeout(keyCode, quickHubAvailable())) onQuickHubShortcut?.invoke()
                }
                true
            }
            QuickHubKeyGate.Action.REPLAY_PRESS -> {
                replayPress(heldDown ?: KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN), event)
                true
            }
        }
    }

    private fun replayPress(down: KeyEvent, up: KeyEvent) {
        if (up.keyCode == KeyEvent.KEYCODE_BACK) {
            // Compose key handlers (dismissOnBack, settings content) see Back first, as they would without the hold.
            val handled = window.superDispatchKeyEvent(down) or window.superDispatchKeyEvent(up)
            if (!handled) onBackPressedDispatcher.onBackPressed()
        } else {
            super.dispatchKeyEvent(down)
            super.dispatchKeyEvent(up)
        }
    }

    private fun cancelQuickHubHold() {
        quickHubHoldJob?.cancel()
        quickHubHoldJob = null
        quickHubHeldDown = null
    }

    private fun resetQuickHubGesture() {
        cancelQuickHubHold()
        quickHubGate.onFocusLost()
    }

    override fun onStart() {
        super.onStart()
        nowPlayingManager.onAppStarted()
    }

    override fun onStop() {
        super.onStop()
        nowPlayingManager.onAppStopped()
    }

    override fun onPause() {
        super.onPause()
        resetQuickHubGesture()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) resetQuickHubGesture()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive && isPlayerCurrentlyPlaying) {
            enterPipNow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The first frame reads motion, overscan and language; the rest load meanwhile.
        com.jellio.android.ui.perf.MotionPolicy.init(this)
        com.jellio.android.data.prefs.DisplayPreferences.init(this)
        com.jellio.android.data.playback.DeviceDecoderCaps.attach(this)
        StartupPrefs.start(this)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        handleIntent(intent)
        setContent {
            JellioTvTheme {
                val prefetchViewModel: com.jellio.android.ui.home.GelatoPrefetchViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                val prefetchLambda = androidx.compose.runtime.remember(prefetchViewModel) {
                    { item: com.jellio.android.data.model.BaseItemDto -> prefetchViewModel.prefetch(item) }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.jellio.android.ui.home.LocalGelatoPrefetch provides prefetchLambda,
                    androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.jellio.android.ui.common.TvBringIntoViewSpec,
                    LocalIsInPipMode provides isInPipModeState.value,
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                        // One dark ground behind every screen and the nav bar alike.
                        Surface(
                            colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = JellioBg),
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black),
                        ) {
                            JellioRoot(
                                deepLinkItemId = deepLinkItemId.value,
                                onDeepLinkConsumed = { deepLinkItemId.value = null },
                                onPlayerActiveChanged = { active ->
                                    isPlayerActive = active
                                    if (!active) {
                                        isPlayerCurrentlyPlaying = false
                                        onPipPlayPause = null
                                        onPipSeekDeltaMs = null
                                        updatePipParams(isPlaying = false)
                                    }
                                },
                            )
                        }
                        var pendingCrashReport by androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(CrashReporter.read(this@MainActivity))
                        }
                        pendingCrashReport?.let { report ->
                            CrashReportOverlay(
                                report = report,
                                onDismiss = {
                                    CrashReporter.clear(this@MainActivity)
                                    pendingCrashReport = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_AUDIOBOOK, false) == true) {
            intent.removeExtra(EXTRA_OPEN_AUDIOBOOK)
            deepLinkItemId.value = "audiobook:active"
            return
        }
        val uri = intent?.data ?: return
        if (uri.host != "jellio.tv") return
        // Launcher entries open the title's page. /play links from older
        // syncs, still on the home screen, do the same instead of playing.
        when (uri.path) {
            "/play", "/item" -> uri.getQueryParameter("id")?.let { deepLinkItemId.value = "detail:$it" }
        }
    }

    companion object {
        const val EXTRA_OPEN_AUDIOBOOK = "com.jellio.android.EXTRA_OPEN_AUDIOBOOK"
        private const val ACTION_PIP_CONTROL = "com.jellio.android.ACTION_PIP_CONTROL"
        private const val EXTRA_PIP_CONTROL_TYPE = "pip_control_type"
        private const val PIP_CONTROL_PLAY_PAUSE = 1
        private const val PIP_CONTROL_REWIND = 2
        private const val PIP_CONTROL_FAST_FORWARD = 3
    }
}
