package com.parroty.player.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.parroty.player.auth.DriveAuth
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Feeds ExoPlayer straight from Drive.
 *
 * A token is attached per request rather than once per session: ExoPlayer opens a
 * fresh ranged request on every seek and every buffer refill, and a Google access
 * token is shorter lived than an audiobook.
 *
 * Caching the token is not enough on its own. Google invalidates tokens ahead of
 * their nominal hour, so a cached one can be dead while still looking fresh. When
 * that happens Drive answers 401 and, without the retry below, ExoPlayer reports
 * ERROR_CODE_IO_BAD_HTTP_STATUS and stops. Streaming from an already-filled buffer
 * hides this, so the failure shows up on the next seek, which makes it look like a
 * background or screen-off problem when it is really just elapsed time.
 *
 * So: on a 401 or 403, drop the cached token, fetch a new one, and open again.
 */
@UnstableApi
class DriveDataSource(
    private val appContext: Context,
    private val upstream: HttpDataSource
) : DataSource by upstream {

    override fun open(dataSpec: DataSpec): Long {
        return try {
            upstream.open(withToken(dataSpec, forceRefresh = false))
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 401 || e.responseCode == 403) {
                // The token was stale. Get a live one and try this exact range once
                // more. One retry only: if a fresh token is also rejected the grant
                // itself is gone and the UI needs to say so.
                upstream.close()
                upstream.open(withToken(dataSpec, forceRefresh = true))
            } else {
                throw e
            }
        }
    }

    private fun withToken(dataSpec: DataSpec, forceRefresh: Boolean): DataSpec {
        val token = DriveAuth.blockingToken(appContext, forceRefresh) ?: return dataSpec
        return dataSpec.withRequestHeaders(mapOf("Authorization" to "Bearer $token"))
    }

    class Factory(context: Context) : DataSource.Factory {
        private val appContext = context.applicationContext

        private val http = OkHttpDataSource.Factory(
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                // Drive occasionally closes an idle keep-alive connection while the
                // screen is off. Retrying is cheaper than surfacing it as an error.
                .retryOnConnectionFailure(true)
                .build()
        )

        private var listener: TransferListener? = null

        fun setTransferListener(transferListener: TransferListener?) = apply {
            listener = transferListener
            http.setTransferListener(transferListener)
        }

        override fun createDataSource(): DataSource =
            DriveDataSource(appContext, http.createDataSource())
    }

    companion object {
        fun factory(context: Context): DataSource.Factory = Factory(context)
    }
}
