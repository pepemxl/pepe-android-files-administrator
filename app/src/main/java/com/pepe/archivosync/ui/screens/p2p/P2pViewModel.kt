package com.pepe.archivosync.ui.screens.p2p

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepe.archivosync.domain.model.P2pDevice
import com.pepe.archivosync.domain.model.P2pFileTransfer
import com.pepe.archivosync.domain.model.P2pMode
import com.pepe.archivosync.domain.model.P2pStatus
import com.pepe.archivosync.domain.model.P2pTransfer
import com.pepe.archivosync.domain.model.PeerLink
import com.pepe.archivosync.domain.model.SignalingState
import com.pepe.archivosync.domain.repository.P2pConnectivityRepository
import com.pepe.archivosync.domain.repository.P2pRepository
import com.pepe.archivosync.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class P2pStats(
    val totalUpKbps: Long = 0,
    val totalDownKbps: Long = 0,
    val avgRatio: Double = 0.0,
    val totalPeers: Int = 0,
)

data class P2pUiState(
    val enabled: Boolean = true,
    val filter: P2pMode? = null,
    val transfers: List<P2pTransfer> = emptyList(),
    val counts: Map<P2pMode?, Int> = emptyMap(),
    val stats: P2pStats = P2pStats(),
)

/** Real WebRTC connectivity slice of the P2P screen. */
data class P2pConnUiState(
    val signaling: SignalingState = SignalingState.DISCONNECTED,
    val devices: List<P2pDevice> = emptyList(),
    val peers: List<PeerLink> = emptyList(),
    val transfers: List<P2pFileTransfer> = emptyList(),
)

@HiltViewModel
class P2pViewModel @Inject constructor(
    private val repo: P2pRepository,
    private val connectivity: P2pConnectivityRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<P2pMode?>(null)
    private val devices = MutableStateFlow<List<P2pDevice>>(emptyList())

    val state: StateFlow<P2pUiState> = combine(
        repo.observeTransfers(),
        filter,
        settingsRepo.settings.map { it.p2pEnabled },
    ) { transfers, f, enabled ->
        val active = if (enabled) transfers.filter { it.status == P2pStatus.ACTIVE } else emptyList()
        P2pUiState(
            enabled = enabled,
            filter = f,
            transfers = if (f == null) transfers else transfers.filter { it.mode == f },
            counts = mapOf(
                null to transfers.size,
                P2pMode.SEED to transfers.count { it.mode == P2pMode.SEED },
                P2pMode.LEECH to transfers.count { it.mode == P2pMode.LEECH },
            ),
            stats = P2pStats(
                totalUpKbps = active.sumOf { it.upRateKbps },
                totalDownKbps = active.filter { it.mode == P2pMode.LEECH }.sumOf { it.downRateKbps },
                avgRatio = if (transfers.isEmpty()) 0.0 else transfers.sumOf { it.ratio } / transfers.size,
                totalPeers = active.sumOf { it.peers },
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), P2pUiState())

    val connState: StateFlow<P2pConnUiState> = combine(
        connectivity.signalingState,
        connectivity.peers,
        connectivity.transfers,
        devices,
    ) { signaling, peers, transfers, devs ->
        P2pConnUiState(signaling = signaling, devices = devs, peers = peers, transfers = transfers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), P2pConnUiState())

    fun setFilter(mode: P2pMode?) { filter.value = mode }

    fun toggleEnabled() = viewModelScope.launch {
        settingsRepo.update { it.copy(p2pEnabled = !it.p2pEnabled) }
    }

    fun togglePaused(id: String, paused: Boolean) = viewModelScope.launch {
        repo.setPaused(id, paused)
    }

    // --- WebRTC connectivity ----------------------------------------------

    fun connect() = viewModelScope.launch {
        connectivity.connect()
        refreshDevices()
    }

    fun disconnect() = connectivity.disconnect()

    fun refreshDevices() = viewModelScope.launch {
        connectivity.listDevices().getOrNull()?.let { devices.value = it }
    }

    fun linkPeer(deviceId: String) = viewModelScope.launch {
        connectivity.connectToPeer(deviceId)
    }

    fun sendFile(deviceId: String, uri: Uri) = viewModelScope.launch {
        connectivity.sendFile(deviceId, uri)
    }
}
