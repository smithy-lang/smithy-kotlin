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

import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.DeserializerStateException
import software.aws.clientrt.serde.SdkFieldDescriptor
import software.aws.clientrt.serde.SdkObjectDescriptor

private enum class IteratorMode {
    LIST,
    MAP,
}

class JsonDeserializer(payload: ByteArray) : Deserializer, Deserializer.ElementIterator, Deserializer.FieldIterator, Deserializer.EntryIterator {
    private val reader = jsonStreamReader(payload)

    private var iteratorMode = IteratorMode.LIST

    private fun switchIterationMode(mode: IteratorMode) {
        iteratorMode = mode
    }

    // return the next token and require that it be of type [TExpected] or else throw an exception
    private inline fun <reified TExpected : JsonToken> nextToken(): TExpected {
        val token = reader.nextToken()
        requireToken<TExpected>(token)
        return token as TExpected
    }

    // require that the given token be of type [TExpected] or else throw an exception
    private inline fun <reified TExpected> requireToken(token: JsonToken) {
        if (token::class != TExpected::class) {
            throw DeserializerStateException("expected ${TExpected::class}; found ${token::class}")
        }
    }

    // deserializing a single byte isn't common in JSON - we are going to assume that bytes are represented
    // as numbers and user understands any truncation issues. `deserializeByte` is more common in binary
    // formats (e.g. protobufs) where the binary encoding stores metadata in a single byte (e.g. flags or headers)
    override fun deserializeByte(): Byte = deserializeDouble().toByte()

    override fun deserializeInt(): Int = deserializeDouble().toInt()

    override fun deserializeShort(): Short = deserializeDouble().toShort()

    override fun deserializeLong(): Long = deserializeDouble().toLong()

    override fun deserializeFloat(): Float = deserializeDouble().toFloat()

    override fun deserializeDouble(): Double {
        val token = nextToken<JsonToken.Number>()
        return token.value
    }

    override fun deserializeString(): String {
        val token = nextToken<JsonToken.String>()
        return (token as JsonToken.String).value
    }

    override fun deserializeBool(): Boolean {
        val token = nextToken<JsonToken.Bool>()
        return token.value
    }

    override fun deserializeStruct(descriptor: SdkFieldDescriptor?): Deserializer.FieldIterator {
        nextToken<JsonToken.BeginObject>()
        return this
    }

    override fun nextField(descriptor: SdkObjectDescriptor): Int {
        return when (reader.peek()) {
            RawJsonToken.EndObject -> {
                // consume the token
                nextToken<JsonToken.EndObject>()
                Deserializer.FieldIterator.EXHAUSTED
            }
            RawJsonToken.EndDocument -> Deserializer.FieldIterator.EXHAUSTED
            else -> {
                val token = nextToken<JsonToken.Name>()
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

    override fun deserializeList(): Deserializer.ElementIterator {
        nextToken<JsonToken.BeginArray>()
        switchIterationMode(IteratorMode.LIST)
        return this
    }

    override fun deserializeMap(): Deserializer.EntryIterator {
        nextToken<JsonToken.BeginObject>()
        switchIterationMode(IteratorMode.MAP)
        return this
    }

    override fun key(): String {
        val token = nextToken<JsonToken.Name>()
        return token.value
    }

    // next has to work for different modes of iteration (list vs map entries)
    override fun next(): Int {
        return when (iteratorMode) {
            IteratorMode.LIST -> nextList()
            IteratorMode.MAP -> nextMap()
        }
    }

    private fun nextMap(): Int {
        return when (reader.peek()) {
            RawJsonToken.EndObject -> {
                // consume the token
                nextToken<JsonToken.EndObject>()
                Deserializer.EntryIterator.EXHAUSTED
            }
            RawJsonToken.EndDocument -> Deserializer.EntryIterator.EXHAUSTED
            else -> 0
        }
    }

    private fun nextList(): Int {
        return when (reader.peek()) {
            RawJsonToken.EndArray -> {
                // consume the token
                nextToken<JsonToken.EndArray>()
                Deserializer.ElementIterator.EXHAUSTED
            }
            RawJsonToken.EndDocument -> Deserializer.ElementIterator.EXHAUSTED
            else -> 0
        }
    }
}
