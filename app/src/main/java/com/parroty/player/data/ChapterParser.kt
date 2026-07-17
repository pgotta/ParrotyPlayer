package com.parroty.player.data

data class Chapter(
    val index: Int,
    val startMs: Long,
    val title: String
)

/**
 * Reads the timestamp list Parroty writes for YouTube, e.g.
 *
 *   00:00 Chapter One: Departure
 *   04:12 Chapter Two: The Storm
 *   1:09:48 Chapter Three: Landfall
 *
 * Both MM:SS and HH:MM:SS appear in the same file once a book passes an hour,
 * so both are accepted. Anything that is not a timestamp line is skipped, which
 * covers blank lines and any header Parroty may add above the list.
 */
object ChapterParser {

    private val LINE = Regex("""^\s*(?:(\d{1,3}):)?(\d{1,2}):(\d{2})\s+(\S.*?)\s*$""")

    fun parse(text: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        text.lineSequence().forEach { line ->
            val m = LINE.find(line) ?: return@forEach
            val (h, mm, ss, title) = m.destructured
            val hours = h.toLongOrNull() ?: 0L
            val minutes = mm.toLongOrNull() ?: return@forEach
            val seconds = ss.toLongOrNull() ?: return@forEach
            if (seconds >= 60) return@forEach
            val startMs = ((hours * 3600) + (minutes * 60) + seconds) * 1000
            chapters.add(Chapter(index = chapters.size, startMs = startMs, title = title.trim()))
        }
        return chapters
            .sortedBy { it.startMs }
            .mapIndexed { i, c -> c.copy(index = i) }
    }

    /** The chapter playing at [positionMs], or null before the first one. */
    fun chapterAt(chapters: List<Chapter>, positionMs: Long): Chapter? =
        chapters.lastOrNull { it.startMs <= positionMs }

    fun formatTimestamp(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}
