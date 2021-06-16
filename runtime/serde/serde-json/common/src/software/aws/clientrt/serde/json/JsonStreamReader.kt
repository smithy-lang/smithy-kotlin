/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.json

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
internal expect fun jsonStreamReader(payload: ByteArray): JsonStreamReader
