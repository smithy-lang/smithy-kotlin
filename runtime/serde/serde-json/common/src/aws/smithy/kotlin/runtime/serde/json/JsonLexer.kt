/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.util.*

private val DIGITS = ('0'..'9').toSet()
private val EXP = setOf('e', 'E')
private val PLUS_MINUS = setOf('-', '+')

private typealias StateStack = ListStack<LexerState>
private typealias StateMutation = (StateStack) -> Unit

/**
 * Manages internal lexer state
 *
 * The entire lexer works off peeking tokens. Only when nextToken() is called should state be mutated.
 * State manager helps enforce this invariant.
 */
private data class StateManager(
    private val state: StateStack = mutableListOf(LexerState.Initial),
    private val pendingMutations: MutableList<StateMutation> = mutableListOf()
) {

    /**
     * The size of the state stack
     */
    val size: Int
        get() = state.size

    /**
     * Get the top of the state stack
     */
    val current: LexerState
        get() = state.top()

    /**
     * Remove all pending mutations and run them to bring state up to date
     */
    fun update() {
        pendingMutations.forEach { it.invoke(state) }
        pendingMutations.clear()
    }

    /**
     * Push a pending mutation
     */
    fun mutate(mutation: StateMutation) { pendingMutations.add(mutation) }
}

/**
 * Tokenizes JSON documents
 */
