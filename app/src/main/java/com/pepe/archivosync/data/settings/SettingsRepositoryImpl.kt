package com.pepe.archivosync.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pepe.archivosync.domain.model.AppLanguage
import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.CloudProvider
import com.pepe.archivosync.domain.model.RemoteType
import com.pepe.archivosync.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
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
        val p2pEnabled = booleanPreferencesKey("p2p_enabled")
        val orchestratorUrl = stringPreferencesKey("orchestrator_url")
        val signalingUrl = stringPreferencesKey("signaling_url")
        val deviceId = stringPreferencesKey("device_id")
        val deviceName = stringPreferencesKey("device_name")
        val autoUpload = booleanPreferencesKey("auto_upload")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val compress = booleanPreferencesKey("compress")
        val notifications = booleanPreferencesKey("notifications")
    }

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
            p[Keys.token] = next.token
            p[Keys.cloudProvider] = next.cloudProvider.name
            p[Keys.host] = next.host
            p[Keys.accessKey] = next.accessKey
            p[Keys.secretKey] = next.secretKey
            p[Keys.p2pEnabled] = next.p2pEnabled
            p[Keys.orchestratorUrl] = next.orchestratorUrl
            p[Keys.signalingUrl] = next.signalingUrl
            p[Keys.deviceId] = next.deviceId
            p[Keys.deviceName] = next.deviceName
            p[Keys.autoUpload] = next.autoUpload
            p[Keys.wifiOnly] = next.wifiOnly
            p[Keys.compress] = next.compress
            p[Keys.notifications] = next.notifications
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            language = this[Keys.language]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: defaults.language,
            accentName = this[Keys.accent] ?: defaults.accentName,
            remoteType = this[Keys.remoteType]?.let { runCatching { RemoteType.valueOf(it) }.getOrNull() }
                ?: defaults.remoteType,
            baseUrl = this[Keys.baseUrl] ?: defaults.baseUrl,
            listEndpoint = this[Keys.listEndpoint] ?: defaults.listEndpoint,
            uploadEndpoint = this[Keys.uploadEndpoint] ?: defaults.uploadEndpoint,
            token = this[Keys.token] ?: defaults.token,
            cloudProvider = this[Keys.cloudProvider]?.let { runCatching { CloudProvider.valueOf(it) }.getOrNull() }
                ?: defaults.cloudProvider,
            host = this[Keys.host] ?: defaults.host,
            accessKey = this[Keys.accessKey] ?: defaults.accessKey,
            secretKey = this[Keys.secretKey] ?: defaults.secretKey,
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
    }
}
