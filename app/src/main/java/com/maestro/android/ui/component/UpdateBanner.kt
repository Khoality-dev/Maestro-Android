package com.maestro.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maestro.android.update.AvailableUpdate
import com.maestro.android.update.InstallProgress

@Composable
fun UpdateBanner(
    update: AvailableUpdate,
    progress: InstallProgress,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Update available: v${update.versionName}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    val sub = when (progress) {
                        is InstallProgress.Idle -> sizeLabel(update.sizeBytes)
                        is InstallProgress.Downloading -> {
                            val pct = if (progress.totalBytes > 0) {
                                (progress.bytesRead * 100 / progress.totalBytes).toInt()
                            } else 0
                            "Downloading $pct%"
                        }
                        InstallProgress.Installing -> "Installing…"
                        is InstallProgress.Failed -> "Failed: ${progress.message}"
                    }
                    Text(text = sub, fontSize = 11.sp)
                }
                when (progress) {
                    is InstallProgress.Idle, is InstallProgress.Failed -> {
                        TextButton(onClick = onUpdate) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Update")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                    else -> {}
                }
            }
            if (progress is InstallProgress.Downloading && progress.totalBytes > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.bytesRead.toFloat() / progress.totalBytes.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun sizeLabel(bytes: Long): String {
    if (bytes <= 0) return "Tap Update to install"
    val mb = bytes.toDouble() / (1024 * 1024)
    return "%.1f MB".format(mb)
}
