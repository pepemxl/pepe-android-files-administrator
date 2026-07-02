package com.pepe.archivosync.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pepe.archivosync.domain.model.AppLanguage
import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.CloudProvider
import com.pepe.archivosync.domain.model.RemoteType
import com.pepe.archivosync.domain.model.ServerProfile
import com.pepe.archivosync.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreSecretCipher,
) : SettingsRepository {

    private object Keys {
        val language = stringPreferencesKey("language")
        val accent = stringPreferencesKey("accent")
        val remoteType = stringPreferencesKey("remote_type")
        val baseUrl = stringPreferencesKey("base_url")
        val listEndpoint = stringPreferencesKey("list_endpoint")
        val uploadEndpoint = stringPreferencesKey("upload_endpoint")
        val token = stringPreferencesKey("token")
        val cloudProvider = stringPreferencesKey("cloud_provider")
        val host = stringPreferencesKey("host")
        val accessKey = stringPreferencesKey("access_key")
        val secretKey = stringPreferencesKey("secret_key")
        val region = stringPreferencesKey("region")
        val cloudPath = stringPreferencesKey("cloud_path")
        val endpoint = stringPreferencesKey("endpoint")
        val p2pEnabled = booleanPreferencesKey("p2p_enabled")
        val orchestratorUrl = stringPreferencesKey("orchestrator_url")
        val signalingUrl = stringPreferencesKey("signaling_url")
        val deviceId = stringPreferencesKey("device_id")
        val deviceName = stringPreferencesKey("device_name")
        val autoUpload = booleanPreferencesKey("auto_upload")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val compress = booleanPreferencesKey("compress")
        val notifications = booleanPreferencesKey("notifications")
        val profiles = stringPreferencesKey("server_profiles")
        val activeProfileId = stringPreferencesKey("active_profile_id")
    }

    private val json = Json { ignoreUnknownKeys = true }

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val next = transform(current)
        dataStore.edit { p ->
            p[Keys.language] = next.language.name
            p[Keys.accent] = next.accentName
            p[Keys.remoteType] = next.remoteType.name
            p[Keys.baseUrl] = next.baseUrl
            p[Keys.listEndpoint] = next.listEndpoint
            p[Keys.uploadEndpoint] = next.uploadEndpoint
            p[Keys.token] = cipher.encrypt(next.token)
            p[Keys.cloudProvider] = next.cloudProvider.name
            p[Keys.host] = next.host
            p[Keys.accessKey] = cipher.encrypt(next.accessKey)
            p[Keys.secretKey] = cipher.encrypt(next.secretKey)
            p[Keys.region] = next.region
            p[Keys.cloudPath] = next.cloudPath
            p[Keys.endpoint] = next.endpoint
            p[Keys.p2pEnabled] = next.p2pEnabled
            p[Keys.orchestratorUrl] = next.orchestratorUrl
            p[Keys.signalingUrl] = next.signalingUrl
            p[Keys.deviceId] = next.deviceId
            p[Keys.deviceName] = next.deviceName
            p[Keys.autoUpload] = next.autoUpload
            p[Keys.wifiOnly] = next.wifiOnly
            p[Keys.compress] = next.compress
            p[Keys.notifications] = next.notifications

            // Keep the active profile's snapshot in sync with the live fields, so
            // edits made in Settings land in the currently-selected profile.
            val activeId = next.activeProfileId.ifBlank { next.profiles.firstOrNull()?.id ?: DEFAULT_ID }
            val activeName = next.profiles.firstOrNull { it.id == activeId }?.name ?: DEFAULT_NAME
            val active = next.toActiveProfile(activeId, activeName)
            val list = if (next.profiles.any { it.id == activeId }) {
                next.profiles.map { if (it.id == activeId) active else it }
            } else {
                next.profiles + active
            }
            p[Keys.profiles] = cipher.encrypt(json.encodeToString(list))
            p[Keys.activeProfileId] = activeId
        }
    }

    override suspend fun saveProfile(profile: ServerProfile) {
        val current = settings.first()
        val id = profile.id.ifBlank { UUID.randomUUID().toString() }
        val saved = profile.copy(id = id)
        val list = if (current.profiles.any { it.id == id }) {
            current.profiles.map { if (it.id == id) saved else it }
        } else {
            current.profiles + saved
        }
        dataStore.edit { p ->
            writeDestination(p, saved)   // saving activates the profile
            p[Keys.profiles] = cipher.encrypt(json.encodeToString(list))
            p[Keys.activeProfileId] = id
        }
    }

    override suspend fun activateProfile(id: String) {
        val current = settings.first()
        val target = current.profiles.firstOrNull { it.id == id } ?: return
        dataStore.edit { p ->
            writeDestination(p, target)
            p[Keys.activeProfileId] = id
        }
    }

    override suspend fun deleteProfile(id: String) {
        val current = settings.first()
        if (current.profiles.size <= 1) return   // always keep at least one
        val list = current.profiles.filterNot { it.id == id }
        dataStore.edit { p ->
            p[Keys.profiles] = cipher.encrypt(json.encodeToString(list))
            if (current.activeProfileId == id) {
                val next = list.first()
                writeDestination(p, next)
                p[Keys.activeProfileId] = next.id
            }
        }
    }

    /** Mirror a profile's destination fields into the live connection keys. */
    private fun writeDestination(p: MutablePreferences, s: ServerProfile) {
        p[Keys.remoteType] = s.remoteType.name
        p[Keys.baseUrl] = s.baseUrl
        p[Keys.listEndpoint] = s.listEndpoint
        p[Keys.uploadEndpoint] = s.uploadEndpoint
        p[Keys.token] = cipher.encrypt(s.token)
        p[Keys.cloudProvider] = s.cloudProvider.name
        p[Keys.host] = s.host
        p[Keys.accessKey] = cipher.encrypt(s.accessKey)
        p[Keys.secretKey] = cipher.encrypt(s.secretKey)
        p[Keys.region] = s.region
        p[Keys.cloudPath] = s.cloudPath
        p[Keys.endpoint] = s.endpoint
    }

    /** Build a profile snapshot from the live destination fields. */
    private fun AppSettings.toActiveProfile(id: String, name: String) = ServerProfile(
        id = id,
        name = name,
        remoteType = remoteType,
        baseUrl = baseUrl,
        listEndpoint = listEndpoint,
        uploadEndpoint = uploadEndpoint,
        token = token,
        cloudProvider = cloudProvider,
        host = host,
        accessKey = accessKey,
        secretKey = secretKey,
        region = region,
        cloudPath = cloudPath,
        endpoint = endpoint,
    )

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        val base = AppSettings(
            language = this[Keys.language]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: defaults.language,
            accentName = this[Keys.accent] ?: defaults.accentName,
            remoteType = this[Keys.remoteType]?.let { runCatching { RemoteType.valueOf(it) }.getOrNull() }
                ?: defaults.remoteType,
            baseUrl = this[Keys.baseUrl] ?: defaults.baseUrl,
            listEndpoint = this[Keys.listEndpoint] ?: defaults.listEndpoint,
            uploadEndpoint = this[Keys.uploadEndpoint] ?: defaults.uploadEndpoint,
            token = this[Keys.token]?.let { cipher.decrypt(it) } ?: defaults.token,
            cloudProvider = this[Keys.cloudProvider]?.let { runCatching { CloudProvider.valueOf(it) }.getOrNull() }
                ?: defaults.cloudProvider,
            host = this[Keys.host] ?: defaults.host,
            accessKey = this[Keys.accessKey]?.let { cipher.decrypt(it) } ?: defaults.accessKey,
            secretKey = this[Keys.secretKey]?.let { cipher.decrypt(it) } ?: defaults.secretKey,
            region = this[Keys.region] ?: defaults.region,
            cloudPath = this[Keys.cloudPath] ?: defaults.cloudPath,
            endpoint = this[Keys.endpoint] ?: defaults.endpoint,
            p2pEnabled = this[Keys.p2pEnabled] ?: defaults.p2pEnabled,
            orchestratorUrl = this[Keys.orchestratorUrl] ?: defaults.orchestratorUrl,
            signalingUrl = this[Keys.signalingUrl] ?: defaults.signalingUrl,
            deviceId = this[Keys.deviceId] ?: defaults.deviceId,
            deviceName = this[Keys.deviceName] ?: defaults.deviceName,
            autoUpload = this[Keys.autoUpload] ?: defaults.autoUpload,
            wifiOnly = this[Keys.wifiOnly] ?: defaults.wifiOnly,
            compress = this[Keys.compress] ?: defaults.compress,
            notifications = this[Keys.notifications] ?: defaults.notifications,
        )

        val stored = this[Keys.profiles]
            ?.let { cipher.decrypt(it) }
            ?.let { runCatching { json.decodeFromString<List<ServerProfile>>(it) }.getOrNull() }
            ?: emptyList()
        // No profiles yet (fresh install / migration): synthesize a "Default"
        // one from the current live fields so the UI always shows one active row.
        val profiles = stored.ifEmpty { listOf(base.toActiveProfile(DEFAULT_ID, DEFAULT_NAME)) }
        val activeId = (this[Keys.activeProfileId] ?: "").ifBlank { profiles.first().id }

        return base.copy(profiles = profiles, activeProfileId = activeId)
    }

    private companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_NAME = "Default"
    }
}
