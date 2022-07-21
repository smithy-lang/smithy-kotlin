/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
@file:OptIn(ExperimentalCoroutinesApi::class)

package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.HeadersBuilder
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.util.text.urlEncodeComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// marker exception thrown by roundTripping
private class MockEngineException : RuntimeException()

/**
 * Setup a [Smithy HTTP Request Test](https://awslabs.github.io/smithy/1.0/spec/http-protocol-compliance-tests.html#httprequesttests).
 *
 * # Example
 * ```
 * fun fooTest() = httpRequestTest {
 *
 *     // setup the expected request that was built
 *     expected {
 *         uri = "/foo/bar"
 *     }
 *
 *     // run the service operation with the provided (mock) [HttpClientEngine]
 *     operation { mockEngine ->
 *         val input = MyInput {
 *             param = "param1"
 *         }
 *
 *         val service = blah()
 *         service.doOperation(input)
 *     }
 * }
 * ```
 */
public fun httpRequestTest(block: HttpRequestTestBuilder.() -> Unit): TestResult = runTest {
    // setup expectations
    val testBuilder = HttpRequestTestBuilder().apply(block)

    // provide the mock engine
    lateinit var actual: HttpRequest
    val mockEngine = object : HttpClientEngineBase("smithy-test-mock-engine") {
        override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
            val testHeaders = HeadersBuilder().apply {
                appendAll(request.headers)
            }

            // capture the request that was built by the service operation
            val contentLength = request.body.contentLength
            if (contentLength != null && contentLength > 0) {
                // Content-Length header is not expected to be set by serialize implementations. It is expected
                // to be read from [HttpBody::contentLength] by the underlying engine and set appropriately
                // add it in here so tests that define it can pass
                testHeaders["Content-Length"] = contentLength.toString()
            }

            actual = request.copy(headers = testHeaders.build())

            // this control flow requires the service call (or whatever calls the mock engine) to be the last
            // statement in the operation{} block...
            throw MockEngineException()
        }
    }

    // run the actual service operation provided by the caller
    try {
        testBuilder.runOperation(mockEngine)
    } catch (ex: Exception) {
        // we expect a MockEngineException, anything else propagate back
        if (ex !is MockEngineException) throw ex
    }

    assertRequest(testBuilder.expected.build(), actual)
}

@OptIn(ExperimentalStdlibApi::class)
private suspend fun assertRequest(expected: ExpectedHttpRequest, actual: HttpRequest) {
    // run the assertions
    assertEquals(expected.method, actual.method, "expected method: `${expected.method}`; got: `${actual.method}`")
    assertEquals(expected.uri, actual.url.path, "expected path: `${expected.uri}`; got: `${actual.url.path}`")

    // have to deal with URL encoding
    expected.queryParams.forEach { (name, value) ->
        val actualValues = actual.url.parameters.getAll(name)
        assertNotNull(actualValues, "expected query parameter `$name`; no values found")
        assertTrue(actualValues.map { it.urlEncodeComponent() }.contains(value), "expected query name value pair not found: `$name:$value`")
    }

    expected.forbiddenQueryParams.forEach {
        assertFalse(actual.url.parameters.contains(it), "forbidden query parameter found: `$it`")
    }

    expected.requiredQueryParams.forEach {
        assertTrue(actual.url.parameters.contains(it), "expected required query parameter not found: `$it`")
    }

    expected.headers.forEach { (name, value) ->
        assertTrue(actual.headers.contains(name), "expected header `$name` has no actual values")

        // the value given in `httpRequestTest` trait may be a list of values as a string e.g. `"foo", "bar"`
        // join the in-memory representation (which is a list of strings associated to the header name) to a string
        // for comparision
        val values = actual.headers.getAll(name)?.joinToString(separator = ", ")
        requireNotNull(values) { "actual values expected to not be null" }

        assertEquals(value, values, "expected header name value pair not equal: `$name:$value`; found: `$name:$values`")
    }

    expected.forbiddenHeaders.forEach {
        assertFalse(actual.headers.contains(it), "forbidden header found: `$it`")
    }
    expected.requiredHeaders.forEach {
        assertTrue(actual.headers.contains(it), "expected required header not found: `$it`")
    }

    expected.resolvedHost?.let {
        assertEquals(it, actual.url.host, "expected host: `${expected.resolvedHost}`; got: `${actual.url.host}`")
    }

    val expectedBody = expected.body?.let {
        assertNotNull(expected.bodyAssert, "body assertion function is required if an expected body is defined")
        ByteArrayContent(it.encodeToByteArray())
    }

    expected.bodyAssert?.invoke(expectedBody, actual.body)
}

