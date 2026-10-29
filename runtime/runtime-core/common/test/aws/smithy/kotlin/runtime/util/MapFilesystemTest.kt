/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapFilesystemTest {
    private fun fs() = MapFilesystem(
        mutableMapOf(
            "/root/a.txt" to TestFile("hello world"),
            "/root/sub/b.txt" to TestFile("b"),
        ),
        filePathSeparator = "/",
    )

    @Test
    fun testReadFile() = runTest {
        val fs = fs()
        assertContentEquals("hello world".encodeToByteArray(), fs.readFile("/root/a.txt"))
        assertContentEquals("world".encodeToByteArray(), fs.readFile("/root/a.txt", offset = 6, length = 5))
        assertFailsWith<IOException> { fs.readFile("/root/a.txt", offset = 6, length = 6) }
        assertFailsWith<IOException> { fs.readFile("/root/a.txt", offset = 1, length = Long.MAX_VALUE) }
        assertFailsWith<IOException> { fs.readFile("/root/a.txt", offset = 12, length = 0) }
        assertFailsWith<FileNotFoundException> { fs.readFile("/root/missing.txt") }
        assertNull(fs.readFileOrNull("/root/missing.txt"))
    }

    @Test
    fun testWriteFile() = runTest {
        val fs = fs()
        fs.writeFile("/root/a.txt", "XY".encodeToByteArray(), WriteType.OFFSET(1))
        assertContentEquals("hXYlo world".encodeToByteArray(), fs.readFile("/root/a.txt"))

        fs.writeFile("/root/new.txt", "secret".encodeToByteArray(), WriteType.OVERWRITE, permissions = "600")
        assertEquals("600", fs.getFilePermissions("/root/new.txt"))

        fs.writeFile("/root/new.txt", "plain".encodeToByteArray())
        assertContentEquals("plain".encodeToByteArray(), fs.readFile("/root/new.txt"))

        assertFailsWith<FileNotFoundException> {
            fs.writeFile("/root/other.txt", byteArrayOf(), WriteType.OVERWRITE, mustExist = true)
        }
    }

    @Test
    fun testPathExistsAndFileSize() = runTest {
        val fs = fs()

        assertTrue(fs.pathExists("/root/a.txt"))
        assertEquals(11L, fs.fileSize("/root/a.txt"))

        assertTrue(fs.pathExists("/root/sub"))

        assertFalse(fs.pathExists("/root/missing"))
        assertFailsWith<FileNotFoundException> { fs.fileSize("/root/missing") }
    }

    @Test
    fun testDirectories() = runTest {
        val fs = fs()
        assertEquals(setOf("a.txt", "sub"), fs.listDirectory("/root").toSet())
        assertFailsWith<FileNotFoundException> { fs.listDirectory("/root/missing") }

        fs.createDirectories("/root/empty")
        assertEquals(setOf("a.txt", "sub", "empty"), fs.listDirectory("/root").toSet())
        assertFailsWith<IOException> { fs.createDirectories("/root/empty", mustCreate = true) }
        assertFailsWith<IOException> { fs.createDirectories("/root/a.txt") }

        assertFailsWith<IOException> { fs.remove("/root/sub") } // not empty
        fs.remove("/root/empty")
        assertFalse(fs.pathExists("/root/empty"))
    }

    @Test
    fun testTrailingSeparator() = runTest {
        val fs = fs()
        fs.createDirectories("/root/dir/")

        assertTrue(fs.pathExists("/root/dir"))
        assertEquals(emptyList<String>(), fs.listDirectory("/root/dir"))
        assertEquals(setOf("a.txt", "sub", "dir"), fs.listDirectory("/root").toSet())

        fs.remove("/root/dir")
        assertFalse(fs.pathExists("/root/dir/"))
    }

    @Test
    fun testRootDirectory() = runTest {
        val fs = MapFilesystem(filePathSeparator = "/")
        fs.createDirectories("/")
        assertEquals(emptyList<String>(), fs.listDirectory("/"))
        fs.remove("/")

        fs.writeFile("/a.txt", "a".encodeToByteArray())
        assertEquals(listOf("a.txt"), fs.listDirectory("/"))
    }

    @Test
    fun testFileAndDirectoryCannotShareAPath() = runTest {
        val fs = fs()
        assertFailsWith<IOException> { fs.writeFile("/root/sub", byteArrayOf()) }
        assertFailsWith<IOException> { fs.rename("/root/a.txt", "/root/sub", overwrite = true) }
        assertContentEquals("hello world".encodeToByteArray(), fs.readFile("/root/a.txt"))
    }

    @Test
    fun testRemove() = runTest {
        val fs = fs()
        fs.remove("/root/a.txt")
        assertFalse(fs.pathExists("/root/a.txt"))
        assertFailsWith<FileNotFoundException> { fs.remove("/root/a.txt") }
        fs.remove("/root/a.txt", mustExist = false)
    }

    @Test
    fun testRename() = runTest {
        val fs = fs()
        fs.rename("/root/a.txt", "/root/c.txt")
        assertFalse(fs.pathExists("/root/a.txt"))
        assertContentEquals("hello world".encodeToByteArray(), fs.readFile("/root/c.txt"))

        assertFailsWith<FileNotFoundException> { fs.rename("/root/missing.txt", "/root/d.txt") }
        assertFailsWith<IOException> { fs.rename("/root/c.txt", "/root/sub/b.txt") }

        fs.rename("/root/c.txt", "/root/sub/b.txt", overwrite = true)
        assertContentEquals("hello world".encodeToByteArray(), fs.readFile("/root/sub/b.txt"))
    }

    @Test
    fun testWindowsSeparator() = runTest {
        val provider = TestPlatformProvider.of(
            fs = mapOf("C:\\root\\a.txt" to TestFile("a")),
            os = OperatingSystem(OsFamily.Windows, "test"),
        )
        assertEquals(listOf("a.txt"), provider.listDirectory("C:\\root"))
        assertTrue(provider.pathExists("C:\\root"))
    }
}
