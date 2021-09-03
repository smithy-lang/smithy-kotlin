/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private val DIGITS = ('0'..'9').toSet()
private val EXP = setOf('e', 'E')
private val PLUS_MINUS = setOf('-', '+')

private enum class State {
    // Entry point. Expecting any JSON value
    Initial,
    // Expecting the next token to be the *first* value in an array, or the end of the array.
    ArrayFirstValueOrEnd,
    // Expecting the next token to the next value in an array, or the end of the array.
    ArrayNextValueOrEnd,
    // Expecting the next token to be the *first* key in the object, or the end of the object.
    ObjectFirstKeyOrEnd,
    // Expecting the next token to the next object key, or the end of the object.
    ObjectNextKeyOrEnd,
    // Expecting the next token to be the value of a field in an object.
    ObjectFieldValue,
}

private typealias StateMutation = () -> Unit

internal class JsonLexer(
    private val data: CharStream
) : JsonStreamReader {
    private var peeked: JsonToken? = null
    private val pendingStateMutations: MutableList<StateMutation> = mutableListOf()
    private val state: Stack<State> = mutableListOf(State.Initial)

    override suspend fun nextToken(): JsonToken {
        val next = peek()
        peeked = null
        pendingStateMutations.forEach(StateMutation::invoke)
        pendingStateMutations.clear()
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
            return when (state.top()) {
                State.Initial -> readToken()
                State.ArrayFirstValueOrEnd -> stateArrayFirstValueOrEnd()
                State.ArrayNextValueOrEnd -> stateArrayNextValueOrEnd()
                State.ObjectFirstKeyOrEnd -> stateObjectFirstKeyOrEnd()
                State.ObjectNextKeyOrEnd -> stateObjectNextKeyOrEnd()
                State.ObjectFieldValue -> stateObjectFieldValue()
            }
        } catch (ex: DeserializationException) {
            throw ex
        } catch (ex: Exception) {
            throw DeserializationException(cause = ex)
        }
    }

    private suspend fun stateObjectFirstKeyOrEnd(): JsonToken {
        return when (val chr = data.nextNonWhitespace(peek = true)) {
            '}' -> endObject()
            '"' -> readName()
            else -> throw unexpectedToken(chr, "\"")
        }
    }

    private suspend fun stateObjectNextKeyOrEnd(): JsonToken {
        return when (val chr = data.nextNonWhitespace(peek = true)) {
            '}' -> endObject()
            ',' -> {
                data.consume(',')
                data.nextNonWhitespace(peek = true)
                readName()
            }
            else -> throw unexpectedToken(chr, ",", "}")
        }
    }

    private suspend fun stateObjectFieldValue(): JsonToken {
        return when (val chr = data.nextNonWhitespace(peek = true)) {
            ':' -> {
                data.consume(':')
                pendingStateMutations.add { state.replaceTop(State.ObjectNextKeyOrEnd) }
                readToken()
            }
            else -> throw unexpectedToken(chr, ":")
        }
    }

    private suspend fun stateArrayFirstValueOrEnd(): JsonToken {
        return when (data.nextNonWhitespace(peek = true)) {
            ']' -> endArray()
            else -> {
                pendingStateMutations.add { state.replaceTop(State.ArrayNextValueOrEnd) }
                readToken()
            }
        }
    }
    private suspend fun stateArrayNextValueOrEnd(): JsonToken {
        return when (val chr = data.nextNonWhitespace(peek = true)) {
            ']' -> endArray()
            ',' -> {
                data.consume(',')
                readToken()
            }
            else -> throw unexpectedToken(chr, ",", "]")
        }
    }

    // discards the '{' character and pushes 'ObjectFirstKeyOrEnd' state
    private suspend fun startObject(): JsonToken {
        data.consume('{')
        pendingStateMutations.add { state.push(State.ObjectFirstKeyOrEnd) }
        return JsonToken.BeginObject
    }

    // discards the '}' character and pops the current state
    private suspend fun endObject(): JsonToken {
        data.consume('}')
        val top = state.top()
        lexerCheck(top == State.ObjectFirstKeyOrEnd || top == State.ObjectNextKeyOrEnd) { "Unexpected close `}` encountered" }
        pendingStateMutations.add { state.pop() }
        return JsonToken.EndObject
    }

    // discards the '[' and pushes 'ArrayFirstValueOrEnd' state
    private suspend fun startArray(): JsonToken {
        data.consume('[')
        pendingStateMutations.add { state.push(State.ArrayFirstValueOrEnd) }
        return JsonToken.BeginArray
    }

    // discards the '}' character and pops the current state
    private suspend fun endArray(): JsonToken {
        data.consume(']')
        val top = state.top()
        lexerCheck(top == State.ArrayFirstValueOrEnd || top == State.ArrayNextValueOrEnd) { "Unexpected close `]` encountered" }
        pendingStateMutations.add { state.pop() }
        return JsonToken.EndArray
    }

    // read an object key
    private suspend fun readName(): JsonToken {
        val name = when (val chr = data.peekOrThrow()) {
            '"' -> readQuoted()
            else -> throw unexpectedToken(chr, "\"")
        }
        pendingStateMutations.add { state.replaceTop(State.ObjectFieldValue) }
        return JsonToken.Name(name)
    }

    // read the next token from the stream (only called from state functions)
    private suspend fun readToken(): JsonToken {
        return when (val chr = data.nextNonWhitespace(peek = true)) {
            '{' -> startObject()
            '[' -> startArray()
            '"' -> JsonToken.String(readQuoted())
            't', 'f', 'n' -> readKeyword()
            '-', in '0'..'9' -> readNumber()
            null -> JsonToken.EndDocument
            else -> throw unexpectedToken(chr, "{", "[", "\"", "null", "true", "false", "<number>")
        }
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

    // fixme move to CharStream extensions
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
private fun <T> MutableList<T>.replaceTop(item: T): T? {
    val lastTop = popOrNull()
    push(item)
    return lastTop
}

private typealias Stack<T> = MutableList<T>

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
