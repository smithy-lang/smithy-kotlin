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
    private fun checkBounds(length: Int, errCondition: String) {
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
            c < ':' ||
            ':' < c && c < 'A' ||
            'Z' < c && c < '_' ||
            '_' < c && c < 'a' ||
            'z' < c && c < '\u00c0' ||
            '\u00d6' < c && c < '\u00d8' ||
            '\u00f6' < c && c < '\u00f8' ||
            '\u02ff' < c && c < '\u0370' ||
            '\u037d' < c && c < '\u037f' ||
            '\u1fff' < c && c < '\u200c' ||
            '\u200d' < c && c < '\u2070' ||
            '\u218f' < c && c < '\u2c00' ||
            '\u2fef' < c && c < '\u3001' ||
            '\ud7ff' < c
        ) {
            error("Unable to find valid XML start name character")
        }

        var peekOffset = offset + 1
        while (peekOffset < end) {
            val ch = source[peekOffset]
            if (
                ch < '-' ||
                '-' < ch && ch < '.' ||
                '.' < ch && ch < '0' ||
                '9' < ch && ch < ':' ||
                ':' < ch && ch < 'A' ||
                'Z' < ch && ch < '_' ||
                '_' < ch && ch < 'a' ||
                'z' < ch && ch < '\u00b7' ||
                '\u00b7' < ch && ch < '\u00c0' ||
                '\u00d6' < ch && ch < '\u00d8' ||
                '\u00f6' < ch && ch < '\u00f8' ||
                '\u02ff' < ch && ch < '\u0300' ||
                '\u036f' < ch && ch < '\u0370' ||
                '\u037d' < ch && ch < '\u037f' ||
                '\u1fff' < ch && ch < '\u200c' ||
                '\u200d' < ch && ch < '\u203f' ||
                '\u2040' < ch && ch < '\u2070' ||
                '\u218f' < ch && ch < '\u2c00' ||
                '\u2fef' < ch && ch < '\u3001' ||
                '\ud7ff' < ch
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
