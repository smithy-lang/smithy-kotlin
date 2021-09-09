/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

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

    /**
     * Get the top of the state stack
     */
    fun current(): LexerState = state.top()
}

/**
 * Tokenizes JSON documents
 */
internal class JsonLexer(
    private val data: CharStream
) : JsonStreamReader {
    private var peeked: JsonToken? = null
    private val state = StateManager()

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

    private suspend fun doPeek(): JsonToken {
        try {
            return when (state.current()) {
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
    }

    // handles the [State.ObjectFirstKeyOrEnd] state
    private suspend fun stateObjectFirstKeyOrEnd(): JsonToken =
        when (val chr = data.nextNonWhitespace(peek = true)) {
            '}' -> endObject()
            '"' -> readName()
            else -> throw unexpectedToken(chr, "\"", "}")
        }

    // handles the [State.ObjectNextKeyOrEnd] state
    private suspend fun stateObjectNextKeyOrEnd(): JsonToken =
        when (val chr = data.nextNonWhitespace(peek = true)) {
            '}' -> endObject()
            ',' -> {
                data.consume(',')
                data.nextNonWhitespace(peek = true)
                readName()
            }
            else -> throw unexpectedToken(chr, ",", "}")
        }

    // handles the [State.ObjectFieldValue] state
    private suspend fun stateObjectFieldValue(): JsonToken =
        when (val chr = data.nextNonWhitespace(peek = true)) {
            ':' -> {
                data.consume(':')
                state.mutate { it.replaceTop(LexerState.ObjectNextKeyOrEnd) }
                readToken()
            }
            else -> throw unexpectedToken(chr, ":")
        }

    // handles the [State.ArrayFirstValueOrEnd] state
    private suspend fun stateArrayFirstValueOrEnd(): JsonToken =
        when (data.nextNonWhitespace(peek = true)) {
            ']' -> endArray()
            else -> {
                state.mutate { it.replaceTop(LexerState.ArrayNextValueOrEnd) }
                readToken()
            }
        }

    // handles the [State.ArrayNextValueOrEnd] state
    private suspend fun stateArrayNextValueOrEnd(): JsonToken =
        when (val chr = data.nextNonWhitespace(peek = true)) {
            ']' -> endArray()
            ',' -> {
                data.consume(',')
                readToken()
            }
            else -> throw unexpectedToken(chr, ",", "]")
        }

    // discards the '{' character and pushes 'ObjectFirstKeyOrEnd' state
    private suspend fun startObject(): JsonToken {
        data.consume('{')
        state.mutate { it.push(LexerState.ObjectFirstKeyOrEnd) }
        return JsonToken.BeginObject
    }

    // discards the '}' character and pops the current state
    private suspend fun endObject(): JsonToken {
        data.consume('}')
        val top = state.current()
        lexerCheck(top == LexerState.ObjectFirstKeyOrEnd || top == LexerState.ObjectNextKeyOrEnd) { "Unexpected close `}` encountered" }
        state.mutate { it.pop() }
        return JsonToken.EndObject
    }

    // discards the '[' and pushes 'ArrayFirstValueOrEnd' state
    private suspend fun startArray(): JsonToken {
        data.consume('[')
        state.mutate { it.push(LexerState.ArrayFirstValueOrEnd) }
        return JsonToken.BeginArray
    }

    // discards the '}' character and pops the current state
    private suspend fun endArray(): JsonToken {
        data.consume(']')
        val top = state.current()
        lexerCheck(top == LexerState.ArrayFirstValueOrEnd || top == LexerState.ArrayNextValueOrEnd) { "Unexpected close `]` encountered" }
        state.mutate { it.pop() }
        return JsonToken.EndArray
    }

    // read an object key
    private suspend fun readName(): JsonToken {
        val name = when (val chr = data.peekOrThrow()) {
            '"' -> readQuoted()
            else -> throw unexpectedToken(chr, "\"")
        }
        state.mutate { it.replaceTop(LexerState.ObjectFieldValue) }
        return JsonToken.Name(name)
    }

    // read the next token from the stream. This is only invoked from state functions which guarantees
    // the current state should be such that the next character is the start of a token
    private suspend fun readToken(): JsonToken =
        when (val chr = data.nextNonWhitespace(peek = true)) {
            '{' -> startObject()
            '[' -> startArray()
            '"' -> JsonToken.String(readQuoted())
            't', 'f', 'n' -> readKeyword()
            '-', in '0'..'9' -> readNumber()
            null -> JsonToken.EndDocument
            else -> throw unexpectedToken(chr, "{", "[", "\"", "null", "true", "false", "<number>")
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
        lexerCheck(value.isNotEmpty()) { "Invalid number, expected '-' || 0..9, found ${data.peek()}" }
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
                            else -> throw DeserializationException("Invalid escape character: `$byte`")
                        }
                    }
                    else -> {
                        if (chr.isControl()) throw DeserializationException("Unescaped control character: `$chr`")
                        append(data.nextOrThrow())
                    }
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
            lexerCheck(escapedLow.startsWith("\\u")) { "Expected surrogate pair, found `$escapedLow`" }
            val low = escapedLow.substring(2).decodeEscapedCodePoint()
            lexerCheck(low.isLowSurrogate()) { "Invalid surrogate pair: (${high.code}, ${low.code})" }
            sb.append(high, low)
        } else {
            sb.append(high)
        }
    }

    private suspend fun readKeyword(): JsonToken = when (val ch = data.peekOrThrow()) {
        't' -> readLiteral("true", JsonToken.Bool(true))
        'f' -> readLiteral("false", JsonToken.Bool(false))
        'n' -> readLiteral("null", JsonToken.Null)
        else -> throw DeserializationException("Unable to handle keyword starting with '$ch'")
    }

    private suspend fun readLiteral(expectedString: String, token: JsonToken): JsonToken {
        data.consume(expectedString)
        return token
    }
}

// decode an escaped unicode character to an integer code point (e.g. D801)
// the escape characters `\u` should be stripped from the input before calling
private fun String.decodeEscapedCodePoint(): Char {
    if (!all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) throw DeserializationException("Invalid unicode escape: `\\u$this`")
    return toInt(16).toChar()
}

@OptIn(ExperimentalContracts::class)
private inline fun lexerCheck(value: Boolean, lazyMessage: () -> Any) {
    contract {
        returns() implies value
    }
    if (!value) {
        val message = lazyMessage()
        throw DeserializationException(message.toString())
    }
}

// Test whether a character is a control character (ignoring SP and DEL)
private fun Char.isControl(): Boolean = code in 0x00..0x1F

private fun unexpectedToken(found: Char?, vararg expected: String): DeserializationException {
    val pluralModifier = if (expected.size > 1) " one of" else ""
    val formatted = expected.joinToString(separator = ", ") { "'$it'" }
    return DeserializationException("Unexpected token '$found', expected$pluralModifier $formatted")
}
