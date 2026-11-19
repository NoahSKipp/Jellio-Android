package com.jellio.android.ui.audiobook

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AudiobookPlaybackService : MediaSessionService() {

    @Inject
    lateinit var controller: AudiobookPlayerController

    override fun onCreate() {
        super.onCreate()
        controller.attachPlaybackService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sessions.toList().forEach { runCatching { removeSession(it) } }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        syncSession(controller.getActiveMediaSession())
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val session = controller.getOrCreateMediaSession()
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
        val session = controller.getActiveMediaSession()
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        controller.detachPlaybackService(this)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.jellio.android.audiobook.ACTION_STOP"
    }
}
