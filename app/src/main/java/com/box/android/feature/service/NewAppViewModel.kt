package com.box.android.feature.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.box.android.R
import com.box.android.data.box.BoxApp
import com.box.android.data.box.BoxAppStatus
import com.box.android.data.box.BoxRepository
import com.box.android.data.box.BoxRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NewAppEvent {
    data class PackageIdChanged(val packageId: String) : NewAppEvent
    data class AccessTokenChanged(val token: String) : NewAppEvent
    data class AppNameChanged(val name: String) : NewAppEvent
    data class RuntimeSelected(val runtime: BoxRuntime) : NewAppEvent
    data class PortChanged(val port: String) : NewAppEvent
    data object DeployClicked : NewAppEvent
    data object DismissError : NewAppEvent
}

sealed interface NewAppEffect {
    data object NavigateBack : NewAppEffect
}

data class HubManifestResult(
    val runtime: BoxRuntime?,
    val port: Int?,
    val requiredEnvKeys: List<String>,
    val defaultEnvVars: Map<String, String> = emptyMap(),
    val version: String,
    val workdir: String? = null,
    val entrypointArgs: List<String> = emptyList()
)

class NewAppViewModel(
    private val boxRepository: BoxRepository,
    initialPackageId: String? = null
) : ViewModel() {

    private val _uiState: MutableStateFlow<NewAppUiState>
    val uiState: StateFlow<NewAppUiState>

    private val _effect = MutableSharedFlow<NewAppEffect>()
    val effect: SharedFlow<NewAppEffect> = _effect.asSharedFlow()

    init {
        val initialPkg = initialPackageId?.trim() ?: ""
        val inferredName = if (initialPkg.contains("/")) {
            initialPkg.substringAfter("/").substringBefore(":").replace("-", " ").replaceFirstChar { it.uppercase() }
        } else ""
        val initialRuntime = when {
            initialPkg.contains("node", ignoreCase = true) || initialPkg.contains("js", ignoreCase = true) -> BoxRuntime.NODEJS
            else -> BoxRuntime.PYTHON
        }
        _uiState = MutableStateFlow(
            NewAppUiState(
                packageId = initialPkg,
                appName = inferredName,
                selectedRuntime = initialRuntime
            )
        )
        uiState = _uiState.asStateFlow()
    }

    fun onEvent(event: NewAppEvent) {
        when (event) {
            is NewAppEvent.PackageIdChanged -> {
                _uiState.update { current ->
                    // Auto-fill app name and runtime if empty or matching keywords
                    val inferredName = if (current.appName.isBlank() && event.packageId.contains("/")) {
                        event.packageId.substringAfter("/").substringBefore(":").replace("-", " ").replaceFirstChar { it.uppercase() }
                    } else current.appName

                    val inferredRuntime = when {
                        event.packageId.contains("node", ignoreCase = true) || event.packageId.contains("js", ignoreCase = true) -> BoxRuntime.NODEJS
                        event.packageId.contains("python", ignoreCase = true) || event.packageId.contains("py", ignoreCase = true) -> BoxRuntime.PYTHON
                        else -> current.selectedRuntime
                    }

                    current.copy(
                        packageId = event.packageId,
                        appName = inferredName,
                        selectedRuntime = inferredRuntime,
                        errorMessage = null,
                        errorMessageRes = null
                    )
                }
            }
            is NewAppEvent.AccessTokenChanged -> {
                _uiState.update { it.copy(accessToken = event.token) }
            }
            is NewAppEvent.AppNameChanged -> {
                _uiState.update { it.copy(appName = event.name, errorMessage = null, errorMessageRes = null) }
            }
            is NewAppEvent.RuntimeSelected -> {
                _uiState.update { it.copy(selectedRuntime = event.runtime) }
            }
            is NewAppEvent.PortChanged -> {
                val cleaned = event.port.filter { it.isDigit() }.take(5)
                _uiState.update { it.copy(portInput = cleaned) }
            }
            is NewAppEvent.DeployClicked -> deployApp()
            is NewAppEvent.DismissError -> {
                _uiState.update { it.copy(errorMessage = null, errorMessageRes = null) }
            }
        }
    }

    private fun deployApp() {
        val state = _uiState.value
        val packageId = state.packageId.trim()
        val appName = state.appName.trim()

        if (packageId.isBlank()) {
            _uiState.update { it.copy(errorMessageRes = R.string.new_app_err_empty_package) }
            return
        }
        if (!state.isPackageFormatValid) {
            _uiState.update { it.copy(errorMessageRes = R.string.new_app_err_invalid_format) }
            return
        }
        if (appName.isBlank()) {
            _uiState.update { it.copy(errorMessageRes = R.string.new_app_err_empty_name) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, errorMessageRes = null) }
            try {
                val effectiveToken = state.accessToken.trim().takeIf { it.isNotBlank() }

                // 1. Resolve real manifest from Box Hub Registry if available
                val hubManifest = resolveManifestFromHub(packageId, effectiveToken)

                val effectiveRuntime = hubManifest?.runtime ?: when {
                    packageId.contains("node", ignoreCase = true) || appName.contains("node", ignoreCase = true) ||
                        packageId.contains("js", ignoreCase = true) || appName.contains("express", ignoreCase = true) -> BoxRuntime.NODEJS
                    packageId.contains("python", ignoreCase = true) || appName.contains("python", ignoreCase = true) ||
                        packageId.contains("fastapi", ignoreCase = true) || appName.contains("flask", ignoreCase = true) -> BoxRuntime.PYTHON
                    else -> state.selectedRuntime
                }

                val isBotOrWorker = appName.contains("bot", ignoreCase = true) ||
                    packageId.contains("bot", ignoreCase = true) ||
                    appName.contains("discord", ignoreCase = true) ||
                    packageId.contains("discord", ignoreCase = true) ||
                    appName.contains("worker", ignoreCase = true)

                val effectivePort = if (state.portInput.isNotBlank()) {
                    state.portInput.toIntOrNull()
                } else if (isBotOrWorker) {
                    null
                } else {
                    hubManifest?.port
                }

                val requiredKeys = if (!hubManifest?.requiredEnvKeys.isNullOrEmpty()) {
                    hubManifest!!.requiredEnvKeys
                } else if (isBotOrWorker) {
                    if (appName.contains("discord", ignoreCase = true) || packageId.contains("discord", ignoreCase = true)) {
                        listOf("DISCORD_TOKEN", "CLIENT_ID")
                    } else {
                        listOf("BOT_TOKEN")
                    }
                } else if (appName.contains("api", ignoreCase = true) || packageId.contains("api", ignoreCase = true) || packageId.contains("backend", ignoreCase = true)) {
                    listOf("SECRET_KEY")
                } else {
                    emptyList()
                }

                val newApp = BoxApp(
                    id = "app_${System.currentTimeMillis()}",
                    name = appName,
                    runtime = effectiveRuntime,
                    status = BoxAppStatus.STOPPED,
                    description = ".box package installed from Box Hub catalogue",
                    port = effectivePort,
                    packageId = packageId,
                    requiredEnvKeys = requiredKeys.filter { it != "raw" },
                    envVars = (hubManifest?.defaultEnvVars ?: emptyMap()).filterKeys { it != "raw" },
                    memoryUsageMb = 0f,
                    uptime = "0m",
                    lastActivity = "Installed",
                    workdir = hubManifest?.workdir,
                    entrypointArgs = hubManifest?.entrypointArgs ?: emptyList(),
                    accessToken = effectiveToken
                )

                boxRepository.addApp(newApp)
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(NewAppEffect.NavigateBack)
            } catch (t: Throwable) {
                _uiState.update { it.copy(isLoading = false, errorMessage = t.message ?: "Failed to deploy app") }
            }
        }
    }

    private suspend fun resolveManifestFromHub(packageRef: String, token: String?): HubManifestResult? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val trimmed = packageRef.trim()
            val author = trimmed.substringBefore("/")
            val rest = trimmed.substringAfter("/")
            val name = rest.substringBefore(":")
            val tag = if (rest.contains(":")) rest.substringAfter(":") else "latest"

            val url = java.net.URL("https://boxhub.paxiz.org/api/v1/registry/resolve/$author/$name/$tag")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "BoxMobileRunner/1.0")
            if (!token.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            val status = conn.responseCode
            if (status in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(body)
                if (json.optBoolean("success", true)) {
                    val manifest = json.optJSONObject("manifest")
                    if (manifest != null) {
                        val runtimeObj = manifest.optJSONObject("runtime")
                        val runtimeTypeStr = runtimeObj?.optString("type", "")?.lowercase() ?: ""
                        val resolvedRuntime = when {
                            runtimeTypeStr.contains("node") || runtimeTypeStr.contains("js") -> BoxRuntime.NODEJS
                            runtimeTypeStr.contains("python") || runtimeTypeStr.contains("py") -> BoxRuntime.PYTHON
                            runtimeTypeStr.contains("rust") -> BoxRuntime.RUST
                            runtimeTypeStr.contains("go") -> BoxRuntime.GO
                            else -> null
                        }

                        val executionObj = manifest.optJSONObject("execution")
                        val portsArray = executionObj?.optJSONArray("ports")
                        val resolvedPort = if (portsArray != null && portsArray.length() > 0) {
                            portsArray.optString(0, "").filter { it.isDigit() }.toIntOrNull()
                        } else null

                        val envVarsObj = manifest.optJSONObject("envVariables")
                        val rawEnvStr = manifest.optString("envVariables", "")
                        val reqKeys = mutableListOf<String>()
                        val defaultEnvMap = mutableMapOf<String, String>()

                        fun parseRawEnv(text: String) {
                            text.lines().forEach { line ->
                                val trimmed = line.trim()
                                if (trimmed.isNotBlank() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                                    val k = trimmed.substringBefore("=").trim()
                                    val v = trimmed.substringAfter("=").trim()
                                    if (k.isNotBlank() && k != "raw") {
                                        reqKeys.add(k)
                                        if (v.isNotBlank()) {
                                            defaultEnvMap[k] = v
                                        }
                                    }
                                }
                            }
                        }

                        if (envVarsObj != null) {
                            if (envVarsObj.has("raw")) {
                                parseRawEnv(envVarsObj.optString("raw", ""))
                            } else {
                                val keys = envVarsObj.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    if (key != "raw") {
                                        reqKeys.add(key)
                                        val v = envVarsObj.optString(key, "")
                                        if (v.isNotBlank()) {
                                            defaultEnvMap[key] = v
                                        }
                                    }
                                }
                            }
                        } else if (rawEnvStr.isNotBlank()) {
                            parseRawEnv(rawEnvStr)
                        }

                        val workdir = manifest.optString("workdir", "").takeIf { it.isNotBlank() }
                        val entrypointStr = manifest.optString("entrypoint", "")
                            .ifBlank { executionObj?.optString("entrypoint", "") ?: "" }
                        val entrypointArgs = if (entrypointStr.isNotBlank()) {
                            val clean = entrypointStr.removePrefix("python3 ").removePrefix("python ").removePrefix("node ").removePrefix("./").trim()
                            val parts = clean.split(" ").filter { it.isNotBlank() }
                            if (parts.size > 1) parts.drop(1) else emptyList()
                        } else emptyList()

                        return@withContext HubManifestResult(
                            runtime = resolvedRuntime,
                            port = resolvedPort,
                            requiredEnvKeys = reqKeys,
                            defaultEnvVars = defaultEnvMap,
                            version = manifest.optString("version", "1.0.0"),
                            workdir = workdir,
                            entrypointArgs = entrypointArgs
                        )
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun provideFactory(boxRepository: BoxRepository, initialPackageId: String? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NewAppViewModel(boxRepository, initialPackageId) as T
                }
            }
    }
}
