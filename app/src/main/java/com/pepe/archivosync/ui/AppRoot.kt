package com.pepe.archivosync.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pepe.archivosync.domain.model.AppLanguage
import com.pepe.archivosync.ui.i18n.LocalStrings
import com.pepe.archivosync.ui.i18n.Strings
import com.pepe.archivosync.ui.navigation.Destination
import com.pepe.archivosync.ui.screens.dashboard.DashboardScreen
import com.pepe.archivosync.ui.screens.downloads.DownloadsScreen
import com.pepe.archivosync.ui.screens.files.FilesScreen
import com.pepe.archivosync.ui.screens.files.FilesViewModel
import com.pepe.archivosync.ui.screens.p2p.P2pScreen
import com.pepe.archivosync.ui.screens.settings.SettingsScreen
import com.pepe.archivosync.ui.screens.uploads.UploadsScreen
import com.pepe.archivosync.ui.theme.AccentColor
import com.pepe.archivosync.ui.theme.AppColors
import com.pepe.archivosync.ui.theme.ArchivoSyncTheme
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun AppRoot() {
    val appViewModel: AppViewModel = hiltViewModel()
    val filesViewModel: FilesViewModel = hiltViewModel()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val accent = AccentColor.fromName(settings.accentName)
    val strings = Strings.of(settings.language)

    // Ask for notification permission once (required for foreground backup on 13+).
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result handled by system; backup still runs */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ArchivoSyncTheme(accent) {
        CompositionLocalProvider(LocalStrings provides strings) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val current = Destination.fromRoute(backStackEntry?.destination?.route)
            val filesState by filesViewModel.state.collectAsStateWithLifecycle()

            Scaffold(
                containerColor = AppColors.Background,
                topBar = {
                    AppBar(
                        title = current.title(strings),
                        subtitle = settings.remoteLabel.substringAfter("://"),
                        accent = accent.color,
                        language = settings.language,
                        onLanguage = appViewModel::setLanguage,
                    )
                },
                bottomBar = {
                    Column {
                        if (current == Destination.Files && filesState.selectionCount > 0) {
                            SelectionBar(
                                count = filesState.selectionCount,
                                accent = accent.color,
                                onClear = filesViewModel::clearSelection,
                                onSeed = { filesViewModel.seedSelected { navController.navigateTo(Destination.P2p) } },
                                onUpload = { filesViewModel.backupSelected { navController.navigateTo(Destination.Uploads) } },
                            )
                        }
                        BottomNav(current, accent.color) { navController.navigateTo(it) }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Destination.Dashboard.route,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(Destination.Dashboard.route) {
                        DashboardScreen(
                            appViewModel = appViewModel,
                            onNavigate = { navController.navigateTo(it) },
                        )
                    }
                    composable(Destination.Files.route) { FilesScreen(filesViewModel) }
                    composable(Destination.Uploads.route) { UploadsScreen() }
                    composable(Destination.Downloads.route) { DownloadsScreen(appViewModel) }
                    composable(Destination.P2p.route) { P2pScreen() }
                    composable(Destination.Settings.route) { SettingsScreen(appViewModel) }
                }
            }
        }
    }
}

private fun NavHostController.navigateTo(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppBar(
    title: String,
    subtitle: String,
    accent: Color,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
) {
    Surface(color = accent) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(9.dp), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Backup, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, fontFamily = com.pepe.archivosync.ui.theme.MonoFamily, maxLines = 1)
            }
            Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(2.dp)) {
                    LangChip("ES", language == AppLanguage.ES, accent) { onLanguage(AppLanguage.ES) }
                    LangChip("EN", language == AppLanguage.EN, accent) { onLanguage(AppLanguage.EN) }
                }
            }
        }
    }
}

@Composable
private fun LangChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        color = if (active) Color.White else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (active) accent else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SelectionBar(count: Int, accent: Color, onClear: () -> Unit, onSeed: () -> Unit, onUpload: () -> Unit) {
    val s = LocalStrings.current
    Surface(color = AppColors.ScrimBar) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(22.dp).clickable(onClick = onClear))
            Text("$count ${s.filesSelected}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp).weight(1f))
            Surface(color = Color.Transparent, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), modifier = Modifier.clickable(onClick = onSeed)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Hub, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(s.p2pSeed, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Surface(color = accent, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(start = 12.dp).clickable(onClick = onUpload)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudUpload, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(s.filesUpload, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun BottomNav(current: Destination, accent: Color, onSelect: (Destination) -> Unit) {
    val s = LocalStrings.current
    Surface(color = AppColors.Surface) {
        Row(
            Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Destination.entries.forEach { dest ->
                val active = dest == current
                Column(
                    Modifier.weight(1f).clickable { onSelect(dest) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) accent.copy(alpha = 0.11f) else Color.Transparent)
                            .padding(horizontal = 11.dp, vertical = 2.dp),
                    ) {
                        Icon(dest.icon, null, tint = if (active) accent else AppColors.OnSurfaceVariant, modifier = Modifier.size(21.dp))
                    }
                    Text(
                        dest.label(s),
                        color = if (active) accent else AppColors.OnSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
