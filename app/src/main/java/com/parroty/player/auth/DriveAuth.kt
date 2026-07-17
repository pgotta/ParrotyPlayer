package com.parroty.player.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Holds the Google account grant for Drive.
 *
 * Uses Play Services' AuthorizationClient rather than a raw OAuth flow, so the
 * account chooser and consent screen are the standard Google ones and Play
 * Services owns the refresh token. Access tokens are short lived (roughly an
 * hour), which is shorter than a long audiobook, so every network read asks for
 * a token rather than holding one for the session.
 */
object DriveAuth {

    /**
     * Restricted scope. The app must stay in Testing on the Google Cloud console
     * unless it goes through Google verification; expect a re-consent prompt
     * roughly weekly.
     */
    const val DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"

    private val request: AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_READONLY)))
            .build()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    /**
     * Deliberately short. Google nominally issues hour-long tokens, but they get
     * invalidated early: a network change, Play Services refreshing in the
     * background, the 7-day testing grant rolling over. A short window means a
     * stale token is re-fetched rather than served until Drive rejects it.
     * Play Services caches the real token, so a miss here is cheap.
     */
    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10)

    sealed interface Outcome {
        /** Authorized. The token is live. */
        data class Granted(val accessToken: String) : Outcome

        /** The user must be shown the Google account / consent screen. */
        data class NeedsConsent(val pendingIntent: PendingIntent) : Outcome

        data class Failed(val message: String) : Outcome
    }

    /**
     * Asks for a token. Returns [Outcome.NeedsConsent] the first time, and after
     * the grant lapses. The caller is responsible for launching the intent.
     */
    suspend fun authorize(context: Context): Outcome = withContext(Dispatchers.IO) {
        try {
            val result: AuthorizationResult = Tasks.await(
                Identity.getAuthorizationClient(context.applicationContext).authorize(request)
            )
            resultToOutcome(result)
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: "Could not reach Google sign-in.")
        }
    }

    /**
     * Call from the activity result after the consent screen closes.
     */
    fun handleConsentResult(context: Context, data: Intent?): Outcome = try {
        val result = Identity.getAuthorizationClient(context.applicationContext)
            .getAuthorizationResultFromIntent(data)
        resultToOutcome(result)
    } catch (e: Exception) {
        Outcome.Failed(e.message ?: "Sign-in did not complete.")
    }

    private fun resultToOutcome(result: AuthorizationResult): Outcome {
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) return Outcome.NeedsConsent(pending)
        val token = result.accessToken
            ?: return Outcome.Failed("Google returned no access token.")
        cachedToken = token
        cachedAtMs = System.currentTimeMillis()
        return Outcome.Granted(token)
    }

    /**
     * A token for a network call, blocking the calling thread. ExoPlayer resolves
     * data specs on a loader thread, so this must never be called from main.
     *
     * Returns null when the grant has lapsed; the caller should surface a
     * re-sign-in prompt rather than retry.
     */
    fun blockingToken(context: Context, forceRefresh: Boolean = false): String? {
        val cached = cachedToken
        if (!forceRefresh &&
            cached != null &&
            System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS
        ) {
            return cached
        }
        if (forceRefresh) invalidate()
        return try {
            val result = Tasks.await(
                Identity.getAuthorizationClient(context.applicationContext).authorize(request),
                30, TimeUnit.SECONDS
            )
            when (val outcome = resultToOutcome(result)) {
                is Outcome.Granted -> outcome.accessToken
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Drops the cached token so the next read fetches a fresh one. */
    fun invalidate() {
        cachedToken = null
        cachedAtMs = 0L
    }

    fun isSignedIn(): Boolean = cachedToken != null

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        invalidate()
        try {
            Tasks.await(Identity.getAuthorizationClient(context.applicationContext).authorize(request))
        } catch (_: Exception) {
            // Nothing to revoke.
        }
        Unit
    }
}
