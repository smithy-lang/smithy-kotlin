/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RequiresLengthInterceptorTest {
    private fun context(request: HttpRequest) = object : ProtocolRequestInterceptorContext<Any, HttpRequest> {
        override val protocolRequest: HttpRequest = request
        override val executionContext: ExecutionContext get() = error("Shouldn't be called")
        override val request: Any get() = error("Shouldn't be called")
    }

    private fun request(contentLength: Long?) = HttpRequest {
        method = HttpMethod.PUT
        url(Url.parse("https://localhost"))
        body = object : HttpBody.SourceContent() {
            override val contentLength: Long? = contentLength
            override fun readFrom(): SdkSource = error("Shouldn't be called")
        }
    }

    @Test
    fun testThrowsWhenContentLengthNull() = runTest {
        val req = request(null)
        val interceptor = RequiresLengthInterceptor()
        assertFailsWith<ClientException> {
            interceptor.modifyBeforeSigning(context(req))
        }
    }

    @Test
    fun testPassesWhenContentLengthSet() = runTest {
        val req = request(1024)
        val interceptor = RequiresLengthInterceptor()
        val result = interceptor.modifyBeforeSigning(context(req))
        assertSame(req, result)
    }
}
