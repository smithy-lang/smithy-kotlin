/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

public actual class Gzip actual constructor() : CompressionAlgorithm {
    actual override val id: String
        get() = "gzip"

    actual override suspend fun compress(stream: HttpBody): HttpBody = when (stream) {
        is HttpBody.SourceContent -> GzipSdkSource(stream.readFrom()).toHttpBody()
        is HttpBody.ChannelContent -> GzipByteReadChannel(stream.readFrom()).toHttpBody()
        is HttpBody.Bytes -> compressByteArray(stream.bytes())
        is HttpBody.Empty -> stream
        else -> throw ClientException("HttpBody type is not supported")
    }
}

internal fun compressByteArray(bytes: ByteArray): HttpBody {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)

    gzipOutputStream.write(bytes)
    gzipOutputStream.close()

    val compressedBody = byteArrayOutputStream.toByteArray().toHttpBody()
    byteArrayOutputStream.close()

    return compressedBody
}

internal class GzipSdkSource(
    private val source: SdkSource,
) : SdkSource by source {
    private val gzipBuffer = SdkBuffer()
    private val gzipOutputStream = GZIPOutputStream(gzipBuffer.outputStream())

    override fun read(sink: SdkBuffer, limit: Long): Long { // TODO: Fix this
        // Read "limit" bytes into byte array
        val converter = SdkBuffer()
        val bytesRead = source.read(converter, limit)
        if (bytesRead == -1L) source.close()
        val byteArray = converter.readByteArray()
        converter.close()

        // Pass byteArray to gzip
        gzipOutputStream.write(byteArray)

        // Extract compressed byteArray into function sink
        gzipBuffer.readAll(sink)
        if (bytesRead == -1L) {
            gzipOutputStream.close()
            gzipBuffer.close()
        }

        // Returns amount of bytes read from source
        return bytesRead
    }
}

internal class GzipByteReadChannel(
    private val source: SdkByteReadChannel,
) : SdkByteReadChannel by source {
    private val gzipBuffer = SdkBuffer()
    private val gzipOutputStream = GZIPOutputStream(gzipBuffer.outputStream())

    override suspend fun read(sink: SdkBuffer, limit: Long): Long { // TODO: Fix this
        // Read "limit" bytes into byte array
        val converter = SdkBuffer()
        val bytesRead = source.read(converter, limit)
        val byteArray = converter.readByteArray()
        converter.close()

        // Pass byteArray to gzip
        gzipOutputStream.write(byteArray)

        // Extract compressed byteArray into function sink
        gzipBuffer.readAll(sink)
        if (bytesRead == -1L) {
            gzipOutputStream.close()
            gzipBuffer.close()
        }

        // Returns amount of bytes read from source
        return bytesRead
    }
}
