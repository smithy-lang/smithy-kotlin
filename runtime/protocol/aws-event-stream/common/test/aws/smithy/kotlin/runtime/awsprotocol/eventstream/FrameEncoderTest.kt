/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.awsprotocol.eventstream

import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.io.SdkBuffer
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FrameEncoderTest {
    @Test
    fun testEncode() = runTest {
        val expected = listOf(
            validMessageWithAllHeaders(),
            validMessageEmptyPayload(),
            validMessageNoHeaders(),
        )

        val message1 = Message.decode(sdkBufferOf(validMessageWithAllHeaders()))
        val message2 = Message.decode(sdkBufferOf(validMessageEmptyPayload()))
        val message3 = Message.decode(sdkBufferOf(validMessageNoHeaders()))

        val messages = flowOf(
            message1,
            message2,
            message3,
        )

        val actual = messages.encode().toList()

        assertEquals(3, actual.size)
        assertContentEquals(expected[0], actual[0].readByteArray())
        assertContentEquals(expected[1], actual[1].readByteArray())
        assertContentEquals(expected[2], actual[2].readByteArray())
    }

    @Test
    fun testAsEventStreamHttpBody() = runTest {
        val messages = flowOf(
            "foo",
            "bar",
            "baz",
        ).map { SdkBuffer().apply { writeUtf8(it) } }

        val body = messages.asEventStreamHttpBody(this)
        val actual = body.readAll()
        val expected = "foobarbaz"
        assertEquals(expected, actual?.decodeToString())
    }
}
