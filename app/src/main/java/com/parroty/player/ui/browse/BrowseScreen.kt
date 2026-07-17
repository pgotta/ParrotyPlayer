package com.parroty.player.ui.browse

import android.app.Application
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.parroty.player.data.drive.DriveCrumb
import com.parroty.player.data.drive.DriveFile
import com.parroty.player.ui.EmptyState
import com.parroty.player.ui.ErrorBanner
import com.parroty.player.ui.LoadingBlock
import com.parroty.player.ui.theme.ParrotyPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseState(
    val crumbs: List<DriveCrumb> = listOf(DriveCrumb(DriveFile.ROOT_ID, "My Drive")),
    val entries: List<DriveFile> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val authExpired: Boolean = false
) {
    val currentFolderId: String get() = crumbs.last().id
}

class BrowseViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository.get(app)

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    fun loadRootIfNeeded() {
        if (_state.value.entries.isEmpty() && _state.value.error == null) {
            load(_state.value.currentFolderId)
        }
    }

    fun open(folder: DriveFile) {
        _state.update { it.copy(crumbs = it.crumbs + DriveCrumb(folder.id, folder.name)) }
        load(folder.id)
    }

    /** Returns false when already at My Drive, so the caller can leave the screen. */
    fun up(): Boolean {
        val crumbs = _state.value.crumbs
        if (crumbs.size <= 1) return false
        val trimmed = crumbs.dropLast(1)
        _state.update { it.copy(crumbs = trimmed) }
        load(trimmed.last().id)
        return true
    }

    fun jumpTo(index: Int) {
        val trimmed = _state.value.crumbs.take(index + 1)
        _state.update { it.copy(crumbs = trimmed) }
        load(trimmed.last().id)
    }

    fun retry() = load(_state.value.currentFolderId)

    private fun load(folderId: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val entries = repo.listFolder(folderId)
                // Folders first, then anything playable; the chapter and subtitle
                // files are companion data and belong on the pairing screen.
                val shown = entries.filter { it.isFolder || it.isPlayable }
                _state.update { it.copy(entries = shown, loading = false) }
            } catch (e: DriveApi.AuthExpired) {
                _state.update { it.copy(loading = false, authExpired = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Could not read that folder.")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onBack: () -> Unit,
    onPickVideo: (DriveFile, String) -> Unit,
    onAuthExpired: () -> Unit,
    viewModel: BrowseViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadRootIfNeeded() }
    LaunchedEffect(state.authExpired) { if (state.authExpired) onAuthExpired() }

    BackHandler { if (!viewModel.up()) onBack() }

    Scaffold(
        containerColor = ParrotyPalette.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Drive") },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.up()) onBack() }) {
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
        ) {
            Breadcrumbs(state.crumbs, onJump = viewModel::jumpTo)
            HorizontalDivider(color = ParrotyPalette.Border)

            when {
                state.loading -> LoadingBlock()
                state.error != null -> Column(Modifier.padding(16.dp)) {
                    ErrorBanner(state.error!!)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap to try again",
                        color = ParrotyPalette.Accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { viewModel.retry() }
                    )
                }
                state.entries.isEmpty() -> EmptyState(
                    headline = "Nothing to play here",
                    detail = "This folder has no sub-folders and no mp4 files."
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.id }) { entry ->
                        DriveRow(
                            entry = entry,
                            onClick = {
                                if (entry.isFolder) viewModel.open(entry)
                                else onPickVideo(entry, state.currentFolderId)
                            }
                        )
                        HorizontalDivider(color = ParrotyPalette.Border)
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(crumbs: List<DriveCrumb>, onJump: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(crumbs.size) { index ->
            val crumb = crumbs[index]
            val last = index == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = crumb.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (last) ParrotyPalette.Text else ParrotyPalette.TextMuted,
                    modifier = Modifier
                        .clickable(enabled = !last) { onJump(index) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
                if (!last) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ParrotyPalette.Border
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveRow(entry: DriveFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                entry.isFolder -> Icons.Filled.Folder
                entry.isAudio -> Icons.Filled.Headphones
                else -> Icons.Filled.Movie
            },
            contentDescription = null,
            tint = if (entry.isFolder) ParrotyPalette.TextMuted else ParrotyPalette.Accent
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = ParrotyPalette.Text
            )
            if (!entry.isFolder && entry.sizeBytes != null) {
                Text(
                    text = humanSize(entry.sizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = ParrotyPalette.TextMuted
                )
            }
        }
        if (entry.isFolder) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ParrotyPalette.Border)
        }
    }
}

private fun humanSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
}
