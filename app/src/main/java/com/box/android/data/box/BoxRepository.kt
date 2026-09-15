package com.box.android.data.box

import android.content.Context
import android.util.Log
import com.box.android.core.process.AppLogEntry
import com.box.android.core.process.BoxProcessManager
import com.box.android.core.runtime.RuntimeManager
import com.box.android.core.sandbox.SandboxManager
import com.box.android.core.service.BoxRunnerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface BoxRepository {
    val apps: Flow<List<BoxApp>>
    val volumes: Flow<List<BoxVolume>>
    fun getLogsFlow(appId: String): Flow<List<AppLogEntry>>
    suspend fun getAppById(id: String): BoxApp?
    suspend fun toggleAppStatus(id: String)
    suspend fun addApp(app: BoxApp)
    suspend fun updateApp(app: BoxApp)
    suspend fun updateAppEnvVars(id: String, envVars: Map<String, String>)
    suspend fun deleteApp(id: String)
    suspend fun addVolume(volume: BoxVolume)
    suspend fun deleteVolume(id: String)
    suspend fun clearBuildCache(): Long
    fun setLanAccessEnabled(enabled: Boolean)
    fun isLanAccessEnabled(): Boolean
}

class BoxRepositoryImpl(
    private val context: Context? = null
) : BoxRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startingAppIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val runtimeManager: RuntimeManager? = context?.let { RuntimeManager(it) }
    private val sandboxManager: SandboxManager? = context?.let { SandboxManager(it) }
    val processManager: BoxProcessManager? = if (context != null && runtimeManager != null && sandboxManager != null) {
        BoxProcessManager(context, runtimeManager, sandboxManager)
    } else null

    private val _apps = MutableStateFlow<List<BoxApp>>(emptyList())
    override val apps: Flow<List<BoxApp>> = _apps.asStateFlow()

    private val _volumes = MutableStateFlow<List<BoxVolume>>(emptyList())
    override val volumes: Flow<List<BoxVolume>> = _volumes.asStateFlow()

    init {
        initData()
        startMetricsTracker()
    }

    private fun initData() {
        scope.launch {
            val sm = sandboxManager ?: return@launch
            val persistedApps = sm.loadPersistedApps()
            _apps.value = persistedApps
            val persistedVolumes = sm.loadPersistedVolumes()
            _volumes.value = persistedVolumes
        }
    }

    private fun startMetricsTracker() {
        scope.launch {
            var volumeCheckCounter = 0
            while (isActive) {
                delay(2000)
                val pm = processManager
                val sm = sandboxManager

                if (pm != null) {
                    val currentApps = _apps.value
                    if (currentApps.any { it.status == BoxAppStatus.RUNNING }) {
                        var hasChanged = false
                        val updated = currentApps.map { app ->
                            if (app.status == BoxAppStatus.RUNNING) {
                                val isStillRunning = pm.isAppRunning(app.id)
                                if (isStillRunning) {
                                    val newUptime = pm.getUptime(app.id)
                                    val newMem = pm.getMemoryUsageMb(app.id)
                                    if (newUptime != app.uptime || kotlin.math.abs(newMem - app.memoryUsageMb) > 0.5f) {
                                        hasChanged = true
                                        app.copy(
                                            uptime = newUptime,
                                            memoryUsageMb = newMem,
                                            lastActivity = "Active"
                                        )
                                    } else app
                                } else {
                                    hasChanged = true
                                    app.copy(
                                        status = BoxAppStatus.STOPPED,
                                        memoryUsageMb = 0f,
                                        uptime = "0m",
                                        lastActivity = "Exited"
                                    )
                                }
                            } else {
                                app
                            }
                        }
                        if (hasChanged) {
                            _apps.value = updated
                        }
                    }
                }

                // Refresh volume disk sizes only every 10 seconds to avoid continuous IO thrashing
                volumeCheckCounter++
                if (sm != null && volumeCheckCounter >= 5) {
                    volumeCheckCounter = 0
                    val currentVolumes = _volumes.value
                    if (currentVolumes.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            var volChanged = false
                            val updatedVols = currentVolumes.map { vol ->
                                val actualMb = sm.calculateVolumeSizeMb(vol.name)
                                if (actualMb != vol.usedMb) {
                                    volChanged = true
                                    vol.copy(usedMb = actualMb)
                                } else vol
                            }
                            if (volChanged) {
                                _volumes.value = updatedVols
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getLogsFlow(appId: String): Flow<List<AppLogEntry>> {
        return processManager?.getLogsFlow(appId) ?: emptyFlow()
    }

    override suspend fun getAppById(id: String): BoxApp? {
        return _apps.value.find { it.id == id }
    }

    override suspend fun toggleAppStatus(id: String) {
        val currentApp = getAppById(id) ?: return
        if (currentApp.status == BoxAppStatus.STARTING || !startingAppIds.add(id)) return

        try {
            val isCurrentlyRunning = currentApp.status == BoxAppStatus.RUNNING

            if (isCurrentlyRunning) {
                processManager?.stopApp(id)
                _apps.update { list ->
                    list.map { app ->
                        if (app.id == id) {
                            app.copy(
                                status = BoxAppStatus.STOPPED,
                                memoryUsageMb = 0f,
                                uptime = "0m",
                                lastActivity = "Just stopped"
                            )
                        } else app
                    }
                }
            } else {
                val sm = sandboxManager
                val isExtracted = sm?.isPackageExtracted(id, currentApp.runtime) == true
                val needsDownload = !isExtracted && currentApp.packageId.contains("/")

                if (needsDownload) {
                    // Only transition to STARTING when download is required
                    _apps.update { list ->
                        list.map { app ->
                            if (app.id == id) {
                                app.copy(
                                    status = BoxAppStatus.STARTING,
                                    lastActivity = "Downloading"
                                )
                            } else app
                        }
                    }
                }

                val result = try {
                    withContext(NonCancellable) {
                        processManager?.startApp(currentApp, isLanAccessEnabledState) ?: Result.success(Unit)
                    }
                } catch (t: Throwable) {
                    Result.failure(t)
                }

                if (result.isSuccess) {
                    val freshlyLoadedApp = sm?.loadPersistedApps()?.firstOrNull { it.id == id } ?: currentApp
                    _apps.update { list ->
                        list.map { app ->
                            if (app.id == id) {
                                freshlyLoadedApp.copy(
                                    status = BoxAppStatus.RUNNING,
                                    memoryUsageMb = processManager?.getMemoryUsageMb(id) ?: 20f,
                                    uptime = "0s",
                                    lastActivity = "Active"
                                )
                            } else app
                        }
                    }
                } else {
                    _apps.update { list ->
                        list.map { app ->
                            if (app.id == id) {
                                app.copy(
                                    status = BoxAppStatus.ERROR,
                                    lastActivity = "Error"
                                )
                            } else app
                        }
                    }
                }
            }

            // Update Foreground Service
            context?.let { ctx ->
                val runningCount = _apps.value.count { it.status == BoxAppStatus.RUNNING }
                if (runningCount > 0) {
                    BoxRunnerService.start(ctx, runningCount)
                } else {
                    BoxRunnerService.stop(ctx)
                }
            }
        } finally {
            startingAppIds.remove(id)
        }
    }

    override suspend fun addApp(app: BoxApp) {
        sandboxManager?.saveAppMetadata(app)
        _apps.update { current ->
            listOf(app) + current.filterNot { it.id == app.id }
        }
    }

    override suspend fun updateApp(app: BoxApp) {
        sandboxManager?.saveAppMetadata(app)
        _apps.update { list ->
            list.map { if (it.id == app.id) app else it }
        }
    }

    override suspend fun updateAppEnvVars(id: String, envVars: Map<String, String>) {
        var updatedApp: BoxApp? = null
        _apps.update { list ->
            list.map {
                if (it.id == id) {
                    val modified = it.copy(
                        envVars = envVars,
                        lastActivity = if (it.requiredEnvKeys.all { key -> !envVars[key].isNullOrBlank() }) "Configured" else it.lastActivity
                    )
                    updatedApp = modified
                    modified
                } else it
            }
        }
        updatedApp?.let { app ->
            sandboxManager?.saveAppMetadata(app)
            sandboxManager?.updateEnvFile(app.id, app.envVars, app.port, isLanAccessEnabledState)
        }
    }

    override suspend fun deleteApp(id: String) {
        // 1. Optimistic removal for instant 60 FPS UI responsiveness
        _apps.update { list -> list.filterNot { it.id == id } }

        // 2. Perform process stop, log cleanup, metadata removal, and disk deletion in background
        withContext(Dispatchers.IO) {
            try {
                processManager?.stopApp(id)
            } catch (t: Throwable) {
                Log.e("BoxRepository", "Failed to stop app $id before deletion", t)
            }
            try {
                processManager?.clearLogs(id)
            } catch (t: Throwable) {
                Log.e("BoxRepository", "Failed to clear logs for app $id", t)
            }
            try {
                sandboxManager?.removeAppMetadata(id)
            } catch (t: Throwable) {
                Log.e("BoxRepository", "Failed to remove metadata for app $id", t)
            }
            try {
                sandboxManager?.cleanSandbox(id)
            } catch (t: Throwable) {
                Log.e("BoxRepository", "Failed to clean sandbox for app $id", t)
            }
        }
    }

    override suspend fun addVolume(volume: BoxVolume) {
        sandboxManager?.createPhysicalVolume(volume)
        _volumes.update { current ->
            listOf(volume) + current.filterNot { it.id == volume.id }
        }
    }

    override suspend fun deleteVolume(id: String) {
        val volumeToDelete = _volumes.value.find { it.id == id }
        // 1. Optimistic removal for instant UI feedback
        _volumes.update { list -> list.filterNot { it.id == id } }

        if (volumeToDelete != null) {
            withContext(Dispatchers.IO) {
                try {
                    sandboxManager?.deletePhysicalVolume(volumeToDelete.name)
                } catch (t: Throwable) {
                    Log.e("BoxRepository", "Failed to delete physical volume ${volumeToDelete.name}", t)
                }
            }
        }
    }

    private var isLanAccessEnabledState: Boolean = false

    override fun setLanAccessEnabled(enabled: Boolean) {
        isLanAccessEnabledState = enabled
    }

    override fun isLanAccessEnabled(): Boolean = isLanAccessEnabledState

    override suspend fun clearBuildCache(): Long {
        return sandboxManager?.clearBuildCache() ?: 0L
    }
}
