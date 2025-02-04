/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemPlatformProviderTest {
    @Test
    fun testReadWriteFile() = runTest {
        val ps = PlatformProvider.System

        val tempDir = if (ps.osInfo().family == OsFamily.Windows) {
            requireNotNull(ps.getenv("TEMP")) { "%TEMP% unexpectedly null" }
        } else {
            "/tmp"
        }
        val path = "$tempDir/testReadWriteFile-${Uuid.random()}.txt"

        val expected = "Hello, File!".encodeToByteArray()

        ps.writeFile(path, expected)
        assertTrue(ps.fileExists(path))

        val actual = ps.readFileOrNull(path)
        assertContentEquals(expected, actual)
    }

    @Test
    fun testGetEnv() = runTest {
        val envVarKeys = listOf("PATH", "USERPROFILE") // PATH is not set on Windows CI
        assertNotNull(
            envVarKeys.firstNotNullOfOrNull { PlatformProvider.System.getenv(it) }
        )

        assertNull(PlatformProvider.System.getenv("THIS_ENV_VAR_IS_NOT_SET"))
    }

    @Test
    fun testGetAllEnvVars() = runTest {
        val allEnv = PlatformProvider.System.getAllEnvVars()
        assertTrue(allEnv.isNotEmpty())

        val envVarKeys = listOf("PATH", "USERPROFILE") // PATH is not set on Windows CI
        assertTrue(
            envVarKeys.any { allEnv.contains(it) }
        )
    }

    @Test
    fun testOsInfo() = runTest {
        val osInfo = PlatformProvider.System.osInfo()
        assertNotEquals(OsFamily.Unknown, osInfo.family)
    }
}
