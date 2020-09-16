/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.smithy.test

import kotlin.test.assertEquals
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.readAll

/**
 * Assert JSON strings for equality ignoring key order
 */
@OptIn(UnstableDefault::class)
fun assertJsonStringsEqual(expected: String, actual: String) {
    val config = JsonConfiguration()
    val expectedElement = Json(config).parseJson(expected)
    val actualElement = Json(config).parseJson(actual)

    assertEquals(expectedElement, actualElement, "expected JSON:\n\n$expected\n\nactual:\n\n$actual\n")
}

/**
 * Assert HTTP bodies are equal as JSON documents
 */
@OptIn(ExperimentalStdlibApi::class)
suspend fun assertJsonBodiesEqual(expected: HttpBody?, actual: HttpBody?) {
    val expectedStr = expected?.readAll()?.decodeToString()
    val actualStr = actual?.readAll()?.decodeToString()
    if (expectedStr == null && actualStr == null) {
        return
    }

    requireNotNull(expectedStr) { "expected JSON body cannot be null" }
    requireNotNull(actualStr) { "actual JSON body cannot be null" }

    assertJsonStringsEqual(expectedStr, actualStr)
}
