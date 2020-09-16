/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.smithy.test

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertFails
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.content.ByteArrayContent
import software.aws.clientrt.testing.runSuspendTest

class UtilsTest {

    private val testBodyContents = "bueller...bueller".encodeAsByteArray()
    private val testBody = ByteArrayContent(testBodyContents)

    @Test
    fun `it compares empty bodies`() = runSuspendTest {
        val ex = assertFails {
            assertEmptyBody(null, testBody)
        }
        ex.message!!.shouldContain("expected an empty HttpBody; found: `bueller...bueller`")

        assertEmptyBody(null, HttpBody.Empty)
    }

    @Test
    fun `it compares bytes`() = runSuspendTest {
        val ex = assertFails {
            assertBytesEqual(null, testBody)
        }
        ex.message!!.shouldContain("expected no content")
        assertBytesEqual(null, HttpBody.Empty)

        val ex2 = assertFails {
            assertBytesEqual(testBody, HttpBody.Empty)
        }
        ex2.message!!.shouldContain("actual content was null")

        val ex3 = assertFails {
            assertBytesEqual(ByteArrayContent("foo".encodeAsByteArray()), testBody)
        }
        ex3.message.shouldContain("actual bytes read does not match expected")

        assertBytesEqual(ByteArrayContent(testBodyContents), testBody)
    }
}
