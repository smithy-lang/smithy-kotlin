/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.*

private val DIGITS = ('0'..'9').toSet()
private val EXP = setOf('e', 'E')
private val PLUS_MINUS = setOf('-', '+')

internal class JsonLexer(
    private val data: CharStream
) : JsonStreamReader {
    private var peeked: RawJsonToken? = null
    private val stack: Stack<RawJsonToken> = mutableListOf()

    override suspend fun nextToken(): JsonToken {
        val raw = peek()
        peeked = null

        return when (raw) {
            RawJsonToken.BeginArray -> openStructure('[', RawJsonToken.BeginArray, JsonToken.BeginArray)
            RawJsonToken.EndArray -> closeStructure(']', RawJsonToken.BeginArray, JsonToken.EndArray).moveToNextElement()
            RawJsonToken.BeginObject -> openStructure('{', RawJsonToken.BeginObject, JsonToken.BeginObject)
            RawJsonToken.EndObject -> closeStructure('}', RawJsonToken.BeginObject, JsonToken.EndObject).moveToNextElement()
            RawJsonToken.Name -> readName()
            RawJsonToken.EndDocument -> {
                check(stack.isEmpty()) { invalidDocMessage() }
                JsonToken.EndDocument
            }
            else -> readScalarValue(raw).moveToNextElement()
        }
    }

    private fun invalidDocMessage(): String = when (stack.top()) {
        RawJsonToken.BeginArray -> "expected ']'"
        RawJsonToken.BeginObject -> "expected '}'"
        RawJsonToken.Name -> "expected ':'"
        else -> "invalid json document"
    }

    override suspend fun peek(): RawJsonToken = peeked ?: doPeek()

    override suspend fun skipNext() {
        val startDepth = stack.size
        nextToken()
        while (stack.size > startDepth) {
            nextToken()
        }
    }

    private suspend fun doPeek(): RawJsonToken {
        val next = data.nextNonWhitespace(peek = true) ?: return RawJsonToken.EndDocument

        return when (next) {
            '{' -> RawJsonToken.BeginObject
            '"' -> if (stack.topOrNull() == RawJsonToken.BeginObject) RawJsonToken.Name else RawJsonToken.String
            '}' -> RawJsonToken.EndObject
            '[' -> RawJsonToken.BeginArray
            ']' -> RawJsonToken.EndArray
            't' -> RawJsonToken.Bool
            'f' -> RawJsonToken.Bool
            'n' -> RawJsonToken.Null
            else -> RawJsonToken.Number
        }.also {
            peeked = it
        }
    }

    private suspend fun readName(): JsonToken {
        val name = readQuoted()
        data.nextNonWhitespace(peek = true)
        data.consume(':')
        stack.push(RawJsonToken.Name)
        return JsonToken.Name(name)
    }

    private suspend fun readScalarValue(raw: RawJsonToken): JsonToken = when (raw) {
        RawJsonToken.String -> JsonToken.String(readQuoted())
        RawJsonToken.Bool, RawJsonToken.Null -> readKeyword()
        RawJsonToken.Number -> readNumber()
        else -> throw IllegalStateException("Unhandled token $raw")
    }

    private suspend fun JsonToken.moveToNextElement(): JsonToken {
        data.nextNonWhitespace(peek = true)
        val top = stack.topOrNull()
        val hadComma = data.consume(',', optional = true)
        val next = data.nextNonWhitespace(peek = true)

        // FIXME - trying to figure out comma handling with only the state we've implemented is difficult
        // extra comma, expected value, e.g.: ["foo", ]
        if (hadComma && ((top == RawJsonToken.BeginArray && next == ']') || (top == RawJsonToken.BeginObject && next == '}'))) {
            throw IllegalStateException("Unexpected char `$next` expected scalar value")
        }

        // expect comma but didn't have one, e.g.: ["foo" "bar"]
//        if (!hadComma && (next != ']' || next != '}')){
//            throw IllegalStateException("Unexpected char `$next` expected `,`")
//        }

        if (top == RawJsonToken.Name) {
            stack.pop()
        }
        return this
    }

    /**
     * Read based on the number spec : https://www.json.org/json-en.html
     * [-]0-9[.[0-9]][[E|e][+|-]0-9]
     */
    private suspend fun readNumber(): JsonToken {
        val value = buildString {
            if (data.peek() == '-') {
                append(data.nextOrThrow())
            }
            readDigits(this)
            if (data.peek() == '.') {
                append(data.nextOrThrow())
                readDigits(this)
            }
            if (data.peek() in EXP) {
                append(data.nextOrThrow())
                if (data.peek() in PLUS_MINUS) {
                    append(data.nextOrThrow())
                }
                readDigits(this)
            }
        }
        check(value.isNotEmpty()) { "Invalid number, expected '-' || 0..9, found ${data.peek()}" }
        return JsonToken.Number(value)
    }

    private suspend fun readDigits(appendable: Appendable) {
        while (data.peek() in DIGITS) {
            appendable.append(data.nextOrThrow())
        }
    }

    // reads a quoted JSON string out of the stream
    private suspend fun readQuoted(): String {
        data.consume('"')
        // read bytes until a non-escaped end-quote
        val value = buildString {
            var chr = data.peekOrThrow()
            while (chr != '"') {
                // handle escapes
                when (chr) {
                    '\\' -> {
                        // consume escape backslash
                        data.nextOrThrow()
                        when (val byte = data.nextOrThrow()) {
                            'u' -> readEscapedUnicode(this)
                            '\\' -> append('\\')
                            '/' -> append('/')
                            '"' -> append('"')
                            'b' -> append('\b')
                            'f' -> append("\u000C")
                            'r' -> append('\r')
                            'n' -> append('\n')
                            't' -> append('\t')
                            else -> throw IllegalStateException("Invalid escape character: `$byte`")
                        }
                    }
                    else -> append(data.nextOrThrow())
                }

                chr = data.peekOrThrow()
            }
        }
        data.consume('"')
        return value
    }

    /**
     * Read JSON unicode escape sequences (e.g. "\u1234") and append them to [sb]. Will also read an additional
     * codepoint if the first codepoint is the start of a surrogate pair
     */
    private suspend fun readEscapedUnicode(sb: StringBuilder) {
        // already consumed \u escape, take next 4 bytes as high
        val high = data.take(4).decodeEscapedCodePoint()
        if (high.isHighSurrogate()) {
            val escapedLow = data.take(6)
            check(escapedLow.startsWith("\\u")) { "Expected surrogate pair, found `$escapedLow`" }
            val low = escapedLow.substring(2).decodeEscapedCodePoint()
            check(low.isLowSurrogate()) { "Invalid surrogate pair: (${high.code}, ${low.code})" }
            sb.append(high, low)
        } else {
            sb.append(high)
        }
    }

    private suspend fun openStructure(expectedChar: Char, rawToken: RawJsonToken, actualToken: JsonToken): JsonToken {
        data.consume(expectedChar)
        stack.push(rawToken)
        return actualToken
    }

    private suspend fun closeStructure(expectedChar: Char, expectedStackToken: RawJsonToken, closeToken: JsonToken): JsonToken {
        data.consume(expectedChar)
        val obj = stack.pop()
        if (obj != expectedStackToken) {
            throw IllegalStateException("Unexpected close token '$expectedChar' encountered")
        }
        return closeToken
    }

    private suspend fun readKeyword(): JsonToken = when (val ch = data.peekOrThrow()) {
        't' -> readLiteral("true", JsonToken.Bool(true))
        'f' -> readLiteral("false", JsonToken.Bool(false))
        'n' -> readLiteral("null", JsonToken.Null)
        else -> throw IllegalStateException("Unable to handle keyword starting with '$ch'")
    }

    private suspend fun readLiteral(expectedString: String, token: JsonToken): JsonToken {
        data.consume(expectedString)
        return token
    }

    private suspend fun CharStream.nextNonWhitespace(peek: Boolean = false): Char? {
        while (peek()?.isWhitespace() == true) {
            next()
        }
        return if (peek) peek() else next()
    }
}

// TODO - move to util
private fun <T> MutableList<T>.push(item: T) = add(item)
private fun <T> MutableList<T>.pop(): T = removeLast()
private fun <T> MutableList<T>.popOrNull(): T? = removeLastOrNull()
private fun <T> MutableList<T>.top(): T = this[count() - 1]
private fun <T> MutableList<T>.topOrNull(): T? = if (isNotEmpty()) top() else null

private typealias Stack<T> = MutableList<T>

// decode an escaped unicode character to an integer code point (e.g. D801)
// the escape characters `\u` should be stripped from the input before calling
private fun String.decodeEscapedCodePoint(): Char {
    if (!all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) throw IllegalStateException("Invalid unicode escape: `\\u$this`")
    return toInt(16).toChar()
}
