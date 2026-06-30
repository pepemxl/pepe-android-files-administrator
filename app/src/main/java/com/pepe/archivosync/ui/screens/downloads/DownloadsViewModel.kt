package com.pepe.archivosync.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val transfers: TransferRepository,
) : ViewModel() {

    val items: StateFlow<List<DownloadItem>> = transfers.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startDownload(id: String) = viewModelScope.launch { transfers.startDownload(id) }
}
