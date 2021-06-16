/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.time

// shared utils for various timestamp parsers

internal data class ParseTest(val input: String, val year: Int, val month: Int, val day: Int, val hour: Int, val min: Int, val sec: Int, val ns: Int, val offsetSec: Int = 0) {
    val expected: ParsedDatetime = ParsedDatetime(year, month, day, hour, min, sec, ns, offsetSec)
}

internal data class ParseErrorTest(val input: String, val expectedMessage: String)
