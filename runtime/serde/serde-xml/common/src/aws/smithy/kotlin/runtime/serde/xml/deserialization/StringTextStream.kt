/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.deserialization

import aws.smithy.kotlin.runtime.serde.DeserializationException
import kotlin.math.max
import kotlin.math.min

private val nonAscii = """[^\x20-\x7E]""".toRegex()

/**
 * A stream of text characters that can be processed sequentially. This stream maintains a current position (i.e.,
 * offset in the string) from which all reading operations begin. The stream is advanced by `read` operations. The
 * stream is **not** advanced by `peek` operations.
 * @param source The source text for this stream.
 */
class StringTextStream(private val source: String) {
    private val end = source.length
    private var offset = 0

    /**
     * Advance the position by the given [length]. Throws an exception if this would advance beyond the end of the
     * stream.
     * @param length The length by which to advance the stream position.
     */
    fun advance(length: Int, errCondition: String) {
        checkBounds(length, errCondition)
        offset += length
    }

    /**
     * Advances the position if the given [text] is next in the stream. Otherwise, the offset is not updated.
     * @param text The text to look for at the current offset.
     * @return True if the given [text] was found and the offset was advanced; otherwise, false.
     */
    fun advanceIf(text: String): Boolean =
        if (source.startsWith(text, offset)) {
            offset += text.length
            true
        } else {
            false
        }

    /**
     * Advances the position until a whitespace character is found (i.e., one of ' ', '\r', '\n', '\t').
     */
    fun advanceUntilSpace() {
        while (offset < end) {
            val ch = source[offset]
            if (ch == ' ' || ch == '\r' || ch == '\n' || ch == '\t') return
            offset++
        }
    }

    /**
     * Advances the position until a non-whitespace character is found (i.e., not one of ' ', '\r', '\n', '\t').
     */
    fun advanceWhileSpace() {
        while (offset < end) {
            val ch = source[offset]
            if (ch != ' ' && ch != '\r' && ch != '\n' && ch != '\t') return
            offset++
        }
    }

    /**
     * Checks whether the bounds of the stream would be exceeded by advancing the given number of characters and, if so,
     * throws an exception.
     * @param length The amount beyond the current position to check.
     * @param errCondition The condition to include in an error message if necessary.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun checkBounds(length: Int, errCondition: String) {
        if (offset + length > end) error("Unexpected end-of-doc while $errCondition")
    }

    /**
     * Throws a [DeserializationException] with the given message and location string. Automatically includes the
     * current offset and a preview of the surrounding characters. For example:
     * ```
     * DeserializationException: Error msg
     * At offset 123 (showing range 120-126):
     * <b>!</b
     *    ^
     * ```
     * @param msg The error message to include with the exception.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun error(msg: String): Nothing {
        val start = max(0, offset - 3)
        val end = min(end - 1, offset + 3)

        val snippet = source.substring(start, end + 1).replace(nonAscii, "·")

        val caretPos = offset - start
        val caret = " ".repeat(caretPos) + "^"

        val locationMultilineString = "At offset $offset (showing range $start-$end):\n$snippet\n$caret"

        val fullMsg = "$msg\n$locationMultilineString"
        throw DeserializationException(fullMsg)
    }

    /**
     * Determines if the next several characters in the stream match the given text without advancing the position.
     */
    fun peekMatches(text: String): Boolean {
        val actualLength = min(text.length, end - offset)
        return sliceByLength(actualLength) == text
    }

    /**
     * Returns the next character in the stream and advances the position. Throws an exception if the end of the stream
     * would be exceeded.
     * @param errCondition The condition to include in an error message if necessary.
     */
    fun readOrThrow(errCondition: String): Char {
        checkBounds(1, errCondition)
        return source[offset++]
    }

