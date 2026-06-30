package com.pepe.archivosync.domain.repository

/** Abstracts WorkManager so use cases stay platform-agnostic. */
interface BackupScheduler {
    /** Enqueue a unique background backup run for the given upload record ids. */
    fun scheduleBackup(transferIds: List<String>)
}
