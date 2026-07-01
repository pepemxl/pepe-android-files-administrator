package com.pepe.archivosync.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.repository.DownloadScheduler
import com.pepe.archivosync.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val transfers: TransferRepository,
    private val scheduler: DownloadScheduler,
) : ViewModel() {

    val items: StateFlow<List<DownloadItem>> = transfers.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Pull the real remote listing into the screen. */
    fun refresh() = viewModelScope.launch {
        _refreshing.value = true
        transfers.refreshDownloads()
        _refreshing.value = false
    }

    /** Optimistically flip to DOWNLOADING and hand off to the background worker. */
    fun startDownload(id: String) = viewModelScope.launch {
        transfers.startDownload(id)
        scheduler.scheduleDownload(id)
    }
}
