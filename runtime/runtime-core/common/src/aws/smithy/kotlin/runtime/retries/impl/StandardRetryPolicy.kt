/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.SdkBaseException
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.ServiceException.*
import aws.smithy.kotlin.runtime.retries.RetryDirective
import aws.smithy.kotlin.runtime.retries.RetryErrorType
import aws.smithy.kotlin.runtime.retries.RetryPolicy

/**
 * A standard retry policy which attempts to derive information from the Smithy exception hierarchy.
 */
open class StandardRetryPolicy : RetryPolicy<Any?> {
    /**
     * Evaluate the given retry attempt.
     */
    override fun evaluate(result: Result<Any?>): RetryDirective = when {
        result.isSuccess -> RetryDirective.TerminateAndSucceed
        else -> evaluate(result.exceptionOrNull()!!)
    }

    private fun evaluate(ex: Throwable): RetryDirective =
        (ex as? SdkBaseException)?.run(::evaluateBaseException)
            ?: (ex as? ServiceException)?.run(::evaluateServiceException)
            ?: (ex as? ClientException)?.run { RetryDirective.RetryError(RetryErrorType.ClientSide) }
            ?: evaluateNonSdkException(ex)
            ?: evaluateOtherExceptions(ex)
            ?: RetryDirective.TerminateAndFail

    private fun evaluateBaseException(ex: SdkBaseException): RetryDirective? = with(ex.sdkErrorMetadata) {
        when {
            isThrottling -> RetryDirective.RetryError(RetryErrorType.Throttling)
            else -> null
        }
    }

    private fun evaluateServiceException(ex: ServiceException): RetryDirective? = with(ex.sdkErrorMetadata) {
        when {
            isRetryable && errorType == ErrorType.Server -> RetryDirective.RetryError(RetryErrorType.ServerSide)
            isRetryable && errorType == ErrorType.Client -> RetryDirective.RetryError(RetryErrorType.ClientSide)
            else -> null
        }
    }

    protected open fun evaluateOtherExceptions(ex: Throwable): RetryDirective? = null

    private fun evaluateNonSdkException(ex: Throwable): RetryDirective? =
        // TODO Write logic to find connection errors, timeouts, stream faults, etc.
        null
}
