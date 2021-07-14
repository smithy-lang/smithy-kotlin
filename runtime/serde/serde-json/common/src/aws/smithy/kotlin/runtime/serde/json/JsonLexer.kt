/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.CharStream
import aws.smithy.kotlin.runtime.serde.consume
import aws.smithy.kotlin.runtime.serde.nextOrThrow
import aws.smithy.kotlin.runtime.serde.peekOrThrow
import aws.smithy.kotlin.runtime.serde.readUntil

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
            RawJsonToken.EndDocument -> JsonToken.EndDocument
            else -> readScalarValue(raw).moveToNextElement()
        }
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

    private suspend fun readScalarValue(raw: RawJsonToken) = when (raw) {
        RawJsonToken.String -> JsonToken.String(readQuoted())
        RawJsonToken.Bool, RawJsonToken.Null -> readKeyWord()
        RawJsonToken.Number -> readNumber()
        else -> throw IllegalStateException("Unhandled token $raw")
    }

    private suspend fun JsonToken.moveToNextElement(): JsonToken {
        data.nextNonWhitespace(peek = true)
        data.consume(',', optional = true)
        if (stack.topOrNull() == RawJsonToken.Name) {
            stack.pop()
        }
        return this
    }

    /**
     * Read based on the number spec : https://www.json.org/json-en.html
     * [-]0-9[.[0-9]][[E|e][+|-]0-9]
     */
    private suspend fun readNumber(): JsonToken {
        val value = with(StringBuilder()) {
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
            toString()
        }
        return JsonToken.Number(value)
    }

    private suspend fun readDigits(appendable: Appendable) {
        while (data.peek() in DIGITS) {
            appendable.append(data.nextOrThrow())
        }
    }

    private suspend fun readQuoted(): String {
        data.consume('"')
        val value = data.readUntil { it == '"' }
        data.consume('"')
        return value
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

    private suspend fun readKeyWord(): JsonToken = when (val ch = data.peekOrThrow()) {
        't' -> readKeyWord("true", JsonToken.Bool(true))
        'f' -> readKeyWord("false", JsonToken.Bool(false))
        'n' -> readKeyWord("null", JsonToken.Null)
        else -> throw IllegalStateException("Unable to handle keyword starting with '$ch'")
    }

    private suspend fun readKeyWord(expectedString: String, token: JsonToken): JsonToken {
        data.consume(expectedString)
        return token
    }

    private suspend fun CharStream.nextNonWhitespace(peek: Boolean = false): Char? {
        while (this.peek()?.isWhitespace() == true) {
            this.next()
        }
        return if (peek) this.peek() else this.next()
    }
}

// TODO - move to util
private fun <T> MutableList<T>.push(item: T) = add(item)
private fun <T> MutableList<T>.pop(): T = removeLast()
private fun <T> MutableList<T>.popOrNull(): T? = removeLastOrNull()
private fun <T> MutableList<T>.top(): T = this[count() - 1]
private fun <T> MutableList<T>.topOrNull(): T? = if (isNotEmpty()) top() else null

private typealias Stack<T> = MutableList<T>
