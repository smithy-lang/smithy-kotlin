/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext.Companion.HttpCallList
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.tracing.NoOpTraceSpan
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MutateHeadersTest {
    private val mockEngine = object : HttpClientEngineBase("test") {
        override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
            val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
            return HttpCall(request, resp, Instant.now(), Instant.now())
        }
    }
    private val client = sdkHttpClient(mockEngine)

    @Test
    fun itOverridesHeaders() = runTest {
        val req = HttpRequestBuilder().apply {
            headers {
                set("foo", "bar")
                set("baz", "qux")
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        val m = MutateHeaders(
            override = mapOf(
                "foo" to "override",
                "z" to "zebra",
            ),
        )
        op.install(m)

        with(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val call = op.context.attributes[HttpCallList].first()
        // overrides
        assertEquals("override", call.request.headers["foo"])

        // adds unset
        assertEquals("zebra", call.request.headers["z"])

        // should leave in existing
        assertEquals("qux", call.request.headers["baz"])
    }

    @Test
    fun itAppendsHeaders() = runTest {
        val req = HttpRequestBuilder().apply {
            headers {
                set("foo", "bar")
                set("baz", "qux")
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        val m = MutateHeaders(
            append = mapOf(
                "foo" to "appended",
                "z" to "zebra",
            ),
        )
        op.install(m)

        with(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val call = op.context.attributes[HttpCallList].first()
        // appends existing
        assertEquals(listOf("bar", "appended"), call.request.headers.getAll("foo"))

        // adds unset
        assertEquals("zebra", call.request.headers["z"])

        // should leave in existing
        assertEquals("qux", call.request.headers["baz"])
    }

    @Test
    fun itSetsMissing() = runTest {
        val req = HttpRequestBuilder().apply {
            headers {
                set("foo", "bar")
                set("baz", "qux")
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        val m = MutateHeaders(
            setMissing = mapOf(
                "foo" to "nope",
                "z" to "zebra",
            ),
        )
        op.install(m)

        with(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val call = op.context.attributes[HttpCallList].first()
        assertEquals("bar", call.request.headers["foo"])
        assertEquals("zebra", call.request.headers["z"])
        assertEquals("qux", call.request.headers["baz"])
    }
}
