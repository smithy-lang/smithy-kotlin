/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import kotlin.test.Test
import kotlin.test.assertEquals

class CloudWatchUnitTest {
    @Test
    fun mapsSecondsUnit() {
        assertEquals(CloudWatchUnit.SECONDS, mapUnitToCloudWatch("s"))
    }

    @Test
    fun mapsMillisecondsUnit() {
        assertEquals(CloudWatchUnit.MILLISECONDS, mapUnitToCloudWatch("ms"))
    }

    @Test
    fun mapsMicrosecondsUnit() {
        assertEquals(CloudWatchUnit.MICROSECONDS, mapUnitToCloudWatch("us"))
    }

    @Test
    fun mapsBytesUnit() {
        assertEquals(CloudWatchUnit.BYTES, mapUnitToCloudWatch("By"))
        assertEquals(CloudWatchUnit.BYTES, mapUnitToCloudWatch("bytes"))
    }

    @Test
    fun mapsKilobytesUnit() {
        assertEquals(CloudWatchUnit.KILOBYTES, mapUnitToCloudWatch("KBy"))
    }

    @Test
    fun mapsMegabytesUnit() {
        assertEquals(CloudWatchUnit.MEGABYTES, mapUnitToCloudWatch("MBy"))
    }

    @Test
    fun mapsGigabytesUnit() {
        assertEquals(CloudWatchUnit.GIGABYTES, mapUnitToCloudWatch("GBy"))
    }

    @Test
    fun mapsCountUnits() {
        assertEquals(CloudWatchUnit.COUNT, mapUnitToCloudWatch("{attempt}"))
        assertEquals(CloudWatchUnit.COUNT, mapUnitToCloudWatch("{error}"))
        assertEquals(CloudWatchUnit.COUNT, mapUnitToCloudWatch("{request}"))
    }

    @Test
    fun mapsPercentUnit() {
        assertEquals(CloudWatchUnit.PERCENT, mapUnitToCloudWatch("%"))
    }

    @Test
    fun nullUnitMapsToNone() {
        assertEquals(CloudWatchUnit.NONE, mapUnitToCloudWatch(null))
    }

    @Test
    fun unknownUnitMapsToNone() {
        assertEquals(CloudWatchUnit.NONE, mapUnitToCloudWatch("unknown_unit"))
    }
}
