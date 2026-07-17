package com.parroty.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.parroty.player.data.Chapter
import com.parroty.player.data.ChapterParser
import com.parroty.player.ui.ErrorBanner
import com.parroty.player.ui.LoadingBlock
import com.parroty.player.ui.theme.ParrotyPalette

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun PlayerScreen(
    fileId: String,
    onBack: () -> Unit,
    onAuthExpired: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val player by viewModel.player.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    // Survives rotation, which is the main reason anyone expands a video.
    var videoExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(fileId) { viewModel.start(fileId) }
    LaunchedEffect(state.authExpired) { if (state.authExpired) onAuthExpired() }

    val listState = rememberLazyListState()
    val currentChapter = state.currentChapter

    // Keep the playing chapter in view without fighting a manual scroll.
    LaunchedEffect(currentChapter?.index) {
        val index = currentChapter?.index ?: return@LaunchedEffect
        if (index !in listState.firstVisibleItemIndex..(listState.firstVisibleItemIndex + 6)) {
            listState.animateScrollToItem(index.coerceAtLeast(0))
        }
    }

    if (state.subtitlesOn && videoExpanded) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { videoExpanded = false },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Filled.FullscreenExit,
                    contentDescription = "Exit full screen",
                    tint = ParrotyPalette.Paper
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = ParrotyPalette.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.book?.name?.substringBeforeLast('.').orEmpty(),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ParrotyPalette.Text
                        )
                    }
                },
                actions = {
                    if (state.canShowSubtitles) {
                        IconButton(onClick = viewModel::toggleSubtitles) {
                            Icon(
                                Icons.Filled.Subtitles,
                                contentDescription = if (state.subtitlesOn) "Hide subtitles" else "Show subtitles",
                                tint = if (state.subtitlesOn) ParrotyPalette.Accent else ParrotyPalette.TextMuted
                            )
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = ParrotyPalette.Text)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state.isOffline) {
                            DropdownMenuItem(
                                text = { Text("Remove download") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.removeDownload()
                                }
                            )
                        } else if (state.canDownload) {
                            DropdownMenuItem(
                                text = { Text("Save for offline") },
                                leadingIcon = { Icon(Icons.Filled.CloudDownload, null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.downloadForOffline()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Playback speed") },
                            onClick = {
                                menuOpen = false
                                speedMenuOpen = true
                            }
                        )
                    }
                    DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                        listOf(0.8f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x") },
                                onClick = {
                                    viewModel.setSpeed(speed)
                                    speedMenuOpen = false
                                }
                            )
                        }
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
        ) {
            state.error?.let {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ErrorBanner(it, Modifier.clickable { viewModel.retryNow() })
                }
            }

            state.downloadProgress?.let { progress ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "Saving for offline — ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = ParrotyPalette.TextMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ParrotyPalette.Accent,
                        trackColor = ParrotyPalette.Rule
                    )
                }
            }

            // The video is a still cover image, so it only earns screen space when
            // there are subtitles to read off it.
            if (state.subtitlesOn) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(ParrotyPalette.Surface)
                ) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { view -> view.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { videoExpanded = true },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Icon(
                            Icons.Filled.Fullscreen,
                            contentDescription = "Full screen",
                            tint = ParrotyPalette.Paper
                        )
                    }
                }
            }

            NowPlaying(
                chapterTitle = currentChapter?.title ?: "Start of book",
                chapterOf = if (state.chapters.isEmpty()) null
                else "Chapter ${(currentChapter?.index ?: 0) + 1} of ${state.chapters.size}"
            )

            Scrubber(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                bufferedMs = state.bufferedMs,
                onSeek = viewModel::seekTo
            )

            Transport(
                isPlaying = state.isPlaying,
                onPrevious = viewModel::previousChapter,
                onBack10 = { viewModel.nudge(-10_000) },
                onToggle = viewModel::togglePlay,
                onForward30 = { viewModel.nudge(30_000) },
                onNext = viewModel::nextChapter,
                onBookmark = viewModel::addBookmark
            )

            HorizontalDivider(color = ParrotyPalette.Border)

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (state.bookmarks.isNotEmpty()) {
                    item {
                        SectionHeader("Bookmarks")
                    }
                    items(state.bookmarks, key = { "bm-${it.id}" }) { bookmark ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.seekTo(bookmark.positionMs) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                ChapterParser.formatTimestamp(bookmark.positionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = ParrotyPalette.Accent
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                bookmark.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ParrotyPalette.Text,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete bookmark",
                                    tint = ParrotyPalette.TextMuted
                                )
                            }
                        }
                    }
                }

                item { SectionHeader(if (state.chapters.isEmpty()) "No chapter file" else "Chapters") }

                items(state.chapters, key = { "ch-${it.index}" }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        playing = chapter.index == currentChapter?.index,
                        onClick = { viewModel.jumpToChapter(chapter) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = ParrotyPalette.TextMuted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun NowPlaying(chapterTitle: String, chapterOf: String?) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
        if (chapterOf != null) {
            Text(
                text = chapterOf,
                style = MaterialTheme.typography.labelMedium,
                color = ParrotyPalette.TextMuted
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = chapterTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = ParrotyPalette.Text,
            maxLines = 2
        )
    }
}

@Composable
private fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val max = durationMs.coerceAtLeast(1L).toFloat()
    val value = if (dragging) dragValue else positionMs.toFloat().coerceIn(0f, max)

    Column(Modifier.padding(horizontal = 16.dp)) {
        Slider(
            value = value,
            valueRange = 0f..max,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragValue.toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = ParrotyPalette.Accent,
                activeTrackColor = ParrotyPalette.Accent,
                inactiveTrackColor = ParrotyPalette.Rule
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                ChapterParser.formatTimestamp(value.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = ParrotyPalette.TextMuted
            )
            Text(
                if (durationMs > 0) {
                    "-" + ChapterParser.formatTimestamp(durationMs - value.toLong())
                } else {
                    ChapterParser.formatTimestamp(bufferedMs)
                },
                style = MaterialTheme.typography.labelMedium,
                color = ParrotyPalette.TextMuted
            )
        }
    }
}

@Composable
private fun Transport(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onBack10: () -> Unit,
    onToggle: () -> Unit,
    onForward30: () -> Unit,
    onNext: () -> Unit,
    onBookmark: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.SkipPrevious, "Previous chapter", tint = ParrotyPalette.Text)
        }
        IconButton(onClick = onBack10) {
            Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = ParrotyPalette.Text)
        }
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = ParrotyPalette.Accent,
                contentColor = ParrotyPalette.OnAccent
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp)
            )
        }
        IconButton(onClick = onForward30) {
            Icon(Icons.Filled.Forward30, "Forward 30 seconds", tint = ParrotyPalette.Text)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, "Next chapter", tint = ParrotyPalette.Text)
        }
        IconButton(onClick = onBookmark) {
            Icon(Icons.Filled.BookmarkAdd, "Bookmark this spot", tint = ParrotyPalette.Accent)
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, playing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (playing) ParrotyPalette.PaperDeep else ParrotyPalette.Paper)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // .chapter uses border-left: 4px solid var(--spine) to mark itself.
        Box(
            Modifier
                .width(4.dp)
                .height(30.dp)
                .background(if (playing) ParrotyPalette.Spine else Color.Transparent)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = ChapterParser.formatTimestamp(chapter.startMs),
            style = MaterialTheme.typography.labelMedium,
            color = if (playing) ParrotyPalette.Accent else ParrotyPalette.TextMuted
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.titleMedium,
            color = ParrotyPalette.Ink,
            modifier = Modifier.weight(1f)
        )
    }
}
