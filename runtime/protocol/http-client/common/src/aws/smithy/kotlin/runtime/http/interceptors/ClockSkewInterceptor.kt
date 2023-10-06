/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

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

public class ClockSkewInterceptor : HttpInterceptor {
    public companion object {
        /**
         * How much must the clock be skewed before attempting correction
         */
        public val CLOCK_SKEW_THRESHOLD: Duration = 4.minutes

        /**
         * Get the current skew between the client time and [serverTime] as a [Duration].
         * It may be negative if the serverTime is in the future.
         * @param serverTime the server's time
         */
        public fun Instant.getSkew(serverTime: Instant): Duration =this.until(serverTime)

        /**
         * Determine whether the client's clock is skewed relative to the server.
         * @param serverTime the server's time
         */
        public fun Instant.isSkewed(serverTime: Instant): Boolean = getSkew(serverTime).absoluteValue >= CLOCK_SKEW_THRESHOLD
    }

    public override suspend fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>): HttpResponse {
        val logger = coroutineContext.logger<ClockSkewInterceptor>()

        val clientTime = context.protocolRequest.headers["x-amz-date"]?.let {
            Instant.fromIso8601(it)
        } ?: run {
            logger.info { "client did not send \"x-amz-date\" header, skipping skew calculation" }
            return context.protocolResponse
        }

        val serverTime = context.protocolResponse.header("Date")?.let {
            Instant.fromRfc5322(it)
        } ?: run {
            logger.info { "service did not return \"Date\" header, skipping skew calculation" }
            return context.protocolResponse
        }

        if (clientTime.isSkewed(serverTime)) {
            val skew = clientTime.getSkew(serverTime)
            logger.warn { "client clock is skewed $skew, applying correction" }
            context.executionContext[HttpOperationContext.ClockSkew] = skew
        } else {
            logger.info { "client clock ($clientTime) is not skewed from the server ($serverTime)" }
        }

        return context.protocolResponse
    }
}
