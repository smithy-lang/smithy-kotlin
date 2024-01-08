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
}
