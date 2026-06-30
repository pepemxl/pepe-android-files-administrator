package com.pepe.archivosync.ui.screens.p2p

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepe.archivosync.domain.model.P2pMode
import com.pepe.archivosync.domain.model.P2pStatus
import com.pepe.archivosync.domain.model.P2pTransfer
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

@HiltViewModel
class P2pViewModel @Inject constructor(
    private val repo: P2pRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<P2pMode?>(null)

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

    fun setFilter(mode: P2pMode?) { filter.value = mode }

    fun toggleEnabled() = viewModelScope.launch {
        settingsRepo.update { it.copy(p2pEnabled = !it.p2pEnabled) }
    }

    fun togglePaused(id: String, paused: Boolean) = viewModelScope.launch {
        repo.setPaused(id, paused)
    }
}
