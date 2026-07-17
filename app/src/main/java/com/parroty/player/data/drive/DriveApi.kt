package com.parroty.player.data.drive

import android.content.Context
import com.parroty.player.auth.DriveAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin Drive v3 client. Only three things are needed: list a folder, read a
 * small text file, and hand ExoPlayer a URL for the mp4.
 */
class DriveApi(
    private val appContext: Context,
    private val client: OkHttpClient = defaultClient()
) {

    class AuthExpired : IOException("Google sign-in has expired.")

    /**
     * Streaming URL for an mp4. Drive honours HTTP Range on this endpoint, which
     * is what gives exact chapter seeking without downloading the file.
     * The Authorization header is attached per-request by DriveDataSource.
     */
    fun mediaUrl(fileId: String): String = "$BASE/files/$fileId?alt=media&supportsAllDrives=true"

    suspend fun listFolder(folderId: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = StringBuilder("$BASE/files")
                .append("?q=").append(urlEncode("'$folderId' in parents and trashed = false"))
                .append("&fields=").append(urlEncode("nextPageToken,files(id,name,mimeType,size,modifiedTime)"))
                .append("&orderBy=").append(urlEncode("folder,name"))
                .append("&pageSize=200")
                .append("&supportsAllDrives=true&includeItemsFromAllDrives=true")
                .apply { if (pageToken != null) append("&pageToken=").append(urlEncode(pageToken!!)) }
                .toString()

            val body = get(url)
            val json = JSONObject(body)
            val files = json.optJSONArray("files")
            if (files != null) {
                for (i in 0 until files.length()) {
                    out.add(files.getJSONObject(i).toDriveFile())
                }
            }
            pageToken = json.optString("nextPageToken").ifEmpty { null }
        } while (pageToken != null)
        out
    }

    suspend fun getMetadata(fileId: String): DriveFile = withContext(Dispatchers.IO) {
        val url = "$BASE/files/$fileId?fields=" +
            urlEncode("id,name,mimeType,size,modifiedTime") + "&supportsAllDrives=true"
        JSONObject(get(url)).toDriveFile()
    }

    /** For chapter .txt and subtitle .srt files, which are a few KB at most. */
    suspend fun readText(fileId: String): String = withContext(Dispatchers.IO) {
        get(mediaUrl(fileId))
    }

    /** Streams bytes to [onChunk], reporting progress. Used for offline downloads. */
    suspend fun download(
        fileId: String,
        onChunk: (ByteArray, Int) -> Unit,
        onProgress: (bytesRead: Long, total: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val response = client.newCall(authed(mediaUrl(fileId))).execute()
        response.use {
            if (it.code == 401 || it.code == 403) {
                DriveAuth.invalidate()
                throw AuthExpired()
            }
            if (!it.isSuccessful) throw IOException("Drive returned ${it.code} for $fileId")
            val body = it.body ?: throw IOException("Drive sent an empty response.")
            val total = body.contentLength()
            val buffer = ByteArray(64 * 1024)
            var read: Long = 0
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    onChunk(buffer, n)
                    read += n
                    onProgress(read, total)
                }
            }
        }
    }

    private fun get(url: String): String {
        client.newCall(authed(url)).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                DriveAuth.invalidate()
                throw AuthExpired()
            }
            if (!response.isSuccessful) throw IOException("Drive returned ${response.code}")
            return response.body?.string() ?: throw IOException("Drive sent an empty response.")
        }
    }

    private fun authed(url: String): Request {
        val token = DriveAuth.blockingToken(appContext) ?: throw AuthExpired()
        return Request.Builder().url(url).header("Authorization", "Bearer $token").build()
    }

    private fun JSONObject.toDriveFile() = DriveFile(
        id = getString("id"),
        name = optString("name", "Untitled"),
        mimeType = optString("mimeType", ""),
        sizeBytes = optString("size").toLongOrNull(),
        modifiedTime = optString("modifiedTime").ifEmpty { null }
    )

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        private const val BASE = "https://www.googleapis.com/drive/v3"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
