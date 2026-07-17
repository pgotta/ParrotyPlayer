package com.parroty.player.ui.local

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parroty.player.data.BookRepository
import com.parroty.player.ui.theme.ParrotyPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalPick(val uri: Uri, val name: String)

data class LocalSetupState(
    val media: LocalPick? = null,
    val chapters: LocalPick? = null,
    val subtitle: LocalPick? = null,
    val chapterCount: Int? = null,
    val openedFileId: String? = null,
    val error: String? = null
)

class LocalSetupViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository.get(app)

    private val _state = MutableStateFlow(LocalSetupState())
    val state: StateFlow<LocalSetupState> = _state.asStateFlow()

    private fun pick(uri: Uri): LocalPick {
        // Without a persisted grant the uri dies when the process does.
        repo.persistPermission(uri)
        return LocalPick(uri, repo.displayName(uri) ?: uri.lastPathSegment.orEmpty())
    }

    fun setMedia(uri: Uri) = _state.update { it.copy(media = pick(uri)) }

    fun setChapters(uri: Uri) {
        val picked = pick(uri)
        _state.update { it.copy(chapters = picked, chapterCount = null) }
        viewModelScope.launch {
            try {
                _state.update { it.copy(chapterCount = repo.loadChapters(uri.toString()).size) }
            } catch (e: Exception) {
                _state.update { it.copy(chapterCount = 0) }
            }
        }
    }

    fun setSubtitle(uri: Uri) = _state.update { it.copy(subtitle = pick(uri)) }

    fun clearChapters() = _state.update { it.copy(chapters = null, chapterCount = null) }
    fun clearSubtitle() = _state.update { it.copy(subtitle = null) }

    fun open() {
        val media = _state.value.media ?: return
        viewModelScope.launch {
            try {
                val fileId = repo.rememberLocalBook(
                    mediaUri = media.uri,
                    chaptersUri = _state.value.chapters?.uri,
                    subtitleUri = _state.value.subtitle?.uri
                )
                _state.update { it.copy(openedFileId = fileId) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not open that file.") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSetupScreen(
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    viewModel: LocalSetupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.openedFileId) {
        state.openedFileId?.let(onPlay)
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::setMedia) }

    val chaptersLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::setChapters) }

    val subtitleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::setSubtitle) }

    Scaffold(
        containerColor = ParrotyPalette.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Open from phone") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ParrotyPalette.Text
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ParrotyPalette.Bg,
                    titleContentColor = ParrotyPalette.Text
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PickRow(
                heading = "Audiobook",
                value = state.media?.name,
                blurb = "The mp3 or mp4 Parroty made.",
                onPick = {
                    mediaLauncher.launch(arrayOf("audio/*", "video/*"))
                },
                onClear = null
            )

            PickRow(
                heading = "Chapters",
                value = state.chapters?.name,
                blurb = when {
                    state.chapterCount != null && state.chapterCount!! > 0 ->
                        "${state.chapterCount} chapters found."
                    state.chapterCount == 0 -> "No timestamps in that file. Try another."
                    else -> "Optional. The youtube_chapters txt file."
                },
                onPick = {
                    // Some file providers report .txt as octet-stream, so this
                    // cannot filter on text/plain alone without hiding the file.
                    chaptersLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                },
                onClear = viewModel::clearChapters
            )

            PickRow(
                heading = "Subtitles",
                value = state.subtitle?.name,
                blurb = "Optional. The .srt file. Only works with an mp4.",
                onPick = {
                    subtitleLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*"))
                },
                onClear = viewModel::clearSubtitle
            )

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ParrotyPalette.Spine)
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = viewModel::open,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.media != null
            ) {
                Text("Open book")
            }
        }
    }
}

@Composable
private fun PickRow(
    heading: String,
    value: String?,
    blurb: String,
    onPick: () -> Unit,
    onClear: (() -> Unit)?
) {
    Card(colors = CardDefaults.cardColors(containerColor = ParrotyPalette.Surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(heading, style = MaterialTheme.typography.titleMedium, color = ParrotyPalette.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                text = value ?: blurb,
                style = if (value != null) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.bodySmall,
                color = if (value != null) ParrotyPalette.Ink else ParrotyPalette.InkSoft
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPick) {
                    Text(if (value == null) "Choose file" else "Change", color = ParrotyPalette.Spine)
                }
                if (value != null && onClear != null) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onClear) {
                        Text("Remove", color = ParrotyPalette.InkSoft)
                    }
                }
            }
        }
    }
}
