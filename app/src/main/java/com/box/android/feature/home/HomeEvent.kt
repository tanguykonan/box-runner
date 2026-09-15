package com.box.android.feature.home

import com.box.android.data.box.BoxApp

sealed interface HomeEvent {
    data class SelectTab(val tab: HomeTab) : HomeEvent
    data class SearchQueryChanged(val query: String) : HomeEvent
    data object ToggleSearch : HomeEvent
    data class ToggleAppStatus(val appId: String) : HomeEvent
    data class AppClicked(val appId: String) : HomeEvent
    data class RequestDeleteApp(val app: BoxApp) : HomeEvent
    data class DeleteApp(val appId: String) : HomeEvent
    data object ConfirmDeleteApp : HomeEvent
    data object DismissDeleteDialog : HomeEvent
    data object OpenNewItemSheet : HomeEvent
    data object DismissNewItemSheet : HomeEvent
    data object NewAppSelected : HomeEvent
    data class StartNewApp(val packageId: String? = null) : HomeEvent
    data object NewVolumeSelected : HomeEvent
    data object DismissEnvConfig : HomeEvent
    data class SaveEnvConfig(val appId: String, val envVars: Map<String, String>, val shouldStart: Boolean) : HomeEvent
    data object ImportBoxClicked : HomeEvent
    data object ProfileClicked : HomeEvent
    data object LogoutClicked : HomeEvent
    data object ClearBuildCache : HomeEvent
    data class ToggleLanAccess(val enabled: Boolean) : HomeEvent
}
