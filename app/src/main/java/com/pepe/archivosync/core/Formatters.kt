package com.pepe.archivosync.core

import java.util.Locale

/** Display helpers shared across screens (sizes, speeds, percentages). */
object Formatters {

    fun bytes(bytes: Long): String {
        if (bytes <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024.0
            i++
        }
        return if (i == 0) "${bytes} B"
        else String.format(Locale.US, "%.1f %s", value, units[i])
    }

    /** Speed from a KB/s rate, matching the design's "KB/s" → "MB/s" rollover. */
    fun speedKbps(kbps: Long): String =
        if (kbps >= 1000) String.format(Locale.US, "%.1f MB/s", kbps / 1000.0)
        else "$kbps KB/s"

    fun percent(progress: Int): String = "$progress%"
}
