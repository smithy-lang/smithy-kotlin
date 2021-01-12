/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde

import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CharStreamTest {

    @Test
    fun canCreateACharStreamFromAByteArray() = runSuspendTest {
        val sut = CharStream("hello".encodeToByteArray())
        val contents = sut.readAll()
        assertEquals("hello", contents)
    }

    @Test
    fun peekDoesNotAffectStreamPosition() = runSuspendTest {
        val sut = CharStream("hello".encodeToByteArray())
        assertEquals('h', sut.peek())
        assertEquals('h', sut.peek())
        assertEquals('h', sut.next())
        assertEquals('e', sut.peek())
    }

    @Test
    fun consumeTheExpectedCharacterThrowingIfNoMatch() = runSuspendTest {
        val sut = CharStream("hello".encodeToByteArray())

        sut.consume('h')

        assertFailsWith<IllegalStateException> { sut.consume('h') }
        assertEquals("ello", sut.readAll())
    }

    @Test
    fun consumeTheExpectedCharSequenceThrowingIfNoMatch() = runSuspendTest {
        val sut = CharStream("hello".encodeToByteArray())

        sut.consume("he")

        assertFailsWith<IllegalStateException> { sut.consume("he") }
        assertEquals("llo", sut.readAll())
    }

    @Test
    fun canReadUntilAPredicateIsHit() = runSuspendTest {
        val sut = CharStream("hello world".encodeToByteArray())

        val read = sut.readUntil { it.isWhitespace() }

        assertEquals("hello", read)
        assertEquals(" world", sut.readAll())
    }

    private suspend fun CharStream.readAll(): String = buildString {
        while (peek() != null) {
            append(next())
        }
    }
}
