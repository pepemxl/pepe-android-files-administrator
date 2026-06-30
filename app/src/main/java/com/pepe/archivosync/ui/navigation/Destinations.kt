package com.pepe.archivosync.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.pepe.archivosync.ui.i18n.Strings

/** The six bottom-nav destinations, in display order. */
enum class Destination(val route: String, val icon: ImageVector) {
    Dashboard("dashboard", Icons.Filled.Home),
    Files("files", Icons.Filled.Folder),
    Uploads("uploads", Icons.Filled.CloudUpload),
    Downloads("downloads", Icons.Filled.CloudDownload),
    P2p("p2p", Icons.Filled.Hub),
    Settings("settings", Icons.Filled.Settings);

    fun label(s: Strings): String = when (this) {
        Dashboard -> s.navHome
        Files -> s.navFiles
        Uploads -> s.navUploads
        Downloads -> s.navDownloads
        P2p -> s.navP2p
        Settings -> s.navSettings
    }

    fun title(s: Strings): String = when (this) {
        Dashboard -> s.dashTitle
        Files -> s.navFiles
        Uploads -> s.histTitle
        Downloads -> s.dlTitle
        P2p -> s.p2pTitle
        Settings -> s.setTitle
    }

    companion object {
        fun fromRoute(route: String?): Destination =
            entries.firstOrNull { it.route == route } ?: Dashboard
    }
}
