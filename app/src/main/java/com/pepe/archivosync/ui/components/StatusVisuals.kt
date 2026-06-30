package com.pepe.archivosync.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.TransferStatus
import com.pepe.archivosync.ui.theme.AppColors

/** Icon + foreground/background colors for a status chip. */
data class StatusVisual(val icon: ImageVector, val fg: Color, val bg: Color)

fun statusVisual(status: TransferStatus, accent: Color): StatusVisual = when (status) {
    TransferStatus.UPLOADING -> StatusVisual(Icons.Filled.Sync, accent, accent.copy(alpha = 0.11f))
    TransferStatus.DONE -> StatusVisual(Icons.Filled.CheckCircle, AppColors.Success, AppColors.SuccessBg)
    TransferStatus.FAILED -> StatusVisual(Icons.Filled.Error, AppColors.Error, AppColors.ErrorBg)
    TransferStatus.QUEUED -> StatusVisual(Icons.Filled.Schedule, AppColors.Warning, AppColors.WarningBg)
}

fun downloadVisual(status: DownloadStatus, accent: Color): StatusVisual = when (status) {
    DownloadStatus.AVAILABLE -> StatusVisual(Icons.Filled.Cloud, AppColors.OnSurfaceVariant, AppColors.SurfaceAlt)
    DownloadStatus.DOWNLOADING -> StatusVisual(Icons.Filled.Downloading, accent, accent.copy(alpha = 0.11f))
    DownloadStatus.DOWNLOADED -> StatusVisual(Icons.Filled.CheckCircle, AppColors.Success, AppColors.SuccessBg)
}
