/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.toByteStream
import aws.smithy.kotlin.runtime.http.toHttpBody

/**
 * Compresses a Http Request and appends content encoding header.
 */
public fun CompressionAlgorithm.compressRequest(request: HttpRequest): HttpRequest {
    val stream = request.body.toByteStream() ?: return request

    val compressedRequest = request.toBuilder()

    val compressedStream = compress(stream)
    compressedRequest.body = compressedStream.toHttpBody()

    compressedRequest.headers.append("Content-Encoding", contentEncoding)

    return compressedRequest.build()
}
