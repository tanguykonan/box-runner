package com.box.android.feature.home

sealed interface HomeEffect {
    data class NavigateToAppDetails(val appId: String) : HomeEffect
    data object NavigateToProfile : HomeEffect
    data object OpenBoxPicker : HomeEffect
    data class NavigateToNewApp(val packageId: String? = null) : HomeEffect
    data object NavigateToNewVolume : HomeEffect
    data object NavigateToLogin : HomeEffect
    data class ShowToast(val message: String) : HomeEffect
}
