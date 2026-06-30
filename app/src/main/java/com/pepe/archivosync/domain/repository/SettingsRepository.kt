package com.pepe.archivosync.domain.repository

import com.pepe.archivosync.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** Persistent, reactive access to [AppSettings] (backed by DataStore). */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
