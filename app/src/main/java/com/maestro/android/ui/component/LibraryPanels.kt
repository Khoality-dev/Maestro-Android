package com.maestro.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.android.data.model.PlayerState
import com.maestro.android.data.model.Track
import com.maestro.android.ui.theme.Bg
import com.maestro.android.ui.theme.TextMuted

/** Songs downloaded to disk — always playable, no internet required. */
@Composable
fun SavedPanel(
    state: PlayerState,
    onPlay: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    onPlaySimilar: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    TrackListPanel(
        header = "Saved offline (${state.offlineLibrary.size})",
        tracks = state.offlineLibrary,
        downloadedIds = state.downloadedIds,
        currentTrackId = state.currentTrack?.id,
        emptyIcon = Icons.Default.DownloadDone,
        emptyText = "Songs you play are saved here automatically and play without internet",
        onPlay = onPlay,
        onEnqueue = onEnqueue,
        onPlaySimilar = onPlaySimilar,
        modifier = modifier,
    )
}

/** Recently played songs. */
@Composable
fun HistoryPanel(
    state: PlayerState,
    onPlay: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    onPlaySimilar: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    TrackListPanel(
        header = "Recently played",
        tracks = state.history,
        downloadedIds = state.downloadedIds,
        currentTrackId = state.currentTrack?.id,
        emptyIcon = Icons.Default.History,
        emptyText = "Nothing played yet",
        onPlay = onPlay,
        onEnqueue = onEnqueue,
        onPlaySimilar = onPlaySimilar,
        modifier = modifier,
    )
}

@Composable
private fun TrackListPanel(
    header: String,
    tracks: List<Track>,
    downloadedIds: Set<String>,
    currentTrackId: String?,
    emptyIcon: ImageVector,
    emptyText: String,
    onPlay: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    onPlaySimilar: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(Bg)) {
        Text(
            text = header,
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(emptyIcon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = emptyText,
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tracks, key = { it.id }) { track ->
                    TrackItem(
                        track = track,
                        onClick = { onPlay(track) },
                        onEnqueue = { onEnqueue(track) },
                        onPlaySimilar = { onPlaySimilar(track) },
                        isPlaying = track.id == currentTrackId,
                        isDownloaded = track.id in downloadedIds,
                    )
                }
            }
        }
    }
}
