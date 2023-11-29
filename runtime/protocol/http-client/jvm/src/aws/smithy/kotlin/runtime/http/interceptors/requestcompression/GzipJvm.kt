/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.GzipByteReadChannel
import aws.smithy.kotlin.runtime.io.GzipSdkSource
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

public actual class Gzip actual constructor() : CompressionAlgorithm {
    actual override val id: String = "gzip"
    actual override val contentEncoding: String = "gzip"

    actual override suspend fun compress(request: HttpRequest): HttpRequest {
        val compressedRequest = request.toBuilder()
        val uncompressedBody = compressedRequest.body

        compressedRequest.body = when (uncompressedBody) {
            is HttpBody.SourceContent -> GzipSdkSource(uncompressedBody.readFrom()).toHttpBody()
            is HttpBody.ChannelContent -> GzipByteReadChannel(uncompressedBody.readFrom()).toHttpBody()
            is HttpBody.Bytes -> compressByteArray(uncompressedBody.bytes())
            is HttpBody.Empty -> uncompressedBody
            else -> throw ClientException("HttpBody type is not supported")
        }
        compressedRequest.headers.append("Content-Encoding", contentEncoding)

        return compressedRequest.build()
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