internal class JsonLexer(
    private val data: ByteArray
) : JsonStreamReader {
    private var peeked: JsonToken? = null
    private val state = StateManager()
    private var idx = 0

    override suspend fun nextToken(): JsonToken {
        val next = peek()
        peeked = null
        state.update()
        return next
    }

    override suspend fun peek(): JsonToken = peeked ?: doPeek().also { peeked = it }

    override suspend fun skipNext() {
        val startDepth = state.size
        nextToken()
        while (state.size > startDepth) {
            nextToken()
        }
    }

    private fun doPeek(): JsonToken =
        try {
            when (state.current) {
                LexerState.Initial -> readToken()
                LexerState.ArrayFirstValueOrEnd -> stateArrayFirstValueOrEnd()
                LexerState.ArrayNextValueOrEnd -> stateArrayNextValueOrEnd()
                LexerState.ObjectFirstKeyOrEnd -> stateObjectFirstKeyOrEnd()
                LexerState.ObjectNextKeyOrEnd -> stateObjectNextKeyOrEnd()
                LexerState.ObjectFieldValue -> stateObjectFieldValue()
            }
        } catch (ex: DeserializationException) {
            throw ex
        } catch (ex: Exception) {
            throw DeserializationException(cause = ex)
        }

    // handles the [State.ObjectFirstKeyOrEnd] state
    private fun stateObjectFirstKeyOrEnd(): JsonToken =
        when (val chr = nextNonWhitespace(peek = true)) {
            '}' -> endObject()
            '"' -> readName()
            else -> unexpectedToken(chr, "\"", "}")
        }

    // handles the [State.ObjectNextKeyOrEnd] state
    private fun stateObjectNextKeyOrEnd(): JsonToken =
        when (val chr = nextNonWhitespace(peek = true)) {
            '}' -> endObject()
            ',' -> {
                consume(',')
                nextNonWhitespace(peek = true)
                readName()
            }
            else -> unexpectedToken(chr, ",", "}")
        }

    // handles the [State.ObjectFieldValue] state
    private fun stateObjectFieldValue(): JsonToken =
        when (val chr = nextNonWhitespace(peek = true)) {
            ':' -> {
                consume(':')
                state.mutate { it.replaceTop(LexerState.ObjectNextKeyOrEnd) }
                readToken()
            }
            else -> unexpectedToken(chr, ":")
        }

    // handles the [State.ArrayFirstValueOrEnd] state
    private fun stateArrayFirstValueOrEnd(): JsonToken =
        when (nextNonWhitespace(peek = true)) {
            ']' -> endArray()
            else -> {
                state.mutate { it.replaceTop(LexerState.ArrayNextValueOrEnd) }
                readToken()
            }
        }

    // handles the [State.ArrayNextValueOrEnd] state
    private fun stateArrayNextValueOrEnd(): JsonToken =
        when (val chr = nextNonWhitespace(peek = true)) {
            ']' -> endArray()
            ',' -> {
                consume(',')
                readToken()
            }
            else -> unexpectedToken(chr, ",", "]")
        }

    // discards the '{' character and pushes 'ObjectFirstKeyOrEnd' state
    private fun startObject(): JsonToken {
        consume('{')
        state.mutate { it.push(LexerState.ObjectFirstKeyOrEnd) }
        return JsonToken.BeginObject
    }

    // discards the '}' character and pops the current state
    private fun endObject(): JsonToken {
        consume('}')
        val top = state.current
        lexerCheck(top == LexerState.ObjectFirstKeyOrEnd || top == LexerState.ObjectNextKeyOrEnd, idx - 1) { "Unexpected close `}` encountered" }
        state.mutate { it.pop() }
        return JsonToken.EndObject
    }

    // discards the '[' and pushes 'ArrayFirstValueOrEnd' state
    private fun startArray(): JsonToken {
        consume('[')
        state.mutate { it.push(LexerState.ArrayFirstValueOrEnd) }
        return JsonToken.BeginArray
    }

    // discards the '}' character and pops the current state
    private fun endArray(): JsonToken {
        consume(']')
        val top = state.current
        lexerCheck(top == LexerState.ArrayFirstValueOrEnd || top == LexerState.ArrayNextValueOrEnd, idx - 1) { "Unexpected close `]` encountered" }
        state.mutate { it.pop() }
        return JsonToken.EndArray
    }

    // read an object key
    private fun readName(): JsonToken {
        val name = when (val chr = peekOrThrow()) {
            '"' -> readQuoted()
            else -> unexpectedToken(chr, "\"")
        }
        state.mutate { it.replaceTop(LexerState.ObjectFieldValue) }
        return JsonToken.Name(name)
    }

    // read the next token from the stream. This is only invoked from state functions which guarantees
    // the current state should be such that the next character is the start of a token
    private fun readToken(): JsonToken =
        when (val chr = nextNonWhitespace(peek = true)) {
            '{' -> startObject()
            '[' -> startArray()
            '"' -> JsonToken.String(readQuoted())
            't', 'f', 'n' -> readKeyword()
            '-', in '0'..'9' -> readNumber()
            null -> JsonToken.EndDocument
            else -> unexpectedToken(chr, "{", "[", "\"", "null", "true", "false", "<number>")
        }

    /**
     * Read based on the number spec : https://www.json.org/json-en.html
     * [-]0-9[.[0-9]][[E|e][+|-]0-9]
     */
    private fun readNumber(): JsonToken {
        val value = buildString {
            if (peekChar() == '-') {
                append(nextOrThrow())
            }
            readDigits(this)
            if (peekChar() == '.') {
                append(nextOrThrow())
                readDigits(this)
            }
            if (peekChar() in EXP) {
                append(nextOrThrow())
                if (peekChar() in PLUS_MINUS) {
                    append(nextOrThrow())
                }
                readDigits(this)
            }
        }
        lexerCheck(value.isNotEmpty()) { "Invalid number, expected `-` || 0..9, found `${peekChar()}`" }
        return JsonToken.Number(value)
    }

    private fun readDigits(appendable: Appendable) {
        while (peekChar() in DIGITS) {
            appendable.append(nextOrThrow())
        }
    }

    /**
     * Read a quoted JSON string out of the stream
     */
    private fun readQuoted(): String {
        consume('"')
        // read bytes until a non-escaped end-quote
        val start = idx
        var chr = peekOrThrow()
        var needsUnescaped = false
        while (chr != '"') {
            // handle escapes
            when (chr) {
                '\\' -> {
                    needsUnescaped = true
                    // consume escape backslash
                    nextOrThrow()
                    when (val byte = nextOrThrow()) {
                        'u' -> {
                            if (idx + 4 >= data.size) throw DeserializationException("Unexpected EOF")
                            idx += 4
                        }
                        '\\', '/', '"', 'b', 'f', 'r', 'n', 't' -> { } // already consumed
                        else -> fail("Invalid escape character: `$byte`", idx - 1)
                    }
                }
                else -> {
                    if (chr.isControl()) fail("Unexpected control character: `$chr`")
                    idx++
                }
            }

            chr = peekOrThrow()
        }

        val value = data.decodeToString(start, idx)
        consume('"')
        return if (needsUnescaped) value.unescape() else value
    }

    private fun readKeyword(): JsonToken = when (val ch = peekOrThrow()) {
        't' -> readLiteral("true", JsonToken.Bool(true))
        'f' -> readLiteral("false", JsonToken.Bool(false))
        'n' -> readLiteral("null", JsonToken.Null)
        else -> fail("Unable to handle keyword starting with '$ch'")
    }

    private fun readLiteral(expectedString: String, token: JsonToken): JsonToken {
        consume(expectedString)
        return token
    }

    /**
     * Advance the cursor until next non-whitespace character is encountered
     * @param peek Flag indicating if the next non-whitespace character should be consumed or peeked
     */
    private fun nextNonWhitespace(peek: Boolean = false): Char? {
        while (peekChar()?.isWhitespace() == true) {
            idx++
        }
        return if (peek) peekChar() else nextOrThrow()
    }

    /**
     * Invoke [consume] for each character in [expected]
     */
    private fun consume(expected: String) = expected.forEach { consume(it) }

    /**
     * Assert that the next character is [expected] and advance
     */
    private fun consume(expected: Char) {
        val chr = data[idx].toInt().toChar()
        lexerCheck(chr == expected) { "Unexpected char `$chr` expected `$expected`" }
        idx++
    }

    /**
     * Return next byte to consume or null if EOF has been reached
     */
    private fun peekByte(): Byte? = data.getOrNull(idx)

    /**
     * Peek the next character or return null if EOF has been reached
     *
     * SAFETY: This assumes ASCII. This is safe because we _only_ use it for tokenization
     * (e.g. {, }, [, ], <number>, <ws>, etc). When reading object keys or string values [readQuoted] is
     * used which handles UTF-8. Do not use these single char related functions to directly construct a string!
     *
     * NOTE: [readQuoted] uses [decodeToString] which is _MUCH_ faster (~3x) than decoding bytes as
     * UTF-8 chars one by one on the fly.
     */
    private fun peekChar(): Char? = peekByte()?.toInt()?.toChar()

    /**
     * Peek the next character or throw if EOF has been reached
     */
    private fun peekOrThrow(): Char = peekChar() ?: throw IllegalStateException("Unexpected EOF")

    /**
     * Consume the next character and advance the index or throw if EOF has been reached
     */
    private fun nextOrThrow(): Char = peekOrThrow().also { idx++ }

    private fun unexpectedToken(found: Char?, vararg expected: String): Nothing {
        val pluralModifier = if (expected.size > 1) " one of" else ""
        val formatted = expected.joinToString(separator = ", ") { "`$it`" }
        fail("found '$found', expected$pluralModifier $formatted")
    }

    private fun fail(message: String, offset: Int = idx, cause: Throwable? = null): Nothing {
        throw DeserializationException("Unexpected JSON token at offset $offset; $message", cause)
    }

    private inline fun lexerCheck(value: Boolean, offset: Int = idx, lazyMessage: () -> Any) {
        if (!value) {
            val message = lazyMessage()
            fail(message.toString(), offset)
        }
    }
}

