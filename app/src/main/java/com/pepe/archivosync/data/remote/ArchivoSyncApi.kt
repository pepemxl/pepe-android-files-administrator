package com.pepe.archivosync.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * Thin Retrofit surface for the REST backend (pepe-api-files-administrator).
 * Uploads are handled directly in [RestDestinationProvider] with a progress-
 * reporting OkHttp body, so only listing lives here.
 */
interface ArchivoSyncApi {
    @GET
    suspend fun listFiles(
        @Url url: String,
        @Header("Authorization") authorization: String?,
    ): List<RemoteFileDto>
}
