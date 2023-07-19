/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smthy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.*

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

private class LengthValidatingSource(
    private val source: SdkSource,
    private val expectedContentLength: Long,
) : SdkSource by source {
    var bytesReceived = 0L
    override fun read(sink: SdkBuffer, limit: Long): Long = source.read(sink, limit).also {
        if (it == -1L) {
            validateContentLength(expectedContentLength, bytesReceived)
        } else {
            bytesReceived += it
        }
    }
}

private class LengthValidatingByteReadChannel(
    private val chan: SdkByteReadChannel,
    private val expectedContentLength: Long,
) : SdkByteReadChannel by chan {
    var bytesReceived = 0L

    override suspend fun read(sink: SdkBuffer, limit: Long): Long = chan.read(sink, limit).also {
        if (it == -1L) {
            validateContentLength(bytesReceived, expectedContentLength)
        } else {
            bytesReceived += it
        }
    }
}

private fun validateContentLength(expected: Long, actual: Long) {
    check(expected == actual) { "Total bytes consumed ($actual) does not match expected ($expected)." }
}
