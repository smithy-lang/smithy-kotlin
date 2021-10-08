/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.httptest

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An expected HttpRequest with the response that should be returned by the engine
 * @param expected the expected request. If null no assertions are made on the request
 * @param respondWith the response to return for this request. If null it defaults to an empty 200-OK response
 */
data class MockRoundTrip(val expected: HttpRequest?, val respondWith: HttpResponse? = null)

/**
 * Actual and expected [HttpRequest] pair
 */
data class CallAssertion(val expected: HttpRequest?, val actual: HttpRequest) {
    /**
     * Assert that all of the components set on [expected] are also the same on [actual]. The actual request
     * may have additional headers, only the ones set in [expected] are compared.
     */
    internal suspend fun assertRequest(idx: Int) {
        if (expected == null) return
        assertEquals(expected.url.toString(), actual.url.toString(), "[request#$idx]: URL mismatch")
        expected.headers.forEach { name, values ->
            values.forEach {
                assertTrue(actual.headers.contains(name, it), "[request#$idx]: header `$name` missing value `$it`")
            }
        }

        val expectedBody = expected.body.readAll()?.decodeToString()
        val actualBody = actual.body.readAll()?.decodeToString()
        assertEquals(expectedBody, actualBody, "[request#$idx]: body mismatch")
    }
}

/**
 * TestConnection implements [aws.smithy.kotlin.runtime.http.engine.HttpClientEngine] with canned responses.
 * For each expected request it will capture the actual and respond with the pre-configured response (or a basic 200-OK
 * with an empty body if none was configured).
 *
 * After all requests/responses have been made use [assertRequests] to test that the actual requests captured match
 * the expected.
 *
 * NOTE: This engine is only capable of modeling request/response pairs. More complicated interactions such as duplex
 * streaming are not implemented.
 */
class TestConnection(private val expected: List<MockRoundTrip> = emptyList()) : HttpClientEngineBase("TestConnection") {
    // expected is mutated in-flight, store original size
    private val iter = expected.iterator()
    private var calls = mutableListOf<CallAssertion>()

    override suspend fun roundTrip(request: HttpRequest): HttpCall {
        check(iter.hasNext()) { "TestConnection has no remaining expected requests" }
        val next = iter.next()
        calls.add(CallAssertion(next.expected, request))

        val response = next.respondWith ?: HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
        val now = Instant.now()
        return HttpCall(request, response, now, now, callContext())
    }

    /**
     * Get the list of captured HTTP requests so far
     */
    fun requests(): List<CallAssertion> = calls

    /**
     * Assert that each captured request matches the expected
     */
    suspend fun assertRequests() {
        assertEquals(expected.size, calls.size)
        calls.forEachIndexed { idx, captured ->
            captured.assertRequest(idx)
        }
    }
}

/**
 * DSL builder for [TestConnection]
 */
class HttpTestConnectionBuilder {
    val requests = mutableListOf<MockRoundTrip>()

    class HttpRequestResponsePairBuilder {
        internal val requestBuilder = HttpRequestBuilder()
        var response: HttpResponse? = null
        fun request(block: HttpRequestBuilder.() -> Unit) = requestBuilder.apply(block)
    }

    fun expect(block: HttpRequestResponsePairBuilder.() -> Unit) {
        val builder = HttpRequestResponsePairBuilder().apply(block)
        requests.add(MockRoundTrip(builder.requestBuilder.build(), builder.response))
    }

    fun expect(request: HttpRequest, response: HttpResponse? = null) {
        requests.add(MockRoundTrip(request, response))
    }

    fun expect(response: HttpResponse? = null) {
        requests.add(MockRoundTrip(null, response))
    }
}

/**
 * Invoke [block] with the given builder and construct a new [TestConnection]
 *
 * Example:
 * ```kotlin
 * val testEngine = buildTestConnection {
 *     expect {
 *         request {
 *             url.host = "myhost"
 *             headers.append("x-foo", "bar")
 *         }
 *         response = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
 *     }
 * }
 * ```
 */
fun buildTestConnection(block: HttpTestConnectionBuilder.() -> Unit): TestConnection {
    val builder = HttpTestConnectionBuilder().apply(block)
    return TestConnection(builder.requests)
}
