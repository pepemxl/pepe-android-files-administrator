package com.pepe.archivosync.ui.screens.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepe.archivosync.core.Formatters
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.ui.AppViewModel
import com.pepe.archivosync.ui.components.AppCard
import com.pepe.archivosync.ui.components.ThinProgressBar
import com.pepe.archivosync.ui.components.visual
import com.pepe.archivosync.ui.i18n.LocalStrings
import com.pepe.archivosync.ui.theme.AppColors
import com.pepe.archivosync.ui.theme.LocalAccent
import com.pepe.archivosync.ui.theme.MonoFamily

@Composable
fun DownloadsScreen(
    appViewModel: AppViewModel,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val accent = LocalAccent.current
    val s = LocalStrings.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = accent.copy(alpha = 0.11f), shape = RoundedCornerShape(9.dp), modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Cloud, null, tint = accent, modifier = Modifier.size(20.dp)) }
                    }
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(s.dlRemoteAt.uppercase(), color = AppColors.OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(settings.remoteLabel, fontFamily = MonoFamily, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
        items(items, key = { it.id }) { item ->
            DownloadCard(item, accent) { viewModel.startDownload(item.id) }
        }
    }
}

@Composable
private fun DownloadCard(item: DownloadItem, accent: Color, onDownload: () -> Unit) {
    val s = LocalStrings.current
    val kv = item.kind.visual()
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kv.icon, null, tint = kv.color, modifier = Modifier.size(27.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(item.name, fontSize = 14.sp, maxLines = 1)
                    Text(Formatters.bytes(item.sizeBytes), color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 11.sp)
                }
                when (item.status) {
                    DownloadStatus.AVAILABLE -> Surface(color = accent, shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable(onClick = onDownload)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(s.dlDownload, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 5.dp))
                        }
                    }
                    DownloadStatus.DOWNLOADED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = AppColors.Success, modifier = Modifier.size(18.dp))
                        Text(s.dlDownloaded, color = AppColors.Success, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 5.dp))
                    }
                    DownloadStatus.DOWNLOADING -> {}
                }
            }
            if (item.status == DownloadStatus.DOWNLOADING) {
                Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    ThinProgressBar(item.progress, accent, Modifier.weight(1f))
                    Text(Formatters.percent(item.progress), color = AppColors.OnSurfaceVariant, fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}
