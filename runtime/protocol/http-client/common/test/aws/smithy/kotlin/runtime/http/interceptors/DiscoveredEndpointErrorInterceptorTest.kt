/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.client.ResponseInterceptorContext
import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.client.operationName
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DiscoveredEndpointErrorInterceptorTest {
    @Test
    fun testCacheInvalidation() = runTest {
        var actualEc: ExecutionContext? = null

        val e = BadEndpointException()
        val context = context(Result.failure(e))
        val interceptor = DiscoveredEndpointErrorInterceptor(BadEndpointException::class) { actualEc = it }

        val result = interceptor.modifyBeforeAttemptCompletion(context)
        assertEquals(e, result.exceptionOrNull())
        assertTrue(e.sdkErrorMetadata.attributes[ErrorMetadata.Retryable])

        assertEquals("test", actualEc!!.operationName)
    }

    @Test
    fun testUnrelatedException() = runTest {
        var actualEc: ExecutionContext? = null

        val e = GenericServiceException()
        val context = context(Result.failure(e))
        val interceptor = DiscoveredEndpointErrorInterceptor(BadEndpointException::class) { actualEc = it }

        val result = interceptor.modifyBeforeAttemptCompletion(context)
        assertEquals(e, result.exceptionOrNull())

        assertNull(actualEc)
    }

    @Test
    fun testNonException() = runTest {
        var actualEc: ExecutionContext? = null

        val value = "foo"
        val context = context(Result.success(value))
        val interceptor = DiscoveredEndpointErrorInterceptor(BadEndpointException::class) { actualEc = it }

        val result = interceptor.modifyBeforeAttemptCompletion(context)
        assertEquals(value, result.getOrNull())

        assertNull(actualEc)
    }
}

private fun context(response: Result<Any>) =
    object : ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?> {
        override val executionContext = ExecutionContext.build { attributes[SdkClientOption.OperationName] = "test" }
        override val request = Unit
        override val response = response
        override val protocolRequest = HttpRequest { }
        override val protocolResponse = null
    }

private class BadEndpointException : ServiceException()
private class GenericServiceException : ServiceException()
