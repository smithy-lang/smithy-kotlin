/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.testing

import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.time.epochMilliseconds
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.random.Random

@OptIn(ExperimentalForeignApi::class)
public actual class RandomTempFile actual constructor(
    sizeInBytes: Long,
    filename: String,
    binaryData: Boolean,
) {
    private val tmpDir: String = getenv("TMPDIR")?.toKString() ?: "/tmp"
    private val path: String = "${tmpDir}/${Clock.System.now().epochMilliseconds}-$filename"

    private val data: ByteArray = if (binaryData) {
        Random.nextBytes(sizeInBytes.toInt())
    } else {
        // Generate random ASCII printable characters
        ByteArray(sizeInBytes.toInt()) { Random.nextInt(32, 127).toByte() }
    }

    init {
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
