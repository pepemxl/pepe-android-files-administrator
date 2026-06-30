package com.pepe.archivosync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.FileKind
import com.pepe.archivosync.domain.model.TransferChannel
import com.pepe.archivosync.domain.model.TransferItem
import com.pepe.archivosync.domain.model.TransferStatus

@Entity(tableName = "uploads")
data class TransferEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val sizeBytes: Long,
    val status: String,
    val progress: Int,
    val destination: String,
    val channel: String,
    val sourceUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String?,
)

fun TransferEntity.toDomain() = TransferItem(
    id = id,
    name = name,
    kind = runCatching { FileKind.valueOf(kind) }.getOrDefault(FileKind.OTHER),
    sizeBytes = sizeBytes,
    status = runCatching { TransferStatus.valueOf(status) }.getOrDefault(TransferStatus.QUEUED),
    progress = progress,
    destination = destination,
    channel = runCatching { TransferChannel.valueOf(channel) }.getOrDefault(TransferChannel.REST),
    sourceUri = sourceUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    error = error,
)

fun TransferItem.toEntity() = TransferEntity(
    id = id,
    name = name,
    kind = kind.name,
    sizeBytes = sizeBytes,
    status = status.name,
    progress = progress,
    destination = destination,
    channel = channel.name,
    sourceUri = sourceUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    error = error,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val sizeBytes: Long,
    val status: String,
    val progress: Int,
    val remotePath: String?,
)

fun DownloadEntity.toDomain() = DownloadItem(
    id = id,
    name = name,
    kind = runCatching { FileKind.valueOf(kind) }.getOrDefault(FileKind.OTHER),
    sizeBytes = sizeBytes,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.AVAILABLE),
    progress = progress,
    remotePath = remotePath,
)

fun DownloadItem.toEntity() = DownloadEntity(
    id = id,
    name = name,
    kind = kind.name,
    sizeBytes = sizeBytes,
    status = status.name,
    progress = progress,
    remotePath = remotePath,
)
