/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class HttpResponseTestBuilderTest {
    private data class Foo(val bar: Int, val baz: String)

    @Test
    @OptIn(ExperimentalStdlibApi::class)
    fun itBuildsResponses() {
        httpResponseTest<Foo> {
            expected {
                statusCode = HttpStatusCode.OK
                headers = mapOf("bar" to "1")
                body = """
                {
                    "baz": "quux"
                }
                """
                response = Foo(1, "quux")
            }

            test { _, mockEngine ->
                val mockedCall = mockEngine.roundTrip(ExecutionContext(), HttpRequestBuilder().build())
                val mockedResp = mockedCall.response

                mockedResp.headers.contains("bar", "1")
                val mockedBody = mockedResp.body.readAll()?.decodeToString()
                mockedBody.shouldContain(
                    """
                    "baz": "quux"
                    """.trimIndent()
                )
            }
        }
    }
}
