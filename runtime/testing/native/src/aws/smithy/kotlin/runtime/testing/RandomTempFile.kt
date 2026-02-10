/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.testing

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.random.Random

public actual class RandomTempFile actual constructor(
    sizeInBytes: Long,
    filename: String,
    private val binaryData: Boolean,
) {
    private val path: String = "${tmpDir()}/${currentTimeMillis()}-$filename"
    private val data: ByteArray

    init {
        data = if (binaryData) {
            Random.Default.nextBytes(sizeInBytes.toInt())
        } else {
            // Generate random ASCII printable characters
            ByteArray(sizeInBytes.toInt()) { Random.nextInt(32, 127).toByte() }
        }
        writeFile()
    }

    private fun writeFile() {
        val file = fopen(path, "wb") ?: error("Failed to create temp file: $path")
        try {
            data.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, data.size.toULong(), file)
            }
        } finally {
            fclose(file)
        }
    }

    public fun readBytes(): ByteArray = data

    public fun readText(): String = data.decodeToString()

    public fun delete(): Boolean {
        val result = remove(path)
        if (result != 0) {
            throw RuntimeException("Could not delete: $path")
        }
        return true
    }
}

private fun tmpDir(): String = getenv("TMPDIR")?.toKString() ?: "/tmp"

private fun currentTimeMillis(): Long {
    memScoped {
        val timeVal = alloc<timeval>()
        gettimeofday(timeVal.ptr, null)
        return timeVal.tv_sec * 1000L + timeVal.tv_usec / 1000L
    }
}

internal actual fun randomFilename(): String = Random.Default.nextInt().toString()
