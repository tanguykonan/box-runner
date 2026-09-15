package com.box.android.core.sandbox

import android.content.Context
import android.util.Log
import com.box.android.data.box.BoxApp
import com.box.android.data.box.BoxRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SandboxManager(private val context: Context) {

    private val sandboxesDir: File by lazy {
        File(context.filesDir, "sandboxes").apply {
            if (!exists()) mkdirs()
        }
    }

    private val appsMetadataFile: File by lazy {
        File(sandboxesDir, "apps_metadata.json")
    }

    private val volumesDir: File by lazy {
        File(context.filesDir, "volumes").apply {
            if (!exists()) mkdirs()
        }
    }

    private val volumesMetadataFile: File by lazy {
        File(volumesDir, "volumes_metadata.json")
    }

    fun loadPersistedApps(): List<BoxApp> {
        if (!appsMetadataFile.exists()) {
            return emptyList()
        }
        val list = mutableListOf<BoxApp>()
        try {
            val jsonStr = appsMetadataFile.readText(Charsets.UTF_8)
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val runtimeStr = obj.optString("runtime", "PYTHON")
                val runtime = try {
                    BoxRuntime.valueOf(runtimeStr)
                } catch (_: Exception) {
                    BoxRuntime.PYTHON
                }

                val envVarsMap = mutableMapOf<String, String>()
                val envObj = obj.optJSONObject("envVars")
                if (envObj != null) {
                    val keys = envObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key != "raw" && key.isNotBlank()) {
                            envVarsMap[key] = envObj.optString(key, "")
                        }
                    }
                }

                val reqKeysList = mutableListOf<String>()
                val reqArray = obj.optJSONArray("requiredEnvKeys")
                if (reqArray != null) {
                    for (k in 0 until reqArray.length()) {
                        val rk = reqArray.getString(k)
                        if (rk != "raw" && rk.isNotBlank()) {
                            reqKeysList.add(rk)
                        }
                    }
                }

                val workdir = if (obj.has("workdir") && !obj.isNull("workdir")) obj.getString("workdir") else null
                val argsList = mutableListOf<String>()
                val argsArray = obj.optJSONArray("entrypointArgs")
                if (argsArray != null) {
                    for (a in 0 until argsArray.length()) {
                        argsList.add(argsArray.getString(a))
                    }
                }

                val accessToken = if (obj.has("accessToken") && !obj.isNull("accessToken")) obj.getString("accessToken") else null

                list.add(
                    BoxApp(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        runtime = runtime,
                        status = com.box.android.data.box.BoxAppStatus.STOPPED,
                        description = obj.optString("description", ""),
                        port = if (obj.has("port") && !obj.isNull("port")) obj.getInt("port") else null,
                        packageId = obj.optString("packageId", ""),
                        envVars = envVarsMap,
                        requiredEnvKeys = reqKeysList,
                        memoryUsageMb = 0f,
                        uptime = "0m",
                        lastActivity = obj.optString("lastActivity", "Ready"),
                        workdir = workdir,
                        entrypointArgs = argsList,
                        accessToken = accessToken
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading apps metadata", e)
        }
        return list
    }

    fun saveAppMetadata(app: BoxApp) {
        val current = loadPersistedApps().filterNot { it.id == app.id }
        writeAppsJson(listOf(app) + current)
    }

    fun saveAllAppsMetadata(apps: List<BoxApp>) {
        writeAppsJson(apps)
    }

    fun removeAppMetadata(appId: String) {
        val current = loadPersistedApps().filterNot { it.id == appId }
        writeAppsJson(current)
    }

    private fun writeAppsJson(apps: List<BoxApp>) {
        try {
            val array = org.json.JSONArray()
            apps.forEach { app ->
                val obj = org.json.JSONObject().apply {
                    put("id", app.id)
                    put("name", app.name)
                    put("runtime", app.runtime.name)
                    put("description", app.description)
                    if (app.port != null) {
                        put("port", app.port)
                    } else {
                        put("port", org.json.JSONObject.NULL)
                    }
                    put("packageId", app.packageId)
                    put("lastActivity", app.lastActivity)
                    if (app.workdir != null) {
                        put("workdir", app.workdir)
                    } else {
                        put("workdir", org.json.JSONObject.NULL)
                    }
                    val argsArr = org.json.JSONArray()
                    app.entrypointArgs.forEach { argsArr.put(it) }
                    put("entrypointArgs", argsArr)

                    val envJson = org.json.JSONObject()
                    app.envVars.filterKeys { it != "raw" && it.isNotBlank() }.forEach { (k, v) -> envJson.put(k, v) }
                    put("envVars", envJson)

                    val reqArray = org.json.JSONArray()
                    app.requiredEnvKeys.filter { it != "raw" && it.isNotBlank() }.forEach { reqArray.put(it) }
                    put("requiredEnvKeys", reqArray)

                    if (!app.accessToken.isNullOrBlank()) {
                        put("accessToken", app.accessToken)
                    }
                }
                array.put(obj)
            }
            appsMetadataFile.writeText(array.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing apps metadata", e)
        }
    }

    suspend fun updateEnvFile(appId: String, envVars: Map<String, String>, port: Int? = null, isLanAccess: Boolean = false): Unit = withContext(Dispatchers.IO) {
        val sandboxDir = getSandboxDir(appId)
        val hostAddress = if (isLanAccess) "0.0.0.0" else "127.0.0.1"
        val envLines = StringBuilder()
        envVars.forEach { (k, v) ->
            envLines.append("$k=$v\n")
        }
        if (port != null) {
            envLines.append("PORT=$port\n")
        }
        envLines.append("HOST=$hostAddress\n")
        val content = envLines.toString()
        File(sandboxDir, ".env.local").writeText(content, Charsets.UTF_8)
        File(sandboxDir, ".env").writeText(content, Charsets.UTF_8)
    }

    fun getSandboxDir(appId: String): File {
        return File(sandboxesDir, appId).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getVolumeDir(volumeName: String): File {
        return File(volumesDir, volumeName).apply {
            if (!exists()) mkdirs()
        }
    }

    fun calculateFolderSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            val rootPath = dir.toPath()
            if (java.nio.file.Files.isSymbolicLink(rootPath)) return 0L

            val stack = ArrayDeque<File>()
            stack.add(dir)
            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                try {
                    val currentPath = current.toPath()
                    if (java.nio.file.Files.isSymbolicLink(currentPath)) continue

                    if (current.isDirectory) {
                        current.listFiles()?.forEach { stack.add(it) }
                    } else if (current.isFile) {
                        size += current.length()
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        return size
    }

    fun calculateVolumeSizeMb(volumeName: String): Float {
        val dir = File(volumesDir, volumeName)
        if (!dir.exists()) return 0f
        val totalBytes = calculateFolderSizeBytes(dir)
        return (totalBytes.toDouble() / (1024 * 1024)).toFloat()
    }

    suspend fun createPhysicalVolume(volume: com.box.android.data.box.BoxVolume): File = withContext(Dispatchers.IO) {
        val volumeDir = getVolumeDir(volume.name)
        if (!volumeDir.exists()) {
            volumeDir.mkdirs()
        }
        saveVolumeMetadata(volume)
        volumeDir
    }

    suspend fun deletePhysicalVolume(volumeName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val volumeDir = File(volumesDir, volumeName)
            val deleted = if (volumeDir.exists() || java.nio.file.Files.isSymbolicLink(volumeDir.toPath())) {
                deleteRecursivelySafe(volumeDir)
            } else {
                true
            }
            removeVolumeMetadata(volumeName)
            deleted
        } catch (t: Throwable) {
            Log.e(TAG, "Error deleting physical volume $volumeName", t)
            false
        }
    }

    fun loadPersistedVolumes(): List<com.box.android.data.box.BoxVolume> {
        if (!volumesMetadataFile.exists()) return emptyList()
        val list = mutableListOf<com.box.android.data.box.BoxVolume>()
        try {
            val jsonStr = volumesMetadataFile.readText(Charsets.UTF_8)
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val realUsedMb = calculateVolumeSizeMb(name)
                list.add(
                    com.box.android.data.box.BoxVolume(
                        id = obj.getString("id"),
                        name = name,
                        capacityDisplay = obj.getString("capacityDisplay"),
                        sizeMb = if (obj.has("sizeMb")) obj.getLong("sizeMb") else null,
                        mountPath = obj.optString("mountPath", "/data"),
                        usedMb = realUsedMb,
                        createdAt = obj.optString("createdAt", "Recently")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading volumes metadata", e)
        }
        return list
    }

    private fun saveVolumeMetadata(volume: com.box.android.data.box.BoxVolume) {
        val current = loadPersistedVolumes().filterNot { it.id == volume.id } + volume
        writeVolumesJson(current)
    }

    private fun removeVolumeMetadata(volumeName: String) {
        val current = loadPersistedVolumes().filterNot { it.name == volumeName }
        writeVolumesJson(current)
    }

    private fun writeVolumesJson(volumes: List<com.box.android.data.box.BoxVolume>) {
        try {
            val array = org.json.JSONArray()
            volumes.forEach { vol ->
                val obj = org.json.JSONObject().apply {
                    put("id", vol.id)
                    put("name", vol.name)
                    put("capacityDisplay", vol.capacityDisplay)
                    vol.sizeMb?.let { put("sizeMb", it) }
                    put("mountPath", vol.mountPath)
                    put("createdAt", vol.createdAt)
                }
                array.put(obj)
            }
            volumesMetadataFile.writeText(array.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing volumes metadata", e)
        }
    }



    suspend fun pullAndExtractPackage(
        app: BoxApp,
        token: String? = null,
        onProgress: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (!app.packageId.contains("/")) return@withContext false
        try {
            val trimmed = app.packageId.trim()
            val author = trimmed.substringBefore("/")
            val rest = trimmed.substringAfter("/")
            val name = rest.substringBefore(":")
            val tag = if (rest.contains(":")) rest.substringAfter(":") else "latest"

            onProgress("[HUB] Demande de téléchargement pour $author/$name:$tag...")
            val url = java.net.URL("https://boxhub.paxiz.org/api/v1/registry/pull/$author/$name/$tag")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "BoxMobileRunner/1.0")
            if (!token.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }

            val status = conn.responseCode
            if (status !in 200..299) {
                val errBody = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                } catch (_: Throwable) { null }
                val serverMsg = try {
                    if (!errBody.isNullOrBlank()) {
                        val errJson = org.json.JSONObject(errBody)
                        val m = errJson.optString("error", errJson.optString("message", ""))
                        if (m.isNotBlank()) m else errBody
                    } else null
                } catch (_: Throwable) { errBody }

                val errorReason = when (status) {
                    401 -> serverMsg?.takeIf { it.isNotBlank() } ?: "Jeton d'accès requis ou invalide"
                    403 -> serverMsg?.takeIf { it.isNotBlank() } ?: "Accès interdit au paquet privé (jeton d'accès invalide ou manquant)"
                    404 -> serverMsg?.takeIf { it.isNotBlank() } ?: "Paquet introuvable sur Box Hub"
                    else -> serverMsg?.takeIf { it.isNotBlank() } ?: "Erreur HTTP $status sur Box Hub"
                }
                onProgress("[HUB] Réponse registre Box Hub : $errorReason")
                throw IllegalStateException(errorReason)
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(body)
            val downloadUrl = json.optString("downloadUrl", "")
            if (downloadUrl.isBlank()) {
                onProgress("[HUB] Aucun flux de téléchargement disponible.")
                throw IllegalStateException("Aucun flux de téléchargement disponible sur Box Hub.")
            }

            onProgress("[HUB] Téléchargement du paquet (.box)...")
            val sandboxDir = getSandboxDir(app.id)
            if (!sandboxDir.exists()) sandboxDir.mkdirs()
            val tempBoxFile = File(sandboxDir, "package.box")

            try {
                val dlUrl = java.net.URL(downloadUrl)
                val dlConn = dlUrl.openConnection() as java.net.HttpURLConnection
                dlConn.connectTimeout = 15000
                dlConn.readTimeout = 30000
                dlConn.setRequestProperty("User-Agent", "BoxMobileRunner/1.0")
                // IMPORTANT: Presigned Cloudflare R2 / S3 storage URLs already include HMAC credentials
                // in their query string. Passing an "Authorization: Bearer" header causes R2 to reject the request
                // with HTTP 400 Bad Request ("Only one auth mechanism allowed") or 403 Forbidden.

                val dlStatus = dlConn.responseCode
                if (dlStatus !in 200..299) {
                    val dlErr = try {
                        dlConn.errorStream?.bufferedReader()?.use { it.readText() }
                    } catch (_: Throwable) { null }
                    throw IllegalStateException("Échec du téléchargement du paquet (.box) : HTTP $dlStatus ${dlErr ?: ""}".trim())
                }

                dlConn.inputStream.use { input ->
                    tempBoxFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                onProgress("[SANDBOX] Décompression de l'archive (.box / zstd / tar)...")
                extractBoxArchive(tempBoxFile, sandboxDir)
                inspectAndSyncSandboxManifest(app, sandboxDir)
                onProgress("[SANDBOX] Paquet extrait et initialisé avec succès.")
                true
            } finally {
                try {
                    val path = tempBoxFile.toPath()
                    if (java.nio.file.Files.isSymbolicLink(path)) {
                        java.nio.file.Files.deleteIfExists(path)
                    } else if (tempBoxFile.exists()) {
                        tempBoxFile.delete()
                    }
                } catch (_: Throwable) {
                    tempBoxFile.delete()
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Package extraction error", e)
            onProgress("[HUB] Note de téléchargement : ${e.message}")
            throw e
        }
    }

    fun inspectAndSyncSandboxManifest(app: BoxApp, sandboxDir: File): BoxApp {
        var updatedRuntime = app.runtime
        var updatedPort = app.port
        var updatedWorkdir: String? = app.workdir
        var updatedEntrypointArgs: List<String> = app.entrypointArgs
        var envSchemaFile: String? = null
        val discoveredReqKeys = app.requiredEnvKeys.toMutableList()
        val discoveredEnvVars = app.envVars.toMutableMap()

        // 1. Read boxfile.yml / boxfile.yaml / boxconfig.yml
        for (yamlName in listOf("boxfile.yml", "boxfile.yaml", "boxconfig.yml", "boxconfig.yaml")) {
            val yamlFile = File(sandboxDir, yamlName)
            if (yamlFile.exists()) {
                try {
                    val lines = yamlFile.readLines(Charsets.UTF_8)
                    var inServer = false
                    var hasExplicitServer = false
                    var inEnvSchema = false
                    var inRuntime = false
                    for (line in lines) {
                        val trimmed = line.trim()
                        val indent = line.length - line.trimStart().length

                        // Top-level runtime: can be inline or block
                        if (trimmed.startsWith("runtime:")) {
                            val inlineVal = trimmed.substringAfter("runtime:").trim().removeSurrounding("\"").removeSurrounding("'").lowercase()
                            if (inlineVal.isNotBlank()) {
                                if (inlineVal.contains("node") || inlineVal.contains("js")) updatedRuntime = BoxRuntime.NODEJS
                                else if (inlineVal.contains("python") || inlineVal.contains("py")) updatedRuntime = BoxRuntime.PYTHON
                                inRuntime = false
                            } else {
                                inRuntime = true
                            }
                            continue
                        }
                        if (inRuntime && indent > 0) {
                            if (trimmed.startsWith("type:")) {
                                val rt = trimmed.substringAfter("type:").trim().removeSurrounding("\"").removeSurrounding("'").lowercase()
                                if (rt.contains("node") || rt.contains("js")) updatedRuntime = BoxRuntime.NODEJS
                                else if (rt.contains("python") || rt.contains("py")) updatedRuntime = BoxRuntime.PYTHON
                            }
                            // version: is read but not used for now (Termux provides one version)
                            continue
                        } else if (inRuntime && indent == 0 && trimmed.isNotBlank()) {
                            inRuntime = false
                        }

                        // entrypoint: — extract file AND arguments
                        if (trimmed.startsWith("entrypoint:")) {
                            val rawVal = trimmed.substringAfter("entrypoint:").trim().removeSurrounding("\"").removeSurrounding("'")
                            if (rawVal.isNotBlank()) {
                                var clean = rawVal
                                    .removePrefix("python3 ").removePrefix("python ")
                                    .removePrefix("node ").removePrefix("./").trim()
                                val parts = clean.split(" ").filter { it.isNotBlank() }
                                if (parts.size > 1) {
                                    updatedEntrypointArgs = parts.drop(1)
                                }
                            }
                        }

                        // workdir:
                        if (trimmed.startsWith("workdir:")) {
                            val wd = trimmed.substringAfter("workdir:").trim().removeSurrounding("\"").removeSurrounding("'")
                            if (wd.isNotBlank() && wd != "." && wd != "/") {
                                updatedWorkdir = wd.trimStart('/', '\\')
                            }
                        }

                        // env_schema: (path to env.example file inside the package)
                        if (trimmed.startsWith("env_schema:")) {
                            envSchemaFile = trimmed.substringAfter("env_schema:").trim().removeSurrounding("\"").removeSurrounding("'")
                        }

                        // server: block
                        if (trimmed.startsWith("server:")) {
                            val inlinePort = trimmed.substringAfter("server:").trim().filter { it.isDigit() }.toIntOrNull()
                            if (inlinePort != null) {
                                updatedPort = inlinePort
                                hasExplicitServer = true
                            } else {
                                inServer = true
                                hasExplicitServer = true
                            }
                            continue
                        }
                        if (inServer) {
                            if (trimmed.startsWith("port:")) {
                                val p = trimmed.substringAfter("port:").trim().filter { it.isDigit() }.toIntOrNull()
                                updatedPort = p
                            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#") && !trimmed.startsWith("host:") && !trimmed.startsWith("-")) {
                                inServer = false
                            }
                        }

                        // env_schema / env: list block
                        if (trimmed.startsWith("env_schema:") || trimmed.startsWith("env:")) {
                            inEnvSchema = true
                            continue
                        }
                        if (inEnvSchema) {
                            if (trimmed.startsWith("-")) {
                                val key = trimmed.removePrefix("-").trim().substringBefore("=").substringBefore(":").removeSurrounding("\"").removeSurrounding("'")
                                if (key.isNotBlank() && key != "raw" && !discoveredReqKeys.contains(key)) {
                                    discoveredReqKeys.add(key)
                                }
                            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                                inEnvSchema = false
                            }
                        }
                    }
                    if (!hasExplicitServer && (app.name.contains("bot", ignoreCase = true) || app.packageId.contains("bot", ignoreCase = true))) {
                        updatedPort = null
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Read env schema file (env.example) for required keys and defaults
        val schemaFileNames = mutableListOf<String>()
        if (!envSchemaFile.isNullOrBlank()) schemaFileNames.add(envSchemaFile)
        schemaFileNames.addAll(listOf("env.example", ".env.example", "example.env"))
        for (exampleName in schemaFileNames) {
            val exFile = File(sandboxDir, exampleName)
            if (exFile.exists()) {
                try {
                    exFile.readLines(Charsets.UTF_8).forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                            val k = trimmed.substringBefore("=").trim()
                            val v = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                            if (k.isNotBlank() && k != "raw" && !discoveredReqKeys.contains(k)) {
                                discoveredReqKeys.add(k)
                            }
                            // Apply default values from env.example (like CLI does)
                            if (k.isNotBlank() && k != "raw" && v.isNotBlank() && !discoveredEnvVars.containsKey(k)) {
                                discoveredEnvVars[k] = v
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val updatedApp = app.copy(
            runtime = updatedRuntime,
            port = updatedPort,
            workdir = updatedWorkdir,
            entrypointArgs = updatedEntrypointArgs,
            requiredEnvKeys = discoveredReqKeys.distinct(),
            envVars = discoveredEnvVars
        )
        saveAppMetadata(updatedApp)
        return updatedApp
    }

    /**
     * Validates environment variables against the env schema (env.example).
     * Returns a list of missing required keys (keys with empty value in env.example).
     * Like the CLI's validate_env: KEY= or KEY means required; KEY=default means optional.
     */
    fun validateEnvSchema(sandboxDir: File, envVars: Map<String, String>): List<String> {
        val missing = mutableListOf<String>()
        val schemaFiles = listOf("env.example", ".env.example", "example.env")
        for (schemaName in schemaFiles) {
            val schemaFile = File(sandboxDir, schemaName)
            if (schemaFile.exists()) {
                try {
                    schemaFile.readLines(Charsets.UTF_8).forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                            if (trimmed.contains("=")) {
                                val k = trimmed.substringBefore("=").trim()
                                val defaultVal = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                                // Required: key has no default value
                                if (k.isNotBlank() && k != "raw" && defaultVal.isBlank()) {
                                    if (envVars[k].isNullOrBlank()) {
                                        missing.add(k)
                                    }
                                }
                            } else {
                                // Bare KEY with no = means strictly required
                                val k = trimmed.trim()
                                if (k.isNotBlank() && k != "raw") {
                                    if (envVars[k].isNullOrBlank()) {
                                        missing.add(k)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                break // Use first found schema file
            }
        }
        return missing
    }

    fun isPackageExtracted(appId: String, runtime: BoxRuntime): Boolean {
        val sandboxDir = getSandboxDir(appId)
        if (!sandboxDir.exists() || !sandboxDir.isDirectory) return false
        val files = sandboxDir.listFiles() ?: return false
        if (files.isEmpty() || files.all { it.name.startsWith(".env") }) return false
        return File(sandboxDir, "boxfile.yml").exists() ||
                File(sandboxDir, "boxfile.yaml").exists() ||
                File(sandboxDir, "boxconfig.yml").exists() ||
                File(sandboxDir, "boxconfig.yaml").exists() ||
                File(sandboxDir, "package.json").exists() ||
                resolveEntrypoint(appId, runtime) != null
    }

    suspend fun provisionSandbox(
        app: BoxApp,
        isLanAccessEnabled: Boolean = false,
        token: String? = null,
        onProgress: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val sandboxDir = getSandboxDir(app.id)
        val hostAddress = if (isLanAccessEnabled) "0.0.0.0" else "127.0.0.1"

        // 1. Pull real package archive if sandbox is not yet extracted
        val isExtracted = isPackageExtracted(app.id, app.runtime)

        if (!isExtracted && app.packageId.contains("/")) {
            val effectiveToken = token
                ?: app.accessToken?.takeIf { it.isNotBlank() }
                ?: app.envVars["BOX_ACCESS_TOKEN"]?.takeIf { it.isNotBlank() }
                ?: app.envVars["BOX_TOKEN"]?.takeIf { it.isNotBlank() }
            pullAndExtractPackage(app, effectiveToken, onProgress)
        }

        // 2. Mount declared and persisted volumes into sandbox
        val mountedVolumes = mountAppVolumes(app, sandboxDir)

        // 3. Remap volume environment variables (e.g. /data -> <sandboxDir>/data)
        val remappedEnvVars = app.envVars.toMutableMap()
        for (vol in mountedVolumes) {
            val ts = vol.targetSpec
            val tsNoSlash = ts.trimStart('/')
            val realPath = vol.sandboxTarget.absolutePath
            for ((key, value) in app.envVars) {
                if (value == ts || value == tsNoSlash || value == "$ts/") {
                    remappedEnvVars[key] = realPath
                } else if (value.startsWith("$ts/")) {
                    remappedEnvVars[key] = value.replace(ts, realPath)
                }
            }
            if (ts == "/data" || tsNoSlash == "data") {
                if (!remappedEnvVars.containsKey("DATA_DIR") || remappedEnvVars["DATA_DIR"] == "/data" || remappedEnvVars["DATA_DIR"] == "data") {
                    remappedEnvVars["DATA_DIR"] = realPath
                }
            }
            val volKey = vol.volumeName.uppercase().replace("-", "_").replace(" ", "_")
            remappedEnvVars["VOLUME_$volKey"] = realPath
            remappedEnvVars["BOX_VOLUME_$volKey"] = realPath
        }

        // 4. Write .env.local and .env files
        val envLines = StringBuilder()
        remappedEnvVars.forEach { (k, v) ->
            envLines.append("$k=$v\n")
        }
        if (app.port != null) {
            envLines.append("PORT=${app.port}\n")
            envLines.append("HOST=$hostAddress\n")
        }
        val content = envLines.toString()
        File(sandboxDir, ".env.local").writeText(content, Charsets.UTF_8)
        File(sandboxDir, ".env").writeText(content, Charsets.UTF_8)
        if (!app.workdir.isNullOrBlank()) {
            val workDir = File(sandboxDir, app.workdir.trim('/', '\\'))
            if (!workDir.exists()) workDir.mkdirs()
            File(workDir, ".env.local").writeText(content, Charsets.UTF_8)
            File(workDir, ".env").writeText(content, Charsets.UTF_8)
        }

        // 4. Ensure _box_node_loader.js detects and reports V8 bytecode incompatibility
        val loaderFile = File(sandboxDir, "_box_node_loader.js")
        if (loaderFile.exists()) {
            try {
                val loaderContent = loaderFile.readText(Charsets.UTF_8)
                if (!loaderContent.contains("cachedDataRejected")) {
                    val patched = loaderContent.replace(
                        "const fn = script.runInThisContext();",
                        "if (script.cachedDataRejected) {\n" +
                                "    console.error('[!] Erreur Bytecode V8 : ce paquet a ete compile avec protect: true sous une version Node.js/V8 incompatible (' + process.version + ').');\n" +
                                "    console.error('[*] Solution : reconstruisez la box sans \"protect: true\" dans boxconfig.yml pour executer le code source JS universellement.');\n" +
                                "    process.exit(1);\n" +
                                "  }\n" +
                                "  const fn = script.runInThisContext();"
                    )
                    loaderFile.writeText(patched, Charsets.UTF_8)
                }
            } catch (_: Exception) {}
        }

        // 5. Adapt Next.js TypeScript config for cross-platform Android execution
        prepareNextJsConfig(sandboxDir)
        if (!app.workdir.isNullOrBlank()) {
            val workDir = File(sandboxDir, app.workdir.trim('/', '\\'))
            if (workDir.exists() && workDir != sandboxDir) {
                prepareNextJsConfig(workDir)
            }
        }

        sandboxDir
    }

    /**
     * Next.js on Android ARM64 does not have prebuilt native SWC binaries (@next/swc-android-arm64).
     * If next.config.ts exists, Next.js requires SWC solely to transpile that single config file.
     * By converting next.config.ts to next.config.mjs and backing up .ts, Next.js loads the config
     * natively via Node ESM in ~30ms without needing SWC or failing at startup.
     */
    fun prepareNextJsConfig(dir: File) {
        val tsConfig = File(dir, "next.config.ts")
        val mjsConfig = File(dir, "next.config.mjs")
        val jsConfig = File(dir, "next.config.js")

        // If next.config.mjs or next.config.js already exists, Next.js can load it directly
        if (!tsConfig.exists() || mjsConfig.exists() || jsConfig.exists()) {
            return
        }

        try {
            val rawTs = tsConfig.readText(Charsets.UTF_8)
            // 1. Comment out type imports and interface/type declarations
            var converted = rawTs.lines().map { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("import type ") || trimmed.startsWith("import type{")) {
                    "// $line"
                } else if (trimmed.startsWith("export type ") || trimmed.startsWith("export interface ") ||
                    trimmed.startsWith("interface ") || (trimmed.startsWith("type ") && trimmed.contains("="))) {
                    "// $line"
                } else {
                    line
                }
            }.joinToString("\n")

            // 2. Remove NextConfig type annotations and assertions
            converted = Regex(":\\s*NextConfig\\b").replace(converted, "")
            converted = Regex("satisfies\\s+NextConfig\\b").replace(converted, "")
            converted = Regex("as\\s+NextConfig\\b").replace(converted, "")

            // 3. Write next.config.mjs and backup next.config.ts
            mjsConfig.writeText(converted, Charsets.UTF_8)
            val backupFile = File(dir, "next.config.ts.bak")
            if (backupFile.exists()) backupFile.delete()
            tsConfig.renameTo(backupFile)
            Log.i(TAG, "Adapted next.config.ts -> next.config.mjs for Android runtime compatibility")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to adapt next.config.ts: ${e.message}")
        }
    }

    data class MountedVolumeInfo(
        val volumeName: String,
        val targetSpec: String,
        val physicalDir: File,
        val sandboxTarget: File
    )

    fun mountVolumeToSandbox(sandboxDir: File, volumeSpec: String, workdirRel: String = ""): MountedVolumeInfo? {
        val parts = volumeSpec.split(":")
        val sourceStr = parts[0].trim()
        val targetStr = if (parts.size >= 2) parts[1].trim() else parts[0].trim()

        if (sourceStr.isBlank() || targetStr.isBlank()) return null

        val physicalVolume = getVolumeDir(sourceStr)
        if (!physicalVolume.exists()) {
            physicalVolume.mkdirs()
        }

        // Register in persisted volumes metadata if not already registered (so it shows in UI)
        try {
            val persisted = loadPersistedVolumes()
            if (!persisted.any { it.name == sourceStr }) {
                val newVol = com.box.android.data.box.BoxVolume(
                    id = "vol_${System.currentTimeMillis()}_${sourceStr.hashCode()}",
                    name = sourceStr,
                    capacityDisplay = "Auto",
                    sizeMb = null,
                    mountPath = if (targetStr.startsWith("/")) targetStr else "/$targetStr",
                    usedMb = calculateVolumeSizeMb(sourceStr),
                    createdAt = "Auto (Box Manifest)"
                )
                saveVolumeMetadata(newVol)
            }
        } catch (_: Exception) {}

        val cleanTarget = targetStr.trimStart('/', '\\')
        val workdirClean = workdirRel.trim('/', '\\')
        val sandboxTarget = if (workdirClean.isNotBlank() && workdirClean != "." && workdirClean != "/" &&
            !cleanTarget.startsWith("$workdirClean/") && cleanTarget != workdirClean) {
            File(File(sandboxDir, workdirClean), cleanTarget)
        } else {
            File(sandboxDir, cleanTarget)
        }

        sandboxTarget.parentFile?.mkdirs()

        val targetPath = sandboxTarget.toPath()
        if (sandboxTarget.exists() || java.nio.file.Files.isSymbolicLink(targetPath)) {
            // Seed initial data to physical volume if physical volume is empty
            if (sandboxTarget.isDirectory && !java.nio.file.Files.isSymbolicLink(targetPath) && physicalVolume.listFiles().isNullOrEmpty()) {
                sandboxTarget.listFiles()?.forEach { file ->
                    try {
                        val dest = File(physicalVolume, file.name)
                        if (file.isDirectory) file.copyRecursively(dest, overwrite = true)
                        else file.copyTo(dest, overwrite = true)
                    } catch (_: Throwable) {}
                }
            }
            try {
                deleteRecursivelySafe(sandboxTarget)
            } catch (_: Throwable) {}
        }

        try {
            android.system.Os.symlink(physicalVolume.absolutePath, sandboxTarget.absolutePath)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to symlink volume $sourceStr -> $sandboxTarget: ${e.message}")
        }

        // Also if sandboxTarget is inside workdir (e.g. sandboxDir/app/data), create a link at sandboxDir/data if needed
        val rootSandboxTarget = File(sandboxDir, cleanTarget)
        if (rootSandboxTarget.absolutePath != sandboxTarget.absolutePath) {
            try {
                rootSandboxTarget.parentFile?.mkdirs()
                deleteRecursivelySafe(rootSandboxTarget)
                android.system.Os.symlink(physicalVolume.absolutePath, rootSandboxTarget.absolutePath)
            } catch (_: Throwable) {}
        }

        return MountedVolumeInfo(
            volumeName = sourceStr,
            targetSpec = if (targetStr.startsWith("/")) targetStr else "/$targetStr",
            physicalDir = physicalVolume,
            sandboxTarget = sandboxTarget
        )
    }

    fun mountAppVolumes(app: BoxApp, sandboxDir: File): List<MountedVolumeInfo> {
        val volumeSpecs = mutableListOf<String>()

        // 1. Check all candidate directories for manifest files (root, app/, workdir)
        val candidateDirs = listOfNotNull(
            sandboxDir,
            File(sandboxDir, "app"),
            if (!app.workdir.isNullOrBlank()) File(sandboxDir, app.workdir.trim('/', '\\')) else null
        ).distinct()

        for (dir in candidateDirs) {
            if (!dir.exists()) continue

            // A. Read volume specs from boxfile.yml / boxconfig.yml
            for (yamlName in listOf("boxfile.yml", "boxfile.yaml", "boxconfig.yml", "boxconfig.yaml")) {
                val yamlFile = File(dir, yamlName)
                if (yamlFile.exists()) {
                    try {
                        var inVolumes = false
                        for (line in yamlFile.readLines(Charsets.UTF_8)) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("volumes:")) {
                                inVolumes = true
                                continue
                            }
                            if (inVolumes) {
                                if (trimmed.startsWith("-")) {
                                    val spec = trimmed.removePrefix("-").trim().removeSurrounding("\"").removeSurrounding("'")
                                    if (spec.isNotBlank()) volumeSpecs.add(spec)
                                } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                                    inVolumes = false
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // B. Read volume specs from box.json
            val boxJsonFile = File(dir, "box.json")
            if (boxJsonFile.exists()) {
                try {
                    val json = org.json.JSONObject(boxJsonFile.readText(Charsets.UTF_8))
                    val volArr = json.optJSONArray("volumes")
                    if (volArr != null) {
                        for (i in 0 until volArr.length()) {
                            val spec = volArr.getString(i).trim()
                            if (spec.isNotBlank()) volumeSpecs.add(spec)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Fallback: match any existing global volume matching app name or data convention
        if (volumeSpecs.isEmpty()) {
            val persisted = loadPersistedVolumes()
            val cleanName = app.name.lowercase().replace(" ", "_").replace("-", "_")
            val cleanId = app.id.lowercase()
            val matchedVol = persisted.firstOrNull { vol ->
                val vName = vol.name.lowercase().replace(" ", "_").replace("-", "_")
                vName == "${cleanName}_data" ||
                vName == "${cleanId}_data" ||
                vName == cleanName ||
                vName == "data" ||
                vol.name == app.name
            }
            if (matchedVol != null) {
                volumeSpecs.add("${matchedVol.name}:${matchedVol.mountPath}")
            }
        }

        return volumeSpecs.distinct().mapNotNull { spec ->
            mountVolumeToSandbox(sandboxDir, spec, app.workdir ?: "")
        }
    }

    fun deleteRecursivelySafe(fileOrDir: File): Boolean {
        return try {
            val path = fileOrDir.toPath()
            // 1. If it is a symlink, DO NOT traverse it (avoid following into host/volume target dirs).
            // Just delete/unlink the symlink itself!
            if (java.nio.file.Files.isSymbolicLink(path)) {
                return try {
                    java.nio.file.Files.deleteIfExists(path)
                } catch (_: Throwable) {
                    fileOrDir.delete()
                }
            }

            if (!fileOrDir.exists()) {
                return true
            }

            // 2. If it's a directory, recursively delete its children first
            if (fileOrDir.isDirectory) {
                fileOrDir.listFiles()?.forEach { child ->
                    deleteRecursivelySafe(child)
                }
            }

            // 3. Delete this file or empty directory
            try {
                fileOrDir.setWritable(true)
            } catch (_: Throwable) {}

            try {
                java.nio.file.Files.deleteIfExists(path)
            } catch (_: Throwable) {
                fileOrDir.delete()
            }
        } catch (_: Throwable) {
            try {
                fileOrDir.delete()
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun detachSandboxLinks(sandboxDir: File) {
        try {
            val rootPath = sandboxDir.toPath()
            if (java.nio.file.Files.isSymbolicLink(rootPath)) {
                try {
                    java.nio.file.Files.deleteIfExists(rootPath)
                } catch (_: Throwable) {
                    sandboxDir.delete()
                }
                return
            }
            if (!sandboxDir.exists() || !sandboxDir.isDirectory) return

            sandboxDir.listFiles()?.forEach { child ->
                try {
                    val childPath = child.toPath()
                    if (java.nio.file.Files.isSymbolicLink(childPath)) {
                        try {
                            java.nio.file.Files.deleteIfExists(childPath)
                        } catch (_: Throwable) {
                            child.delete()
                        }
                    } else if (child.isDirectory) {
                        detachSandboxLinks(child)
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    suspend fun cleanSandbox(appId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sandboxDir = File(sandboxesDir, appId)
            val path = sandboxDir.toPath()
            if (sandboxDir.exists() || java.nio.file.Files.isSymbolicLink(path)) {
                detachSandboxLinks(sandboxDir)
                deleteRecursivelySafe(sandboxDir)
            } else {
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error cleaning sandbox for app $appId", t)
            false
        }
    }

    suspend fun clearBuildCache(): Long = withContext(Dispatchers.IO) {
        var bytesFreed = 0L

        fun purgeFolder(dir: File) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    try {
                        val path = file.toPath()
                        if (java.nio.file.Files.isSymbolicLink(path)) {
                            try {
                                java.nio.file.Files.deleteIfExists(path)
                            } catch (_: Throwable) {
                                file.delete()
                            }
                        } else if (file.isDirectory) {
                            val size = calculateFolderSizeBytes(file)
                            deleteRecursivelySafe(file)
                            bytesFreed += size
                        } else {
                            val size = file.length()
                            file.delete()
                            bytesFreed += size
                        }
                    } catch (_: Throwable) {}
                }
            }
        }

        // 1. Android standard cacheDir & codeCacheDir
        purgeFolder(context.cacheDir)
        purgeFolder(context.codeCacheDir)

        // 2. Custom build and package cache directories
        val buildCacheDir = File(context.filesDir, "build_cache")
        purgeFolder(buildCacheDir)

        val pipCacheDir = File(context.filesDir, ".cache")
        purgeFolder(pipCacheDir)

        // 3. Purge framework build caches inside sandboxes (.next/cache, node_modules/.cache, __pycache__)
        if (sandboxesDir.exists() && sandboxesDir.isDirectory) {
            sandboxesDir.listFiles()?.forEach { sbox ->
                if (sbox.isDirectory) {
                    try {
                        val stack = ArrayDeque<File>()
                        stack.add(sbox)
                        while (stack.isNotEmpty()) {
                            val current = stack.removeLast()
                            try {
                                val currentPath = current.toPath()
                                if (java.nio.file.Files.isSymbolicLink(currentPath)) continue

                                if (current.isDirectory) {
                                    val name = current.name
                                    val parentName = current.parentFile?.name
                                    if ((name == "cache" && (parentName == ".next" || parentName == "node_modules")) ||
                                        name == "__pycache__"
                                    ) {
                                        purgeFolder(current)
                                    } else {
                                        current.listFiles()?.forEach { stack.add(it) }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    } catch (_: Throwable) {}
                }
            }
        }

        bytesFreed
    }

    fun resolveEntrypoint(appId: String, runtime: BoxRuntime): File? {
        val sandboxDir = getSandboxDir(appId)
        val workdirRel = loadPersistedApps().firstOrNull { it.id == appId }?.workdir

        fun resolveCandidatePath(raw: String): File? {
            if (raw.isBlank()) return null
            var clean = raw.trim()
                .removePrefix("node ")
                .removePrefix("python3 ")
                .removePrefix("python ")
                .removePrefix("./")
                .trim()
            if (clean.contains(" ")) {
                clean = clean.substringBefore(" ")
            }
            if (clean.isNotBlank()) {
                val f = File(sandboxDir, clean)
                if (f.exists() && f.isFile) return f
                if (!workdirRel.isNullOrBlank()) {
                    val wf = File(File(sandboxDir, workdirRel.trim('/', '\\')), clean)
                    if (wf.exists() && wf.isFile) return wf
                }
                // Bytecode fallback: .py -> .pyc, .js -> .jsc (for protected packages)
                if (clean.endsWith(".py")) {
                    val pyc = File(sandboxDir, clean.removeSuffix(".py") + ".pyc")
                    if (pyc.exists() && pyc.isFile) return pyc
                }
                if (clean.endsWith(".js")) {
                    val jsc = File(sandboxDir, clean.removeSuffix(".js") + ".jsc")
                    if (jsc.exists() && jsc.isFile) return jsc
                }
            }
            return null
        }

        // 1. Check boxfile.yml, boxfile.yaml, boxconfig.yml, or box.json
        for (yamlName in listOf("boxfile.yml", "boxfile.yaml", "boxconfig.yml", "boxconfig.yaml")) {
            val yamlFile = File(sandboxDir, yamlName)
            if (yamlFile.exists()) {
                try {
                    for (line in yamlFile.readLines(Charsets.UTF_8)) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("entrypoint:")) {
                            val rawVal = trimmed.substringAfter("entrypoint:").trim().removeSurrounding("\"").removeSurrounding("'")
                            resolveCandidatePath(rawVal)?.let { return it }
                        }
                        if (trimmed.startsWith("main:")) {
                            val rawVal = trimmed.substringAfter("main:").trim().removeSurrounding("\"").removeSurrounding("'")
                            resolveCandidatePath(rawVal)?.let { return it }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val boxJsonFile = File(sandboxDir, "box.json")
        if (boxJsonFile.exists()) {
            try {
                val json = org.json.JSONObject(boxJsonFile.readText(Charsets.UTF_8))
                resolveCandidatePath(json.optString("entrypoint", ""))?.let { return it }
                resolveCandidatePath(json.optString("main", ""))?.let { return it }
                resolveCandidatePath(json.optString("start", ""))?.let { return it }
                resolveCandidatePath(json.optString("script", ""))?.let { return it }
                val scripts = json.optJSONObject("scripts")
                if (scripts != null) {
                    resolveCandidatePath(scripts.optString("start", ""))?.let { return it }
                    resolveCandidatePath(scripts.optString("main", ""))?.let { return it }
                }
            } catch (_: Exception) {}
        }

        // 2. Check package.json if present
        val pkgJsonFile = File(sandboxDir, "package.json")
        if (pkgJsonFile.exists()) {
            try {
                val json = org.json.JSONObject(pkgJsonFile.readText(Charsets.UTF_8))
                resolveCandidatePath(json.optString("main", ""))?.let { return it }
                val scripts = json.optJSONObject("scripts")
                if (scripts != null) {
                    resolveCandidatePath(scripts.optString("start", ""))?.let { return it }
                    resolveCandidatePath(scripts.optString("bot", ""))?.let { return it }
                }
            } catch (_: Exception) {}
        }

        // 3. Comprehensive standard filenames (includes bytecode variants for protected packages)
        val candidates = when (runtime) {
            BoxRuntime.NODEJS -> listOf(
                "bot.js", "index.js", "main.js", "app.js", "server.js",
                "src/bot.js", "src/index.js", "src/main.js", "src/app.js", "src/server.js",
                "dist/bot.js", "dist/index.js", "dist/main.js", "dist/app.js", "dist/server.js",
                "bot.mjs", "index.mjs", "main.mjs", "app.mjs", "server.mjs"
            )
            BoxRuntime.PYTHON -> listOf(
                "bot.py", "main.py", "app.py", "server.py", "run.py",
                "bot.pyc", "main.pyc", "app.pyc", "server.pyc", "run.pyc",
                "src/bot.py", "src/main.py", "src/app.py", "src/server.py", "src/run.py",
                "src/bot.pyc", "src/main.pyc", "src/app.pyc", "src/server.pyc", "src/run.pyc",
                "api.py", "api.pyc"
            )
            else -> listOf("main", "app", "bot")
        }

        val isCandidateValid = { f: File ->
            f.exists() && f.isFile
        }

        for (c in candidates) {
            val f = File(sandboxDir, c)
            if (isCandidateValid(f)) return f
        }

        // 4. Any top-level js/py/pyc file that is not a placeholder
        val fallbackFile = sandboxDir.listFiles()?.firstOrNull { file ->
            isCandidateValid(file) && when (runtime) {
                BoxRuntime.NODEJS -> file.name.endsWith(".js") || file.name.endsWith(".mjs")
                BoxRuntime.PYTHON -> file.name.endsWith(".py") || file.name.endsWith(".pyc")
                else -> false
            }
        }
        if (fallbackFile != null) return fallbackFile

        // 5. Ultimate fallback if nothing else exists
        val anyExisting = candidates.map { File(sandboxDir, it) }.firstOrNull { it.exists() && it.isFile }
        return anyExisting
    }

    companion object {
        private const val TAG = "SandboxManager"

        fun extractBoxArchive(archiveFile: File, outputDir: File) {
            val bis = java.io.BufferedInputStream(archiveFile.inputStream())
            bis.mark(16)
            val header = ByteArray(8)
            val bytesRead = bis.read(header, 0, 8)
            bis.reset()

            val decompressedStream: java.io.InputStream = when {
                // Zstandard magic: 0x28 0xB5 0x2F 0xFD
                bytesRead >= 4 &&
                    header[0] == 0x28.toByte() &&
                    header[1] == 0xB5.toByte() &&
                    header[2] == 0x2F.toByte() &&
                    header[3] == 0xFD.toByte() -> {
                    com.github.luben.zstd.ZstdInputStream(bis)
                }
                // Gzip magic: 0x1F 0x8B
                bytesRead >= 2 &&
                    header[0] == 0x1F.toByte() &&
                    header[1] == 0x8B.toByte() -> {
                    java.util.zip.GZIPInputStream(bis)
                }
                // XZ magic: 0xFD '7' 'z' 'X' 'Z' 0x00
                bytesRead >= 6 &&
                    header[0] == 0xFD.toByte() &&
                    header[1] == '7'.code.toByte() &&
                    header[2] == 'z'.code.toByte() &&
                    header[3] == 'X'.code.toByte() &&
                    header[4] == 'Z'.code.toByte() &&
                    header[5] == 0x00.toByte() -> {
                    org.tukaani.xz.XZInputStream(bis)
                }
                else -> bis
            }

            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(decompressedStream).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val cleanName = entry.name.removePrefix("./").removePrefix("/")
                    if (cleanName.isNotBlank() && !cleanName.startsWith("__MACOSX")) {
                        val destFile = File(outputDir, cleanName)
                        if (entry.isDirectory) {
                            destFile.mkdirs()
                        } else if (entry.isSymbolicLink) {
                            destFile.parentFile?.mkdirs()
                            try {
                                val destPath = destFile.toPath()
                                if (java.nio.file.Files.isSymbolicLink(destPath)) {
                                    java.nio.file.Files.deleteIfExists(destPath)
                                } else if (destFile.exists()) {
                                    destFile.delete()
                                }
                            } catch (_: Throwable) {
                                destFile.delete()
                            }
                            try {
                                android.system.Os.symlink(entry.linkName, destFile.absolutePath)
                            } catch (_: Throwable) {}
                        } else {
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().buffered().use { fos ->
                                tarIn.copyTo(fos)
                            }
                        }
                    }
                    entry = tarIn.nextEntry
                }
            }
        }

        fun extractTarGz(tarGzFile: File, outputDir: File) {
            extractBoxArchive(tarGzFile, outputDir)
        }
    }
}
