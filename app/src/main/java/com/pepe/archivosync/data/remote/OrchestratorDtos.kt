package com.pepe.archivosync.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /v1/ice-servers → { iceServers: [...] }. */
@Serializable
data class IceServersResponseDto(
    @SerialName("iceServers") val iceServers: List<IceServerDto> = emptyList(),
)

@Serializable
data class IceServerDto(
    val urls: List<String> = emptyList(),
    val username: String? = null,
    val credential: String? = null,
)

/** POST /v1/devices body. */
@Serializable
data class RegisterDeviceDto(
    val name: String,
    val platform: String,
    @SerialName("public_key") val publicKey: String,
    val capabilities: List<String>? = null,
)

/** POST /v1/devices → { id }. */
@Serializable
data class RegisterDeviceResponseDto(
    val id: String,
)

/** GET /v1/devices → { devices: [...] }. */
@Serializable
data class DevicesResponseDto(
    val devices: List<DeviceDto> = emptyList(),
)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String = "",
    val platform: String = "",
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** POST /v1/devices/{id}/endpoints body. */
@Serializable
data class PublishEndpointDto(
    @SerialName("endpoint_type") val endpointType: String,
    val address: String,
    val port: Int? = null,
    val priority: Int? = null,
    @SerialName("nat_type") val natType: String? = null,
    val ttl: Int? = null,
)
