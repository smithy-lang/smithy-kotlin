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

public interface DoubleSchema : SerializableSchema<Double>

private data class DoubleSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : DoubleSchema {
    override val shapeType: ShapeType = ShapeType.DOUBLE
    override fun deserialize(decoder: Decoder): Double = decoder.decodeDouble()
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

public fun DoubleSchema(shapeId: ShapeId, traits: List<Trait>): DoubleSchema = DoubleSchemaImpl(shapeId, traits)
