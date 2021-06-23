/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.*

/**
 * Deserialize primitive values for single values, lists, and maps
 */
internal class XmlPrimitiveDeserializer(private val reader: XmlStreamReader, private val fieldDescriptor: SdkFieldDescriptor) :
    PrimitiveDeserializer {

    constructor(input: ByteArray, fieldDescriptor: SdkFieldDescriptor) : this(xmlStreamReader(input), fieldDescriptor)

    private suspend fun <T> deserializeValue(transform: ((String) -> T)): T {
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

    override suspend fun deserializeByte(): Byte = deserializeValue { it.toIntOrNull()?.toByte() ?: throw DeserializationException("Unable to deserialize $it as Byte") }

    override suspend fun deserializeInt(): Int = deserializeValue { it.toIntOrNull() ?: throw DeserializationException("Unable to deserialize $it as Int") }

    override suspend fun deserializeShort(): Short = deserializeValue { it.toIntOrNull()?.toShort() ?: throw DeserializationException("Unable to deserialize $it as Short") }

    override suspend fun deserializeLong(): Long = deserializeValue { it.toLongOrNull() ?: throw DeserializationException("Unable to deserialize $it as Long") }

    override suspend fun deserializeFloat(): Float = deserializeValue { it.toFloatOrNull() ?: throw DeserializationException("Unable to deserialize $it as Float") }

    override suspend fun deserializeDouble(): Double = deserializeValue { it.toDoubleOrNull() ?: throw DeserializationException("Unable to deserialize $it as Double") }

    override suspend fun deserializeString(): String = deserializeValue { it }

    override suspend fun deserializeBoolean(): Boolean = deserializeValue { it.toBoolean() }

    override suspend fun deserializeNull(): Nothing? {
        reader.nextToken() ?: throw DeserializationException("Unexpected end of stream")
        reader.seek<XmlToken.EndElement>()
        reader.nextToken() ?: throw DeserializationException("Unexpected end of stream")

        return null
    }
}
