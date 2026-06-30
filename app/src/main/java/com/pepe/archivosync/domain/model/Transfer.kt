package com.pepe.archivosync.domain.model

/** Lifecycle of an upload/backup of a single file. Mirrors checkpoint states. */
enum class TransferStatus { QUEUED, UPLOADING, DONE, FAILED }

/** Lifecycle of a remote→device download. */
enum class DownloadStatus { AVAILABLE, DOWNLOADING, DOWNLOADED }

/** Which mechanism moved (or is moving) the bytes. */
enum class TransferChannel { REST, CLOUD, P2P }

/**
 * One upload/backup record. Persisted in Room so progress survives process
 * death and the worker can resume from the last verified file.
 */
data class TransferItem(
    val id: String,
    val name: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val status: TransferStatus,
    val progress: Int,            // 0..100
    val destination: String,
    val channel: TransferChannel = TransferChannel.REST,
    val sourceUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val error: String? = null,
)

/** A file available on the remote that can be pulled back to the device. */
data class DownloadItem(
    val id: String,
    val name: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val status: DownloadStatus,
    val progress: Int,            // 0..100
    val remotePath: String? = null,
)
