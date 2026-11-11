/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(PlannedRemoval::class)
@file:Suppress("DEPRECATION")

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.PlannedRemoval
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Covers the deprecated blocking [Filesystem] members, which still ship until they are removed in 1.8.
 */
class SystemPlatformProviderBlockingTest {
    private val ps = PlatformProvider.System

    private fun tempPath(): String {
        val tempDir = if (ps.osInfo().family == OsFamily.Windows) {
            requireNotNull(ps.getenv("TEMP")) { "%TEMP% unexpectedly null" }
        } else {
            "/tmp"
        }
        return "$tempDir/testBlocking-${Uuid.random()}.bin"
    }

    @Test
    fun testWriteAndRead() {
        val path = tempPath()
        ps.write(path, "hello world".encodeToByteArray(), WriteType.OVERWRITE)
        ps.write(path, "XY".encodeToByteArray(), WriteType.OFFSET(1))
        ps.write(path, "!".encodeToByteArray(), WriteType.APPEND)

        assertContentEquals("hXYlo world!".encodeToByteArray(), ps.read(path, readAll = true))
        assertContentEquals("world".encodeToByteArray(), ps.read(path, amount = 5, offset = 6))
        assertTrue(ps.exists(path))
        assertTrue(ps.fileExists(path))
        assertEquals(12L, ps.size(path))
        ps.delete(path)
    }

    @Test
    fun testMissingFile() {
        val path = tempPath()
        assertFalse(ps.exists(path))
        assertFails { ps.read(path, readAll = true) }
        assertFails { ps.readOrNull(path, readAll = true) }
        assertNull(ps.readOrNull(path, readAll = true, mustExist = false))
        assertFails { ps.size(path) }
        assertFails { ps.list(path) }
        assertFails { ps.delete(path) }
        ps.delete(path, mustExist = false)
        assertFails { ps.atomicMove(path, tempPath()) }
        assertFails { ps.write(path, byteArrayOf(), WriteType.OVERWRITE, mustExist = true) }
    }

    @Test
    fun testAtomicMove() {
        val src = tempPath()
        val dst = tempPath()
        ps.write(src, "new".encodeToByteArray(), WriteType.OVERWRITE)
        ps.write(dst, "old".encodeToByteArray(), WriteType.OVERWRITE)

        assertFails { ps.atomicMove(src, dst, overwrite = false) }
        ps.atomicMove(src, dst, overwrite = true)

        assertFalse(ps.exists(src))
        assertContentEquals("new".encodeToByteArray(), ps.read(dst, readAll = true))
        ps.delete(dst)
    }

    @Test
    fun testCreateDirAndList() {
        val dir = tempPath()
        ps.createDir(dir)
        ps.write("$dir/a.txt", "a".encodeToByteArray(), WriteType.OVERWRITE)

        assertEquals(setOf("a.txt"), ps.list(dir).toSet())
        ps.delete("$dir/a.txt")
        ps.delete(dir)
    }
}
