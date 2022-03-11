/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.tokenization

import aws.smithy.kotlin.runtime.serde.DeserializationException
import kotlin.math.max
import kotlin.math.min

private val nonAscii = """[^\x20-\x7E]""".toRegex()

/**
 * A stream of text characters that can be processed sequentially. This stream maintains a current position (i.e.,
 * offset in the string) from which all reading operations begin. The stream is advanced by `read` operations. The
 * stream is **not** advanced by `peek` operations.
 * @param bytes The source bytes for this stream (which will be decoded to a string)
 */
class StringTextStream(private val source: String) {
    private val end = source.length
    private var offset = 0

    /**
     * Checks whether the bounds of the stream would be exceeded by advancing the given number of characters and, if so,
     * throws an exception.
     * @param length The amount beyond the current position to check.
     * @param errMessage A provider of an error message to include in the exception.
     */
    private fun checkBounds(length: Int, errMessage: () -> String) {
        if (offset + length > end) error(errMessage())
    }

    /**
     * Throws a [DeserializationException] with the given message and location string.
     * @param msg The error message to include with the exception.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun error(msg: String): Nothing {
        val fullMsg = "$msg\n$locationMultilineString"
        throw DeserializationException(fullMsg)
    }

    /**
     * Gets a multiline string that shows the current offset and a preview of the surrounding characters. For example:
     * ```
     * At offset 123 (showing range 120-126):
     * <b>!</b
     *    ^
     * ```
     */
    val locationMultilineString: String
        get() {
            val start = max(0, offset - 3)
            val end = min(end - 1, offset + 3)
            val snippet = source.substring(start..end).replace(nonAscii, "·")
            val caretPos = offset - start
            val caret = " ".repeat(caretPos) + "^"
            return "At offset $offset (showing range $start-$end):\n$snippet\n$caret"
        }

    /**
     * Returns the next [length] characters in the stream without advancing the position. The return is truncated if
     * [length] would exceed the end of the stream.
     * @param length The number of characters (at most) to return.
     */
    fun peekAtMost(length: Int): String {
        val actualLength = min(length, end - offset)
        return sliceByLength(actualLength)
    }

    /**
     * Determines if the next several characters in the stream match the given text without advancing the position.
     */
    fun peekMatches(text: String): Boolean = peekAtMost(text.length) == text

    /**
     * Returns the next character in the stream without advancing the position. Throws an exception if the position is
     * at the stream's end.
     * @param errMessage A provider of an error message to include in the exception.
     */
    fun peekOrThrow(errMessage: () -> String): Char {
        checkBounds(1, errMessage)
        return source[offset]
    }

    /**
     * Returns the next [length] characters in the stream without advancing the position. Throws an exception if the end
     * of the stream would be exceeded.
     * @param length The number of characters to read.
     * @param errMessage A provider of an error message to include in the exception.
     */
    fun peekOrThrow(length: Int, errMessage: () -> String): String {
        checkBounds(length, errMessage)
        return sliceByLength(length)
    }

    /**
     * Returns contents of the stream up to and including the given text without advancing the position. Throws an
     * exception if the text is not encountered before the end of the stream.
     * @param text The text to seek
     * @param errMessage A provider of an error message to include in the exception.
     * @return The stream contents from the current position up to and including [text].
     */
    fun peekThrough(text: String, errMessage: () -> String): String {
        val charIndex = source.indexOf(text, startIndex = offset)
        if (charIndex < 0) error(errMessage())
        return sliceByEnd(charIndex + text.length)
    }

    /**
     * Returns zero or more characters from the stream while the given predicate is matched without advancing the
     * position.
     * @param predicate The evaluation function for each character.
     */
    fun peekWhile(predicate: (Char) -> Boolean): String {
        var peekOffset = offset
        while (peekOffset < end && predicate(source[peekOffset])) {
            peekOffset++
        }
        return sliceByEnd(peekOffset)
    }

    /**
     * Returns the next [length] characters in the stream and advances the position. Throws an exception if the end of
     * the stream would be exceeded.
     * @param length The number of characters to read.
     * @param errMessage A provider of an error message to include in the exception.
     */
    fun readOrThrow(errMessage: () -> String): Char =
        peekOrThrow(errMessage).also { offset++ }

    /**
     * Returns the next [length] characters in the stream and advances the position. Throws an exception if the end of
     * the stream would be exceeded.
     * @param length The number of characters to read.
     * @param errMessage A provider of an error message to include in the exception.
     */
    fun readOrThrow(length: Int, errMessage: () -> String): String =
        peekOrThrow(length, errMessage).also { offset += length }

    /**
     * Returns contents of the stream up to and including the given text and advances the position. Throws an exception
     * if the text is not encountered before the end of the stream.
     * @param text The text to seek
     * @param errMessage A provider of an error message to include in the exception.
     * @return The stream contents from the current position up to and including [text].
     */
    fun readThrough(text: String, errMessage: () -> String): String =
        peekThrough(text, errMessage).also { offset += it.length }

    /**
     * Returns zero or more characters from the stream while the given predicate is matched and advances the position.
     * @param predicate The evaluation function for each character.
     */
    fun readWhile(predicate: (Char) -> Boolean): String =
        peekWhile(predicate).also { offset += it.length }

    /**
     * Returns a slice of the source up to (but not including) the given end position.
     * @param endExclusive The exclusive end position.
     */
    private fun sliceByEnd(endExclusive: Int): String = source.substring(offset until endExclusive)

    /**
     * Returns a slice of the source that is [length] characters long.
     * @param length The number of characters to return.
     */
    private fun sliceByLength(length: Int): String = sliceByEnd(offset + length)
}
