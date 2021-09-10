/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.util.text.byteCountUtf8
import aws.smithy.kotlin.runtime.util.text.codePointToChars
import aws.smithy.kotlin.runtime.util.text.isSupplementaryCodePoint

/**
 * A stream of data that returns characters one [Char] at a time
 */
interface CharStream {
    /**
     * Return the next [Char] in the stream, *without* 'consuming' it (i.e. [peek] should not alter
     * the read position of the underlying stream)  suspending if necessary until one is available.
     *
     * @return the next [Char] or null if the stream is completed
     */
    fun peek(): Char?

    /**
     * Return and 'consume' the next [Char] in the stream, suspending if necessary until one is available.
     *
     * @return the next [Char] or null if the stream is completed
     */
    fun next(): Char?

    fun bytes(): ByteArray

    companion object {
//        operator fun invoke(bytes: ByteArray): CharStream = CharStream(SdkByteReadChannel(bytes))
        operator fun invoke(bytes: ByteArray): CharStream = ByteArrayCharStream(bytes)
//        operator fun invoke(chan: SdkByteReadChannel): CharStream = ReadChannelCharStream(chan)
    }
}

// internal class ReadChannelCharStream(private val chan: SdkByteReadChannel) : CharStream {
//    private var peeked = mutableListOf<Char>()
//    override suspend fun peek(): Char? {
//        if (peeked.isEmpty()) {
//            val code = chan.readUtf8CodePoint() ?: return null
//            when {
//                Char.isSupplementaryCodePoint(code) -> {
//                    val chars = Char.codePointToChars(code)
//                    chars.forEach(peeked::add)
//                }
//                else -> peeked.add(code.toChar())
//            }
//        }
//        return peeked.lastOrNull()
//    }
//
//    override suspend fun next(): Char? {
//        if (peeked.isEmpty()) {
//            peek()
//        }
//        return peeked.removeLastOrNull()
//    }
// }

internal class ByteArrayCharStream(private val bytes: ByteArray) : CharStream {
    private var peeked = mutableListOf<Char>()
    private var cursor = 0
    override fun bytes(): ByteArray = bytes

    override fun peek(): Char? {
        if (peeked.isEmpty()) {
            if (cursor >= bytes.size) return null
            val firstByte = bytes[cursor]
            val cnt = byteCountUtf8(firstByte)
            var code = when (cnt) {
                1 -> firstByte.toInt()
                2 -> firstByte.toInt() and 0x1f
                3 -> firstByte.toInt() and 0x0f
                4 -> firstByte.toInt() and 0x07
                else -> throw IllegalStateException("Invalid UTF-8 start sequence: $firstByte")
            }
            cursor++

            for (i in 1 until cnt) {
                if (cursor >= bytes.size) throw IllegalStateException("unexpected EOF: expected ${cnt - i} bytes")
                val byte = bytes[cursor]
                val bint = byte.toInt()
                if (bint and 0xc0 != 0x80) throw IllegalStateException("invalid UTF-8 successor byte: $byte")

                code = (code shl 6) or (bint and 0x3f)
                cursor++
            }

            when {
                Char.isSupplementaryCodePoint(code) -> {
                    val chars = Char.codePointToChars(code)
                    for (i in chars.size - 1 downTo 0) {
                        peeked.add(chars[i])
                    }
                }
                else -> peeked.add(code.toChar())
            }
        }
        return peeked.lastOrNull()
    }

    override fun next(): Char? {
        if (peeked.isEmpty()) {
            peek()
        }
        return peeked.removeLastOrNull()
    }
}

// internal class ByteArrayCharStream(bytes: ByteArray) : CharStream {
//    private var peeked: Char? = null
//    private var cursor = 0
//    private val data = bytes.decodeToString()
//
//    override fun peek(): Char? {
//        if (peeked != null) return peeked
//        if (cursor >= data.length) return null
//        peeked = data[cursor]
//        return peeked
//    }
//
//    override fun next(): Char? {
//        val next = peeked ?: peek()
//        cursor++
//        peeked = null
//        return next
//    }
// }

/**
 * Consume and return the next [Char] in the underlying [CharStream] throwing an exception if null
 *
 * @see [CharStream.next]
 */
fun CharStream.nextOrThrow(): Char = next() ?: throw IllegalStateException("Unexpected end of stream")

/**
 * Peek and return the next [Char] in the underlying [CharStream] throwing an exception if null.
 *
 * The returned [Char] is *not* consumed from the underlying [CharStream]
 *
 * @see [CharStream.peek]
 */
fun CharStream.peekOrThrow(): Char = peek() ?: throw IllegalStateException("Unexpected end of stream")

/**
 * Consume the [expected] CharSequence or throw an exception if an unexpected char is encountered
 */
fun CharStream.consume(expected: CharSequence) = expected.forEach { consume(it) }

/**
 * If the next character matches [expected] consume it.
 *
 * If it does not match:
 *  - and [optional] is true don't consume it, return
 *  - else throw [IllegalStateException]
 *
 *  Returns whether the expected character was consumed
 */
fun CharStream.consume(expected: Char, optional: Boolean = false): Boolean {
    val ch = peek()
    if (ch == expected) {
        nextOrThrow()
    } else if (!optional) {
        throw IllegalStateException("Unexpected char '$ch' expected '$expected'")
    }

    return ch == expected
}

/**
 * Read the stream, buffering into a [StringBuilder] until [exitPredicate] returns true.
 *
 * The first character that matches the [exitPredicate] is left on the stream (ie. not consumed).
 */
fun CharStream.readUntil(exitPredicate: (Char) -> Boolean): String = buildString {
    while (!exitPredicate(peekOrThrow())) {
        append(nextOrThrow())
    }
}

/**
 * Take [count] characters from the stream
 */
fun CharStream.take(count: Int): String = buildString {
    require(count >= 0) { "expected count > 0: $count" }
    for (i in 0 until count) {
        append(nextOrThrow())
    }
}

/**
 * Read the next non-whitespace character from the stream
 * @param peek Flag indicating if the next character should be consumed or peeked (whitespace will be consumed,
 * this only controls if the next non-whitespace character is consumed as well)
 */
fun CharStream.nextNonWhitespace(peek: Boolean = false): Char? {
    while (peek()?.isWhitespace() == true) {
        next()
    }
    return if (peek) peek() else next()
}
