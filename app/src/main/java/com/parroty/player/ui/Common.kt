package com.parroty.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parroty.player.R
import com.parroty.player.ui.theme.ParrotyPalette

@Composable
fun ConnectScreen(
    busy: Boolean,
    error: String?,
    onConnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParrotyPalette.Bg)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.parrot_mark),
                contentDescription = "Parrot reading a book",
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = "Parroty Player",
                style = MaterialTheme.typography.headlineSmall,
                color = ParrotyPalette.Text
            )
            Text(
                text = "Play the audiobooks Parroty put on your Drive, with the chapter list attached.",
                style = MaterialTheme.typography.bodyMedium,
                color = ParrotyPalette.TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            if (busy) {
                CircularProgressIndicator(color = ParrotyPalette.Accent)
            } else {
                Button(onClick = onConnect) {
                    Text("Connect Google Drive")
                }
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                ErrorBanner(error)
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ParrotyPalette.WarnBg)
            .border(1.dp, ParrotyPalette.Gilt, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = ParrotyPalette.Spine
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = ParrotyPalette.Text
        )
    }
}

@Composable
fun EmptyState(headline: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(headline, style = MaterialTheme.typography.titleMedium, color = ParrotyPalette.Text)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = ParrotyPalette.TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = ParrotyPalette.Accent)
    }
}
