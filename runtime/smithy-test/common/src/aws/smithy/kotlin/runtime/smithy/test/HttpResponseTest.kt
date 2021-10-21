/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import aws.smithy.kotlin.runtime.time.Instant

typealias HttpResponseTestFn<T> = suspend (expectedResponse: T?, mockEngine: HttpClientEngine) -> Unit

class ExpectedHttpResponse<T> {
    var statusCode: HttpStatusCode = HttpStatusCode.OK
    var headers: Map<String, String> = mapOf()
    var body: String? = null
    var bodyMediaType: String? = null
    var response: T? = null
}

class HttpResponseTestBuilder<T> {
    internal var expected: ExpectedHttpResponse<T> = ExpectedHttpResponse()
    internal var testFn: HttpResponseTestFn<T> = { _, _ -> }

    /**
     * Setup the expected HTTP response that the service operation should consume
     */
    fun expected(block: ExpectedHttpResponse<T>.() -> Unit) {
        expected.apply(block)
    }

    /**
     * Invoke the service operation and make assertions about the result. The [HttpClientEngine] to use
     * for the test is provided as input to the function. The [block] is responsible for setting up the
     * service client with the provided engine, providing the input, and executing the
     * operation.
     */
    fun test(block: HttpResponseTestFn<T>) {
        testFn = block
    }
}

/**
 * Setup a [Smithy HTTP Response Test](https://awslabs.github.io/smithy/1.0/spec/http-protocol-compliance-tests.html#httpresponsetests)
 *
 * # Example
 * ```
 * fun fooTest() = httpResponseTest<MyOutput> {
 *
 *     // setup the expected request that was built
 *     expected {
 *         status = HttpStatusCode.fromValue(200)
 *         headers = mapOf("foo" to "bar")
 *         body = """{"baz": "quux"}"""
 *         bodyMediaType = "application/json"
 *         response = MyOutput {
 *             baz = "quux"
 *         }
 *     }
 *
 *     test { expectedResult, mockEngine ->
 *        val input = MyInput{}
 *        val actualResult = client.myOperation(input)
 *        // compare expected and actual
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalStdlibApi::class)
fun <T> httpResponseTest(block: HttpResponseTestBuilder<T>.() -> Unit) = runSuspendTest {
    val testBuilder = HttpResponseTestBuilder<T>().apply(block)

    // provide the mock engine
    val mockEngine = object : HttpClientEngineBase("smithy-test-resp-mock-engine") {
        override suspend fun roundTrip(request: HttpRequest): HttpCall {
            val headers = Headers {
                testBuilder.expected.headers.forEach { (key, value) ->
                    append(key, value)
                }
            }

            val body: HttpBody = testBuilder.expected.body?.let {
                // emulate a real response stream which typically can only be consumed once
                // see: https://github.com/awslabs/aws-sdk-kotlin/issues/356
                object : HttpBody.Streaming() {
                    private var consumed = false
                    override fun readFrom(): SdkByteReadChannel {
                        val content = if (consumed) ByteArray(0) else it.encodeToByteArray()
                        consumed = true
                        return SdkByteReadChannel(content)
                    }
                }
            } ?: HttpBody.Empty

            val resp = HttpResponse(testBuilder.expected.statusCode, headers, body)
            val now = Instant.now()
            return HttpCall(request, resp, now, now)
        }
    }

    testBuilder.testFn.invoke(testBuilder.expected.response, mockEngine)
}
