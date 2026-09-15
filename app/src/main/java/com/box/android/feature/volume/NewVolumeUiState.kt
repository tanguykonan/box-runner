package com.box.android.feature.volume

import androidx.annotation.StringRes
import com.box.android.data.box.CapacityUnit
import com.box.android.data.box.VolumeCapacityType

data class NewVolumeUiState(
    val volumeName: String = "",
    val capacityType: VolumeCapacityType = VolumeCapacityType.PRESET_1_GB,
    val customValue: String = "",
    val customUnit: CapacityUnit = CapacityUnit.GB,
    val mountPath: String = "/data",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    @StringRes val errorMessageRes: Int? = null
) {
    val resolvedCapacityDisplay: String
        get() = when (capacityType) {
            VolumeCapacityType.PRESET_500_MB -> "500 MB"
            VolumeCapacityType.PRESET_1_GB -> "1 GB"
            VolumeCapacityType.PRESET_2_5_GB -> "2.5 GB"
            VolumeCapacityType.PRESET_5_GB -> "5 GB"
            VolumeCapacityType.STANDALONE -> "Standalone"
            VolumeCapacityType.CUSTOM -> {
                val num = customValue.trim().ifEmpty { "1" }
                "$num ${customUnit.symbol}"
            }
        }

    val resolvedSizeMb: Long?
        get() = when (capacityType) {
            VolumeCapacityType.PRESET_500_MB -> 500L
            VolumeCapacityType.PRESET_1_GB -> 1024L
            VolumeCapacityType.PRESET_2_5_GB -> 2560L
            VolumeCapacityType.PRESET_5_GB -> 5120L
            VolumeCapacityType.STANDALONE -> null
            VolumeCapacityType.CUSTOM -> {
                val num = customValue.trim().toFloatOrNull() ?: 1f
                if (customUnit == CapacityUnit.GB) (num * 1024).toLong() else num.toLong()
            }
        }
}
