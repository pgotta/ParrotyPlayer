package com.parroty.player.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.parroty.player.auth.DriveAuth
import com.parroty.player.ui.theme.ParrotyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParrotyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ParrotyApp()
                }
            }
        }
    }
}

/**
 * Everything sits behind a Drive grant, so the app either shows the connect
 * screen or the library. There is nothing useful to show while signed out.
 */
@Composable
private fun ParrotyApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var signedIn by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (val outcome = DriveAuth.handleConsentResult(context, result.data)) {
            is DriveAuth.Outcome.Granted -> {
                signedIn = true
                error = null
            }
            is DriveAuth.Outcome.Failed -> error = outcome.message
            is DriveAuth.Outcome.NeedsConsent -> error = "Google sign-in was cancelled."
        }
        checking = false
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Playback works either way; the notification is a convenience. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // A silent pass: if the grant is still live this returns a token with no UI.
        when (val outcome = DriveAuth.authorize(context)) {
            is DriveAuth.Outcome.Granted -> signedIn = true
            is DriveAuth.Outcome.NeedsConsent -> Unit
            is DriveAuth.Outcome.Failed -> error = outcome.message
        }
        checking = false
    }

    val startSignIn: () -> Unit = {
        error = null
        checking = true
        scope.launch {
            when (val outcome = DriveAuth.authorize(context)) {
                is DriveAuth.Outcome.Granted -> {
                    signedIn = true
                    checking = false
                }
                is DriveAuth.Outcome.NeedsConsent -> {
                    consentLauncher.launch(
                        IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build()
                    )
                }
                is DriveAuth.Outcome.Failed -> {
                    error = outcome.message
                    checking = false
                }
            }
        }
    }

    if (signedIn) {
        ParrotyNavHost(onAuthExpired = {
            signedIn = false
            DriveAuth.invalidate()
        })
    } else {
        ConnectScreen(
            busy = checking,
            error = error,
            onConnect = startSignIn
        )
    }
}
