package com.pepe.archivosync.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette mirrored from the ArchivoSync design (slate neutrals + a selectable
 * brand accent). These are the literal hex values used across the mockup.
 */
object AppColors {
    // Neutrals (Tailwind "slate" family used throughout the design)
    val Background = Color(0xFFEEF2F6)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF8FAFC)
    val SurfaceAlt = Color(0xFFF1F5F9)
    val Outline = Color(0xFFE2E8F0)
    val OutlineStrong = Color(0xFFCBD5E1)
    val OnSurface = Color(0xFF0F172A)
    val OnSurfaceVariant = Color(0xFF64748B)
    val OnSurfaceFaint = Color(0xFF94A3B8)
    val ScrimBar = Color(0xFF0F172A)

    // Semantic / status
    val Success = Color(0xFF16A34A)
    val SuccessBg = Color(0xFFF0FDF4)
    val Warning = Color(0xFFD97706)
    val WarningBg = Color(0xFFFFFBEB)
    val Error = Color(0xFFDC2626)
    val ErrorBg = Color(0xFFFEF2F6)
    val Info = Color(0xFF0EA5E9)

    // File-kind accents
    val KindFolder = Color(0xFFF59E0B)
    val KindImage = Color(0xFF0EA5E9)
    val KindPdf = Color(0xFFEF4444)
    val KindVideo = Color(0xFF8B5CF6)
    val KindArchive = Color(0xFFF59E0B)
    val KindData = Color(0xFF16A34A)
    val KindDb = Color(0xFF6366F1)
    val KindNeutral = Color(0xFF64748B)
}

/** Selectable brand accents exposed in Settings → Appearance. */
enum class AccentColor(val color: Color) {
    Blue(Color(0xFF2563EB)),
    Teal(Color(0xFF0D9488)),
    Purple(Color(0xFF7C3AED)),
    Orange(Color(0xFFEA580C));

    companion object {
        val Default = Blue
        fun fromName(name: String?): AccentColor =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Default
    }
}
