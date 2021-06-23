/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.url
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpRequestBuilderTest {
    @Test
    fun itBuilds() {
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
