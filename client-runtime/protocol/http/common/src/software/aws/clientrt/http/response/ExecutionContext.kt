/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.response

import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.feature.HttpDeserialize

/**
 * Features are registered at the [software.aws.clientrt.http.SdkHttpClient] level and are executed
 * on every request/response. The data flowing through those pipelines changes with every call though.
 *
 * ExecutionContext is the response pipeline per/operation context (metadata) that features can use
 * to drive behavior that is specific to a particular response.
 */
class ExecutionContext private constructor(builder: ExecutionContextBuilder) {
    /**
     * An instance of a deserializer (i.e. HttpDeserialize) used to transform an [HttpResponse] to the expected
     * output type T for a single round trip
     */
    val deserializer: HttpDeserialize? = builder.deserializer

    /**
     * The expected HTTP status code of a successful response. Pipeline features can make use of this
     * for targeted validation
     */
    val expectedHttpStatus: HttpStatusCode? = builder.expectedHttpStatus?.let { HttpStatusCode.fromValue(it) }

    companion object {
        fun build(block: ExecutionContextBuilder.() -> Unit): ExecutionContext = ExecutionContextBuilder().apply(block).build()
    }

    class ExecutionContextBuilder {
        var deserializer: HttpDeserialize? = null
        var expectedHttpStatus: Int? = null

        fun build(): ExecutionContext = ExecutionContext(this)
    }
}
