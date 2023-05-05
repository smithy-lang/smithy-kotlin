/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.readAll
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals

/**
 * Assert JSON strings for equality ignoring key order
 */
public fun assertJsonStringsEqual(expected: String, actual: String) {

    val expectedElement = Json.parseToJsonElement(expected)
    val actualElement = Json.parseToJsonElement(actual)

    assertEquals(expectedElement, actualElement, "expected JSON:\n\n$expected\n\nactual:\n\n$actual\n")
}

/**
 * Assert HTTP bodies are equal as JSON documents
 */
public suspend fun assertJsonBodiesEqual(expected: HttpBody?, actual: HttpBody?) {
    val expectedStr = expected?.readAll()?.decodeToString()
    val actualStr = actual?.readAll()?.decodeToString()
    if (expectedStr == null && actualStr == null) {
        return
    }

    requireNotNull(expectedStr) { "expected JSON body cannot be null" }
    requireNotNull(actualStr) { "actual JSON body cannot be null" }

    assertJsonStringsEqual(expectedStr, actualStr)
}
