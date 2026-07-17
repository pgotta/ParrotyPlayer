package com.parroty.player.ui.library

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parroty.player.R
import com.parroty.player.data.BookRepository
import com.parroty.player.data.ChapterParser
import com.parroty.player.data.db.BookEntity
import com.parroty.player.ui.EmptyState
import com.parroty.player.ui.theme.ParrotyPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository.get(app)

    val books: StateFlow<List<BookEntity>> =
        repo.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun forget(fileId: String) = viewModelScope.launch { repo.forget(fileId) }
    fun forgetAll() = viewModelScope.launch { repo.forgetAll() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBrowseDrive: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenBook: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onAuthExpired: () -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    var addMenuOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ParrotyPalette.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Parroty Player") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ParrotyPalette.Bg,
                    titleContentColor = ParrotyPalette.Ink
                ),
                actions = {
                    // The empty state already offers both sources in the middle of
                    // the screen, so repeating them up here would be the same tap
                    // twice. These only appear once there is a library to add to.
                    if (books.isNotEmpty()) {
                        IconButton(onClick = { addMenuOpen = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add a book", tint = ParrotyPalette.Spine)
                        }
                        DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Google Drive") },
                                leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                                onClick = {
                                    addMenuOpen = false
                                    onBrowseDrive()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("This phone") },
                                leadingIcon = { Icon(Icons.Filled.PhoneAndroid, null) },
                                onClick = {
                                    addMenuOpen = false
                                    onOpenLocal()
                                }
                            )
                        }
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = ParrotyPalette.Ink)
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear library") },
                                onClick = {
                                    overflowOpen = false
                                    confirmClearAll = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (books.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.parrot_mark),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp)
                )
                Spacer(Modifier.height(20.dp))
                EmptyState(
                    headline = "No books open yet",
                    detail = "Pick the mp3 or mp4 Parroty made. If its chapter file is alongside it, that comes too."
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBrowseDrive) {
                        Icon(Icons.Filled.FolderOpen, null, tint = ParrotyPalette.Spine)
                        Spacer(Modifier.width(6.dp))
                        Text("Google Drive", color = ParrotyPalette.Spine)
                    }
                    OutlinedButton(onClick = onOpenLocal) {
                        Icon(Icons.Filled.PhoneAndroid, null, tint = ParrotyPalette.Spine)
                        Spacer(Modifier.width(6.dp))
                        Text("This phone", color = ParrotyPalette.Spine)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(books, key = { it.fileId }) { book ->
                    BookCard(
                        book = book,
                        onClick = { onOpenBook(book.fileId) },
                        onForget = { viewModel.forget(book.fileId) }
                    )
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = ParrotyPalette.Surface,
            title = { Text("Clear library?", color = ParrotyPalette.Ink) },
            text = {
                Text(
                    "Removes every book, its saved position and its bookmarks, and deletes anything saved for offline. Nothing in Drive or on the phone is touched.",
                    color = ParrotyPalette.InkSoft
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forgetAll()
                    confirmClearAll = false
                }) { Text("Clear", color = ParrotyPalette.Spine) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("Cancel", color = ParrotyPalette.InkSoft)
                }
            }
        )
    }
}

@Composable
private fun BookCard(book: BookEntity, onClick: () -> Unit, onForget: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val progress = if (book.durationMs > 0) {
        (book.positionMs.toFloat() / book.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ParrotyPalette.Surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.name.substringBeforeLast('.'),
                    style = MaterialTheme.typography.titleMedium,
                    color = ParrotyPalette.Ink,
                    modifier = Modifier.weight(1f)
                )
                if (book.isLocal) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = "On this phone",
                        tint = ParrotyPalette.InkSoft
                    )
                } else if (book.localPath != null) {
                    Icon(
                        Icons.Filled.CloudDone,
                        contentDescription = "Saved for offline",
                        tint = ParrotyPalette.Ok
                    )
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = ParrotyPalette.InkSoft)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove from library") },
                        onClick = {
                            menuOpen = false
                            onForget()
                        }
                    )
                }
            }
            Text(
                text = if (book.durationMs > 0) {
                    "${ChapterParser.formatTimestamp(book.positionMs)} of ${ChapterParser.formatTimestamp(book.durationMs)}"
                } else {
                    "Not started"
                },
                style = MaterialTheme.typography.labelMedium,
                color = ParrotyPalette.InkSoft
            )
            if (progress > 0f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = ParrotyPalette.Spine,
                    trackColor = ParrotyPalette.Rule
                )
            }
        }
    }
}
