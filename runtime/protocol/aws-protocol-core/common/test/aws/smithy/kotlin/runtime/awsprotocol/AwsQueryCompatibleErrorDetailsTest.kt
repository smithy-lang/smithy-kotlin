/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol

import aws.smithy.kotlin.runtime.ServiceException
import kotlin.test.*

class AwsQueryCompatibleErrorDetailsTest {
    @Test
    fun testParseMalformed() {
        val ex = assertFailsWith<IllegalArgumentException> { AwsQueryCompatibleErrorDetails.parse("malformed") }
        assertEquals("value is malformed", ex.message)
    }

    @Test
    fun testParseEmptyCode() {
        val ex = assertFailsWith<IllegalArgumentException> { AwsQueryCompatibleErrorDetails.parse(";type") }
        assertEquals("code is empty", ex.message)
    }

    @Test
    fun testParseEmptyType() {
        val ex = assertFailsWith<IllegalArgumentException> { AwsQueryCompatibleErrorDetails.parse("code;") }
        assertEquals("type is empty", ex.message)
    }

    @Test
    fun testParseErrorClient() {
        val expected = AwsQueryCompatibleErrorDetails(
            "com.test.ErrorCode",
            ServiceException.ErrorType.Client,
        )
        val actual = AwsQueryCompatibleErrorDetails.parse("com.test.ErrorCode;Sender")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseErrorServer() {
        val expected = AwsQueryCompatibleErrorDetails(
            "com.test.ErrorCode",
            ServiceException.ErrorType.Server,
        )
        val actual = AwsQueryCompatibleErrorDetails.parse("com.test.ErrorCode;Receiver")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseErrorUnknown() {
        val expected = AwsQueryCompatibleErrorDetails(
            "com.test.ErrorCode",
            ServiceException.ErrorType.Unknown,
        )
        val actual = AwsQueryCompatibleErrorDetails.parse("com.test.ErrorCode;idk")
        assertEquals(expected, actual)
    }
}
