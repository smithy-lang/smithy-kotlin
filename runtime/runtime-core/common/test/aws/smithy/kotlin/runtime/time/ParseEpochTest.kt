/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.time

import kotlin.test.Test
import kotlin.test.assertEquals

class ParseEpochTest {
    @Test
    fun itParsesEpochTimestamps() {
        val tests = listOf(
            Triple("0", 0L, 0),
            Triple("1200", 1200L, 0),
            Triple("1594911364", 1594911364L, 0),
            Triple("1604588357", 1604588357L, 0),
            Triple("1604588357.1", 1604588357L, 100_000_000),
            Triple("1604588357.0345", 1604588357L, 34_500_000),
            Triple("1604588357.000001", 1604588357L, 1000),
            Triple("1604588357.000000001", 1604588357L, 1),
            Triple("1.604588357E9", 1604588357L, 0),
            Triple("1604.588357E6", 1604588357L, 0),
            Triple("1604588357000E-3", 1604588357L, 0),
            Triple("0.001604588357E12", 1604588357L, 0),
            Triple("1.6045883570345E9", 1604588357L, 34_500_000),
            Triple("1.604588357000001E9", 1604588357L, 1000),
            Triple("1.604588357000000001E9", 1604588357L, 1),
        )

        for ((idx, test) in tests.withIndex()) {
            val actual = parseEpoch(test.first)
            assertEquals(test.second, actual.epochSeconds, "test[$idx]: failed to correctly parse seconds: ${test.first}")
            assertEquals(test.third, actual.nanosecondsOfSecond, "test[$idx]: failed to correctly parse nanos: ${test.first}")
        }
    }
}
