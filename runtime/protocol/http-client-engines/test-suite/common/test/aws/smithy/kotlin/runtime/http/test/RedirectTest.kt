/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.test
import aws.smithy.kotlin.runtime.http.test.util.testSetup
import kotlin.test.Test
import kotlin.test.assertEquals

class RedirectTest : AbstractEngineTest() {

    @Test
    fun testDoNotFollow301() = testDoNotFollow(HttpStatusCode.MovedPermanently, "/redirect/permanent")

    @Test
    fun testDoNotFollow302() = testDoNotFollow(HttpStatusCode.Found, "/redirect/found")

    private fun testDoNotFollow(expectedStatus: HttpStatusCode, path: String) = testEngines {
        test { env, client ->
            val req = HttpRequest {
                testSetup(env)
                url.path = path
            }

            val call = client.call(req)
            call.complete()
            assertEquals(expectedStatus, call.response.status)
        }
    }
}
