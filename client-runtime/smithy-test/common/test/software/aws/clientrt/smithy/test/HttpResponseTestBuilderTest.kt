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

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.readAll
import software.aws.clientrt.http.request.HttpRequestBuilder

class HttpResponseTestBuilderTest {
    private data class Foo(val bar: Int, val baz: String)

    @Test
    @OptIn(ExperimentalStdlibApi::class)
    fun `it builds responses`() {
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
                mockedBody.shouldContain("""
                    "baz": "quux"
                """.trimIndent())
            }
        }
    }
}
