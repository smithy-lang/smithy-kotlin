/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.*
import kotlin.uuid.Uuid

class WritePermissionsJVMTest {

    private fun tempPath(): String = Files.createTempDirectory("perms-test")
        .resolve("test-${Uuid.random()}.bin")
        .toAbsolutePath()
        .toString()

    @Test
    fun testWriteWithPermissions600() = runTest {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return@runTest

        val path = tempPath()
        ps.writeFile(path, "secret".encodeToByteArray(), WriteType.OVERWRITE, permissions = "600")

        val perms = Files.getPosixFilePermissions(File(path).toPath())
        assertEquals(PosixFilePermissions.fromString("rw-------"), perms)
        assertContentEquals("secret".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testWriteWithPermissions644() = runTest {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return@runTest

        val path = tempPath()
        ps.writeFile(path, "data".encodeToByteArray(), WriteType.OVERWRITE, permissions = "644")

        val perms = Files.getPosixFilePermissions(File(path).toPath())
        assertEquals(PosixFilePermissions.fromString("rw-r--r--"), perms)
    }

    @Test
    fun testWritePermissionsIgnoredOnExistingFile() = runTest {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return@runTest

        val path = tempPath()
        ps.writeFile(path, "first".encodeToByteArray(), WriteType.OVERWRITE, permissions = "644")
        val originalPerms = Files.getPosixFilePermissions(File(path).toPath())

        ps.writeFile(path, "second".encodeToByteArray(), WriteType.OVERWRITE, permissions = "600")

        assertEquals(originalPerms, Files.getPosixFilePermissions(File(path).toPath()))
        assertContentEquals("second".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testWriteAppendWithPermissionsOnNewFile() = runTest {
        val ps = PlatformProvider.System
        if (ps.osInfo().family == OsFamily.Windows) return@runTest

        val path = tempPath()
        ps.writeFile(path, "hello".encodeToByteArray(), WriteType.APPEND, permissions = "600")

        val perms = Files.getPosixFilePermissions(File(path).toPath())
        assertEquals(PosixFilePermissions.fromString("rw-------"), perms)
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
