package com.pepe.archivosync.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepe.archivosync.domain.model.CloudProvider
import com.pepe.archivosync.domain.model.RemoteType
import com.pepe.archivosync.ui.AppViewModel
import com.pepe.archivosync.ui.ConnStatus
import com.pepe.archivosync.ui.components.AppCard
import com.pepe.archivosync.ui.components.AppToggle
import com.pepe.archivosync.ui.i18n.LocalStrings
import com.pepe.archivosync.ui.theme.AccentColor
import com.pepe.archivosync.ui.theme.AppColors

@Composable
fun SettingsScreen(appViewModel: AppViewModel) {
    val s = LocalStrings.current
    val accent = com.pepe.archivosync.ui.theme.LocalAccent.current
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val connStatus by appViewModel.connStatus.collectAsStateWithLifecycle()
    val isRest = settings.remoteType == RemoteType.REST

    LazyColumn(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Connection card
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    SectionHeader(Icons.Filled.CloudSync, s.setConnection, accent)
                    Text(s.setRemoteType, color = AppColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = AppColors.SurfaceAlt) {
                        Row(Modifier.padding(3.dp)) {
                            Segment(Modifier.weight(1f), Icons.Filled.Api, s.setRest, isRest, accent) {
                                appViewModel.update { it.copy(remoteType = RemoteType.REST) }
                            }
                            Segment(Modifier.weight(1f), Icons.Filled.Cloud, s.setCloud, !isRest, accent) {
                                appViewModel.update { it.copy(remoteType = RemoteType.CLOUD) }
                            }
                        }
                    }

                    if (isRest) {
                        Field(s.setBaseUrl, settings.baseUrl) { v -> appViewModel.update { it.copy(baseUrl = v) } }
                        Field(s.setGetEp, settings.listEndpoint) { v -> appViewModel.update { it.copy(listEndpoint = v) } }
                        Field(s.setPostEp, settings.uploadEndpoint) { v -> appViewModel.update { it.copy(uploadEndpoint = v) } }
                        Field(s.setToken, settings.token) { v -> appViewModel.update { it.copy(token = v) } }
                    } else {
                        ProviderDropdown(settings.cloudProvider) { p -> appViewModel.update { it.copy(cloudProvider = p) } }
                        Field(s.setHost, settings.host) { v -> appViewModel.update { it.copy(host = v) } }
                        Field(s.setAccessKey, settings.accessKey) { v -> appViewModel.update { it.copy(accessKey = v) } }
                        Field(s.setSecretKey, settings.secretKey) { v -> appViewModel.update { it.copy(secretKey = v) } }
                    }

                    Surface(
                        color = accent, shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clickable { appViewModel.test() },
                    ) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WifiTethering, null, tint = Color.White, modifier = Modifier.size(19.dp))
                            Text(s.setTestConn, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                    TestStatus(connStatus)
                }
            }
        }

        // P2P orchestrator card
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    SectionHeader(Icons.Filled.Hub, s.setP2pSection, accent)
                    Field(s.setOrchestratorUrl, settings.orchestratorUrl) { v -> appViewModel.update { it.copy(orchestratorUrl = v) } }
                    Field(s.setSignalingUrl, settings.signalingUrl) { v -> appViewModel.update { it.copy(signalingUrl = v) } }
                    Field(s.setDeviceName, settings.deviceName) { v -> appViewModel.update { it.copy(deviceName = v) } }
                    if (settings.deviceId.isNotBlank()) {
                        Text(
                            "${s.setDeviceId}: ${settings.deviceId}",
                            color = AppColors.OnSurfaceFaint, fontSize = 11.sp,
                            fontFamily = com.pepe.archivosync.ui.theme.MonoFamily,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        // General toggles
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    Text(s.setGeneral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    ToggleRow(s.setAutoUpload, s.setAutoUploadSub, settings.autoUpload, accent) { appViewModel.update { it.copy(autoUpload = !it.autoUpload) } }
                    ToggleRow(s.setWifiOnly, s.setWifiOnlySub, settings.wifiOnly, accent) { appViewModel.update { it.copy(wifiOnly = !it.wifiOnly) } }
                    ToggleRow(s.setCompress, s.setCompressSub, settings.compress, accent) { appViewModel.update { it.copy(compress = !it.compress) } }
                    ToggleRow(s.setNotif, s.setNotifSub, settings.notifications, accent) { appViewModel.update { it.copy(notifications = !it.notifications) } }
                }
            }
        }

        // Appearance (accent picker)
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(s.setAppearance, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccentColor.entries.forEach { ac ->
                            val selected = settings.accentName.equals(ac.name, ignoreCase = true)
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(ac.color)
                                    .clickable { appViewModel.setAccent(ac.name) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // Version
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = accent.copy(alpha = 0.11f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Backup, null, tint = accent, modifier = Modifier.size(20.dp)) }
                    }
                    Column(Modifier.padding(start = 11.dp)) {
                        Text(s.appName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.setVersion, color = AppColors.OnSurfaceFaint, fontSize = 12.sp, fontFamily = com.pepe.archivosync.ui.theme.MonoFamily)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
    }
}

@Composable
private fun Segment(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (active) AppColors.Surface else Color.Transparent,
    ) {
        Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) accent else AppColors.OnSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(label, color = if (active) accent else AppColors.OnSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, color = AppColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 5.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(selected: CloudProvider, onSelect: (CloudProvider) -> Unit) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        CloudProvider.S3 to "Amazon S3",
        CloudProvider.FTP to "FTP / SFTP",
        CloudProvider.WEBDAV to "WebDAV",
        CloudProvider.GCS to "Google Cloud Storage",
    )
    Column(Modifier.padding(top = 12.dp)) {
        Text(s.setProvider, color = AppColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 5.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = labels[selected] ?: selected.name,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                labels.forEach { (provider, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(provider); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, sub: String, checked: Boolean, accent: Color, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = 14.sp)
            Text(sub, color = AppColors.OnSurfaceFaint, fontSize = 12.sp)
        }
        AppToggle(checked, accent, onToggle)
    }
    androidx.compose.material3.HorizontalDivider(color = AppColors.SurfaceAlt)
}

@Composable
private fun TestStatus(status: ConnStatus) {
    val s = LocalStrings.current
    val (icon, color, text) = when (status) {
        ConnStatus.OK -> Triple(Icons.Filled.CheckCircle, AppColors.Success, s.setOk)
        ConnStatus.TESTING -> Triple(Icons.Filled.WifiTethering, AppColors.Warning, s.setTesting)
        ConnStatus.FAIL -> Triple(Icons.Filled.Error, AppColors.Error, s.setFail)
        ConnStatus.IDLE -> Triple(Icons.Filled.Error, AppColors.OnSurfaceFaint, s.setIdle)
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}
