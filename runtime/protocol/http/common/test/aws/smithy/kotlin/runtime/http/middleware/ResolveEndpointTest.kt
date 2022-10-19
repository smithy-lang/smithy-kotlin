/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.Protocol
import aws.smithy.kotlin.runtime.http.Url
import aws.smithy.kotlin.runtime.http.endpoints.Endpoint
import aws.smithy.kotlin.runtime.http.endpoints.EndpointResolver
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.tracing.NoOpTraceSpan
import aws.smithy.kotlin.runtime.tracing.withRootTraceSpan
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveEndpointTest {
    private val mockEngine = object : HttpClientEngineBase("test") {
        override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
            val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
            return HttpCall(request, resp, Instant.now(), Instant.now())
        }
    }
    private val client = sdkHttpClient(mockEngine)

    @Test
    fun testHostIsSet() = runTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder(), Unit)
        val endpoint = Endpoint(uri = Url.parse("https://api.test.com"))
        val resolver = EndpointResolver { endpoint }
        op.install(ResolveEndpoint(resolver))

        coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTPS, actual.url.scheme)
        assertEquals("api.test.com", actual.headers["Host"])
    }

    @Test
    fun testHostWithPort() = runTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder(), Unit)
        val endpoint = Endpoint(uri = Url.parse("https://api.test.com:8080"))
        val resolver = EndpointResolver { endpoint }
        op.install(ResolveEndpoint(resolver))

        coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTPS, actual.url.scheme)
        assertEquals(8080, actual.url.port)
    }

    @Test
    fun testHostWithBasePath() = runTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder().apply { url.path = "/operation" }, Unit)
        val endpoint = Endpoint(uri = Url.parse("https://api.test.com:8080/foo/bar"))
        val resolver = EndpointResolver { endpoint }
        op.install(ResolveEndpoint(resolver))

        coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTPS, actual.url.scheme)
        assertEquals(8080, actual.url.port)
        assertEquals("/foo/bar/operation", actual.url.path)
    }

    @Test
    fun testHostPrefix() = runTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder().apply { url.path = "/operation" }, Unit)
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com"))
        val resolver = EndpointResolver { endpoint }
        op.install(ResolveEndpoint(resolver))
        op.context[HttpOperationContext.HostPrefix] = "prefix."

        coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("prefix.api.test.com", actual.url.host)
        assertEquals(Protocol.HTTP, actual.url.scheme)
        assertEquals("/operation", actual.url.path)
    }

    @Test
    fun testSkipHostPrefixForImmutableHostnames() = runTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder().apply { url.path = "/operation" }, Unit)
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com"), isHostnameImmutable = true)
        val resolver = EndpointResolver { endpoint }
        op.install(ResolveEndpoint(resolver))
        op.context[HttpOperationContext.HostPrefix] = "prefix."

        coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTP, actual.url.scheme)
        assertEquals("/operation", actual.url.path)
    }

    @Test
    fun testEndpointPathPrefixWithNonEmptyPath() = runTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder().apply { url.path = "/operation" }, Unit)
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com/path/prefix/"))
        val resolver = EndpointResolver { endpoint }
        op.install(ResolveEndpoint(resolver))

        coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTP, actual.url.scheme)
        assertEquals("/path/prefix/operation", actual.url.path)
    }

    @Test
    fun testEndpointPathPrefixWithEmptyPath() = runTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder().apply { url.path = "" }, Unit)
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com/path/prefix"))
        val resolver = EndpointResolver { endpoint }
        op.install(ResolveEndpoint(resolver))

        coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTP, actual.url.scheme)
        assertEquals("/path/prefix", actual.url.path)
    }

    @Test
    fun testQueryParameters() = runTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder().apply { url.path = "/operation" }, Unit)
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com?foo=bar&baz=qux"))
        val resolver = EndpointResolver { endpoint }
        op.install(ResolveEndpoint(resolver))

        coroutineContext.withRootTraceSpan(NoOpTraceSpan) {
            op.roundTrip(client, Unit)
        }
        val actual = op.context[HttpOperationContext.HttpCallList].first().request

        assertEquals("api.test.com", actual.url.host)
        assertEquals(Protocol.HTTP, actual.url.scheme)
        assertEquals("/operation", actual.url.path)
        assertEquals("bar", actual.url.parameters["foo"])
        assertEquals("qux", actual.url.parameters["baz"])
    }
}
