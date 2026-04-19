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
    fun testWriteOverwrite() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "hello".encodeToByteArray(), WriteType.OVERWRITE)
        ps.write(path, "world".encodeToByteArray(), WriteType.OVERWRITE)

        assertContentEquals("world".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testWriteAppend() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "hello".encodeToByteArray(), WriteType.APPEND)
        ps.write(path, "world".encodeToByteArray(), WriteType.APPEND)

        assertContentEquals("helloworld".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testWriteOffset() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "hello".encodeToByteArray(), WriteType.OVERWRITE)
        ps.write(path, "XY".encodeToByteArray(), WriteType.OFFSET(1))

        assertContentEquals("hXYlo".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testWriteOffsetNewFile() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "abc".encodeToByteArray(), WriteType.OFFSET(2))

        assertContentEquals("\u0000\u0000abc".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testWriteMustExistThrows() {
        val ps = PlatformProvider.System
        val path = tempPath()

        assertFails {
            ps.write(path, "data".encodeToByteArray(), WriteType.OVERWRITE, mustExist = true)
        }
    }

    @Test
    fun testWriteMustExistSucceeds() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "initial".encodeToByteArray(), WriteType.OVERWRITE)
        ps.write(path, "updated".encodeToByteArray(), WriteType.OVERWRITE, mustExist = true)

        assertContentEquals("updated".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testReadAll() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "hello".encodeToByteArray(), WriteType.OVERWRITE)

        assertContentEquals("hello".encodeToByteArray(), ps.read(path, readAll = true))
    }

    @Test
    fun testReadAmountAndOffset() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "hello world".encodeToByteArray(), WriteType.OVERWRITE)

        assertContentEquals("world".encodeToByteArray(), ps.read(path, amount = 5, offset = 6))
    }

    @Test
    fun testReadMustExistThrows() {
        val ps = PlatformProvider.System
        assertFails { ps.read(tempPath(), readAll = true) }
    }

    @Test
    fun testSize() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "hello".encodeToByteArray(), WriteType.OVERWRITE)

        assertEquals(5L, ps.size(path))
    }

    @Test
    fun testSizeMustExistThrows() {
        val ps = PlatformProvider.System
        assertFails { ps.size(tempPath()) }
    }

    @Test
    fun testDelete() {
        val ps = PlatformProvider.System
        val path = tempPath()

        ps.write(path, "data".encodeToByteArray(), WriteType.OVERWRITE)
        assertTrue(ps.fileExists(path))

        ps.delete(path)
        assertFalse(ps.fileExists(path))
    }

    @Test
    fun testDeleteMustExistThrows() {
        val ps = PlatformProvider.System
        assertFails { ps.delete(tempPath()) }
    }

    @Test
    fun testDeleteMustExistFalse() {
        val ps = PlatformProvider.System
        ps.delete(tempPath(), mustExist = false) // should not throw
    }

    @Test
    fun testAtomicMove() {
        val ps = PlatformProvider.System
        val src = tempPath()
        val dst = tempPath()

        ps.write(src, "data".encodeToByteArray(), WriteType.OVERWRITE)
        ps.atomicMove(src, dst)

        assertFalse(ps.fileExists(src))
        assertContentEquals("data".encodeToByteArray(), ps.read(dst, readAll = true))
    }

    @Test
    fun testAtomicMoveMustExistThrows() {
        val ps = PlatformProvider.System
        assertFails { ps.atomicMove(tempPath(), tempPath()) }
    }

    @Test
    fun testAtomicMoveOverwriteFalseThrows() {
        val ps = PlatformProvider.System
        val src = tempPath()
        val dst = tempPath()

        ps.write(src, "a".encodeToByteArray(), WriteType.OVERWRITE)
        ps.write(dst, "b".encodeToByteArray(), WriteType.OVERWRITE)

        assertFails { ps.atomicMove(src, dst, overwrite = false) }
    }

    @Test
    fun testAtomicMoveOverwriteTrue() {
        val ps = PlatformProvider.System
        val src = tempPath()
        val dst = tempPath()

        ps.write(src, "new".encodeToByteArray(), WriteType.OVERWRITE)
        ps.write(dst, "old".encodeToByteArray(), WriteType.OVERWRITE)

        ps.atomicMove(src, dst, overwrite = true)

        assertFalse(ps.fileExists(src))
        assertContentEquals("new".encodeToByteArray(), ps.read(dst, readAll = true))
    }

    @Test
    fun testCreateDirAndList() {
        val ps = PlatformProvider.System
        val dir = tempPath()

        ps.createDir(dir)
        ps.write("$dir/a.txt", "a".encodeToByteArray(), WriteType.OVERWRITE)
        ps.write("$dir/b.txt", "b".encodeToByteArray(), WriteType.OVERWRITE)

        val entries = ps.list(dir)
        assertEquals(setOf("a.txt", "b.txt"), entries.toSet())
    }

    @Test
    fun testListMustExistThrows() {
        val ps = PlatformProvider.System
        assertFails { ps.list(tempPath()) }
    }
}
