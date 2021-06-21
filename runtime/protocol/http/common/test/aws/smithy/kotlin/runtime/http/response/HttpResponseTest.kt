/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.response

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpResponseTest {
    @Test
    fun testProtocolResponseExtensions() {
        val resp = HttpResponse(
            HttpStatusCode.BadRequest,
            Headers {
                append("foo", "v1")
                append("foo", "v2")
                append("bar", "v3")
            },
            HttpBody.Empty
        )

        assertEquals("v1", resp.header("foo"))
        assertEquals(listOf("v1", "v2"), resp.getAllHeaders("foo"))
        assertEquals(HttpStatusCode.BadRequest, resp.statusCode())
    }
}
