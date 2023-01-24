/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.hashing.md5
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.encodeBase64String

/**
 * Set the `Content-MD5` header based on the current payload
 * See:
 *   - https://awslabs.github.io/smithy/1.0/spec/core/behavior-traits.html#httpchecksumrequired-trait
 *   - https://datatracker.ietf.org/doc/html/rfc1864.html
 */
@InternalApi
public class Md5ChecksumInterceptor<I>(
    private val block: ((input: I) -> Boolean)? = null,
) : HttpInterceptor {

    private var shouldRun: Boolean = false

    override fun readAfterSerialization(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        shouldRun = block?.let {
            val input = context.request as I
            it(input)
        } ?: true
    }

    override suspend fun modifyBeforeRetryLoop(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        if (!shouldRun) {
            println("should not run, skipping")
            return context.protocolRequest
        }

        val checksum = when (val body = context.protocolRequest.body) {
            is HttpBody.Bytes -> body.bytes().md5().encodeBase64String()
            else -> null
        }

        return checksum?.let {
            val req = context.protocolRequest.toBuilder()
            req.header("Content-MD5", it)
            req.build()
        } ?: context.protocolRequest
    }
}
