/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFails

@OptIn(ExperimentalCoroutinesApi::class)
class UtilsTest {

    private val testBodyContents = "bueller...bueller".encodeAsByteArray()
    private val testBody = ByteArrayContent(testBodyContents)

    @Test
    fun itComparesEmptyBodies() = runTest {
        val ex = assertFails {
            assertEmptyBody(null, testBody)
        }
        ex.message!!.shouldContain("expected an empty HttpBody; found: `bueller...bueller`")

        assertEmptyBody(null, HttpBody.Empty)
    }

    @Test
    fun itComparesBytes() = runTest {
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
