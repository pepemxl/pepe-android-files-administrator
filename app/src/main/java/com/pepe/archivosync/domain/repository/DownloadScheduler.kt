package com.pepe.archivosync.domain.repository

/** Abstracts WorkManager for downloads so callers stay platform-agnostic. */
interface DownloadScheduler {
    /** Enqueue a background download for one Downloads record id. */
    fun scheduleDownload(downloadId: String)
}
