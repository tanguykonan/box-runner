package com.box.android.feature.volume.list

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.box.android.data.box.BoxRepository
import com.box.android.data.box.BoxVolume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VolumesEvent {
    data class RequestDeleteVolume(val volume: BoxVolume) : VolumesEvent
    data object ConfirmDeleteVolume : VolumesEvent
    data object DismissDeleteDialog : VolumesEvent
}

class VolumesViewModel(
    private val boxRepository: BoxRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolumesUiState())
    val uiState: StateFlow<VolumesUiState> = _uiState.asStateFlow()

    init {
        observeVolumes()
    }

    private fun observeVolumes() {
        viewModelScope.launch {
            boxRepository.volumes.collect { list ->
                _uiState.update { it.copy(volumes = list) }
            }
        }
    }

    fun onEvent(event: VolumesEvent) {
        when (event) {
            is VolumesEvent.RequestDeleteVolume -> {
                _uiState.update { it.copy(volumePendingDeletion = event.volume) }
            }
            is VolumesEvent.ConfirmDeleteVolume -> {
                val volume = _uiState.value.volumePendingDeletion ?: return
                _uiState.update { it.copy(volumePendingDeletion = null) }
                viewModelScope.launch {
                    try {
                        boxRepository.deleteVolume(volume.id)
                    } catch (t: Throwable) {
                        Log.e("VolumesViewModel", "Error deleting volume ${volume.id}", t)
                    }
                }
            }
            is VolumesEvent.DismissDeleteDialog -> {
                _uiState.update { it.copy(volumePendingDeletion = null) }
            }
        }
    }

    companion object {
        fun provideFactory(boxRepository: BoxRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VolumesViewModel(boxRepository) as T
                }
            }
    }
}
