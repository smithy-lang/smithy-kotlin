/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.testing.RandomTempFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ByteStreamJVMTest {
    @Test
    fun `file as byte stream validates start`() = runTest {
        val file = RandomTempFile(1024)
        val e = assertFailsWith<Throwable> {
            file.asByteStream(-1)
        }
        assertEquals("start index -1 cannot be negative", e.message)
    }

    @Test
    fun `file as byte stream validates end`() = runTest {
        val file = RandomTempFile(1024)
        val e = assertFailsWith<Throwable> {
            file.asByteStream(endInclusive = 1024)
        }
        assertEquals("end index 1024 must be less than file size 1024", e.message)
    }

    @Test
    fun `file as byte stream validates start and end`() = runTest {
        val file = RandomTempFile(1024)
        val e = assertFailsWith<Throwable> {
            file.asByteStream(5, 1)
        }
        assertEquals("end index 1 must be greater than or equal to start index 5", e.message)
    }

    @Test
    fun `file as byte stream has contentLength`() = runTest {
        val file = RandomTempFile(1024)
        val stream = file.asByteStream()

        assertEquals(1024, stream.contentLength)
    }

    @Test
    fun `partial file as byte stream has contentLength`() = runTest {
        val file = RandomTempFile(1024)
        val stream = file.asByteStream(1, 1023)

        assertEquals(1023, stream.contentLength)
    }

    @Test
    fun `partial file as byte stream has contentLength with implicit end`() = runTest {
        val file = RandomTempFile(1024)
        val stream = file.asByteStream(1)

        assertEquals(1023, stream.contentLength)
    }

    @Test
    fun `file as byte stream matches read`() = runTest {
        val file = RandomTempFile(1024)

        val expected = file.readBytes()
        val actual = file.asByteStream().toByteArray()

        assertContentEquals(expected, actual)
    }

    @Test
    fun `partial file as byte stream matches read`() = runTest {
        val file = RandomTempFile(1024)

        val expected = file.readBytes()
        val part0 = file.asByteStream(endInclusive = 255).toByteArray()
        val part1 = file.asByteStream(256, 511).toByteArray()
        val part2 = file.asByteStream(512).toByteArray()

        assertContentEquals(expected, part0 + part1 + part2)
    }
}
