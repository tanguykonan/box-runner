package com.box.android.core.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import com.box.android.core.sandbox.SandboxManager
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class RuntimeManager(private val context: Context) {

    private val runtimesDir: File by lazy {
        File(context.filesDir, "runtimes").apply {
            if (!exists()) mkdirs()
        }
    }

    val primaryAbi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    fun getRuntimeDir(runtimeId: String): File {
        return File(runtimesDir, runtimeId).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun isElfBinary(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { input ->
                val b = ByteArray(4)
                if (input.read(b) == 4) {
                    b[0] == 0x7F.toByte() && b[1] == 'E'.code.toByte() && b[2] == 'L'.code.toByte() && b[3] == 'F'.code.toByte()
                } else false
            }
        } catch (_: Exception) { false }
    }

    private fun replacePattern(raf: java.io.RandomAccessFile, targetBytes: ByteArray, replacementBytes: ByteArray) {
        val bufSize = 64 * 1024
        val buffer = ByteArray(bufSize)
        var filePos = 0L
        val fileLen = raf.length()

        while (filePos < fileLen) {
            raf.seek(filePos)
            val read = raf.read(buffer)
            if (read < targetBytes.size) break

            var offset = 0
            while (offset <= read - targetBytes.size) {
                var matches = true
                for (k in targetBytes.indices) {
                    if (buffer[offset + k] != targetBytes[k]) {
                        matches = false
                        break
                    }
                }
                if (matches) {
                    val matchPos = filePos + offset
                    raf.seek(matchPos)
                    raf.write(replacementBytes)
                    raf.seek(matchPos + targetBytes.size)
                    offset += targetBytes.size
                } else {
                    offset++
                }
            }

            filePos += (read - targetBytes.size + 1)
        }
    }

    fun repairAndPatchElf(file: File, isBinary: Boolean) {
        if (!file.exists() || !file.isFile || file.length() < 4) return
        try {
            java.io.RandomAccessFile(file, "rw").use { raf ->
                if (raf.length() < 4) return
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] != 0x7F.toByte() || magic[1] != 'E'.code.toByte() ||
                    magic[2] != 'L'.code.toByte() || magic[3] != 'F'.code.toByte()) {
                    return
                }

                // 1. Repair previously corrupted JSON occurrences in Node binary (-L$ORIGIN/../lib\0...)
                if (isBinary) {
                    val badJsonPattern = "-L\$ORIGIN/../lib\u0000".toByteArray(Charsets.US_ASCII)
                    val originalJson = "-L/data/data/com.termux/files/usr/lib".toByteArray(Charsets.US_ASCII) // 37 bytes
                    replacePattern(raf, badJsonPattern, originalJson)
                }

                // 2. Patch ELF DT_RUNPATH
                val isDynload = file.parentFile?.name == "lib-dynload" || file.absolutePath.contains("lib-dynload")
                val (replDoubleStr, replSingleStr) = when {
                    isDynload -> "\$ORIGIN/../..:\$ORIGIN\u0000" to "\$ORIGIN/../..\u0000"
                    isBinary -> "\$ORIGIN/../lib:\$ORIGIN\u0000" to "\$ORIGIN/../lib\u0000"
                    else -> "\$ORIGIN:\$ORIGIN\u0000" to "\$ORIGIN\u0000"
                }

                // First patch double path occurrences (72 bytes: two paths separated by colon + null terminator)
                val targetDouble = "/data/data/com.termux/files/usr/lib:/data/data/com.termux/files/usr/lib\u0000".toByteArray(Charsets.US_ASCII)
                val replDouble = ByteArray(targetDouble.size)
                val rawReplDouble = replDoubleStr.toByteArray(Charsets.US_ASCII)
                System.arraycopy(rawReplDouble, 0, replDouble, 0, minOf(rawReplDouble.size, replDouble.size))
                replacePattern(raf, targetDouble, replDouble)

                // Then patch single path occurrences (36 bytes)
                val targetSingle = "/data/data/com.termux/files/usr/lib\u0000".toByteArray(Charsets.US_ASCII)
                val replSingle = ByteArray(targetSingle.size)
                val rawReplSingle = replSingleStr.toByteArray(Charsets.US_ASCII)
                System.arraycopy(rawReplSingle, 0, replSingle, 0, minOf(rawReplSingle.size, replSingle.size))
                replacePattern(raf, targetSingle, replSingle)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to repair/patch ELF in ${file.name}", e)
        }
    }

    private fun findLocalSystemBinary(type: RuntimeType): File? {
        val candidates = when (type) {
            RuntimeType.NODEJS -> listOf(
                File("/data/data/com.termux/files/usr/bin/node"),
                File("/data/user/0/com.termux/files/usr/bin/node"),
                File("/system/bin/node"),
                File("/system/xbin/node"),
                File(context.applicationInfo.nativeLibraryDir, "libnode.so")
            )
            RuntimeType.PYTHON -> listOf(
                File("/data/data/com.termux/files/usr/bin/python3"),
                File("/data/user/0/com.termux/files/usr/bin/python3"),
                File("/data/data/com.termux/files/usr/bin/python"),
                File("/system/bin/python3"),
                File("/system/bin/python"),
                File(context.applicationInfo.nativeLibraryDir, "libpython3.so")
            )
        }
        return candidates.firstOrNull { it.exists() && (isElfBinary(it) || it.canExecute()) }
    }

    suspend fun resolveRuntime(type: RuntimeType, version: String = ""): RuntimeInfo = withContext(Dispatchers.IO) {
        val resolvedVersion = version.ifBlank {
            when (type) {
                RuntimeType.PYTHON -> "3.12"
                RuntimeType.NODEJS -> "20"
            }
        }

        val runtimeId = "${type.identifier}-$resolvedVersion-$primaryAbi"
        val targetDir = getRuntimeDir(runtimeId)
        val binDir = File(targetDir, "bin").apply { if (!exists()) mkdirs() }
        val libDir = File(targetDir, "lib").apply { if (!exists()) mkdirs() }

        // For Python: ensure 'python' and 'python3' are real physical executable files copied from python3.x
        if (type == RuntimeType.PYTHON) {
            // Purge incompatible libc++ from Python lib directory to prevent shadowing system /system/lib64/libc++.so
            File(libDir, "libc++.so").apply { if (exists()) delete() }
            File(libDir, "libc++_shared.so").apply { if (exists()) delete() }
            File(libDir, "libcrypto.so").apply { if (exists()) delete() }
            File(libDir, "libssl.so").apply { if (exists()) delete() }

            val pyExe = binDir.listFiles()?.firstOrNull {
                (it.name.startsWith("python3.") || it.name == "python3") &&
                        !it.name.endsWith("-config") && !it.name.endsWith(".so") && it.isFile && isElfBinary(it)
            } ?: File(binDir, "python3.14")

            if (pyExe.exists()) {
                for (aliasName in listOf("python", "python3")) {
                    val aliasFile = File(binDir, aliasName)
                    val isSymlink = try { java.nio.file.Files.isSymbolicLink(aliasFile.toPath()) } catch (_: Exception) { false }
                    val needsCopy = !aliasFile.exists() || isSymlink || aliasFile.length() != pyExe.length() || !isElfBinary(aliasFile)
                    if (needsCopy) {
                        try {
                            if (aliasFile.exists() || isSymlink) aliasFile.delete()
                            pyExe.copyTo(aliasFile, overwrite = true)
                            aliasFile.setReadable(true, false)
                            aliasFile.setExecutable(true, false)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        var executableFile = File(binDir, type.executableName)
        if (!executableFile.exists() && type == RuntimeType.PYTHON) {
            val altPy = File(binDir, "python3").takeIf { it.exists() }
                ?: File(binDir, "python3.14").takeIf { it.exists() }
                ?: binDir.listFiles()?.firstOrNull { it.name.startsWith("python") && isElfBinary(it) }
            if (altPy != null) {
                try {
                    altPy.copyTo(executableFile, overwrite = true)
                    executableFile.setReadable(true, false)
                    executableFile.setExecutable(true, false)
                } catch (_: Exception) {
                    executableFile = altPy
                }
            }
        }

        val minExecutableSize = if (type == RuntimeType.PYTHON) 1000L else 1024L * 1024L

        // 1. Purge legacy non-ELF or corrupt/truncated files sitting in runtime dir
        if (executableFile.exists() && (!isElfBinary(executableFile) || executableFile.length() < minExecutableSize)) {
            Log.w(TAG, "Purging invalid/truncated file at ${executableFile.absolutePath} (size=${executableFile.length()})")
            try { executableFile.delete() } catch (_: Exception) {}
        }

        // 2. Check if real standalone ELF binary is present and companion libs exist
        if (executableFile.exists() && executableFile.length() >= minExecutableSize && isElfBinary(executableFile)) {
            val hasEssentialLibs = when (type) {
                RuntimeType.NODEJS -> (File(libDir, "libsqlite3.so").exists() || File(libDir, "libsqlite3.so.0").exists()) &&
                        (File(libDir, "libz.so.1").exists() || File(libDir, "libz.so").exists()) &&
                        (File(libDir, "libuv.so.1").exists() || File(libDir, "libuv.so").exists()) &&
                        (File(libDir, "libcrypto.so.3").exists() || File(libDir, "libcrypto.so").exists())
                RuntimeType.PYTHON -> File(libDir, "libandroid-support.so").exists() &&
                        (File(libDir, "libpython3.so").exists() || File(libDir, "libpython3.14.so").exists() || (libDir.listFiles()?.any { it.name.startsWith("libpython3") } == true))
            }

            if (hasEssentialLibs) {
                val patchMarker = File(targetDir, ".patch_v6")
                if (!patchMarker.exists()) {
                    repairAndPatchElf(executableFile, isBinary = true)
                    binDir.listFiles()?.filter { it.isFile }?.forEach {
                        repairAndPatchElf(it, isBinary = true)
                        it.setReadable(true, false)
                        it.setExecutable(true, false)
                    }
                    libDir.walkTopDown().filter { it.isFile && (it.name.endsWith(".so") || it.name.contains(".so.") || isElfBinary(it)) }.forEach {
                        repairAndPatchElf(it, isBinary = false)
                        it.setReadable(true, false)
                        it.setExecutable(true, false)
                    }
                    try { patchMarker.createNewFile() } catch (_: Exception) {}
                }

                // Ensure all companion libraries and C-extensions in lib-dynload/ are healthy
                if (type == RuntimeType.PYTHON) {
                    val dynloadDir = libDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "lib-dynload" }
                    if (dynloadDir != null && dynloadDir.exists()) {
                        // Auto-repair any previously truncated .cpython- C-extensions
                        dynloadDir.listFiles()?.filter {
                            it.isFile && it.name.contains(".cpython-") && !it.name.endsWith(".so") && !it.name.endsWith(".pyc")
                        }?.forEach { f ->
                            val base = f.name.substringBefore(".cpython-")
                            val correct = File(dynloadDir, "$base.cpython-314-aarch64-linux-android.so")
                            if (correct.exists()) {
                                f.delete()
                            } else {
                                f.renameTo(correct)
                            }
                        }

                        libDir.listFiles()?.filter { it.isFile && (it.name.endsWith(".so") || it.name.contains(".so.")) }?.forEach { soFile ->
                            val target = File(dynloadDir, soFile.name)
                            if (!target.exists() || target.length() != soFile.length()) {
                                try {
                                    soFile.copyTo(target, overwrite = true)
                                    target.setReadable(true, false)
                                    target.setExecutable(true, false)
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    // Provide PEP 594 audioop compatibility shim for Python 3.14+
                    val pyLibDir = libDir.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("python3") }
                    if (pyLibDir != null && pyLibDir.exists()) {
                        val audioopFile = File(pyLibDir, "audioop.py")
                        if (!audioopFile.exists()) {
                            try {
                                val shimCode = """
                                    # PEP 594 audioop compatibility shim
                                    class error(Exception): pass
                                    def mul(f, w, fac): return f
                                    def tostereo(f, w, lf, rf): return f
                                    def tomono(f, w, lf, rf): return f
                                    def minmax(f, w): return (0, 0)
                                    def avg(f, w): return 0
                                    def rms(f, w): return 0
                                    def cross(f, w): return 0
                                    def add(f1, f2, w): return f1
                                    def bias(f, w, b): return f
                                    def reverse(f, w): return f
                                    def byteswap(f, w): return f
                                    def lin2lin(f, w1, w2): return f
                                    def ratecv(f, w, nch, ir, otr, s, wfac=1): return (f, s)
                                    def lin2ulaw(f, w): return f
                                    def ulaw2lin(f, w): return f
                                    def lin2alaw(f, w): return f
                                    def alaw2lin(f, w): return f
                                    def lin2adpcm(f, w, s): return (f, s)
                                    def adpcm2lin(f, w, s): return (f, s)
                                """.trimIndent()
                                audioopFile.writeText(shimCode, Charsets.UTF_8)
                            } catch (_: Exception) {}
                        }
                    }
                }
                ensureExecutable(executableFile)
                return@withContext RuntimeInfo(
                    id = runtimeId,
                    type = type,
                    version = resolvedVersion,
                    executable = executableFile,
                    binDir = binDir,
                    libDir = libDir,
                    isInstalled = true
                )
            }
        }

        // 3. Check if a local system or Termux ELF binary is available
        val systemBin = findLocalSystemBinary(type)
        if (systemBin != null) {
            return@withContext RuntimeInfo(
                id = "${type.identifier}-system",
                type = type,
                version = resolvedVersion,
                executable = systemBin,
                binDir = systemBin.parentFile,
                libDir = systemBin.parentFile?.parentFile?.let { File(it, "lib") },
                isInstalled = true
            )
        }

        RuntimeInfo(
            id = runtimeId,
            type = type,
            version = resolvedVersion,
            executable = executableFile,
            binDir = binDir,
            libDir = libDir,
            isInstalled = false
        )
    }

    suspend fun ensureExecutable(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext false
        try {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            val process = Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to chmod +x on ${file.absolutePath}", e)
            file.canExecute()
        }
    }

    suspend fun ensureRuntimeInstalled(
        type: RuntimeType,
        version: String = "",
        onProgress: (String) -> Unit = {}
    ): RuntimeInfo = withContext(Dispatchers.IO) {
        var runtimeInfo = resolveRuntime(type, version)
        if (runtimeInfo.isInstalled) {
            onProgress("[RUNTIME] Native ${type.identifier.uppercase()} engine ready (${runtimeInfo.id})")
            return@withContext runtimeInfo
        }

        onProgress("[RUNTIME] Resolving portable standalone ${type.identifier.uppercase()} engine for $primaryAbi...")

        val targetDir = getRuntimeDir(runtimeInfo.id)
        val binDir = runtimeInfo.binDir ?: File(targetDir, "bin")
        val libDir = runtimeInfo.libDir ?: File(targetDir, "lib")
        binDir.mkdirs()
        libDir.mkdirs()

        val (debArch, _) = when {
            primaryAbi.contains("arm64") || primaryAbi.contains("aarch64") -> "aarch64" to "arm64"
            primaryAbi.contains("v7a") || primaryAbi.contains("arm") -> "arm" to "arm"
            primaryAbi.contains("x86_64") -> "x86_64" to "x86_64"
            else -> "i686" to "x86"
        }

        val packageUrls = when (type) {
            RuntimeType.NODEJS -> listOf(
                "zlib" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/z/zlib/zlib_1.3.2_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/z/zlib/zlib_1.3.1_$debArch.deb"
                ),
                "c-ares" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/c/c-ares/c-ares_1.34.8_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/c/c-ares/c-ares_1.34.4_$debArch.deb"
                ),
                "libuv" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/libu/libuv/libuv_1.52.1_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/libu/libuv/libuv_1.50.0_$debArch.deb"
                ),
                "libandroid-support" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-support/libandroid-support_29-1_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-support/libandroid-support_29_$debArch.deb"
                ),
                "openssl" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/o/openssl/openssl_1%3A3.6.3_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/o/openssl/openssl_1%3A3.4.1_$debArch.deb"
                ),
                "libffi" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/libf/libffi/libffi_3.8.0_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/libf/libffi/libffi_3.4.6_$debArch.deb"
                ),
                "libc++" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/libc/libc++/libc%2B%2B_29_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/libc/libc++/libc%2B%2B_28_$debArch.deb"
                ),
                "libicu" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/libi/libicu/libicu_78.3_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/libi/libicu/libicu_76.1_$debArch.deb"
                ),
                "libsqlite" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/libs/libsqlite/libsqlite_3.53.4_$debArch.deb"
                ),
                "nodejs" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/n/nodejs/nodejs_26.4.0-1_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/n/nodejs/nodejs_26.4.0_$debArch.deb",
                    "https://packages.termux.dev/apt/termux-main/pool/main/n/nodejs/nodejs_23.6.0_$debArch.deb"
                )
            )
            RuntimeType.PYTHON -> listOf(
                "zlib" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/z/zlib/zlib_1.3.2_$debArch.deb"
                ),
                "libandroid-support" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-support/libandroid-support_29-1_$debArch.deb"
                ),
                "openssl" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/o/openssl/openssl_1%3A3.6.3_$debArch.deb"
                ),
                "libffi" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/libf/libffi/libffi_3.8.0_$debArch.deb"
                ),
                "libsqlite" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/libs/libsqlite/libsqlite_3.53.4_$debArch.deb"
                ),
                "libandroid-posix-semaphore" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-posix-semaphore/libandroid-posix-semaphore_0.1-4_$debArch.deb"
                ),
                "libcrypt" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/libc/libcrypt/libcrypt_0.2-6_$debArch.deb"
                ),
                "libexpat" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/libe/libexpat/libexpat_2.8.4_$debArch.deb"
                ),
                "xz-utils" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/x/xz-utils/xz-utils_5.8.4_$debArch.deb"
                ),
                "bzip2" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/b/bzip2/bzip2_1.0.8-8_$debArch.deb"
                ),
                "readline" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/r/readline/readline_8.3.3_$debArch.deb"
                ),
                "zstd" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/z/zstd/zstd_1.5.7-1_$debArch.deb"
                ),
                "gdbm" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/g/gdbm/gdbm_1.26-1_$debArch.deb"
                ),
                "python" to listOf(
                    "https://packages.termux.dev/apt/termux-main/pool/main/p/python/python_3.14.6-1_$debArch.deb"
                )
            )
        }

        onProgress("[RUNTIME] Téléchargement du moteur autonome ${type.identifier.uppercase()} et de ses bibliothèques ($debArch)...")

        for ((pkgName, candidateUrls) in packageUrls) {
            val alreadyExtracted = when (pkgName) {
                "python" -> {
                    val hasExe = (File(binDir, "python3.14").exists() || File(binDir, "python3").exists() || File(binDir, "python").exists()) && (File(libDir, "libpython3.14.so").exists() || File(libDir, "libpython3.so").exists())
                    val dynloadDir = libDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "lib-dynload" }
                    val socketSo = dynloadDir?.listFiles()?.firstOrNull { it.name.startsWith("_socket") && it.name.endsWith(".so") }
                    hasExe && socketSo != null
                }
                "nodejs" -> File(binDir, "node").exists() && File(binDir, "node").length() >= 1024 * 1024
                "zlib" -> File(libDir, "libz.so").exists() || File(libDir, "libz.so.1").exists()
                "libandroid-support" -> File(libDir, "libandroid-support.so").exists()
                "openssl" -> (File(libDir, "libcrypto.so.3").exists() && File(libDir, "libcrypto.so.3").length() >= 4 * 1024 * 1024L) && (File(libDir, "libssl.so").exists() || File(libDir, "libssl.so.3").exists())
                "libffi" -> File(libDir, "libffi.so").exists() || File(libDir, "libffi.so.8").exists()
                "libc++" -> File(libDir, "libc++.so").exists() || File(libDir, "libc++_shared.so").exists()
                "libsqlite" -> File(libDir, "libsqlite3.so").exists() || File(libDir, "libsqlite3.so.0").exists()
                "c-ares" -> File(libDir, "libcares.so").exists() || File(libDir, "libcares.so.2").exists()
                "libuv" -> File(libDir, "libuv.so").exists() || File(libDir, "libuv.so.1").exists()
                else -> false
            }
            if (alreadyExtracted) {
                continue
            }

            var downloaded = false
            for (downloadUrl in candidateUrls) {
                try {
                    val url = URL(downloadUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 60000
                    conn.setRequestProperty("User-Agent", "BoxMobileRunner/1.0")

                    if (conn.responseCode in 200..299) {
                        val expectedLen = conn.contentLengthLong
                        val tempArchive = File(targetDir, "temp_$pkgName.pkg")
                        conn.inputStream.use { input ->
                            tempArchive.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (expectedLen > 0 && tempArchive.length() < expectedLen) {
                            tempArchive.delete()
                            throw java.io.IOException("Incomplete download for $pkgName: got ${tempArchive.length()} of $expectedLen bytes")
                        }
                        extractDebOrArchive(tempArchive, targetDir)
                        tempArchive.delete()
                        onProgress("[RUNTIME] Module $pkgName installé.")
                        downloaded = true
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Download attempt failed for $pkgName from $downloadUrl: ${e.message}")
                }
            }
            if (!downloaded) {
                Log.w(TAG, "All candidate URLs failed for package $pkgName")
            }
        }

        // Post-processing: Generate soname aliases and fix symlinks in bin/ and lib/
        if (type == RuntimeType.PYTHON) {
            // Purge incompatible libc++ from Python lib directory to prevent shadowing system /system/lib64/libc++.so
            File(libDir, "libc++.so").apply { if (exists()) delete() }
            File(libDir, "libc++_shared.so").apply { if (exists()) delete() }
            File(libDir, "libcrypto.so").apply { if (exists()) delete() }
            File(libDir, "libssl.so").apply { if (exists()) delete() }

            val pyExe = binDir.listFiles()?.firstOrNull {
                (it.name.startsWith("python3.") || it.name == "python3") &&
                        !it.name.endsWith("-config") && !it.name.endsWith(".so") && it.isFile && isElfBinary(it)
            } ?: File(binDir, "python3.14")

            if (pyExe.exists()) {
                for (aliasName in listOf("python", "python3")) {
                    val aliasFile = File(binDir, aliasName)
                    try {
                        if (aliasFile.exists() || java.nio.file.Files.isSymbolicLink(aliasFile.toPath())) {
                            aliasFile.delete()
                        }
                        pyExe.copyTo(aliasFile, overwrite = true)
                        aliasFile.setReadable(true, false)
                        aliasFile.setExecutable(true, false)
                    } catch (_: Exception) {}
                }
            }
        }

        fun ensureAlias(sourceName: String, targetName: String) {
            val sourceFile = File(libDir, sourceName)
            val targetFile = File(libDir, targetName)
            if (sourceFile.exists() && !targetFile.exists()) {
                try {
                    sourceFile.copyTo(targetFile, overwrite = true)
                    targetFile.setReadable(true, false)
                    targetFile.setExecutable(true, false)
                } catch (_: Exception) {}
            }
        }

        // Auto-generate aliases for all versioned .so files
        libDir.listFiles()?.filter { it.isFile && it.name.contains(".so") }?.forEach { f ->
            val name = f.name
            if (type == RuntimeType.PYTHON && (name.startsWith("libc++") || name.startsWith("libcrypto.so") || name.startsWith("libssl.so"))) {
                return@forEach
            }
            // e.g. libz.so.1.3.2 -> libz.so.1, libz.so
            // e.g. libuv.so.1.52.1 -> libuv.so.1, libuv.so
            val match = Regex("^(lib[^.]+)\\.so\\.(\\d+)(?:\\.\\d+)*$").find(name)
            if (match != null) {
                val base = match.groupValues[1]
                val major = match.groupValues[2]
                ensureAlias(name, "$base.so.$major")
                ensureAlias(name, "$base.so")
            }
        }

        // Standard explicit aliases
        ensureAlias("libsqlite3.so.0", "libsqlite3.so")
        ensureAlias("libsqlite3.so", "libsqlite3.so.0")
        ensureAlias("libsqlite3.so.0.8.6", "libsqlite3.so")
        ensureAlias("libsqlite3.so.0.8.6", "libsqlite3.so.0")
        ensureAlias("libz.so", "libz.so.1")
        ensureAlias("libz.so.1", "libz.so")
        ensureAlias("libuv.so", "libuv.so.1")
        ensureAlias("libuv.so.1", "libuv.so")
        ensureAlias("libcares.so", "libcares.so.2")
        ensureAlias("libcares.so.2", "libcares.so")
        if (type != RuntimeType.PYTHON) {
            ensureAlias("libcrypto.so", "libcrypto.so.3")
            ensureAlias("libcrypto.so.3", "libcrypto.so")
            ensureAlias("libssl.so", "libssl.so.3")
            ensureAlias("libssl.so.3", "libssl.so")
            ensureAlias("libc++.so", "libc++_shared.so")
            ensureAlias("libc++_shared.so", "libc++.so")
        }
        ensureAlias("libffi.so", "libffi.so.8")
        ensureAlias("libffi.so.8", "libffi.so")
        ensureAlias("libbz2.so", "libbz2.so.1.0")
        ensureAlias("libbz2.so.1.0", "libbz2.so")
        ensureAlias("liblzma.so", "liblzma.so.5")
        ensureAlias("liblzma.so.5", "liblzma.so")
        ensureAlias("libexpat.so", "libexpat.so.1")
        ensureAlias("libexpat.so.1", "libexpat.so")
        ensureAlias("libzstd.so", "libzstd.so.1")
        ensureAlias("libzstd.so.1", "libzstd.so")
        ensureAlias("libpython3.14.so", "libpython3.so")
        ensureAlias("libpython3.so", "libpython3.14.so")

        val patchMarker = File(targetDir, ".patch_v5")
        binDir.listFiles()?.forEach { 
            repairAndPatchElf(it, isBinary = true)
            ensureExecutable(it) 
        }
        libDir.walkTopDown().filter { it.isFile && (it.name.endsWith(".so") || it.name.contains(".so.") || isElfBinary(it)) }.forEach { 
            repairAndPatchElf(it, isBinary = false)
            ensureExecutable(it) 
        }
        if (runtimeInfo.executable.exists()) {
            repairAndPatchElf(runtimeInfo.executable, isBinary = true)
            ensureExecutable(runtimeInfo.executable)
        }
        if (type == RuntimeType.PYTHON) {
            val dynloadDir = libDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "lib-dynload" }
            if (dynloadDir != null && dynloadDir.exists()) {
                libDir.listFiles()?.filter { it.isFile && (it.name.endsWith(".so") || it.name.contains(".so.")) }?.forEach { soFile ->
                    val target = File(dynloadDir, soFile.name)
                    if (!target.exists() || target.length() != soFile.length()) {
                        try {
                            soFile.copyTo(target, overwrite = true)
                            target.setReadable(true, false)
                            target.setExecutable(true, false)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        try { patchMarker.createNewFile() } catch (_: Exception) {}

        runtimeInfo = resolveRuntime(type, version)
        if (runtimeInfo.isInstalled) {
            onProgress("[RUNTIME] Moteur autonome ${type.identifier.uppercase()} prêt à l'emploi.")
            return@withContext runtimeInfo
        }

        val minExeSize = if (type == RuntimeType.PYTHON) 1000L else 1024L * 1024L
        if (runtimeInfo.executable.exists() && runtimeInfo.executable.length() >= minExeSize && isElfBinary(runtimeInfo.executable)) {
            repairAndPatchElf(runtimeInfo.executable, isBinary = true)
            ensureExecutable(runtimeInfo.executable)
            try { patchMarker.createNewFile() } catch (_: Exception) {}
            return@withContext runtimeInfo.copy(isInstalled = true)
        }

        onProgress("[RUNTIME] Native runtime engine resolved: ${runtimeInfo.id}")
        runtimeInfo
    }

    private fun extractDebOrArchive(archiveFile: File, targetDir: File) {
        try {
            archiveFile.inputStream().buffered().use { fis ->
                val magic = ByteArray(8)
                val readMagic = fis.read(magic)
                if (readMagic == 8 && String(magic, Charsets.US_ASCII) == "!<arch>\n") {
                    // Extract Debian AR package
                    val header = ByteArray(60)
                    while (fis.read(header) == 60) {
                        val name = String(header, 0, 16, Charsets.US_ASCII).trim()
                        val sizeStr = String(header, 48, 10, Charsets.US_ASCII).trim()
                        val size = sizeStr.toLongOrNull() ?: 0L

                        if (name.startsWith("data.tar.xz") || name.startsWith("data.tar.gz")) {
                            val isXz = name.startsWith("data.tar.xz")
                            val tempFile = File(targetDir, "temp_data_${System.currentTimeMillis()}.${if (isXz) "tar.xz" else "tar.gz"}")
                            try {
                                tempFile.outputStream().buffered().use { fos ->
                                    var rem = size
                                    val buf = ByteArray(16384)
                                    while (rem > 0) {
                                        val toRead = minOf(buf.size.toLong(), rem).toInt()
                                        val r = fis.read(buf, 0, toRead)
                                        if (r == -1) break
                                        fos.write(buf, 0, r)
                                        rem -= r
                                    }
                                }

                                if (isXz) {
                                    tempFile.inputStream().buffered().use { tis ->
                                        val xzStream = org.tukaani.xz.XZInputStream(tis)
                                        extractTarPayload(xzStream, targetDir)
                                    }
                                } else {
                                    tempFile.inputStream().buffered().use { tis ->
                                        val gzStream = java.util.zip.GZIPInputStream(tis)
                                        extractTarPayload(gzStream, targetDir)
                                    }
                                }
                            } finally {
                                try { tempFile.delete() } catch (_: Exception) {}
                            }
                            break
                        } else {
                            var toSkip = size + (size % 2)
                            while (toSkip > 0) {
                                val skipped = fis.skip(toSkip)
                                if (skipped <= 0) {
                                    val dummy = ByteArray(minOf(toSkip, 8192L).toInt())
                                    val r = fis.read(dummy)
                                    if (r == -1) break
                                    toSkip -= r
                                } else {
                                    toSkip -= skipped
                                }
                            }
                        }
                    }
                    return
                }
            }

            // Fallback: standard archive (.tar, .tar.gz, .box, .tar.zst)
            SandboxManager.extractBoxArchive(archiveFile, targetDir)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting runtime archive", e)
        }
    }

    private fun extractTarPayload(input: java.io.InputStream, outputDir: File) {
        TarArchiveInputStream(input).use { tarIn ->
            var entry = tarIn.nextEntry as? org.apache.commons.compress.archivers.tar.TarArchiveEntry
            while (entry != null) {
                val rawName = entry.name
                var cleanName = rawName.removePrefix("./").removePrefix("/")
                if (cleanName.startsWith("data/data/com.termux/files/usr/")) {
                    cleanName = cleanName.removePrefix("data/data/com.termux/files/usr/")
                } else if (cleanName.startsWith("usr/")) {
                    cleanName = cleanName.removePrefix("usr/")
                }

                if (cleanName.isNotBlank() && !cleanName.startsWith("__MACOSX")) {
                    val targetFile = File(outputDir, cleanName)

                    if (entry.isDirectory || cleanName.endsWith("/")) {
                        targetFile.mkdirs()
                    } else if (entry.isSymbolicLink || entry.isLink) {
                        val linkTarget = entry.linkName
                        if (!linkTarget.isNullOrBlank()) {
                            targetFile.parentFile?.mkdirs()
                            try {
                                if (targetFile.exists() || java.nio.file.Files.isSymbolicLink(targetFile.toPath())) {
                                    targetFile.delete()
                                }
                                android.system.Os.symlink(linkTarget, targetFile.absolutePath)
                            } catch (_: Exception) {
                                try {
                                    val resolvedTarget = if (linkTarget.startsWith("/")) {
                                        var cleanTarget = linkTarget.removePrefix("./").removePrefix("/")
                                        if (cleanTarget.startsWith("data/data/com.termux/files/usr/")) {
                                            cleanTarget = cleanTarget.removePrefix("data/data/com.termux/files/usr/")
                                        } else if (cleanTarget.startsWith("usr/")) {
                                            cleanTarget = cleanTarget.removePrefix("usr/")
                                        }
                                        File(outputDir, cleanTarget)
                                    } else {
                                        File(targetFile.parentFile, linkTarget)
                                    }
                                    if (resolvedTarget.exists()) {
                                        resolvedTarget.copyTo(targetFile, overwrite = true)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } else {
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().buffered().use { fos ->
                            tarIn.copyTo(fos)
                        }

                        val isBin = targetFile.parentFile?.name == "bin" || targetFile.name == "node" || targetFile.name.startsWith("python")
                        if (isBin) {
                            repairAndPatchElf(targetFile, isBinary = true)
                            targetFile.setReadable(true, false)
                            targetFile.setExecutable(true, false)
                        } else if (targetFile.parentFile?.name == "lib" || targetFile.name.endsWith(".so") || targetFile.name.contains(".so.")) {
                            repairAndPatchElf(targetFile, isBinary = false)
                            targetFile.setReadable(true, false)
                            targetFile.setExecutable(true, false)
                        }
                    }
                }
                entry = tarIn.nextEntry as? org.apache.commons.compress.archivers.tar.TarArchiveEntry
            }
        }
    }

    fun buildExecutionCommand(
        runtimeInfo: RuntimeInfo,
        entrypointFile: File,
        extraArgs: List<String> = emptyList()
    ): List<String> {
        val exe = runtimeInfo.executable
        val baseCmd = listOf(exe.absolutePath, entrypointFile.absolutePath)
        return if (extraArgs.isNotEmpty()) baseCmd + extraArgs else baseCmd
    }

    companion object {
        private const val TAG = "RuntimeManager"
    }
}
