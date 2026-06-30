package com.pepe.archivosync.ui.screens.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepe.archivosync.core.Formatters
import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.model.StorageVolume
import com.pepe.archivosync.ui.components.visual
import com.pepe.archivosync.ui.i18n.LocalStrings
import com.pepe.archivosync.ui.theme.AppColors
import com.pepe.archivosync.ui.theme.LocalAccent
import com.pepe.archivosync.ui.theme.MonoFamily

@Composable
fun FilesScreen(viewModel: FilesViewModel) {
    val accent = LocalAccent.current
    val s = LocalStrings.current
    val ui by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { viewModel.onTreeGranted(ui.volume, it.toString()) } }

    Column(Modifier.fillMaxSize()) {
        // Volume tabs
        Surface(color = AppColors.Background) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = AppColors.Surface, border = BorderStroke(1.dp, AppColors.Outline)) {
                    Row(Modifier.padding(3.dp)) {
                        VolumeTab(Modifier.weight(1f), Icons.Filled.Smartphone, s.filesInternal, ui.volume == StorageVolume.INTERNAL, accent) {
                            viewModel.selectVolume(StorageVolume.INTERNAL)
                        }
                        VolumeTab(Modifier.weight(1f), Icons.Filled.SdCard, s.filesSdcard, ui.volume == StorageVolume.SD_CARD, accent) {
                            viewModel.selectVolume(StorageVolume.SD_CARD)
                        }
                    }
                }
                if (ui.granted) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            ui.crumbs.forEachIndexed { index, node ->
                                val last = index == ui.crumbs.lastIndex
                                Text(
                                    text = if (index == 0) volumeLabel(ui.volume, s) else node.name,
                                    color = if (last) AppColors.OnSurface else accent,
                                    fontSize = 13.sp,
                                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.clickable { viewModel.navigateToCrumb(index) },
                                )
                                if (!last) {
                                    Icon(Icons.Filled.ChevronRight, null, tint = AppColors.OutlineStrong, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.selectAllVisible() }.padding(4.dp),
                        ) {
                            Icon(
                                if (ui.allVisibleSelected) Icons.Filled.CheckBox else Icons.Filled.SelectAll,
                                null, tint = accent, modifier = Modifier.size(18.dp),
                            )
                            Text(s.filesSelectAll, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }

        when {
            !ui.granted -> GrantAccess(s.filesGrantTitle, s.filesGrantBtn) { picker.launch(null) }
            ui.items.isEmpty() -> EmptyFolder(s.filesEmpty)
            else -> LazyColumn(
                Modifier.fillMaxWidth().background(AppColors.Surface),
            ) {
                items(ui.items, key = { it.id }) { node ->
                    FileRow(node, selected = ui.selected.containsKey(node.id), accent = accent,
                        onCheck = { viewModel.toggleSelect(node) }, onRow = { viewModel.open(node) })
                }
            }
        }
    }
}

private fun volumeLabel(volume: StorageVolume, s: com.pepe.archivosync.ui.i18n.Strings) =
    if (volume == StorageVolume.INTERNAL) s.filesInternal else s.filesSdcard

@Composable
private fun VolumeTab(modifier: Modifier, icon: ImageVector, label: String, active: Boolean, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (active) AppColors.Surface else AppColors.Background.copy(alpha = 0f),
    ) {
        Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) accent else AppColors.OnSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(label, color = if (active) accent else AppColors.OnSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun FileRow(node: FileNode, selected: Boolean, accent: androidx.compose.ui.graphics.Color, onCheck: () -> Unit, onRow: () -> Unit) {
    val s = LocalStrings.current
    val kv = node.kind.visual()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) accent.copy(alpha = 0.11f) else AppColors.Surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clickable(onClick = onCheck).padding(start = 12.dp, end = 6.dp, top = 14.dp, bottom = 14.dp)) {
            Icon(
                if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                null, tint = if (selected) accent else AppColors.OutlineStrong, modifier = Modifier.size(23.dp),
            )
        }
        Row(
            Modifier.weight(1f).clickable(onClick = onRow).padding(end = 12.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(kv.icon, null, tint = kv.color, modifier = Modifier.size(25.dp))
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(node.name, fontSize = 14.sp, maxLines = 1)
                Text(
                    if (node.isDirectory) "${node.childCount} ${s.filesItems}" else Formatters.bytes(node.sizeBytes),
                    color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 11.sp,
                )
            }
            if (node.isDirectory) {
                Icon(Icons.Filled.ChevronRight, null, tint = AppColors.OutlineStrong, modifier = Modifier.size(22.dp))
            }
        }
    }
    androidx.compose.material3.HorizontalDivider(color = AppColors.SurfaceAlt)
}

@Composable
private fun EmptyFolder(message: String) {
    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.FolderOpen, null, tint = AppColors.OutlineStrong, modifier = Modifier.size(42.dp))
        Text(message, color = AppColors.OnSurfaceFaint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun GrantAccess(title: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.CreateNewFolder, null, tint = AppColors.OutlineStrong, modifier = Modifier.size(48.dp))
        Text(title, color = AppColors.OnSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))
        Button(onClick = onClick) { Text(button) }
    }
}
