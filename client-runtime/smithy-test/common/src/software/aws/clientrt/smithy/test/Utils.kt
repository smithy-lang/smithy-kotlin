/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.smithy.test

import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.readAll

/**
 * Convenience method for (test) code generation so that it doesn't have to deal
 * with opt-in requirements
 */
@OptIn(ExperimentalStdlibApi::class)
fun String.encodeAsByteArray(): ByteArray = encodeToByteArray()

/**
 * Assert that the [actual] body is empty
 */
@OptIn(ExperimentalStdlibApi::class)
suspend fun assertEmptyBody(@Suppress("UNUSED_PARAMETER") expected: HttpBody?, actual: HttpBody?) {
    if (actual !is HttpBody.Empty) {
        val actualBody = actual?.readAll()?.decodeToString()
        fail("expected an empty HttpBody; found: `$actualBody`")
    }
}

/**
 * Assert that [actual] == [expected]
 */
@OptIn(ExperimentalStdlibApi::class)
suspend fun assertBytesEqual(expected: HttpBody?, actual: HttpBody?) {
    val actualRead = actual?.readAll()
    val expectedRead = expected?.readAll()

    if (expectedRead == null) {
        assertNull(actualRead, "expected no body; found ${actualRead?.decodeToString()}")
        return
    }

    if (actualRead == null) {
        fail("expected an HttpBody; actual body was null")
    }

    assertTrue(expectedRead.contentEquals(actualRead),
        "actual body does not match expected: \n" +
                "expected: `${expectedRead.decodeToString()}`\n" +
                "actual: `${actualRead.decodeToString()}`")
}
