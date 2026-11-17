/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(PlannedRemoval::class)

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.PlannedRemoval
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that the suspending [Filesystem] API does not run blocking work on the caller's thread.
 */
class FilesystemDispatchJVMTest {
    // Records the thread each deprecated blocking member runs on
    @Suppress("OVERRIDE_DEPRECATION")
    private class RecordingFilesystem : Filesystem {
        val threads: MutableList<Thread> = Collections.synchronizedList(mutableListOf())
        private fun record() {
            threads += Thread.currentThread()
        }

        override val filePathSeparator: String = "/"
        override suspend fun readFileOrNull(path: String): ByteArray? = error("not needed for test")
        override suspend fun writeFile(path: String, data: ByteArray): Unit = error("not needed for test")
        override fun fileExists(path: String): Boolean = error("not needed for test")
        override fun write(path: String, data: ByteArray, writeType: WriteType, mustExist: Boolean, permissions: String?) = record()
        override fun read(path: String, amount: Long, offset: Long, readAll: Boolean): ByteArray = byteArrayOf().also { record() }
        override fun list(path: String, mustExist: Boolean): Collection<String> = emptyList<String>().also { record() }
        override fun createDir(path: String, mustCreate: Boolean) = record()
        override fun delete(path: String, mustExist: Boolean) = record()
        override fun atomicMove(source: String, destination: String, mustExist: Boolean, overwrite: Boolean) = record()
        override fun exists(path: String): Boolean = true.also { record() }
        override fun size(path: String, mustExist: Boolean): Long = 0L.also { record() }
    }

    @Test
    fun testDefaultsDispatchOffCallerThread() {
        val fs = RecordingFilesystem()
        val caller = Thread.currentThread()

        runBlocking {
            fs.readFile("a")
            fs.readFile("a", offset = 0, length = 1)
            fs.writeFile("a", byteArrayOf(), WriteType.OVERWRITE)
            fs.pathExists("a")
            fs.fileSize("a")
            fs.listDirectory("a")
            fs.createDirectories("a")
            fs.remove("a")
            fs.rename("a", "b")
        }

        assertEquals(9, fs.threads.size)
        fs.threads.forEach { assertTrue(it != caller, "blocking work ran on the caller thread ${it.name}") }
    }

    // A FIFO blocks open() until the other end opens it. The test runs on a single-threaded event loop: if the
    // provider blocked that thread, the coroutine opening the other end could never run and the loop would hang.
    private fun assertDoesNotBlockCaller(
        block: suspend (fifo: String) -> Unit,
        otherEnd: (fifo: File) -> Unit,
    ) {
        if (PlatformProvider.System.osInfo().family == OsFamily.Windows) return
        val dir = Files.createTempDirectory("fifo-test")
        val fifo = dir.resolve("fifo").toFile()
        check(ProcessBuilder("mkfifo", fifo.absolutePath).start().waitFor() == 0) { "mkfifo failed" }

        var failure: Throwable? = null
        val rescued = AtomicBoolean(false)
        // Daemon threads so a regression can never keep the test JVM alive
        val loop = thread(isDaemon = true) {
            try {
                runBlocking {
                    val op = async { block(fifo.absolutePath) }
                    delay(100) // lets op start; it only yields this thread if it moved off it
                    // After a rescue the other end was already opened; opening it again would have no peer and hang
                    if (!rescued.get()) otherEnd(fifo)
                    op.await()
                }
            } catch (t: Throwable) {
                failure = t
            }
        }

        loop.join(10_000)
        val blocked = loop.isAlive
        if (blocked) {
            // Unblock the stuck thread by opening the other end ourselves
            rescued.set(true)
            thread(isDaemon = true) { runCatching { otherEnd(fifo) } }
            loop.join(10_000)
        }
        fifo.delete()
        Files.delete(dir)

        assertFalse(blocked, "file IO blocked the calling thread")
        failure?.let { throw it }
    }

    @Test
    fun testSystemReadFileOrNullDoesNotBlockCaller() = assertDoesNotBlockCaller(
        block = { assertContentEquals("hi".encodeToByteArray(), PlatformProvider.System.readFileOrNull(it)) },
        otherEnd = { it.writeBytes("hi".encodeToByteArray()) },
    )

    @Test
    fun testSystemWriteFileDoesNotBlockCaller() = assertDoesNotBlockCaller(
        block = { PlatformProvider.System.writeFile(it, "hi".encodeToByteArray()) },
        otherEnd = { assertContentEquals("hi".encodeToByteArray(), it.readBytes()) },
    )
}
