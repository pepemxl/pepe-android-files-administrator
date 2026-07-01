package com.pepe.archivosync.ui.screens.p2p

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepe.archivosync.core.Formatters
import com.pepe.archivosync.domain.model.P2pDevice
import com.pepe.archivosync.domain.model.P2pMode
import com.pepe.archivosync.domain.model.P2pStatus
import com.pepe.archivosync.domain.model.P2pTransfer
import com.pepe.archivosync.domain.model.PeerLink
import com.pepe.archivosync.domain.model.SignalingState
import com.pepe.archivosync.ui.components.AppCard
import com.pepe.archivosync.ui.components.AppToggle
import com.pepe.archivosync.ui.components.CountFilterChip
import com.pepe.archivosync.ui.components.visual
import com.pepe.archivosync.ui.i18n.LocalStrings
import com.pepe.archivosync.ui.theme.AppColors
import com.pepe.archivosync.ui.theme.LocalAccent
import com.pepe.archivosync.ui.theme.MonoFamily

@Composable
fun P2pScreen(viewModel: P2pViewModel = hiltViewModel()) {
    val accent = LocalAccent.current
    val s = LocalStrings.current
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val conn by viewModel.connState.collectAsStateWithLifecycle()

    val filters = listOf(null to s.p2pAll, P2pMode.SEED to s.p2pSeeding, P2pMode.LEECH to s.p2pLeeching)

    var sendTarget by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = sendTarget
        if (uri != null && target != null) viewModel.sendFile(target, uri)
        sendTarget = null
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ConnectivityCard(
                conn = conn,
                accent = accent,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onRefresh = viewModel::refreshDevices,
                onLink = viewModel::linkPeer,
                onSend = { deviceId -> sendTarget = deviceId; picker.launch("*/*") },
            )
        }

        // header card with toggle + aggregate stats
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = accent.copy(alpha = 0.11f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Hub, null, tint = accent, modifier = Modifier.size(21.dp)) }
                        }
                        Column(Modifier.padding(start = 11.dp).weight(1f)) {
                            Text(s.p2pEnabled, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(s.p2pEnabledSub, color = AppColors.OnSurfaceFaint, fontSize = 12.sp)
                        }
                        AppToggle(ui.enabled, accent) { viewModel.toggleEnabled() }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat(Modifier.weight(1f), Formatters.speedKbps(ui.stats.totalUpKbps), "↑ ${s.p2pUp}", AppColors.Success)
                        MiniStat(Modifier.weight(1f), Formatters.speedKbps(ui.stats.totalDownKbps), "↓ ${s.p2pDown}", accent)
                        MiniStat(Modifier.weight(1f), ui.stats.active.toString(), s.p2pActive, AppColors.OnSurface)
                        MiniStat(Modifier.weight(1f), ui.stats.peers.toString(), s.p2pPeers, AppColors.OnSurface)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                filters.forEach { (mode, label) ->
                    CountFilterChip(label, ui.counts[mode] ?: 0, ui.filter == mode, accent) { viewModel.setFilter(mode) }
                }
            }
        }

        if (ui.transfers.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Hub, null, tint = AppColors.OutlineStrong, modifier = Modifier.size(42.dp))
                    Text(s.p2pEmpty, color = AppColors.OnSurfaceFaint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            items(ui.transfers, key = { it.id }) { p ->
                P2pCard(p, ui.enabled, accent)
            }
        }
    }
}

