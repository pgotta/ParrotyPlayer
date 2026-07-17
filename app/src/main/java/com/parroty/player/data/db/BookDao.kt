package com.parroty.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY lastPlayedAt DESC")
    fun observeRecent(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE fileId = :fileId LIMIT 1")
    suspend fun find(fileId: String): BookEntity?

    @Query("SELECT * FROM books WHERE fileId = :fileId LIMIT 1")
    fun observe(fileId: String): Flow<BookEntity?>

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Query("UPDATE books SET positionMs = :positionMs, durationMs = :durationMs, lastPlayedAt = :now WHERE fileId = :fileId")
    suspend fun savePosition(fileId: String, positionMs: Long, durationMs: Long, now: Long)

    @Query("UPDATE books SET localPath = :path WHERE fileId = :fileId")
    suspend fun setLocalPath(fileId: String, path: String?)

    @Query("DELETE FROM books WHERE fileId = :fileId")
    suspend fun delete(fileId: String)

    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAllBookmarks()

    @Query("DELETE FROM bookmarks WHERE fileId = :fileId")
    suspend fun deleteBookmarksFor(fileId: String)

    @Query("SELECT * FROM bookmarks WHERE fileId = :fileId ORDER BY positionMs ASC")
    fun observeBookmarks(fileId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)
}
