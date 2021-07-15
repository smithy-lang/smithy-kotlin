/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.DeserializationException
import com.google.gson.stream.JsonReader
import java.nio.charset.Charset
import com.google.gson.stream.JsonToken as RawToken

private class JsonStreamReaderGson(payload: ByteArray, charset: Charset = Charsets.UTF_8) : JsonStreamReader {
    private val reader = JsonReader(payload.inputStream().reader(charset))

    override suspend fun nextToken(): JsonToken = when (peek()) {
        RawJsonToken.BeginArray -> {
            reader.beginArray()
            JsonToken.BeginArray
        }
        RawJsonToken.EndArray -> {
            reader.endArray()
            JsonToken.EndArray
        }
        RawJsonToken.BeginObject -> {
            reader.beginObject()
            JsonToken.BeginObject
        }
        RawJsonToken.EndObject -> {
            reader.endObject()
            JsonToken.EndObject
        }
        RawJsonToken.Name -> JsonToken.Name(reader.nextName())
        RawJsonToken.String -> JsonToken.String(reader.nextString())
        RawJsonToken.Number -> JsonToken.Number(reader.nextString())
        RawJsonToken.Bool -> JsonToken.Bool(reader.nextBoolean())
        RawJsonToken.Null -> {
            reader.nextNull()
            JsonToken.Null
        }
        RawJsonToken.EndDocument -> JsonToken.EndDocument
    }

    override suspend fun skipNext() = reader.skipValue()

    override suspend fun peek(): RawJsonToken = when (reader.peek()) {
        RawToken.BEGIN_ARRAY -> RawJsonToken.BeginArray
        RawToken.END_ARRAY -> RawJsonToken.EndArray
        RawToken.BEGIN_OBJECT -> RawJsonToken.BeginObject
        RawToken.END_OBJECT -> RawJsonToken.EndObject
        RawToken.NAME -> RawJsonToken.Name
        RawToken.STRING -> RawJsonToken.String
        RawToken.NUMBER -> RawJsonToken.Number
        RawToken.BOOLEAN -> RawJsonToken.Bool
        RawToken.NULL -> RawJsonToken.Null
        RawToken.END_DOCUMENT -> RawJsonToken.EndDocument
        else -> throw DeserializationException("unknown JSON token encountered during deserialization")
    }
}

/*
* Creates a [JsonStreamReader] instance
*/
// internal actual fun jsonStreamReader(payload: ByteArray): JsonStreamReader = JsonStreamReaderGson(payload)
