package com.pepe.archivosync.ui.screens.p2p

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.pepe.archivosync.domain.model.P2pMode
import com.pepe.archivosync.domain.model.P2pStatus
import com.pepe.archivosync.domain.model.P2pTransfer
import com.pepe.archivosync.ui.components.AppCard
import com.pepe.archivosync.ui.components.AppToggle
import com.pepe.archivosync.ui.components.CountFilterChip
import com.pepe.archivosync.ui.components.visual
import com.pepe.archivosync.ui.i18n.LocalStrings
import com.pepe.archivosync.ui.theme.AppColors
import com.pepe.archivosync.ui.theme.LocalAccent
import com.pepe.archivosync.ui.theme.MonoFamily
import java.util.Locale

@Composable
fun P2pScreen(viewModel: P2pViewModel = hiltViewModel()) {
    val accent = LocalAccent.current
    val s = LocalStrings.current
    val ui by viewModel.state.collectAsStateWithLifecycle()

    val filters = listOf(null to s.p2pAll, P2pMode.SEED to s.p2pSeeding, P2pMode.LEECH to s.p2pLeeching)

    LazyColumn(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                        MiniStat(Modifier.weight(1f), String.format(Locale.US, "%.2f", ui.stats.avgRatio), s.p2pRatio, AppColors.OnSurface)
                        MiniStat(Modifier.weight(1f), ui.stats.totalPeers.toString(), s.p2pPeers, AppColors.OnSurface)
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
                P2pCard(p, ui.enabled, accent) { viewModel.togglePaused(p.id, p.status == P2pStatus.ACTIVE) }
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
private fun P2pCard(p: P2pTransfer, networkEnabled: Boolean, accent: Color, onToggle: () -> Unit) {
    val s = LocalStrings.current
    val kv = p.kind.visual()
    val isSeed = p.mode == P2pMode.SEED
    val paused = p.status == P2pStatus.PAUSED
    val active = networkEnabled && !paused
    val modeColor = if (isSeed) AppColors.Success else accent
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
            if (!isSeed) {
                Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.pepe.archivosync.ui.components.ThinProgressBar(p.progress, accent, Modifier.weight(1f))
                    Text(Formatters.percent(p.progress), color = AppColors.OnSurfaceVariant, fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("↑ ${Formatters.speedKbps(if (active) p.upRateKbps else 0)}", color = AppColors.Success, fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("↓ ${Formatters.speedKbps(if (active && !isSeed) p.downRateKbps else 0)}", color = accent, fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("${s.p2pRatio} ${String.format(Locale.US, "%.2f", p.ratio)}", color = AppColors.OnSurfaceVariant, fontFamily = MonoFamily, fontSize = 11.sp)
            }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(top = 10.dp), color = AppColors.SurfaceAlt)
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Link, null, tint = AppColors.OnSurfaceFaint, modifier = Modifier.size(13.dp))
                    Text(p.magnetUri, color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(start = 3.dp))
                }
                Surface(color = AppColors.SurfaceAlt, shape = RoundedCornerShape(7.dp), modifier = Modifier.clickable(onClick = onToggle)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                        Text(if (paused) s.p2pResume else s.p2pPause, color = Color(0xFF475569), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}
