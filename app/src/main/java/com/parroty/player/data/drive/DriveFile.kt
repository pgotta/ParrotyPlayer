package com.parroty.player.data.drive

/** One entry in a Drive folder listing. */
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedTime: String?
) {
    val isFolder: Boolean get() = mimeType == MIME_FOLDER

    val isVideo: Boolean
        get() = mimeType.startsWith("video/") || name.endsWith(".mp4", ignoreCase = true)

    /** Parroty's combined audiobook, which is the same narration without the
     *  static cover-image video track wrapped around it. */
    val isAudio: Boolean
        get() = mimeType.startsWith("audio/") ||
            AUDIO_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    val isPlayable: Boolean get() = isVideo || isAudio

    /** Parroty writes youtube_chapters-<slug>.txt next to the audiobook. */
    val isChapterText: Boolean
        get() = name.endsWith(".txt", ignoreCase = true) && name.contains("chapter", ignoreCase = true)

    val isSubtitle: Boolean
        get() = name.endsWith(".srt", ignoreCase = true) || name.endsWith(".vtt", ignoreCase = true)

    companion object {
        const val MIME_FOLDER = "application/vnd.google-apps.folder"
        const val ROOT_ID = "root"
        private val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".m4b", ".opus", ".flac", ".wav", ".ogg")
    }
}

/** A crumb in the folder path shown across the top of the browser. */
data class DriveCrumb(val id: String, val name: String)
