package com.parroty.player.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.parroty.player.data.db.AppDatabase
import com.parroty.player.data.db.BookDao
import com.parroty.player.data.db.BookEntity
import com.parroty.player.data.db.BookmarkEntity
import com.parroty.player.data.drive.DriveApi
import com.parroty.player.data.drive.DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** The chapter and subtitle files Parroty wrote next to a given mp4. */
data class Companions(
    val chapterFiles: List<DriveFile>,
    val subtitleFiles: List<DriveFile>,
    val suggestedChapters: DriveFile?,
    val suggestedSubtitle: DriveFile?,
    /** Every playable file in the folder that belongs to the same book. Lets the
     *  pairing screen offer the mp3 instead of the mp4, which is the same
     *  narration without a cover image re-encoded around it. */
    val sourceOptions: List<DriveFile>
)

class BookRepository(
    private val appContext: Context,
    private val drive: DriveApi,
    private val dao: BookDao
) {

    fun observeRecent(): Flow<List<BookEntity>> = dao.observeRecent()
    fun observe(fileId: String): Flow<BookEntity?> = dao.observe(fileId)
    suspend fun find(fileId: String): BookEntity? = dao.find(fileId)
    fun observeBookmarks(fileId: String): Flow<List<BookmarkEntity>> = dao.observeBookmarks(fileId)

    suspend fun listFolder(folderId: String): List<DriveFile> = drive.listFolder(folderId)

    /**
     * Looks beside the chosen mp4 for the files Parroty emitted in the same run.
     * The suggestion is a starting point only; the pairing screen always lets the
     * choice be overridden, because a folder can hold more than one book.
     */
    suspend fun findCompanions(video: DriveFile, folderId: String): Companions =
        withContext(Dispatchers.Default) {
            val siblings = drive.listFolder(folderId)
            val chapters = siblings.filter { it.isChapterText }
            val subtitles = siblings.filter { it.isSubtitle }
            val wanted = tokens(video.name)
            val sources = siblings
                .filter { it.isPlayable }
                .filter { it.id == video.id || tokens(it.name).any { token -> token in wanted } }
                .sortedBy { it.isVideo }
            Companions(
                chapterFiles = chapters,
                subtitleFiles = subtitles,
                suggestedChapters = bestMatch(video.name, chapters),
                suggestedSubtitle = bestMatch(video.name, subtitles),
                sourceOptions = sources
            )
        }

    /**
     * One candidate is unambiguous. More than one gets scored on how much of the
     * book slug the filename shares with the mp4, since Parroty names every file
     * in a run after the same book.
     */
    private fun bestMatch(videoName: String, candidates: List<DriveFile>): DriveFile? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()
        val target = tokens(videoName)
        return candidates.maxByOrNull { candidate ->
            tokens(candidate.name).count { it in target }
        }
    }

    private fun tokens(name: String): Set<String> =
        name.substringBeforeLast('.')
            .lowercase()
            .split('-', '_', ' ', '.')
            .filter { it.length > 2 && it !in IGNORED_TOKENS }
            .toSet()

    suspend fun loadChapters(chaptersFileId: String): List<Chapter> =
        ChapterParser.parse(readText(chaptersFileId))

    /** Drive id or content:// uri, whichever this book uses. */
    private suspend fun readText(id: String): String =
        if (id.startsWith("content://")) {
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(Uri.parse(id))
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw java.io.IOException("Could not open that file.")
            }
        } else {
            drive.readText(id)
        }

    /**
     * Saves a book picked off the phone. SAF hands back a uri with no folder to
     * look in, so unlike the Drive path nothing can be auto-paired; the caller
     * passes whatever the user picked.
     */
    suspend fun rememberLocalBook(
        mediaUri: Uri,
        chaptersUri: Uri?,
        subtitleUri: Uri?
    ): String {
        val fileId = mediaUri.toString()
        val name = displayName(mediaUri) ?: "Local book"
        val existing = dao.find(fileId)
        dao.upsert(
            BookEntity(
                fileId = fileId,
                name = name,
                folderId = "",
                isLocal = true,
                hasVideo = !name.substringAfterLast('.', "").lowercase().let {
                    it in setOf("mp3", "m4a", "m4b", "opus", "flac", "wav", "ogg")
                },
                chaptersFileId = chaptersUri?.toString(),
                chaptersFileName = chaptersUri?.let { displayName(it) },
                subtitleFileId = subtitleUri?.toString(),
                subtitleFileName = subtitleUri?.let { displayName(it) },
                positionMs = existing?.positionMs ?: 0L,
                durationMs = existing?.durationMs ?: 0L,
                lastPlayedAt = System.currentTimeMillis(),
                localPath = null
            )
        )
        return fileId
    }

    /** SAF only gives a uri; the human-readable name has to be queried for. */
    fun displayName(uri: Uri): String? = try {
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Without this the uri stops working the next time the app starts, and the
     * book turns into a row that fails to open with no obvious reason.
     */
    fun persistPermission(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Some providers do not offer persistable grants. The book will work
            // this session and fail later, which beats refusing to open it now.
        }
    }

    suspend fun rememberBook(
        source: DriveFile,
        folderId: String,
        chapters: DriveFile?,
        subtitle: DriveFile?
    ) {
        val existing = dao.find(source.id)
        dao.upsert(
            BookEntity(
                fileId = source.id,
                name = source.name,
                folderId = folderId,
                hasVideo = source.isVideo,
                chaptersFileId = chapters?.id,
                chaptersFileName = chapters?.name,
                subtitleFileId = subtitle?.id,
                subtitleFileName = subtitle?.name,
                positionMs = existing?.positionMs ?: 0L,
                durationMs = existing?.durationMs ?: 0L,
                lastPlayedAt = System.currentTimeMillis(),
                localPath = existing?.localPath
            )
        )
    }

    suspend fun savePosition(fileId: String, positionMs: Long, durationMs: Long) {
        dao.savePosition(fileId, positionMs, durationMs, System.currentTimeMillis())
    }

    suspend fun addBookmark(fileId: String, positionMs: Long, label: String) {
        dao.addBookmark(
            BookmarkEntity(
                fileId = fileId,
                positionMs = positionMs,
                label = label,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    suspend fun forget(fileId: String) {
        find(fileId)?.localPath?.let { File(it).delete() }
        dao.deleteBookmarksFor(fileId)
        dao.delete(fileId)
    }

    /** Clears the library. Also removes anything downloaded for offline. */
    suspend fun forgetAll() = withContext(Dispatchers.IO) {
        offlineDir().listFiles()?.forEach { it.delete() }
        dao.deleteAllBookmarks()
        dao.deleteAllBooks()
    }

    // ── Offline ───────────────────────────────────────────────────────────────

    /** Keeps the original extension so the extractor is picked by name rather
     *  than by sniffing the first bytes. */
    fun offlineFile(fileId: String, fileName: String): File {
        val ext = fileName.substringAfterLast('.', "mp4")
        return File(offlineDir(), "$fileId.$ext")
    }

    private fun offlineDir(): File =
        File(appContext.filesDir, "offline").apply { mkdirs() }

    /**
     * Pulls the mp4 down for listening without a connection. Streaming stays the
     * default; this only runs when asked for.
     */
    suspend fun downloadForOffline(
        fileId: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = offlineFile(fileId, fileName)
        val partial = File(offlineDir(), "${target.name}.part")
        if (partial.exists()) partial.delete()

        FileOutputStream(partial).use { out ->
            drive.download(
                fileId = fileId,
                onChunk = { buffer, length -> out.write(buffer, 0, length) },
                onProgress = { read, total ->
                    if (total > 0) onProgress(read.toFloat() / total.toFloat())
                }
            )
        }
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) throw java.io.IOException("Could not finish saving the download.")
        dao.setLocalPath(fileId, target.absolutePath)
        onProgress(1f)
        target
    }

    suspend fun removeDownload(fileId: String) = withContext(Dispatchers.IO) {
        dao.find(fileId)?.localPath?.let { File(it).delete() }
        dao.setLocalPath(fileId, null)
    }

    companion object {
        private val IGNORED_TOKENS = setOf(
            "txt", "srt", "vtt", "mp4", "mp3", "m4a", "m4b", "wav", "flac", "opus", "ogg"
        )

        @Volatile
        private var instance: BookRepository? = null

        fun get(context: Context): BookRepository =
            instance ?: synchronized(this) {
                instance ?: BookRepository(
                    appContext = context.applicationContext,
                    drive = DriveApi(context.applicationContext),
                    dao = AppDatabase.get(context).bookDao()
                ).also { instance = it }
            }

        fun driveApi(context: Context): DriveApi = DriveApi(context.applicationContext)
    }
}
