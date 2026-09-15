package com.box.android.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.box.android.data.auth.AuthRepository
import com.box.android.data.box.BoxApp
import com.box.android.data.box.BoxAppStatus
import com.box.android.data.box.BoxRepository
import com.box.android.data.box.BoxRuntime
import com.box.android.data.local.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val boxRepository: BoxRepository,
    private val authRepository: AuthRepository,
    private val preferencesRepository: UserPreferencesRepository? = null
) : ViewModel() {

    private val initialLanEnabled = preferencesRepository?.isLanAccessEnabled() ?: false

    private val _uiState = MutableStateFlow(HomeUiState(isLanAccessEnabled = initialLanEnabled))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<HomeEffect>()
    val effect: SharedFlow<HomeEffect> = _effect.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        boxRepository.setLanAccessEnabled(initialLanEnabled)
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                boxRepository.apps,
                boxRepository.volumes,
                authRepository.currentUser,
                _searchQuery
            ) { apps, volumes, user, query ->
                val filtered = filterApps(apps, query)
                val runningCount = apps.count { it.status == BoxAppStatus.RUNNING }
                _uiState.update { current ->
                    current.copy(
                        apps = apps,
                        volumes = volumes,
                        filteredApps = filtered,
                        currentUser = user,
                        runningAppsCount = runningCount
                    )
                }
            }.collect()
        }
    }

    private fun filterApps(
        apps: List<BoxApp>,
        query: String
    ): List<BoxApp> {
        if (query.isBlank()) return apps
        return apps.filter { app ->
            app.name.contains(query, ignoreCase = true) ||
                app.description.contains(query, ignoreCase = true)
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SearchQueryChanged -> {
                _searchQuery.value = event.query
                _uiState.update { current ->
                    val filtered = filterApps(current.apps, event.query)
                    current.copy(searchQuery = event.query, filteredApps = filtered)
                }
            }
            is HomeEvent.ToggleSearch -> {
                _uiState.update { current ->
                    val newActive = !current.isSearchActive
                    val query = if (newActive) current.searchQuery else ""
                    _searchQuery.value = query
                    val filtered = filterApps(current.apps, query)
                    current.copy(isSearchActive = newActive, searchQuery = query, filteredApps = filtered)
                }
            }
            is HomeEvent.ToggleAppStatus -> {
                val app = _uiState.value.apps.find { it.id == event.appId }
                if (app != null && app.status == BoxAppStatus.STARTING) return
                if (app != null && app.status == BoxAppStatus.STOPPED && app.requiresConfig) {
                    _uiState.update { it.copy(appPendingEnvConfig = app) }
                } else {
                    viewModelScope.launch {
                        try {
                            boxRepository.toggleAppStatus(event.appId)
                        } catch (t: Throwable) {
                            Log.e("HomeViewModel", "Error toggling app status ${event.appId}", t)
                        }
                    }
                }
            }
            is HomeEvent.DismissEnvConfig -> {
                _uiState.update { it.copy(appPendingEnvConfig = null) }
            }
            is HomeEvent.SaveEnvConfig -> {
                val app = _uiState.value.apps.find { it.id == event.appId }
                val hasEmptyVars = event.envVars.any { it.value.isBlank() } || (app?.requiredEnvKeys?.any { event.envVars[it].isNullOrBlank() } ?: false)
                _uiState.update { it.copy(appPendingEnvConfig = null) }
                viewModelScope.launch {
                    try {
                        boxRepository.updateAppEnvVars(event.appId, event.envVars)
                        if (event.shouldStart && !hasEmptyVars && app?.status != BoxAppStatus.RUNNING && app?.status != BoxAppStatus.STARTING) {
                            boxRepository.toggleAppStatus(event.appId)
                        }
                    } catch (t: Throwable) {
                        Log.e("HomeViewModel", "Error saving env config for ${event.appId}", t)
                    }
                }
            }
            is HomeEvent.AppClicked -> {
                viewModelScope.launch {
                    _effect.emit(HomeEffect.NavigateToAppDetails(event.appId))
                }
            }
            is HomeEvent.RequestDeleteApp -> {
                if (event.app.status == BoxAppStatus.STOPPED) {
                    _uiState.update { it.copy(appPendingDeletion = event.app) }
                }
            }
            is HomeEvent.DeleteApp -> {
                val appId = event.appId
                _uiState.update { current ->
                    val newApps = current.apps.filterNot { it.id == appId }
                    current.copy(
                        apps = newApps,
                        filteredApps = filterApps(newApps, current.searchQuery),
                        appPendingDeletion = null
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        boxRepository.deleteApp(appId)
                    } catch (t: Throwable) {
                        Log.e("HomeViewModel", "Error deleting app $appId", t)
                    }
                }
            }
            is HomeEvent.ConfirmDeleteApp -> {
                val appToDelete = _uiState.value.appPendingDeletion
                _uiState.update { current ->
                    val newApps = current.apps.filterNot { it.id == appToDelete?.id }
                    current.copy(
                        appPendingDeletion = null,
                        apps = newApps,
                        filteredApps = filterApps(newApps, current.searchQuery)
                    )
                }
                if (appToDelete != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            boxRepository.deleteApp(appToDelete.id)
                        } catch (t: Throwable) {
                            Log.e("HomeViewModel", "Error deleting app ${appToDelete.id}", t)
                        }
                    }
                }
            }
            is HomeEvent.DismissDeleteDialog -> {
                _uiState.update { it.copy(appPendingDeletion = null) }
            }
            is HomeEvent.OpenNewItemSheet -> {
                _uiState.update { it.copy(isNewItemSheetVisible = true) }
            }
            is HomeEvent.DismissNewItemSheet -> {
                _uiState.update { it.copy(isNewItemSheetVisible = false) }
            }
            is HomeEvent.NewAppSelected -> {
                _uiState.update { it.copy(isNewItemSheetVisible = false) }
                viewModelScope.launch {
                    _effect.emit(HomeEffect.NavigateToNewApp(null))
                }
            }
            is HomeEvent.StartNewApp -> {
                viewModelScope.launch {
                    _effect.emit(HomeEffect.NavigateToNewApp(event.packageId?.takeIf { it.isNotBlank() }))
                }
            }
            is HomeEvent.NewVolumeSelected -> {
                _uiState.update { it.copy(isNewItemSheetVisible = false) }
                viewModelScope.launch {
                    _effect.emit(HomeEffect.NavigateToNewVolume)
                }
            }
            is HomeEvent.ImportBoxClicked -> {
                _uiState.update { it.copy(isNewItemSheetVisible = true) }
            }
            is HomeEvent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab) }
            }
            is HomeEvent.ProfileClicked -> {
                viewModelScope.launch {
                    _effect.emit(HomeEffect.NavigateToProfile)
                }
            }
            is HomeEvent.LogoutClicked -> {
                viewModelScope.launch {
                    authRepository.logout()
                    _effect.emit(HomeEffect.NavigateToLogin)
                }
            }
            is HomeEvent.ClearBuildCache -> {
                viewModelScope.launch {
                    try {
                        val bytesFreed = boxRepository.clearBuildCache()
                        _effect.emit(HomeEffect.ShowToast("CACHE_CLEARED:$bytesFreed"))
                    } catch (t: Throwable) {
                        Log.e("HomeViewModel", "Error clearing build cache", t)
                        _effect.emit(HomeEffect.ShowToast("CACHE_CLEARED:0"))
                    }
                }
            }
            is HomeEvent.ToggleLanAccess -> {
                preferencesRepository?.setLanAccessEnabled(event.enabled)
                boxRepository.setLanAccessEnabled(event.enabled)
                _uiState.update { it.copy(isLanAccessEnabled = event.enabled) }
            }
        }
    }

    companion object {
        fun provideFactory(
            boxRepository: BoxRepository,
            authRepository: AuthRepository,
            preferencesRepository: UserPreferencesRepository? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(boxRepository, authRepository, preferencesRepository) as T
                }
            }
    }
}
