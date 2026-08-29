/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.schema.MemberSchema
import aws.smithy.kotlin.runtime.serde.schema.Schema
import aws.smithy.kotlin.runtime.serde.schema.StructureSchema
import aws.smithy.kotlin.runtime.serde.schema.UnionSchema
import aws.smithy.kotlin.runtime.serde.schema.getTrait
import aws.smithy.kotlin.runtime.serde.schema.resolveTimestampFormat
import aws.smithy.kotlin.runtime.serde.schema.serde.ListConsumer
import aws.smithy.kotlin.runtime.serde.schema.serde.MapConsumer
import aws.smithy.kotlin.runtime.serde.schema.serde.ShapeDeserializer
import aws.smithy.kotlin.runtime.serde.schema.serde.StructConsumer
import aws.smithy.kotlin.runtime.serde.schema.trait.JsonNameTrait
import aws.smithy.kotlin.runtime.text.encoding.decodeBase64Bytes
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.ParseException
import aws.smithy.kotlin.runtime.time.TimestampFormat as WireTimestampFormat

public class JsonShapeDeserializer(
    source: ByteArray,
    private val settings: JsonCodecSettings = JsonCodecSettings(),
) : ShapeDeserializer {
    private val reader = jsonStreamReader(source)

    // ── simple types ─────────────────────────────────────────────────────────────────────────────

    override fun readBoolean(schema: Schema): Boolean = reader.nextTokenOf<JsonToken.Bool>().value

    override fun readByte(schema: Schema): Byte = nextNumber { it.toByteOrNull() ?: it.toDouble().toInt().toByte() }

    override fun readShort(schema: Schema): Short = nextNumber { it.toShortOrNull() ?: it.toDouble().toInt().toShort() }

    override fun readInt(schema: Schema): Int = nextNumber { it.toIntOrNull() ?: it.toDouble().toInt() }

    override fun readLong(schema: Schema): Long = nextNumber { it.toLongOrNull() ?: it.toDouble().toLong() }

    override fun readFloat(schema: Schema): Float = readDouble(schema).toFloat()

    override fun readDouble(schema: Schema): Double = nextNumber { it.toDouble() }

    override fun readBigInteger(schema: Schema): BigInteger = nextNumber(::BigInteger)

    override fun readBigDecimal(schema: Schema): BigDecimal = nextNumber(::BigDecimal)

    override fun readString(schema: Schema): String = when (val token = reader.nextToken()) {
        is JsonToken.String -> token.value
        is JsonToken.Number -> token.value
        is JsonToken.Bool -> token.value.toString()
        else -> throw DeserializationException("$token cannot be deserialized as type String")
    }

    override fun readBlob(schema: Schema): ByteArray = readString(schema).decodeBase64Bytes()

    override fun readTimestamp(schema: Schema): Instant {
        val fmt = schema.resolveTimestampFormat(settings.defaultTimestampFormat)
        return try {
            when (fmt) {
                WireTimestampFormat.EPOCH_SECONDS -> Instant.fromEpochSeconds(readRawNumberOrString())
                WireTimestampFormat.RFC_5322 -> Instant.fromRfc5322(readString(schema))
                else -> Instant.fromIso8601(readString(schema))
            }
        } catch (e: ParseException) {
            throw DeserializationException("cannot deserialize timestamp value", e)
        }
    }

    override fun readDocument(schema: Schema): Document? = readDocumentValue()

    override fun isNull(): Boolean = when (reader.peek()) {
        JsonToken.Null -> {
            reader.nextToken() // consume the null so the surrounding container advances
            true
        }
        else -> false
    }

    // ── aggregate types ──────────────────────────────────────────────────────────────────────────

    override fun <T> readStruct(schema: Schema, state: T, consumer: StructConsumer<T>) {
        if (reader.peek() == JsonToken.Null) {
            reader.nextToken()
            return
        }
        reader.nextTokenOf<JsonToken.BeginObject>()

        val membersByWireName = membersByWireName(schema)
        while (true) {
            when (reader.peek()) {
                JsonToken.EndObject -> {
                    reader.nextToken()
                    break
                }
                JsonToken.EndDocument -> break
                else -> {
                    val name = reader.nextTokenOf<JsonToken.Name>().value
                    val member = membersByWireName[name]
                    when {
                        // skip explicit nulls: leave the member unset (matches legacy struct behavior)
                        reader.peek() == JsonToken.Null -> reader.nextToken()
                        member == null -> reader.skipNext() // unknown member: discard its value
                        else -> consumer.accept(state, member, this)
                    }
                }
            }
        }
    }

    override fun <T> readList(schema: Schema, state: T, consumer: ListConsumer<T>) {
        reader.nextTokenOf<JsonToken.BeginArray>()
        while (true) {
            when (reader.peek()) {
                JsonToken.EndArray -> {
                    reader.nextToken()
                    break
                }
                JsonToken.EndDocument -> break
                else -> consumer.accept(state, this) // sparse nulls handled by the consumer via isNull()
            }
        }
    }

    override fun <T> readMap(schema: Schema, state: T, consumer: MapConsumer<T>) {
        reader.nextTokenOf<JsonToken.BeginObject>()
        while (true) {
            when (reader.peek()) {
                JsonToken.EndObject -> {
                    reader.nextToken()
                    break
                }
                JsonToken.EndDocument -> break
                else -> {
                    val key = reader.nextTokenOf<JsonToken.Name>().value
                    consumer.accept(state, key, this) // sparse nulls handled by the consumer via isNull()
                }
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private fun <T> nextNumber(block: (String) -> T): T = when (val token = reader.nextToken()) {
        is JsonToken.Number -> block(token.value)
        is JsonToken.String -> block(token.value) // Infinity/-Infinity/NaN arrive as strings
        else -> throw DeserializationException("$token cannot be deserialized as type Number")
    }

    // epoch-seconds timestamps may be encoded as a bare number or a quoted string
    private fun readRawNumberOrString(): String = when (val token = reader.nextToken()) {
        is JsonToken.Number -> token.value
        is JsonToken.String -> token.value
        else -> throw DeserializationException("$token cannot be deserialized as an epoch-seconds timestamp")
    }

    // members of a structure/union keyed by their JSON wire name (@jsonName when enabled, else member name)
    private fun membersByWireName(schema: Schema): Map<String, MemberSchema> {
        val members = when (val s = if (schema is MemberSchema) schema.target else schema) {
            is StructureSchema -> s.members
            is UnionSchema -> s.members
            else -> throw DeserializationException("readStruct requires a structure or union schema, got $schema")
        }
        return members.associateBy { member ->
            if (settings.useJsonName) {
                member.getTrait<JsonNameTrait>(JsonNameTrait.ID)?.value ?: member.memberName
            } else {
                member.memberName
            }
        }
    }

    private fun readDocumentValue(): Document? = when (reader.peek()) {
        is JsonToken.Number -> {
            val raw = reader.nextTokenOf<JsonToken.Number>().value
            Document(if (raw.contains('.')) raw.toDouble() else raw.toLong())
        }
        is JsonToken.String -> Document(reader.nextTokenOf<JsonToken.String>().value)
        is JsonToken.Bool -> Document(reader.nextTokenOf<JsonToken.Bool>().value)
        JsonToken.Null -> {
            reader.nextToken()
            null
        }
        JsonToken.BeginArray -> {
            reader.nextToken()
            val items = mutableListOf<Document?>()
            while (reader.peek() != JsonToken.EndArray) items.add(readDocumentValue())
            reader.nextToken() // EndArray
            Document(items)
        }
        JsonToken.BeginObject -> {
            reader.nextToken()
            val entries = mutableMapOf<String, Document?>()
            while (reader.peek() != JsonToken.EndObject) {
                val key = reader.nextTokenOf<JsonToken.Name>().value
                entries[key] = readDocumentValue()
            }
            reader.nextToken() // EndObject
            Document(entries)
        }
        else -> throw DeserializationException("unexpected token ${reader.peek()} while reading document")
    }
}
