/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.PlannedRemoval
import aws.smithy.kotlin.runtime.io.IOException
import aws.smithy.kotlin.runtime.io.internal.SdkDispatchers
import kotlinx.cinterop.*
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
public abstract class SystemDefaultProviderBase : PlatformProvider {
    override fun getenv(key: String): String? = platform.posix.getenv(key)?.toKString()

    // Catches Exception rather than IOException because kotlinx-io and okio throw their own IOException types on Native
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    override suspend fun readFileOrNull(path: String): ByteArray? = withContext(SdkDispatchers.IO) {
        try {
            readOrNull(path, readAll = true, mustExist = false)
        } catch (_: Exception) {
            null
        }
    }

    @OptIn(PlannedRemoval::class)
    @Deprecated("Use pathExists() instead", replaceWith = ReplaceWith("pathExists(path)"))
    @Suppress("DEPRECATION")
    override fun fileExists(path: String): Boolean = exists(path)

    @OptIn(PlannedRemoval::class)
    @Deprecated(
        "Blocks the calling thread. Use writeFile() instead",
        replaceWith = ReplaceWith("writeFile(path, data, writeType, mustExist, permissions)"),
    )
    override fun write(path: String, data: ByteArray, writeType: WriteType, mustExist: Boolean, permissions: String?) {
        val exists = SystemFileSystem.exists(Path(path))
        if (mustExist && !exists) throw IOException("$path does not exist and mustExist is set to true")

        if (!exists && permissions != null && osInfo().family != OsFamily.Windows) {
            val fd = open(path, O_CREAT or O_WRONLY, permissions.toInt(8))
            if (fd == -1) throw IOException("Cannot create file: $path")
            close(fd)
        }

        val mode = when (writeType) {
            is WriteType.OFFSET -> if (exists) "r+b" else "wb"
            is WriteType.APPEND -> "ab"
            is WriteType.OVERWRITE -> "wb"
        }

        val file = fopen(path, mode) ?: throw IOException("Cannot open file for writing: $path")
        try {
            if (writeType is WriteType.OFFSET) {
                // Handles offset being int or long depending on platform with convert
                @OptIn(UnsafeNumber::class)
                fseek(file, writeType.offset.convert(), SEEK_SET)
            }
            val wc = fwrite(data.refTo(0), 1uL, data.size.toULong(), file)
            if (wc != data.size.toULong()) {
                throw IOException("Failed to write all bytes to file $path, expected ${data.size}, wrote $wc")
            }
        } finally {
            fclose(file)
        }
    }

    override val isJvm: Boolean = false
    override val isAndroid: Boolean = false
    override val isBrowser: Boolean = false
    override val isNode: Boolean = false
    override val isNative: Boolean = true

    // Kotlin/Native doesn't have system properties
    override fun getAllProperties(): Map<String, String> = emptyMap()
    override fun getProperty(key: String): String? = null
}
