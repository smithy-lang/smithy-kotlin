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
package software.aws.clientrt.http

import io.ktor.util.pipeline.PipelineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponseContext
import software.aws.clientrt.http.response.HttpResponsePipeline
import software.aws.clientrt.http.response.TypeInfo
import software.aws.clientrt.testing.runSuspendTest

// Coroutine builders like `runBlocking` are only available on a specific platform
class PipelineTest {

    @Test
    fun `request pipeline runs`() = runSuspendTest {
        val pipeline = HttpRequestPipeline()
        pipeline.intercept(HttpRequestPipeline.Initialize) { proceedWith((subject as Int) + 1) }
        pipeline.intercept(HttpRequestPipeline.Transform) { proceedWith((subject as Int) + 1) }
        pipeline.intercept(HttpRequestPipeline.Finalize) { proceedWith((subject as Int) + 1) }
        val builder = HttpRequestBuilder()
        val result = pipeline.execute(builder, 0)
        assertEquals(3, result as Int)
    }

    @Test
    fun `response pipeline runs`() = runSuspendTest {
        val pipeline = HttpResponsePipeline()
        pipeline.intercept(HttpResponsePipeline.Receive) { proceedWith((subject as Int) + 1) }
        pipeline.intercept(HttpResponsePipeline.Transform) { proceedWith((subject as Int) + 1) }
        pipeline.intercept(HttpResponsePipeline.Finalize) { proceedWith((subject as Int) + 1) }
        val response = HttpResponse(
            HttpStatusCode.OK,
            Headers {},
            HttpBody.Empty,
            HttpRequestBuilder().build()
        )
        val result = pipeline.execute(HttpResponseContext(response, TypeInfo(Int::class)), 0)
        assertEquals(3, result as Int)
    }

    @Test
    fun `free functions can be used`() = runSuspendTest {
        val pipeline = HttpResponsePipeline()

        suspend fun freeFunc(ctx: PipelineContext<Any, HttpResponseContext>) {
            ctx.proceedWith((ctx.subject as Int) + 1)
        }
        pipeline.interceptFn(HttpResponsePipeline.Receive, ::freeFunc)
        val response = HttpResponse(
            HttpStatusCode.OK,
            Headers {},
            HttpBody.Empty,
            HttpRequestBuilder().build()
        )
        val result = pipeline.execute(HttpResponseContext(response, TypeInfo(Int::class)), 0)
        assertEquals(1, result as Int)
    }
}
