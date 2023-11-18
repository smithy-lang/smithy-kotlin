/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

public actual class Gzip actual constructor(): CompressionAlgorithm {
    actual override val id: String
        get() = "gzip"

    actual override suspend fun compress(stream: HttpBody): HttpBody = when(stream) {
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
    private val delegate: SdkSource,
): SdkSource by delegate {
    private val buffer = SdkBuffer()
    private val gzipOS = GZIPOutputStream(buffer.outputStream())

    // TODO: Deal with edge cases and maybe change limits
    override fun read(sink: SdkBuffer, limit: Long): Long {
        val tempBuff = SdkBuffer()
        delegate.read(tempBuff, 1)
        val bA = tempBuff.readByteArray()
        gzipOS.write(bA)
        buffer.read(sink, 1)
        return -1
    }
}

internal class GzipByteReadChannel(
    private val delegate: SdkByteReadChannel,
): SdkByteReadChannel by delegate {
    private val buffer = SdkBuffer()
    private val gzipOS = GZIPOutputStream(buffer.outputStream())

    // TODO: Deal with edge cases and maybe change limits ... not entirely sure how this works yet
    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        val tempBuff = SdkBuffer()
        delegate.read(tempBuff, 1)
        val bA = tempBuff.readByteArray()
        gzipOS.write(bA)
        buffer.read(sink, 1)
        return -1
    }
}
