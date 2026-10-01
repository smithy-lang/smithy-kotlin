/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.test
import aws.smithy.kotlin.runtime.http.test.util.testSetup
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderTest : AbstractEngineTest() {
    // https://github.com/awslabs/aws-sdk-kotlin/issues/1163
    @Test
    fun testNonAsciiHeaderValue() = testEngines {
        test { _, client ->
            val req = HttpRequest {
                testSetup()
                url.path.decoded = "non-ascii"
                headers.append("non-ascii-header", "µ")
                headers.append("another-one", "\uD83C\uDF0A")
            }

            val call = client.call(req)
            call.complete()
            assertEquals(HttpStatusCode.OK, call.response.status)
        }
    }

    // tt/V2228436368/F1 - Verifies that CRLF header injection attacks fail across all engines
    @Test
    fun testCrlfInHeaderValueDoesNotInjectExtraHeaders() = testEngines {
        test { _, client ->
            val maliciousValue = "innocent\r\nx-injected-header: pwned"

            val call = try {
                client.call(
                    HttpRequest {
                        testSetup()
                        url.path.decoded = "echo-headers"
                        headers.append("x-amz-meta-note", maliciousValue)
                    },
                )
            } catch (_: Exception) {
                // If the engine rejects the header value with an exception, that's acceptable
                return@test
            }

            try {
                // If the request went through, verify the server did NOT see an injected header
                val injectedCount = call.response.headers["x-injected-count"]?.toIntOrNull() ?: 0
                assertEquals(0, injectedCount, "CRLF injection detected: server received $injectedCount injected header(s)")
            } finally {
                call.complete()
            }
        }
    }

    // tt/V2228436368/F1 - Verifies that CRLF header injection attacks fail across all engines
    @Test
    fun testCrlfInHeaderValueWithMultipleInjectedHeaders() = testEngines {
        test { _, client ->
            val maliciousValue = "innocent\r\nx-injected-one: first\r\nx-injected-two: second"

            val call = try {
                client.call(
                    HttpRequest {
                        testSetup()
                        url.path.decoded = "echo-headers"
                        headers.append("x-amz-meta-data", maliciousValue)
                    },
                )
            } catch (_: Exception) {
                return@test
            }

            try {
                val injectedCount = call.response.headers["x-injected-count"]?.toIntOrNull() ?: 0
                assertEquals(0, injectedCount, "CRLF injection detected: server received $injectedCount injected header(s)")
            } finally {
                call.complete()
            }
        }
    }
}
