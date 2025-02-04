/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemPlatformProviderTestLinux {
    @Test
    fun testOsInfo() = runTest {
        val osInfo = PlatformProvider.System.osInfo()
        assertEquals(OsFamily.Linux, osInfo.family)
    }
}