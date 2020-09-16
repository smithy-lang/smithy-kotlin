/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
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
