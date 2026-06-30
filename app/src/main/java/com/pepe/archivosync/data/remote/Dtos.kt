package com.pepe.archivosync.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Item returned by the REST list endpoint (GET /files). */
@Serializable
data class RemoteFileDto(
    val id: String? = null,
    val name: String,
    @SerialName("size") val sizeBytes: Long = 0,
    @SerialName("path") val remotePath: String? = null,
)

/** Response wrapper accepted from the upload endpoint (POST /upload). */
@Serializable
data class UploadResponseDto(
    val id: String? = null,
    val url: String? = null,
    val ok: Boolean = true,
)
