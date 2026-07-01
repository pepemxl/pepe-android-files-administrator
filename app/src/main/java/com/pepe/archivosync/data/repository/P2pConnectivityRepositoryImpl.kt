package com.pepe.archivosync.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pepe.archivosync.data.remote.OrchestratorApi
import com.pepe.archivosync.data.remote.RegisterDeviceDto
import com.pepe.archivosync.data.webrtc.SignalEvent
import com.pepe.archivosync.data.webrtc.SignalingClient
import com.pepe.archivosync.data.webrtc.WebRtcSessionManager
import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.IceServer
import com.pepe.archivosync.domain.model.P2pDevice
import com.pepe.archivosync.domain.model.P2pFileTransfer
import com.pepe.archivosync.domain.model.PeerLink
import com.pepe.archivosync.domain.model.SignalingState
import com.pepe.archivosync.domain.repository.P2pConnectivityRepository
import com.pepe.archivosync.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real P2P over WebRTC, wiring together the orchestrator control API
 * ([OrchestratorApi]), the signaling socket ([SignalingClient]) and the
 * WebRTC/DataChannel layer ([WebRtcSessionManager]).
 */
@Singleton
class P2pConnectivityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: OrchestratorApi,
    private val signaling: SignalingClient,
    private val webrtc: WebRtcSessionManager,
    private val settingsRepo: SettingsRepository,
    private val appScope: CoroutineScope,
) : P2pConnectivityRepository {

    private val _signalingState = MutableStateFlow(SignalingState.DISCONNECTED)
    override val signalingState: Flow<SignalingState> = _signalingState.asStateFlow()

    override val peers: Flow<List<PeerLink>> = webrtc.peers
    override val transfers: Flow<List<P2pFileTransfer>> = webrtc.transfers

    private var collecting = false
    private val deviceNames = mutableMapOf<String, String>()

    override suspend fun connect(): Result<Unit> = runCatching {
        val settings = settingsRepo.settings.first()
        require(settings.token.isNotBlank()) { "missing bearer token" }
        val deviceId = ensureRegistered(settings)

        // Best-effort ICE fetch (welcome will also carry them).
        runCatching { api.iceServers(url(settings, "ice-servers"), auth(settings)) }
            .getOrNull()?.let { resp ->
                webrtc.setIceServers(resp.iceServers.map { IceServer(it.urls, it.username, it.credential) })
            }

        startCollecting(settings)
        _signalingState.value = SignalingState.CONNECTING
        signaling.connect(settings.signalingUrl, settings.token, deviceId)
    }

    override fun disconnect() {
        signaling.close()
        webrtc.closeAll()
        _signalingState.value = SignalingState.DISCONNECTED
    }

    override suspend fun listDevices(): Result<List<P2pDevice>> = runCatching {
        val settings = settingsRepo.settings.first()
        val resp = api.listDevices(url(settings, "devices"), auth(settings))
        resp.devices
            .filter { it.id != settings.deviceId }
            .map { P2pDevice(it.id, it.name, it.platform, it.lastSeenAt) }
            .also { list ->
                list.forEach { deviceNames[it.id] = it.name }
                webrtc.peerNames(deviceNames.toMap())
            }
    }

    override suspend fun connectToPeer(deviceId: String): Result<Unit> = runCatching {
        webrtc.openConnection(deviceId, deviceNames[deviceId])
    }

    override suspend fun sendFile(deviceId: String, uri: Uri): Result<Unit> = runCatching {
        val (name, size) = queryFile(uri)
        val input = context.contentResolver.openInputStream(uri)
            ?: error("cannot open $uri")
        webrtc.sendFile(deviceId, name, size, input)
    }

    // --- internals ---------------------------------------------------------

    private suspend fun ensureRegistered(settings: AppSettings): String {
        if (settings.deviceId.isNotBlank()) return settings.deviceId
        val body = RegisterDeviceDto(
            name = settings.deviceName,
            platform = "android",
            publicKey = randomKeyB64(),
        )
        val resp = api.registerDevice(url(settings, "devices"), auth(settings), body)
        settingsRepo.update { it.copy(deviceId = resp.id) }
        return resp.id
    }

    private fun startCollecting(settings: AppSettings) {
        if (collecting) return
        collecting = true
        appScope.launch {
            signaling.events.collect { event -> route(event, settings) }
        }
    }

    private fun route(event: SignalEvent, settings: AppSettings) {
        when (event) {
            is SignalEvent.Open -> _signalingState.value = SignalingState.CONNECTING
            is SignalEvent.Welcome -> {
                if (event.iceServers.isNotEmpty()) webrtc.setIceServers(event.iceServers)
                if (settings.deviceId.isBlank() && event.deviceId.isNotBlank()) {
                    appScope.launch { settingsRepo.update { it.copy(deviceId = event.deviceId) } }
                }
                _signalingState.value = SignalingState.CONNECTED
            }
            is SignalEvent.Offer -> appScope.launch {
                webrtc.onRemoteOffer(event.from, event.sdp, deviceNames[event.from])
            }
            is SignalEvent.Answer -> appScope.launch { webrtc.onRemoteAnswer(event.from, event.sdp) }
            is SignalEvent.Ice -> webrtc.onRemoteIce(event.from, event.candidate, event.sdpMid, event.sdpMLineIndex)
            is SignalEvent.Bye -> webrtc.onRemoteBye(event.from)
            is SignalEvent.Failed -> _signalingState.value = SignalingState.ERROR
            is SignalEvent.Closed -> _signalingState.value = SignalingState.DISCONNECTED
        }
    }

    private fun queryFile(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        return name to size
    }

    private fun randomKeyB64(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun auth(settings: AppSettings): String? = settings.token
        .takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("Bearer", ignoreCase = true)) it else "Bearer $it" }

    private fun url(settings: AppSettings, path: String): String =
        settings.orchestratorUrl.trimEnd('/') + "/" + path.trimStart('/')
}
