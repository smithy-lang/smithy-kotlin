/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.readAll
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Convenience method for (test) code generation so that it doesn't have to deal
 * with opt-in requirements
 */
@OptIn(ExperimentalStdlibApi::class)
public fun String.encodeAsByteArray(): ByteArray = encodeToByteArray()

/**
 * Assert that the [actual] body is empty
 */
@OptIn(ExperimentalStdlibApi::class)
public suspend fun assertEmptyBody(@Suppress("UNUSED_PARAMETER") expected: HttpBody?, actual: HttpBody?) {
    if (actual !is HttpBody.Empty) {
        val actualBody = actual?.readAll()?.decodeToString()
        fail("expected an empty HttpBody; found: `$actualBody`")
    }
}

/**
 * Assert that [actual] == [expected]
 */
@OptIn(ExperimentalStdlibApi::class)
public suspend fun assertBytesEqual(expected: HttpBody?, actual: HttpBody?) {
    val actualRead = actual?.readAll()
    val expectedRead = expected?.readAll()
    assertBytesEqual(expectedRead, actualRead)
}

/**
 * Assert that [actual] == [expected]
 */
@OptIn(ExperimentalStdlibApi::class)
public fun assertBytesEqual(expected: ByteArray?, actual: ByteArray?) {
    if (expected == null) {
        assertNull(actual, "expected no content; found ${actual?.decodeToString()}")
        return
    }

    if (actual == null) {
        fail("expected content; actual content was null")
    }

    assertTrue(
        expected.contentEquals(actual),
        "actual bytes read does not match expected: \n" +
            "expected: `${expected.decodeToString()}`\n" +
            "actual: `${actual.decodeToString()}`",
    )
}
