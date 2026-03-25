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

public interface ShortSchema : SerializableSchema<Short>

private data class ShortSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : ShortSchema {
    override val shapeType: ShapeType = ShapeType.SHORT
    override fun deserialize(decoder: Decoder): Short = decoder.decodeShort()
    override fun serialize(encoder: Encoder, value: Short) = encoder.encodeShort(value)
}

public fun ShortSchema(shapeId: ShapeId, traits: List<Trait>): ShortSchema = ShortSchemaImpl(shapeId, traits)
