package com.parroty.player.ui.pair

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.parroty.player.data.drive.DriveApi
import com.parroty.player.data.drive.DriveFile
import com.parroty.player.ui.ErrorBanner
import com.parroty.player.ui.LoadingBlock
import com.parroty.player.ui.theme.ParrotyPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairState(
    val loading: Boolean = true,
    val video: DriveFile? = null,
    val sourceOptions: List<DriveFile> = emptyList(),
    val chosenSource: DriveFile? = null,
    val chapterFiles: List<DriveFile> = emptyList(),
    val subtitleFiles: List<DriveFile> = emptyList(),
    val chosenChapters: DriveFile? = null,
    val chosenSubtitle: DriveFile? = null,
    val chapterCount: Int? = null,
    val error: String? = null,
    val authExpired: Boolean = false,
    val ready: Boolean = false
)

class PairViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository.get(app)
    private val drive = BookRepository.driveApi(app)

    private val _state = MutableStateFlow(PairState())
    val state: StateFlow<PairState> = _state.asStateFlow()

    private var loaded = false

    fun load(videoId: String, folderId: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            try {
                val video = drive.getMetadata(videoId)
                val companions = repo.findCompanions(video, folderId)
                _state.update {
                    it.copy(
                        loading = false,
                        video = video,
                        sourceOptions = companions.sourceOptions.ifEmpty { listOf(video) },
                        // Default to what was tapped in the browser. The mp3 is
                        // offered, not forced.
                        chosenSource = video,
                        chapterFiles = companions.chapterFiles,
                        subtitleFiles = companions.subtitleFiles,
                        chosenChapters = companions.suggestedChapters,
                        chosenSubtitle = companions.suggestedSubtitle
                    )
                }
                companions.suggestedChapters?.let { previewChapters(it) }
            } catch (e: DriveApi.AuthExpired) {
                _state.update { it.copy(loading = false, authExpired = true) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not read that folder.") }
            }
        }
    }

    fun chooseSource(file: DriveFile) {
        // Subtitles need a video track to draw on, so switching to the mp3 drops them.
        _state.update {
            it.copy(
                chosenSource = file,
                chosenSubtitle = if (file.isVideo) it.chosenSubtitle else null
            )
        }
    }

    fun chooseChapters(file: DriveFile?) {
        _state.update { it.copy(chosenChapters = file, chapterCount = null) }
        file?.let { previewChapters(it) }
    }

    fun chooseSubtitle(file: DriveFile?) {
        _state.update { it.copy(chosenSubtitle = file) }
    }

    /** Reads the file up front so a bad pairing is caught here, not in the player. */
    private fun previewChapters(file: DriveFile) {
        viewModelScope.launch {
            try {
                val chapters = repo.loadChapters(file.id)
                _state.update { it.copy(chapterCount = chapters.size) }
            } catch (e: Exception) {
                _state.update { it.copy(chapterCount = 0) }
            }
        }
    }

    fun confirm(folderId: String) {
        val state = _state.value
        val source = state.chosenSource ?: state.video ?: return
        viewModelScope.launch {
            repo.rememberBook(source, folderId, state.chosenChapters, state.chosenSubtitle)
            _state.update { it.copy(ready = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(
    videoId: String,
    folderId: String,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onAuthExpired: () -> Unit,
    viewModel: PairViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(videoId, folderId) { viewModel.load(videoId, folderId) }
    LaunchedEffect(state.authExpired) { if (state.authExpired) onAuthExpired() }
    LaunchedEffect(state.ready) {
        if (state.ready) onPlay(state.chosenSource?.id ?: videoId)
    }

    Scaffold(
        containerColor = ParrotyPalette.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Set up book") },
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
        if (state.loading) {
            LoadingBlock(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.error?.let { ErrorBanner(it) }

            Text(
                text = state.video?.name?.substringBeforeLast('.').orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                color = ParrotyPalette.Text
            )

            if (state.sourceOptions.size > 1) {
                PickerCard(
                    heading = "Play from",
                    blurb = "The mp4 is a still cover image wrapped around the same narration. " +
                        "The mp3 is smaller and lighter on battery, but has no subtitles.",
                    files = state.sourceOptions,
                    selected = state.chosenSource,
                    allowNone = false,
                    noneLabel = "",
                    onSelect = { file -> file?.let(viewModel::chooseSource) }
                )
            }

            PickerCard(
                heading = "Chapters",
                blurb = when {
                    state.chapterFiles.isEmpty() -> "No chapter file in this folder. The book will play without a chapter list."
                    state.chapterCount != null && state.chapterCount!! > 0 ->
                        "${state.chapterCount} chapters found."
                    state.chapterCount == 0 -> "That file has no timestamps in it. Try another."
                    else -> "Reading the file."
                },
                files = state.chapterFiles,
                selected = state.chosenChapters,
                allowNone = true,
                noneLabel = "No chapters",
                onSelect = viewModel::chooseChapters
            )

            if (state.chosenSource?.isVideo != false) {
                PickerCard(
                    heading = "Subtitles",
                    blurb = if (state.subtitleFiles.isEmpty()) {
                        "No .srt in this folder. Nothing to show."
                    } else {
                        "Optional. Turning subtitles on shows the video."
                    },
                    files = state.subtitleFiles,
                    selected = state.chosenSubtitle,
                    allowNone = true,
                    noneLabel = "No subtitles",
                    onSelect = viewModel::chooseSubtitle
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.confirm(folderId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.chosenSource != null
            ) {
                Text("Open book")
            }
        }
    }
}

@Composable
private fun PickerCard(
    heading: String,
    blurb: String,
    files: List<DriveFile>,
    selected: DriveFile?,
    allowNone: Boolean,
    noneLabel: String,
    onSelect: (DriveFile?) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = ParrotyPalette.Surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(heading, style = MaterialTheme.typography.titleMedium, color = ParrotyPalette.Text)
            Spacer(Modifier.height(4.dp))
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = ParrotyPalette.TextMuted)
            if (files.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                files.forEach { file ->
                    OptionRow(
                        label = file.name,
                        selected = selected?.id == file.id,
                        onClick = { onSelect(file) }
                    )
                }
                if (allowNone) {
                    OptionRow(
                        label = noneLabel,
                        selected = selected == null,
                        onClick = { onSelect(null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) ParrotyPalette.Text else ParrotyPalette.TextMuted
        )
    }
}
