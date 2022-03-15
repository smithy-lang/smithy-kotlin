/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.test
import kotlin.test.Test
import kotlin.test.assertEquals

class AsyncStressTest : AbstractEngineTest() {

    @Test
    fun testConcurrentRequests() = testEngines {
        // https://github.com/awslabs/aws-sdk-kotlin/issues/170
        concurrency = 1_000

        test { env, client ->
            val req = HttpRequest {
                url(env.testServer)
                url.path = "concurrent"
            }

            val call = client.call(req)
            assertEquals(HttpStatusCode.OK, call.response.status)

            try {
                val resp = call.response.body.readAll() ?: error("expected response body")

                val contentLength = call.response.body.contentLength ?: 0L
                val text = "testing"
                val expectedText = text.repeat(contentLength.toInt() / text.length)
                assertEquals(expectedText, resp.decodeToString())
            } finally {
                call.complete()
            }
        }
    }
}
