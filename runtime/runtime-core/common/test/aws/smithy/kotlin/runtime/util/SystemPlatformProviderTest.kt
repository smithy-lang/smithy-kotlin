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
        val path = "/tmp/testReadWriteFile-${Uuid.random()}.txt"
        val expected = "Hello, File!".encodeToByteArray()

        try {
            ps.writeFile(path, expected)

            assertTrue(ps.fileExists(path))

            val actual = ps.readFileOrNull(path)
            assertContentEquals(expected, actual)
        } finally {
            ps.deleteFile(path)
        }
    }

    @Test
    fun testGetEnv() = runTest {
        val envVarKeys = listOf("PATH", "USERPROFILE") // PATH is not set on Windows CI...
        val result: String? = envVarKeys.fold(null) { acc, curr -> acc ?: PlatformProvider.System.getenv(curr) }
        assertNotNull(result)

        assertNull(PlatformProvider.System.getenv("THIS_ENV_VAR_IS_NOT_SET"))
    }

    @Test
    fun testOsInfo() = runTest {
        val osInfo = PlatformProvider.System.osInfo()
        assertNotEquals(OsFamily.Unknown, osInfo.family)
    }
}
