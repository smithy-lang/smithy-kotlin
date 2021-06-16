/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.smithy.test

import io.kotest.matchers.string.shouldContain
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertFails

class UtilsTest {

    private val testBodyContents = "bueller...bueller".encodeAsByteArray()
    private val testBody = ByteArrayContent(testBodyContents)

    @Test
    fun itComparesEmptyBodies() = runSuspendTest {
        val ex = assertFails {
            assertEmptyBody(null, testBody)
        }
        ex.message!!.shouldContain("expected an empty HttpBody; found: `bueller...bueller`")

        assertEmptyBody(null, HttpBody.Empty)
    }

    @Test
    fun itComparesBytes() = runSuspendTest {
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
