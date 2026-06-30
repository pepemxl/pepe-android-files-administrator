package com.pepe.archivosync.ui.screens.uploads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepe.archivosync.domain.model.TransferItem
import com.pepe.archivosync.domain.model.TransferStatus
import com.pepe.archivosync.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** null filter = "all"; otherwise restrict to a single status. */
data class UploadsUiState(
    val filter: TransferStatus? = null,
    val items: List<TransferItem> = emptyList(),
    val counts: Map<TransferStatus?, Int> = emptyMap(),
)

@HiltViewModel
class UploadsViewModel @Inject constructor(
    private val transfers: TransferRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<TransferStatus?>(null)

    val state: StateFlow<UploadsUiState> =
        combine(transfers.observeUploads(), filter) { uploads, f ->
            val counts = mapOf<TransferStatus?, Int>(
                null to uploads.size,
                TransferStatus.UPLOADING to uploads.count { it.status == TransferStatus.UPLOADING },
                TransferStatus.DONE to uploads.count { it.status == TransferStatus.DONE },
                TransferStatus.FAILED to uploads.count { it.status == TransferStatus.FAILED },
                TransferStatus.QUEUED to uploads.count { it.status == TransferStatus.QUEUED },
            )
            UploadsUiState(
                filter = f,
                items = if (f == null) uploads else uploads.filter { it.status == f },
                counts = counts,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UploadsUiState())

    fun setFilter(status: TransferStatus?) { filter.value = status }

    fun retry(id: String) = viewModelScope.launch { transfers.retry(id) }
}
