package com.box.android.data.box

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.box.android.R

enum class VolumeCapacityType(@StringRes val labelRes: Int) {
    PRESET_500_MB(R.string.volume_preset_500_mb),
    PRESET_1_GB(R.string.volume_preset_1_gb),
    PRESET_2_5_GB(R.string.volume_preset_2_5_gb),
    PRESET_5_GB(R.string.volume_preset_5_gb),
    STANDALONE(R.string.new_volume_capacity_standalone),
    CUSTOM(R.string.new_volume_capacity_custom)
}

enum class CapacityUnit(val symbol: String, @StringRes val symbolRes: Int) {
    MB("MB", R.string.unit_mb),
    GB("GB", R.string.unit_gb)
}

@Immutable
data class BoxVolume(
    val id: String,
    val name: String,
    val capacityDisplay: String,
    val sizeMb: Long? = null,
    val mountPath: String = "/data",
    val usedMb: Float = 0f,
    val createdAt: String = "Just now"
)
