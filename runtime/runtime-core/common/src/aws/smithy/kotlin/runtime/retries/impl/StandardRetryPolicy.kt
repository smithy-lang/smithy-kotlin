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
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * A standard retry policy which attempts to derive information from the Smithy exception hierarchy.
 */
open class StandardRetryPolicy : RetryPolicy<Any?> {
    companion object {
        val Default = StandardRetryPolicy()
    }

    private val evaluationStrategies = sequenceOf(
        EvaluationStrategy(::evaluateBaseException),
        EvaluationStrategy(::evaluateServiceException),
        EvaluationStrategy(::evaluateClientException),
        EvaluationStrategy(::evaluateNonSdkException),
        EvaluationStrategy(::evaluateOtherExceptions),
    )

    /**
     * Evaluate the given retry attempt.
     */
    override fun evaluate(result: Result<Any?>): RetryDirective = when {
        result.isSuccess -> RetryDirective.TerminateAndSucceed
        else -> evaluate(result.exceptionOrNull()!!)
    }

    private fun evaluate(ex: Throwable): RetryDirective =
        evaluationStrategies.firstNotNullOfOrNull { it.evaluate(ex) } ?: RetryDirective.TerminateAndFail

    private fun evaluateBaseException(ex: SdkBaseException): RetryDirective? = with(ex.sdkErrorMetadata) {
        when {
            isThrottling -> RetryDirective.RetryError(RetryErrorType.Throttling)
            else -> null
        }
    }

    private fun evaluateClientException(ex: ClientException): RetryDirective? = if (ex.sdkErrorMetadata.isRetryable) {
        RetryDirective.RetryError(RetryErrorType.ClientSide)
    } else {
        null
    }

    private fun evaluateServiceException(ex: ServiceException): RetryDirective? = with(ex.sdkErrorMetadata) {
        when {
            isRetryable && errorType == ErrorType.Server -> RetryDirective.RetryError(RetryErrorType.ServerSide)
            isRetryable && errorType == ErrorType.Client -> RetryDirective.RetryError(RetryErrorType.ClientSide)
            else -> null
        }
    }

    protected open fun evaluateOtherExceptions(ex: Throwable): RetryDirective? = null

    @Suppress("UNUSED_PARAMETER") // Until method is implemented
    private fun evaluateNonSdkException(ex: Throwable): RetryDirective? =
        // TODO Write logic to find connection errors, timeouts, stream faults, etc.
        null
}

private class EvaluationStrategy<T : Throwable>(val clazz: KClass<T>, private val evaluator: (T) -> RetryDirective?) {
    companion object {
        inline operator fun <reified T : Throwable> invoke(noinline evaluator: (T) -> RetryDirective?) =
            EvaluationStrategy(T::class, evaluator)
    }

    fun evaluate(ex: Throwable) = clazz.safeCast(ex)?.run(evaluator)
}
