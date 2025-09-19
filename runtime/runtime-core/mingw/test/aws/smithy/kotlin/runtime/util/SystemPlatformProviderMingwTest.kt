/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SystemPlatformProviderMingwTest {
    @Test
    fun testOsInfo() = runTest {
        val osInfo = PlatformProvider.System.osInfo()
        println(osInfo)
        assertEquals(OsFamily.Windows, osInfo.family)
        assertNotNull(osInfo.version)
    }
}
