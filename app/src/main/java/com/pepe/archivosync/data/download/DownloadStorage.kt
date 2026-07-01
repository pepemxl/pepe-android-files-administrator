package com.pepe.archivosync.data.download

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves where downloaded files land on disk. Uses the app-specific external
 * files dir (`Android/data/<pkg>/files/Download/ArchivoSync`) so no runtime
 * storage permission is needed on any API level — consistent with the app's
 * SAF-only read model. Names are sanitized and de-duplicated.
 */
@Singleton
class DownloadStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun baseDir(): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(root, "ArchivoSync").apply { mkdirs() }
    }

    /** A not-yet-existing destination file for [name] (adds a numeric suffix on clash). */
    fun fileFor(name: String): File {
        val dir = baseDir()
        val safe = sanitize(name).ifBlank { "download.bin" }
        var candidate = File(dir, safe)
        if (!candidate.exists()) return candidate
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($i)$ext")
            i++
        }
        return candidate
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
