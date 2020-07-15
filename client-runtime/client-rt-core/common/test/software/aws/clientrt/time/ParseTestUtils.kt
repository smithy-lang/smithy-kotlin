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
package software.aws.clientrt.time

// shared utils for various timestamp parsers

internal data class ParseTest(val input: String, val year: Int, val month: Int, val day: Int, val hour: Int, val min: Int, val sec: Int, val ns: Int, val offsetSec: Int = 0) {
    val expected: ParsedDatetime = ParsedDatetime(year, month, day, hour, min, sec, ns, offsetSec)
}

internal data class ParseErrorTest(val input: String, val expectedMessage: String)
