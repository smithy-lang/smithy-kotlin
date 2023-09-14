/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.*
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.response.copy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.IllegalStateException
import kotlin.test.*

/**
 * Sanity test interceptor behavior w.r.t read/write hooks from beginning to end.
 *
 * This test sets up 3 interceptors
 * 1. Read only observer hook that asserts the starting value at every hook
 * 2. Write only hook that modifies at every chance possible
 * 3. Read only observer hook that asserts any modified value
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HttpInterceptorTest {

    class TestWriteHook : HttpInterceptor {
        override suspend fun modifyBeforeSerialization(context: RequestInterceptorContext<Any>): Any {
            val input = assertIs<TestInput>(context.request)
            return input.copy(value = "modified")
        }

        override suspend fun modifyBeforeRetryLoop(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
            val builder = context.protocolRequest.toBuilder()
            assertEquals(context.protocolRequest.headers["req-header"], "modified")
            builder.headers["req-header"] = "modify-before-retry-loop"
            return builder.build()
        }

        override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
            val builder = context.protocolRequest.toBuilder()
            builder.headers["req-header"] = "modify-before-signing"
            return builder.build()
        }

        override suspend fun modifyBeforeTransmit(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
            val builder = context.protocolRequest.toBuilder()
            builder.headers["req-header"] = "modify-before-transmit"
            return builder.build()
        }

        override suspend fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>): HttpResponse {
            val modifiedHeaders = Headers {
                appendAll(context.protocolResponse.headers)
                set("resp-header", "modify-before-deserialization")
            }
            return context.protocolResponse.copy(headers = modifiedHeaders)
        }

        override suspend fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
            val output = assertIs<TestOutput>(context.response.getOrThrow())
            assertEquals("deserialized", output.value)
            val modified = output.copy(value = "modified")
            return Result.success(modified)
        }

        override suspend fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
            val output = assertIs<TestOutput>(context.response.getOrThrow())
            assertNotNull(context.protocolRequest)
            assertNotNull(context.protocolResponse)
            val modified = output.copy(value = "final")
            return Result.success(modified)
        }
    }

    class TestReadHook : HttpInterceptor {
        override fun readBeforeExecution(context: RequestInterceptorContext<Any>) {
            val input = assertIs<TestInput>(context.request)
            assertEquals("initial", input.value)
        }

        // <modify hook>

        override fun readBeforeSerialization(context: RequestInterceptorContext<Any>) {
            val input = assertIs<TestInput>(context.request)
            assertEquals("modified", input.value)
        }

        override fun readAfterSerialization(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            assertIs<TestInput>(context.request)
            assertEquals(context.protocolRequest.headers["req-header"], "modified")
        }

        // <modify hook>

        override fun readBeforeAttempt(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            assertIs<TestInput>(context.request)
            // should always start with the original on each attempt
            assertEquals(context.protocolRequest.headers["req-header"], "modify-before-retry-loop")
        }

        // <modify hook>

        override fun readBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            assertIs<TestInput>(context.request)
            assertEquals(context.protocolRequest.headers["req-header"], "modify-before-signing")
        }

        override fun readAfterSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            assertIs<TestInput>(context.request)
        }

        // <modify hook>
        override fun readBeforeTransmit(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            assertIs<TestInput>(context.request)
            assertEquals(context.protocolRequest.headers["req-header"], "modify-before-transmit")
        }

        override fun readAfterTransmit(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
            assertIs<TestInput>(context.request)
            assertEquals("transmit", context.protocolResponse.headers["resp-header"])
        }

        // <modify hook>
        override fun readBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
            assertIs<TestInput>(context.request)
            assertEquals(context.protocolResponse.headers["resp-header"], "modify-before-deserialization")
        }

        override fun readAfterDeserialization(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse>) {
            assertIs<TestInput>(context.request)
            val output = assertIs<TestOutput>(context.response.getOrThrow())
            assertEquals("deserialized", output.value)
        }

        // <modify hook>
        override fun readAfterAttempt(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>) {
            assertIs<TestInput>(context.request)
            val output = assertIs<TestOutput>(context.response.getOrThrow())
            assertEquals("modified", output.value)
        }

        // <modify hook>
        override fun readAfterExecution(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>) {
            assertIs<TestInput>(context.request)
            val output = assertIs<TestOutput>(context.response.getOrThrow())
            assertEquals("final", output.value)
        }
    }

    @Test
    fun testInterceptorModifications() = runTest {
        val serialized = HttpRequestBuilder().apply {
            headers["req-header"] = "modified"
        }

        val op = newTestOperation<TestInput, TestOutput>(serialized, TestOutput("deserialized"))
        val interceptors = listOf(
            TestReadHook(),
            TestWriteHook(),
            TestReadHook(),
        )
        op.interceptors.addAll(interceptors)
        val client = newMockHttpClient {
            responseHeaders = Headers { set("resp-header", "transmit") }
        }

        val output = op.roundTrip(client, TestInput("initial"))

        assertEquals("final", output.value)
    }

    @Test
    fun testInterceptorModificationsWithRetries() = runTest {
        val serialized = HttpRequestBuilder().apply {
            headers["req-header"] = "modified"
        }

        val op = newTestOperation<TestInput, TestOutput>(serialized, TestOutput("deserialized"))
        val interceptors = listOf(
            TestReadHook(),
            TestWriteHook(),
        )
        op.interceptors.addAll(interceptors)

        val client = newMockHttpClient {
            failOnAttempts = setOf(1)
            failWithRetryableError = true
            responseHeaders = Headers { set("resp-header", "transmit") }
        }

        val output = op.roundTrip(client, TestInput("initial"))

        assertEquals("final", output.value)
    }

    private suspend fun testMapFailure(interceptor: HttpInterceptor) {
        val serialized = HttpRequestBuilder()
        val op = newTestOperation<TestInput, TestOutput>(serialized, TestOutput("deserialized"))
        op.interceptors.add(interceptor)

        val client = newMockHttpClient {
            failOnAttempts = setOf(1)
            failWithRetryableError = false
        }

        val output = op.roundTrip(client, TestInput("initial"))
        assertEquals("ignore-failure", output.value)
    }

    @Test
    fun testMapFailureOnAttempt() = runTest {
        val interceptor = object : HttpInterceptor {
            override suspend fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
                assertTrue(context.response.isFailure)
                return Result.success(TestOutput("ignore-failure"))
            }
        }

        testMapFailure(interceptor)
    }

    @Test
    fun testMapFailureOnCompletion() = runTest {
        val interceptor = object : HttpInterceptor {
            override suspend fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
                assertTrue(context.response.isFailure)
                return Result.success(TestOutput("ignore-failure"))
            }
        }

        testMapFailure(interceptor)
    }

    @Test
    fun testReadAfterExecutionSuppressedException() = runTest {
        val interceptor = object : HttpInterceptor {
            override suspend fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
                assertTrue(context.response.isFailure)
                return super.modifyBeforeCompletion(context)
            }

            override fun readAfterExecution(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>) {
                throw IllegalStateException("modified exception")
            }
        }

        val serialized = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(serialized, Unit)
        op.interceptors.add(interceptor)

        val client = newMockHttpClient {
            failOnAttempts = setOf(1)
            failWithRetryableError = false
        }

        val ex = assertFailsWith<IllegalStateException> {
            op.roundTrip(client, Unit)
        }

        val cause = assertNotNull(ex.cause)
        assertEquals(1, cause.suppressed.size)
        assertIs<TestException>(cause.suppressed.last())
    }
}
