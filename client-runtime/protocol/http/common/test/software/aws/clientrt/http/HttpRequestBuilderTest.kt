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
package software.aws.clientrt.http

import kotlin.test.Test
import kotlin.test.assertEquals
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.header
import software.aws.clientrt.http.request.headers
import software.aws.clientrt.http.request.url

class HttpRequestBuilderTest {
    @Test
    fun `it builds`() {
        val builder = HttpRequestBuilder()
        builder.headers {
            append("x-foo", "bar")
        }

        builder.url {
            host = "test.amazon.com"
        }

        builder.header("x-baz", "quux")

        val request = builder.build()
        assertEquals("bar", request.headers["x-foo"])
        assertEquals("quux", request.headers["x-baz"])
        assertEquals("test.amazon.com", request.url.host)
        assertEquals(HttpBody.Empty, request.body)
    }
}
