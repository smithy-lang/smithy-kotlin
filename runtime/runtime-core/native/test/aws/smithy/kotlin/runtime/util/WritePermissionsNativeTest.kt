/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.cinterop.*
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
    fun testWriteWithPermissions600() {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return

        val path = tempPath()
        ps.write(path, "secret".encodeToByteArray(), WriteType.OVERWRITE, permissions = "600")

        assertEquals("600".toInt(8), filePermissions(path))
        assertContentEquals("secret".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testWriteWithPermissions644() {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return

        val path = tempPath()
        ps.write(path, "data".encodeToByteArray(), WriteType.OVERWRITE, permissions = "644")

        assertEquals("644".toInt(8), filePermissions(path))
    }

    @Test
    fun testWritePermissionsIgnoredOnExistingFile() {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return

        val path = tempPath()
        ps.write(path, "first".encodeToByteArray(), WriteType.OVERWRITE, permissions = "644")
        val originalPerms = filePermissions(path)

        ps.write(path, "second".encodeToByteArray(), WriteType.OVERWRITE, permissions = "600")

        assertEquals(originalPerms, filePermissions(path))
        assertContentEquals("second".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testWriteAppendWithPermissionsOnNewFile() {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return

        val path = tempPath()
        ps.write(path, "hello".encodeToByteArray(), WriteType.APPEND, permissions = "600")

        assertEquals("600".toInt(8), filePermissions(path))
        assertContentEquals("hello".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testWriteWithNullPermissions() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "data".encodeToByteArray(), WriteType.OVERWRITE)

        assertTrue(ps.fileExists(path))
        assertContentEquals("data".encodeToByteArray(), ps.read(path, readAll = true))
    }
}
