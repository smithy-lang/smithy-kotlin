/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.Feature
import aws.smithy.kotlin.runtime.http.FeatureKey
import aws.smithy.kotlin.runtime.http.HttpClientFeatureFactory
import aws.smithy.kotlin.runtime.http.operation.SdkHttpOperation
import aws.smithy.kotlin.runtime.http.operation.getLogger
import kotlin.time.ExperimentalTime

/**
 * Logs a summary of API calls.
 */
class CallLogging : Feature {
    companion object Feature : HttpClientFeatureFactory<Unit, CallLogging> {
        override val key: FeatureKey<CallLogging> = FeatureKey("CallLogging")
        override fun create(block: Unit.() -> Unit): CallLogging = CallLogging()
    }

    @OptIn(ExperimentalTime::class)
    override fun <I, O> install(operation: SdkHttpOperation<I, O>) {
        operation.execution.receive.intercept { req, next ->
            val res = next.call(req)
            req.context.getLogger("CallLogging").debug {
                val time = res.responseTime - res.requestTime
                val timeMs = time.inWholeMilliseconds
                val statusCode = res.response.status.value
                "Completed call in ${timeMs}ms; HTTP status code: $statusCode"
            }
            res
        }
    }
}
