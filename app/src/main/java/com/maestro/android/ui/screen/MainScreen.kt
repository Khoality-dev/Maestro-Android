package com.maestro.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.maestro.android.data.model.PlaybackState
import com.maestro.android.ui.component.HistoryPanel
import com.maestro.android.ui.component.NowPlayingBar
import com.maestro.android.ui.component.QueuePanel
import com.maestro.android.ui.component.SavedPanel
import com.maestro.android.ui.component.SearchPanel
import com.maestro.android.ui.component.UpdateBanner
import com.maestro.android.ui.theme.Bg
import com.maestro.android.ui.theme.Primary
import com.maestro.android.ui.theme.Surface
import com.maestro.android.ui.theme.TextMuted
import com.maestro.android.ui.viewmodel.PlayerViewModel
import com.maestro.android.update.UpdateViewModel
import kotlinx.coroutines.launch

private enum class MainTab(val label: String, val icon: ImageVector) {
    SEARCH("Search", Icons.Default.Search),
    SAVED("Saved", Icons.Default.DownloadDone),
    HISTORY("History", Icons.Default.History),
    QUEUE("Queue", Icons.Default.QueueMusic),
}

@Composable
fun MainScreen(viewModel: PlayerViewModel, updateViewModel: UpdateViewModel) {
    val playerState by viewModel.state.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val availableUpdate by updateViewModel.available.collectAsState()
    val updateProgress by updateViewModel.progress.collectAsState()

    val tabs = MainTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // Re-scan the cache whenever the Saved tab comes into view, so freshly downloaded songs appear.
    LaunchedEffect(pagerState.currentPage) {
        if (tabs[pagerState.currentPage] == MainTab.SAVED) viewModel.refreshDownloaded()
    }

    val isPlaying = playerState.state != PlaybackState.STOPPED && playerState.currentTrack != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
    ) {
        availableUpdate?.let { update ->
            UpdateBanner(
                update = update,
                progress = updateProgress,
                onUpdate = updateViewModel::startUpdate,
                onDismiss = updateViewModel::dismiss,
            )
        }

        // Tab bar
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Surface,
            contentColor = Primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
        ) {
            tabs.forEachIndexed { index, tab ->
                val badgeCount = if (tab == MainTab.QUEUE) playerState.queue.size else 0
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (badgeCount > 0) {
                                    Badge(containerColor = Primary) { Text("$badgeCount") }
                                }
                            }
                        ) {
                            Icon(tab.icon, contentDescription = null)
                        }
                    },
                    text = { Text(tab.label) },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextMuted
                )
            }
        }

        // Page content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (tabs[page]) {
                MainTab.SEARCH -> SearchPanel(
                    searchResults = searchResults,
                    isSearching = isSearching,
                    searchError = searchError,
                    downloadedIds = playerState.downloadedIds,
                    onSearch = viewModel::search,
                    onPlay = viewModel::playOrEnqueue,
                    onEnqueue = viewModel::enqueue,
                    onPlaySimilar = viewModel::playSimilar,
                )
                MainTab.SAVED -> SavedPanel(
                    state = playerState,
                    onPlay = viewModel::play,
                    onEnqueue = viewModel::enqueue,
                    onPlaySimilar = viewModel::playSimilar,
                )
                MainTab.HISTORY -> HistoryPanel(
                    state = playerState,
                    onPlay = viewModel::play,
                    onEnqueue = viewModel::enqueue,
                    onPlaySimilar = viewModel::playSimilar,
                )
                MainTab.QUEUE -> QueuePanel(
                    state = playerState,
                    onPlayTrack = viewModel::play,
                    onRemoveFromQueue = viewModel::removeFromQueue,
                    onClearQueue = viewModel::clearQueue
                )
            }
        }

        // Now Playing bar at bottom
        if (isPlaying) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            NowPlayingBar(
                state = playerState,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkip = viewModel::skip,
                onStop = viewModel::stop,
                onCycleLoop = viewModel::cycleLoopMode,
                onToggleAutoplay = viewModel::toggleAutoplaySimilar,
                onVolumeChange = viewModel::setVolume
            )
        }
    }
}
