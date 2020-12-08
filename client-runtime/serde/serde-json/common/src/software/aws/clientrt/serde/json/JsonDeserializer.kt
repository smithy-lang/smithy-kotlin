/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.*

class JsonDeserializer(payload: ByteArray) : Deserializer, Deserializer.ElementIterator, Deserializer.EntryIterator {
    private val reader = jsonStreamReader(payload)

    // deserializing a single byte isn't common in JSON - we are going to assume that bytes are represented
    // as numbers and user understands any truncation issues. `deserializeByte` is more common in binary
    // formats (e.g. protobufs) where the binary encoding stores metadata in a single byte (e.g. flags or headers)
    override fun deserializeByte(): Byte? = nextNumberValue { it.toByteOrNull() ?: it.toDouble().toInt().toByte() }

    override fun deserializeInt(): Int? = nextNumberValue { it.toIntOrNull() ?: it.toDouble().toInt() }

    override fun deserializeShort(): Short? = nextNumberValue { it.toShortOrNull() ?: it.toDouble().toInt().toShort() }

    override fun deserializeLong(): Long? = nextNumberValue { it.toLongOrNull() ?: it.toDouble().toLong() }

    override fun deserializeFloat(): Float? = deserializeDouble()?.toFloat()

    override fun deserializeDouble(): Double? = nextNumberValue { it.toDouble() }

    // assert the next token is a Number and execute [block] with the raw value as a string. Returns result
    // of executing the block. This is mostly so that numeric conversions can keep as much precision as possible
    private fun <T> nextNumberValue(block: (value: String) -> T): T? =
        when (val token = reader.nextToken()) {
            is JsonToken.Number -> block(token.value)
            is JsonToken.Null -> null
            else -> throw DeserializationException("$token cannot be deserialized as type Number")
        }

    override fun deserializeString(): String? =
        // allow for tokens to be consumed as string even when the next token isn't a quoted string
        when (val token = reader.nextToken()) {
            is JsonToken.String -> token.value
            is JsonToken.Number -> token.value
            is JsonToken.Bool -> token.value.toString()
            is JsonToken.Null -> null
            else -> throw DeserializationException("$token cannot be deserialized as type String")
        }

    override fun deserializeBool(): Boolean? =
        when (val token = reader.nextToken()) {
            is JsonToken.Bool -> token.value
            is JsonToken.Null -> null
            else -> throw DeserializationException("$token cannot be deserialized as type Boolean")
        }

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator =
        when (reader.peek()) {
            RawJsonToken.BeginObject -> {
                reader.nextTokenOf<JsonToken.BeginObject>()
                JsonFieldIterator(reader, descriptor, this)
            }
            RawJsonToken.Null -> JsonNullFieldIterator(this)
            else -> error("Unexpected token type ${reader.peek()}")
        }

    override fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        reader.nextTokenOf<JsonToken.BeginArray>()
        return this
    }

    override fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        reader.nextTokenOf<JsonToken.BeginObject>()
        return this
    }

    override fun key(): String {
        val token = reader.nextTokenOf<JsonToken.Name>()
        return token.value
    }

    override fun hasNextEntry(): Boolean =
        when (reader.peek()) {
            RawJsonToken.EndObject -> {
                // consume the token
                reader.nextTokenOf<JsonToken.EndObject>()
                false
            }
            RawJsonToken.Null,
            RawJsonToken.EndDocument -> false
            else -> true
        }

    override fun hasNextElement(): Boolean =
        when (reader.peek()) {
            RawJsonToken.EndArray -> {
                // consume the token
                reader.nextTokenOf<JsonToken.EndArray>()
                false
            }
            RawJsonToken.EndDocument -> false
            else -> true
        }
}

// Represents the deserialization of a null object.
private class JsonNullFieldIterator(deserializer: Deserializer) : Deserializer.FieldIterator, Deserializer by deserializer {
    override fun findNextFieldIndex(): Int? = null

    override fun skipValue() {
        error("This should not be called during deserialization.")
    }
}

private class JsonFieldIterator(
    private val reader: JsonStreamReader,
    private val descriptor: SdkObjectDescriptor,
    deserializer: Deserializer
) : Deserializer.FieldIterator, Deserializer by deserializer {

    override fun findNextFieldIndex(): Int? {
        return when (reader.peek()) {
            RawJsonToken.EndObject -> {
                // consume the token
                reader.nextTokenOf<JsonToken.EndObject>()
                null
            }
            RawJsonToken.EndDocument -> null
            RawJsonToken.Null -> {
                reader.nextTokenOf<JsonToken.Null>()
                null
            }
            else -> {
                val token = reader.nextTokenOf<JsonToken.Name>()
                val propertyName = token.value
                val field = descriptor.fields.find { it.serialName == propertyName }
                field?.index ?: Deserializer.FieldIterator.UNKNOWN_FIELD
            }
        }
    }

    override fun skipValue() {
        // stream reader skips the *next* token
        reader.skipNext()
    }
}

// return the next token and require that it be of type [TExpected] or else throw an exception
private inline fun <reified TExpected : JsonToken> JsonStreamReader.nextTokenOf(): TExpected {
    val token = this.nextToken()
    requireToken<TExpected>(token)
    return token as TExpected
}

// require that the given token be of type [TExpected] or else throw an exception
private inline fun <reified TExpected> requireToken(token: JsonToken) {
    if (token::class != TExpected::class) {
        throw DeserializerStateException("expected ${TExpected::class}; found ${token::class}")
    }
}
