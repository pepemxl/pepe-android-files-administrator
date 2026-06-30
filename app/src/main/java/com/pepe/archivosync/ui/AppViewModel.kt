package com.pepe.archivosync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepe.archivosync.domain.model.AppLanguage
import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.repository.ConnectionResult
import com.pepe.archivosync.domain.repository.SettingsRepository
import com.pepe.archivosync.domain.usecase.TestConnectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnStatus { IDLE, TESTING, OK, FAIL }

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val testConnection: TestConnectionUseCase,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _connStatus = MutableStateFlow(ConnStatus.OK)
    val connStatus: StateFlow<ConnStatus> = _connStatus.asStateFlow()

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settingsRepo.update { it.copy(language = language) }
    }

    fun setAccent(accentName: String) = viewModelScope.launch {
        settingsRepo.update { it.copy(accentName = accentName) }
    }

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepo.update(transform)
    }

    fun test() = viewModelScope.launch {
        _connStatus.value = ConnStatus.TESTING
        _connStatus.value = when (testConnection()) {
            is ConnectionResult.Ok -> ConnStatus.OK
            is ConnectionResult.Failed -> ConnStatus.FAIL
        }
    }
}
