package com.box.android.feature.volume.list

import com.box.android.data.box.BoxVolume

data class VolumesUiState(
    val volumes: List<BoxVolume> = emptyList(),
    val volumePendingDeletion: BoxVolume? = null,
    val isLoading: Boolean = false
) {
    val totalUsedMb: Float
        get() = volumes.sumOf { it.usedMb.toDouble() }.toFloat()

    val totalUsedDisplay: String
        get() = if (totalUsedMb >= 1024f) {
            String.format(java.util.Locale.US, "%.1f GB", totalUsedMb / 1024f)
        } else {
            String.format(java.util.Locale.US, "%.1f MB", totalUsedMb)
        }
}
