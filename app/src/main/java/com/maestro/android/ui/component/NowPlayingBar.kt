package com.maestro.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.android.data.model.LoopMode
import com.maestro.android.data.model.PlaybackState
import com.maestro.android.data.model.PlayerState
import com.maestro.android.ui.theme.*
import com.maestro.android.util.formatDurationMs

@Composable
fun NowPlayingBar(
    state: PlayerState,
    onTogglePlayPause: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onCycleLoop: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack ?: return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Surface)
            .padding(12.dp)
    ) {
        // Progress bar
        val progress = if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Primary,
            trackColor = Border,
        )

        Spacer(Modifier.height(8.dp))

        // Track info + time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = TextColor,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.artist != null) {
                    Text(
                        text = track.artist,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${formatDurationMs(state.position)} / ${formatDurationMs(state.duration)}",
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Loop mode
            IconButton(onClick = onCycleLoop) {
                Icon(
                    imageVector = when (state.loopMode) {
                        LoopMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Loop: ${state.loopMode.name}",
                    tint = if (state.loopMode != LoopMode.OFF) Primary else TextMuted
                )
            }

            // Play/Pause
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (state.state == PlaybackState.PLAYING)
                        Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.state == PlaybackState.PLAYING) "Pause" else "Play",
                    tint = Primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Skip
            IconButton(onClick = onSkip) {
                Icon(Icons.Default.SkipNext, contentDescription = "Skip", tint = TextColor)
            }

            // Stop
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = TextMuted)
            }
        }

        // Volume slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.VolumeDown,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Slider(
                value = state.volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Primary,
                    activeTrackColor = Primary,
                    inactiveTrackColor = Border
                )
            )
            Icon(
                Icons.Default.VolumeUp,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
