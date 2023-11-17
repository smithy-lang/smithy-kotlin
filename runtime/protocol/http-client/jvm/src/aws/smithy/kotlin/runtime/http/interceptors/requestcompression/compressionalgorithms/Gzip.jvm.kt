/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.toByteStream
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.internal.toOkio
import java.util.zip.GZIPOutputStream

internal actual class Gzip : CompressionAlgorithm {
    actual override val id: String
        get() = "gzip"

    actual override suspend fun compress(stream: HttpBody): HttpBody {
        when(stream) {
            is HttpBody.SourceContent -> {
                GzipSdkSource(stream.readFrom())
            }
            is HttpBody.ChannelContent -> {

            }
            is HttpBody.Bytes -> {
                stream.toByteStream()
            }
            is HttpBody.Empty -> stream
        }
        TODO("Finish implementation ...")
    }
}


HttpBody.SourceContent -> object: HttpBody {
    override fun readFrom(): SdkSource = GzipSdkSource(stream.readFrom())
}

private class GzipByteReadChannel(
    private val delegate: SdkByteReadChannel,
): SdkByteReadChannel {

    val buffer = SdkBuffer()
    val gzipOS = GZIPOutputStream(buffer.outputStream())

    override val availableForRead: Int
        get() = TODO("Not yet implemented")
    override val isClosedForRead: Boolean
        get() = TODO("Not yet implemented")
    override val isClosedForWrite: Boolean
        get() = TODO("Not yet implemented")
    override val closedCause: Throwable?
        get() = TODO("Not yet implemented")

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        val tempBuff = SdkBuffer()
        delegate.read(tempBuff, 2)
        val bA = tempBuff.readByteArray()
        gzipOS.write(bA)
        buffer.read(sink, 2)
    }

    override fun cancel(cause: Throwable?): Boolean {
        TODO("Not yet implemented")
    }
}

private class GzipSdkSource( // runtime core / io | look at tests too
    private val delegate: SdkSource,
): SdkSource {

    val buffer = SdkBuffer()
    val gzipOS = GZIPOutputStream(buffer.outputStream())

    override fun read(sink: SdkBuffer, limit: Long): Long {
        val tempBuff = SdkBuffer()
        delegate.read(tempBuff, 2)
        val bA = tempBuff.readByteArray()
        gzipOS.write(bA)
        buffer.read(sink, 2)
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}