/**
 * Unescape a JSON string (either object key or string value)
 */
private fun String.unescape(): String {
    val str = this
    return buildString(str.length + 1) {
        var i = 0
        while (i < str.length) {
            val chr = str[i]
            when (chr) {
                '\\' -> {
                    i++ // consume backslash
                    when (val byte = str[i++]) {
                        'u' -> {
                            i += readEscapedUnicode(str, i, this)
                        }
                        '\\' -> append('\\')
                        '/' -> append('/')
                        '"' -> append('"')
                        'b' -> append('\b')
                        'f' -> append('\u000C')
                        'r' -> append('\r')
                        'n' -> append('\n')
                        't' -> append('\t')
                        else -> throw DeserializationException("Invalid escape character: `$byte`")
                    }
                }
                else -> {
                    append(chr)
                    i++
                }
            }
        }
    }
}

/**
 * Reads an escaped unicode code point from [s] starting at [start] offset. This assumes that '\u' has already
 * been consumed and [start] is pointing to the first hex digit. If the code point represents a surrogate pair
 * an additional escaped code point will be consumed from the string.
 * @param s The string to decode from
 * @param start The starting index to start reading from
 * @param sb The string builder to append unescaped unicode characters to
 * @return The number of characters consumed
 */
private fun readEscapedUnicode(s: String, start: Int, sb: StringBuilder): Int {
    // already consumed \u escape, take next 4 bytes as high
    check(start + 4 <= s.length) { "Unexpected EOF reading escaped high surrogate" }
    val high = s.substring(start, start + 4).decodeEscapedCodePoint()
    var consumed = 4
    if (high.isHighSurrogate()) {
        val lowStart = start + consumed
        val escapedLow = s.substring(lowStart, lowStart + 6)
        check(escapedLow.startsWith("\\u")) { "Expected surrogate pair, found `$escapedLow`" }
        val low = escapedLow.substring(2).decodeEscapedCodePoint()
        check(low.isLowSurrogate()) { "Invalid surrogate pair: (${high.code}, ${low.code})" }
        sb.append(high, low)
        consumed += 6
    } else {
        sb.append(high)
    }
    return consumed
}

/**
 * decode an escaped unicode character to an integer code point (e.g. D801)
 * the escape characters `\u` should be stripped from the input before calling
 */
private fun String.decodeEscapedCodePoint(): Char {
    check(all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Invalid unicode escape: `\\u$this`" }
    return toInt(16).toChar()
}

/**
 * Test whether a character is a control character (ignoring SP and DEL)
 */
private fun Char.isControl(): Boolean = code in 0x00..0x1F
