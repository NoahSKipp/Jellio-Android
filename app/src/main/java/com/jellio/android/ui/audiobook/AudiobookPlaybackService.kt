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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return controller.getOrCreateMediaSession()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = controller.getOrCreateMediaSession()
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}
