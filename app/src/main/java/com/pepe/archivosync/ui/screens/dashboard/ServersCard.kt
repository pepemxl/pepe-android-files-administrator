package com.pepe.archivosync.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.CloudProvider
import com.pepe.archivosync.domain.model.RemoteType
import com.pepe.archivosync.domain.model.ServerProfile
import com.pepe.archivosync.ui.AppViewModel
import com.pepe.archivosync.ui.components.AppCard
import com.pepe.archivosync.ui.i18n.LocalStrings
import com.pepe.archivosync.ui.theme.AppColors
import com.pepe.archivosync.ui.theme.MonoFamily

/** Dashboard card: list, switch, create, edit and delete server profiles. */
@Composable
fun ServersCard(settings: AppSettings, appViewModel: AppViewModel, accent: Color) {
    val s = LocalStrings.current
    var editing by remember { mutableStateOf<ServerProfile?>(null) }
    var creating by remember { mutableStateOf(false) }

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Dns, null, tint = accent, modifier = Modifier.size(19.dp))
                    Text(s.srvSection, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { creating = true },
                ) {
                    Icon(Icons.Filled.Add, null, tint = accent, modifier = Modifier.size(18.dp))
                    Text(s.srvCreate, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                }
            }

            settings.profiles.forEach { p ->
                val active = p.id == settings.activeProfileId
                Row(
                    Modifier.fillMaxWidth().padding(top = 11.dp).clickable { appViewModel.activateProfile(p.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (active) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        null,
                        tint = if (active) accent else AppColors.OnSurfaceFaint,
                        modifier = Modifier.size(20.dp),
                    )
                    Icon(
                        if (p.remoteType == RemoteType.REST) Icons.Filled.Api else Icons.Filled.Cloud,
                        null,
                        tint = AppColors.OnSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp).size(18.dp),
                    )
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(p.label, color = AppColors.OnSurfaceFaint, fontFamily = MonoFamily, fontSize = 11.sp, maxLines = 1)
                    }
                    IconBtn(Icons.Filled.Edit, AppColors.OnSurfaceVariant) { editing = p }
                    if (settings.profiles.size > 1) {
                        IconBtn(Icons.Filled.Delete, AppColors.Error) { appViewModel.deleteProfile(p.id) }
                    }
                }
            }
        }
    }

    if (creating) {
        ServerFormDialog(initial = null, accent = accent, onDismiss = { creating = false }) {
            appViewModel.saveProfile(it); creating = false
        }
    }
    editing?.let { p ->
        ServerFormDialog(initial = p, accent = accent, onDismiss = { editing = null }) {
            appViewModel.saveProfile(it); editing = null
        }
    }
}

@Composable
private fun IconBtn(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerFormDialog(
    initial: ServerProfile?,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (ServerProfile) -> Unit,
) {
    val s = LocalStrings.current
    val base = initial ?: ServerProfile(id = "", name = "")
    var name by remember { mutableStateOf(base.name) }
    var isRest by remember { mutableStateOf(base.remoteType == RemoteType.REST) }
    var baseUrl by remember { mutableStateOf(base.baseUrl) }
    var listEp by remember { mutableStateOf(base.listEndpoint) }
    var uploadEp by remember { mutableStateOf(base.uploadEndpoint) }
    var token by remember { mutableStateOf(base.token) }
    var provider by remember { mutableStateOf(base.cloudProvider) }
    var host by remember { mutableStateOf(base.host) }
    var accessKey by remember { mutableStateOf(base.accessKey) }
    var secretKey by remember { mutableStateOf(base.secretKey) }
    var region by remember { mutableStateOf(base.region) }
    var cloudPath by remember { mutableStateOf(base.cloudPath) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = AppColors.Surface) {
            Column(
                Modifier.padding(18.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (initial == null) s.srvNewTitle else s.srvEditTitle,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )

                FormField(s.srvName, name) { name = it }

                Text(s.setRemoteType, color = AppColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = AppColors.SurfaceAlt) {
                    Row(Modifier.padding(3.dp)) {
                        Seg(Modifier.weight(1f), Icons.Filled.Api, s.setRest, isRest, accent) { isRest = true }
                        Seg(Modifier.weight(1f), Icons.Filled.Cloud, s.setCloud, !isRest, accent) { isRest = false }
                    }
                }

                if (isRest) {
                    FormField(s.setBaseUrl, baseUrl) { baseUrl = it }
                    FormField(s.setGetEp, listEp) { listEp = it }
                    FormField(s.setPostEp, uploadEp) { uploadEp = it }
                    FormField(s.setToken, token) { token = it }
                } else {
                    ProviderDropdown(provider) { provider = it }
                    FormField(s.setHost, host) { host = it }
                    FormField(s.setAccessKey, accessKey) { accessKey = it }
                    FormField(s.setSecretKey, secretKey) { secretKey = it }
                    if (provider == CloudProvider.S3 || provider == CloudProvider.GCS) {
                        FormField(s.setRegion, region) { region = it }
                    }
                    FormField(s.setCloudPath, cloudPath) { cloudPath = it }
                }

                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    Text(s.srvCancel, color = AppColors.OnSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onDismiss() }.padding(10.dp))
                    Surface(color = accent, shape = RoundedCornerShape(9.dp), modifier = Modifier.padding(start = 8.dp).clickable {
                        onSave(
                            base.copy(
                                name = name.ifBlank { host.ifBlank { baseUrl } },
                                remoteType = if (isRest) RemoteType.REST else RemoteType.CLOUD,
                                baseUrl = baseUrl, listEndpoint = listEp, uploadEndpoint = uploadEp, token = token,
                                cloudProvider = provider, host = host, accessKey = accessKey, secretKey = secretKey,
                                region = region, cloudPath = cloudPath,
                            ),
                        )
                    }) {
                        Text(s.srvSave, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, color = AppColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 5.dp))
        OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Seg(modifier: Modifier, icon: ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(8.dp), color = if (active) AppColors.Surface else Color.Transparent) {
        Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) accent else AppColors.OnSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(label, color = if (active) accent else AppColors.OnSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
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
                labels.forEach { (p, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(p); expanded = false })
                }
            }
        }
    }
}
