package com.box.android.feature.service.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.box.android.data.box.BoxApp
import com.box.android.data.box.BoxAppStatus
import com.box.android.data.box.BoxRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AppDetailsEvent {
    data object StartEditingName : AppDetailsEvent
    data class NameChanged(val name: String) : AppDetailsEvent
    data object SaveName : AppDetailsEvent
    data object CancelEditingName : AppDetailsEvent
    data object ToggleStatus : AppDetailsEvent
    data object OpenEnvSheet : AppDetailsEvent
    data object DismissEnvSheet : AppDetailsEvent
    data class SaveEnvVars(val envVars: Map<String, String>, val shouldStart: Boolean) : AppDetailsEvent
    data object RequestDeleteApp : AppDetailsEvent
    data object ConfirmDeleteApp : AppDetailsEvent
    data object DismissDeleteDialog : AppDetailsEvent
}

sealed interface AppDetailsEffect {
    data object NavigateBack : AppDetailsEffect
}

class AppDetailsViewModel(
    private val appId: String,
    private val boxRepository: BoxRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppDetailsUiState())
    val uiState: StateFlow<AppDetailsUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<AppDetailsEffect>()
    val effect: SharedFlow<AppDetailsEffect> = _effect.asSharedFlow()

    init {
        observeApp()
        observeLogs()
    }

    private fun observeApp() {
        viewModelScope.launch {
            boxRepository.apps.collect { apps ->
                val current = apps.find { it.id == appId }
                if (current != null) {
                    _uiState.update { it.copy(app = current, editedName = if (!it.isEditingName) current.name else it.editedName) }
                }
            }
        }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            boxRepository.getLogsFlow(appId).collect { logsList ->
                _uiState.update { it.copy(logs = logsList) }
            }
        }
    }

    fun onEvent(event: AppDetailsEvent) {
        when (event) {
            is AppDetailsEvent.StartEditingName -> {
                _uiState.update { it.copy(isEditingName = true, editedName = it.app?.name ?: "") }
            }
            is AppDetailsEvent.NameChanged -> {
                _uiState.update { it.copy(editedName = event.name) }
            }
            is AppDetailsEvent.SaveName -> {
                val state = _uiState.value
                val newName = state.editedName.trim()
                if (newName.isNotBlank() && state.app != null) {
                    viewModelScope.launch {
                        boxRepository.updateApp(state.app.copy(name = newName))
                        _uiState.update { it.copy(isEditingName = false) }
                    }
                }
            }
            is AppDetailsEvent.CancelEditingName -> {
                _uiState.update { it.copy(isEditingName = false, editedName = it.app?.name ?: "") }
            }
            is AppDetailsEvent.ToggleStatus -> {
                val app = _uiState.value.app ?: return
                if (app.status == BoxAppStatus.STARTING) return
                if (app.status == BoxAppStatus.STOPPED && app.requiresConfig) {
                    _uiState.update { it.copy(isEnvSheetVisible = true) }
                } else {
                    viewModelScope.launch {
                        try {
                            boxRepository.toggleAppStatus(app.id)
                        } catch (t: Throwable) {
                            Log.e("AppDetailsViewModel", "Error toggling app status ${app.id}", t)
                        }
                    }
                }
            }
            is AppDetailsEvent.OpenEnvSheet -> {
                _uiState.update { it.copy(isEnvSheetVisible = true) }
            }
            is AppDetailsEvent.DismissEnvSheet -> {
                _uiState.update { it.copy(isEnvSheetVisible = false) }
            }
            is AppDetailsEvent.SaveEnvVars -> {
                val app = _uiState.value.app ?: return
                val hasEmptyVars = event.envVars.any { it.value.isBlank() } || app.requiredEnvKeys.any { event.envVars[it].isNullOrBlank() }
                _uiState.update { it.copy(isEnvSheetVisible = false) }
                viewModelScope.launch {
                    try {
                        boxRepository.updateAppEnvVars(app.id, event.envVars)
                        if (event.shouldStart && !hasEmptyVars && app.status != BoxAppStatus.RUNNING) {
                            boxRepository.toggleAppStatus(app.id)
                        }
                    } catch (t: Throwable) {
                        Log.e("AppDetailsViewModel", "Error saving env vars for ${app.id}", t)
                    }
                }
            }
            is AppDetailsEvent.RequestDeleteApp -> {
                if (_uiState.value.app?.status == BoxAppStatus.STOPPED) {
                    _uiState.update { it.copy(isDeleteDialogOpen = true) }
                }
            }
            is AppDetailsEvent.ConfirmDeleteApp -> {
                _uiState.update { it.copy(isDeleteDialogOpen = false) }
                viewModelScope.launch {
                    _effect.emit(AppDetailsEffect.NavigateBack)
                    try {
                        boxRepository.deleteApp(appId)
                    } catch (t: Throwable) {
                        Log.e("AppDetailsViewModel", "Error deleting app $appId", t)
                    }
                }
            }
            is AppDetailsEvent.DismissDeleteDialog -> {
                _uiState.update { it.copy(isDeleteDialogOpen = false) }
            }
        }
    }

    companion object {
        fun provideFactory(appId: String, boxRepository: BoxRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppDetailsViewModel(appId, boxRepository) as T
                }
            }
    }
}
