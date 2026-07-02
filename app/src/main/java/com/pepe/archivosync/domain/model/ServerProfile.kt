package com.pepe.archivosync.domain.model

import kotlinx.serialization.Serializable

/**
 * A saved remote-destination configuration the user can create, switch between,
 * edit and delete. The *active* profile's fields drive the live connection —
 * they are mirrored into [AppSettings]'s destination fields, so the resolver,
 * workers and cards keep reading [AppSettings] unchanged.
 */
@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val remoteType: RemoteType = RemoteType.REST,
    // REST destination
    val baseUrl: String = "https://api.midominio.com/v1",
    val listEndpoint: String = "/files",
    val uploadEndpoint: String = "/upload",
    val token: String = "",
    // Cloud destination
    val cloudProvider: CloudProvider = CloudProvider.S3,
    val host: String = "mi-bucket",
    val accessKey: String = "",
    val secretKey: String = "",
    val region: String = "us-east-1",
    val cloudPath: String = "",
    val endpoint: String = "",
) {
    /** Short human label (host/URL) shown next to the profile name. */
    val label: String
        get() = when (remoteType) {
            RemoteType.REST -> baseUrl
            RemoteType.CLOUD -> "${cloudProvider.name.lowercase()}://$host"
        }
}
