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

public interface FloatSchema : SerializableSchema<Float>

private data class FloatSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : FloatSchema {
    override val shapeType: ShapeType = ShapeType.FLOAT
    override fun deserialize(decoder: Decoder): Float = decoder.decodeFloat()
    override fun serialize(encoder: Encoder, value: Float) = encoder.encodeFloat(value)
}

public fun FloatSchema(shapeId: ShapeId, traits: List<Trait>): FloatSchema = FloatSchemaImpl(shapeId, traits)
