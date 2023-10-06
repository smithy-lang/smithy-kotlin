/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.response.header
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.atomicfu.*

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
         * Get the current skew between the client time and [serverTime] as a [Duration].
         * It may be negative if the serverTime is in the past.
         * @param serverTime the server's time
         */
        internal fun Instant.getSkew(serverTime: Instant): Duration = this.until(serverTime)

        /**
         * Determine whether the client's clock is skewed relative to the server.
         * @param serverTime the server's time
         */
        internal fun Instant.isSkewed(serverTime: Instant): Boolean = getSkew(serverTime).absoluteValue >= CLOCK_SKEW_THRESHOLD
    }

    // Clock skew to be applied to all requests
    private var _currentSkew: AtomicRef<Duration> = atomic(Duration.ZERO)

    private val currentSkew: Duration
        get() = _currentSkew.value

    /**
     * Apply the previously-computed skew, if it's set, to the execution context before signing
     */
    public override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        val logger = coroutineContext.logger<ClockSkewInterceptor>()

        val skew = currentSkew
        if (skew != Duration.ZERO) {
            logger.debug { "applying clock skew $skew to client" }
            context.executionContext[HttpOperationContext.ClockSkew] = skew
        }

        return context.protocolRequest
    }

    /**
     * After receiving a response, check if the client clock is skewed and apply a correction if necessary.
     */
    public override suspend fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>): HttpResponse {
        val logger = coroutineContext.logger<ClockSkewInterceptor>()

        val serverTime = context.protocolResponse.header("Date")?.let {
            Instant.fromRfc5322(it)
        } ?: run {
            logger.info { "service did not return \"Date\" header, skipping skew calculation" }
            return context.protocolResponse
        }

        val clientTime = context.protocolRequest.headers["Date"]?.let {
            Instant.fromRfc5322(it)
        } ?: context.protocolRequest.headers["x-amz-date"]?.let {
            Instant.fromIso8601(it)
        } ?: Instant.now()

        if (clientTime.isSkewed(serverTime)) {
            val skew = clientTime.getSkew(serverTime)
            logger.warn { "client clock is skewed $skew, applying correction" }
            _currentSkew.getAndSet(skew)
            context.executionContext[HttpOperationContext.ClockSkew] = skew
        } else {
            logger.debug { "client clock ($clientTime) is not skewed from the server ($serverTime)" }
        }

        return context.protocolResponse
    }
}
