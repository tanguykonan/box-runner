package com.box.android.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.box.android.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OnboardingEffect {
    data object NavigateToLogin : OnboardingEffect
}

class OnboardingViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<OnboardingEffect>()
    val effect: SharedFlow<OnboardingEffect> = _effect.asSharedFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.PageChanged -> {
                _uiState.update { it.copy(currentPageIndex = event.pageIndex) }
            }
            is OnboardingEvent.NextClicked,
            is OnboardingEvent.SkipClicked,
            is OnboardingEvent.GetStartedClicked -> {
                completeOnboarding()
            }
        }
    }

    private fun completeOnboarding() {
        preferencesRepository.setOnboardingCompleted(true)
        viewModelScope.launch {
            _effect.emit(OnboardingEffect.NavigateToLogin)
        }
    }

    companion object {
        fun provideFactory(
            preferencesRepository: UserPreferencesRepository
        ): androidx.lifecycle.ViewModelProvider.Factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return OnboardingViewModel(preferencesRepository) as T
            }
        }
    }
}
