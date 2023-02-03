/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.policy

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.SdkBaseException
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.ServiceException.*
import aws.smithy.kotlin.runtime.TransientHttpException
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * A standard retry policy which attempts to derive information from the Smithy exception hierarchy.
 */
public open class StandardRetryPolicy : RetryPolicy<Any?> {
    public companion object {
        public val Default: StandardRetryPolicy = StandardRetryPolicy()
    }

    private val evaluationStrategies = sequenceOf(
        EvaluationStrategy(::evaluateSpecificExceptions),
        EvaluationStrategy(::evaluateBaseException),
        EvaluationStrategy(::evaluateServiceException),
        EvaluationStrategy(::evaluateClientException),
        EvaluationStrategy(::evaluateNonSdkException),
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

    protected open fun evaluateSpecificExceptions(ex: Throwable): RetryDirective? = null

    private fun evaluateNonSdkException(ex: Throwable): RetryDirective? =
        // TODO Write _real_ logic to find connection errors, timeouts, stream faults, etc.
        when (ex) {
            is TransientHttpException -> RetryDirective.RetryError(RetryErrorType.ServerSide)
            else -> null
        }
}

private class EvaluationStrategy<T : Throwable>(val clazz: KClass<T>, private val evaluator: (T) -> RetryDirective?) {
    companion object {
        inline operator fun <reified T : Throwable> invoke(noinline evaluator: (T) -> RetryDirective?) =
            EvaluationStrategy(T::class, evaluator)
    }

    fun evaluate(ex: Throwable) = clazz.safeCast(ex)?.run(evaluator)
}
