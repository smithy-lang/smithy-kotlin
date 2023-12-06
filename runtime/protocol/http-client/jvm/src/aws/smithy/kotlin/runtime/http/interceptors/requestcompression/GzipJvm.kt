/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.GzipByteReadChannel
import aws.smithy.kotlin.runtime.io.GzipSdkSource
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

    actual override fun compress(request: HttpRequest): HttpRequest {
        val compressedRequest = request.toBuilder()
        val uncompressedBody = request.body

        compressedRequest.body = when (uncompressedBody) {
            is HttpBody.SourceContent -> GzipSdkSource(uncompressedBody.readFrom(), uncompressedBody.contentLength).toHttpBody()
            is HttpBody.ChannelContent -> GzipByteReadChannel(uncompressedBody.readFrom()).toHttpBody()
            is HttpBody.Bytes -> compressBytes(uncompressedBody.bytes()).toHttpBody()
            is HttpBody.Empty -> uncompressedBody
            else -> throw IllegalStateException("HttpBody type '$uncompressedBody' is not supported")
        }

        compressedRequest.headers.append("Content-Encoding", contentEncoding)

        return compressedRequest.build()
    }

    public fun compressBytes(bytes: ByteArray): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)

        gzipOutputStream.write(bytes)
        gzipOutputStream.close()

        val compressedBody = byteArrayOutputStream.toByteArray()
        byteArrayOutputStream.close()

        return compressedBody
    }
}
