package com.pepe.archivosync.data.destination

import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.RemoteType
import com.pepe.archivosync.domain.repository.DestinationProvider
import com.pepe.archivosync.domain.repository.DestinationResolver
import javax.inject.Inject

/** Picks the active provider from [AppSettings.remoteType]. */
class DestinationResolverImpl @Inject constructor(
    private val rest: RestDestinationProvider,
    private val cloud: CloudDestinationProvider,
) : DestinationResolver {
    override fun resolve(settings: AppSettings): DestinationProvider =
        when (settings.remoteType) {
            RemoteType.REST -> rest
            RemoteType.CLOUD -> cloud
        }
}
