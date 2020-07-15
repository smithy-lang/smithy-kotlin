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

import kotlin.test.Test
import kotlin.test.assertEquals

class ParseEpochTest {
    @Test
    fun `it parses epoch timestamps`() {
        val tests = listOf(
            Triple("0", 0L, 0),
            Triple("1200", 1200L, 0),
            Triple("1594911364", 1594911364L, 0),
            Triple("1604588357", 1604588357L, 0),
            Triple("1604588357.1", 1604588357L, 100_000_000),
            Triple("1604588357.0345", 1604588357L, 34_500_000),
            Triple("1604588357.000001", 1604588357L, 1000),
            Triple("1604588357.000000001", 1604588357L, 1)
        )

        for ((idx, test) in tests.withIndex()) {
            val actual = parseEpoch(test.first)
            assertEquals(test.second, actual.epochSeconds, "test[$idx]: failed to correctly parse seconds: ${test.first}")
            assertEquals(test.third, actual.nanosecondsOfSecond, "test[$idx]: failed to correctly parse nanos: ${test.first}")
        }
    }
}
