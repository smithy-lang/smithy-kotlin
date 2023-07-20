/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.*

/**
 * An interceptor which compares the `Content-Length` header value against the length of the returned body. Throws an
 * [IllegalStateException] if there is a mismatch.
 */
@InternalApi
public class ResponseLengthValidationInterceptor : HttpInterceptor {
    override suspend fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>): HttpResponse {
        val response = context.protocolResponse

        return response.headers["Content-Length"]?.let {
            response.copy(body = response.body.toLengthValidatingBody(it.toLong()))
        } ?: response
    }
}

@InternalApi
private fun HttpBody.toLengthValidatingBody(expectedContentLength: Long): HttpBody = when (this) {
    is HttpBody.SourceContent -> LengthValidatingSource(readFrom(), expectedContentLength).toHttpBody(contentLength)
    is HttpBody.ChannelContent -> LengthValidatingByteReadChannel(readFrom(), expectedContentLength).toHttpBody(contentLength)
    else -> throw ClientException("HttpBody type is not supported")
}

/**
 * An [SdkSource] which keeps track of how many bytes were consumed. After the underlying source is exhausted or too many
 * bytes are read, it compares the number of bytes received against the expected length.
 * @param source The underlying [SdkSource] to read from
 * @param expectedContentLength The expected content length as described by the `Content-Length` header
 * @throws [IllegalStateException] if there is a mismatch.
 */
private class LengthValidatingSource(
    private val source: SdkSource,
    private val expectedContentLength: Long,
) : SdkSource by source {
    var bytesReceived = 0L
    override fun read(sink: SdkBuffer, limit: Long): Long = source.read(sink, limit).also {
        if (it == -1L || bytesReceived > expectedContentLength) {
            validateContentLength(expectedContentLength, bytesReceived)
        } else {
            bytesReceived += it
        }
    }
}

/**
 * An [SdkByteReadChannel] which keeps track of how many bytes were consumed. After the underlying channel is exhausted
 * or too many bytes are read, it compares the number of bytes received against the expected length.
 * @param chan The underlying [SdkByteReadChannel] to read from
 * @param expectedContentLength The expected content length as described by the `Content-Length` header
 * @throws [IllegalStateException] if there is a mismatch.
 */
private class LengthValidatingByteReadChannel(
    private val chan: SdkByteReadChannel,
    private val expectedContentLength: Long,
) : SdkByteReadChannel by chan {
    var bytesReceived = 0L

    override suspend fun read(sink: SdkBuffer, limit: Long): Long = chan.read(sink, limit).also {
        if (chan.isClosedForRead || bytesReceived > expectedContentLength) {
            if (it != -1L) { bytesReceived += it }
            validateContentLength(expectedContentLength, bytesReceived)
        } else {
            bytesReceived += it
        }
    }
}

private fun validateContentLength(expected: Long, actual: Long) {
    check(expected == actual) { "Total bytes consumed ($actual) does not match expected ($expected)." }
}
