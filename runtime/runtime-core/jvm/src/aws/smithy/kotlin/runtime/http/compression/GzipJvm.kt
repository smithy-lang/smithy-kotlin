/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.compression

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.GzipByteReadChannel
import aws.smithy.kotlin.runtime.io.GzipSdkSource
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * The gzip compression algorithm.
 * Used to compress http requests.
 *
 * See: https://en.wikipedia.org/wiki/Gzip
 */
@InternalApi
public actual class Gzip actual constructor() : CompressionAlgorithm {

    actual override val id: String = "gzip"
    actual override val contentEncoding: String = "gzip"

    actual override fun compressBytes(bytes: ByteArray): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)

        gzipOutputStream.write(bytes)
        gzipOutputStream.close()

        val compressedBody = byteArrayOutputStream.toByteArray()
        byteArrayOutputStream.close()

        return compressedBody
    }

    actual override fun compressSdkSource(source: SdkSource, bytesToRead: Long?): SdkSource = GzipSdkSource(source, bytesToRead)

    actual override fun compressSdkByteReadChannel(channel: SdkByteReadChannel): SdkByteReadChannel = GzipByteReadChannel(channel)
}
