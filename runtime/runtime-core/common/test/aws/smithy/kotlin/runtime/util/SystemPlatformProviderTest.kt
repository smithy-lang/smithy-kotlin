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
            ps.getenv("TEMP") ?: "C:\\Windows\\Temp"
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
        val result: String? = envVarKeys.fold(null) { acc, curr -> acc ?: PlatformProvider.System.getenv(curr) }
        assertNotNull(result)

        assertNull(PlatformProvider.System.getenv("THIS_ENV_VAR_IS_NOT_SET"))
    }

    @Test
    fun testGetAllEnvVars() = runTest {
        val allEnv = PlatformProvider.System.getAllEnvVars()
        assertTrue(allEnv.isNotEmpty())

        val envVarKeys = listOf("PATH", "USERPROFILE") // PATH is not set on Windows CI

        var envContainsKey = false
        envVarKeys.forEach { key ->
            envContainsKey = envContainsKey || allEnv.contains(key)
        }
        assertTrue(envContainsKey)
    }

    @Test
    fun testOsInfo() = runTest {
        val osInfo = PlatformProvider.System.osInfo()
        assertNotEquals(OsFamily.Unknown, osInfo.family)
    }
}
