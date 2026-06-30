package com.pepe.archivosync.domain.model

/** Logical kind of a file, used for icon + accent-color mapping in the UI. */
enum class FileKind {
    FOLDER, IMAGE, PDF, VIDEO, ZIP, TXT, JSON, CSV, XLSX, DB, BIN, OTHER;

    companion object {
        /** Best-effort kind from a file name / extension. */
        fun fromName(name: String): FileKind {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> IMAGE
                "pdf" -> PDF
                "mp4", "mkv", "mov", "avi", "webm" -> VIDEO
                "zip", "rar", "7z", "tar", "gz" -> ZIP
                "txt", "log", "md" -> TXT
                "json" -> JSON
                "csv" -> CSV
                "xls", "xlsx" -> XLSX
                "sql", "db", "sqlite" -> DB
                "bin", "iso", "img", "flac" -> BIN
                else -> OTHER
            }
        }
    }
}

/** The storage tree a [FileNode] belongs to. */
enum class StorageVolume { INTERNAL, SD_CARD }

/**
 * A node in a browsable storage tree. [id] is an opaque document URI string
 * (SAF) for real files, or a synthetic path for the bootstrap/demo tree.
 */
data class FileNode(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val childCount: Int = 0,
    val lastModified: Long = 0L,
    val kind: FileKind = if (isDirectory) FileKind.FOLDER else FileKind.fromName(name),
)
