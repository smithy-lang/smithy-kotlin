/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.io.IOException
import aws.smithy.kotlin.runtime.io.internal.SdkDispatchers
import aws.smithy.platform.posix.get_environ_ptr
import kotlinx.cinterop.*
import kotlinx.coroutines.withContext
import platform.posix.*

internal actual object SystemDefaultProvider : PlatformProvider {
    actual override fun getAllEnvVars(): Map<String, String> = memScoped {
        val environ = get_environ_ptr()
        generateSequence(0) { it + 1 }
            .map { idx -> environ?.get(idx)?.toKString() }
            .takeWhile { it != null }
            .associate { env ->
                val parts = env?.split("=", limit = 2)
                check(parts?.size == 2) { "Environment entry \"$env\" is malformed" }
                parts[0] to parts[1]
            }
    }

    actual override fun getenv(key: String): String? = platform.posix.getenv(key)?.toKString()

    actual override val filePathSeparator: String
        get() = when (osInfo().family) {
            OsFamily.Windows -> "\\"
            else -> "/"
        }

    actual override suspend fun readFileOrNull(path: String): ByteArray? = withContext(SdkDispatchers.IO) {
        try {
            val file = fopen(path, "rb") ?: return@withContext null

            try {
                // Get file size
                fseek(file, 0L, SEEK_END)
                val size = ftell(file)
                fseek(file, 0L, SEEK_SET)

                // Read file content
                val buffer = ByteArray(size.toInt()).pin()
                val rc = fread(buffer.addressOf(0), 1uL, size.toULong(), file)
                if (rc == size.toULong()) buffer.get() else null
            } finally {
                fclose(file)
            }
        } catch (_: Exception) {
            null
        }
    }

    actual override suspend fun writeFile(path: String, data: ByteArray) = withContext(SdkDispatchers.IO) {
        val file = fopen(path, "wb") ?: throw IOException("Cannot open file for writing: $path")
        try {
            val wc = fwrite(data.refTo(0), 1uL, data.size.toULong(), file)
            if (wc != data.size.toULong()) {
                throw IOException("Failed to write all bytes to file $path, expected ${data.size.toLong()}, wrote $wc")
            }
        } finally {
            fclose(file)
        }
    }

    actual override fun fileExists(path: String): Boolean = access(path, F_OK) == 0

    actual override fun osInfo(): OperatingSystem = memScoped {
        val utsname = alloc<utsname>()
        uname(utsname.ptr)

        val sysName = utsname.sysname.toKString().lowercase()
        val version = utsname.release.toKString()
        val machine = utsname.machine.toKString().lowercase() // Helps differentiate Apple platforms

        val family = when {
            sysName.contains("darwin") -> {
                when {
                    machine.startsWith("iphone") -> OsFamily.Ios
                    // TODO Validate that iPadOS/tvOS/watchOS resolves correctly on each of these devices
                    machine.startsWith("ipad") -> OsFamily.IpadOs
                    machine.startsWith("tv") -> OsFamily.TvOs
                    machine.startsWith("watch") -> OsFamily.WatchOs
                    else -> OsFamily.MacOs
                }
            }
            sysName.contains("linux") -> OsFamily.Linux
            sysName.contains("windows") -> OsFamily.Windows
            else -> OsFamily.Unknown
        }

        return OperatingSystem(family, version)
    }

    actual override val isJvm: Boolean = false
    actual override val isAndroid: Boolean = false
    actual override val isBrowser: Boolean = false
    actual override val isNode: Boolean = false
    actual override val isNative: Boolean = true

    // Kotlin/Native doesn't have system properties
    actual override fun getAllProperties(): Map<String, String> = emptyMap()
    actual override fun getProperty(key: String): String? = null
}
