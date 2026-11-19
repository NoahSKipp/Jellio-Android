package com.jellio.android.ui.audiobook

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Coordinates the single active foreground MediaSession across Audiobooks,
 * Video playback, and Live TV so all media types appear in the Android
 * SystemUI Media Drawer, lock screen, and connected wearables.
 */
object MediaSessionRegistry {
    private var service: AudiobookPlaybackService? = null
    private var audiobookSession: MediaSession? = null
    private var videoSession: MediaSession? = null

    fun activeSession(): MediaSession? = videoSession ?: audiobookSession

    fun attachService(playbackService: AudiobookPlaybackService) {
        service = playbackService
        playbackService.syncSession(activeSession())
    }

    fun detachService(playbackService: AudiobookPlaybackService) {
        if (service === playbackService) {
            service = null
        }
    }

    fun setAudiobookSession(context: Context, session: MediaSession?, stopIfEmpty: Boolean = false) {
        audiobookSession = session
        syncOrStartService(context, stopIfEmpty)
    }

    fun setVideoSession(context: Context, session: MediaSession?) {
        videoSession = session
        syncOrStartService(context, stopIfEmpty = session == null)
    }

    fun ensureServiceRunning(context: Context) {
        if (activeSession() != null) {
            syncOrStartService(context, stopIfEmpty = false)
        }
    }

    private fun syncOrStartService(context: Context, stopIfEmpty: Boolean) {
        val active = activeSession()
        val running = service
        if (active != null) {
            if (running != null) {
                running.syncSession(active)
                return
            }
            val intent = Intent(context, AudiobookPlaybackService::class.java)
            runCatching {
                context.startService(intent)
            }.onFailure { err ->
                Log.w("MediaSessionRegistry", "startService failed, retrying foreground service", err)
                runCatching {
                    ContextCompat.startForegroundService(context, intent)
                }
            }
        } else {
            running?.syncSession(null)
            if (stopIfEmpty) {
                runCatching {
                    val stopIntent = Intent(context, AudiobookPlaybackService::class.java).apply {
                        action = AudiobookPlaybackService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                }
            }
        }
    }
}

@AndroidEntryPoint
class AudiobookPlaybackService : MediaSessionService() {

    @Inject
    lateinit var controller: AudiobookPlayerController

    override fun onCreate() {
        super.onCreate()
        MediaSessionRegistry.attachService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sessions.toList().forEach { runCatching { removeSession(it) } }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        syncSession(MediaSessionRegistry.activeSession() ?: controller.getActiveMediaSession())
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val session = MediaSessionRegistry.activeSession() ?: controller.getOrCreateMediaSession()
        syncSession(session)
        return session
    }

    fun syncSession(session: MediaSession?) {
        val current = sessions.toList()
        current.forEach { existing ->
            if (existing != session) {
                runCatching { removeSession(existing) }
            }
        }
        if (session != null && !sessions.contains(session)) {
            addSession(session)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = MediaSessionRegistry.activeSession() ?: controller.getActiveMediaSession()
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        MediaSessionRegistry.detachService(this)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.jellio.android.audiobook.ACTION_STOP"
    }
}
