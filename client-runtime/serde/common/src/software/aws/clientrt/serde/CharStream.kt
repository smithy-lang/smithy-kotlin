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
suspend fun CharStream.readUntil(exitPredicate: (Char) -> Boolean): String {
    val buffer = StringBuilder()
    while (true) {
        if (exitPredicate(peekOrThrow())) {
            return buffer.toString()
        }
        buffer.append(nextOrThrow())
    }
}
