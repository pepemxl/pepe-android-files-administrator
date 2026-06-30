package com.pepe.archivosync.ui.screens.uploads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepe.archivosync.core.Formatters
import com.pepe.archivosync.domain.model.TransferItem
import com.pepe.archivosync.domain.model.TransferStatus
import com.pepe.archivosync.ui.components.AppCard
import com.pepe.archivosync.ui.components.CountFilterChip
import com.pepe.archivosync.ui.components.StatusChip
import com.pepe.archivosync.ui.components.ThinProgressBar
import com.pepe.archivosync.ui.components.statusVisual
import com.pepe.archivosync.ui.components.visual
import com.pepe.archivosync.ui.i18n.LocalStrings
import com.pepe.archivosync.ui.theme.AppColors
import com.pepe.archivosync.ui.theme.LocalAccent
import com.pepe.archivosync.ui.theme.MonoFamily

@Composable
fun UploadsScreen(viewModel: UploadsViewModel = hiltViewModel()) {
    val accent = LocalAccent.current
    val s = LocalStrings.current
    val ui by viewModel.state.collectAsStateWithLifecycle()

    val filters = listOf(
        null to s.histAll,
        TransferStatus.UPLOADING to s.histUploading,
        TransferStatus.DONE to s.histDone,
        TransferStatus.FAILED to s.histFailed,
        TransferStatus.QUEUED to s.histQueued,
    )

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                filters.forEach { (status, label) ->
                    CountFilterChip(label, ui.counts[status] ?: 0, ui.filter == status, accent) { viewModel.setFilter(status) }
                }
            }
        }
        if (ui.items.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CloudDone, null, tint = AppColors.OutlineStrong, modifier = Modifier.size(42.dp))
                    Text(s.histEmpty, color = AppColors.OnSurfaceFaint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            items(ui.items, key = { it.id }) { item ->
                Column(Modifier.padding(horizontal = 12.dp)) {
                    UploadCard(item, accent) { viewModel.retry(item.id) }
                }
            }
        }
    }
}

@Composable
private fun UploadCard(item: TransferItem, accent: androidx.compose.ui.graphics.Color, onRetry: () -> Unit) {
    val s = LocalStrings.current
    val kv = item.kind.visual()
    val sv = statusVisual(item.status, accent)
    val statusLabel = when (item.status) {
        TransferStatus.UPLOADING -> s.histUploading
        TransferStatus.DONE -> s.histDone
        TransferStatus.FAILED -> s.histFailed
        TransferStatus.QUEUED -> s.histQueued
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kv.icon, null, tint = kv.color, modifier = Modifier.size(27.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(item.name, fontSize = 14.sp, maxLines = 1)
                    Text(Formatters.bytes(item.sizeBytes), color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 11.sp)
                }
                StatusChip(statusLabel, sv.icon, sv.fg, sv.bg)
            }
            if (item.status == TransferStatus.UPLOADING || item.status == TransferStatus.QUEUED) {
                Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    ThinProgressBar(
                        item.progress,
                        if (item.status == TransferStatus.QUEUED) AppColors.OutlineStrong else accent,
                        Modifier.weight(1f),
                    )
                    Text(Formatters.percent(item.progress), color = AppColors.OnSurfaceVariant, fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
            }
            if (item.status == TransferStatus.FAILED) {
                Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.histErr, color = AppColors.Error, fontSize = 12.sp)
                    Surface(color = accent.copy(alpha = 0.11f), shape = RoundedCornerShape(7.dp), modifier = Modifier.clickable(onClick = onRetry)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Refresh, null, tint = accent, modifier = Modifier.size(16.dp))
                            Text(s.histRetry, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(top = 10.dp), color = AppColors.SurfaceAlt)
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = AppColors.OnSurfaceFaint, modifier = Modifier.size(13.dp))
                    Text("${s.histTo} ${item.destination}", color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 3.dp))
                }
            }
        }
    }
}
