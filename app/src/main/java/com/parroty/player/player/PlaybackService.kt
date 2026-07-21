package com.parroty.player.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.parroty.player.data.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

/**
 * Keeps the book playing with the screen off. The player itself lives here
 * rather than in a ViewModel so that rotating, or leaving the app, never
 * interrupts a chapter.
 *
 * Resume persistence also lives here. The previous implementation saved from
 * PlayerViewModel, so saving stopped as soon as the player screen was closed
 * even though this service kept the audiobook playing in the background.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var positionTracker: Job? = null

    private val repository by lazy { BookRepository.get(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var trackedFileId: String? = null
    private var trackedPositionMs: Long = 0L
    private var trackedDurationMs: Long = 0L
    private var lastSavedFileId: String? = null
    private var lastSavedPositionMs: Long = Long.MIN_VALUE

    private val persistenceListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The callback arrives after the player has switched items, so save
            // the last captured snapshot before replacing it with the new item.
            persistTrackedPosition(force = true)
            trackedFileId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
            trackedPositionMs = 0L
            trackedDurationMs = 0L
            captureCurrentPosition()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            captureCurrentPosition()
            if (!isPlaying) persistTrackedPosition(force = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            captureCurrentPosition()
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                persistTrackedPosition(force = true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            captureCurrentPosition()
            persistTrackedPosition(force = true)
        }
    }

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
        player.addListener(persistenceListener)

        exoPlayer = player
        mediaSession = MediaSession.Builder(this, player).build()
        startPositionTracker()
    }

    private fun startPositionTracker() {
        positionTracker?.cancel()
        positionTracker = serviceScope.launch {
            while (isActive) {
                captureCurrentPosition()
                persistTrackedPosition(force = false)
                delay(POSITION_POLL_MS)
            }
        }
    }

    /** Must run on the player's application thread, which is the main thread. */
    private fun captureCurrentPosition() {
        val player = exoPlayer ?: return
        val mediaId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        trackedFileId = mediaId
        trackedPositionMs = player.currentPosition.coerceAtLeast(0L)
        trackedDurationMs = if (player.duration == C.TIME_UNSET) 0L else player.duration.coerceAtLeast(0L)
    }

    private fun persistTrackedPosition(force: Boolean) {
        val fileId = trackedFileId ?: return
        val duration = trackedDurationMs
        if (duration <= 0L) return

        val movedEnough = lastSavedFileId != fileId ||
            lastSavedPositionMs == Long.MIN_VALUE ||
            abs(trackedPositionMs - lastSavedPositionMs) >= SAVE_INTERVAL_MS
        if (!force && !movedEnough) return

        val position = trackedPositionMs
        lastSavedFileId = fileId
        lastSavedPositionMs = position
        serviceScope.launch {
            runCatching { repository.savePosition(fileId, position, duration) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        captureCurrentPosition()
        persistTrackedPosition(force = true)

        val player = exoPlayer
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        captureCurrentPosition()

        // Service teardown cancels its coroutine scope, so make the final tiny
        // Room write synchronously before releasing the player. This is not used
        // during normal playback and prevents a last-second pause/stop from being
        // lost when Android removes the service immediately afterward.
        val finalFileId = trackedFileId
        val finalPosition = trackedPositionMs
        val finalDuration = trackedDurationMs
        if (finalFileId != null && finalDuration > 0L) {
            runBlocking(Dispatchers.IO) {
                runCatching {
                    repository.savePosition(finalFileId, finalPosition, finalDuration)
                }
            }
        }

        positionTracker?.cancel()
        positionTracker = null
        exoPlayer?.removeListener(persistenceListener)
        serviceScope.cancel()

        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        private const val POSITION_POLL_MS = 1_000L
        private const val SAVE_INTERVAL_MS = 5_000L
    }
}
