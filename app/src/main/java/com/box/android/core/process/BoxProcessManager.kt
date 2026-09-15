package com.box.android.core.process

import android.content.Context
import android.util.Log
import com.box.android.R
import com.box.android.core.runtime.RuntimeManager
import com.box.android.core.runtime.RuntimeType
import com.box.android.core.sandbox.SandboxManager
import com.box.android.data.box.BoxApp
import com.box.android.data.box.BoxRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

data class RunningProcess(
    val appId: String,
    val process: Process,
    val startTime: Long,
    val readerJobs: List<Job>,
    var memoryMb: Float = 18.5f
)

class BoxProcessManager(
    private val context: Context,
    private val runtimeManager: RuntimeManager,
    private val sandboxManager: SandboxManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningProcesses = ConcurrentHashMap<String, RunningProcess>()
    private val logMap = ConcurrentHashMap<String, MutableStateFlow<List<AppLogEntry>>>()

    fun getLogsFlow(appId: String): Flow<List<AppLogEntry>> {
        return logMap.getOrPut(appId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    fun clearLogs(appId: String) {
        logMap.remove(appId)
    }

    fun isAppRunning(appId: String): Boolean {
        val running = runningProcesses[appId] ?: return false
        return try {
            running.process.exitValue()
            runningProcesses.remove(appId)
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    }

    fun getRunningAppsCount(): Int {
        return runningProcesses.keys.count { isAppRunning(it) }
    }

    fun getUptime(appId: String): String {
        val running = runningProcesses[appId] ?: return "0m"
        val diffSeconds = (System.currentTimeMillis() - running.startTime) / 1000
        val minutes = diffSeconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${diffSeconds % 60}s"
            else -> "${diffSeconds}s"
        }
    }

    fun getMemoryUsageMb(appId: String): Float {
        val running = runningProcesses[appId] ?: return 0f
        try {
            val procStr = running.process.toString()
            val pidMatch = Regex("pid=(\\d+)").find(procStr) ?: Regex("PID: (\\d+)").find(procStr)
            val pid = pidMatch?.groupValues?.get(1)?.toIntOrNull()
            if (pid != null) {
                val statmFile = File("/proc/$pid/statm")
                if (statmFile.exists()) {
                    val parts = statmFile.readText().trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val rssPages = parts[1].toLongOrNull() ?: 0L
                        val rssBytes = rssPages * 4096L
                        val mb = (rssBytes / (1024f * 1024f))
                        if (mb > 0f) {
                            running.memoryMb = mb
                            return mb
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return running.memoryMb
    }

    private fun appendLog(appId: String, message: String, isError: Boolean = false) {
        val flow = logMap.getOrPut(appId) { MutableStateFlow(emptyList()) }
        flow.update { current ->
            val updated = current + AppLogEntry(message = message, isError = isError)
            if (updated.size > 400) updated.takeLast(400) else updated
        }
    }

    suspend fun startApp(app: BoxApp, isLanAccessEnabled: Boolean = false): Result<Unit> = withContext(NonCancellable + Dispatchers.IO) {
        if (isAppRunning(app.id)) {
            return@withContext Result.success(Unit)
        }

        // Reset terminal logs for fresh execution
        logMap[app.id]?.value = emptyList()

        try {
            val sandboxDir = sandboxManager.provisionSandbox(app, isLanAccessEnabled)

            // Reload app metadata in case pullAndExtractPackage synced manifest with new runtime/workdir/entrypointArgs
            val effectiveApp = sandboxManager.loadPersistedApps().firstOrNull { it.id == app.id } ?: app

            val runtimeType = when (effectiveApp.runtime) {
                BoxRuntime.PYTHON -> RuntimeType.PYTHON
                BoxRuntime.NODEJS -> RuntimeType.NODEJS
                else -> RuntimeType.PYTHON
            }

            val runtimeInfo = runtimeManager.ensureRuntimeInstalled(runtimeType)

            if (!runtimeInfo.isInstalled || !runtimeInfo.executable.exists()) {
                val rtName = runtimeType.identifier
                appendLog(effectiveApp.id, "Runtime '$rtName' not found on device (${runtimeManager.primaryAbi}).", isError = true)
                return@withContext Result.failure(IllegalStateException("Runtime $rtName not found"))
            }

            val entrypointFile = sandboxManager.resolveEntrypoint(effectiveApp.id, effectiveApp.runtime)

            if (entrypointFile == null || !entrypointFile.exists()) {
                val candidateDesc = entrypointFile?.name ?: (if (runtimeType == RuntimeType.PYTHON) "main.py" else "index.js")
                val filesInSandbox = sandboxDir.listFiles()?.map { it.name } ?: emptyList()
                val isOnlyEnv = filesInSandbox.isEmpty() || filesInSandbox.all { it.startsWith(".env") }
                val err = if (isOnlyEnv) {
                    context.getString(R.string.error_sandbox_extract_failed)
                } else {
                    context.getString(R.string.error_entrypoint_not_found, candidateDesc)
                }
                appendLog(effectiveApp.id, err, isError = true)
                return@withContext Result.failure(IllegalStateException(err))
            }

            // Effective execution directory (manifest workdir or sandbox root)
            val execWorkdir = if (!effectiveApp.workdir.isNullOrBlank()) {
                val subDir = File(sandboxDir, effectiveApp.workdir.trim('/', '\\'))
                if (!subDir.exists()) subDir.mkdirs()
                subDir
            } else {
                sandboxDir
            }

            // Ensure permissions on engine and entrypoint
            runtimeInfo.executable.setReadable(true, false)
            runtimeInfo.executable.setExecutable(true, false)
            entrypointFile.setReadable(true, false)
            entrypointFile.setExecutable(true, false)

            var effectiveArgs = effectiveApp.entrypointArgs
            if (effectiveArgs.isEmpty()) {
                for (yamlName in listOf("boxfile.yml", "boxfile.yaml", "boxconfig.yml", "boxconfig.yaml")) {
                    val yamlFile = File(sandboxDir, yamlName)
                    if (yamlFile.exists()) {
                        try {
                            for (line in yamlFile.readLines(Charsets.UTF_8)) {
                                val trimmed = line.trim()
                                if (trimmed.startsWith("entrypoint:")) {
                                    val rawVal = trimmed.substringAfter("entrypoint:").trim().removeSurrounding("\"").removeSurrounding("'")
                                    val clean = rawVal.removePrefix("python3 ").removePrefix("python ").removePrefix("node ").removePrefix("./").trim()
                                    val parts = clean.split(" ").filter { it.isNotBlank() }
                                    if (parts.size > 1) {
                                        effectiveArgs = parts.drop(1)
                                        break
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        if (effectiveArgs.isNotEmpty()) break
                    }
                }
            }

            // Next.js TypeScript config compatibility for Android (converts next.config.ts -> next.config.mjs if SWC is absent)
            sandboxManager.prepareNextJsConfig(execWorkdir)
            if (execWorkdir.absolutePath != sandboxDir.absolutePath) {
                sandboxManager.prepareNextJsConfig(sandboxDir)
            }

            if (runtimeType == RuntimeType.PYTHON) {
                try {
                    val stack = ArrayDeque<File>()
                    stack.add(sandboxDir)
                    while (stack.isNotEmpty()) {
                        val current = stack.removeLast()
                        try {
                            val path = current.toPath()
                            if (java.nio.file.Files.isSymbolicLink(path)) continue
                            if (current.isDirectory) {
                                if (current.name == "__pycache__") {
                                    sandboxManager.deleteRecursivelySafe(current)
                                } else {
                                    current.listFiles()?.forEach { stack.add(it) }
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
            }

            val command = runtimeManager.buildExecutionCommand(runtimeInfo, entrypointFile, effectiveArgs)
            appendLog(app.id, "$ ${command.joinToString(" ")}")

            val pb = ProcessBuilder(command)
            pb.directory(execWorkdir)
            pb.redirectErrorStream(true)

            val env = pb.environment()

            // 1. Mount volumes and remap environment variables for sandbox execution
            val mountedVolumes = sandboxManager.mountAppVolumes(effectiveApp, sandboxDir)
            val effectiveEnvVars = effectiveApp.envVars.toMutableMap()
            for (vol in mountedVolumes) {
                val ts = vol.targetSpec
                val tsNoSlash = ts.trimStart('/')
                val realPath = vol.sandboxTarget.absolutePath
                for ((key, value) in effectiveApp.envVars) {
                    if (value == ts || value == tsNoSlash || value == "$ts/") {
                        effectiveEnvVars[key] = realPath
                    } else if (value.startsWith("$ts/")) {
                        effectiveEnvVars[key] = value.replace(ts, realPath)
                    }
                }
                if (ts == "/data" || tsNoSlash == "data") {
                    if (!effectiveEnvVars.containsKey("DATA_DIR") || effectiveEnvVars["DATA_DIR"] == "/data" || effectiveEnvVars["DATA_DIR"] == "data") {
                        effectiveEnvVars["DATA_DIR"] = realPath
                    }
                }
                val volKey = vol.volumeName.uppercase().replace("-", "_").replace(" ", "_")
                effectiveEnvVars["VOLUME_$volKey"] = realPath
                effectiveEnvVars["BOX_VOLUME_$volKey"] = realPath
            }

            // Injected app environment variables
            effectiveEnvVars.filterKeys { it != "raw" && it.isNotBlank() }.forEach { (k, v) -> env[k] = v }

            // 2. Write both .env and .env.local to sandboxDir and execWorkdir
            val envLines = StringBuilder()
            effectiveEnvVars.filterKeys { it != "raw" && it.isNotBlank() }.forEach { (k, v) ->
                envLines.append("$k=$v\n")
            }
            if (effectiveApp.port != null) {
                val host = if (isLanAccessEnabled) "0.0.0.0" else "127.0.0.1"
                envLines.append("PORT=${effectiveApp.port}\n")
                envLines.append("HOST=$host\n")
            }
            val envContent = envLines.toString()
            try {
                File(sandboxDir, ".env").writeText(envContent, Charsets.UTF_8)
                File(sandboxDir, ".env.local").writeText(envContent, Charsets.UTF_8)
                if (execWorkdir.absolutePath != sandboxDir.absolutePath) {
                    File(execWorkdir, ".env").writeText(envContent, Charsets.UTF_8)
                    File(execWorkdir, ".env.local").writeText(envContent, Charsets.UTF_8)
                }
            } catch (_: Exception) {}

            // 3. Read any other .env / .env.local variables from sandbox or workdir
            listOf(
                File(sandboxDir, ".env"),
                File(sandboxDir, ".env.local"),
                File(execWorkdir, ".env"),
                File(execWorkdir, ".env.local")
            ).filter { it.exists() }.forEach { f ->
                try {
                    f.readLines(Charsets.UTF_8).forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                            val k = trimmed.substringBefore("=").trim()
                            var v = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                            if (k.isNotBlank() && !env.containsKey(k)) {
                                for (vol in mountedVolumes) {
                                    val ts = vol.targetSpec
                                    val tsNoSlash = ts.trimStart('/')
                                    val realPath = vol.sandboxTarget.absolutePath
                                    if (v == ts || v == tsNoSlash || v == "$ts/") {
                                        v = realPath
                                    } else if (v.startsWith("$ts/")) {
                                        v = v.replace(ts, realPath)
                                    }
                                }
                                env[k] = v
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 4. Port & Host ONLY for web/HTTP services (supports standard PORT and UVICORN/FastAPI conventions)
            if (effectiveApp.port != null) {
                val host = if (isLanAccessEnabled) "0.0.0.0" else "127.0.0.1"
                env["PORT"] = effectiveApp.port.toString()
                env["UVICORN_PORT"] = effectiveApp.port.toString()
                env["HOST"] = host
                env["UVICORN_HOST"] = host
            }
            env["PYTHONUNBUFFERED"] = "1"
            env["NODE_ENV"] = "production"
            env["__NEXT_NODE_NATIVE_TS_LOADER_ENABLED"] = "true"
            env["SSL_CERT_DIR"] = "/system/etc/security/cacerts"

            val caBundle = resolveSslCertFile(sandboxDir, execWorkdir)
            if (caBundle != null) {
                env["SSL_CERT_FILE"] = caBundle.absolutePath
                env["REQUESTS_CA_BUNDLE"] = caBundle.absolutePath
                env["CURL_CA_BUNDLE"] = caBundle.absolutePath
                env["NODE_EXTRA_CA_CERTS"] = caBundle.absolutePath
            }

            val binPath = runtimeInfo.binDir?.absolutePath
            val libPath = runtimeInfo.libDir?.absolutePath
            val appNativeLib = context.applicationInfo.nativeLibraryDir
            val existingPath = env["PATH"] ?: System.getenv("PATH") ?: "/system/bin"
            if (binPath != null) {
                env["PATH"] = "$binPath:$existingPath"
            }
            val ldPaths = mutableListOf<String>()
            if (libPath != null) ldPaths.add(libPath)
            if (libPath != null && runtimeType == RuntimeType.PYTHON) {
                try {
                    val libDir = File(libPath)
                    val dynloadDir = File(libDir, "lib-dynload").takeIf { it.exists() && it.isDirectory }
                        ?: libDir.listFiles()?.firstOrNull { it.isDirectory && it.name == "lib-dynload" }
                        ?: libDir.listFiles()?.filter { it.isDirectory }?.firstNotNullOfOrNull { sub ->
                            File(sub, "lib-dynload").takeIf { it.exists() && it.isDirectory }
                        }
                    if (dynloadDir != null) ldPaths.add(dynloadDir.absolutePath)
                } catch (_: Throwable) {}
            }
            if (appNativeLib.isNotBlank()) ldPaths.add(appNativeLib)
            ldPaths.add("/system/lib64")
            ldPaths.add("/vendor/lib64")
            ldPaths.add("/system/lib")
            ldPaths.add("/vendor/lib")
            env["LD_LIBRARY_PATH"] = ldPaths.joinToString(":")

            // Android filesystem compatibility (avoid /tmp permission errors)
            env["TMPDIR"] = context.cacheDir.absolutePath
            env["TEMP"] = context.cacheDir.absolutePath
            env["TMP"] = context.cacheDir.absolutePath
            env["HOME"] = sandboxDir.absolutePath
            env["PWD"] = execWorkdir.absolutePath
            env["SHELL"] = "/system/bin/sh"

            // Node.js module resolution
            val runtimeRoot = runtimeInfo.binDir?.parentFile
            val nodeModulesGlobal = if (runtimeRoot != null) File(runtimeRoot, "lib/node_modules").absolutePath else ""
            val nodeModulesApp = File(sandboxDir, "node_modules").absolutePath
            val nodeModulesWorkdir = File(execWorkdir, "node_modules").absolutePath
            val nodePaths = mutableListOf<String>()
            nodePaths.add(nodeModulesApp)
            if (nodeModulesWorkdir != nodeModulesApp) nodePaths.add(nodeModulesWorkdir)
            if (nodeModulesGlobal.isNotBlank()) nodePaths.add(nodeModulesGlobal)
            env["NODE_PATH"] = nodePaths.joinToString(":")

            // DNS resolution fallback and Volume VFS redirection for Node.js on Android
            if (runtimeType == RuntimeType.NODEJS) {
                val dnsHelperFile = File(sandboxDir, "box_dns_fallback.js")
                val nodeDataTarget = mountedVolumes.firstOrNull { it.targetSpec == "/data" || it.targetSpec == "data" }?.sandboxTarget
                    ?: File(sandboxDir, "data")
                if (!nodeDataTarget.exists()) nodeDataTarget.mkdirs()

                val dnsCode = """
                    const dns = require('dns');
                    const net = require('net');
                    const fs = require('fs');
                    const path = require('path');

                    // 1. Transparent volume path redirection for /data on Android
                    const _NODE_DATA = ${JSONObject.quote(nodeDataTarget.absolutePath)};
                    function _redirectVolPath(p) {
                        if (typeof p !== 'string') return p;
                        if (p === '/data' || p === '/data/') return _NODE_DATA;
                        if (p.startsWith('/data/')) {
                            const sub = p.slice(6);
                            const first = sub.split('/')[0];
                            if (!['user', 'data', 'app', 'local', 'dalvik-cache', 'system', 'misc', 'media'].includes(first)) {
                                return path.join(_NODE_DATA, sub);
                            }
                        }
                        return p;
                    }
                    ['open', 'openSync', 'readFile', 'readFileSync', 'writeFile', 'writeFileSync',
                     'appendFile', 'appendFileSync', 'stat', 'statSync', 'lstat', 'lstatSync',
                     'mkdir', 'mkdirSync', 'readdir', 'readdirSync', 'unlink', 'unlinkSync',
                     'rmdir', 'rmdirSync', 'rm', 'rmSync', 'access', 'accessSync', 'chmod', 'chmodSync',
                     'createReadStream', 'createWriteStream'].forEach(m => {
                        if (typeof fs[m] === 'function') {
                            const orig = fs[m];
                            fs[m] = function(p, ...args) {
                                return orig.call(this, _redirectVolPath(p), ...args);
                            };
                        }
                    });
                    if (fs.promises) {
                        ['open', 'readFile', 'writeFile', 'appendFile', 'stat', 'lstat',
                         'mkdir', 'readdir', 'unlink', 'rmdir', 'rm', 'access', 'chmod'].forEach(m => {
                            if (typeof fs.promises[m] === 'function') {
                                const orig = fs.promises[m];
                                fs.promises[m] = function(p, ...args) {
                                    return orig.call(this, _redirectVolPath(p), ...args);
                                };
                            }
                        });
                    }

                    // 2. DNS resolution fallback
                    try {
                        const customDns = (process.env.BOX_DNS_SERVERS || '')
                            .split(',')
                            .map(s => s.trim())
                            .filter(Boolean);
                        const servers = customDns.concat(['8.8.8.8', '1.1.1.1', '8.8.4.4', '1.0.0.1']);
                        dns.setServers(servers);
                        if (typeof dns.setDefaultResultOrder === 'function') {
                            dns.setDefaultResultOrder('ipv4first');
                        }
                    } catch (_) {}

                    const originalLookup = dns.lookup.bind(dns);

                    dns.lookup = function(hostname, options, callback) {
                        let opts = options;
                        let cb = callback;
                        if (typeof opts === 'function') {
                            cb = opts;
                            opts = {};
                        } else if (typeof opts === 'number') {
                            opts = { family: opts };
                        } else if (!opts) {
                            opts = {};
                        }

                        if (!hostname || hostname === 'localhost' || net.isIP(hostname)) {
                            return originalLookup(hostname, opts, cb);
                        }

                        originalLookup(hostname, opts, (err, address, family) => {
                            if (err && (err.code === 'ENOTFOUND' || err.code === 'EAI_AGAIN' || err.code === 'EAI_NODATA' || err.code === 'EAI_FAIL')) {
                                dns.resolve4(hostname, (resErr, addresses) => {
                                    if (!resErr && addresses && addresses.length > 0) {
                                        if (opts.all) {
                                            return cb(null, addresses.map(addr => ({ address: addr, family: 4 })));
                                        } else {
                                            return cb(null, addresses[0], 4);
                                        }
                                    }
                                    dns.resolve6(hostname, (res6Err, addresses6) => {
                                        if (!res6Err && addresses6 && addresses6.length > 0) {
                                            if (opts.all) {
                                                return cb(null, addresses6.map(addr => ({ address: addr, family: 6 })));
                                            } else {
                                                return cb(null, addresses6[0], 6);
                                            }
                                        }
                                        cb(err, address, family);
                                    });
                                });
                            } else {
                                cb(err, address, family);
                            }
                        });
                    };
                """.trimIndent()
                try {
                    dnsHelperFile.writeText(dnsCode, Charsets.UTF_8)
                    dnsHelperFile.setReadable(true, false)
                } catch (_: Exception) {}

                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    val activeNet = cm?.activeNetwork
                    val lp = cm?.getLinkProperties(activeNet)
                    val activeDns = lp?.dnsServers?.mapNotNull { it.hostAddress }?.filter { it.isNotBlank() } ?: emptyList()
                    if (activeDns.isNotEmpty()) {
                        env["BOX_DNS_SERVERS"] = activeDns.joinToString(",")
                    }
                } catch (_: Exception) {}

                val existingNodeOptions = env["NODE_OPTIONS"] ?: ""
                if (!existingNodeOptions.contains("box_dns_fallback.js")) {
                    env["NODE_OPTIONS"] = (existingNodeOptions + " --require ${dnsHelperFile.absolutePath}").trim()
                }
            }

            // Python standard library and search paths (including _box_lib from box-cli builder)
            if (runtimeType == RuntimeType.PYTHON) {
                if (runtimeRoot != null) {
                    env["PYTHONHOME"] = runtimeRoot.absolutePath
                }
                val pyLibDir = if (libPath != null) {
                    File(libPath).listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("python3") }
                } else null

                val pyPaths = mutableListOf<String>()
                pyPaths.add(execWorkdir.absolutePath)
                if (execWorkdir.absolutePath != sandboxDir.absolutePath) {
                    pyPaths.add(sandboxDir.absolutePath)
                }
                pyPaths.add(File(sandboxDir, "_box_lib").absolutePath)
                pyPaths.add(File(sandboxDir, "site-packages").absolutePath)
                pyPaths.add(File(sandboxDir, "lib").absolutePath)
                if (pyLibDir != null && pyLibDir.exists()) {
                    pyPaths.add(pyLibDir.absolutePath)
                    val dynload = File(pyLibDir, "lib-dynload")
                    if (dynload.exists()) pyPaths.add(dynload.absolutePath)
                    val sitePackages = File(pyLibDir, "site-packages")
                    if (sitePackages.exists()) pyPaths.add(sitePackages.absolutePath)
                }
                env["PYTHONPATH"] = pyPaths.joinToString(":")
                env["MULTIDICT_NO_EXTENSIONS"] = "1"
                env["YARL_NO_EXTENSIONS"] = "1"
                env["FROZENLIST_NO_EXTENSIONS"] = "1"
                env["PROPCACHE_NO_EXTENSIONS"] = "1"

                // Write sitecustomize.py into sandboxDir and execWorkdir for transparent volume path redirection
                try {
                    val pyDataTarget = mountedVolumes.firstOrNull { it.targetSpec == "/data" || it.targetSpec == "data" }?.sandboxTarget
                        ?: File(sandboxDir, "data")
                    if (!pyDataTarget.exists()) pyDataTarget.mkdirs()

                    val pyHookScript = """
import os
import sys
import builtins

_SANDBOX_DIR = ${JSONObject.quote(sandboxDir.absolutePath)}
_SANDBOX_DATA = ${JSONObject.quote(pyDataTarget.absolutePath)}

def _redirect_path(path):
    if not isinstance(path, (str, bytes, os.PathLike)):
        return path
    if hasattr(path, '__fspath__'):
        p_str = path.__fspath__()
    else:
        p_str = path
    is_bytes = isinstance(p_str, bytes)
    s = p_str.decode('utf-8', errors='surrogateescape') if is_bytes else p_str

    if s == '/data' or s == '/data/':
        return _SANDBOX_DATA.encode('utf-8') if is_bytes else _SANDBOX_DATA

    if s.startswith('/data/'):
        sub = s[6:]
        first = sub.split('/')[0]
        if first not in ('user', 'data', 'app', 'local', 'dalvik-cache', 'system', 'misc', 'media'):
            res = os.path.join(_SANDBOX_DATA, sub)
            return res.encode('utf-8') if is_bytes else res

    return path

_orig_open = builtins.open
def _hooked_open(file, *args, **kwargs):
    return _orig_open(_redirect_path(file), *args, **kwargs)
builtins.open = _hooked_open

_orig_os_open = os.open
def _hooked_os_open(path, *args, **kwargs):
    return _orig_os_open(_redirect_path(path), *args, **kwargs)
os.open = _hooked_os_open

_orig_stat = os.stat
def _hooked_stat(path, *args, **kwargs):
    return _orig_stat(_redirect_path(path), *args, **kwargs)
os.stat = _hooked_stat

_orig_lstat = os.lstat
def _hooked_lstat(path, *args, **kwargs):
    return _orig_lstat(_redirect_path(path), *args, **kwargs)
os.lstat = _hooked_lstat

_orig_makedirs = os.makedirs
def _hooked_makedirs(name, *args, **kwargs):
    return _orig_makedirs(_redirect_path(name), *args, **kwargs)
os.makedirs = _hooked_makedirs

_orig_mkdir = os.mkdir
def _hooked_mkdir(path, *args, **kwargs):
    return _orig_mkdir(_redirect_path(path), *args, **kwargs)
os.mkdir = _hooked_mkdir

_orig_remove = os.remove
def _hooked_remove(path, *args, **kwargs):
    return _orig_remove(_redirect_path(path), *args, **kwargs)
os.remove = _hooked_remove

_orig_unlink = os.unlink
def _hooked_unlink(path, *args, **kwargs):
    return _orig_unlink(_redirect_path(path), *args, **kwargs)
os.unlink = _hooked_unlink

_orig_rmdir = os.rmdir
def _hooked_rmdir(path, *args, **kwargs):
    return _orig_rmdir(_redirect_path(path), *args, **kwargs)
os.rmdir = _hooked_rmdir

_orig_rename = os.rename
def _hooked_rename(src, dst, *args, **kwargs):
    return _orig_rename(_redirect_path(src), _redirect_path(dst), *args, **kwargs)
os.rename = _hooked_rename

_orig_replace = os.replace
def _hooked_replace(src, dst, *args, **kwargs):
    return _orig_replace(_redirect_path(src), _redirect_path(dst), *args, **kwargs)
os.replace = _hooked_replace

_orig_scandir = os.scandir
def _hooked_scandir(path=None):
    if path is not None:
        path = _redirect_path(path)
    return _orig_scandir(path)
os.scandir = _hooked_scandir

_orig_listdir = os.listdir
def _hooked_listdir(path=None):
    if path is not None:
        path = _redirect_path(path)
    return _orig_listdir(path)
os.listdir = _hooked_listdir

_orig_chmod = os.chmod
def _hooked_chmod(path, *args, **kwargs):
    return _orig_chmod(_redirect_path(path), *args, **kwargs)
os.chmod = _hooked_chmod

_orig_access = os.access
def _hooked_access(path, *args, **kwargs):
    return _orig_access(_redirect_path(path), *args, **kwargs)
os.access = _hooked_access

_orig_exists = os.path.exists
def _hooked_exists(path):
    return _orig_exists(_redirect_path(path))
os.path.exists = _hooked_exists

_orig_isfile = os.path.isfile
def _hooked_isfile(path):
    return _orig_isfile(_redirect_path(path))
os.path.isfile = _hooked_isfile

_orig_isdir = os.path.isdir
def _hooked_isdir(path):
    return _orig_isdir(_redirect_path(path))
os.path.isdir = _hooked_isdir
""".trimIndent()

                    val targetFiles = listOfNotNull(
                        File(sandboxDir, "sitecustomize.py"),
                        if (execWorkdir.absolutePath != sandboxDir.absolutePath) File(execWorkdir, "sitecustomize.py") else null
                    )
                    for (sf in targetFiles) {
                        sf.writeText(pyHookScript, Charsets.UTF_8)
                        sf.setReadable(true, false)
                    }
                } catch (_: Exception) {}
            }

            val process = pb.start()
            val startTime = System.currentTimeMillis()

            val outputJob = scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { appendLog(app.id, it, isError = false) }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Output reader ended for ${app.id}")
                }
            }

            val monitorJob = scope.launch {
                try {
                    val exitCode = process.waitFor()
                    outputJob.join() // Ensure all stdout/stderr is flushed to logs first
                    val wasExplicitlyStopped = !runningProcesses.containsKey(app.id)
                    runningProcesses.remove(app.id)
                    if (!wasExplicitlyStopped) {
                        if (exitCode != 0 && exitCode != 143) {
                            appendLog(app.id, "Process exited with code $exitCode", isError = true)
                        }
                    }
                } catch (_: Exception) {}
            }

            runningProcesses[app.id] = RunningProcess(
                appId = app.id,
                process = process,
                startTime = startTime,
                readerJobs = listOf(outputJob, monitorJob),
                memoryMb = 18.5f
            )

            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.d(TAG, "startApp cancelled for ${app.id}")
                throw e
            }
            Log.e(TAG, "Failed to start app ${app.id}", e)
            appendLog(app.id, "Startup error: ${e.message ?: "Unknown error"}", isError = true)
            Result.failure(e)
        }
    }

    suspend fun stopApp(appId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val running = runningProcesses.remove(appId)
        if (running == null) {
            return@withContext Result.success(Unit)
        }

        try {
            running.process.destroy()
            running.readerJobs.forEach { it.cancel() }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping app $appId", e)
            Result.failure(e)
        }
    }

    suspend fun stopAll() = withContext(Dispatchers.IO) {
        runningProcesses.keys.toList().forEach { stopApp(it) }
    }

    private fun resolveSslCertFile(sandboxDir: File, execWorkdir: File): File? {
        val candidates = listOf(
            File(sandboxDir, "_box_lib/certifi/cacert.pem"),
            File(execWorkdir, "_box_lib/certifi/cacert.pem"),
            File(sandboxDir, "certifi/cacert.pem"),
            File(execWorkdir, "certifi/cacert.pem"),
            File(context.filesDir, "cacert.pem")
        )
        val existing = candidates.firstOrNull { it.exists() && it.isFile && it.length() > 1000 }
        if (existing != null) return existing

        val systemCaFile = File(context.filesDir, "cacert.pem")
        try {
            val ks = java.security.KeyStore.getInstance("AndroidCAStore")
            ks.load(null, null)
            val aliases = ks.aliases()
            val certs = StringBuilder()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                val cert = ks.getCertificate(alias)
                if (cert != null) {
                    certs.append("-----BEGIN CERTIFICATE-----\n")
                    certs.append(android.util.Base64.encodeToString(cert.encoded, android.util.Base64.DEFAULT))
                    certs.append("-----END CERTIFICATE-----\n")
                }
            }
            if (certs.isNotEmpty()) {
                systemCaFile.writeText(certs.toString(), Charsets.UTF_8)
                systemCaFile.setReadable(true, false)
                return systemCaFile
            }
        } catch (_: Exception) {}

        return null
    }

    companion object {
        private const val TAG = "BoxProcessManager"
    }
}
