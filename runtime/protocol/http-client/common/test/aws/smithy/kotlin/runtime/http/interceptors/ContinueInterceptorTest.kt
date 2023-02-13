/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.net.Url
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class ContinueInterceptorTest {
    private fun context(request: HttpRequest) = object : ProtocolRequestInterceptorContext<Any, HttpRequest> {
        override val protocolRequest: HttpRequest = request
        override val executionContext: ExecutionContext get() = fail("Shouldn't have invoked `executionContext`")
        override val request: Any get() = fail("Shouldn't have invoked `request`")
    }

    private fun request(contentLength: Long?) = HttpRequest {
        method = HttpMethod.POST
        url(Url.parse("https://localhost"))
        header("foo", "bar")
        body = object : HttpBody.SourceContent() {
            override val contentLength: Long? = contentLength
            override fun readFrom(): SdkSource = fail("Shouldn't have invoked `readFrom`")
        }
    }

    @Test
    fun testInterceptorSmallBody() = runTest {
        val input = request(50)
        val interceptor = ContinueInterceptor(100)
        val output = interceptor.modifyBeforeSigning(context(input))
        assertEquals("bar", output.headers["foo"])
        assertNull(output.headers["Expect"])
    }

    @Test
    fun testInterceptorLargeBody() = runTest {
        val input = request(150)
        val interceptor = ContinueInterceptor(100)
        val output = interceptor.modifyBeforeSigning(context(input))
        assertEquals("bar", output.headers["foo"])
        assertEquals("100-continue", output.headers["Expect"])
    }

    @Test
    fun testInterceptorUnknownLengthBody() = runTest {
        val input = request(null)
        val interceptor = ContinueInterceptor(100)
        val output = interceptor.modifyBeforeSigning(context(input))
        assertEquals("bar", output.headers["foo"])
        assertEquals("100-continue", output.headers["Expect"])
    }
}