@Composable
private fun ConnectivityCard(
    conn: P2pConnUiState,
    accent: Color,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onLink: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    val s = LocalStrings.current
    val (stateLabel, stateColor) = when (conn.signaling) {
        SignalingState.CONNECTED -> s.p2pStConnected to AppColors.Success
        SignalingState.CONNECTING -> s.p2pStConnecting to accent
        SignalingState.ERROR -> s.p2pStError to AppColors.Error
        SignalingState.DISCONNECTED -> s.p2pStDisconnected to AppColors.OnSurfaceFaint
    }
    val active = conn.signaling == SignalingState.CONNECTED || conn.signaling == SignalingState.CONNECTING
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent.copy(alpha = 0.11f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Lan, null, tint = accent, modifier = Modifier.size(21.dp)) }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(s.p2pDirect, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${s.p2pSignaling}: $stateLabel", color = stateColor, fontSize = 12.sp, fontFamily = MonoFamily)
                }
                if (active) {
                    OutlinedButton(onClick = onDisconnect) { Text(s.p2pDisconnect) }
                } else {
                    Button(onClick = onConnect) { Text(s.p2pConnect) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Devices, null, tint = AppColors.OnSurfaceFaint, modifier = Modifier.size(15.dp))
                Text(s.p2pDevices, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 5.dp).weight(1f))
                TextButton(onClick = onRefresh) { Text(s.p2pRefresh, fontSize = 12.sp) }
            }

            if (conn.devices.isEmpty()) {
                Text(s.p2pNoDevices, color = AppColors.OnSurfaceFaint, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                conn.devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        link = conn.peers.firstOrNull { it.deviceId == device.id },
                        accent = accent,
                        onLink = { onLink(device.id) },
                        onSend = { onSend(device.id) },
                    )
                }
            }

        }
    }
}

@Composable
private fun DeviceRow(
    device: P2pDevice,
    link: PeerLink?,
    accent: Color,
    onLink: () -> Unit,
    onSend: () -> Unit,
) {
    val s = LocalStrings.current
    val open = link?.channelOpen == true
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(device.name.ifBlank { device.id }, fontSize = 13.sp, maxLines = 1)
            Text(
                if (open) s.p2pLinked else device.platform,
                color = if (open) AppColors.Success else AppColors.OnSurfaceFaint,
                fontSize = 11.sp,
                fontFamily = MonoFamily,
            )
        }
        if (open) {
            Button(onClick = onSend, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Icon(Icons.Filled.Send, null, modifier = Modifier.size(15.dp))
                Text(s.p2pSend, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
            }
        } else {
            OutlinedButton(onClick = onLink, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Icon(Icons.Filled.Link, null, tint = accent, modifier = Modifier.size(15.dp))
                Text(s.p2pLink, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, value: String, label: String, valueColor: Color) {
    Surface(modifier = modifier, color = AppColors.SurfaceMuted, shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Outline)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = valueColor, fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(label, color = AppColors.OnSurfaceFaint, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun P2pCard(p: P2pTransfer, networkEnabled: Boolean, accent: Color) {
    val s = LocalStrings.current
    val kv = p.kind.visual()
    val isSeed = p.mode == P2pMode.SEED
    val running = p.status == P2pStatus.ACTIVE
    val active = networkEnabled && running
    val modeColor = if (isSeed) AppColors.Success else accent
    val (stateLabel, stateColor) = when (p.status) {
        P2pStatus.ACTIVE -> (if (isSeed) s.p2pSeeding else s.p2pLeeching) to modeColor
        P2pStatus.DONE -> s.p2pDone to AppColors.Success
        P2pStatus.FAILED -> s.p2pFailed to AppColors.Error
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kv.icon, null, tint = kv.color, modifier = Modifier.size(27.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(p.name, fontSize = 14.sp, maxLines = 1)
                    Text(Formatters.bytes(p.sizeBytes), color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 11.sp)
                }
                Surface(color = modeColor.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isSeed) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, tint = modeColor, modifier = Modifier.size(13.dp))
                        Text(if (isSeed) s.p2pSeeding else s.p2pLeeching, color = modeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 3.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                com.pepe.archivosync.ui.components.ThinProgressBar(p.progress, accent, Modifier.weight(1f))
                Text(Formatters.percent(p.progress), color = AppColors.OnSurfaceVariant, fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("↑ ${Formatters.speedKbps(if (active && isSeed) p.upRateKbps else 0)}", color = AppColors.Success, fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("↓ ${Formatters.speedKbps(if (active && !isSeed) p.downRateKbps else 0)}", color = accent, fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(Formatters.bytes(p.transferredBytes), color = AppColors.OnSurfaceVariant, fontFamily = MonoFamily, fontSize = 11.sp)
            }
            HorizontalDivider(Modifier.padding(top = 10.dp), color = AppColors.SurfaceAlt)
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Devices, null, tint = AppColors.OnSurfaceFaint, modifier = Modifier.size(13.dp))
                    Text(p.peerName, color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
                }
                Surface(color = stateColor.copy(alpha = 0.12f), shape = RoundedCornerShape(7.dp)) {
                    Text(stateLabel, color = stateColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}
