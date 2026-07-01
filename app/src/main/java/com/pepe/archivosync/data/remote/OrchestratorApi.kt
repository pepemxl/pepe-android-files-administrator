package com.pepe.archivosync.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit surface for the P2P orchestrator control API (pepe-p2p-orquestrator).
 * Like [ArchivoSyncApi], every call takes a full @Url built from the user-config
 * orchestrator base, so no fixed base URL is baked in.
 */
interface OrchestratorApi {

    @GET
    suspend fun iceServers(
        @Url url: String,
        @Header("Authorization") authorization: String?,
    ): IceServersResponseDto

    @POST
    suspend fun registerDevice(
        @Url url: String,
        @Header("Authorization") authorization: String?,
        @Body body: RegisterDeviceDto,
    ): RegisterDeviceResponseDto

    @GET
    suspend fun listDevices(
        @Url url: String,
        @Header("Authorization") authorization: String?,
    ): DevicesResponseDto

    @POST
    suspend fun publishEndpoint(
        @Url url: String,
        @Header("Authorization") authorization: String?,
        @Body body: PublishEndpointDto,
    ): Map<String, Boolean>
}
