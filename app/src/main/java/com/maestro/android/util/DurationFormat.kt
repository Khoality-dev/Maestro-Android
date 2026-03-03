package com.maestro.android.util

fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds < 0) return "--:--"
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}

fun formatDurationMs(ms: Long): String {
    if (ms < 0) return "--:--"
    return formatDuration(ms / 1000)
}
