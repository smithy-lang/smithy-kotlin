/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.readAll
import kotlin.test.assertEquals

/**
 * Assert x-www-form-url strings for equality ignoring key order
 */
fun assertFormUrlStringsEqual(expected: String, actual: String) {
    val expectedAsMap = expected.parseAsFormUrlMap()
    val actualAsMap = actual.parseAsFormUrlMap()

    val expectedCanonical = expectedAsMap.entries.sortedBy { it.key }.joinToString(separator = "&")
    val actualCanonical = actualAsMap.entries.sortedBy { it.key }.joinToString(separator = "&")

    assertEquals(expectedCanonical, actualCanonical)
}

/**
 * Assert HTTP bodies are equal as x-www-form-url documents
 */
suspend fun assertFormUrlBodiesEqual(expected: HttpBody?, actual: HttpBody?) {
    val expectedStr = expected?.readAll()?.decodeToString()
    val actualStr = actual?.readAll()?.decodeToString()
    if (expectedStr == null && actualStr == null) {
        return
    }

    requireNotNull(expectedStr) { "expected x-www-form-url body cannot be null" }
    requireNotNull(actualStr) { "actual x-www-form-url body cannot be null" }

    assertFormUrlStringsEqual(expectedStr, actualStr)
}

private fun String.parseAsFormUrlMap(): Map<String, String> =
    split("&").associate {
        val values = it.split("=")
        check(values.size == 2) { "x-www-form-url entry split should be of form `key=value`: found $it" }
        values[0].trim() to values[1].trim()
    }
