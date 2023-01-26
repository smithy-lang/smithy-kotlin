/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.testing.RandomTempFile
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class FileSourceTest {
    @Test
    fun testStartPositionValidation() = runTest {
        val file = RandomTempFile(1024)
        val source = file.source(-1)
        assertFailsWith<IllegalArgumentException> {
            // Files are opened lazily...IAE won't be thrown until a read is attempted
            source.readToByteArray()
        }.message.shouldContain("start position should be >= 0, found -1")
    }

    @Test
    fun testEndPositionValidation() = runTest {
        val file = RandomTempFile(1024)
        val source = file.source(endInclusive = 1024)
        assertFailsWith<IllegalArgumentException> {
            // Files are opened lazily...IAE won't be thrown until a read is attempted
            source.readToByteArray()
        }.message.shouldContain("endInclusive should be less than or equal to the length of the file, was 1024")

        file.source(1023).close()
    }

    @Test
    fun testStartPosition() {
        val file = RandomTempFile(1024)
        val rc = file.source(4).use {
            it.buffer().readAll(SdkSink.blackhole())
        }

        assertEquals(1020, rc)
    }

    @Test
    fun testEndPosition() {
        val file = RandomTempFile(1024)
        val rc = file.source(endInclusive = 15).use {
            it.buffer().readAll(SdkSink.blackhole())
        }

        assertEquals(16, rc)
    }

    @Test
    fun testStartAndEndPosition() {
        val file = RandomTempFile(1024)
        val bytes = file.readBytes()
        val expected = bytes.sliceArray(700..712).decodeToString()

        val sink = SdkBuffer()
        file.source(700L..712L).use {
            it.buffer().readAll(sink)
        }

        val actual1 = sink.readUtf8()
        assertEquals(expected, actual1)

        file.source(700L, 712L).use {
            it.buffer().readAll(sink)
        }

        val actual2 = sink.readUtf8()

        assertEquals(expected, actual2)
    }

    @Test
    fun testPathSource() {
        val path = RandomTempFile(1024).toPath()
        val rc = path.source().use {
            it.buffer().readAll(SdkSink.blackhole())
        }

        assertEquals(1024L, rc)
    }
}
