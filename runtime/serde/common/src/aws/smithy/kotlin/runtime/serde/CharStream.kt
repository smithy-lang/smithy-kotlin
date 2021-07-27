/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.readUtf8CodePoint
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
    suspend fun peek(): Char?

    /**
     * Return and 'consume' the next [Char] in the stream, suspending if necessary until one is available.
     *
     * @return the next [Char] or null if the stream is completed
     */
    suspend fun next(): Char?

    companion object {
        operator fun invoke(bytes: ByteArray): CharStream = CharStream(SdkByteReadChannel(bytes))
        operator fun invoke(chan: SdkByteReadChannel): CharStream = ReadChannelCharStream(chan)
    }
}

internal class ReadChannelCharStream(private val chan: SdkByteReadChannel) : CharStream {
    private var peeked = mutableListOf<Char>()
    override suspend fun peek(): Char? {
        if (peeked.isEmpty()) {
            val code = chan.readUtf8CodePoint() ?: return null
            when {
                Char.isSupplementaryCodePoint(code) -> {
                    val chars = Char.codePointToChars(code)
                    chars.forEach(peeked::add)
                }
                else -> peeked.add(code.toChar())
            }
        }
        return peeked.lastOrNull()
    }

    override suspend fun next(): Char? {
        if (peeked.isEmpty()) {
            peek()
        }
        return peeked.removeLastOrNull()
    }
}

/**
 * Consume and return the next [Char] in the underlying [CharStream] throwing an exception if null
 *
 * @see [CharStream.next]
 */
suspend fun CharStream.nextOrThrow(): Char = next() ?: throw IllegalStateException("Unexpected end of stream")

/**
 * Peek and return the next [Char] in the underlying [CharStream] throwing an exception if null.
 *
 * The returned [Char] is *not* consumed from the underlying [CharStream]
 *
 * @see [CharStream.peek]
 */
suspend fun CharStream.peekOrThrow(): Char = peek() ?: throw IllegalStateException("Unexpected end of stream")

/**
 * Consume the [expected] CharSequence or throw an exception if an unexpected char is encountered
 */
suspend fun CharStream.consume(expected: CharSequence) = expected.forEach { consume(it) }

/**
 * If the next character matches [expected] consume it.
 *
 * If it does not match:
 *  - and [optional] is true don't consume it, return
 *  - else throw [IllegalStateException]
 */
suspend fun CharStream.consume(expected: Char, optional: Boolean = false) {
    val ch = peek()
    if (ch == expected) {
        nextOrThrow()
    } else if (!optional) {
        throw IllegalStateException("Unexpected char '$ch' expected '$expected'")
    }
}

/**
 * Read the stream, buffering into a [StringBuilder] until [exitPredicate] returns true.
 *
 * The first character that matches the [exitPredicate] is left on the stream (ie. not consumed).
 */
suspend fun CharStream.readUntil(exitPredicate: (Char) -> Boolean): String = buildString {
    while (!exitPredicate(peekOrThrow())) {
        append(nextOrThrow())
    }
}

/**
 * Take [count] characters from the stream
 */
suspend fun CharStream.take(count: Int): String = buildString {
    require(count >= 0) { "expected count > 0: $count" }
    for (i in 0 until count) {
        append(nextOrThrow())
    }
}
