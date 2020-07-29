/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.*

class JsonDeserializer(payload: ByteArray) : Deserializer, Deserializer.ElementIterator, Deserializer.EntryIterator {
    private val reader = jsonStreamReader(payload)

    // deserializing a single byte isn't common in JSON - we are going to assume that bytes are represented
    // as numbers and user understands any truncation issues. `deserializeByte` is more common in binary
    // formats (e.g. protobufs) where the binary encoding stores metadata in a single byte (e.g. flags or headers)
    override fun deserializeByte(): Byte = nextNumberValue { it.toByteOrNull() ?: it.toDouble().toByte() }

    override fun deserializeInt(): Int = nextNumberValue { it.toIntOrNull() ?: it.toDouble().toInt() }

    override fun deserializeShort(): Short = nextNumberValue { it.toShortOrNull() ?: it.toDouble().toShort() }

    override fun deserializeLong(): Long = nextNumberValue { it.toLongOrNull() ?: it.toDouble().toLong() }

    override fun deserializeFloat(): Float = deserializeDouble().toFloat()

    override fun deserializeDouble(): Double = nextNumberValue { it.toDouble() }

    // assert the next token is a Number and execute [block] with the raw value as a string. Returns result
    // of executing the block. This is mostly so that numeric conversions can keep as much precision as possible
    private fun <T> nextNumberValue(block: (value: String) -> T): T {
        val token = reader.nextTokenOf<JsonToken.Number>()
        return block(token.value)
    }

    override fun deserializeString(): String {
        val token = reader.nextTokenOf<JsonToken.String>()
        return token.value
    }

    override fun deserializeBool(): Boolean {
        val token = reader.nextTokenOf<JsonToken.Bool>()
        return token.value
    }

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        reader.nextTokenOf<JsonToken.BeginObject>()
        return JsonFieldIterator(reader, descriptor, this)
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

    override fun hasNextEntry(): Boolean {
        return when (reader.peek()) {
            RawJsonToken.EndObject -> {
                // consume the token
                reader.nextTokenOf<JsonToken.EndObject>()
                false
            }
            RawJsonToken.EndDocument -> false
            else -> true
        }
    }

    override fun hasNextElement(): Boolean {
        return when (reader.peek()) {
            RawJsonToken.EndArray -> {
                // consume the token
                reader.nextTokenOf<JsonToken.EndArray>()
                false
            }
            RawJsonToken.EndDocument -> false
            else -> true
        }
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
