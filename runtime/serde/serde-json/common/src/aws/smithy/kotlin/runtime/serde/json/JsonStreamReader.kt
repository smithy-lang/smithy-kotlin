/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.serde.CharStream
import aws.smithy.kotlin.runtime.serde.DeserializationException

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
}

/*
* Creates a [JsonStreamReader] instance
*/
fun jsonStreamReader(payload: ByteArray): JsonStreamReader = JsonLexer(CharStream(SdkByteReadChannel(payload)))

// return the next token and require that it be of type [TExpected] or else throw an exception
internal suspend inline fun <reified TExpected : JsonToken> JsonStreamReader.nextTokenOf(): TExpected {
    val token = this.nextToken()
    requireToken<TExpected>(token)
    return token as TExpected
}

// require that the given token be of type [TExpected] or else throw an exception
internal inline fun <reified TExpected> requireToken(token: JsonToken) {
    if (token::class != TExpected::class) {
        throw DeserializationException("expected ${TExpected::class}; found ${token::class}")
    }
}
