package com.box.android.core.runtime

import java.io.File

enum class RuntimeType(val identifier: String, val executableName: String) {
    PYTHON("python", "python"),
    NODEJS("node", "node")
}

data class RuntimeInfo(
    val id: String,
    val type: RuntimeType,
    val version: String,
    val executable: File,
    val binDir: File? = executable.parentFile,
    val libDir: File? = executable.parentFile?.parentFile?.let { File(it, "lib") },
    val isInstalled: Boolean = true
)
