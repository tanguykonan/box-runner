package com.box.android.feature.home

import com.box.android.data.auth.AuthUser
import com.box.android.data.box.BoxApp
import com.box.android.data.box.BoxVolume

data class HomeUiState(
    val selectedTab: HomeTab = HomeTab.HOME,
    val apps: List<BoxApp> = emptyList(),
    val filteredApps: List<BoxApp> = emptyList(),
    val volumes: List<BoxVolume> = emptyList(),
    val isLanAccessEnabled: Boolean = true,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val appPendingDeletion: BoxApp? = null,
    val appPendingEnvConfig: BoxApp? = null,
    val currentUser: AuthUser? = null,
    val isNewItemSheetVisible: Boolean = false,
    val isLoading: Boolean = false,
    val runningAppsCount: Int = 0
)
