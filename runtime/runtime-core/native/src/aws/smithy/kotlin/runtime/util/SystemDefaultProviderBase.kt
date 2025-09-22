/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.io.IOException
import aws.smithy.kotlin.runtime.io.internal.SdkDispatchers
import kotlinx.cinterop.*
import kotlinx.coroutines.withContext
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
public abstract class SystemDefaultProviderBase : PlatformProvider {
    override fun getenv(key: String): String? = platform.posix.getenv(key)?.toKString()

    override suspend fun readFileOrNull(path: String): ByteArray? = withContext(SdkDispatchers.IO) {
        try {
            val size: Long = memScoped {
                val statResult = alloc<stat>()
                if (stat(path, statResult.ptr) != 0) return@withContext null
                statResult.st_size
            }

            val file = fopen(path, "rb") ?: return@withContext null

            try {
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

    override suspend fun writeFile(path: String, data: ByteArray): Unit = withContext(SdkDispatchers.IO) {
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

    override fun fileExists(path: String): Boolean = access(path, F_OK) == 0

    override val isJvm: Boolean = false
    override val isAndroid: Boolean = false
    override val isBrowser: Boolean = false
    override val isNode: Boolean = false
    override val isNative: Boolean = true

    // Kotlin/Native doesn't have system properties
    override fun getAllProperties(): Map<String, String> = emptyMap()
    override fun getProperty(key: String): String? = null
}
