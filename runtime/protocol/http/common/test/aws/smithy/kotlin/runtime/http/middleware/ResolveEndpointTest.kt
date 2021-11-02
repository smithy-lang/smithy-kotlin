/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.operation.Endpoint
import aws.smithy.kotlin.runtime.http.operation.EndpointResolver
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.get
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveEndpointTest {
    private val mockEngine = object : HttpClientEngineBase("test") {
        override suspend fun roundTrip(request: HttpRequest): HttpCall {
            val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
            return HttpCall(request, resp, Instant.now(), Instant.now())
        }
    }
    private val client = sdkHttpClient(mockEngine)

    @Test
    fun testHostIsSet(): Unit = runSuspendTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder(), Unit)
        val endpoint = Endpoint(uri = Url.parse("https://api.test.com"))
        op.install(ResolveEndpoint) {
            resolver = EndpointResolver { endpoint }
        }

        op.roundTrip(client, Unit)
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTPS, actual.url.scheme)
        assertEquals("api.test.com", actual.headers["Host"])
    }

    @Test
    fun testHostWithPort(): Unit = runSuspendTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder(), Unit)
        val endpoint = Endpoint(uri = Url.parse("https://api.test.com:8080"))
        op.install(ResolveEndpoint) {
            resolver = EndpointResolver { endpoint }
        }

        op.roundTrip(client, Unit)
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTPS, actual.url.scheme)
        assertEquals(8080, actual.url.port)
    }

    @Test
    fun testHostWithBasePath(): Unit = runSuspendTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder().apply { url.path = "/operation" }, Unit)
        val endpoint = Endpoint(uri = Url.parse("https://api.test.com:8080/foo/bar"))
        op.install(ResolveEndpoint) {
            resolver = EndpointResolver { endpoint }
        }

        op.roundTrip(client, Unit)
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTPS, actual.url.scheme)
        assertEquals(8080, actual.url.port)
        assertEquals("/foo/bar/operation", actual.url.path)
    }

    @Test
    fun testHostPrefix(): Unit = runSuspendTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder().apply { url.path = "/operation" }, Unit)
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com"))
        op.install(ResolveEndpoint) {
            resolver = EndpointResolver { endpoint }
        }
        op.context[HttpOperationContext.HostPrefix] = "prefix."

        op.roundTrip(client, Unit)
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("prefix.api.test.com", actual.url.host)
        assertEquals(Protocol.HTTP, actual.url.scheme)
        assertEquals("/operation", actual.url.path)
    }
}
