package com.kuro.app.core.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.0f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.1f GB", gb)
}

fun formatDate(millis: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return fmt.format(Date(millis))
}

fun formatDateTime(millis: Long): String {
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.US)
    return fmt.format(Date(millis))
}

fun formatRelative(millis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - millis
    if (diff < 0) return formatDate(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    if (minutes < 1) return "Just now"
    if (minutes < 60) return "$minutes min ago"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours < 24) return "$hours hr ago"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    if (days < 7) return "$days d ago"
    return formatDate(millis)
}
