/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ErrorMetadata
import aws.smithy.kotlin.runtime.SdkBaseException
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.client.ResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.response.header
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.until
import aws.smithy.kotlin.runtime.util.get
import kotlinx.atomicfu.*
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * An interceptor used to detect clock skew (difference between client and server clocks) and apply a correction.
 */
public class ClockSkewInterceptor : HttpInterceptor {
    public companion object {
        /**
         * How much must the clock be skewed before attempting correction
         */
        public val CLOCK_SKEW_THRESHOLD: Duration = 4.minutes

        /**
         * Determine whether the client's clock is skewed relative to the server.
         * @return true if the service's response represents a definite clock skew error
         * OR a *possible* clock skew error AND the skew exists. false otherwise.
         * @param responseCodeDescription the server's response code description
         * @param serverTime the server's time
         */
        internal fun Instant.isSkewed(serverTime: Instant, responseCodeDescription: String?): Boolean =
            responseCodeDescription?.let {
                CLOCK_SKEW_ERROR_CODES.contains(it) || (POSSIBLE_CLOCK_SKEW_ERROR_CODES.contains(it) && until(serverTime).absoluteValue >= CLOCK_SKEW_THRESHOLD)
            } ?: false

        // Errors definitely caused by clock skew
        private val CLOCK_SKEW_ERROR_CODES = listOf(
            "RequestTimeTooSkewed",
            "RequestExpired",
            "RequestInTheFuture",
        )

        // Errors possibly caused by clock skew
        private val POSSIBLE_CLOCK_SKEW_ERROR_CODES = listOf(
            "PriorRequestNotComplete",
            "RequestTimeout",
            "RequestTimeoutException",
            "InternalError",
        )
    }

    // Clock skew to be applied to all requests
    private val _currentSkew: AtomicRef<Duration?> = atomic(null)

    /**
     * Apply the previously-computed skew, if it's set, to the execution context before signing
     */
    public override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        val logger = coroutineContext.logger<ClockSkewInterceptor>()

        val skew = _currentSkew.value
        skew?.let {
            logger.info { "applying clock skew $skew to client" }
            context.executionContext[HttpOperationContext.ClockSkew] = skew
        }

        context.executionContext[HttpOperationContext.ClockSkewApproximateSigningTime] = Instant.now()

        return context.protocolRequest
    }

    /**
     * After receiving a response, check if the client clock is skewed and apply a correction if necessary.
     */
    public override suspend fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
        val logger = coroutineContext.logger<ClockSkewInterceptor>()

        val serverTime = context.protocolResponse?.header("Date")?.let {
            Instant.fromRfc5322(it)
        } ?: run {
            logger.debug { "service did not return \"Date\" header, skipping skew calculation" }
            return context.response
        }

        val clientTime = context.protocolRequest.headers["Date"]?.let {
            Instant.fromRfc5322(it)
        } ?: context.protocolRequest.headers["x-amz-date"]?.let {
            Instant.fromIso8601(it)
        } ?: context.executionContext[HttpOperationContext.ClockSkewApproximateSigningTime]

        if (clientTime.isSkewed(serverTime, context.protocolResponse?.status?.description)) {
            val skew = clientTime.until(serverTime)
            logger.warn { "client clock ($clientTime) is skewed $skew from the server ($serverTime), applying correction" }
            _currentSkew.getAndSet(skew)
            context.executionContext[HttpOperationContext.ClockSkew] = skew

            // Mark the exception as retryable
            val ex = (context.response.exceptionOrNull() as? SdkBaseException)
            ex?.sdkErrorMetadata?.attributes?.set(ErrorMetadata.Retryable, true)
            ex?.let { return Result.failure(it) }
        } else {
            logger.trace { "client clock ($clientTime) is not skewed from the server ($serverTime)" }
        }

        return context.response
    }
}
