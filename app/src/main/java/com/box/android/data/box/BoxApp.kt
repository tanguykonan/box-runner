package com.box.android.data.box

import androidx.compose.runtime.Immutable

enum class BoxRuntime(val displayName: String) {
    PYTHON("Python"),
    NODEJS("Node.js"),
    RUST("Rust"),
    GO("Go")
}

enum class BoxAppStatus {
    RUNNING,
    STOPPED,
    STARTING,
    ERROR
}

@Immutable
data class BoxApp(
    val id: String,
    val name: String,
    val runtime: BoxRuntime,
    val status: BoxAppStatus,
    val description: String = "",
    val port: Int? = null,
    val packageId: String = "",
    val envVars: Map<String, String> = emptyMap(),
    val requiredEnvKeys: List<String> = emptyList(),
    val memoryUsageMb: Float = 0f,
    val uptime: String = "0m",
    val lastActivity: String = "Just now",
    val workdir: String? = null,
    val entrypointArgs: List<String> = emptyList(),
    val accessToken: String? = null
) {
    val requiresConfig: Boolean
        get() {
            val cleanReqs = requiredEnvKeys.filter { it != "raw" && it.isNotBlank() }
            if (cleanReqs.isNotEmpty() && cleanReqs.any { envVars[it].isNullOrBlank() }) {
                return true
            }
            val cleanEnvs = envVars.filterKeys { it != "raw" && it.isNotBlank() }
            if (cleanEnvs.isNotEmpty() && cleanEnvs.any { it.value.isBlank() }) {
                return true
            }
            return false
        }
}
