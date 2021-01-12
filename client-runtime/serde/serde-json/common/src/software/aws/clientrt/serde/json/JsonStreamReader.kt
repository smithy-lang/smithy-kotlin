/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.*

/**
 * Interface for deserializing JSON documents as a stream of tokens
 */
interface JsonStreamReader {
    /**
     * Grab the next token in the stream
     */
    suspend fun nextToken(): JsonToken

    /**
     * Recursively skip the next token. Meant for discarding unwanted/unrecognized properties in a JSON document
     */
    suspend fun skipNext()

    /**
     * Peek at the next token type
     */
    suspend fun peek(): RawJsonToken

    companion object {
        internal operator fun invoke(payload: ByteArray): JsonStreamReader = JsonStreamReader(CharStream(payload))
        internal operator fun invoke(payload: CharStream): JsonStreamReader = DefaultJsonStreamReader(payload)
    }
}

internal class DefaultJsonStreamReader(private val data: CharStream) : JsonStreamReader {
    private var peeked: RawJsonToken? = null
    private val stack = mutableListOf<RawJsonToken>()

    override suspend fun nextToken(): JsonToken {
        val raw = peek()
        peeked = null

        return try {
            when (raw) {
                RawJsonToken.BeginArray -> openStructure('[', RawJsonToken.BeginArray, JsonToken.BeginArray)
                RawJsonToken.EndArray -> closeStructure(']', RawJsonToken.BeginArray, JsonToken.EndArray).moveToNextElement()
                RawJsonToken.BeginObject -> openStructure('{', RawJsonToken.BeginObject, JsonToken.BeginObject)
                RawJsonToken.EndObject -> closeStructure('}', RawJsonToken.BeginObject, JsonToken.EndObject).moveToNextElement()
                RawJsonToken.Name -> readName()
                RawJsonToken.EndDocument -> JsonToken.EndDocument
                else -> readScalarValue(raw).moveToNextElement()
            }
        } catch (e: Exception) {
            throw e.takeIf { it is JsonGenerationException } ?: JsonGenerationException(e)
        }
    }

    override suspend fun peek(): RawJsonToken = peeked ?: try {
        doPeek()
    } catch (e: Exception) {
        throw e.takeIf { it is JsonGenerationException } ?: JsonGenerationException(e)
    }

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
            '"' -> if (stack.lastOrNull() == RawJsonToken.BeginObject) RawJsonToken.Name else RawJsonToken.String
            '}' -> RawJsonToken.EndObject
            '[' -> RawJsonToken.BeginArray
            ']' -> RawJsonToken.EndArray
            't' -> RawJsonToken.Bool
            'f' -> RawJsonToken.Bool
            'n' -> RawJsonToken.Null
            '-', in DIGITS -> RawJsonToken.Number
            else -> throw IllegalStateException("Expected token $next")
        }.also {
            peeked = it
        }
    }

    private suspend fun readName(): JsonToken {
        val name = readQuoted()
        data.nextNonWhitespace(peek = true)
        data.consume(':')
        stack.add(RawJsonToken.Name)
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
        if (stack.lastOrNull() == RawJsonToken.Name) {
            stack.removeLast()
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
        stack.add(rawToken)
        return actualToken
    }

    private suspend fun closeStructure(expectedChar: Char, expectedStackToken: RawJsonToken, closeToken: JsonToken): JsonToken {
        data.consume(expectedChar)
        val obj = stack.removeLast()
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

    private companion object {
        val DIGITS = ('0'..'9').toSet()
        val EXP = setOf('e', 'E')
        val PLUS_MINUS = setOf('-', '+')
    }
}
