package com.box.android.feature.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.box.android.R

enum class HomeTab(
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    RESOURCES(R.string.tab_resources, Icons.Rounded.Memory),
    HOME(R.string.tab_services, Icons.Rounded.Widgets),
    SETTINGS(R.string.tab_settings, Icons.Rounded.Settings)
}
