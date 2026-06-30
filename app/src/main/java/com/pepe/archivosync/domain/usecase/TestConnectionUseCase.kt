package com.pepe.archivosync.domain.usecase

import com.pepe.archivosync.domain.repository.ConnectionResult
import com.pepe.archivosync.domain.repository.DestinationResolver
import com.pepe.archivosync.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Probes the currently-configured remote. */
class TestConnectionUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val resolver: DestinationResolver,
) {
    suspend operator fun invoke(): ConnectionResult {
        val current = settings.settings.first()
        return resolver.resolve(current).test(current)
    }
}
