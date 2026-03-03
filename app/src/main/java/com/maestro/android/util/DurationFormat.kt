package com.maestro.android.util

fun formatDuration(seconds: Number?): String {
    if (seconds == null) return "--:--"
    val total = seconds.toLong()
    if (total < 0) return "--:--"
    val mins = total / 60
    val secs = total % 60
    return "%d:%02d".format(mins, secs)
}

fun formatDurationMs(ms: Long): String {
    if (ms < 0) return "--:--"
    return formatDuration(ms / 1000)
}
