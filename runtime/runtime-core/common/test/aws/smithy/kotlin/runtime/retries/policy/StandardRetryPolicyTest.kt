/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.policy

import aws.smithy.kotlin.runtime.*
import kotlin.test.Test
import kotlin.test.assertEquals

class StandardRetryPolicyTest {
    private fun test(ex: Throwable): RetryDirective = StandardRetryPolicy().evaluate(Result.failure(ex))

    @Test
    fun testSuccess() {
        val result = StandardRetryPolicy().evaluate(Result.success("Literally any value will work!"))
        assertEquals(RetryDirective.TerminateAndSucceed, result)
    }

    @Test
    fun testThrottling() {
        val ex = SdkBaseException()
        ex.sdkErrorMetadata.attributes[ErrorMetadata.ThrottlingError] = true
        val result = test(ex)
        assertEquals(RetryDirective.RetryError(RetryErrorType.Throttling), result)
    }

    @Test
    fun testServerSideException() {
        val ex = ServiceException()
        ex.sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true
        ex.sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ServiceException.ErrorType.Server
        val result = test(ex)
        assertEquals(RetryDirective.RetryError(RetryErrorType.ServerSide), result)
    }

    @Test
    fun testClientSideException() {
        val ex = ServiceException()
        ex.sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true
        ex.sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ServiceException.ErrorType.Client
        val result = test(ex)
        assertEquals(RetryDirective.RetryError(RetryErrorType.ClientSide), result)
    }

    @Test
    fun testModeledThrottlingServiceException() {
        // A modeled @retryable(throttling = true) error is retried as throttling, whatever its error type
        listOf(ServiceException.ErrorType.Client, ServiceException.ErrorType.Server).forEach { errorType ->
            val ex = ServiceException()
            ex.sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true
            ex.sdkErrorMetadata.attributes[ErrorMetadata.ThrottlingError] = true
            ex.sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = errorType
            val result = test(ex)
            assertEquals(RetryDirective.RetryError(RetryErrorType.Throttling), result, "Unexpected result for $errorType")
        }
    }

    @Test
    fun testModeledThrottlingServiceExceptionWhenErrorTypeTakesPrecedence() {
        val policy = object : StandardRetryPolicy() {
            override val throttlingOverridesErrorType = false
        }

        val expected = mapOf(
            ServiceException.ErrorType.Client to RetryErrorType.ClientSide,
            ServiceException.ErrorType.Server to RetryErrorType.ServerSide,
            ServiceException.ErrorType.Unknown to RetryErrorType.Throttling,
        )

        expected.forEach { (errorType, retryErrorType) ->
            val ex = ServiceException()
            ex.sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true
            ex.sdkErrorMetadata.attributes[ErrorMetadata.ThrottlingError] = true
            ex.sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = errorType
            val result = policy.evaluate(Result.failure(ex))
            assertEquals(RetryDirective.RetryError(retryErrorType), result, "Unexpected result for $errorType")
        }
    }

    @Test
    fun testRetryableClientException() {
        val ex = ClientException()
        ex.sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true
        val result = test(ex)
        assertEquals(RetryDirective.RetryError(RetryErrorType.ClientSide), result)
    }

    @Test
    fun testNonRetryableClientException() {
        val result = test(ClientException())
        assertEquals(RetryDirective.TerminateAndFail, result)
    }

    @Test
    fun testUnknownException() {
        val result = test(IllegalArgumentException())
        assertEquals(RetryDirective.TerminateAndFail, result)
    }
}
