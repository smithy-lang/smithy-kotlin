/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.*

internal actual object SystemDefaultProvider : PlatformProvider {
    actual override fun getAllEnvVars(): Map<String, String> = System.getenv()

    /**
     * Get an environment variable or null
     */
    actual override fun getenv(key: String): String? = System.getenv()[key]

    actual override val isJvm: Boolean = true
    actual override val isAndroid: Boolean by lazy { isAndroid() }
    actual override val isBrowser: Boolean = false
    actual override val isNode: Boolean = false
    actual override val isNative: Boolean = false

    actual override fun osInfo(): OperatingSystem = getOsInfo()

    /**
     * Read the contents of a file as a [String] or return null on any IO error.
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem.
     * @return contents of file or null if error (file does not exist, etc.)
     */
    actual override suspend fun readFileOrNull(path: String): ByteArray? = try {
        withContext(Dispatchers.IO) {
            File(path).readBytes()
        }
    } catch (e: IOException) {
        null
    }

    actual override suspend fun writeFile(path: String, data: ByteArray) {
        withContext(Dispatchers.IO) {
            File(path).writeBytes(data)
        }
    }

    actual override fun fileExists(path: String): Boolean = File(path).exists()

    public suspend fun readFileOrNull(path: Path): ByteArray? = readFileOrNull(path.toAbsolutePath().toString())
    public suspend fun readFileOrNull(file: File): ByteArray? = readFileOrNull(file.absolutePath)

    actual override fun getAllProperties(): Map<String, String> = System
        .getProperties()
        .entries
        .associate { (key, value) -> key.toString() to value.toString() }

    /**
     * Get a system property or null
     *
     * @param key name of environment variable
     * @return value of system property or null if undefined or platform does not support properties
     */
    actual override fun getProperty(key: String): String? = System.getProperty(key)

    /**
     * return the platform-specific file path separator char.  Eg on Linux a path may be '/root` and the path
     * segment char is '/'.
     */
    actual override val filePathSeparator: String by lazy { File.separator }
}

private fun isAndroid(): Boolean = try {
    Class.forName("android.os.Build")
    true
} catch (ex: ClassNotFoundException) {
    false
}

private fun normalize(value: String): String = value.lowercase(Locale.US).replace(Regex("[^a-z0-9+]"), "")

private fun getOsInfo(): OperatingSystem {
    val name = normalize(System.getProperty("os.name"))

    val family = when {
        isAndroid() -> OsFamily.Android
        name.contains("windows") -> OsFamily.Windows
        name.contains("linux") -> OsFamily.Linux
        name.contains("macosx") -> OsFamily.MacOs
        else -> OsFamily.Unknown
    }

    val version = runCatching { System.getProperty("os.version") }.getOrNull()

    return OperatingSystem(family, version)
}
