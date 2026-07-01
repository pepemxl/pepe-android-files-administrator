package com.pepe.archivosync.domain.model

enum class AppLanguage { ES, EN }

/** Where backups go. Each value maps to a concrete DestinationProvider. */
enum class RemoteType { REST, CLOUD }

enum class CloudProvider { S3, FTP, WEBDAV, GCS }

/**
 * All user-configurable state, persisted in DataStore. Secrets (token,
 * secretKey) live here too and are excluded from system backups via
 * data_extraction_rules.xml.
 */
data class AppSettings(
    val language: AppLanguage = AppLanguage.ES,
    val accentName: String = "Blue",
    val remoteType: RemoteType = RemoteType.REST,

    // REST destination
    val baseUrl: String = "https://api.midominio.com/v1",
    val listEndpoint: String = "/files",
    val uploadEndpoint: String = "/upload",
    val token: String = "",

    // Cloud destination
    val cloudProvider: CloudProvider = CloudProvider.S3,
    val host: String = "mi-bucket.s3.amazonaws.com",
    val accessKey: String = "",
    val secretKey: String = "",

    // P2P (WebRTC via the orchestrator: pepe-p2p-orquestrator)
    val p2pEnabled: Boolean = true,
    /** Orchestrator HTTP base for control calls (ice-servers, devices, endpoints). */
    val orchestratorUrl: String = "https://orquestador.midominio.com:9501/v1",
    /** WebSocket signaling base; the client appends ?token=&device_id=. */
    val signalingUrl: String = "wss://orquestador.midominio.com:9501",
    /** This device's stable id at the orchestrator (assigned on first register). */
    val deviceId: String = "",
    /** Human label published when registering this device. */
    val deviceName: String = "Android",

    // General toggles
    val autoUpload: Boolean = true,
    val wifiOnly: Boolean = true,
    val compress: Boolean = false,
    val notifications: Boolean = true,
) {
    /** Human label for the configured remote (host shown in app bar / cards). */
    val remoteLabel: String
        get() = when (remoteType) {
            RemoteType.REST -> baseUrl
            RemoteType.CLOUD -> "${cloudProvider.name.lowercase()}://$host"
        }
}
