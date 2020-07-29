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
package software.aws.clientrt.smithy.test

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpMethod
import software.aws.clientrt.http.content.ByteArrayContent
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.util.encodeUrlPath
import software.aws.clientrt.http.util.urlEncodeComponent
import software.aws.clientrt.testing.runSuspendTest

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
fun httpRequestTest(block: HttpRequestTestBuilder.() -> Unit) = runSuspendTest {
    // setup expectations
    val testBuilder = HttpRequestTestBuilder().apply(block)

    // provide the mock engine
    lateinit var actual: HttpRequestBuilder
    val mockEngine = object : HttpClientEngine {
        override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse {
            // capture the request that was built by the service operation
            if (requestBuilder.body.contentLength != null) {
                // Content-Length header is not expected to be set by serialize implementations. It is expected
                // to be read from [HttpBody::contentLength] by the underlying engine and set appropriately
                // add it in here so tests that define it can pass
                requestBuilder.headers["Content-Length"] = requestBuilder.body.contentLength.toString()
            }

            // Url::path is the raw path at this point and engines (or their wrappers) are expected to
            // encode the raw path. Protocol tests specify expectations with the encoded path though
            // so we need to encode the raw path that was actually built
            var encodedPath = requestBuilder.url.path.encodeUrlPath()

            // RFC-3986 §3.3 allows sub-delims (defined in section2.2) to be in the path component.
            // This includes both colon ':' and comma ',' characters.
            // Smithy protocol tests percent encode these expected values though whereas `encodeUrlPath()`
            // does not and follows the RFC. Fixing the tests was discussed but would adversely affect
            // other SDK's and we were asked to work around it.
            // Replace any left over sub-delims with the percent encoded value so that tests can proceed
            // https://tools.ietf.org/html/rfc3986#section-3.3
            val replacements = mapOf(":" to "%3A", "," to "%2C")
            for ((oldValue, newValue) in replacements) {
                encodedPath = encodedPath.replace(oldValue, newValue)
            }

            requestBuilder.url.path = encodedPath

            actual = requestBuilder

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
private suspend fun assertRequest(expected: ExpectedHttpRequest, actual: HttpRequestBuilder) {
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

    val expectedBody = expected.body?.let {
        assertNotNull(expected.bodyAssert, "body assertion function is required if an expected body is defined")
        ByteArrayContent(it.encodeToByteArray())
    }

    expected.bodyAssert?.invoke(expectedBody, actual.body)
}

data class ExpectedHttpRequest(
    // the HTTP method expected
    val method: HttpMethod = HttpMethod.GET,
    // expected path without the query string (e.g. /foo/bar)
    val uri: String = "",
    // query parameter names AND the associated values that must appear
    val queryParams: List<Pair<String, String>> = listOf(),
    // query parameter names that MUST not appear
    val forbiddenQueryParams: List<String> = listOf(),
    // query parameter names that MUST appear but no assertion on values
    val requiredQueryParams: List<String> = listOf(),
    // header names AND values that must appear
    val headers: Map<String, String> = mapOf(),
    // header names that must not appear
    val forbiddenHeaders: List<String> = listOf(),
    // header names that must appear but no assertion on values
    val requiredHeaders: List<String> = listOf(),
    // if no body is defined no assertions are made about it
    val body: String? = null,
    // actual function to use for the assertion
    val bodyAssert: BodyAssertFn? = null,
    // if not defined no assertion is made
    val bodyMediaType: String? = null
)

/**
 * The function used to assert the expected and actual body contents are equal. Callers
 * should make whatever assertions they want about the body and should throw an appropriate
 * exception when the contents do not match.
 *
 * The function will be passed the [expected] contents and the [actual] read contents
 * from the built request as a string.
 */
typealias BodyAssertFn = suspend (expected: HttpBody?, actual: HttpBody?) -> Unit

class ExpectedHttpRequestBuilder {
    var method: HttpMethod = HttpMethod.GET
    var uri: String = ""
    var queryParams: List<Pair<String, String>> = listOf()
    var forbiddenQueryParams: List<String> = listOf()
    var requiredQueryParams: List<String> = listOf()
    var headers: Map<String, String> = mapOf()
    var forbiddenHeaders: List<String> = listOf()
    var requiredHeaders: List<String> = listOf()
    var body: String? = null
    var bodyAssert: BodyAssertFn? = null
    var bodyMediaType: String? = null

    fun build(): ExpectedHttpRequest =
        ExpectedHttpRequest(
            this.method,
            this.uri,
            this.queryParams,
            this.forbiddenQueryParams,
            this.requiredQueryParams,
            this.headers,
            this.forbiddenHeaders,
            this.requiredHeaders,
            this.body,
            this.bodyAssert,
            this.bodyMediaType
        )
}

class HttpRequestTestBuilder {
    internal var runOperation: suspend (mockEngine: HttpClientEngine) -> Unit = {}
    internal var expected = ExpectedHttpRequestBuilder()

    /**
     * Setup the expected HTTP request that the service operation should produce
     */
    fun expected(block: ExpectedHttpRequestBuilder.() -> Unit) {
        expected.apply(block)
    }

    /**
     * Setup the service operation to run. The [HttpClientEngine] to use for the test is
     * provided as input to the function. The [block] is responsible for setting up the
     * service client with the provided engine, providing the input, and executing the
     * operation (which *MUST* be the last statement in the block).
     */
    fun operation(block: suspend (mockEngine: HttpClientEngine) -> Unit) {
        runOperation = block
    }
}
