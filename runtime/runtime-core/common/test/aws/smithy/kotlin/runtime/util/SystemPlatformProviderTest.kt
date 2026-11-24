/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

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

        ps.writeFile(path, expected, WriteType.OVERWRITE)
        assertTrue(ps.pathExists(path))

        val actual = ps.readFileOrNull(path)
        assertContentEquals(expected, actual)
    }

    @Test
    fun testReadFileOrNullReturnsNullOnIoError() = runTest {
        // Reading a directory as a file is an IO error on every platform
        val ps = PlatformProvider.System
        val dir = tempPath()
        ps.createDirectories(dir)
        try {
            assertNull(ps.readFileOrNull(dir))
        } finally {
            ps.remove(dir)
        }
    }

    @Test
    fun testGetEnv() = runTest {
        val envVarKeys = listOf("PATH", "USERPROFILE") // PATH is not set on Windows CI
        assertNotNull(
            envVarKeys.firstNotNullOfOrNull { PlatformProvider.System.getenv(it) },
        )

        assertNull(PlatformProvider.System.getenv("THIS_ENV_VAR_IS_NOT_SET"))
    }

    @Test
    fun testGetAllEnvVars() = runTest {
        val allEnv = PlatformProvider.System.getAllEnvVars()
        assertTrue(allEnv.isNotEmpty())

        val envVarKeys = listOf("PATH", "USERPROFILE") // PATH is not set on Windows CI
        assertTrue(
            envVarKeys.any { allEnv.contains(it) },
        )
    }

    @Test
    fun testOsInfo() = runTest {
        val osInfo = PlatformProvider.System.osInfo()
        assertNotEquals(OsFamily.Unknown, osInfo.family)
    }

    private fun tempPath(): String {
        val ps = PlatformProvider.System
        val tempDir = if (ps.osInfo().family == OsFamily.Windows) {
            requireNotNull(ps.getenv("TEMP")) { "%TEMP% unexpectedly null" }
        } else {
            "/tmp"
        }
        return "$tempDir/testWrite-${Uuid.random()}.bin"
    }

    @Test
    fun testWriteOverwrite() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "hello".encodeToByteArray(), WriteType.OVERWRITE)
        ps.writeFile(path, "world".encodeToByteArray(), WriteType.OVERWRITE)

        assertContentEquals("world".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testWriteAppend() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "hello".encodeToByteArray(), WriteType.APPEND)
        ps.writeFile(path, "world".encodeToByteArray(), WriteType.APPEND)

        assertContentEquals("helloworld".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testWriteOffset() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "hello".encodeToByteArray(), WriteType.OVERWRITE)
        ps.writeFile(path, "XY".encodeToByteArray(), WriteType.OFFSET(1))

        assertContentEquals("hXYlo".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testWriteOffsetNewFile() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "abc".encodeToByteArray(), WriteType.OFFSET(2))

        assertContentEquals("\u0000\u0000abc".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testWriteMustExistThrows() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        assertFails {
            ps.writeFile(path, "data".encodeToByteArray(), WriteType.OVERWRITE, mustExist = true)
        }
    }

    @Test
    fun testWriteMustExistSucceeds() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "initial".encodeToByteArray(), WriteType.OVERWRITE)
        ps.writeFile(path, "updated".encodeToByteArray(), WriteType.OVERWRITE, mustExist = true)

        assertContentEquals("updated".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testReadAll() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "hello".encodeToByteArray(), WriteType.OVERWRITE)

        assertContentEquals("hello".encodeToByteArray(), ps.readFile(path))
    }

    @Test
    fun testReadAmountAndOffset() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "hello world".encodeToByteArray(), WriteType.OVERWRITE)

        assertContentEquals("world".encodeToByteArray(), ps.readFile(path, offset = 6, length = 5))
    }

    @Test
    fun testReadMustExistThrows() = runTest {
        val ps = PlatformProvider.System
        assertFails { ps.readFile(tempPath()) }
    }

    @Test
    fun testFileSize() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "hello".encodeToByteArray(), WriteType.OVERWRITE)

        assertEquals(5L, ps.fileSize(path))
    }

    @Test
    fun testFileSizeMissingThrows() = runTest {
        val ps = PlatformProvider.System
        assertFails { ps.fileSize(tempPath()) }
    }

    @Test
    fun testPathExistsMissing() = runTest {
        val ps = PlatformProvider.System
        assertFalse(ps.pathExists(tempPath()))
    }

    @Test
    fun testRemove() = runTest {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.writeFile(path, "data".encodeToByteArray(), WriteType.OVERWRITE)
        assertTrue(ps.pathExists(path))

        ps.remove(path)
        assertFalse(ps.pathExists(path))
    }

    @Test
    fun testRemoveMustExistThrows() = runTest {
        val ps = PlatformProvider.System
        assertFails { ps.remove(tempPath()) }
    }

    @Test
    fun testRemoveMustExistFalse() = runTest {
        val ps = PlatformProvider.System
        ps.remove(tempPath(), mustExist = false) // should not throw
    }

    @Test
    fun testRename() = runTest {
        val ps = PlatformProvider.System
        val src = tempPath()
        val dst = tempPath()

        ps.writeFile(src, "data".encodeToByteArray(), WriteType.OVERWRITE)
        ps.rename(src, dst)

        assertFalse(ps.pathExists(src))
        assertContentEquals("data".encodeToByteArray(), ps.readFile(dst))
    }

    @Test
    fun testRenameMustExistThrows() = runTest {
        val ps = PlatformProvider.System
        assertFails { ps.rename(tempPath(), tempPath()) }
    }

    @Test
    fun testRenameOverwriteFalseThrows() = runTest {
        val ps = PlatformProvider.System
        val src = tempPath()
        val dst = tempPath()

        ps.writeFile(src, "a".encodeToByteArray(), WriteType.OVERWRITE)
        ps.writeFile(dst, "b".encodeToByteArray(), WriteType.OVERWRITE)

        assertFails { ps.rename(src, dst, overwrite = false) }
    }

    @Test
    fun testRenameOverwriteTrue() = runTest {
        val ps = PlatformProvider.System
        val src = tempPath()
        val dst = tempPath()

        ps.writeFile(src, "new".encodeToByteArray(), WriteType.OVERWRITE)
        ps.writeFile(dst, "old".encodeToByteArray(), WriteType.OVERWRITE)

        ps.rename(src, dst, overwrite = true)

        assertFalse(ps.pathExists(src))
        assertContentEquals("new".encodeToByteArray(), ps.readFile(dst))
    }

    @Test
    fun testCreateDirectoriesAndList() = runTest {
        val ps = PlatformProvider.System
        val dir = tempPath()

        ps.createDirectories(dir)
        ps.writeFile("$dir/a.txt", "a".encodeToByteArray(), WriteType.OVERWRITE)
        ps.writeFile("$dir/b.txt", "b".encodeToByteArray(), WriteType.OVERWRITE)

        val entries = ps.listDirectory(dir)
        assertEquals(setOf("a.txt", "b.txt"), entries.toSet())
    }

    @Test
    fun testListMustExistThrows() = runTest {
        val ps = PlatformProvider.System
        assertFails { ps.listDirectory(tempPath()) }
    }
}
