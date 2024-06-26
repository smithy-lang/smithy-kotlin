/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.httptest

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.test.assertEquals

/**
 * An expected HttpRequest with the response that should be returned by the engine
 * @param expected the expected request. If null no assertions are made on the request
 * @param respondWith the response to return for this request. If null it defaults to an empty 200-OK response
 */
public data class MockRoundTrip(public val expected: HttpRequest?, public val respondWith: HttpResponse? = null)

/**
 * Actual and expected [HttpRequest] pair
 */
public data class RequestComparands(public val expected: HttpRequest?, public val actual: HttpRequest) {
    /**
     * Assert that [expected] matches [actual] according to [asserter].
     * @param msgPrefix The prefix to include in the message if an [AssertionError] is thrown
     */
    internal suspend fun assertRequest(msgPrefix: String, asserter: CallAsserter) {
        expected?.let { asserter.assertEquals(msgPrefix, it, actual) }
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
public class TestConnection(private val expected: List<MockRoundTrip> = emptyList()) : HttpClientEngineBase("TestConnection") {

    // expected is mutated in-flight, store original size
    private val iter = expected.iterator()
    private var requests = mutableListOf<RequestComparands>()

    override val config: HttpClientEngineConfig = HttpClientEngineConfig.Default

    public companion object {
        /**
         * Construct a [TestConnection] from a JSON payload. The payload should be an array of request-response object
         * pairs.
         *
         * e.g.
         *
         * ```json
         * [
         *   {
         *     "request": {
         *       "method": "GET",
         *       "uri": "http://foo.com/path/to/xyz",
         *       // required headers, header and value(s) must be present.
         *       // No assertion will be made about other headers or values if they exist
         *       "headers": {
         *         "single": "foo",
         *         // multi-value headers can be present as a list
         *         "multiple": ["foo", "bar"]
         *       },
         *       "bodyContentType": oneOf("utf8", "binary")
         *       // By default body is interpreted as a UTF8 string. When not set it is assumed empty.
         *       // Content type of `binary` is interpreted as a base64 encoded string of bytes. In which
         *       // case the decoded value will be used.
         *       "body": "",
         *     },
         *     "response": {
         *       "status": 200,
         *       "version": "HTTP/1.1", // this is the default
         *       "headers": {
         *         "foo": "bar",
         *         "multivalue": ["baz", "qux"]
         *       },
         *       "body": "foobar"
         *     }
         *   },
         *   {
         *     "request": {...},
         *     "response": {...}
         *   }
         * ]
         *
         * ```
         */
        public fun fromJson(payload: String): TestConnection = parseHttpTraffic(payload)
    }

    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
        check(iter.hasNext()) { "TestConnection has no remaining expected requests" }
        val next = iter.next()
        requests.add(RequestComparands(next.expected, request))

        val response = next.respondWith ?: HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
        val now = Instant.now()
        return HttpCall(request, response, now, now, callContext())
    }

    /**
     * Get the list of captured HTTP requests so far
     */
    public fun requests(): List<RequestComparands> = requests

    /**
     * Assert that each captured request matches the expected
     */
    public suspend fun assertRequests(asserter: CallAsserter = CallAsserter.FullyMatching) {
        assertEquals(expected.size, requests.size)
        requests.forEachIndexed { idx, captured ->
            captured.assertRequest("[request#$idx]", asserter)
        }
    }
}

/**
 * DSL builder for [TestConnection]
 */
public class HttpTestConnectionBuilder {
    public val requests: MutableList<MockRoundTrip> = mutableListOf<MockRoundTrip>()

    public class HttpRequestResponsePairBuilder {
        internal val requestBuilder = HttpRequestBuilder()
        public var response: HttpResponse? = null
        public fun request(block: HttpRequestBuilder.() -> Unit): HttpRequestBuilder = requestBuilder.apply(block)
    }

    public fun expect(block: HttpRequestResponsePairBuilder.() -> Unit) {
        val builder = HttpRequestResponsePairBuilder().apply(block)
        requests.add(MockRoundTrip(builder.requestBuilder.build(), builder.response))
    }

    public fun expect(request: HttpRequest, response: HttpResponse? = null) {
        requests.add(MockRoundTrip(request, response))
    }

    public fun expect(response: HttpResponse? = null) {
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
public fun buildTestConnection(block: HttpTestConnectionBuilder.() -> Unit): TestConnection {
    val builder = HttpTestConnectionBuilder().apply(block)
    return TestConnection(builder.requests)
}
