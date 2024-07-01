/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.readAll
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Convenience method for (test) code generation so that it doesn't have to deal
 * with opt-in requirements
 */
public fun String.encodeAsByteArray(): ByteArray = encodeToByteArray()

/**
 * Assert that the [actual] body is empty
 */
public suspend fun assertEmptyBody(@Suppress("UNUSED_PARAMETER") expected: HttpBody?, actual: HttpBody?) {
    if (actual !is HttpBody.Empty) {
        val actualBody = actual?.readAll()?.decodeToString()
        fail("expected an empty HttpBody; found: `$actualBody`")
    }
}

/**
 * Assert that [actual] == [expected]
 */
public suspend fun assertBytesEqual(expected: HttpBody?, actual: HttpBody?) {
    val actualRead = actual?.readAll()
    val expectedRead = expected?.readAll()
    assertBytesEqual(expectedRead, actualRead)
}

/**
 * Assert that [actual] == [expected]
 */
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

public fun assertContentEquals(expected: List<ByteArray>?, actual: List<ByteArray>?) {
    if (expected == null) {
        assertNull(actual, "expected no content, found list with ${actual?.size} elements")
        return
    }

    if (actual == null) {
        fail("Expected content, actual content was null")
    }

    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEach { (a, b) ->
        assertBytesEqual(a, b)
    }
}

/**
 * Check if the given [ExpectedHttpRequestBuilder.bodyMediaType] or [ExpectedHttpResponse.bodyMediaType]
 * is a binary media type. This means that the content must be base64-encoded / decoded prior to validation.
 *
 * https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html
 * > Because the body parameter is a string, binary data MUST be represented in body by base64 encoding the data (for example, use "Zm9vCg==" and not "foo").
 */
public val String.isBinaryMediaType: Boolean
    get() = listOf(
        "application/cbor",
    ).contains(this.lowercase())
