package com.box.android.feature.service.details

import androidx.compose.runtime.Immutable
import com.box.android.core.process.AppLogEntry
import com.box.android.data.box.BoxApp

@Immutable
data class AppDetailsUiState(
    val app: BoxApp? = null,
    val isEditingName: Boolean = false,
    val editedName: String = "",
    val isEnvSheetVisible: Boolean = false,
    val isDeleteDialogOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val logs: List<AppLogEntry> = emptyList()
)
