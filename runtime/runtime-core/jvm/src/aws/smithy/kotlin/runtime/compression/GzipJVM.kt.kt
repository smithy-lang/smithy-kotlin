/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.content.ByteStream
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
public actual class Gzip : CompressionAlgorithm {
    override val id: String = "gzip"
    override val contentEncoding: String = "gzip"

    override fun compress(stream: ByteStream): ByteStream =
        when (stream) {
            is ByteStream.Buffer ->
                object : ByteStream.Buffer() {
                    override fun bytes(): ByteArray {
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)

                        gzipOutputStream.write(stream.bytes())
                        gzipOutputStream.close()

                        val compressedBody = byteArrayOutputStream.toByteArray()
                        byteArrayOutputStream.close()

                        return compressedBody
                    }
                    override val contentLength: Long? = null
                }
            is ByteStream.ChannelStream ->
                object : ByteStream.ChannelStream() {
                    override fun readFrom(): SdkByteReadChannel = GzipByteReadChannel(stream.readFrom())
                    override val contentLength: Long? = null
                    override val isOneShot: Boolean = stream.isOneShot
                }
            is ByteStream.SourceStream ->
                object : ByteStream.SourceStream() {
                    override fun readFrom(): SdkSource = GzipSdkSource(stream.readFrom())
                    override val contentLength: Long? = null
                    override val isOneShot: Boolean = stream.isOneShot
                }
        }
}
