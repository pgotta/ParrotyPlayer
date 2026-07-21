package com.parroty.player.ui.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.parroty.player.data.BookRepository
import com.parroty.player.data.Chapter
import com.parroty.player.data.ChapterParser
import com.parroty.player.data.db.BookEntity
import com.parroty.player.data.db.BookmarkEntity
import com.parroty.player.data.drive.DriveApi
import com.parroty.player.player.PlaybackService
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerUiState(
    val loading: Boolean = true,
    val book: BookEntity? = null,
    val chapters: List<Chapter> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val isPlaying: Boolean = false,
    val subtitlesOn: Boolean = false,
    val downloadProgress: Float? = null,
    val error: String? = null,
    val authExpired: Boolean = false
) {
    val currentChapter: Chapter? get() = ChapterParser.chapterAt(chapters, positionMs)
    val isOffline: Boolean get() = book?.localPath != null

    /** A book already on the phone has nothing to download. */
    val canDownload: Boolean get() = book != null && !book.isLocal && book.localPath == null

    /** The mp3 has no surface to draw subtitles on. */
    val canShowSubtitles: Boolean get() = book?.hasVideo == true && book.subtitleFileId != null
}

@UnstableApi
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository.get(app)
    private val drive = BookRepository.driveApi(app)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null

    /** Exposed only so the subtitle surface can attach; the UI drives everything
     *  else through this view model rather than touching the player. */
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var currentFileId: String? = null
    private var recoveryAttempts = 0

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                recoveryAttempts = 0
                _state.update { it.copy(error = null) }
            }
            // PlaybackService owns resume persistence. Keeping a second writer
            // here could associate the old player's position with a newly opened
            // book while MediaController was switching media items.
        }

        override fun onPlayerError(error: PlaybackException) {
            // Anything network shaped is worth retrying: the token may have gone
            // stale, or the radio may have been asleep. DriveDataSource already
            // retries a 401 in place, so reaching here means that was not enough.
            val recoverable = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
                else -> false
            }

            if (recoverable && recoveryAttempts < MAX_RECOVERY_ATTEMPTS) {
                recoveryAttempts++
                recover()
                return
            }

            _state.update {
                it.copy(
                    error = if (recoverable) {
                        "Lost the connection to Drive and could not get it back. Tap to retry."
                    } else {
                        error.errorCodeName + ": " + (error.message ?: "Playback stopped.")
                    }
                )
            }
        }
    }

    fun start(fileId: String) {
        if (currentFileId == fileId) return
        currentFileId = fileId

        viewModelScope.launch {
            val book = repo.find(fileId)
            if (book == null) {
                _state.update { it.copy(loading = false, error = "That book is no longer set up.") }
                return@launch
            }
            _state.update { it.copy(book = book, loading = false) }

            book.chaptersFileId?.let { loadChapters(it) }
            observeBookmarks(fileId)
            connectController(book)
            trackPosition()
        }
    }

    private fun loadChapters(chaptersFileId: String) {
        viewModelScope.launch {
            try {
                val chapters = repo.loadChapters(chaptersFileId)
                _state.update { it.copy(chapters = chapters) }
            } catch (e: DriveApi.AuthExpired) {
                _state.update { it.copy(authExpired = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not read the chapter file.") }
            }
        }
    }

    private fun observeBookmarks(fileId: String) {
        viewModelScope.launch {
            repo.observeBookmarks(fileId).collect { list ->
                _state.update { it.copy(bookmarks = list) }
            }
        }
    }

    private fun connectController(book: BookEntity) {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                val mediaController = try {
                    future.get()
                } catch (e: Exception) {
                    _state.update { it.copy(error = "Could not start the player service.") }
                    return@addListener
                }
                controller = mediaController
                _player.value = mediaController
                mediaController.addListener(listener)

                val alreadyLoaded = mediaController.currentMediaItem?.mediaId == book.fileId
                if (!alreadyLoaded) {
                    mediaController.setMediaItem(buildMediaItem(book, subtitlesOn = false))
                    mediaController.prepare()
                    if (book.positionMs > 0) mediaController.seekTo(book.positionMs)
                }
                _state.update { it.copy(isPlaying = mediaController.isPlaying) }
            },
            ContextCompat.getMainExecutor(app)
        )
    }

    /**
     * The mp4 either streams from Drive or comes off disk if it was downloaded.
     * Subtitles are side-loaded from Drive through the same authenticated data
     * source, so there is no separate file to manage.
     */
    private fun buildMediaItem(book: BookEntity, subtitlesOn: Boolean): MediaItem {
        val downloaded = book.localPath?.let { File(it) }
        val uri = when {
            // An offline copy wins over everything: no network, no token.
            downloaded != null && downloaded.exists() -> Uri.fromFile(downloaded)
            // A book picked off the phone is already a playable uri.
            book.isLocal -> Uri.parse(book.fileId)
            else -> Uri.parse(drive.mediaUrl(book.fileId))
        }

        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(book.fileId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(book.name.substringBeforeLast('.'))
                    .setArtist("Parroty")
                    .build()
            )

        val subtitleId = book.subtitleFileId
        if (subtitlesOn && subtitleId != null) {
            val subtitleName = book.subtitleFileName.orEmpty()
            val mime = if (subtitleName.endsWith(".vtt", ignoreCase = true)) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP
            }
            val subtitleUri =
                if (book.isLocal) Uri.parse(subtitleId) else Uri.parse(drive.mediaUrl(subtitleId))
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(mime)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }
        return builder.build()
    }

    /** UI polling only. PlaybackService performs the durable position writes. */
    private fun trackPosition() {
        viewModelScope.launch {
            while (isActive) {
                controller?.let { c ->
                    val position = c.currentPosition
                    val duration = if (c.duration == C.TIME_UNSET) 0L else c.duration
                    _state.update {
                        it.copy(
                            positionMs = position,
                            durationMs = duration,
                            bufferedMs = c.bufferedPosition
                        )
                    }
                }
                delay(500)
            }
        }
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0))
        _state.update { it.copy(positionMs = ms) }
    }

    fun nudge(deltaMs: Long) {
        val c = controller ?: return
        seekTo((c.currentPosition + deltaMs).coerceIn(0, if (c.duration > 0) c.duration else Long.MAX_VALUE))
    }

    fun jumpToChapter(chapter: Chapter) = seekTo(chapter.startMs)

    fun previousChapter() {
        val chapters = _state.value.chapters
        if (chapters.isEmpty()) return nudge(-15_000)
        val position = _state.value.positionMs
        // Matches the convention every audiobook player uses: a tap early in a
        // chapter goes back one, a tap later restarts the current one.
        val current = ChapterParser.chapterAt(chapters, position)
        val target = when {
            current == null -> chapters.first()
            position - current.startMs > 3_000 -> current
            else -> chapters.getOrElse(current.index - 1) { chapters.first() }
        }
        seekTo(target.startMs)
    }

    fun nextChapter() {
        val chapters = _state.value.chapters
        if (chapters.isEmpty()) return nudge(30_000)
        val current = ChapterParser.chapterAt(chapters, _state.value.positionMs)
        val next = if (current == null) chapters.first() else chapters.getOrNull(current.index + 1)
        next?.let { seekTo(it.startMs) }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun toggleSubtitles() {
        val book = _state.value.book ?: return
        if (book.subtitleFileId == null || !book.hasVideo) return
        val turningOn = !_state.value.subtitlesOn
        val c = controller ?: return
        val position = c.currentPosition
        val wasPlaying = c.isPlaying

        c.setMediaItem(buildMediaItem(book, subtitlesOn = turningOn))
        c.prepare()
        c.seekTo(position)
        c.playWhenReady = wasPlaying
        _state.update { it.copy(subtitlesOn = turningOn) }
    }

    fun addBookmark() {
        val c = controller ?: return
        val fileId = c.currentMediaItem?.mediaId ?: currentFileId ?: return
        val position = c.currentPosition.coerceAtLeast(0L)
        val label = ChapterParser.chapterAt(_state.value.chapters, position)?.title ?: "Bookmark"

        // Start immediately and finish the tiny Room write even if the user taps
        // Back at once after creating the bookmark.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                repo.addBookmark(fileId, position, label)
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                repo.deleteBookmark(id)
            }
        }
    }

    fun downloadForOffline() {
        val fileId = currentFileId ?: return
        val book = _state.value.book ?: return
        if (_state.value.downloadProgress != null) return
        viewModelScope.launch {
            _state.update { it.copy(downloadProgress = 0f) }
            try {
                repo.downloadForOffline(fileId, book.name) { fraction ->
                    _state.update { it.copy(downloadProgress = fraction) }
                }
                _state.update { it.copy(downloadProgress = null, book = repo.find(fileId)) }
            } catch (e: DriveApi.AuthExpired) {
                _state.update { it.copy(downloadProgress = null, authExpired = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(downloadProgress = null, error = e.message ?: "The download did not finish.")
                }
            }
        }
    }

    fun removeDownload() {
        val fileId = currentFileId ?: return
        viewModelScope.launch {
            repo.removeDownload(fileId)
            _state.update { it.copy(book = repo.find(fileId)) }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /**
     * Re-prepares at the current position. ExoPlayer keeps the position across a
     * failure, so this picks up where the audio stopped rather than restarting the
     * chapter. Backed off a little because an instant retry usually races whatever
     * knocked the connection over in the first place.
     */
    private fun recover() {
        val c = controller ?: return
        val at = c.currentPosition
        viewModelScope.launch {
            delay(1_500L * recoveryAttempts)
            val player = controller ?: return@launch
            player.prepare()
            if (at > 0) player.seekTo(at)
            player.play()
        }
    }

    /** For the retry affordance on the error banner. */
    fun retryNow() {
        recoveryAttempts = 0
        _state.update { it.copy(error = null) }
        recover()
    }

    companion object {
        private const val MAX_RECOVERY_ATTEMPTS = 4
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _player.value = null
        super.onCleared()
    }
}
