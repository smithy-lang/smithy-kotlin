/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.smithy.test

import io.kotest.matchers.string.shouldContain
import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.readAll
import software.aws.clientrt.http.request.HttpRequestBuilder
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
                val mockedResp = mockEngine.roundTrip(HttpRequestBuilder())
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
