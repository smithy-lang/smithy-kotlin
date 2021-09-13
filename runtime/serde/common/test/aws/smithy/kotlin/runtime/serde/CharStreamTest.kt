/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

abstract class CharStreamTest {
    abstract fun newCharStream(contents: String): CharStream

    @Test
    fun canCreateACharStreamFromAByteArray() = runSuspendTest {
        val sut = newCharStream("hello")
        val contents = sut.readAll()
        assertEquals("hello", contents)
    }

    @Test
    fun peekDoesNotAffectStreamPosition() = runSuspendTest {
        val sut = newCharStream("hello")
        assertEquals('h', sut.peek())
        assertEquals('h', sut.peek())
        assertEquals('h', sut.next())
        assertEquals('e', sut.peek())
    }

    @Test
    fun consumeTheExpectedCharacterThrowingIfNoMatch() = runSuspendTest {
        val sut = newCharStream("hello")

        sut.consume('h')

        assertFailsWith<IllegalStateException> { sut.consume('h') }
        assertEquals("ello", sut.readAll())
    }

    @Test
    fun consumeTheExpectedCharSequenceThrowingIfNoMatch() = runSuspendTest {
        val sut = newCharStream("hello")

        sut.consume("he")

        assertFailsWith<IllegalStateException> { sut.consume("he") }
        assertEquals("llo", sut.readAll())
    }

    @Test
    fun canReadUntilAPredicateIsHit() = runSuspendTest {
        val sut = newCharStream("hello world")

        val read = sut.readUntil { it.isWhitespace() }

        assertEquals("hello", read)
        assertEquals(" world", sut.readAll())
    }

    @Test
    fun testTake(): Unit = runSuspendTest {
        val sut = newCharStream("foobar")
        val read = sut.take(4)
        assertEquals("foob", read)

        assertFailsWith<IllegalStateException> {
            sut.take(3)
        }

        Unit
    }

    @Test
    fun testUnicode(): Unit = runSuspendTest {
        val languages = listOf(
            "こんにちは世界",
            "مرحبا بالعالم",
            "Привет, мир",
            "Γειά σου Κόσμε",
            "नमस्ते दुनिया",
            "you have summoned ZA̡͊͠͝LGΌ"
        )

        languages.forEachIndexed { idx, lang ->
            val sut = newCharStream(lang)
            lang.forEach {
                assertEquals(it, sut.next(), "[idx=$idx] expected $it from input $lang")
            }
            assertNull(sut.next())
        }

        // surrogate pair
        val sut = newCharStream("foo\uD834\uDD1Ebar")
        assertEquals("foo", sut.take(3))
        assertEquals('\uD834', sut.next())
        assertEquals('\uDD1E', sut.next())
        assertEquals("bar", sut.take(3))
    }

    private suspend fun CharStream.readAll(): String = buildString {
        while (peek() != null) {
            append(next())
        }
    }
}

class ReadChannelCharStreamTest : CharStreamTest() {
    override fun newCharStream(contents: String): CharStream = CharStream(SdkByteReadChannel(contents.encodeToByteArray()))
}
