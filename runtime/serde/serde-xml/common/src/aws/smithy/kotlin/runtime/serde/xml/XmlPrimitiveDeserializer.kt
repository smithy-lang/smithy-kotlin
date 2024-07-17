/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.text.encoding.decodeBase64Bytes
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat

/**
 * Deserialize primitive values for single values, lists, and maps
 */
internal class XmlPrimitiveDeserializer(private val reader: XmlStreamReader, private val fieldDescriptor: SdkFieldDescriptor) : PrimitiveDeserializer {

    constructor(input: ByteArray, fieldDescriptor: SdkFieldDescriptor) : this(xmlStreamReader(input), fieldDescriptor)

    private fun <T> deserializeValue(transform: ((String) -> T)): T {
        if (reader.peek() is XmlToken.BeginElement) {
            // In the case of flattened lists, we "fall" into the first member as there is no wrapper.
            // this conditional checks that case for the first element of the list.
            val wrapperToken = reader.takeNextAs<XmlToken.BeginElement>()
            if (wrapperToken.name.local != fieldDescriptor.generalName()) {
                // Depending on flat/not-flat, may need to consume multiple start tokens
                return deserializeValue(transform)
            }
        }

        val token = reader.takeNextAs<XmlToken.Text>()

        return token.value
            ?.let { transform(it) }
            ?.also<T> { reader.takeNextAs<XmlToken.EndElement>() } ?: throw DeserializationException("$token specifies nonexistent or invalid value.")
    }

    override fun deserializeByte(): Byte = deserializeValue { it.toIntOrNull()?.toByte() ?: throw DeserializationException("Unable to deserialize $it as Byte") }

    override fun deserializeInt(): Int = deserializeValue { it.toIntOrNull() ?: throw DeserializationException("Unable to deserialize $it as Int") }

    override fun deserializeShort(): Short = deserializeValue { it.toIntOrNull()?.toShort() ?: throw DeserializationException("Unable to deserialize $it as Short") }

    override fun deserializeLong(): Long = deserializeValue { it.toLongOrNull() ?: throw DeserializationException("Unable to deserialize $it as Long") }

    override fun deserializeFloat(): Float = deserializeValue { it.toFloatOrNull() ?: throw DeserializationException("Unable to deserialize $it as Float") }

    override fun deserializeDouble(): Double = deserializeValue { it.toDoubleOrNull() ?: throw DeserializationException("Unable to deserialize $it as Double") }

    override fun deserializeBigInteger(): BigInteger = deserializeValue {
        runCatching { BigInteger(it) }
            .getOrElse { throw DeserializationException("Unable to deserialize $it as BigInteger") }
    }

    override fun deserializeBigDecimal(): BigDecimal = deserializeValue {
        runCatching { BigDecimal(it) }
            .getOrElse { throw DeserializationException("Unable to deserialize $it as BigDecimal") }
    }

    override fun deserializeString(): String = deserializeValue { it }

    override fun deserializeBoolean(): Boolean = deserializeValue { it.toBoolean() }

    override fun deserializeDocument(): Document = throw DeserializationException("cannot deserialize unsupported Document type in xml")

    override fun deserializeNull(): Nothing? {
        reader.nextToken() ?: throw DeserializationException("Unexpected end of stream")
        reader.seek<XmlToken.EndElement>()
        reader.nextToken() ?: throw DeserializationException("Unexpected end of stream")

        return null
    }

    override fun deserializeByteArray(): ByteArray = deserializeString().decodeBase64Bytes()

    override fun deserializeInstant(format: TimestampFormat): Instant = when (format) {
        TimestampFormat.EPOCH_SECONDS -> deserializeString().let { Instant.fromEpochSeconds(it) }
        TimestampFormat.ISO_8601 -> deserializeString().let { Instant.fromIso8601(it) }
        TimestampFormat.RFC_5322 -> deserializeString().let { Instant.fromRfc5322(it) }
        else -> throw DeserializationException("unknown timestamp format: $format")
    }
}