    /**
     * Returns contents of the stream up to and including the given text and advances the position. Throws an exception
     * if the text is not encountered before the end of the stream.
     * @param text The text to seek.
     * @param errCondition The condition to include in an error message if necessary.
     * @return The stream contents from the current position up to and including [text].
     */
    fun readThrough(text: String, errCondition: String): String {
        val charIndex = source.indexOf(text, startIndex = offset)
        if (charIndex < 0) error("Unexpected end-of-doc while $errCondition")

        val endOfResult = charIndex + text.length
        val result = sliceByEnd(endOfResult)
        offset = endOfResult
        return result
    }

    /**
     * Returns contents of the stream up to but not including the given text and advances the position. Throws an
     * exception if the text is not encountered before the end of the stream.
     * @param text The text to seek.
     * @param errCondition The condition to include in an error message if necessary.
     * @return The stream contents from the current position up to but not including [text].
     */
    fun readUntil(text: String, errCondition: String): String {
        val charIndex = source.indexOf(text, startIndex = offset)
        if (charIndex < 0) error("Unexpected end-of-doc while $errCondition")

        val result = sliceByEnd(charIndex)
        offset = charIndex
        return result
    }

    /**
     * Returns an XML name from the stream and advances the position. Throws an exception if unable to find a valid XML
     * name start character. See https://www.w3.org/TR/xml/#NT-Name for name character rules.
     */
    fun readWhileXmlName(): String {
        val c = source[offset]
        if (
            !(
                'a' <= c && c <= 'z' ||
                    'A' <= c && c <= 'Z' ||
                    c == ':' ||
                    c == '_' ||
                    '\u00c0' <= c && c <= '\u00d6' ||
                    '\u00d8' <= c && c <= '\u00f6' ||
                    '\u00f8' <= c && c <= '\u02ff' ||
                    '\u0370' <= c && c <= '\u037d' ||
                    '\u037f' <= c && c <= '\u1fff' ||
                    '\u200c' <= c && c <= '\u200d' ||
                    '\u2070' <= c && c <= '\u218f' ||
                    '\u2c00' <= c && c <= '\u2fef' ||
                    '\u3001' <= c && c <= '\ud7ff'
                )
        ) {
            error("Found '$c' but expected a valid XML start name character")
        }

        var peekOffset = offset + 1
        while (peekOffset < end) {
            val ch = source[peekOffset]
            if (
                !(
                    'a' <= ch && ch <= 'z' ||
                        'A' <= ch && ch <= 'Z' ||
                        '0' <= ch && ch <= '9' ||
                        ch == ':' ||
                        ch == '-' ||
                        ch == '.' ||
                        ch == '_' ||
                        ch == '\u00b7' ||
                        '\u00c0' <= ch && ch <= '\u00d6' ||
                        '\u00d8' <= ch && ch <= '\u00f6' ||
                        '\u00f8' <= ch && ch <= '\u02ff' ||
                        '\u0300' <= ch && ch <= '\u036f' ||
                        '\u0370' <= ch && ch <= '\u037d' ||
                        '\u037f' <= ch && ch <= '\u1fff' ||
                        '\u200c' <= ch && ch <= '\u200d' ||
                        '\u203f' <= ch && ch <= '\u2040' ||
                        '\u2070' <= ch && ch <= '\u218f' ||
                        '\u2c00' <= ch && ch <= '\u2fef' ||
                        '\u3001' <= ch && ch <= '\ud7ff'
                    )
            ) {
                // Found end of name
                break
            }

            peekOffset++
        }
        return sliceByEnd(peekOffset).also { offset = peekOffset }
    }

    /**
     * Moves the stream position back by [length] characters. Throws an exception if this would exceed the bounds of the
     * stream.
     * @param length The amount of characters to go back.
     * @param errCondition The condition to include in an error message if necessary.
     */
    fun rewind(length: Int, errCondition: String) {
        checkBounds(-length, errCondition)
        offset -= length
    }

    /**
     * Returns a slice of the source up to (but not including) the given end position.
     * @param endExclusive The exclusive end position.
     */
    private fun sliceByEnd(endExclusive: Int): String = source.substring(offset, endExclusive)

    /**
     * Returns a slice of the source that is [length] characters long.
     * @param length The number of characters to return.
     */
    private fun sliceByLength(length: Int): String = sliceByEnd(offset + length)
}
