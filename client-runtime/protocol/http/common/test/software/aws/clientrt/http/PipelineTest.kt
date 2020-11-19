/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import io.ktor.util.pipeline.PipelineContext
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestContext
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponseContext
import software.aws.clientrt.http.response.HttpResponsePipeline
import software.aws.clientrt.http.response.TypeInfo
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Coroutine builders like `runBlocking` are only available on a specific platform
class PipelineTest {

    @Test
    fun `request pipeline runs`() = runSuspendTest {
        val pipeline = HttpRequestPipeline()
        pipeline.intercept(HttpRequestPipeline.Initialize) { subject.headers.append("key", "1") }
        pipeline.intercept(HttpRequestPipeline.Transform) { subject.headers.append("key", "2") }
        pipeline.intercept(HttpRequestPipeline.Finalize) { subject.headers.append("key", "3") }
        val builder = HttpRequestBuilder()
        val ctx = HttpRequestContext(ExecutionContext())
        val result = pipeline.execute(ctx, builder)
        assertEquals(3, result.headers.getAll("key")?.size)
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
        val result = pipeline.execute(HttpResponseContext(response, TypeInfo(Int::class), ExecutionContext()), 0)
        assertEquals(3, result as Int)
    }

    @Test
    fun `functions can be used`() = runSuspendTest {
        val pipeline = HttpResponsePipeline()

        suspend fun freeFunc(ctx: PipelineContext<Any, HttpResponseContext>) {
            ctx.proceedWith((ctx.subject as Int) + 1)
        }
        pipeline.intercept(HttpResponsePipeline.Receive) { freeFunc(this) }
        val response = HttpResponse(
            HttpStatusCode.OK,
            Headers {},
            HttpBody.Empty,
            HttpRequestBuilder().build()
        )
        val result = pipeline.execute(HttpResponseContext(response, TypeInfo(Int::class), ExecutionContext()), 0)
        assertEquals(1, result as Int)
    }
}
