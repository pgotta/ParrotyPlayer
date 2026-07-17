package com.parroty.player.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps the book playing with the screen off. The player itself lives here
 * rather than in a ViewModel so that rotating, or leaving the app, never
 * interrupts a chapter.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // DefaultDataSource handles file:// for offline downloads and delegates
        // http(s) to the Drive-authenticated factory.
        val dataSourceFactory = DefaultDataSource.Factory(this, DriveDataSource.factory(this))

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            // Default is three tries. Streaming a long book over mobile means
            // transient failures are normal, and giving up on one is worse than
            // waiting a moment.
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            // Without this, Android is free to sleep the Wi-Fi radio and the CPU
            // once the screen is off. The buffer carries playback for a while, then
            // the next request finds a dead socket. NETWORK holds a wifi lock as
            // well as a wake lock, which is what a streamed book needs.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        // Chapter timestamps must land on the word, not the nearest keyframe.
        player.setSeekParameters(SeekParameters.EXACT)

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
