package com.pepe.archivosync.ui.screens.dashboard

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepe.archivosync.core.Formatters
import com.pepe.archivosync.domain.model.TransferItem
import com.pepe.archivosync.domain.model.TransferStatus
import com.pepe.archivosync.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val pending: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val storageLabel: String = "—",
    val recent: List<TransferItem> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext context: Context,
    transfers: TransferRepository,
) : ViewModel() {

    private val storageLabel: String = runCatching {
        val stat = StatFs(context.filesDir.absolutePath)
        Formatters.bytes(stat.availableBytes)
    }.getOrDefault("—")

    val state: StateFlow<DashboardUiState> = transfers.observeUploads()
        .map { uploads ->
            DashboardUiState(
                pending = uploads.count { it.status == TransferStatus.UPLOADING || it.status == TransferStatus.QUEUED },
                done = uploads.count { it.status == TransferStatus.DONE },
                failed = uploads.count { it.status == TransferStatus.FAILED },
                storageLabel = storageLabel,
                recent = uploads.take(4),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
