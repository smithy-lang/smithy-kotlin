/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.compression

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.io.decompressGzipBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class GzipTest {
    @Test
    fun testCompress() = runTest {
        val payload = "Hello World".encodeToByteArray()
        val byteStream = ByteStream.fromBytes(payload)

        val compressed = Gzip()
            .compress(byteStream)
            .toByteArray()

        val decompressedBytes = decompressGzipBytes(compressed)
        assertContentEquals(payload, decompressedBytes)
    }

    @Test
    fun testCompressEmptyByteArray() = runTest {
        val payload = ByteArray(0)
        val byteStream = ByteStream.fromBytes(payload)

        val compressed = Gzip()
            .compress(byteStream)
            .toByteArray()

        val decompressedBytes = decompressGzipBytes(compressed)

        assertContentEquals(payload, decompressedBytes)
    }
}
