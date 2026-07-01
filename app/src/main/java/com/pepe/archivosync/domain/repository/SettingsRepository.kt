package com.pepe.archivosync.domain.repository

import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.ServerProfile
import kotlinx.coroutines.flow.Flow

/** Persistent, reactive access to [AppSettings] (backed by DataStore). */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)

    /** Create or update a server profile (blank id = create). Saving activates it. */
    suspend fun saveProfile(profile: ServerProfile)

    /** Make [id] the active profile; its fields become the live connection. */
    suspend fun activateProfile(id: String)

    /** Remove a profile. No-op if it is the last one; if it was active, another becomes active. */
    suspend fun deleteProfile(id: String)
}
