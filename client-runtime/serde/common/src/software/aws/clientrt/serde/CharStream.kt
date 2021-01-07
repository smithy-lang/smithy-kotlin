/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde

/**
 * A streaming view of a block of data that returns characters one at a time
 */
interface CharStream {
    suspend fun peek(): Char?
    suspend fun next(): Char?

    companion object {
        fun fromByteArray(bytes: ByteArray) = object : CharStream {
            private var position = 0
            override suspend fun peek(): Char? = bytes.getOrNull(position)?.toChar()

            override suspend fun next(): Char? = bytes.getOrNull(position++)?.toChar()
        }
    }
}

suspend fun CharStream.nextOrThrow(): Char = next() ?: throw IllegalStateException("Unexpected end of stream")
suspend fun CharStream.peekOrThrow(): Char = peek() ?: throw IllegalStateException("Unexpected end of stream")

/**
 * Burn the [expected] CharSequence
 */
suspend fun CharStream.burn(expected: CharSequence) = expected.forEach { burn(it) }

/**
 * If the next character matches [expected] burn it, if it does not match
 * unexpected character [IllegalStateException] is thrown - unless [optional] is true
 */
suspend fun CharStream.burn(expected: Char, optional: Boolean = false) {
    val ch = peek()
    if (ch == expected) {
        nextOrThrow()
    } else if (!optional) {
        throw IllegalStateException("Unexpected char '$ch' expected '$expected'")
    }
}

/**
 * Burn characters in the stream that match the [predicate], the first character that
 * does not match the predicate will be left unconsumed on the stream unless [inclusive] is true
 *
 * If the stream ends before the [predicate] is met an [IllegalStateException] is thrown
 */
suspend fun CharStream.burnWhile(inclusive: Boolean = false, predicate: (Char) -> Boolean) {
    while (predicate(peekOrThrow())) {
        next()
    }
    if (inclusive) {
        next()
    }
}

/**
 * Read the stream buffering into a [StringBuilder] until [exitPredicate] returns true.
 *
 * [Char] matching the [exitPredicate] will not be consumed, and will not be included in the returned String unless
 * [inclusive] is true
 */
suspend fun CharStream.readUntil(
    first: Char? = null,
    inclusive: Boolean = false,
    exitPredicate: (Char) -> Boolean
): String {
    val buffer = StringBuilder()
    first?.let { buffer.append(it) }
    while (true) {
        if (exitPredicate(peekOrThrow())) {
            if (inclusive) {
                buffer.append(nextOrThrow())
            }
            return buffer.toString()
        }
        buffer.append(nextOrThrow())
    }
}
