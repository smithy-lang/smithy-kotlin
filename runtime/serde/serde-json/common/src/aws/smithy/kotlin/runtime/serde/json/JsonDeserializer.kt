/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.smithy.Document

/**
 * Provides a deserializer for JSON documents
 *
 * @param payload underlying document from which tokens are read
 */
public class JsonDeserializer(payload: ByteArray) : Deserializer, Deserializer.ElementIterator, Deserializer.EntryIterator, PrimitiveDeserializer {
    public companion object {
        private val validNumberStrings = setOf("Infinity", "-Infinity", "NaN")
    }

    private val reader = jsonStreamReader(payload)

    // deserializing a single byte isn't common in JSON - we are going to assume that bytes are represented
    // as numbers and user understands any truncation issues. `deserializeByte` is more common in binary
    // formats (e.g. protobufs) where the binary encoding stores metadata in a single byte (e.g. flags or headers)
    override fun deserializeByte(): Byte = nextNumberValue { it.toByteOrNull() ?: it.toDouble().toInt().toByte() }

    override fun deserializeInt(): Int = nextNumberValue { it.toIntOrNull() ?: it.toDouble().toInt() }

    override fun deserializeShort(): Short = nextNumberValue { it.toShortOrNull() ?: it.toDouble().toInt().toShort() }

    override fun deserializeLong(): Long = nextNumberValue { it.toLongOrNull() ?: it.toDouble().toLong() }

    override fun deserializeFloat(): Float = deserializeDouble().toFloat()

    override fun deserializeDouble(): Double = nextNumberValue { it.toDouble() }

    // deserializes the next token as a number with the maximum discernible precision
    private fun deserializeNumber(): Number =
        nextNumberValue { if (it.contains('.')) it.toDouble() else it.toLong() }

    // assert the next token is a Number and execute [block] with the raw value as a string. Returns result
    // of executing the block. This is mostly so that numeric conversions can keep as much precision as possible
    private fun <T> nextNumberValue(block: (value: String) -> T): T {
        val token = reader.nextToken()
        return when {
            token is JsonToken.Number -> block(token.value)
            token is JsonToken.String && validNumberStrings.contains(token.value) -> block(token.value)
            else -> throw DeserializationException("$token cannot be deserialized as type Number")
        }
    }

    override fun deserializeString(): String =
        // allow for tokens to be consumed as string even when the next token isn't a quoted string
        when (val token = reader.nextToken()) {
            is JsonToken.String -> token.value
            is JsonToken.Number -> token.value
            is JsonToken.Bool -> token.value.toString()
            else -> throw DeserializationException("$token cannot be deserialized as type String")
        }

    override fun deserializeBoolean(): Boolean {
        val token = reader.nextTokenOf<JsonToken.Bool>()
        return token.value
    }

    override fun deserializeDocument(): Document =
        checkNotNull(deserializeDocumentImpl()) { "expected non-null document field" }

    private fun deserializeDocumentImpl(): Document? =
        when (val token = reader.peek()) {
            is JsonToken.Number -> Document(deserializeNumber())
            is JsonToken.String -> Document(deserializeString())
            is JsonToken.Bool -> Document(deserializeBoolean())
            JsonToken.Null -> {
                reader.nextToken()
                null
            }
            JsonToken.BeginArray ->
                deserializeList(SdkFieldDescriptor(SerialKind.Document)) {
                    val values = mutableListOf<Document?>()
                    while (hasNextElement()) {
                        values.add(deserializeDocumentImpl())
                    }
                    Document.List(values)
                }
            JsonToken.BeginObject ->
                deserializeMap(SdkFieldDescriptor(SerialKind.Document)) {
                    val values = mutableMapOf<String, Document?>()
                    while (hasNextEntry()) {
                        values[key()] = deserializeDocumentImpl()
                    }
                    Document.Map(values)
                }
            JsonToken.EndArray, JsonToken.EndObject, JsonToken.EndDocument ->
                throw DeserializationException(
                    "encountered unexpected json token \"$token\" while deserializing document",
                )
            is JsonToken.Name ->
                throw DeserializationException(
                    "encountered unexpected json field declaration \"${token.value}\" while deserializing document",
                )
        }

    override fun deserializeNull(): Nothing? {
        reader.nextTokenOf<JsonToken.Null>()
        return null
    }

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator =
        when (reader.peek()) {
            JsonToken.BeginObject -> {
                reader.nextTokenOf<JsonToken.BeginObject>()
                JsonFieldIterator(reader, descriptor, this)
            }
            JsonToken.Null -> JsonNullFieldIterator(this)
            else -> throw DeserializationException("Unexpected token type ${reader.peek()}")
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

    override fun nextHasValue(): Boolean = reader.peek() != JsonToken.Null

    override fun hasNextEntry(): Boolean =
        when (reader.peek()) {
            JsonToken.EndObject -> {
                // consume the token
                reader.nextTokenOf<JsonToken.EndObject>()
                false
            }
            JsonToken.Null,
            JsonToken.EndDocument,
            -> false
            else -> true
        }

    override fun hasNextElement(): Boolean =
        when (reader.peek()) {
            JsonToken.EndArray -> {
                // consume the token
                reader.nextTokenOf<JsonToken.EndArray>()
                false
            }
            JsonToken.EndDocument -> false
            else -> true
        }
}

// Represents the deserialization of a null object.
private class JsonNullFieldIterator(deserializer: JsonDeserializer) : Deserializer.FieldIterator, Deserializer by deserializer, PrimitiveDeserializer by deserializer {
    override fun findNextFieldIndex(): Int? = null

    override fun skipValue() {
        throw DeserializationException("This should not be called during deserialization.")
    }
}

private class JsonFieldIterator(
    private val reader: JsonStreamReader,
    private val descriptor: SdkObjectDescriptor,
    deserializer: JsonDeserializer,
) : Deserializer.FieldIterator, Deserializer by deserializer, PrimitiveDeserializer by deserializer {

    override fun findNextFieldIndex(): Int? {
        val candidate = when (reader.peek()) {
            JsonToken.EndObject -> {
                // consume the token
                reader.nextTokenOf<JsonToken.EndObject>()
                null
            }
            JsonToken.EndDocument -> null
            JsonToken.Null -> {
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

        if (candidate != null) {
            // found a field
            if (reader.peek() == JsonToken.Null) {
                // skip explicit nulls
                reader.nextTokenOf<JsonToken.Null>()
                return findNextFieldIndex()
            }
        }

        return candidate
    }

    override fun skipValue() {
        // stream reader skips the *next* token
        reader.skipNext()
    }
}
