/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.httptest.TestEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MutateHeadersTest {
    private val client = SdkHttpClient(TestEngine())

    @Test
    fun itOverridesHeaders() = runTest {
        val req = HttpRequestBuilder().apply {
            headers {
                set("foo", "bar")
                set("baz", "qux")
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        val m = MutateHeaders(
            override = mapOf(
                "foo" to "override",
                "z" to "zebra",
            ),
        )
        op.install(m)

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        // overrides
        assertEquals("override", call.request.headers["foo"])

        // adds unset
        assertEquals("zebra", call.request.headers["z"])

        // should leave in existing
        assertEquals("qux", call.request.headers["baz"])
    }

    @Test
    fun itAppendsHeaders() = runTest {
        val req = HttpRequestBuilder().apply {
            headers {
                set("foo", "bar")
                set("baz", "qux")
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        val m = MutateHeaders(
            append = mapOf(
                "foo" to "appended",
                "z" to "zebra",
            ),
        )
        op.install(m)

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        // appends existing
        assertEquals(listOf("bar", "appended"), call.request.headers.getAll("foo"))

        // adds unset
        assertEquals("zebra", call.request.headers["z"])

        // should leave in existing
        assertEquals("qux", call.request.headers["baz"])
    }

    @Test
    fun itSetsMissing() = runTest {
        val req = HttpRequestBuilder().apply {
            headers {
                set("foo", "bar")
                set("baz", "qux")
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        val m = MutateHeaders(
            setMissing = mapOf(
                "foo" to "nope",
                "z" to "zebra",
            ),
        )
        op.install(m)

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("bar", call.request.headers["foo"])
        assertEquals("zebra", call.request.headers["z"])
        assertEquals("qux", call.request.headers["baz"])
    }
}