public data class ExpectedHttpRequest(
    // the HTTP method expected
    public val method: HttpMethod = HttpMethod.GET,
    // expected path without the query string (e.g. /foo/bar)
    public val uri: String = "",
    // query parameter names AND the associated values that must appear
    public val queryParams: List<Pair<String, String>> = listOf(),
    // query parameter names that MUST not appear
    public val forbiddenQueryParams: List<String> = listOf(),
    // query parameter names that MUST appear but no assertion on values
    public val requiredQueryParams: List<String> = listOf(),
    // header names AND values that must appear
    public val headers: Map<String, String> = mapOf(),
    // header names that must not appear
    public val forbiddenHeaders: List<String> = listOf(),
    // header names that must appear but no assertion on values
    public val requiredHeaders: List<String> = listOf(),
    // the host / endpoint that the client should send to, not including the path or scheme
    public var resolvedHost: String? = null,
    // if no body is defined no assertions are made about it
    public val body: String? = null,
    // actual function to use for the assertion
    public val bodyAssert: BodyAssertFn? = null,
    // if not defined no assertion is made
    public val bodyMediaType: String? = null
)

/**
 * The function used to assert the expected and actual body contents are equal. Callers
 * should make whatever assertions they want about the body and should throw an appropriate
 * exception when the contents do not match.
 *
 * The function will be passed the [expected] contents and the [actual] read contents
 * from the built request as a string.
 */
public typealias BodyAssertFn = suspend (expected: HttpBody?, actual: HttpBody?) -> Unit

public class ExpectedHttpRequestBuilder {
    public var method: HttpMethod = HttpMethod.GET
    public var uri: String = ""
    public var queryParams: List<Pair<String, String>> = listOf()
    public var forbiddenQueryParams: List<String> = listOf()
    public var requiredQueryParams: List<String> = listOf()
    public var headers: Map<String, String> = mapOf()
    public var forbiddenHeaders: List<String> = listOf()
    public var requiredHeaders: List<String> = listOf()
    public var resolvedHost: String? = null
    public var body: String? = null
    public var bodyAssert: BodyAssertFn? = null
    public var bodyMediaType: String? = null

    public fun build(): ExpectedHttpRequest =
        ExpectedHttpRequest(
            this.method,
            this.uri,
            this.queryParams,
            this.forbiddenQueryParams,
            this.requiredQueryParams,
            this.headers,
            this.forbiddenHeaders,
            this.requiredHeaders,
            this.resolvedHost,
            this.body,
            this.bodyAssert,
            this.bodyMediaType
        )
}

public class HttpRequestTestBuilder {
    internal var runOperation: suspend (mockEngine: HttpClientEngine) -> Unit = {}
    internal var expected = ExpectedHttpRequestBuilder()

    /**
     * Setup the expected HTTP request that the service operation should produce
     */
    public fun expected(block: ExpectedHttpRequestBuilder.() -> Unit) {
        expected.apply(block)
    }

    /**
     * Setup the service operation to run. The [HttpClientEngine] to use for the test is
     * provided as input to the function. The [block] is responsible for setting up the
     * service client with the provided engine, providing the input, and executing the
     * operation (which *MUST* be the last statement in the block).
     */
    public fun operation(block: suspend (mockEngine: HttpClientEngine) -> Unit) {
        runOperation = block
    }
}
