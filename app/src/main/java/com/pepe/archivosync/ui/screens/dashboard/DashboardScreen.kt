package com.pepe.archivosync.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RuleFolder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepe.archivosync.core.Formatters
import com.pepe.archivosync.domain.model.TransferStatus
import com.pepe.archivosync.ui.AppViewModel
import com.pepe.archivosync.ui.ConnStatus
import com.pepe.archivosync.ui.components.AppCard
import com.pepe.archivosync.ui.components.StatusChip
import com.pepe.archivosync.ui.components.statusVisual
import com.pepe.archivosync.ui.components.visual
import com.pepe.archivosync.ui.i18n.LocalStrings
import com.pepe.archivosync.ui.navigation.Destination
import com.pepe.archivosync.ui.theme.AppColors
import com.pepe.archivosync.ui.theme.LocalAccent
import com.pepe.archivosync.ui.theme.MonoFamily

@Composable
fun DashboardScreen(
    appViewModel: AppViewModel,
    onNavigate: (Destination) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val accent = LocalAccent.current
    val s = LocalStrings.current
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val connStatus by appViewModel.connStatus.collectAsStateWithLifecycle()
    val ui by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Connection card
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        TintIcon(Icons.Filled.Dns, accent)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(s.dashServerLabel.uppercase(), color = AppColors.OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(settings.remoteLabel, fontFamily = MonoFamily, fontSize = 13.sp, maxLines = 1)
                        }
                        ConnectionChip(connStatus)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = accent.copy(alpha = 0.11f),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.weight(1f).clickable { appViewModel.test() },
                        ) {
                            Row(
                                Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.WifiTethering, null, tint = accent, modifier = Modifier.size(18.dp))
                                Text(s.dashTest, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        Surface(
                            color = AppColors.SurfaceAlt,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.size(44.dp).clickable { onNavigate(Destination.Settings) },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Tune, null, tint = AppColors.OnSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // Server profiles (create / switch / edit / delete)
        item {
            ServersCard(settings, appViewModel, accent)
        }

        // Stats grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Modifier.weight(1f), Icons.Filled.CloudUpload, accent, ui.pending.toString(), s.dashPending)
                    StatCard(Modifier.weight(1f), Icons.Filled.SdStorage, AppColors.Info, ui.storageLabel, s.dashStorage)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Modifier.weight(1f), Icons.Filled.TaskAlt, AppColors.Success, ui.done.toString(), s.dashCompletedToday)
                    StatCard(Modifier.weight(1f), Icons.Filled.Error, AppColors.Error, ui.failed.toString(), s.dashFailed)
                }
            }
        }

        // Quick actions
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(s.dashQuickActions, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val actions = listOf(
                        Triple(Icons.Filled.RuleFolder, "${s.filesUpload} / ${s.navFiles}", Destination.Files),
                        Triple(Icons.Filled.History, s.dashRecent, Destination.Uploads),
                        Triple(Icons.Filled.CloudDownload, s.navDownloads, Destination.Downloads),
                        Triple(Icons.Filled.Tune, s.navSettings, Destination.Settings),
                    )
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        actions.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (icon, label, dest) ->
                                    QuickAction(Modifier.weight(1f), icon, label, accent) { onNavigate(dest) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent activity
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.dashRecent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.dashViewAll, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onNavigate(Destination.Uploads) })
                    }
                    if (ui.recent.isEmpty()) {
                        Text(
                            s.dashNoRecent,
                            color = AppColors.OnSurfaceFaint,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    ui.recent.forEach { item ->
                        val kv = item.kind.visual()
                        val sv = statusVisual(item.status, accent)
                        Row(
                            Modifier.fillMaxWidth().padding(top = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(kv.icon, null, tint = kv.color, modifier = Modifier.size(24.dp))
                            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                Text(item.name, fontSize = 13.sp, maxLines = 1)
                                Text(Formatters.bytes(item.sizeBytes), color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 11.sp)
                            }
                            StatusChip(statusLabel(item.status), sv.icon, sv.fg, sv.bg)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "${s.dashLastSync}: ${ui.recent.firstOrNull()?.let { Formatters.percent(it.progress) } ?: "—"}",
                color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun statusLabel(status: TransferStatus): String {
    val s = LocalStrings.current
    return when (status) {
        TransferStatus.UPLOADING -> s.histUploading
        TransferStatus.DONE -> s.histDone
        TransferStatus.FAILED -> s.histFailed
        TransferStatus.QUEUED -> s.histQueued
    }
}

@Composable
private fun ConnectionChip(status: ConnStatus) {
    val s = LocalStrings.current
    val (label, fg, bg, icon) = when (status) {
        ConnStatus.OK -> Quad(s.online, AppColors.Success, AppColors.SuccessBg, Icons.Filled.CheckCircle)
        ConnStatus.TESTING -> Quad(s.setTesting, AppColors.Warning, AppColors.WarningBg, Icons.Filled.WifiTethering)
        ConnStatus.FAIL -> Quad(s.offline, AppColors.Error, AppColors.ErrorBg, Icons.Filled.Error)
        ConnStatus.IDLE -> Quad(s.offline, AppColors.OnSurfaceVariant, AppColors.SurfaceAlt, Icons.Filled.Error)
    }
    StatusChip(label, icon, fg, bg)
}

private data class Quad(val label: String, val fg: Color, val bg: Color, val icon: ImageVector)

@Composable
private fun TintIcon(icon: ImageVector, accent: Color) {
    Surface(color = accent.copy(alpha = 0.11f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: ImageVector, iconColor: Color, value: String, label: String) {
    AppCard(modifier) {
        Column(Modifier.padding(13.dp)) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text(label, color = AppColors.OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun QuickAction(modifier: Modifier, icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Outline),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = 0.11f), shape = RoundedCornerShape(9.dp), modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp)) }
            }
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
