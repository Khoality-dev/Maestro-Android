package com.maestro.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maestro.android.data.model.PlaybackState
import com.maestro.android.ui.component.NowPlayingBar
import com.maestro.android.ui.component.QueuePanel
import com.maestro.android.ui.component.SearchPanel
import com.maestro.android.ui.theme.Bg
import com.maestro.android.ui.theme.Primary
import com.maestro.android.ui.theme.Surface
import com.maestro.android.ui.theme.TextMuted
import com.maestro.android.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: PlayerViewModel) {
    val playerState by viewModel.state.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    val isPlaying = playerState.state != PlaybackState.STOPPED && playerState.currentTrack != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
    ) {
        // Tab bar
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Surface,
            contentColor = Primary,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    color = Primary
                )
            }
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                text = { Text("Search") },
                selectedContentColor = Primary,
                unselectedContentColor = TextMuted
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                icon = {
                    BadgedBox(
                        badge = {
                            if (playerState.queue.isNotEmpty()) {
                                Badge(containerColor = Primary) {
                                    Text("${playerState.queue.size}")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.QueueMusic, contentDescription = null)
                    }
                },
                text = { Text("Queue") },
                selectedContentColor = Primary,
                unselectedContentColor = TextMuted
            )
        }

        // Page content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> SearchPanel(
                    searchResults = searchResults,
                    history = playerState.history,
                    isSearching = isSearching,
                    searchError = searchError,
                    serverUrl = serverUrl,
                    onSearch = viewModel::search,
                    onPlay = viewModel::playOrEnqueue,
                    onEnqueue = viewModel::enqueue,
                    onServerUrlChange = viewModel::updateServerUrl
                )
                1 -> QueuePanel(
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
                onVolumeChange = viewModel::setVolume
            )
        }
    }
}
