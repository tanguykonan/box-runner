package com.box.android.feature.service

import androidx.annotation.StringRes
import com.box.android.data.box.BoxRuntime

data class NewAppUiState(
    val packageId: String = "",
    val accessToken: String = "",
    val appName: String = "",
    val selectedRuntime: BoxRuntime = BoxRuntime.PYTHON,
    val portInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    @StringRes val errorMessageRes: Int? = null
) {
    /**
     * Valide le format strict de référence du paquet : namespace/nom-paquet[:tag]
     * Exemples valides : box/fastapi-backend, box/discord-bot:v1.0, user/my-app:latest
     * Rejette formellement les mots isolés comme "vvvv", les slashs orphelins, etc.
     */
    val isPackageFormatValid: Boolean
        get() {
            val trimmed = packageId.trim()
            if (trimmed.isEmpty()) return true
            val strictRegex = Regex("^[a-zA-Z0-9_-]{2,30}/[a-zA-Z0-9_.-]{2,50}(:[a-zA-Z0-9_.-]{1,30})?$")
            return strictRegex.matches(trimmed)
        }

    val isPackageIdEmpty: Boolean
        get() = packageId.trim().isEmpty()

    val canInstall: Boolean
        get() = !isLoading &&
                !isPackageIdEmpty &&
                appName.trim().isNotBlank() &&
                isPackageFormatValid
}
