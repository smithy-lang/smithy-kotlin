/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.middleware

import software.aws.clientrt.http.Headers
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.operation.HttpOperationContext.Companion.HttpCallList
import software.aws.clientrt.http.operation.newTestOperation
import software.aws.clientrt.http.operation.roundTrip
import software.aws.clientrt.http.request.HttpRequest
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.headers
import software.aws.clientrt.http.response.HttpCall
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.sdkHttpClient
import software.aws.clientrt.testing.runSuspendTest
import software.aws.clientrt.time.Instant
import software.aws.clientrt.util.get
import kotlin.test.Test
import kotlin.test.assertEquals

class MutateHeadersTest {

    private val mockEngine = object : HttpClientEngine {
        override suspend fun roundTrip(request: HttpRequest): HttpCall {
            val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
            return HttpCall(request, resp, Instant.now(), Instant.now())
        }
    }
    private val client = sdkHttpClient(mockEngine)

    @Test
    fun itOverridesHeaders() = runSuspendTest {
        val req = HttpRequestBuilder().apply {
            headers {
                set("foo", "bar")
                set("baz", "qux")
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.install(MutateHeaders) {
            set("foo", "override")
            set("z", "zebra")
        }

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpCallList].first()
        // overrides
        assertEquals("override", call.request.headers["foo"])

        // adds unset
        assertEquals("zebra", call.request.headers["z"])

        // should leave in existing
        assertEquals("qux", call.request.headers["baz"])

        return@runSuspendTest
    }

    @Test
    fun itAppendsHeaders() = runSuspendTest {
        val req = HttpRequestBuilder().apply {
            headers {
                set("foo", "bar")
                set("baz", "qux")
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.install(MutateHeaders) {
            append("foo", "appended")
            append("z", "zebra")
        }

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpCallList].first()
        // appends existing
        assertEquals(listOf("bar", "appended"), call.request.headers.getAll("foo"))

        // adds unset
        assertEquals("zebra", call.request.headers["z"])

        // should leave in existing
        assertEquals("qux", call.request.headers["baz"])

        return@runSuspendTest
    }

    @Test
    fun itSetsMissing() = runSuspendTest {
        val req = HttpRequestBuilder().apply {
            headers {
                set("foo", "bar")
                set("baz", "qux")
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.install(MutateHeaders) {
            setIfMissing("foo", "nope")
            setIfMissing("z", "zebra")
        }

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpCallList].first()
        assertEquals("bar", call.request.headers["foo"])
        assertEquals("zebra", call.request.headers["z"])
        assertEquals("qux", call.request.headers["baz"])

        return@runSuspendTest
    }
}
