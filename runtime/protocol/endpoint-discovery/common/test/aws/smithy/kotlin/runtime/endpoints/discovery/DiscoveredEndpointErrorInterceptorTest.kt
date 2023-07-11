/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.endpoints.discovery

import aws.smithy.kotlin.runtime.ErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.client.ResponseInterceptorContext
import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.client.operationName
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.util.get
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveredEndpointErrorInterceptorTest {
    @Test
    fun testCacheInvalidation() = runTest {
        val depResolver = mockk<DiscoveredEndpointResolver>()
        val slot = slot<ExecutionContext>()
        coEvery { depResolver.invalidate(capture(slot)) } just Runs

        val e = BadEndpointException()
        val context = context(Result.failure(e))
        val interceptor = DiscoveredEndpointErrorInterceptor(BadEndpointException::class, depResolver)

        val result = interceptor.modifyBeforeAttemptCompletion(context)
        assertEquals(e, result.exceptionOrNull())
        assertTrue(e.sdkErrorMetadata.attributes[ErrorMetadata.Retryable])

        coVerify { depResolver.invalidate(any()) }
        assertEquals("test", slot.captured.operationName)
    }

    @Test
    fun testUnrelatedException() = runTest {
        val depResolver = mockk<DiscoveredEndpointResolver>()

        val e = GenericServiceException()
        val context = context(Result.failure(e))
        val interceptor = DiscoveredEndpointErrorInterceptor(BadEndpointException::class, depResolver)

        val result = interceptor.modifyBeforeAttemptCompletion(context)
        assertEquals(e, result.exceptionOrNull())

        coVerify(exactly = 0) { depResolver.invalidate(any()) }
    }

    @Test
    fun testNonException() = runTest {
        val depResolver = mockk<DiscoveredEndpointResolver>()

        val value = "foo"
        val context = context(Result.success(value))
        val interceptor = DiscoveredEndpointErrorInterceptor(BadEndpointException::class, depResolver)

        val result = interceptor.modifyBeforeAttemptCompletion(context)
        assertEquals(value, result.getOrNull())

        coVerify(exactly = 0) { depResolver.invalidate(any()) }
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
