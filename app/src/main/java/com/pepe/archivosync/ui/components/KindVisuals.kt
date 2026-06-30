package com.pepe.archivosync.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TableView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pepe.archivosync.domain.model.FileKind
import com.pepe.archivosync.ui.theme.AppColors

/** Icon + accent color for a [FileKind], matching the design's KIND_ICON/COLOR. */
data class KindVisual(val icon: ImageVector, val color: Color)

fun FileKind.visual(): KindVisual = when (this) {
    FileKind.FOLDER -> KindVisual(Icons.Filled.Folder, AppColors.KindFolder)
    FileKind.IMAGE -> KindVisual(Icons.Filled.Image, AppColors.KindImage)
    FileKind.PDF -> KindVisual(Icons.Filled.PictureAsPdf, AppColors.KindPdf)
    FileKind.VIDEO -> KindVisual(Icons.Filled.Movie, AppColors.KindVideo)
    FileKind.ZIP -> KindVisual(Icons.Filled.FolderZip, AppColors.KindArchive)
    FileKind.TXT -> KindVisual(Icons.Filled.Description, AppColors.KindNeutral)
    FileKind.JSON -> KindVisual(Icons.Filled.DataObject, AppColors.KindNeutral)
    FileKind.CSV -> KindVisual(Icons.Filled.TableView, AppColors.KindData)
    FileKind.XLSX -> KindVisual(Icons.Filled.TableChart, AppColors.KindData)
    FileKind.DB -> KindVisual(Icons.Filled.Storage, AppColors.KindDb)
    FileKind.BIN -> KindVisual(Icons.Filled.Memory, AppColors.KindNeutral)
    FileKind.OTHER -> KindVisual(Icons.Filled.InsertDriveFile, AppColors.KindNeutral)
}
