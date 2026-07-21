package com.parroty.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Preserves every usable value from the original database while bringing
         * its tables up to the current schema. The old build used a destructive
         * fallback here, which meant an upgrade could silently erase the library,
         * resume positions and bookmarks.
         *
         * Rebuilding the tables also makes this tolerant of the different early
         * development schemas that existed before the repository was published.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateBooks(db)
                migrateBookmarks(db)
            }
        }

        private fun migrateBooks(db: SupportSQLiteDatabase) {
            val oldColumns = tableColumns(db, "books")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `books_new` (
                    `fileId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `folderId` TEXT NOT NULL,
                    `isLocal` INTEGER NOT NULL,
                    `hasVideo` INTEGER NOT NULL,
                    `chaptersFileId` TEXT,
                    `chaptersFileName` TEXT,
                    `subtitleFileId` TEXT,
                    `subtitleFileName` TEXT,
                    `positionMs` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `lastPlayedAt` INTEGER NOT NULL,
                    `localPath` TEXT,
                    PRIMARY KEY(`fileId`)
                )
                """.trimIndent()
            )

            if (oldColumns.isNotEmpty()) {
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `books_new` (
                        `fileId`, `name`, `folderId`, `isLocal`, `hasVideo`,
                        `chaptersFileId`, `chaptersFileName`, `subtitleFileId`,
                        `subtitleFileName`, `positionMs`, `durationMs`,
                        `lastPlayedAt`, `localPath`
                    )
                    SELECT
                        ${valueFor(oldColumns, "fileId", "''")},
                        ${valueFor(oldColumns, "name", "'Book'")},
                        ${valueFor(oldColumns, "folderId", "''")},
                        ${valueFor(oldColumns, "isLocal", "0")},
                        ${valueFor(oldColumns, "hasVideo", "1")},
                        ${valueFor(oldColumns, "chaptersFileId", "NULL")},
                        ${valueFor(oldColumns, "chaptersFileName", "NULL")},
                        ${valueFor(oldColumns, "subtitleFileId", "NULL")},
                        ${valueFor(oldColumns, "subtitleFileName", "NULL")},
                        ${valueFor(oldColumns, "positionMs", "0")},
                        ${valueFor(oldColumns, "durationMs", "0")},
                        ${valueFor(oldColumns, "lastPlayedAt", "0")},
                        ${valueFor(oldColumns, "localPath", "NULL")}
                    FROM `books`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `books`")
            }

            db.execSQL("ALTER TABLE `books_new` RENAME TO `books`")
        }

        private fun migrateBookmarks(db: SupportSQLiteDatabase) {
            val oldColumns = tableColumns(db, "bookmarks")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `bookmarks_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `fileId` TEXT NOT NULL,
                    `positionMs` INTEGER NOT NULL,
                    `label` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )

            if (oldColumns.isNotEmpty()) {
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `bookmarks_new` (
                        `id`, `fileId`, `positionMs`, `label`, `createdAt`
                    )
                    SELECT
                        ${valueFor(oldColumns, "id", "NULL")},
                        ${valueFor(oldColumns, "fileId", "''")},
                        ${valueFor(oldColumns, "positionMs", "0")},
                        ${valueFor(oldColumns, "label", "'Bookmark'")},
                        ${valueFor(oldColumns, "createdAt", "0")}
                    FROM `bookmarks`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `bookmarks`")
            }

            db.execSQL("ALTER TABLE `bookmarks_new` RENAME TO `bookmarks`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_bookmarks_fileId` " +
                    "ON `bookmarks` (`fileId`)"
            )
        }

        private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) columns += cursor.getString(nameIndex)
                }
            }
            return columns
        }

        private fun valueFor(columns: Set<String>, column: String, fallback: String): String =
            if (column in columns) "`$column`" else fallback

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parroty-player.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
