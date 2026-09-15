package com.box.android.feature.volume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.box.android.R
import com.box.android.data.box.BoxRepository
import com.box.android.data.box.BoxVolume
import com.box.android.data.box.CapacityUnit
import com.box.android.data.box.VolumeCapacityType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NewVolumeEvent {
    data class VolumeNameChanged(val name: String) : NewVolumeEvent
    data class CapacityTypeSelected(val type: VolumeCapacityType) : NewVolumeEvent
    data class CustomValueChanged(val value: String) : NewVolumeEvent
    data class CustomUnitSelected(val unit: CapacityUnit) : NewVolumeEvent
    data class MountPathChanged(val path: String) : NewVolumeEvent
    data object CreateVolumeClicked : NewVolumeEvent
    data object DismissError : NewVolumeEvent
}

sealed interface NewVolumeEffect {
    data object NavigateBack : NewVolumeEffect
}

class NewVolumeViewModel(
    private val boxRepository: BoxRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewVolumeUiState())
    val uiState: StateFlow<NewVolumeUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<NewVolumeEffect>()
    val effect: SharedFlow<NewVolumeEffect> = _effect.asSharedFlow()

    fun onEvent(event: NewVolumeEvent) {
        when (event) {
            is NewVolumeEvent.VolumeNameChanged -> {
                _uiState.update { it.copy(volumeName = event.name, errorMessage = null, errorMessageRes = null) }
            }
            is NewVolumeEvent.CapacityTypeSelected -> {
                _uiState.update { it.copy(capacityType = event.type, errorMessage = null, errorMessageRes = null) }
            }
            is NewVolumeEvent.CustomValueChanged -> {
                val cleaned = event.value.filter { it.isDigit() || it == '.' }.take(6)
                _uiState.update { it.copy(customValue = cleaned, errorMessage = null, errorMessageRes = null) }
            }
            is NewVolumeEvent.CustomUnitSelected -> {
                _uiState.update { it.copy(customUnit = event.unit) }
            }
            is NewVolumeEvent.MountPathChanged -> {
                _uiState.update { it.copy(mountPath = event.path) }
            }
            is NewVolumeEvent.CreateVolumeClicked -> createVolume()
            is NewVolumeEvent.DismissError -> {
                _uiState.update { it.copy(errorMessage = null, errorMessageRes = null) }
            }
        }
    }

    private fun createVolume() {
        val state = _uiState.value
        val name = state.volumeName.trim()

        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessageRes = R.string.new_volume_err_empty_name) }
            return
        }

        if (state.capacityType == VolumeCapacityType.CUSTOM && state.customValue.isBlank()) {
            _uiState.update { it.copy(errorMessageRes = R.string.new_volume_err_empty_capacity) }
            return
        }

        val formattedMount = if (state.mountPath.isBlank()) "/data" else state.mountPath.trim()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, errorMessageRes = null) }
            delay(500)

            val newVolume = BoxVolume(
                id = "vol_${System.currentTimeMillis()}",
                name = name,
                capacityDisplay = state.resolvedCapacityDisplay,
                sizeMb = state.resolvedSizeMb,
                mountPath = formattedMount,
                usedMb = 0f,
                createdAt = "Just now"
            )

            try {
                boxRepository.addVolume(newVolume)
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(NewVolumeEffect.NavigateBack)
            } catch (t: Throwable) {
                _uiState.update { it.copy(isLoading = false, errorMessage = t.message ?: "Failed to create volume") }
            }
        }
    }

    companion object {
        fun provideFactory(boxRepository: BoxRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NewVolumeViewModel(boxRepository) as T
                }
            }
    }
}
