package com.parroty.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Parroty audiobook the user has opened, keyed by its Drive file id so that
 * renaming or moving the mp4 in Drive never loses the saved position.
 */
@Entity(tableName = "books")
data class BookEntity(
    /**
     * A Drive file id, or for a local book the SAF content:// uri as a string.
     * Either way it is stable across renames, which is the point.
     */
    @PrimaryKey val fileId: String,
    val name: String,
    val folderId: String,
    /** True when this came from the phone rather than Drive. */
    val isLocal: Boolean = false,
    /** False for the mp3. Subtitles need a video surface, so they are only
     *  offered when this is true. */
    val hasVideo: Boolean = true,
    val chaptersFileId: String?,
    val chaptersFileName: String?,
    val subtitleFileId: String?,
    val subtitleFileName: String?,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    /** Set once the mp4 has been pulled down for offline listening. */
    val localPath: String? = null
)

@Entity(
    tableName = "bookmarks",
    indices = [Index("fileId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: String,
    val positionMs: Long,
    val label: String,
    val createdAt: Long
)
