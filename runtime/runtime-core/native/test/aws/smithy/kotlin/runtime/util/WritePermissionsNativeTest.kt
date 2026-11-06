/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.cinterop.*
import kotlinx.coroutines.test.runTest
import platform.posix.*
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class)
class WritePermissionsNativeTest {

    private fun tempPath(): String = "/tmp/testWritePerms-${Uuid.random()}.bin"

    @OptIn(UnsafeNumber::class)
    private fun filePermissions(path: String): Int = memScoped {
        val statResult = alloc<stat>()
        check(stat(path, statResult.ptr) == 0) { "stat failed for $path" }
        (statResult.st_mode.toInt() and (S_IRWXU or S_IRWXG or S_IRWXO))
    }

    @Test
    fun testWriteWithPermissions600() = runTest {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return@runTest

        val path = tempPath()
        ps.writeFile(path, "secret".encodeToByteArray(), WriteType.OVERWRITE, permissions = "600")

        assertEquals("600".toInt(8), filePermissions(path))
        assertContentEquals("secret".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testWriteWithPermissions644() = runTest {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return@runTest

        val path = tempPath()
        ps.writeFile(path, "data".encodeToByteArray(), WriteType.OVERWRITE, permissions = "644")

        assertEquals("644".toInt(8), filePermissions(path))
    }

    @Test
    fun testWritePermissionsIgnoredOnExistingFile() = runTest {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return@runTest

        val path = tempPath()
        ps.writeFile(path, "first".encodeToByteArray(), WriteType.OVERWRITE, permissions = "644")
        val originalPerms = filePermissions(path)

        ps.writeFile(path, "second".encodeToByteArray(), WriteType.OVERWRITE, permissions = "600")

        assertEquals(originalPerms, filePermissions(path))
        assertContentEquals("second".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testWriteAppendWithPermissionsOnNewFile() = runTest {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return@runTest

        val path = tempPath()
        ps.writeFile(path, "hello".encodeToByteArray(), WriteType.APPEND, permissions = "600")

        assertEquals("600".toInt(8), filePermissions(path))
        assertContentEquals("hello".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testWriteWithNullPermissions() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "data".encodeToByteArray(), WriteType.OVERWRITE)

        assertTrue(ps.pathExists(path))
        assertContentEquals("data".encodeToByteArray(), ps.readFile(path))
    }
}
