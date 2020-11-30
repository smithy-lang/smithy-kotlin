/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.request

import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.http.util.Phase
import software.aws.clientrt.http.util.Pipeline

/**
 * A container for the HttpRequestPipeline context parameter
 *
 * @property executionContext User data passed to the response pipeline that features can choose to take
 * advantage of.
 */
data class HttpRequestContext(val executionContext: ExecutionContext)

/**
 * Request pipeline that can be hooked into to transform an [HttpRequestBuilder] instance
 * and modify outgoing request parameters. At the end of executing the pipeline the builder should be fully
 * prepared to be built into an outgoing [HttpRequest].
 *
 * It is the expectation that the pipeline is configured in a way to make the desired transformation happen.
 */
class HttpRequestPipeline : Pipeline<HttpRequestBuilder, HttpRequestContext>(Initialize, Transform, Finalize) {
    companion object {
        /**
         * Any pre-flight checks. Validate inputs, set defaults, etc.
         */
        val Initialize = Phase("Initialize")

        /**
         * Transform the input (e.g. serialize the input to an HttpBody payload)
         */
        val Transform = Phase("Transform")

        /**
         * Any final modifications to the outgoing request. At this stage the request is expected to be fully
         * built.
         */
        val Finalize = Phase("Finalize")
    }
}
