/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http.response

import software.aws.clientrt.http.HttpStatusCode

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
    val deserializer: Any? = builder.deserializer

    /**
     * The expected HTTP status code of a successful response. Pipeline features can make use of this
     * for targeted validation
     */
    val expectedHttpStatus: HttpStatusCode? = builder.expectedHttpStatus?.let { HttpStatusCode.fromValue(it) }

    companion object {
        fun build(block: ExecutionContextBuilder.() -> Unit): ExecutionContext = ExecutionContextBuilder().apply(block).build()
    }

    class ExecutionContextBuilder {
        var deserializer: Any? = null
        var expectedHttpStatus: Int? = null

        fun build(): ExecutionContext = ExecutionContext(this)
    }
}
