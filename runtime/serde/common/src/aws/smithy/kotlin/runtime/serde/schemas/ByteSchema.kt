/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder

public interface ByteSchema : SerializableSchema<Byte>

private data class ByteSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : ByteSchema {
    override val shapeType: ShapeType = ShapeType.BYTE
    override fun deserialize(decoder: Decoder): Byte = decoder.decodeByte()
    override fun serialize(encoder: Encoder, value: Byte) = encoder.encodeByte(value)
}

public fun ByteSchema(shapeId: ShapeId, traits: List<Trait>): ByteSchema = ByteSchemaImpl(shapeId, traits)
