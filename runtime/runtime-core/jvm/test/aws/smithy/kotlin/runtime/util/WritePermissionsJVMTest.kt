/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.*

class WritePermissionsJVMTest {

    private fun tempPath(): String = Files.createTempDirectory("perms-test")
        .resolve("test-${Uuid.random()}.bin")
        .toAbsolutePath()
        .toString()

    @Test
    fun testWriteWithPermissions600() {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return

        val path = tempPath()
        ps.write(path, "secret".encodeToByteArray(), WriteType.OVERWRITE, permissions = "600")

        val perms = Files.getPosixFilePermissions(File(path).toPath())
        assertEquals(PosixFilePermissions.fromString("rw-------"), perms)
        assertContentEquals("secret".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testWriteWithPermissions644() {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return

        val path = tempPath()
        ps.write(path, "data".encodeToByteArray(), WriteType.OVERWRITE, permissions = "644")

        val perms = Files.getPosixFilePermissions(File(path).toPath())
        assertEquals(PosixFilePermissions.fromString("rw-r--r--"), perms)
    }

    @Test
    fun testWritePermissionsIgnoredOnExistingFile() {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return

        val path = tempPath()
        ps.write(path, "first".encodeToByteArray(), WriteType.OVERWRITE, permissions = "644")
        val originalPerms = Files.getPosixFilePermissions(File(path).toPath())

        ps.write(path, "second".encodeToByteArray(), WriteType.OVERWRITE, permissions = "600")

        assertEquals(originalPerms, Files.getPosixFilePermissions(File(path).toPath()))
        assertContentEquals("second".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testWriteAppendWithPermissionsOnNewFile() {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return

        val path = tempPath()
        ps.write(path, "hello".encodeToByteArray(), WriteType.APPEND, permissions = "600")

        val perms = Files.getPosixFilePermissions(File(path).toPath())
        assertEquals(PosixFilePermissions.fromString("rw-------"), perms)
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
